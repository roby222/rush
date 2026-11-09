package io.github.roby222.rush

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
)

private val Dark = darkColorScheme(
    primary = Amber, onPrimary = Ink,
    secondaryContainer = Color(0xFF3A3324), onSecondaryContainer = Color(0xFFFFE6A8),
    background = Ink, onBackground = Color(0xFFE8EAED),
    surface = Ink, onSurface = Color(0xFFE8EAED), onSurfaceVariant = Color(0xFFA8ADB4),
    surfaceContainerHighest = Color(0xFF2A2E35), surfaceContainerHigh = Color(0xFF2A2E35),
    outline = Color(0xFF5F6368), outlineVariant = Color(0xFF3C4043),
)

@Composable
fun RushTheme(content: @Composable () -> Unit) = MaterialTheme(
    colorScheme = if (isSystemInDarkTheme()) Dark else Light,
    shapes = Shapes(medium = RoundedCornerShape(24.dp), large = RoundedCornerShape(28.dp)),
    content = content,
)

/** Travel-time color by traffic level, like map apps: green, orange, red. Contrast ≥4.5:1 on the cards. */
@Composable
@ReadOnlyComposable
fun trafficColor(level: Int): Color = if (isSystemInDarkTheme()) {
    listOf(Color(0xFF81C995), Color(0xFFFDD663), Color(0xFFF28B82))[level]
} else {
    listOf(Color(0xFF137333), Color(0xFFB06000), Color(0xFFC5221F))[level]
}
