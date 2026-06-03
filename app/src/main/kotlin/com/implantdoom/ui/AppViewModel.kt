package com.implantdoom.ui

import android.content.Intent
import android.nfc.Tag
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.implantdoom.cartridge.Cartridge
import com.implantdoom.cartridge.CartridgeCodec
import com.implantdoom.cartridge.DemoCartridge
import com.implantdoom.cartridge.MapGenerator
import com.implantdoom.nfc.NdefCartridge
import com.implantdoom.nfc.NfcReader
import com.implantdoom.nfc.NfcWriter
import com.implantdoom.nfc.ReadResult
import com.implantdoom.nfc.TagDiagnostics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What the next tag tap should do. Set by the screens, read by [MainActivity]. */
enum class NfcMode { READ, WRITE, DIAGNOSTICS }

/** Where the currently-active cartridge came from (shown on the details screen). */
enum class CartridgeSource(val label: String) {
    NONE("—"),
    DEMO("Built-in demo"),
    NFC("NFC implant"),
    BUILDER("Builder"),
}

/** Editable state for the cartridge builder/writer screen. */
data class BuilderState(
    val seed: Long = 0x1D00D000L,
    val entityCount: Int = 8,
    val itemCount: Int = 8,
    val wallDensity: Double = 0.18,
    val playerStartX: Int = 1,
    val playerStartY: Int = 1,
    val themeId: Int = 0,
    val cartridge: Cartridge? = null,
    val bytes: ByteArray? = null,
    val sizeBytes: Int = 0,
    val crcValid: Boolean = false,
    val underLimit: Boolean = true,
    val error: String? = null,
)

/**
 * Single source of truth for UI + NFC state. [MainActivity] owns the NFC adapter
 * and forwards scanned tags here via [onTagScanned]; the Compose screens observe
 * the exposed [StateFlow]s.
 *
 * All blocking NFC I/O runs on [Dispatchers.IO]; state is published back on the
 * main dispatcher by [MutableStateFlow] (thread-safe).
 */
class AppViewModel : ViewModel() {

    // --- NFC hardware status (pushed in by MainActivity) ---
    private val _nfcSupported = MutableStateFlow(false)
    val nfcSupported: StateFlow<Boolean> = _nfcSupported.asStateFlow()
    private val _nfcEnabled = MutableStateFlow(false)
    val nfcEnabled: StateFlow<Boolean> = _nfcEnabled.asStateFlow()

    // --- Current NFC interaction mode ---
    val nfcMode = MutableStateFlow(NfcMode.READ)

    // --- The cartridge currently loaded for details / play ---
    private val _activeCartridge = MutableStateFlow<Cartridge?>(null)
    val activeCartridge: StateFlow<Cartridge?> = _activeCartridge.asStateFlow()
    private val _activeBytes = MutableStateFlow<ByteArray?>(null)
    val activeBytes: StateFlow<ByteArray?> = _activeBytes.asStateFlow()
    private val _activeSource = MutableStateFlow(CartridgeSource.NONE)
    val activeSource: StateFlow<CartridgeSource> = _activeSource.asStateFlow()

    // --- Scan results ---
    private val _readError = MutableStateFlow<String?>(null)
    val readError: StateFlow<String?> = _readError.asStateFlow()
    private val _lastReadAt = MutableStateFlow(0L)
    val lastReadAt: StateFlow<Long> = _lastReadAt.asStateFlow()

    // --- Diagnostics ---
    private val _diagnostics = MutableStateFlow<TagDiagnostics?>(null)
    val diagnostics: StateFlow<TagDiagnostics?> = _diagnostics.asStateFlow()
    val advancedNfcVEnabled = MutableStateFlow(false)

    // --- Writing ---
    private val _writeArmed = MutableStateFlow(false)
    val writeArmed: StateFlow<Boolean> = _writeArmed.asStateFlow()
    private val _writeStatus = MutableStateFlow<String?>(null)
    val writeStatus: StateFlow<String?> = _writeStatus.asStateFlow()
    private var pendingWriteBytes: ByteArray? = null

    // --- Builder ---
    val builder = MutableStateFlow(BuilderState())

    // --- One-shot navigation requests (consumed by the NavHost) ---
    private val _pendingRoute = MutableStateFlow<String?>(null)
    val pendingRoute: StateFlow<String?> = _pendingRoute.asStateFlow()

    init {
        // Make the app immediately usable without NFC.
        loadDemoCartridge(navigate = false)
        regenerateBuilder()
    }

    // ------------------------------------------------------------------
    // Hardware status
    // ------------------------------------------------------------------

    fun setNfcStatus(supported: Boolean, enabled: Boolean) {
        _nfcSupported.value = supported
        _nfcEnabled.value = enabled
    }

    // ------------------------------------------------------------------
    // Active cartridge
    // ------------------------------------------------------------------

    fun setActiveCartridge(cartridge: Cartridge, bytes: ByteArray?, source: CartridgeSource) {
        _activeCartridge.value = cartridge
        _activeBytes.value = bytes ?: runCatching { CartridgeCodec.encode(cartridge) }.getOrNull()
        _activeSource.value = source
    }

    fun loadDemoCartridge(navigate: Boolean = true) {
        val cartridge = DemoCartridge.default()
        setActiveCartridge(cartridge, CartridgeCodec.encode(cartridge), CartridgeSource.DEMO)
        if (navigate) requestRoute(Routes.DETAILS)
    }

    // ------------------------------------------------------------------
    // NFC tag dispatch (called by MainActivity on every scanned tag)
    // ------------------------------------------------------------------

    fun onTagScanned(tag: Tag?, intent: Intent?) {
        when (nfcMode.value) {
            NfcMode.WRITE -> handleWrite(tag)
            NfcMode.DIAGNOSTICS -> handleDiagnostics(tag, intent)
            NfcMode.READ -> handleRead(tag, intent)
        }
    }

    private fun handleRead(tag: Tag?, intent: Intent?) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { NfcReader.read(tag, intent, readNfcVBlocks = false) }
            applyReadResult(result, navigateOnSuccess = true)
        }
    }

    private fun handleDiagnostics(tag: Tag?, intent: Intent?) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                NfcReader.read(
                    tag = tag,
                    intent = intent,
                    readNfcVBlocks = advancedNfcVEnabled.value,
                    blockStart = 0,
                    blockCount = 8,
                )
            }
            _diagnostics.value = result.diagnostics
            // Populate the active cartridge too, if one happened to be present.
            if (result.cartridge != null) {
                setActiveCartridge(result.cartridge, result.cartridgeBytes, CartridgeSource.NFC)
            }
        }
    }

    private fun applyReadResult(result: ReadResult, navigateOnSuccess: Boolean) {
        _diagnostics.value = result.diagnostics
        _lastReadAt.value = _lastReadAt.value + 1 // bump so screens can react
        val cartridge = result.cartridge
        if (cartridge != null) {
            setActiveCartridge(cartridge, result.cartridgeBytes, CartridgeSource.NFC)
            _readError.value = null
            // Straight into the game when a cartridge is read from the implant —
            // no details screen, no confirmation (covers both in-app Scan and the
            // home-screen NFC cold-launch).
            if (navigateOnSuccess) requestRoute(Routes.PLAY)
        } else {
            _readError.value = when {
                result.parseError != null -> "Cartridge record found but invalid: ${result.parseError}"
                result.cartridgeBytes != null -> "Cartridge record found but could not be parsed"
                result.diagnostics.cartridgeFound -> "Cartridge record present but empty"
                else -> "No NFC-DOOM cartridge found on this tag"
            }
        }
    }

    /** Process the cold-start launch intent (NDEF_DISCOVERED) on app open. */
    fun handleLaunchIntent(tag: Tag?, intent: Intent?) {
        if (tag == null) return
        nfcMode.value = NfcMode.READ
        handleRead(tag, intent)
    }

    // ------------------------------------------------------------------
    // Writing
    // ------------------------------------------------------------------

    /** Arm a write of [bytes]; the next tag tap (in WRITE mode) performs it. */
    fun armWrite(bytes: ByteArray) {
        pendingWriteBytes = bytes
        _writeArmed.value = true
        _writeStatus.value = "Ready — hold your implant to the phone to write ${bytes.size} bytes."
        nfcMode.value = NfcMode.WRITE
    }

    fun cancelWrite() {
        pendingWriteBytes = null
        _writeArmed.value = false
        _writeStatus.value = null
        nfcMode.value = NfcMode.READ
    }

    private val writeInProgress = java.util.concurrent.atomic.AtomicBoolean(false)

    private fun handleWrite(tag: Tag?) {
        val bytes = pendingWriteBytes
        if (tag == null || bytes == null) return
        // Reader Mode can deliver the same tag again while a write is running; ignore re-entry.
        if (!writeInProgress.compareAndSet(false, true)) return
        viewModelScope.launch {
            try {
                _writeStatus.value = "Writing ${bytes.size} bytes…"
                val outcome = withContext(Dispatchers.IO) {
                    NfcWriter.write(tag, NdefCartridge.toNdefMessage(bytes))
                }
                _writeStatus.value = describeOutcome(outcome)
                if (outcome is NfcWriter.Outcome.Success) {
                    _writeArmed.value = false
                    pendingWriteBytes = null
                    nfcMode.value = NfcMode.READ
                }
            } finally {
                writeInProgress.set(false)
            }
        }
    }

    private fun describeOutcome(outcome: NfcWriter.Outcome): String = when (outcome) {
        is NfcWriter.Outcome.Success ->
            "Success — wrote ${outcome.bytesWritten} bytes" +
                (if (outcome.formatted) " (tag was formatted as NDEF)." else ".") +
                (outcome.maxSize?.let { " Tag capacity: $it bytes." } ?: "")
        NfcWriter.Outcome.ReadOnly -> "Failed — tag is read-only / not writable."
        is NfcWriter.Outcome.TooSmall ->
            "Failed — cartridge needs ${outcome.neededBytes} bytes but tag holds ${outcome.availableBytes}."
        NfcWriter.Outcome.NotNdef -> "Failed — tag does not support NDEF and cannot be formatted."
        is NfcWriter.Outcome.Error -> "Failed — ${outcome.message}"
    }

    // ------------------------------------------------------------------
    // Diagnostics toggles
    // ------------------------------------------------------------------

    fun setDiagnosticsMode() { nfcMode.value = NfcMode.DIAGNOSTICS }
    fun setReadMode() { nfcMode.value = NfcMode.READ }
    fun toggleAdvancedNfcV() { advancedNfcVEnabled.value = !advancedNfcVEnabled.value }
    fun clearReadError() { _readError.value = null }

    // ------------------------------------------------------------------
    // Builder
    // ------------------------------------------------------------------

    fun updateBuilderSeed(seed: Long) { builder.value = builder.value.copy(seed = seed and 0xFFFFFFFFL); regenerateBuilder() }
    fun randomizeBuilderSeed() {
        // No Date.now()/Random here that breaks determinism elsewhere; derive a new
        // seed by hashing the current one so repeated taps keep changing it.
        val next = (builder.value.seed * 6364136223846793005L + 1442695040888963407L) and 0xFFFFFFFFL
        updateBuilderSeed(next)
    }
    fun setBuilderEntityCount(n: Int) { builder.value = builder.value.copy(entityCount = n.coerceIn(0, 64)); regenerateBuilder() }
    fun setBuilderItemCount(n: Int) { builder.value = builder.value.copy(itemCount = n.coerceIn(0, 64)); regenerateBuilder() }
    fun setBuilderTheme(themeId: Int) { builder.value = builder.value.copy(themeId = themeId.coerceIn(0, 3)); regenerateBuilder() }
    fun setBuilderWallDensity(d: Double) { builder.value = builder.value.copy(wallDensity = d.coerceIn(0.0, 0.45)); regenerateBuilder() }

    fun regenerateBuilder() {
        val s = builder.value
        try {
            val cartridge = MapGenerator.generate(
                seed = s.seed,
                wallDensity = s.wallDensity,
                entityCount = s.entityCount,
                itemCount = s.itemCount,
                playerStartX = s.playerStartX,
                playerStartY = s.playerStartY,
                textureThemeId = s.themeId,
            )
            val bytes = CartridgeCodec.encode(cartridge)
            builder.value = s.copy(
                cartridge = cartridge,
                bytes = bytes,
                sizeBytes = bytes.size,
                crcValid = CartridgeCodec.isValid(bytes),
                underLimit = bytes.size <= CartridgeCodec.MAX_SIZE_BYTES,
                error = null,
            )
        } catch (e: Exception) {
            builder.value = s.copy(error = e.message, cartridge = null, bytes = null, crcValid = false)
        }
    }

    /** Load the demo into the builder (for "Write demo cartridge to tag"). */
    fun loadDemoIntoBuilder() {
        val demo = DemoCartridge.default()
        val bytes = CartridgeCodec.encode(demo)
        builder.value = builder.value.copy(
            seed = demo.seed,
            entityCount = demo.entityCount,
            itemCount = demo.itemCount,
            themeId = demo.textureThemeId,
            cartridge = demo,
            bytes = bytes,
            sizeBytes = bytes.size,
            crcValid = CartridgeCodec.isValid(bytes),
            underLimit = bytes.size <= CartridgeCodec.MAX_SIZE_BYTES,
            error = null,
        )
    }

    /** Promote the builder's cartridge to the active one (for details/play). */
    fun useBuilderCartridge(navigate: Boolean = true) {
        val s = builder.value
        val cartridge = s.cartridge ?: return
        setActiveCartridge(cartridge, s.bytes, CartridgeSource.BUILDER)
        if (navigate) requestRoute(Routes.DETAILS)
    }

    // ------------------------------------------------------------------
    // Navigation plumbing
    // ------------------------------------------------------------------

    fun requestRoute(route: String) { _pendingRoute.value = route }
    fun consumeRoute() { _pendingRoute.value = null }
}
