package com.implantdoom.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.implantdoom.cartridge.EntityType
import com.implantdoom.game.GameState
import com.implantdoom.ui.AppViewModel
import com.implantdoom.ui.DoomAssets
import com.implantdoom.ui.HoldButton
import com.implantdoom.ui.ScreenScaffold
import com.implantdoom.ui.rememberDoomAssets
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Internal framebuffer width in pixels. The whole 3D view is rendered at this
 * tiny resolution and then upscaled with nearest-neighbour filtering, giving big
 * chunky "Doom on a huge screen" pixels. Lower = chunkier.
 */
private const val RENDER_WIDTH = 128

/**
 * Play screen: a textured first-person raycaster rendered with Freedoom art
 * (BSD-licensed), with billboarded sprite monsters, a weapon, and the iconic
 * Doom-style status bar. The level (map/monsters/items) comes from the cartridge
 * on the implant; the engine + art library live on the phone.
 */
@Composable
fun PlayScreen(viewModel: AppViewModel, navController: NavHostController) {
    val cartridge by viewModel.activeCartridge.collectAsState()
    val c = cartridge

    if (c == null) {
        ScreenScaffold(title = "Play", navController = navController) {
            Column(Modifier.fillMaxWidth().padding(24.dp)) {
                Text("No cartridge loaded.")
                Spacer(Modifier.height(12.dp))
                Button(onClick = { viewModel.loadDemoCartridge(navigate = false) }) {
                    Text("Load demo cartridge")
                }
            }
        }
        return
    }

    // Lock to landscape while the game is on screen (Doom is widescreen); restore on exit.
    val context = LocalContext.current
    DisposableEffect(Unit) {
        val activity = context.findActivity()
        val previousOrientation = activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        onDispose { activity?.requestedOrientation = previousOrientation }
    }

    val assets = rememberDoomAssets()
    var runId by remember { mutableIntStateOf(0) }
    val game = remember(c, runId) { GameState(c) }
    var frame by remember { mutableLongStateOf(0L) }

    LaunchedEffect(game) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                if (last != 0L) game.update((now - last) / 1_000_000_000.0)
                last = now
                frame = now
            }
        }
    }

    var viewport by remember { mutableStateOf(IntSize.Zero) }
    Column(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            // Android 15 (targetSdk 35) forces edge-to-edge; keep the view + status
            // bar inside the safe area so nothing hides under the system bars.
            .windowInsetsPadding(WindowInsets.systemBars),
    ) {
        // 3D viewport (fills everything above the status bar).
        Box(Modifier.weight(1f).fillMaxWidth().onSizeChanged { viewport = it }) {
            if (viewport.width > 0 && viewport.height > 0) {
                // Render the 3D view into a tiny framebuffer, then upscale with
                // nearest-neighbour filtering for big, chunky retro pixels.
                val lowW = RENDER_WIDTH
                val lowH = (RENDER_WIDTH.toFloat() * viewport.height / viewport.width)
                    .roundToInt().coerceIn(64, 512)
                val framebuffer = remember(lowW, lowH) { ImageBitmap(lowW, lowH) }
                val offscreen = remember { CanvasDrawScope() }
                Canvas(Modifier.fillMaxSize()) {
                    @Suppress("UNUSED_EXPRESSION") frame // subscribe the draw phase to the frame tick
                    val gc = androidx.compose.ui.graphics.Canvas(framebuffer)
                    offscreen.draw(Density(1f), LayoutDirection.Ltr, gc, Size(lowW.toFloat(), lowH.toFloat())) {
                        renderGame(game, assets)
                    }
                    drawImage(
                        image = framebuffer,
                        dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                        filterQuality = FilterQuality.None,
                    )
                }
            }
            TopHud(game, frame, Modifier.align(Alignment.TopStart))
            Controls(game, Modifier.align(Alignment.BottomCenter))
            if (game.status != GameState.Status.PLAYING) {
                EndOverlay(
                    game = game,
                    onRestart = { runId++ },
                    onExit = { navController.popBackStack() },
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }
        // Iconic status bar.
        DoomStatusBar(game, assets, frame, Modifier.fillMaxWidth())
    }
}

// ---------------------------------------------------------------------------
// Top HUD (compact: cartridge provenance + FPS + pickup banner)
// ---------------------------------------------------------------------------

@Composable
private fun TopHud(game: GameState, frame: Long, modifier: Modifier) {
    @Suppress("UNUSED_EXPRESSION") frame // changing param forces a recompose each frame
    Column(
        modifier
            .padding(10.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xAA000000))
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(
            "▣ ${game.cartridgeName} — FROM IMPLANT",
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
            color = Color(0xFFE0A23A),
        )
        Text(
            "FPS ${"%.0f".format(game.fps)}",
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
            color = Color(0xFF9C9C9C),
        )
        if (game.banner.isNotEmpty()) {
            Text(
                game.banner,
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFFF4F4F4),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Status bar (Freedoom STBAR + numbers + face)
// ---------------------------------------------------------------------------

@Composable
private fun DoomStatusBar(game: GameState, assets: DoomAssets, frame: Long, modifier: Modifier) {
    val bar = assets.statusBar()
    val barW = bar.width.toFloat()
    val barH = bar.height.toFloat()
    Canvas(modifier.aspectRatio(barW / barH)) {
        @Suppress("UNUSED_EXPRESSION") frame // redraw the bar each frame (live health/ammo)
        val scale = size.width / barW

        fun draw(img: ImageBitmap, bx: Int, by: Int) {
            drawImage(
                image = img,
                dstOffset = IntOffset((bx * scale).roundToInt(), (by * scale).roundToInt()),
                dstSize = IntSize((img.width * scale).roundToInt(), (img.height * scale).roundToInt()),
                filterQuality = FilterQuality.None,
            )
        }
        // Right-justified number ending at bar-space x = rightX.
        fun number(value: Int, rightX: Int, y: Int) {
            var x = rightX
            value.coerceAtLeast(0).toString().reversed().forEach { ch ->
                val d = assets.digit(ch - '0')
                x -= d.width
                draw(d, x, y)
            }
        }

        // Bar background scaled to full width.
        drawImage(
            image = bar,
            dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
            filterQuality = FilterQuality.None,
        )

        // Canonical Doom status-bar positions (in the 320x32 bar space).
        number(game.ammo, rightX = 44, y = 3)
        number(game.health, rightX = 90, y = 3); draw(assets.percent(), 90, 3)
        number(game.armor, rightX = 221, y = 3); draw(assets.percent(), 221, 3)
        draw(assets.face(if (game.status == GameState.Status.DEAD) 0 else game.health), 143, 1)
    }
}

// ---------------------------------------------------------------------------
// Controls + overlays
// ---------------------------------------------------------------------------

@Composable
private fun Controls(game: GameState, modifier: Modifier) {
    Row(
        modifier.fillMaxWidth().padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Bottom) {
            ControlButton(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Turn left") { game.turnLeft = it }
            ControlButton(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Turn right") { game.turnRight = it }
        }
        Button(
            onClick = { game.requestFire() },
            modifier = Modifier.size(72.dp),
            shape = RoundedCornerShape(36.dp),
        ) {
            Icon(Icons.Filled.RadioButtonChecked, contentDescription = "Fire")
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ControlButton(Icons.Filled.KeyboardArrowUp, "Forward") { game.moveForward = it }
            ControlButton(Icons.Filled.KeyboardArrowDown, "Back") { game.moveBackward = it }
        }
    }
}

@Composable
private fun ControlButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onPressedChange: (Boolean) -> Unit,
) {
    HoldButton(modifier = Modifier.size(64.dp), onPressedChange = onPressedChange) {
        Icon(icon, contentDescription = description)
    }
}

@Composable
private fun EndOverlay(
    game: GameState,
    onRestart: () -> Unit,
    onExit: () -> Unit,
    modifier: Modifier,
) {
    val won = game.status == GameState.Status.LEVEL_COMPLETE
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xEE0B0E0B))
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            if (won) "LEVEL COMPLETE" else "YOU DIED",
            style = MaterialTheme.typography.headlineSmall.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold),
            color = if (won) Color(0xFF36E07A) else Color(0xFFE0483A),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Enemies left: ${game.aliveEnemyCount()} • Items left: ${game.remainingItemCount()}",
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF9C9C9C),
        )
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onRestart) { Text("Restart") }
            OutlinedButton(onClick = onExit) { Text("Exit") }
        }
    }
}

// ---------------------------------------------------------------------------
// Rendering
// ---------------------------------------------------------------------------

private fun DrawScope.renderGame(game: GameState, assets: DoomAssets) {
    val w = size.width
    val h = size.height
    val themeId = game.level.cartridge.textureThemeId

    // Flat-shaded ceiling + floor (colours sampled from the matching flats).
    drawRect(assets.ceilColor(themeId), topLeft = Offset(0f, 0f), size = Size(w, h / 2f))
    drawRect(assets.floorColor(themeId), topLeft = Offset(0f, h / 2f), size = Size(w, h / 2f))

    // One ray per framebuffer column (size is the low-res offscreen, not the screen).
    val cols = w.toInt().coerceAtLeast(1)
    val zBuffer = DoubleArray(cols)

    val posX = game.posX
    val posY = game.posY
    val dirX = game.dirX
    val dirY = game.dirY
    val planeX = game.planeX
    val planeY = game.planeY

    // Textured wall columns.
    for (i in 0 until cols) {
        val cameraX = 2.0 * i / cols - 1.0
        val rayDirX = dirX + planeX * cameraX
        val rayDirY = dirY + planeY * cameraX
        val hit = game.raycaster.cast(posX, posY, rayDirX, rayDirY)
        zBuffer[i] = hit.perpDist

        val lineH = (h / hit.perpDist).toFloat()
        val top = (h - lineH) / 2f
        val xi = (i * w / cols).toInt()
        val xi1 = ((i + 1) * w / cols).toInt()
        val colW = (xi1 - xi).coerceAtLeast(1)

        val tex = assets.wall(hit.tileType, themeId)
        val texCol = (hit.wallX * tex.width).toInt().coerceIn(0, tex.width - 1)

        drawImage(
            image = tex,
            srcOffset = IntOffset(texCol, 0),
            srcSize = IntSize(1, tex.height),
            dstOffset = IntOffset(xi, top.roundToInt()),
            dstSize = IntSize(colW, lineH.roundToInt().coerceAtLeast(1)),
            filterQuality = FilterQuality.None,
        )

        // Distance fog + side shading as a translucent black overlay.
        val fog = (1.0 / (1.0 + hit.perpDist * hit.perpDist * 0.012)).coerceIn(0.18, 1.0)
        val brightness = fog * (if (hit.side == 1) 0.72 else 1.0)
        val shade = (1.0 - brightness).toFloat().coerceIn(0f, 0.85f)
        if (shade > 0.01f) {
            drawRect(Color.Black.copy(alpha = shade), topLeft = Offset(xi.toFloat(), top), size = Size(colW.toFloat(), lineH))
        }
    }

    // Sprites: items + living entities, far to near.
    data class Spr(val x: Double, val y: Double, val img: ImageBitmap, val scale: Double)
    val sprites = ArrayList<Spr>()
    for (item in game.level.items) {
        if (!item.collected) sprites.add(Spr(item.x, item.y, assets.item(item.def.type), 0.45))
    }
    for (e in game.level.entities) {
        if (e.alive) {
            val scale = if (e.def.type == EntityType.BOSS) 1.0 else 0.72
            sprites.add(Spr(e.x, e.y, assets.entity(e.def.type), scale))
        }
    }
    sprites.sortByDescending { (it.x - posX) * (it.x - posX) + (it.y - posY) * (it.y - posY) }

    val invDet = 1.0 / (planeX * dirY - dirX * planeY)
    for (s in sprites) {
        val sx = s.x - posX
        val sy = s.y - posY
        val transformX = invDet * (dirY * sx - dirX * sy)
        val transformY = invDet * (-planeY * sx + planeX * sy)
        if (transformY <= 0.05) continue

        val screenCol = (cols / 2.0) * (1 + transformX / transformY)
        val centerCol = screenCol.toInt()
        if (centerCol < 0 || centerCol >= cols) continue
        if (transformY >= zBuffer[centerCol]) continue

        val lineH = (h / transformY).toFloat()
        val floorLine = (h + lineH) / 2f
        val spriteH = lineH * s.scale.toFloat()
        val spriteW = spriteH * (s.img.width.toFloat() / s.img.height.toFloat())
        val cx = (screenCol * w / cols).toFloat()
        val left = cx - spriteW / 2f
        val topY = floorLine - spriteH

        // Distance fog: darken the sprite itself (Modulate) so transparency is kept
        // and only the sprite pixels are shaded — not a box around it.
        val fog = (1.0 / (1.0 + transformY * transformY * 0.012)).coerceIn(0.3, 1.0).toFloat()
        val tint = if (fog < 0.99f) {
            androidx.compose.ui.graphics.ColorFilter.tint(
                Color(fog, fog, fog, 1f),
                androidx.compose.ui.graphics.BlendMode.Modulate,
            )
        } else {
            null
        }
        drawImage(
            image = s.img,
            dstOffset = IntOffset(left.roundToInt(), topY.roundToInt()),
            dstSize = IntSize(spriteW.roundToInt().coerceAtLeast(1), spriteH.roundToInt().coerceAtLeast(1)),
            colorFilter = tint,
            filterQuality = FilterQuality.None,
        )
    }

    // First-person weapon, centred at the bottom of the viewport.
    val weapon = assets.weapon()
    val weaponH = h * 0.42f
    val weaponW = weaponH * (weapon.width.toFloat() / weapon.height.toFloat())
    drawImage(
        image = weapon,
        dstOffset = IntOffset(((w - weaponW) / 2f).roundToInt(), (h - weaponH).roundToInt()),
        dstSize = IntSize(weaponW.roundToInt(), weaponH.roundToInt()),
        filterQuality = FilterQuality.None,
    )
}

/** Walk up the Context wrapper chain to the hosting Activity (for orientation control). */
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
