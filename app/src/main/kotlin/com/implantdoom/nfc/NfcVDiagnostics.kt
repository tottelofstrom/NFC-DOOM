package com.implantdoom.nfc

import android.nfc.tech.NfcV
import java.io.IOException

/**
 * **Read-only** ISO 15693 / NFC-V helpers for the diagnostics screen.
 *
 * These issue only non-destructive ISO 15693 commands:
 *   * `0x2B` Get System Information
 *   * `0x20` Read Single Block
 *
 * There is intentionally **no** write/lock/AFI/DSFID/password command here. The
 * first release never modifies a tag at the block level; the only block-00 note
 * we surface is the informational CC value `E1408009` (shown as text only).
 *
 * All methods assume the supplied [NfcV] is already `connect()`-ed by the caller
 * and leave closing to the caller.
 */
object NfcVDiagnostics {

    /** Most NFC-V (ICODE) tags use 4-byte blocks. Used as a fallback. */
    const val DEFAULT_BLOCK_SIZE = 4

    /**
     * Result of an ISO 15693 "Get System Information" (0x2B) request. Any field
     * may be null if the tag did not advertise it in its info flags.
     */
    data class SystemInfo(
        val dsfid: Int?,
        val afi: Int?,
        val blockCount: Int?,
        val blockSizeBytes: Int?,
        val icReference: Int?,
        val raw: ByteArray,
    )

    /** One block read attempt; exactly one of [data]/[error] is non-null. */
    data class BlockRead(
        val index: Int,
        val data: ByteArray?,
        val error: String?,
    ) {
        fun hexOrError(): String =
            data?.joinToString(" ") { "%02X".format(it) } ?: ("ERR: ${error ?: "unknown"}")
    }

    /**
     * Issue Get System Information (read-only). Returns null on tag error or a
     * malformed/short response.
     */
    fun readSystemInfo(nfcV: NfcV): SystemInfo? {
        return try {
            // Flags 0x02 = high data rate, non-addressed (single tag in field).
            val resp = nfcV.transceive(byteArrayOf(0x02, 0x2B.toByte()))
            if (resp.size < 10 || (resp[0].toInt() and 0x01) != 0) return null

            var p = 1
            val infoFlags = resp[p++].toInt() and 0xFF
            p += 8 // skip the 8-byte UID echo

            var dsfid: Int? = null
            var afi: Int? = null
            var blockCount: Int? = null
            var blockSize: Int? = null
            var icRef: Int? = null

            if (infoFlags and 0x01 != 0 && p < resp.size) dsfid = resp[p++].toInt() and 0xFF
            if (infoFlags and 0x02 != 0 && p < resp.size) afi = resp[p++].toInt() and 0xFF
            if (infoFlags and 0x04 != 0 && p + 1 < resp.size) {
                blockCount = (resp[p++].toInt() and 0xFF) + 1
                blockSize = (resp[p++].toInt() and 0xFF) + 1
            }
            if (infoFlags and 0x08 != 0 && p < resp.size) icRef = resp[p++].toInt() and 0xFF

            SystemInfo(dsfid, afi, blockCount, blockSize, icRef, resp)
        } catch (_: IOException) {
            null
        } catch (_: RuntimeException) {
            // Some stacks throw on unsupported commands.
            null
        }
    }

    /**
     * Read up to [count] single blocks starting at [start] (read-only). Stops
     * early if the tag connection drops. Never writes.
     */
    fun readBlocks(nfcV: NfcV, start: Int, count: Int): List<BlockRead> {
        val results = ArrayList<BlockRead>(count)
        for (offset in 0 until count) {
            val block = start + offset
            try {
                // 0x20 = Read Single Block, flags 0x02 = high data rate, non-addressed.
                val resp = nfcV.transceive(byteArrayOf(0x02, 0x20.toByte(), block.toByte()))
                if (resp.isEmpty() || (resp[0].toInt() and 0x01) != 0) {
                    val flags = if (resp.isEmpty()) "no response" else "flags=0x%02X".format(resp[0])
                    results.add(BlockRead(block, null, "tag error ($flags)"))
                } else {
                    results.add(BlockRead(block, resp.copyOfRange(1, resp.size), null))
                }
            } catch (e: IOException) {
                // Connection lost / out of field; stop trying further blocks.
                results.add(BlockRead(block, null, e.message ?: "IO error"))
                break
            } catch (e: RuntimeException) {
                results.add(BlockRead(block, null, e.message ?: "transceive failed"))
                break
            }
        }
        return results
    }
}
