package com.implantdoom.game

import com.implantdoom.cartridge.TileType
import kotlin.math.abs
import kotlin.math.floor

/**
 * A classic grid DDA raycaster (the Wolfenstein 3D / "lodev tutorial" technique).
 *
 * It is pure math with no Android/Compose dependencies so it can be reasoned
 * about and tested independently of rendering. [PlayScreen] drives it once per
 * screen column and paints the resulting wall slices.
 */
class Raycaster(private val level: GameLevel) {

    /** Result of casting a single ray until it hits a solid cell. */
    data class Hit(
        /** Perpendicular distance to the wall (avoids the classic fish-eye warp). */
        val perpDist: Double,
        /** Tile id that was hit (wall/door/...). */
        val tileType: Int,
        /** 0 if a vertical (E/W) wall face was hit, 1 if horizontal (N/S). */
        val side: Int,
        /** Where along the wall face the ray hit, 0..1 (for vertical texturing). */
        val wallX: Double,
        /** Map cell that was hit. */
        val mapX: Int,
        val mapY: Int,
    )

    /**
     * Cast a ray from ([posX],[posY]) in direction ([rayDirX],[rayDirY]).
     * Out-of-bounds is treated as a solid wall so rays always terminate.
     */
    fun cast(posX: Double, posY: Double, rayDirX: Double, rayDirY: Double): Hit {
        var mapX = floor(posX).toInt()
        var mapY = floor(posY).toInt()

        val deltaDistX = if (rayDirX == 0.0) 1e30 else abs(1.0 / rayDirX)
        val deltaDistY = if (rayDirY == 0.0) 1e30 else abs(1.0 / rayDirY)

        val stepX: Int
        var sideDistX: Double
        if (rayDirX < 0) {
            stepX = -1
            sideDistX = (posX - mapX) * deltaDistX
        } else {
            stepX = 1
            sideDistX = (mapX + 1.0 - posX) * deltaDistX
        }

        val stepY: Int
        var sideDistY: Double
        if (rayDirY < 0) {
            stepY = -1
            sideDistY = (posY - mapY) * deltaDistY
        } else {
            stepY = 1
            sideDistY = (mapY + 1.0 - posY) * deltaDistY
        }

        var side = 0
        var tile = TileType.EMPTY
        // Cap iterations so a degenerate ray can never spin forever.
        val maxSteps = (level.width + level.height) * 2 + 4
        var steps = 0
        while (steps++ < maxSteps) {
            if (sideDistX < sideDistY) {
                sideDistX += deltaDistX
                mapX += stepX
                side = 0
            } else {
                sideDistY += deltaDistY
                mapY += stepY
                side = 1
            }
            tile = level.tileAt(mapX, mapY)
            if (TileType.isSolid(tile)) break
        }

        val perpDist = if (side == 0) {
            (mapX - posX + (1 - stepX) / 2.0) / rayDirX
        } else {
            (mapY - posY + (1 - stepY) / 2.0) / rayDirY
        }.let { if (it <= 0.0) 1e-4 else it }

        val wallXRaw = if (side == 0) posY + perpDist * rayDirY else posX + perpDist * rayDirX
        val wallX = wallXRaw - floor(wallXRaw)

        return Hit(perpDist, tile, side, wallX, mapX, mapY)
    }

    /**
     * Line-of-sight test used by shooting: steps from ([fromX],[fromY]) toward
     * ([toX],[toY]) and returns false if a solid wall is in the way.
     */
    fun hasLineOfSight(fromX: Double, fromY: Double, toX: Double, toY: Double): Boolean {
        val dx = toX - fromX
        val dy = toY - fromY
        val dist = kotlin.math.hypot(dx, dy)
        if (dist < 1e-6) return true
        val stepCount = (dist / 0.05).toInt().coerceAtLeast(1)
        val sx = dx / stepCount
        val sy = dy / stepCount
        var x = fromX
        var y = fromY
        for (i in 0 until stepCount) {
            x += sx
            y += sy
            if (level.isSolid(floor(x).toInt(), floor(y).toInt())) return false
        }
        return true
    }
}
