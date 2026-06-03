package com.implantdoom.cartridge

import org.junit.Assert.assertEquals
import org.junit.Test

/** Verifies the standard CRC32 used for the cartridge footer. */
class Crc32Test {

    @Test
    fun crc32_matchesStandardCheckValue() {
        // The canonical CRC-32 check value for the ASCII string "123456789".
        val data = "123456789".toByteArray(Charsets.US_ASCII)
        assertEquals(0xCBF43926L, CartridgeCodec.crc32(data, 0, data.size))
    }

    @Test
    fun crc32_ofEmptyRangeIsZero() {
        assertEquals(0L, CartridgeCodec.crc32(ByteArray(0), 0, 0))
    }

    @Test
    fun footer_isCrc32OfEverythingBefore() {
        val tiles = IntArray(16 * 16)
        val c = Cartridge(
            playerStartX = 1, playerStartY = 1, playerStartAngle = 0,
            seed = 0x01020304L, tiles = tiles,
        )
        val bytes = CartridgeCodec.encode(c)

        val expected = CartridgeCodec.crc32(bytes, 0, bytes.size - 4)
        val storedLe = (bytes[bytes.size - 4].toLong() and 0xFF) or
            ((bytes[bytes.size - 3].toLong() and 0xFF) shl 8) or
            ((bytes[bytes.size - 2].toLong() and 0xFF) shl 16) or
            ((bytes[bytes.size - 1].toLong() and 0xFF) shl 24)
        assertEquals(expected, storedLe)
    }
}
