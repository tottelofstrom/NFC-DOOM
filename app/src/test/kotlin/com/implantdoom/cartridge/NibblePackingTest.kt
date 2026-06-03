package com.implantdoom.cartridge

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.random.Random

/** Tests the 4-bit, two-cells-per-byte map packing (low nibble first). */
class NibblePackingTest {

    @Test
    fun packMap_lowNibbleIsFirstCell() {
        // Cells [1,2,3,4] -> byte0 = 0x21 (low=1, high=2), byte1 = 0x43 (low=3, high=4).
        val tiles = intArrayOf(1, 2, 3, 4)
        val packed = CartridgeCodec.packMap(tiles, 4, 1)
        assertArrayEquals(byteArrayOf(0x21, 0x43), packed)
    }

    @Test
    fun packMap_handlesOddCellCount() {
        // 3 cells -> 2 bytes, last high nibble unused (0).
        val tiles = intArrayOf(5, 6, 7)
        val packed = CartridgeCodec.packMap(tiles, 3, 1)
        assertArrayEquals(byteArrayOf(0x65, 0x07), packed)
        val unpacked = CartridgeCodec.unpackMap(packed, 0, 3, 1)
        assertArrayEquals(tiles, unpacked)
    }

    @Test
    fun packThenUnpack_roundTrips16x16() {
        val rng = Random(1234)
        val tiles = IntArray(16 * 16) { rng.nextInt(0, 16) }
        val packed = CartridgeCodec.packMap(tiles, 16, 16)
        assertEquals(128, packed.size)
        val unpacked = CartridgeCodec.unpackMap(packed, 0, 16, 16)
        assertArrayEquals(tiles, unpacked)
    }

    @Test
    fun unpackMap_honoursOffset() {
        val tiles = intArrayOf(15, 0, 1, 14)
        val packed = CartridgeCodec.packMap(tiles, 4, 1)
        val framed = ByteArray(3)
        packed.copyInto(framed, destinationOffset = 1)
        val unpacked = CartridgeCodec.unpackMap(framed, 1, 4, 1)
        assertArrayEquals(tiles, unpacked)
    }
}
