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
    // «Архивный бланк»: пыльно-персиковая бумага (как на фото)
    val background: Color = Color(0xFFE8BCA8),       // Основной цвет бумаги
    val surface: Color = Color(0xFFE0B09A),          // Чуть более плотный оттенок для полей
    val surfaceElevated: Color = Color(0xFFD6A38C),  // Карточки и плашки
    val outline: Color = Color(0xFFC48F77),          // Цвет печатных линий и рамок на бланке

    // Чернила печатного текста (теплый темно-серый, почти черный)
    val textPrimary: Color = Color(0xFF2B2624),      
    val textSecondary: Color = Color(0xFF5C514D),
    val textDim: Color = Color(0xFF8A7A73),

    // Акцент: цвет синей шариковой ручки (как подпись врача на фото)
    val accentBlue: Color = Color(0xFF34547A),       // Глубокий чернильно-синий
    val accentBlueBg: Color = Color(0xFFD4A48F),     // Выделение текста (чуть темнее фона)
    
    // Дополнительные цвета (оставляем приглушенными, чтобы не ломать стиль)
    val accentGreen: Color = Color(0xFF4A6B4E),      // Приглушенный зеленый
    val accentGreenBg: Color = Color(0xFFD1B19D),
    val error: Color = Color(0xFFA3452C),            // Терракотовый красный
    val errorBg: Color = Color(0xFFE3A896)
)

val LocalPalette = staticCompositionLocalOf { Palette() }

object Radius { val sm = 4.dp; val md = 8.dp; val lg = 12.dp; val pill = 999.dp }
object Space { val xs = 4.dp; val sm = 8.dp; val md = 12.dp; val lg = 16.dp; val xl = 24.dp }

private val appTypography = Typography(
    // Основной текст делаем Serif, чуть крупнее и с большим "воздухом" (lineHeight)
    bodyLarge   = TextStyle(color = Color(0xFF2B2624), fontSize = 16.sp, lineHeight = 24.sp, fontFamily = FontFamily.Serif),
    bodyMedium  = TextStyle(color = Color(0xFF5C514D), fontSize = 14.sp, lineHeight = 20.sp, fontFamily = FontFamily.Serif),
    bodySmall   = TextStyle(color = Color(0xFF5C514D), fontSize = 12.sp, lineHeight = 18.sp, fontFamily = FontFamily.Serif),
    
    // Системные подписи (кнопки, время) оставляем без засечек для контраста
    labelLarge  = TextStyle(color = Color(0xFF2B2624), fontSize = 12.sp,  fontWeight = FontWeight.Medium, letterSpacing = 0.2.sp),
    labelMedium = TextStyle(color = Color(0xFF5C514D), fontSize = 11.5.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.4.sp),
    labelSmall  = TextStyle(color = Color(0xFF8A7A73), fontSize = 11.sp,  letterSpacing = 0.3.sp),
    
    titleLarge  = TextStyle(color = Color(0xFF2B2624), fontSize = 20.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Serif),
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