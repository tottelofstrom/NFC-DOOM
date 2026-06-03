package com.implantdoom.cartridge

import java.util.Random
import kotlin.math.abs

/**
 * Built-in cartridges so the app is fully usable on an emulator or any phone
 * without ever touching NFC. [DemoCartridge.default] is a hand-authored, fully
 * deterministic level; [MapGenerator.generate] produces seeded random levels for
 * the in-app builder.
 *
 * None of this content is derived from id Software's Doom; the layout, tiles and
 * placeholder actors are original to this project.
 */
object DemoCartridge {

    /**
     * The canonical demo level: a 16x16 tech-base style map with sparse pillars,
     * an exit, 8 placeholder enemies and 8 pickups. Encodes to 208 bytes.
     */
    fun default(): Cartridge {
        // '#' wall, '.' empty, 'E' exit. Player starts at (1,1).
        val layout = listOf(
            "################",
            "#..............#",
            "#..##.....###..#",
            "#..##.....#....#",
            "#.........#....#",
            "#....####......#",
            "#....#.....##..#",
            "#....#.....##..#",
            "#....###.......#",
            "#..............#",
            "#..##.....####.#",
            "#..##..........#",
            "#.........##...#",
            "#...####..##...#",
            "#...#.....E....#",
            "################",
        )
        val tiles = parseLayout(layout, CartridgeCodec.DEFAULT_MAP_WIDTH, CartridgeCodec.DEFAULT_MAP_HEIGHT)

        val entities = listOf(
            Entity(7, 2, EntityType.BASIC_ENEMY),
            Entity(12, 4, EntityType.BASIC_ENEMY),
            Entity(2, 6, EntityType.TURRET),
            Entity(13, 7, EntityType.BASIC_ENEMY),
            Entity(8, 9, EntityType.TURRET),
            Entity(6, 11, EntityType.BASIC_ENEMY),
            Entity(13, 12, EntityType.BOSS),
            Entity(7, 14, EntityType.BASIC_ENEMY),
        )
        val items = listOf(
            Item(5, 1, ItemType.HEALTH),
            Item(11, 3, ItemType.AMMO),
            Item(2, 4, ItemType.HEALTH),
            Item(9, 5, ItemType.AMMO),
            Item(2, 8, ItemType.KEY),
            Item(11, 9, ItemType.AMMO),
            Item(2, 12, ItemType.HEALTH),
            Item(13, 14, ItemType.KEY),
        )

        return Cartridge(
            playerStartX = 1,
            playerStartY = 1,
            playerStartAngle = 0, // facing East, into the open corridor
            seed = 0x1D00D000L,
            textureThemeId = 0,
            tiles = tiles,
            entities = entities,
            items = items,
        )
    }

    /** The demo cartridge already serialised, ready to hand to the NFC writer. */
    fun defaultBytes(): ByteArray = CartridgeCodec.encode(default())

    /** Parse a list of equal-length ASCII rows into a row-major tile array. */
    private fun parseLayout(rows: List<String>, width: Int, height: Int): IntArray {
        require(rows.size == height) { "layout has ${rows.size} rows, expected $height" }
        val tiles = IntArray(width * height)
        for (y in 0 until height) {
            val row = rows[y]
            require(row.length == width) { "row $y has length ${row.length}, expected $width" }
            for (x in 0 until width) {
                tiles[y * width + x] = when (row[x]) {
                    '#' -> TileType.WALL
                    '.' -> TileType.EMPTY
                    'D' -> TileType.DOOR
                    'E' -> TileType.EXIT
                    'H' -> TileType.HAZARD
                    else -> TileType.EMPTY
                }
            }
        }
        return tiles
    }
}

/**
 * Generates seeded random cartridges for the builder. Levels are bordered by
 * walls, have sparse interior walls, a guaranteed clear player start and an exit
 * placed at the farthest reachable-by-distance empty cell. Entities and items are
 * scattered over the remaining empty cells.
 *
 * The same [seed] (and same parameters) always produces the same level.
 */
object MapGenerator {

    fun generate(
        seed: Long,
        mapWidth: Int = CartridgeCodec.DEFAULT_MAP_WIDTH,
        mapHeight: Int = CartridgeCodec.DEFAULT_MAP_HEIGHT,
        wallDensity: Double = 0.18,
        entityCount: Int = 8,
        itemCount: Int = 8,
        playerStartX: Int = 1,
        playerStartY: Int = 1,
        playerStartAngle: Int = 0,
        textureThemeId: Int = 0,
    ): Cartridge {
        val w = mapWidth
        val h = mapHeight
        val rnd = Random(seed)
        val tiles = IntArray(w * h)
        fun idx(x: Int, y: Int) = y * w + x

        // Solid border.
        for (x in 0 until w) {
            tiles[idx(x, 0)] = TileType.WALL
            tiles[idx(x, h - 1)] = TileType.WALL
        }
        for (y in 0 until h) {
            tiles[idx(0, y)] = TileType.WALL
            tiles[idx(w - 1, y)] = TileType.WALL
        }

        // Sparse interior walls.
        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                tiles[idx(x, y)] = if (rnd.nextDouble() < wallDensity) TileType.WALL else TileType.EMPTY
            }
        }

        // Guarantee a clear 3x3 around the player start.
        val psx = playerStartX.coerceIn(1, w - 2)
        val psy = playerStartY.coerceIn(1, h - 2)
        for (dy in -1..1) {
            for (dx in -1..1) {
                val nx = psx + dx
                val ny = psy + dy
                if (nx in 1 until w - 1 && ny in 1 until h - 1) tiles[idx(nx, ny)] = TileType.EMPTY
            }
        }

        // Exit at the farthest (Manhattan) empty cell from the start.
        var exitIndex = -1
        var bestDist = -1
        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                if (tiles[idx(x, y)] == TileType.EMPTY) {
                    val d = abs(x - psx) + abs(y - psy)
                    if (d > bestDist) {
                        bestDist = d
                        exitIndex = idx(x, y)
                    }
                }
            }
        }
        if (exitIndex >= 0) tiles[exitIndex] = TileType.EXIT

        // Collect free cells (empty, not the start, not the exit) and shuffle.
        val startIdx = idx(psx, psy)
        val free = ArrayList<Int>()
        for (i in tiles.indices) {
            if (tiles[i] == TileType.EMPTY && i != startIdx) free.add(i)
        }
        // Deterministic Fisher–Yates using the seeded java.util.Random.
        for (i in free.indices.reversed()) {
            val j = rnd.nextInt(i + 1)
            val tmp = free[i]
            free[i] = free[j]
            free[j] = tmp
        }

        // Respect the global size budget.
        val maxEnt = CartridgeCodec.maxEntities(itemCount, w, h)
        val wantEntities = entityCount.coerceIn(0, maxEnt)
        var cursor = 0

        val entityTypes = intArrayOf(
            EntityType.BASIC_ENEMY, EntityType.BASIC_ENEMY, EntityType.BASIC_ENEMY,
            EntityType.TURRET, EntityType.BOSS,
        )
        val entities = ArrayList<Entity>(wantEntities)
        while (entities.size < wantEntities && cursor < free.size) {
            val cell = free[cursor++]
            entities.add(Entity(cell % w, cell / w, entityTypes[rnd.nextInt(entityTypes.size)]))
        }

        val itemTypes = intArrayOf(ItemType.HEALTH, ItemType.AMMO, ItemType.AMMO, ItemType.KEY)
        val items = ArrayList<Item>(itemCount)
        while (items.size < itemCount && cursor < free.size) {
            val cell = free[cursor++]
            items.add(Item(cell % w, cell / w, itemTypes[rnd.nextInt(itemTypes.size)]))
        }

        return Cartridge(
            playerStartX = psx,
            playerStartY = psy,
            playerStartAngle = playerStartAngle,
            seed = seed and 0xFFFFFFFFL,
            textureThemeId = textureThemeId,
            tiles = tiles,
            entities = entities,
            items = items,
        )
    }
}
