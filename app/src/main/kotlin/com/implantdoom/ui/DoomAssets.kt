package com.implantdoom.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import com.implantdoom.cartridge.EntityType
import com.implantdoom.cartridge.ItemType
import com.implantdoom.cartridge.TileType

/**
 * Loads the bundled Freedoom-derived PNGs (under assets/doomgfx) into Compose
 * [ImageBitmap]s and exposes lookups by tile/entity/item.
 *
 * The artwork is **Freedoom** (BSD-licensed, free/open) — NOT id Software's
 * copyrighted Doom assets. Attribution lives in assets/doomgfx/CREDITS.txt and
 * the About screen. The phone is the "console" that owns this art library; the
 * implant only supplies the level that selects which textures/sprites to use.
 */
class DoomAssets private constructor(
    private val images: Map<String, ImageBitmap>,
    private val floorColors: List<Color>,
    private val ceilColors: List<Color>,
) {
    fun image(name: String): ImageBitmap =
        images[name] ?: images["wall_tech"] ?: images.values.first()

    fun has(name: String): Boolean = images.containsKey(name)

    /** Wall texture for a tile, honouring door/exit/hazard specials then the theme. */
    fun wall(tileType: Int, themeId: Int): ImageBitmap = when (tileType) {
        TileType.DOOR -> image("door")
        TileType.EXIT -> image("wall_exit")
        TileType.HAZARD -> image("wall_hazard")
        else -> when (themeId) {
            1 -> image("wall_inferno")
            2 -> image("wall_cavern")
            3 -> image("wall_cryo")
            else -> image("wall_tech")
        }
    }

    fun floorColor(themeId: Int): Color = floorColors[themeId.coerceIn(0, floorColors.lastIndex)]
    fun ceilColor(themeId: Int): Color = ceilColors[themeId.coerceIn(0, ceilColors.lastIndex)]

    fun entity(type: Int): ImageBitmap = when (type) {
        EntityType.TURRET -> image("enemy_imp")
        EntityType.BOSS -> image("enemy_boss")
        else -> image("enemy_basic")
    }

    fun item(type: Int): ImageBitmap = when (type) {
        ItemType.HEALTH -> image("item_health")
        ItemType.KEY -> image("item_key")
        else -> image("item_ammo")
    }

    fun weapon(): ImageBitmap = image("weapon_pistol")
    fun statusBar(): ImageBitmap = image("stbar")
    fun digit(d: Int): ImageBitmap = image("num${d.coerceIn(0, 9)}")
    fun percent(): ImageBitmap = image("pct")

    fun face(health: Int): ImageBitmap = when {
        health <= 0 -> image("face_dead")
        health < 40 -> image("face_hurt")
        else -> image("face_neutral")
    }

    companion object {
        private val NAMES: List<String> = listOf(
            "wall_tech", "wall_tek", "wall_brown", "wall_metal", "wall_stone",
            "wall_inferno", "wall_cavern", "wall_cryo", "door", "wall_exit", "wall_hazard",
            "floor_tech", "ceil_tech", "floor_hazard", "floor_cavern", "floor_alt",
            "enemy_basic", "enemy_shotgun", "enemy_imp", "enemy_demon", "enemy_boss",
            "weapon_pistol", "weapon_shotgun",
            "item_health", "item_ammo", "item_key", "item_armor",
            "stbar", "starms", "pct", "minus",
            "face_neutral", "face_hurt", "face_god", "face_dead",
        ) + (0..9).map { "num$it" }

        fun load(context: Context): DoomAssets {
            val bitmaps = HashMap<String, Bitmap>()
            for (name in NAMES) {
                runCatching {
                    context.assets.open("doomgfx/$name.png").use { stream ->
                        BitmapFactory.decodeStream(stream)?.let { bitmaps[name] = it }
                    }
                }
            }
            val images = bitmaps.mapValues { it.value.asImageBitmap() }

            // Floor/ceiling are flat-shaded with a colour sampled from the matching flat.
            val floorByTheme = listOf("floor_tech", "floor_hazard", "floor_cavern", "floor_alt")
            val floors = floorByTheme.map { averageColor(bitmaps[it]) }
            val ceils = floors.map { darken(it, 0.45f) }

            return DoomAssets(images, floors, ceils)
        }

        /** Average colour of a bitmap, sampled on a coarse grid (cheap, at load time). */
        private fun averageColor(bm: Bitmap?): Color {
            if (bm == null) return Color(0xFF2A2A2A)
            var r = 0L; var g = 0L; var b = 0L; var n = 0L
            val stepX = (bm.width / 8).coerceAtLeast(1)
            val stepY = (bm.height / 8).coerceAtLeast(1)
            var y = 0
            while (y < bm.height) {
                var x = 0
                while (x < bm.width) {
                    val c = bm.getPixel(x, y)
                    r += (c shr 16) and 0xFF
                    g += (c shr 8) and 0xFF
                    b += c and 0xFF
                    n++
                    x += stepX
                }
                y += stepY
            }
            if (n == 0L) return Color(0xFF2A2A2A)
            return Color((r / n).toInt(), (g / n).toInt(), (b / n).toInt())
        }

        private fun darken(c: Color, f: Float): Color =
            Color(c.red * f, c.green * f, c.blue * f, 1f)
    }
}

/** Loads (once) and remembers the Doom asset library for the current Context. */
@Composable
fun rememberDoomAssets(): DoomAssets {
    val context = LocalContext.current
    return remember { DoomAssets.load(context) }
}
