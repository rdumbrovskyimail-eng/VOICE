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
    val surface: Color = Color(0xFFF8F9FA),
    val surfaceElevated: Color = Color(0xFFF1F3F4),
    val outline: Color = Color(0xFFE8EAED),
    val textPrimary: Color = Color(0xFF202124),
    val textSecondary: Color = Color(0xFF5F6368),
    val textDim: Color = Color(0xFF9AA0A6),
    
    val accentBlue: Color = Color(0xFF1A73E8),
    val accentBlueBg: Color = Color(0xFFE8F0FE),
    val accentGreen: Color = Color(0xFF137333),
    val accentGreenBg: Color = Color(0xFFE6F4EA),
    
    val error: Color = Color(0xFFD93025),
    val errorBg: Color = Color(0xFFFCE8E6)
)

val LocalPalette = staticCompositionLocalOf { Palette() }

object Radius { val sm = 4.dp; val md = 8.dp; val lg = 12.dp; val pill = 999.dp }
object Space { val xs = 4.dp; val sm = 8.dp; val md = 12.dp; val lg = 16.dp; val xl = 24.dp }

private val appTypography = Typography(
    bodyLarge = TextStyle(color = Color(0xFF202124), fontSize = 14.sp, lineHeight = 20.sp),
    bodyMedium = TextStyle(color = Color(0xFF5F6368), fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(color = Color(0xFF202124), fontSize = 12.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(color = Color(0xFF9AA0A6), fontSize = 11.sp),
    titleLarge = TextStyle(color = Color(0xFF202124), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
)

@Composable
fun GeminiVoiceTheme(darkOverride: Boolean? = null, content: @Composable () -> Unit) {
    val palette = Palette()
    val colorScheme = lightColorScheme(
        primary = palette.accentBlue, onPrimary = Color.White,
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