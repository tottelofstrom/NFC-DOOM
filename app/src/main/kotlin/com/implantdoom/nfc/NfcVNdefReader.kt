package com.implantdoom.nfc

import android.nfc.tech.NfcV
import android.util.Log

/**
 * Reads an NDEF message back from an NFC Forum Type 5 / ISO 15693 (NXP ICODE) tag
 * at the raw block level via [NfcV] — the counterpart to [NfcVNdefWriter].
 *
 * This is needed because the reference implant's Capability Container is the
 * "broken" `E1 40 FF 09` value, which makes Android's NDEF detection fail. By
 * reading the NDEF data blocks directly (block 1+) and parsing the Type 5 TLV, we
 * recover the cartridge regardless of the CC — read-only, never modifies anything.
 *
 * Returns the raw NDEF *message* bytes (to wrap in `NdefMessage`), or null.
 */
object NfcVNdefReader {

    private const val TAG = "IDOOM-NFC"
    private const val CMD_READ_SINGLE = 0x20

    private data class Profile(val label: String, val readFlags: Int, val addressed: Boolean)

    // Addressed + low data rate first: the reference ICODE implant answers it most
    // reliably (addr+hi flickers with 0x0F on marginal coupling; non-addressed times out).
    private val PROFILES = listOf(
        Profile("addr+lo", 0x20, true),
        Profile("addr+hi", 0x22, true),
        Profile("nonaddr+hi", 0x02, false),
        Profile("nonaddr+lo", 0x00, false),
    )

    fun read(nfcV: NfcV): ByteArray? {
        val uid = runCatching { nfcV.tag.id }.getOrNull()
        return try {
            nfcV.connect()

            // Find a command profile the chip answers, by reading block 0 (the CC).
            var profile: Profile? = null
            for (p in PROFILES) {
                try {
                    if (!nfcV.isConnected) nfcV.connect()
                    val resp = nfcV.transceive(buildCommand(p.readFlags, uid, 0))
                    if (resp.isNotEmpty() && (resp[0].toInt() and 0x01) == 0) {
                        profile = p
                        Log.i(TAG, "read: using profile ${p.label} (CC=[${resp.copyOfRange(1, resp.size).hex()}])")
                        break
                    }
                } catch (e: Exception) {
                    runCatching { nfcV.close() }
                }
            }
            val prof = profile ?: run {
                Log.w(TAG, "read: no profile answered")
                return null
            }
            if (!nfcV.isConnected) nfcV.connect()

            // Block 1 starts the NDEF data area (our writer puts the 0x03 TLV there).
            val first = readBlock(nfcV, 1, prof, uid) ?: return null
            val blockSize = first.size.coerceAtLeast(4)
            if ((first[0].toInt() and 0xFF) != 0x03) {
                Log.w(TAG, "read: block1 is not an NDEF TLV (starts 0x%02X)".format(first[0]))
                return null
            }

            val len: Int
            val valueStart: Int
            if ((first[1].toInt() and 0xFF) == 0xFF) {
                // 3-byte length form.
                len = ((first[2].toInt() and 0xFF) shl 8) or (first[3].toInt() and 0xFF)
                valueStart = 4
            } else {
                len = first[1].toInt() and 0xFF
                valueStart = 2
            }
            val totalNeeded = valueStart + len
            val blocksNeeded = (totalNeeded + blockSize - 1) / blockSize
            Log.i(TAG, "read: NDEF len=$len -> $blocksNeeded blocks")

            val all = ByteArray(blocksNeeded * blockSize)
            for (i in 0 until blocksNeeded) {
                val data = readBlock(nfcV, 1 + i, prof, uid) ?: return null
                data.copyInto(all, i * blockSize, 0, minOf(blockSize, data.size))
            }
            if (valueStart + len > all.size) return null
            all.copyOfRange(valueStart, valueStart + len).also {
                Log.i(TAG, "read: recovered ${it.size} NDEF bytes")
            }
        } catch (e: Exception) {
            Log.e(TAG, "read failed: ${e.javaClass.simpleName}: ${e.message}")
            null
        } finally {
            runCatching { nfcV.close() }
        }
    }

    private fun readBlock(nfcV: NfcV, block: Int, p: Profile, uid: ByteArray?): ByteArray? {
        val resp = nfcV.transceive(buildCommand(p.readFlags, if (p.addressed) uid else null, block))
        if (resp.isEmpty() || (resp[0].toInt() and 0x01) != 0) return null
        return resp.copyOfRange(1, resp.size)
    }

    private fun buildCommand(flags: Int, uid: ByteArray?, block: Int): ByteArray {
        val uidPart = uid ?: ByteArray(0)
        val cmd = ByteArray(2 + uidPart.size + 1)
        cmd[0] = flags.toByte()
        cmd[1] = CMD_READ_SINGLE.toByte()
        uidPart.copyInto(cmd, 2)
        cmd[2 + uidPart.size] = block.toByte()
        return cmd
    }

    private fun ByteArray.hex(): String = joinToString(" ") { "%02X".format(it) }
}
