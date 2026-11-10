package io.github.roby222.rush.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// Map-app look: neutral surfaces, ink text, amber as the single brand accent.
val Amber = Color(0xFFFFB300)
val Ink = Color(0xFF1C1F24)

private val Light = lightColorScheme(
    primary = Ink, onPrimary = Color.White,
    secondaryContainer = Color(0xFFFFF1CC), onSecondaryContainer = Color(0xFF4A3500),
    background = Color.White, onBackground = Ink,
    surface = Color.White, onSurface = Ink, onSurfaceVariant = Color(0xFF5F6368),
    surfaceContainerHighest = Color(0xFFF1F3F4), surfaceContainerHigh = Color(0xFFF1F3F4),
    outline = Color(0xFFDADCE0), outlineVariant = Color(0xFFE8EAED),
    error = Color(0xFFC5221F), errorContainer = Color(0xFFFCE8E6), onErrorContainer = Ink,
)

private val Dark = darkColorScheme(
    primary = Amber, onPrimary = Ink,
    secondaryContainer = Color(0xFF3A3324), onSecondaryContainer = Color(0xFFFFE6A8),
    background = Ink, onBackground = Color(0xFFE8EAED),
    surface = Ink, onSurface = Color(0xFFE8EAED), onSurfaceVariant = Color(0xFFA8ADB4),
    surfaceContainerHighest = Color(0xFF2A2E35), surfaceContainerHigh = Color(0xFF2A2E35),
    outline = Color(0xFF5F6368), outlineVariant = Color(0xFF3C4043),
    error = Color(0xFFF28B82), errorContainer = Color(0xFF3D2522), onErrorContainer = Color(0xFFE8EAED),
)

@Composable
fun RushTheme(content: @Composable () -> Unit) = MaterialTheme(
    colorScheme = if (isSystemInDarkTheme()) Dark else Light,
    shapes = Shapes(medium = RoundedCornerShape(24.dp), large = RoundedCornerShape(28.dp)),
    content = content,
)

/** Raw palette, shared with the home-screen widget (Glance cannot read the Compose theme). Index = traffic level. */
object Palette {
    val trafficLight = listOf(Color(0xFF137333), Color(0xFFA04D00), Color(0xFFC5221F))
    val trafficDark = listOf(Color(0xFF81C995), Color(0xFFFCAD70), Color(0xFFF28B82))
    val statusLight = listOf(Color(0xFFE6F4EA), Color(0xFFFEEFE3), Color(0xFFFCE8E6))
    val statusDark = listOf(Color(0xFF1F3326), Color(0xFF3B2A20), Color(0xFF3D2522))
    val textLight = Ink
    val textDark = Color(0xFFE8EAED)
    val mutedLight = Color(0xFF5F6368)
    val mutedDark = Color(0xFFA8ADB4)
    val cardLight = Color(0xFFF1F3F4)
    val cardDark = Color(0xFF2A2E35)
}

/** Traffic color by level, like map apps: green, orange, red; orange stays clear of the brand Amber. Contrast ≥4.5:1 on the cards. */
@Composable
@ReadOnlyComposable
fun trafficColor(level: Int): Color = (if (isSystemInDarkTheme()) Palette.trafficDark else Palette.trafficLight)[level]

/** Status card ground for a traffic level, a tint of [trafficColor] that keeps body text at ≥4.5:1. */
@Composable
@ReadOnlyComposable
fun statusContainer(level: Int): Color = (if (isSystemInDarkTheme()) Palette.statusDark else Palette.statusLight)[level]
