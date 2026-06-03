package com.implantdoom.cartridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tests for [CartridgeCodec] encode/decode and all the documented failure modes. */
class CartridgeCodecTest {

    /** A small, fully-specified cartridge used across the round-trip tests. */
    private fun sampleCartridge(): Cartridge {
        val w = 16
        val h = 16
        val tiles = IntArray(w * h)
        // Border walls + a couple of interior features so nibbles vary.
        for (x in 0 until w) { tiles[x] = TileType.WALL; tiles[(h - 1) * w + x] = TileType.WALL }
        for (y in 0 until h) { tiles[y * w] = TileType.WALL; tiles[y * w + (w - 1)] = TileType.WALL }
        tiles[5 * w + 5] = TileType.EXIT
        tiles[6 * w + 7] = TileType.DOOR
        tiles[8 * w + 8] = TileType.HAZARD
        return Cartridge(
            flags = 0,
            playerStartX = 1,
            playerStartY = 1,
            playerStartAngle = 64,
            seed = 0xDEADBEEFL,
            textureThemeId = 2,
            tiles = tiles,
            entities = listOf(
                Entity(3, 3, EntityType.BASIC_ENEMY, 0),
                Entity(10, 4, EntityType.TURRET, 1),
                Entity(12, 12, EntityType.BOSS, 0),
            ),
            items = listOf(
                Item(2, 2, ItemType.HEALTH),
                Item(9, 9, ItemType.AMMO),
            ),
        )
    }

    @Test
    fun encodeThenDecode_roundTripsExactly() {
        val original = sampleCartridge()
        val bytes = CartridgeCodec.encode(original)
        val decoded = CartridgeCodec.decode(bytes)
        assertEquals(original, decoded)
    }

    @Test
    fun encode_producesExpectedHeaderAndSize() {
        val c = sampleCartridge()
        val bytes = CartridgeCodec.encode(c)

        // Magic "IDOOM".
        assertEquals('I'.code.toByte(), bytes[0])
        assertEquals('D'.code.toByte(), bytes[1])
        assertEquals('O'.code.toByte(), bytes[2])
        assertEquals('O'.code.toByte(), bytes[3])
        assertEquals('M'.code.toByte(), bytes[4])
        // Version, map size, counts.
        assertEquals(CartridgeCodec.VERSION.toByte(), bytes[5])
        assertEquals(16.toByte(), bytes[7])
        assertEquals(16.toByte(), bytes[8])
        assertEquals(3.toByte(), bytes[17]) // entityCount
        assertEquals(2.toByte(), bytes[18]) // itemCount
        assertEquals(0.toByte(), bytes[19]) // reserved

        // 20 header + 128 map + 3*4 entities + 2*3 items + 4 crc.
        assertEquals(20 + 128 + 12 + 6 + 4, bytes.size)
        assertEquals(bytes.size, c.encodedSize())
    }

    @Test
    fun seed_isStoredLittleEndian() {
        val c = sampleCartridge() // seed 0xDEADBEEF
        val bytes = CartridgeCodec.encode(c)
        assertEquals(0xEF.toByte(), bytes[12])
        assertEquals(0xBE.toByte(), bytes[13])
        assertEquals(0xAD.toByte(), bytes[14])
        assertEquals(0xDE.toByte(), bytes[15])
        assertEquals(0xDEADBEEFL, CartridgeCodec.decode(bytes).seed)
    }

    @Test
    fun decode_rejectsInvalidMagic() {
        val bytes = CartridgeCodec.encode(sampleCartridge())
        bytes[0] = 'X'.code.toByte()
        val ex = assertThrows(CartridgeException::class.java) { CartridgeCodec.decode(bytes) }
        assertTrue(ex.message!!.contains("magic"))
    }

    @Test
    fun decode_rejectsUnsupportedVersion() {
        val bytes = CartridgeCodec.encode(sampleCartridge())
        bytes[5] = 0x02 // bump version (checked before CRC)
        val ex = assertThrows(CartridgeException::class.java) { CartridgeCodec.decode(bytes) }
        assertTrue(ex.message!!.contains("version"))
    }

    @Test
    fun decode_rejectsTruncatedPayload() {
        val bytes = CartridgeCodec.encode(sampleCartridge())
        val truncated = bytes.copyOfRange(0, bytes.size - 1)
        assertThrows(CartridgeException::class.java) { CartridgeCodec.decode(truncated) }
    }

    @Test
    fun decode_rejectsOverLongPayload() {
        val bytes = CartridgeCodec.encode(sampleCartridge())
        val padded = bytes.copyOf(bytes.size + 1) // trailing zero byte
        assertThrows(CartridgeException::class.java) { CartridgeCodec.decode(padded) }
    }

    @Test
    fun decode_rejectsCorruptedCrc() {
        val bytes = CartridgeCodec.encode(sampleCartridge())
        // Flip a bit in the map region (not the CRC, not the header counts).
        bytes[40] = (bytes[40].toInt() xor 0xFF).toByte()
        val ex = assertThrows(CartridgeException::class.java) { CartridgeCodec.decode(bytes) }
        assertTrue(ex.message!!.contains("CRC"))
    }

    @Test
    fun decode_rejectsTooShortPayload() {
        assertThrows(CartridgeException::class.java) { CartridgeCodec.decode(ByteArray(5)) }
    }

    @Test
    fun encode_rejectsCartridgeOverMaxSize() {
        // 255 entities -> 20 + 128 + 255*4 + 4 = 1172 bytes > 1000.
        val tiles = IntArray(16 * 16)
        val many = (0 until 255).map { Entity(it % 16, it % 16, EntityType.BASIC_ENEMY) }
        val big = Cartridge(
            playerStartX = 1, playerStartY = 1, playerStartAngle = 0,
            seed = 1L, tiles = tiles, entities = many,
        )
        assertThrows(CartridgeException::class.java) { CartridgeCodec.encode(big) }
    }

    @Test
    fun isValid_reflectsCrcIntegrity() {
        val bytes = CartridgeCodec.encode(sampleCartridge())
        assertTrue(CartridgeCodec.isValid(bytes))
        bytes[30] = (bytes[30].toInt() xor 0x01).toByte()
        assertNotEquals(true, CartridgeCodec.isValid(bytes))
    }
}
