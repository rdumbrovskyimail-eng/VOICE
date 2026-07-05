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
    val background: Color = Color(0xFFFFFFFF),       // Чистый белый
    val surface: Color = Color(0xFFFFFFFF),
    val surfaceVariant: Color = Color(0xFFF4F4F4),   // Светло-серый (Input, User Bubble)
    val surfaceElevated: Color = Color(0xFFF9F9F9),  // Чуть выделяющийся фон для карточек
    val outline: Color = Color(0xFFE5E5E5),          // Тонкие разделители

    val textPrimary: Color = Color(0xFF0D0D0D),      // Почти черный (читабельность)
    val textSecondary: Color = Color(0xFF676767),    // Серый (плейсхолдеры, подписи)
    val textTertiary: Color = Color(0xFF8E8E8E),     // Светло-серый (мелкие детали)
    val textDim: Color = Color(0xFFAAAAAA),          // Тусклый текст (неактивные элементы)

    val actionPrimary: Color = Color(0xFF000000),    // Черные кнопки (Send)
    val actionPrimaryText: Color = Color(0xFFFFFFFF),
    
    val accentActive: Color = Color(0xFF2563EB),     // Синий (активный микрофон)
    val accentBlue: Color = Color(0xFF2563EB),       // Основной акцентный синий
    val accentBlueBg: Color = Color(0xFFEFF6FF),     // Светло-синий фон
    val accentGreen: Color = Color(0xFF10B981),      // Зеленый акцент
    val accentGreenBg: Color = Color(0xFFECFDF5),    // Светло-зеленый фон
    val error: Color = Color(0xFFEF4444),            // Красный (ошибки, удаление)
    val errorBg: Color = Color(0xFFFEF2F2)           // Светло-красный фон
)

val LocalPalette = staticCompositionLocalOf { Palette() }

object Radius { 
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp 
    val pill = 999.dp 
}

object Space { 
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp 
    val xxl = 32.dp
}

// Настраиваем типографику под стиль Inter / SF Pro
private val appTypography = Typography(
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp, // Важно для чтения длинных текстов ИИ
        letterSpacing = 0.1.sp,
        color = Color(0xFF0D0D0D)
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        color = Color(0xFF0D0D0D)
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        color = Color(0xFF676767)
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        color = Color(0xFF0D0D0D)
    )
)

@Composable
fun GeminiVoiceTheme(content: @Composable () -> Unit) {
    val palette = Palette()
    val colorScheme = lightColorScheme(
        primary = palette.actionPrimary,
        onPrimary = palette.actionPrimaryText,
        background = palette.background,
        surface = palette.surface,
        error = palette.error
    )
    CompositionLocalProvider(LocalPalette provides palette) {
        MaterialTheme(colorScheme = colorScheme, typography = appTypography, content = content)
    }
}

object AppTheme {
    val palette: Palette @Composable get() = LocalPalette.current
}