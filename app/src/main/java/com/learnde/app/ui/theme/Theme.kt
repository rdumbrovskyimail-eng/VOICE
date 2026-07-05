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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Immutable
data class Palette(
    // «Тёплая бумага»: кремовые поверхности, сепия-чернила, бронзовый акцент.
    val background: Color = Color(0xFFF6F1E4),       // лист бумаги
    val surface: Color = Color(0xFFEFE8D6),          // поля ввода
    val surfaceElevated: Color = Color(0xFFE8DFC9),  // карточки
    val outline: Color = Color(0xFFDCD2B8),          // волосяные линии

    val textPrimary: Color = Color(0xFF3A3423),      // чернила
    val textSecondary: Color = Color(0xFF6B6350),
    val textDim: Color = Color(0xFF9B9077),

    val accentBlue: Color = Color(0xFF7D5A24),       // бронзовые чернила (акцент)
    val accentBlueBg: Color = Color(0xFFF0E5C6),     // золотистая плашка
    val accentGreen: Color = Color(0xFF4F6B33),      // оливковый
    val accentGreenBg: Color = Color(0xFFE7EBD2),

    val error: Color = Color(0xFFA3452C),            // терракота
    val errorBg: Color = Color(0xFFF3DFD3)
)

val LocalPalette = staticCompositionLocalOf { Palette() }

object Radius { val sm = 4.dp; val md = 8.dp; val lg = 12.dp; val pill = 999.dp }
object Space { val xs = 4.dp; val sm = 8.dp; val md = 12.dp; val lg = 16.dp; val xl = 24.dp }

private val appTypography = Typography(
    bodyLarge   = TextStyle(color = Color(0xFF3A3423), fontSize = 14.sp, lineHeight = 21.sp),
    bodyMedium  = TextStyle(color = Color(0xFF6B6350), fontSize = 13.sp, lineHeight = 19.sp),
    bodySmall   = TextStyle(color = Color(0xFF6B6350), fontSize = 12.sp, lineHeight = 17.sp),
    labelLarge  = TextStyle(color = Color(0xFF3A3423), fontSize = 12.sp,  fontWeight = FontWeight.Medium, letterSpacing = 0.2.sp),
    labelMedium = TextStyle(color = Color(0xFF6B6350), fontSize = 11.5.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.4.sp),
    labelSmall  = TextStyle(color = Color(0xFF9B9077), fontSize = 11.sp,  letterSpacing = 0.3.sp),
    // Serif-заголовки задают «бумажный», редакционный характер интерфейса.
    titleLarge  = TextStyle(color = Color(0xFF3A3423), fontSize = 17.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Serif),
)

@Composable
fun GeminiVoiceTheme(content: @Composable () -> Unit) {
    val palette = Palette()
    val colorScheme = lightColorScheme(
        primary = palette.accentBlue,          onPrimary = Color.White,
        primaryContainer = palette.accentBlueBg, onPrimaryContainer = palette.accentBlue,
        background = palette.background,       onBackground = palette.textPrimary,
        surface = palette.background,          onSurface = palette.textPrimary,
        surfaceVariant = palette.surfaceElevated, onSurfaceVariant = palette.textSecondary,
        outline = palette.outline,             outlineVariant = palette.outline,
        error = palette.error,                 onError = Color.White,
    )
    CompositionLocalProvider(LocalPalette provides palette) {
        MaterialTheme(colorScheme = colorScheme, typography = appTypography, content = content)
    }
}

object AppTheme {
    val palette: Palette @Composable get() = LocalPalette.current
}