package theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val AppColorScheme = lightColorScheme(
    primary = Brand,
    onPrimary = White,
    secondary = Brand,
    onSecondary = White,
    background = White,
    onBackground = Color(0xFF111827),
    surface = White,
    onSurface = Color(0xFF111827),
    surfaceVariant = BrandLight,
    onSurfaceVariant = Color(0xFF6B7280),
    outline = Color(0xFFE5E7EB),
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AppColorScheme,
        content = content,
    )
}
