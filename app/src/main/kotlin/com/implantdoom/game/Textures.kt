package com.implantdoom.game

import com.implantdoom.cartridge.EntityType
import com.implantdoom.cartridge.ItemType
import com.implantdoom.cartridge.TileType

/**
 * Procedurally generated colours for the raycaster.
 *
 * Everything here is an original, generated palette: flat colours with side
 * shading and distance fog. There are NO imported textures, sprites or assets
 * from Doom or any other game. Returned values are 0xAARRGGBB ints (paired with
 * Compose's `Color(Int)` at the call site).
 */
object Textures {

    /** Theme-specific wall base colour (before side/fog shading). */
    fun wallColor(themeId: Int, tileType: Int): Int = when (tileType) {
        TileType.DOOR -> 0xFFB9883E.toInt()      // amber door
        TileType.EXIT -> 0xFF36E07A.toInt()      // green exit marker
        TileType.HAZARD -> 0xFFB23A2E.toInt()    // red hazard wall
        else -> when (themeId) {                 // plain wall, per theme
            1 -> 0xFF7A2B2B.toInt()              // Inferno
            2 -> 0xFF5A5347.toInt()              // Cavern
            3 -> 0xFF3F6E86.toInt()              // Cryo
            else -> 0xFF566077.toInt()           // Tech Base
        }
    }

    fun ceilingColor(themeId: Int): Int = when (themeId) {
        1 -> 0xFF2A1414.toInt()
        2 -> 0xFF26231C.toInt()
        3 -> 0xFF16242B.toInt()
        else -> 0xFF1B2030.toInt()
    }

    fun floorColor(themeId: Int): Int = when (themeId) {
        1 -> 0xFF3C2418.toInt()
        2 -> 0xFF302925.toInt()
        3 -> 0xFF20323A.toInt()
        else -> 0xFF2A2F3A.toInt()
    }

    fun entityColor(type: Int): Int = when (type) {
        EntityType.BASIC_ENEMY -> 0xFFE0483A.toInt()
        EntityType.TURRET -> 0xFFE0A23A.toInt()
        EntityType.BOSS -> 0xFFC23AE0.toInt()
        else -> 0xFFE0E0E0.toInt()
    }

    fun itemColor(type: Int): Int = when (type) {
        ItemType.HEALTH -> 0xFF3AE07A.toInt()
        ItemType.KEY -> 0xFFE0D23A.toInt()
        ItemType.AMMO -> 0xFF8AB4F8.toInt()
        else -> 0xFFFFFFFF.toInt()
    }

    /**
     * Shade an ARGB colour by [factor] (0 = black, 1 = unchanged). Used for the
     * vertical/horizontal wall-face distinction and distance fog.
     */
    fun shade(color: Int, factor: Double): Int {
        val f = factor.coerceIn(0.0, 1.0)
        val a = (color ushr 24) and 0xFF
        val r = ((color ushr 16) and 0xFF) * f
        val g = ((color ushr 8) and 0xFF) * f
        val b = (color and 0xFF) * f
        return (a shl 24) or (r.toInt() shl 16) or (g.toInt() shl 8) or b.toInt()
    }

    /**
     * A cheap "brick" effect without textures: darken the thin mortar bands near
     * tile-face edges so flat walls read as masonry. [wallX] is 0..1 along the face.
     */
    fun brickShade(wallX: Double): Double {
        val edge = 0.06
        return if (wallX < edge || wallX > 1.0 - edge) 0.78 else 1.0
    }
}
