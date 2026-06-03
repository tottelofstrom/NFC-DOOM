package com.implantdoom.nfc

import android.nfc.NdefMessage
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import android.nfc.tech.NfcV

/**
 * Writes a cartridge [NdefMessage] to a tag.
 *
 * Safety constraints (intentional, do not relax without explicit user intent):
 *   * Writes only the NDEF message. Never locks the tag, never makes it
 *     read-only, never sets a password and never touches AFI/DSFID/lock/security.
 *   * If the tag is already NDEF-formatted it is written in place; if it is only
 *     [NdefFormatable] it is formatted (NOT formatReadOnly) with the message.
 *   * Refuses to write if the message does not fit in the tag's reported size.
 *
 * Call from a background thread: NFC I/O blocks.
 */
object NfcWriter {

    /** Result of a write attempt. */
    sealed interface Outcome {
        /** Message written successfully. [formatted] is true if the tag had to be NDEF-formatted first. */
        data class Success(val formatted: Boolean, val bytesWritten: Int, val maxSize: Int?) : Outcome

        /** Tag supports NDEF but is read-only / not writable. */
        data object ReadOnly : Outcome

        /** Message is larger than the tag can hold. */
        data class TooSmall(val neededBytes: Int, val availableBytes: Int) : Outcome

        /** Tag supports neither Ndef nor NdefFormatable. */
        data object NotNdef : Outcome

        /** Any I/O or unexpected failure. */
        data class Error(val message: String) : Outcome
    }

    fun write(tag: Tag, message: NdefMessage): Outcome {
        // DEFAULT for ISO 15693 / Type 5 implants (NXP ICODE): write NDEF at the raw
        // block level. Android's high-level NDEF format/write is unreliable on Type 5
        // tags ("NDEF format failed"), so for any NfcV tag we skip it entirely and use
        // the stable raw path — which only writes NDEF data blocks, never the CC/lock/security.
        NfcV.get(tag)?.let { nfcv ->
            return NfcVNdefWriter.write(nfcv, message)
        }

        val needed = message.toByteArray().size

        // Preferred path: tag is already NDEF formatted.
        Ndef.get(tag)?.let { ndef ->
            return try {
                ndef.connect()
                when {
                    !ndef.isWritable -> Outcome.ReadOnly
                    ndef.maxSize < needed -> Outcome.TooSmall(needed, ndef.maxSize)
                    else -> {
                        ndef.writeNdefMessage(message)
                        Outcome.Success(formatted = false, bytesWritten = needed, maxSize = ndef.maxSize)
                    }
                }
            } catch (e: Exception) {
                Outcome.Error(e.message ?: "NDEF write failed")
            } finally {
                runCatching { ndef.close() }
            }
        }

        // Fallback path: blank tag we can format then write (no read-only lock).
        NdefFormatable.get(tag)?.let { formatable ->
            return try {
                formatable.connect()
                formatable.format(message)
                Outcome.Success(formatted = true, bytesWritten = needed, maxSize = null)
            } catch (e: Exception) {
                Outcome.Error(e.message ?: "NDEF format failed")
            } finally {
                runCatching { formatable.close() }
            }
        }

        return Outcome.NotNdef
    }
}
