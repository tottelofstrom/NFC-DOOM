package com.implantdoom.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.implantdoom.cartridge.Cartridge
import com.implantdoom.cartridge.EntityType
import com.implantdoom.cartridge.ItemType
import com.implantdoom.cartridge.TileType
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** A titled card section used across the detail/builder/diagnostics screens. */
@Composable
fun SectionCard(title: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Box(Modifier.padding(top = 8.dp)) { content() }
        }
    }
}

/** A label/value row; the value is monospace and may wrap. */
@Composable
fun InfoRow(label: String, value: String, valueColor: Color = MaterialTheme.colorScheme.onSurface) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            color = valueColor,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}

/**
 * Top-down preview of a cartridge map: walls, exit, doors, hazards, the player
 * start (with facing arrow), entities (red) and items (blue).
 */
@Composable
fun MapPreview(cartridge: Cartridge, modifier: Modifier = Modifier) {
    val wallColor = Color(0xFF566077)
    val emptyColor = Color(0xFF10140F)
    val doorColor = Color(0xFFB9883E)
    val exitColor = Color(0xFF36E07A)
    val hazardColor = Color(0xFFB23A2E)
    val gridColor = Color(0xFF000000)
    val playerColor = Color(0xFF7CFFA0)
    val enemyColor = Color(0xFFE0483A)
    val bossColor = Color(0xFFC23AE0)
    val itemColor = Color(0xFF8AB4F8)

    Canvas(modifier) {
        val w = cartridge.mapWidth
        val h = cartridge.mapHeight
        val cell = min(size.width / w, size.height / h)
        val originX = (size.width - cell * w) / 2f
        val originY = (size.height - cell * h) / 2f

        // Tiles.
        for (y in 0 until h) {
            for (x in 0 until w) {
                val color = when (cartridge.tileAt(x, y)) {
                    TileType.WALL -> wallColor
                    TileType.DOOR -> doorColor
                    TileType.EXIT -> exitColor
                    TileType.HAZARD -> hazardColor
                    else -> emptyColor
                }
                drawRect(
                    color = color,
                    topLeft = Offset(originX + x * cell, originY + y * cell),
                    size = Size(cell, cell),
                )
                drawRect(
                    color = gridColor,
                    topLeft = Offset(originX + x * cell, originY + y * cell),
                    size = Size(cell, cell),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 0.5f),
                )
            }
        }

        // Items.
        for (item in cartridge.items) {
            val cx = originX + (item.x + 0.5f) * cell
            val cy = originY + (item.y + 0.5f) * cell
            drawCircle(itemColor, radius = cell * 0.22f, center = Offset(cx, cy))
        }

        // Entities.
        for (e in cartridge.entities) {
            val cx = originX + (e.x + 0.5f) * cell
            val cy = originY + (e.y + 0.5f) * cell
            val c = if (e.type == EntityType.BOSS) bossColor else enemyColor
            drawCircle(c, radius = cell * 0.3f, center = Offset(cx, cy))
        }

        // Player + facing arrow.
        val pcx = originX + (cartridge.playerStartX + 0.5f) * cell
        val pcy = originY + (cartridge.playerStartY + 0.5f) * cell
        drawCircle(playerColor, radius = cell * 0.32f, center = Offset(pcx, pcy))
        val a = cartridge.playerStartAngleRadians()
        drawLine(
            color = Color.Black,
            start = Offset(pcx, pcy),
            end = Offset(pcx + cos(a).toFloat() * cell * 0.6f, pcy + sin(a).toFloat() * cell * 0.6f),
            strokeWidth = cell * 0.12f,
        )
    }
}

/** Monospace hex preview of up to [maxBytes] bytes, in `00 11 22` groups. */
@Composable
fun HexDump(bytes: ByteArray, modifier: Modifier = Modifier, maxBytes: Int = 256) {
    val shown = if (bytes.size > maxBytes) bytes.copyOfRange(0, maxBytes) else bytes
    val text = buildString {
        shown.forEachIndexed { i, b ->
            append("%02X".format(b))
            append(if ((i + 1) % 16 == 0) "\n" else " ")
        }
        if (bytes.size > maxBytes) append("\n… (${bytes.size - maxBytes} more bytes)")
    }
    Box(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF05070A))
            .horizontalScroll(rememberScrollState())
            .padding(12.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium,
            ),
            color = Color(0xFF6FE08A),
            fontSize = 12.sp,
        )
    }
}

/**
 * A button that reports its pressed/released state for as long as the finger is
 * held — used for the game's movement controls. [onPressedChange] is invoked with
 * `true` on touch-down and `false` on release/cancel.
 */
@Composable
fun HoldButton(
    modifier: Modifier = Modifier,
    onPressedChange: (Boolean) -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    var pressed by remember { mutableStateOf(false) }
    val bg = if (pressed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (pressed) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Box(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        onPressedChange(true)
                        tryAwaitRelease()
                        pressed = false
                        onPressedChange(false)
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.material3.LocalContentColor provides fg,
        ) { content() }
    }
}
