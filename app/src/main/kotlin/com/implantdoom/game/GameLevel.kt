package com.implantdoom.game

import com.implantdoom.cartridge.Cartridge
import com.implantdoom.cartridge.Entity
import com.implantdoom.cartridge.Item
import com.implantdoom.cartridge.TileType

/**
 * Mutable runtime state for a level, built from an immutable [Cartridge].
 *
 * The cartridge only *initialises* the level (tiles + actor placement). All
 * gameplay state (which enemies are dead, which items are collected, opened
 * doors) lives here on the phone and is never written back to the implant.
 */
class GameLevel(val cartridge: Cartridge) {

    val width: Int = cartridge.mapWidth
    val height: Int = cartridge.mapHeight

    /** A private copy so opening doors etc. never mutates the source cartridge. */
    private val tiles: IntArray = cartridge.tiles.copyOf()

    val entities: List<EntityState> = cartridge.entities.map { EntityState(it) }
    val items: List<ItemState> = cartridge.items.map { ItemState(it) }

    fun tileAt(x: Int, y: Int): Int {
        if (x < 0 || y < 0 || x >= width || y >= height) return TileType.WALL
        return tiles[y * width + x]
    }

    fun setTile(x: Int, y: Int, value: Int) {
        if (x in 0 until width && y in 0 until height) tiles[y * width + x] = value
    }

    /** True if the cell blocks movement and rays (out-of-bounds counts as solid). */
    fun isSolid(x: Int, y: Int): Boolean = TileType.isSolid(tileAt(x, y))
}

/** Live state of one placed entity; starts at the tile centre and may roam. */
class EntityState(val def: Entity) {
    var x: Double = def.x + 0.5
    var y: Double = def.y + 0.5
    var alive: Boolean = true
}

/** Live state of one placed item; positioned at the tile centre. */
class ItemState(val def: Item) {
    val x: Double = def.x + 0.5
    val y: Double = def.y + 0.5
    var collected: Boolean = false
}
