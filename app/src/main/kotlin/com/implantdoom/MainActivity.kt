package com.implantdoom

import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.implantdoom.ui.AppViewModel
import com.implantdoom.ui.ImplantDoomApp

/**
 * Single activity hosting the whole Compose UI and owning the [NfcAdapter].
 *
 * NFC strategy: while the app is in the foreground it uses **Reader Mode** with
 * `SKIP_NDEF_CHECK` for ALL interactions (read / write / diagnostics). Our target
 * is a Type 5 / ISO 15693 implant whose Capability Container is the "broken"
 * `E140FF09`, so we talk raw ISO 15693 (addressed) for both reading and writing —
 * which needs the single, stable tag handle Reader Mode provides. Foreground
 * dispatch re-polls a lingering tag and throws "Tag is out of date" mid-operation.
 *
 * The [readerCallback] routes every scanned tag to the view model, which decides
 * what to do based on the current [com.implantdoom.ui.NfcMode]. The manifest's
 * NDEF/TECH/TAG intent filters still cold-launch the app from a tap.
 */
class MainActivity : ComponentActivity() {

    private val viewModel: AppViewModel by viewModels()
    private var nfcAdapter: NfcAdapter? = null
    private var readerModeActive = false

    /** Reader Mode delivers tags here on a binder thread (already off the main thread). */
    private val readerCallback = NfcAdapter.ReaderCallback { tag ->
        viewModel.onTagScanned(tag, null)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        publishNfcStatus()

        setContent { ImplantDoomApp(viewModel) }

        // Cold-start: the app may have been launched by tapping a written implant.
        handleIntent(intent, isLaunch = true)
    }

    override fun onResume() {
        super.onResume()
        publishNfcStatus()
        enableReaderMode()
    }

    override fun onPause() {
        super.onPause()
        disableReaderMode()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent, isLaunch = false)
    }

    private fun publishNfcStatus() {
        viewModel.setNfcStatus(
            supported = nfcAdapter != null,
            enabled = nfcAdapter?.isEnabled == true,
        )
    }

    private fun enableReaderMode() {
        val adapter = nfcAdapter ?: return
        if (!adapter.isEnabled) return
        val flags = NfcAdapter.FLAG_READER_NFC_A or
            NfcAdapter.FLAG_READER_NFC_B or
            NfcAdapter.FLAG_READER_NFC_F or
            NfcAdapter.FLAG_READER_NFC_V or
            NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK
        // A tiny implant couples weakly. By default Android presence-checks the tag
        // every ~125ms and, on a brief dip, declares it gone and invalidates our
        // handle mid-operation ("Tag is out of date"). A long presence-check delay
        // makes Reader Mode ride through those dips so multi-block read/write completes.
        val extras = Bundle().apply {
            putInt(NfcAdapter.EXTRA_READER_PRESENCE_CHECK_DELAY, 5000)
        }
        runCatching { adapter.enableReaderMode(this, readerCallback, flags, extras) }
        readerModeActive = true
    }

    private fun disableReaderMode() {
        if (readerModeActive) {
            runCatching { nfcAdapter?.disableReaderMode(this) }
            readerModeActive = false
        }
    }

    /** Cold-launch path: the OS started us from a tag tap (manifest intent filters). */
    private fun handleIntent(intent: Intent?, isLaunch: Boolean) {
        if (intent == null) return
        when (intent.action) {
            NfcAdapter.ACTION_NDEF_DISCOVERED,
            NfcAdapter.ACTION_TECH_DISCOVERED,
            NfcAdapter.ACTION_TAG_DISCOVERED,
            -> {
                val tag = getTagExtra(intent)
                if (isLaunch && intent.action == NfcAdapter.ACTION_NDEF_DISCOVERED) {
                    viewModel.handleLaunchIntent(tag, intent)
                } else {
                    viewModel.onTagScanned(tag, intent)
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun getTagExtra(intent: Intent): Tag? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(NfcAdapter.EXTRA_TAG, Tag::class.java)
        } else {
            intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
        }
}
