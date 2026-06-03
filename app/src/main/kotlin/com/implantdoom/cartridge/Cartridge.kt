package com.implantdoom.cartridge

/**
 * ImplantDoom cartridge model (format v1).
 *
 * The NFC implant is *not* the computer: it is passive storage for a tiny binary
 * "cartridge" that initialises a level. The phone runs the game. This file holds
 * the in-memory representation; [CartridgeCodec] converts to/from the on-tag bytes.
 *
 * See [CartridgeCodec] for the exact byte layout. All multi-byte integers in the
 * wire format are little-endian.
 */

/** Tile identifiers stored in the 4-bit map cells. */
object TileType {
    const val EMPTY = 0
    const val WALL = 1
    const val DOOR = 2
    const val EXIT = 3
    const val HAZARD = 4
    // 5..15 are reserved / theme-specific for future versions.

    /** True when [id] blocks movement / a ray in the basic raycaster. */
    fun isSolid(id: Int): Boolean = id == WALL || id == DOOR
}

/** Entity (dynamic actor) type identifiers. */
object EntityType {
    const val BASIC_ENEMY = 1
    const val TURRET = 2
    const val BOSS = 3
}

/** Item (pickup) type identifiers. */
object ItemType {
    const val HEALTH = 1
    const val KEY = 2
    const val AMMO = 3
}

/**
 * A dynamic actor placed on the map. Serialises to exactly 4 bytes:
 * `x, y, type, flags`.
 */
data class Entity(
    val x: Int,
    val y: Int,
    val type: Int,
    val flags: Int = 0,
)

/**
 * A pickup placed on the map. Serialises to exactly 3 bytes: `x, y, type`.
 */
data class Item(
    val x: Int,
    val y: Int,
    val type: Int,
)

/**
 * A fully-parsed cartridge.
 *
 * [tiles] is row-major and has exactly `mapWidth * mapHeight` entries, each a
 * value in `0..15` (a [TileType]). [entities] and [items] are capped on encode so
 * the whole cartridge stays under [CartridgeCodec.MAX_SIZE_BYTES].
 *
 * Note: this is a `data class` so callers get `copy()` for the builder UI, but
 * [equals]/[hashCode] are overridden because the default generated versions would
 * compare the [tiles] IntArray by reference rather than by content.
 */
data class Cartridge(
    val version: Int = CartridgeCodec.VERSION,
    val flags: Int = 0,
    val mapWidth: Int = CartridgeCodec.DEFAULT_MAP_WIDTH,
    val mapHeight: Int = CartridgeCodec.DEFAULT_MAP_HEIGHT,
    val playerStartX: Int,
    val playerStartY: Int,
    /** Player facing, 0..255 where 0=East, 64=South, 128=West, 192=North. */
    val playerStartAngle: Int,
    /** uint32 seed, stored in a Long so the top bit is never interpreted as a sign. */
    val seed: Long,
    val textureThemeId: Int = 0,
    val tiles: IntArray,
    val entities: List<Entity> = emptyList(),
    val items: List<Item> = emptyList(),
) {
    val entityCount: Int get() = entities.size
    val itemCount: Int get() = items.size

    /** Tile at [x],[y] (no bounds checking; callers must stay in range). */
    fun tileAt(x: Int, y: Int): Int = tiles[y * mapWidth + x]

    fun isInBounds(x: Int, y: Int): Boolean =
        x in 0 until mapWidth && y in 0 until mapHeight

    /** Player facing converted to radians for the raycaster (screen Y points down). */
    fun playerStartAngleRadians(): Double =
        playerStartAngle / 256.0 * (2.0 * Math.PI)

    /** Stable, human-friendly name derived from the seed (the format stores no name string). */
    fun displayName(): String = "Cartridge %08X".format(seed and 0xFFFFFFFFL)

    fun themeName(): String = when (textureThemeId) {
        0 -> "Tech Base"
        1 -> "Inferno"
        2 -> "Cavern"
        3 -> "Cryo"
        else -> "Theme $textureThemeId"
    }

    /** Serialised size in bytes for the current contents (header + map + actors + CRC). */
    fun encodedSize(): Int = CartridgeCodec.encodedSize(this)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Cartridge) return false
        return version == other.version &&
            flags == other.flags &&
            mapWidth == other.mapWidth &&
            mapHeight == other.mapHeight &&
            playerStartX == other.playerStartX &&
            playerStartY == other.playerStartY &&
            playerStartAngle == other.playerStartAngle &&
            seed == other.seed &&
            textureThemeId == other.textureThemeId &&
            tiles.contentEquals(other.tiles) &&
            entities == other.entities &&
            items == other.items
    }

    override fun hashCode(): Int {
        var result = version
        result = 31 * result + flags
        result = 31 * result + mapWidth
        result = 31 * result + mapHeight
        result = 31 * result + playerStartX
        result = 31 * result + playerStartY
        result = 31 * result + playerStartAngle
        result = 31 * result + seed.hashCode()
        result = 31 * result + textureThemeId
        result = 31 * result + tiles.contentHashCode()
        result = 31 * result + entities.hashCode()
        result = 31 * result + items.hashCode()
        return result
    }
}
