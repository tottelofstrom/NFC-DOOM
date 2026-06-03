package com.implantdoom.nfc

import android.content.Intent
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.nfc.tech.NfcV
import com.implantdoom.cartridge.Cartridge
import com.implantdoom.cartridge.CartridgeCodec
import com.implantdoom.cartridge.CartridgeException
import java.nio.charset.StandardCharsets

// ---------------------------------------------------------------------------
// Diagnostic models (rendered by the NFC diagnostics screen)
// ---------------------------------------------------------------------------

/** Human-readable summary of one NDEF record. */
data class NdefRecordInfo(
    val index: Int,
    val tnfName: String,
    val typeDescription: String,
    val payloadLength: Int,
    val isCartridge: Boolean,
)

/** ISO 15693 / NFC-V specific diagnostics. */
data class NfcVInfo(
    val dsfid: Int?,
    val responseFlags: Int?,
    val maxTransceiveLength: Int?,
    val systemInfo: NfcVDiagnostics.SystemInfo?,
    val blocks: List<NfcVDiagnostics.BlockRead>,
)

/** Everything the diagnostics screen needs about a scanned tag. */
data class TagDiagnostics(
    /** UID in Android's byte order, colon-separated hex (e.g. `04:E0:…`). */
    val uidColonHex: String,
    /** UID, Android byte order, no separators. */
    val uidHex: String,
    /** UID reversed (the order MTools and some readers display). */
    val uidReversedHex: String,
    val techList: List<String>,
    val ndefAvailable: Boolean,
    val ndefType: String?,
    val ndefMaxSizeBytes: Int?,
    val ndefWritable: Boolean?,
    val ndefCanMakeReadOnly: Boolean?,
    val records: List<NdefRecordInfo>,
    val cartridgeFound: Boolean,
    val nfcV: NfcVInfo?,
    val notes: List<String>,
)

/** Outcome of reading a tag: diagnostics plus an optional parsed cartridge. */
data class ReadResult(
    val diagnostics: TagDiagnostics,
    val cartridgeBytes: ByteArray?,
    val cartridge: Cartridge?,
    val parseError: String?,
)

/**
 * Reads a scanned [Tag] (and the originating [Intent], when available) into a
 * [ReadResult]: full diagnostics, plus the cartridge payload + parsed cartridge
 * if a `application/vnd.implantdoom.cartridge` MIME record is present.
 *
 * Everything here is read-only. NfcV block/system-info reads only happen when
 * [readNfcVBlocks] is set (the diagnostics screen's clearly-labelled advanced
 * option) and never write to the tag.
 *
 * Call from a background thread: it performs blocking NFC I/O.
 */
object NfcReader {

    fun read(
        tag: Tag?,
        intent: Intent?,
        readNfcVBlocks: Boolean = false,
        blockStart: Int = 0,
        blockCount: Int = 8,
    ): ReadResult {
        val notes = mutableListOf<String>()

        val uidBytes = tag?.id ?: ByteArray(0)
        val uidHex = uidBytes.joinToString("") { "%02X".format(it) }
        val uidColon = uidBytes.joinToString(":") { "%02X".format(it) }
        val uidReversed = uidBytes.reversed().joinToString("") { "%02X".format(it) }
        val techList = tag?.techList?.map { it.substringAfterLast('.') } ?: emptyList()

        // --- NDEF ---
        var ndefAvailable = false
        var ndefType: String? = null
        var ndefMaxSize: Int? = null
        var ndefWritable: Boolean? = null
        var ndefCanMakeRo: Boolean? = null
        var records: List<NdefRecordInfo> = emptyList()
        var cartridgeBytes: ByteArray? = null

        val ndef = tag?.let { Ndef.get(it) }
        if (ndef != null) {
            ndefAvailable = true
            // Cached properties: no connection needed.
            ndefType = ndef.type
            ndefMaxSize = ndef.maxSize
            ndefWritable = ndef.isWritable
            ndefCanMakeRo = ndef.canMakeReadOnly()

            val message = readNdefMessage(ndef, intent, notes)
            if (message != null) {
                records = describeRecords(message)
                cartridgeBytes = NdefCartridge.extractPayload(message)
            }
        } else {
            // The launch intent may still carry NDEF messages even if Ndef.get is null.
            val message = intentNdefMessages(intent).firstOrNull()
            if (message != null) {
                records = describeRecords(message)
                cartridgeBytes = NdefCartridge.extractPayload(message)
            }
        }

        // --- NfcV (ISO 15693) ---
        var nfcVInfo: NfcVInfo? = null
        val nfcV = tag?.let { NfcV.get(it) }

        // Raw ISO 15693 NDEF read fallback: the reference implant's broken CC
        // (E140FF09) hides NDEF from Android, so if the normal path found no
        // cartridge, read the NDEF data blocks directly. Read-only.
        if (cartridgeBytes == null && nfcV != null) {
            try {
                val ndefBytes = NfcVNdefReader.read(nfcV)
                if (ndefBytes != null) {
                    val message = NdefMessage(ndefBytes)
                    records = describeRecords(message)
                    cartridgeBytes = NdefCartridge.extractPayload(message)
                    if (cartridgeBytes != null) notes += "Read via raw ISO 15693 (CC bypass)."
                }
            } catch (e: Exception) {
                notes += "Raw NfcV read failed: ${e.message}"
            }
        }

        if (nfcV != null) {
            // Cheap cached values (no connection required).
            val dsfid = runCatching { nfcV.dsfId.toInt() and 0xFF }.getOrNull()
            val responseFlags = runCatching { nfcV.responseFlags.toInt() and 0xFF }.getOrNull()
            val maxTransceive = runCatching { nfcV.maxTransceiveLength }.getOrNull()

            var systemInfo: NfcVDiagnostics.SystemInfo? = null
            var blocks: List<NfcVDiagnostics.BlockRead> = emptyList()
            if (readNfcVBlocks) {
                try {
                    nfcV.connect()
                    systemInfo = NfcVDiagnostics.readSystemInfo(nfcV)
                    val count = systemInfo?.blockCount?.coerceAtMost(blockCount) ?: blockCount
                    blocks = NfcVDiagnostics.readBlocks(nfcV, blockStart, count)
                } catch (e: Exception) {
                    notes += "NfcV read failed: ${e.message}"
                } finally {
                    runCatching { nfcV.close() }
                }
            }
            nfcVInfo = NfcVInfo(dsfid, responseFlags, maxTransceive, systemInfo, blocks)
        }

        val diagnostics = TagDiagnostics(
            uidColonHex = uidColon,
            uidHex = uidHex,
            uidReversedHex = uidReversed,
            techList = techList,
            ndefAvailable = ndefAvailable,
            ndefType = ndefType,
            ndefMaxSizeBytes = ndefMaxSize,
            ndefWritable = ndefWritable,
            ndefCanMakeReadOnly = ndefCanMakeRo,
            records = records,
            cartridgeFound = cartridgeBytes != null,
            nfcV = nfcVInfo,
            notes = notes,
        )

        // --- Parse cartridge if found ---
        var cartridge: Cartridge? = null
        var parseError: String? = null
        if (cartridgeBytes != null) {
            try {
                cartridge = CartridgeCodec.decode(cartridgeBytes)
            } catch (e: CartridgeException) {
                parseError = e.message
            }
        }

        return ReadResult(diagnostics, cartridgeBytes, cartridge, parseError)
    }

    /** Read a live NDEF message, falling back to the intent's cached messages. */
    private fun readNdefMessage(ndef: Ndef, intent: Intent?, notes: MutableList<String>): NdefMessage? {
        // Prefer the system-provided messages from the launch/dispatch intent.
        intentNdefMessages(intent).firstOrNull()?.let { return it }

        // Otherwise connect and read live.
        return try {
            ndef.connect()
            ndef.ndefMessage ?: ndef.cachedNdefMessage
        } catch (e: Exception) {
            notes += "NDEF read failed: ${e.message}"
            ndef.cachedNdefMessage
        } finally {
            runCatching { ndef.close() }
        }
    }

    /** Extract any NDEF messages the OS attached to [intent] (cold-start path). */
    @Suppress("DEPRECATION") // typed getParcelableArrayExtra is API 33+; we support 26+
    private fun intentNdefMessages(intent: Intent?): List<NdefMessage> {
        val raw = intent?.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES) ?: return emptyList()
        return raw.mapNotNull { it as? NdefMessage }
    }

    private fun describeRecords(message: NdefMessage): List<NdefRecordInfo> =
        message.records.mapIndexed { i, r ->
            NdefRecordInfo(
                index = i,
                tnfName = tnfName(r.tnf),
                typeDescription = typeDescription(r),
                payloadLength = r.payload.size,
                isCartridge = NdefCartridge.isCartridgeRecord(r),
            )
        }

    private fun tnfName(tnf: Short): String = when (tnf) {
        NdefRecord.TNF_EMPTY -> "EMPTY"
        NdefRecord.TNF_WELL_KNOWN -> "WELL_KNOWN"
        NdefRecord.TNF_MIME_MEDIA -> "MIME"
        NdefRecord.TNF_ABSOLUTE_URI -> "ABSOLUTE_URI"
        NdefRecord.TNF_EXTERNAL_TYPE -> "EXTERNAL"
        NdefRecord.TNF_UNKNOWN -> "UNKNOWN"
        NdefRecord.TNF_UNCHANGED -> "UNCHANGED"
        else -> "RESERVED"
    }

    private fun typeDescription(r: NdefRecord): String = when (r.tnf) {
        NdefRecord.TNF_MIME_MEDIA -> r.toMimeType() ?: String(r.type, StandardCharsets.US_ASCII)
        NdefRecord.TNF_ABSOLUTE_URI -> String(r.type, StandardCharsets.US_ASCII)
        NdefRecord.TNF_WELL_KNOWN -> "RTD:" + String(r.type, StandardCharsets.US_ASCII)
        NdefRecord.TNF_EXTERNAL_TYPE -> String(r.type, StandardCharsets.US_ASCII)
        else -> r.type.joinToString("") { "%02X".format(it) }
    }
}
