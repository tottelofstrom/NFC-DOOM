package com.implantdoom.nfc

import android.nfc.NdefMessage
import android.nfc.TagLostException
import android.nfc.tech.NfcV
import android.util.Log
import java.io.IOException

/**
 * Writes an NDEF message to an NFC Forum Type 5 / ISO 15693 (NXP ICODE) tag at the
 * raw block level via [NfcV] — the stable default for implants, because Android's
 * high-level NDEF format/write is unreliable on Type 5 tags.
 *
 * Different ISO 15693 tags/readers need different command framing (addressed vs
 * non-addressed, high vs low data rate), so this **auto-probes** a few command
 * "profiles" by reading block 0 and then uses whichever one the chip answers.
 *
 * Safety: writes ONLY the NDEF data blocks (block 1+). Never writes block 0 (the
 * Capability Container), and never touches lock bits, AFI, DSFID, passwords or any
 * security setting. Heavily logged under "IDOOM-NFC" for live diagnosis.
 */
object NfcVNdefWriter {

    private const val TAG = "IDOOM-NFC"
    private const val CMD_READ_SINGLE = 0x20
    private const val CMD_WRITE_SINGLE = 0x21

    /**
     * A command framing profile. Flags bits: 0x02 high data rate, 0x20 addressed,
     * 0x40 option (needed by many ICODE chips for writes).
     */
    private data class Profile(
        val label: String,
        val readFlags: Int,
        val writeFlags: Int,
        val addressed: Boolean,
    )

    // Addressed + low data rate first: the reference ICODE implant answers it most
    // reliably (addr+hi flickers with 0x0F on marginal coupling; non-addressed times out).
    private val PROFILES = listOf(
        Profile("addr+lo", readFlags = 0x20, writeFlags = 0x60, addressed = true),
        Profile("addr+hi", readFlags = 0x22, writeFlags = 0x62, addressed = true),
        Profile("nonaddr+hi", readFlags = 0x02, writeFlags = 0x42, addressed = false),
        Profile("nonaddr+lo", readFlags = 0x00, writeFlags = 0x40, addressed = false),
    )

    fun write(nfcV: NfcV, message: NdefMessage): NfcWriter.Outcome {
        val ndefBytes = message.toByteArray()
        val tlv = buildNdefTlv(ndefBytes)
        val uid = runCatching { nfcV.tag.id }.getOrNull()
        Log.i(TAG, "write() begin: ndef=${ndefBytes.size} tlv=${tlv.size} uid=[${uid?.hex()}]")

        var written = 0
        var totalBlocks = 0
        return try {
            // --- Probe: find a profile the chip answers by reading block 0 (CC) ---
            var profile: Profile? = null
            var cc: ByteArray? = null
            for (p in PROFILES) {
                try {
                    if (!nfcV.isConnected) nfcV.connect()
                    // Raw read of block 0 so we can see the exact response/error code.
                    val resp = nfcV.transceive(
                        buildCommand(p.readFlags, CMD_READ_SINGLE, p.addressed, uid, byteArrayOf(0)),
                    )
                    val ok = resp.isNotEmpty() && (resp[0].toInt() and 0x01) == 0
                    Log.i(TAG, "probe ${p.label}: resp=[${resp.hex()}] ok=$ok")
                    if (ok) {
                        profile = p
                        cc = resp.copyOfRange(1, resp.size)
                        break
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "probe ${p.label}: ${e.javaClass.simpleName}: ${e.message}")
                    runCatching { nfcV.close() }
                }
            }
            val prof = profile
            val ccBytes = cc
            if (prof == null || ccBytes == null) {
                return NfcWriter.Outcome.Error(
                    "No ISO 15693 command profile worked (the chip didn't answer block 0). " +
                        "Hold the implant still on the upper-center back and retry.",
                )
            }
            Log.i(TAG, "using profile ${prof.label}; CC=[${ccBytes.hex()}]")
            if (!nfcV.isConnected) nfcV.connect()

            if ((ccBytes[0].toInt() and 0xFF) != 0xE1) {
                return NfcWriter.Outcome.Error(
                    "Implant CC byte0 = 0x%02X (expected 0xE1); not modifying the CC.".format(ccBytes[0]),
                )
            }

            // --- Geometry from the CC: block size = CC read length, capacity = MLEN*8 ---
            val blockSize = ccBytes.size.coerceIn(4, 32)
            val ccMemoryBytes = (ccBytes.getOrNull(2)?.toInt()?.and(0xFF) ?: 0) * 8
            val blockCount = if (ccMemoryBytes >= blockSize) ccMemoryBytes / blockSize else 256
            val available = (blockCount - 1) * blockSize
            Log.i(TAG, "blockSize=$blockSize blockCount=$blockCount available=$available")
            if (tlv.size > available) return NfcWriter.Outcome.TooSmall(tlv.size, available)

            val padded = tlv.copyOf(((tlv.size + blockSize - 1) / blockSize) * blockSize)
            totalBlocks = padded.size / blockSize
            Log.i(TAG, "writing $totalBlocks blocks with ${prof.label}")

            var block = 1
            var offset = 0
            while (offset < padded.size) {
                writeBlock(nfcV, block, padded.copyOfRange(offset, offset + blockSize), prof, uid)
                written++
                if (block % 10 == 0) Log.i(TAG, "...wrote $block/$totalBlocks")
                block++
                offset += blockSize
            }
            Log.i(TAG, "wrote $written/$totalBlocks; verifying")

            val check = readBlock(nfcV, 1, prof, uid)
            val firstChunk = padded.copyOfRange(0, blockSize)
            if (check == null || !check.copyOf(blockSize).contentEquals(firstChunk)) {
                Log.e(TAG, "verify mismatch got=[${check?.hex()}] want=[${firstChunk.hex()}]")
                return NfcWriter.Outcome.Error("Write verify failed (read-back mismatch). Keep the implant still and retry.")
            }
            Log.i(TAG, "verify OK -> SUCCESS ($written/$totalBlocks, profile=${prof.label})")
            NfcWriter.Outcome.Success(formatted = false, bytesWritten = ndefBytes.size, maxSize = available)
        } catch (e: TagLostException) {
            Log.e(TAG, "TAG LOST after $written/$totalBlocks", e)
            NfcWriter.Outcome.Error(
                "Implant moved — connection lost after $written/$totalBlocks blocks. " +
                    "Hold it dead still on the antenna (upper-center back) until it says Success.",
            )
        } catch (e: Exception) {
            Log.e(TAG, "write FAILED after $written/$totalBlocks: ${e.javaClass.simpleName}: ${e.message}", e)
            NfcWriter.Outcome.Error(
                "ISO 15693 write failed after $written/$totalBlocks blocks: ${e.javaClass.simpleName}: ${e.message}",
            )
        } finally {
            runCatching { nfcV.close() }
        }
    }

    /** Read one block; returns its data (without the response-flags byte), or null on a tag error flag. */
    private fun readBlock(nfcV: NfcV, block: Int, p: Profile, uid: ByteArray?): ByteArray? {
        val resp = nfcV.transceive(buildCommand(p.readFlags, CMD_READ_SINGLE, p.addressed, uid, byteArrayOf(block.toByte())))
        if (resp.isEmpty() || (resp[0].toInt() and 0x01) != 0) return null
        return resp.copyOfRange(1, resp.size)
    }

    /** Write one block; throws [IOException] on a tag error flag. */
    private fun writeBlock(nfcV: NfcV, block: Int, data: ByteArray, p: Profile, uid: ByteArray?) {
        val tail = ByteArray(1 + data.size)
        tail[0] = block.toByte()
        data.copyInto(tail, 1)
        val resp = nfcV.transceive(buildCommand(p.writeFlags, CMD_WRITE_SINGLE, p.addressed, uid, tail))
        if (resp.isNotEmpty() && (resp[0].toInt() and 0x01) != 0) {
            throw IOException("block $block error (response flags=0x%02X)".format(resp[0]))
        }
    }

    /** Build an ISO 15693 request: flags, command, [UID if addressed], then [tail]. */
    private fun buildCommand(flags: Int, command: Int, addressed: Boolean, uid: ByteArray?, tail: ByteArray): ByteArray {
        val uidPart = if (addressed && uid != null) uid else ByteArray(0)
        val cmd = ByteArray(2 + uidPart.size + tail.size)
        cmd[0] = flags.toByte()
        cmd[1] = command.toByte()
        uidPart.copyInto(cmd, 2)
        tail.copyInto(cmd, 2 + uidPart.size)
        return cmd
    }

    /** Build a Type 5 NDEF Message TLV: `0x03, length, <ndef>, 0xFE terminator`. */
    private fun buildNdefTlv(ndef: ByteArray): ByteArray {
        val out = ArrayList<Byte>(ndef.size + 4)
        out.add(0x03)
        if (ndef.size < 0xFF) {
            out.add(ndef.size.toByte())
        } else {
            out.add(0xFF.toByte())
            out.add(((ndef.size ushr 8) and 0xFF).toByte())
            out.add((ndef.size and 0xFF).toByte())
        }
        ndef.forEach { out.add(it) }
        out.add(0xFE.toByte())
        return out.toByteArray()
    }

    private fun ByteArray.hex(): String = joinToString(" ") { "%02X".format(it) }
}
