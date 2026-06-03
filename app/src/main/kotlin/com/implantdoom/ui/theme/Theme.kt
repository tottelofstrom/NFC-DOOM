package com.implantdoom.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Dark, gritty Doom-style palette (forced dark regardless of system setting) with
// the classic red/amber/green accents. The in-game 3D view is rendered with
// Freedoom textures/sprites; this scheme just styles the menus/HUD chrome.
private val DoomRed = Color(0xFFE0483A)
private val DoomAmber = Color(0xFFE0A23A)
private val DoomGreen = Color(0xFF36E07A)
private val Ink = Color(0xFF0A0908)
private val Surface1 = Color(0xFF15110F)
private val Surface2 = Color(0xFF221A16)
private val Paper = Color(0xFFE8E2DA)

private val DoomColors = darkColorScheme(
    primary = DoomRed,
    onPrimary = Color(0xFF160503),
    secondary = DoomAmber,
    onSecondary = Color(0xFF1A1206),
    tertiary = DoomGreen,
    onTertiary = Color(0xFF06150C),
    background = Ink,
    onBackground = Paper,
    surface = Surface1,
    onSurface = Paper,
    surfaceVariant = Surface2,
    onSurfaceVariant = Color(0xFFB7A99B),
    outline = Color(0xFF4A3B30),
    error = DoomRed,
    onError = Color(0xFF160503),
)

/** App-wide Material 3 theme — forced dark for a consistent Doom mood. */
@Composable
fun ImplantDoomTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DoomColors,
        typography = Typography(),
        content = content,
    )
}
