package fr.hermorse.app.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

val Navy = Color(0xFF1B3C80)
val NavyDeep = Color(0xFF0B1A3A)
val Gold = Color(0xFFE9A93A)
val GoodGreen = Color(0xFF34C759)
val BadRed = Color(0xFFFF3B30)

private val LightColors = lightColorScheme(
    primary = Navy,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE4F7),
    onPrimaryContainer = Navy,
    secondary = Gold,
    onSecondary = Color(0xFF1A1200),
    secondaryContainer = Color(0xFFFCEBCB),
    onSecondaryContainer = Color(0xFF3A2C10),
    background = Color(0xFFF2F2F7),
    onBackground = Color(0xFF1C1C1E),
    surface = Color.White,
    onSurface = Color(0xFF1C1C1E),
    surfaceVariant = Color(0xFFE5E5EA),
    onSurfaceVariant = Color(0xFF6C6C70)
)

private val DarkColors = darkColorScheme(
    primary = Gold,
    onPrimary = Color(0xFF1A1200),
    primaryContainer = Color(0xFF3A2C10),
    onPrimaryContainer = Gold,
    secondary = Color(0xFF8FB0FF),
    onSecondary = NavyDeep,
    secondaryContainer = Navy,
    onSecondaryContainer = Color.White,
    background = Color.Black,
    onBackground = Color(0xFFF2F2F7),
    surface = Color(0xFF1C1C1E),
    onSurface = Color(0xFFF2F2F7),
    surfaceVariant = Color(0xFF2C2C2E),
    onSurfaceVariant = Color(0xFFAEAEB2)
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp)
)

@Composable
fun HerMorseTheme(dark: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        shapes = AppShapes,
        content = content
    )
}
