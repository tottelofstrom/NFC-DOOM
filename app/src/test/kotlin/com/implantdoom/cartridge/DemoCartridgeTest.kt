package com.implantdoom.cartridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tests the built-in demo cartridge and the seeded map generator. */
class DemoCartridgeTest {

    @Test
    fun demoCartridge_encodesToExpectedSizeUnderLimit() {
        val bytes = DemoCartridge.defaultBytes()
        // 20 header + 128 map + 8*4 entities + 8*3 items + 4 crc = 208.
        assertEquals(208, bytes.size)
        assertTrue("demo must stay under the limit", bytes.size < CartridgeCodec.MAX_SIZE_BYTES)
    }

    @Test
    fun demoCartridge_roundTrips() {
        val demo = DemoCartridge.default()
        val decoded = CartridgeCodec.decode(CartridgeCodec.encode(demo))
        assertEquals(demo, decoded)
    }

    @Test
    fun demoCartridge_hasExpectedActorCounts() {
        val demo = DemoCartridge.default()
        assertEquals(8, demo.entityCount)
        assertEquals(8, demo.itemCount)
        assertEquals(TileType.EMPTY, demo.tileAt(demo.playerStartX, demo.playerStartY))
    }

    @Test
    fun demoCartridge_isValid() {
        assertTrue(CartridgeCodec.isValid(DemoCartridge.defaultBytes()))
    }

    @Test
    fun mapGenerator_isDeterministicForSameSeed() {
        val a = MapGenerator.generate(seed = 42L)
        val b = MapGenerator.generate(seed = 42L)
        assertEquals(a, b)
    }

    @Test
    fun mapGenerator_staysUnderSizeLimit() {
        val c = MapGenerator.generate(seed = 9999L, entityCount = 16, itemCount = 16)
        val bytes = CartridgeCodec.encode(c)
        assertTrue(bytes.size <= CartridgeCodec.MAX_SIZE_BYTES)
    }

    @Test
    fun mapGenerator_clearsPlayerStartAndPlacesExit() {
        val c = MapGenerator.generate(seed = 7L)
        assertEquals(TileType.EMPTY, c.tileAt(c.playerStartX, c.playerStartY))
        val exitCount = c.tiles.count { it == TileType.EXIT }
        assertTrue("expected at least one exit tile", exitCount >= 1)
    }

    @Test
    fun mapGenerator_bordersAreWalls() {
        val c = MapGenerator.generate(seed = 3L)
        for (x in 0 until c.mapWidth) {
            assertEquals(TileType.WALL, c.tileAt(x, 0))
            assertEquals(TileType.WALL, c.tileAt(x, c.mapHeight - 1))
        }
        for (y in 0 until c.mapHeight) {
            assertEquals(TileType.WALL, c.tileAt(0, y))
            assertEquals(TileType.WALL, c.tileAt(c.mapWidth - 1, y))
        }
    }
}
