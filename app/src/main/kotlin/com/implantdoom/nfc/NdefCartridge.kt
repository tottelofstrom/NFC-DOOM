package com.implantdoom.nfc

import android.nfc.NdefMessage
import android.nfc.NdefRecord
import com.implantdoom.cartridge.CartridgeCodec
import java.nio.charset.StandardCharsets

/**
 * NDEF (de)serialization for ImplantDoom cartridges.
 *
 * The primary on-tag representation is a single **MIME** NDEF record:
 *
 *   * MIME type: `application/vnd.implantdoom.cartridge`
 *   * Payload:   the raw v1 cartridge bytes (see [CartridgeCodec])
 *
 * The cartridge is deliberately **not** stored as Text or URI; a custom MIME type
 * lets Android auto-launch this app (via the NDEF_DISCOVERED intent filter) and
 * keeps the payload compact. A debug-only URI helper is provided but never used
 * as the primary format.
 */
object NdefCartridge {

    /** Custom vendor MIME type identifying an ImplantDoom cartridge record. */
    const val MIME_TYPE = "application/vnd.implantdoom.cartridge"

    private val MIME_TYPE_BYTES = MIME_TYPE.toByteArray(StandardCharsets.US_ASCII)

    /** Wrap raw cartridge [payload] bytes into a one-record NDEF message. */
    fun toNdefMessage(payload: ByteArray): NdefMessage =
        NdefMessage(arrayOf(createMimeRecord(payload)))

    /** A single MIME record carrying the cartridge payload. */
    fun createMimeRecord(payload: ByteArray): NdefRecord =
        NdefRecord.createMime(MIME_TYPE, payload)

    /**
     * Find the first ImplantDoom cartridge record in [message] and return its raw
     * payload bytes, or `null` if no matching MIME record is present.
     *
     * Matching is done on the TNF + type bytes directly (rather than relying on
     * [NdefRecord.toMimeType]) so it also recognises records written by other
     * tools using the well-known-type encoding of a MIME string.
     */
    fun extractPayload(message: NdefMessage): ByteArray? {
        for (record in message.records) {
            if (isCartridgeRecord(record)) return record.payload
        }
        return null
    }

    /** True if [record] is a MIME record whose type is our cartridge MIME type. */
    fun isCartridgeRecord(record: NdefRecord): Boolean {
        val isMimeTnf = record.tnf == NdefRecord.TNF_MIME_MEDIA
        if (isMimeTnf && record.type.contentEquals(MIME_TYPE_BYTES)) return true
        // Fall back to the framework helper, which normalises some edge cases.
        return record.toMimeType()?.equals(MIME_TYPE, ignoreCase = true) == true
    }

    /**
     * Debug-only fallback: encode the cartridge as a URI record
     * (`implantdoom://cartridge?...`). Not used as the primary format and never
     * written by the app's normal flows; provided to aid manual inspection.
     */
    fun createDebugUriRecord(payload: ByteArray): NdefRecord {
        val hex = payload.joinToString("") { "%02x".format(it) }
        return NdefRecord.createUri("implantdoom://cartridge?v=1&data=$hex")
    }
}
