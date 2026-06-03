package com.implantdoom.game

import com.implantdoom.cartridge.Cartridge
import com.implantdoom.cartridge.EntityType
import com.implantdoom.cartridge.ItemType
import com.implantdoom.cartridge.TileType
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.sin

/**
 * All gameplay state for a running level, plus the per-frame [update] step.
 *
 * Runs entirely on the phone. The cartridge initialises it; nothing here is ever
 * written back to NFC. Pure Kotlin (no Android types) so the simulation can be
 * unit-tested headlessly.
 */
class GameState(cartridge: Cartridge) {

    enum class Status { PLAYING, LEVEL_COMPLETE, DEAD }

    val level = GameLevel(cartridge)
    val raycaster = Raycaster(level)
    val cartridgeName: String = cartridge.displayName()
    val cartridgeVersion: Int = cartridge.version

    // Player pose (map units; tile centres are *.5).
    var posX: Double = cartridge.playerStartX + 0.5
        private set
    var posY: Double = cartridge.playerStartY + 0.5
        private set
    var angle: Double = cartridge.playerStartAngleRadians()
        private set

    // Vitals.
    var health: Int = 100
        private set
    var ammo: Int = 50
        private set
    var armor: Int = 0
        private set
    var keys: Int = 0
        private set
    var status: Status = Status.PLAYING
        private set

    // Smoothed FPS for the HUD.
    var fps: Double = 0.0
        private set

    /** A short, fading message for pickups / events shown on the HUD. */
    var banner: String = ""
        private set
    private var bannerTimer = 0.0

    /** Camera plane half-width; tan(FOV/2). 0.66 ~= 66-degree field of view. */
    val planeScale = 0.66

    val dirX: Double get() = cos(angle)
    val dirY: Double get() = sin(angle)
    val planeX: Double get() = -sin(angle) * planeScale
    val planeY: Double get() = cos(angle) * planeScale

    // --- Input intents (set by the on-screen controls) ---
    var moveForward = false
    var moveBackward = false
    var turnLeft = false
    var turnRight = false
    var strafeLeft = false
    var strafeRight = false

    private var fireQueued = false

    /** Queue a shot to be resolved on the next [update]. */
    fun requestFire() { fireQueued = true }

    /**
     * Advance the simulation by [dt] seconds (clamped to avoid huge jumps after
     * a pause). Safe to call every frame.
     */
    fun update(dt: Double) {
        if (status != Status.PLAYING) {
            // Still update FPS so the HUD stays live on the end screen.
            updateFps(dt)
            return
        }
        val step = dt.coerceIn(0.0, 0.05)

        // Rotation.
        if (turnLeft) angle -= ROT_SPEED * step
        if (turnRight) angle += ROT_SPEED * step
        angle = wrapAngle(angle)

        // Translation (forward/back along facing, strafe along camera plane).
        var moveX = 0.0
        var moveY = 0.0
        if (moveForward) { moveX += dirX; moveY += dirY }
        if (moveBackward) { moveX -= dirX; moveY -= dirY }
        if (strafeLeft) { moveX += -planeX / planeScale; moveY += -planeY / planeScale }
        if (strafeRight) { moveX += planeX / planeScale; moveY += planeY / planeScale }

        val len = hypot(moveX, moveY)
        if (len > 1e-6) {
            moveX = moveX / len * MOVE_SPEED * step
            moveY = moveY / len * MOVE_SPEED * step
            // Per-axis so the player slides along walls instead of sticking.
            if (!collides(posX + moveX, posY)) posX += moveX
            if (!collides(posX, posY + moveY)) posY += moveY
        }

        if (fireQueued) {
            fireQueued = false
            resolveFire()
        }

        updateEnemies(step)
        resolvePickups()
        resolveEnemyContact(step)
        resolveTileEffects(step)

        if (health <= 0) {
            health = 0
            status = Status.DEAD
        }

        tickBanner(step)
        updateFps(dt)
    }

    /** Box collision against solid tiles using a small player radius. */
    private fun collides(x: Double, y: Double): Boolean {
        val r = PLAYER_RADIUS
        return level.isSolid(floor(x - r).toInt(), floor(y - r).toInt()) ||
            level.isSolid(floor(x + r).toInt(), floor(y - r).toInt()) ||
            level.isSolid(floor(x - r).toInt(), floor(y + r).toInt()) ||
            level.isSolid(floor(x + r).toInt(), floor(y + r).toInt())
    }

    private fun resolvePickups() {
        for (item in level.items) {
            if (item.collected) continue
            if (hypot(item.x - posX, item.y - posY) < PICKUP_RADIUS) {
                item.collected = true
                when (item.def.type) {
                    ItemType.HEALTH -> { health = (health + 25).coerceAtMost(100); showBanner("+25 health") }
                    ItemType.AMMO -> { ammo += 10; showBanner("+10 ammo") }
                    ItemType.KEY -> { keys += 1; showBanner("Key acquired") }
                    else -> showBanner("Picked up item")
                }
            }
        }
    }

    /**
     * Simple chase AI: living, non-turret enemies step toward the player when in
     * range and in line of sight, sliding along walls (per-axis collision).
     */
    private fun updateEnemies(step: Double) {
        for (e in level.entities) {
            if (!e.alive || e.def.type == EntityType.TURRET) continue
            val dx = posX - e.x
            val dy = posY - e.y
            val dist = hypot(dx, dy)
            if (dist < 0.45 || dist > ENEMY_AGGRO_RANGE) continue
            if (!raycaster.hasLineOfSight(e.x, e.y, posX, posY)) continue

            val speed = (if (e.def.type == EntityType.BOSS) ENEMY_SPEED * 0.75 else ENEMY_SPEED) * step
            val nx = e.x + dx / dist * speed
            val ny = e.y + dy / dist * speed
            val r = ENEMY_RADIUS
            if (!level.isSolid(floor(nx - r).toInt(), floor(e.y).toInt()) &&
                !level.isSolid(floor(nx + r).toInt(), floor(e.y).toInt())
            ) {
                e.x = nx
            }
            if (!level.isSolid(floor(e.x).toInt(), floor(ny - r).toInt()) &&
                !level.isSolid(floor(e.x).toInt(), floor(ny + r).toInt())
            ) {
                e.y = ny
            }
        }
    }

    private fun resolveEnemyContact(step: Double) {
        for (e in level.entities) {
            if (!e.alive) continue
            val d = hypot(e.x - posX, e.y - posY)
            if (d < ENEMY_TOUCH_RADIUS) {
                val dps = if (e.def.type == EntityType.BOSS) BOSS_DPS else ENEMY_DPS
                health -= (dps * step).toInt().coerceAtLeast(0)
                // Apply at least 1 damage on contact occasionally even if rounding to 0.
                if ((dps * step) > 0 && (dps * step) < 1) health -= 1
            }
        }
    }

    private fun resolveTileEffects(step: Double) {
        val tx = floor(posX).toInt()
        val ty = floor(posY).toInt()
        when (level.tileAt(tx, ty)) {
            TileType.EXIT -> { status = Status.LEVEL_COMPLETE; showBanner("Level complete!") }
            TileType.HAZARD -> {
                val dmg = HAZARD_DPS * step
                health -= dmg.toInt()
                if (dmg > 0 && dmg < 1) health -= 1
            }
        }
    }

    /** Resolve a shot: kill the nearest live enemy inside a forward cone with line-of-sight. */
    private fun resolveFire() {
        if (ammo <= 0) { showBanner("No ammo"); return }
        ammo -= 1

        var bestIndex = -1
        var bestDist = Double.MAX_VALUE
        for ((i, e) in level.entities.withIndex()) {
            if (!e.alive) continue
            val dx = e.x - posX
            val dy = e.y - posY
            val dist = hypot(dx, dy)
            if (dist > FIRE_RANGE) continue
            // Angle between facing and the enemy.
            val enemyAngle = atan2(dy, dx)
            val diff = abs(wrapAngle(enemyAngle - angle))
            if (diff > FIRE_CONE_HALF) continue
            if (!raycaster.hasLineOfSight(posX, posY, e.x, e.y)) continue
            if (dist < bestDist) { bestDist = dist; bestIndex = i }
        }
        if (bestIndex >= 0) {
            level.entities[bestIndex].alive = false
            showBanner("Target down")
        } else {
            showBanner("Miss")
        }
    }

    fun aliveEnemyCount(): Int = level.entities.count { it.alive }
    fun remainingItemCount(): Int = level.items.count { !it.collected }

    private fun showBanner(text: String) { banner = text; bannerTimer = BANNER_SECONDS }
    private fun tickBanner(step: Double) {
        if (bannerTimer > 0) {
            bannerTimer -= step
            if (bannerTimer <= 0) banner = ""
        }
    }

    private fun updateFps(dt: Double) {
        if (dt > 0) {
            val instant = 1.0 / dt
            fps = if (fps == 0.0) instant else fps * 0.9 + instant * 0.1
        }
    }

    private fun wrapAngle(a: Double): Double {
        var r = a
        while (r > PI) r -= 2 * PI
        while (r < -PI) r += 2 * PI
        return r
    }

    companion object {
        const val MOVE_SPEED = 3.0      // tiles / second
        const val ROT_SPEED = 2.6       // radians / second
        const val PLAYER_RADIUS = 0.2
        const val PICKUP_RADIUS = 0.5
        const val ENEMY_TOUCH_RADIUS = 0.7
        const val ENEMY_SPEED = 1.3          // tiles / second (player is 3.0)
        const val ENEMY_AGGRO_RANGE = 11.0   // tiles
        const val ENEMY_RADIUS = 0.25
        const val ENEMY_DPS = 12.0
        const val BOSS_DPS = 25.0
        const val HAZARD_DPS = 20.0
        const val FIRE_RANGE = 12.0
        const val FIRE_CONE_HALF = 0.22  // radians (~12.5 deg) half-cone
        const val BANNER_SECONDS = 1.5
    }
}
