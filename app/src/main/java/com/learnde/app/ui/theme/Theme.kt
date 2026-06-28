package com.learnde.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Immutable
data class Palette(
    val background: Color = Color(0xFFFFFFFF),
    val surface: Color = Color(0xFFFFFFFF),
    val surfaceElevated: Color = Color(0xFFF5F5F5),
    val outline: Color = Color(0xFF000000),
    val textPrimary: Color = Color(0xFF000000),
    val textSecondary: Color = Color(0xFF555555),
    val textDim: Color = Color(0xFF999999),
    val accent: Color = Color(0xFF000000),
    val onAccent: Color = Color(0xFFFFFFFF),
    val error: Color = Color(0xFFD32F2F),
)

val LocalPalette = staticCompositionLocalOf { Palette() }

// Очень маленькие отступы
object Space { val xs = 2.dp; val sm = 4.dp; val md = 8.dp; val lg = 12.dp; val xl = 16.dp }
object Radius { val sm = 4.dp; val md = 8.dp; val lg = 12.dp; val pill = 999.dp }
object Motion { const val medium = 250 }

// Мелкая типографика
private val appTypography = Typography(
    bodyLarge = TextStyle(color = Color.Black, fontSize = 12.sp, lineHeight = 16.sp),
    bodyMedium = TextStyle(color = Color.DarkGray, fontSize = 11.sp, lineHeight = 14.sp),
    labelLarge = TextStyle(color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(color = Color.Gray, fontSize = 9.sp),
    titleLarge = TextStyle(color = Color.Black, fontSize = 14.sp, fontWeight = FontWeight.Bold)
)

@Composable
fun GeminiVoiceTheme(darkOverride: Boolean? = null, content: @Composable () -> Unit) {
    val palette = Palette() // Принудительно белая минималистичная тема
    val colorScheme = lightColorScheme(
        primary = palette.accent, onPrimary = palette.onAccent,
        background = palette.background, onBackground = palette.textPrimary,
        surface = palette.surface, onSurface = palette.textPrimary,
        error = palette.error,
    )
    CompositionLocalProvider(LocalPalette provides palette) {
        MaterialTheme(colorScheme = colorScheme, typography = appTypography, content = content)
    }
}

object AppTheme {
    val palette: Palette @Composable get() = LocalPalette.current
}