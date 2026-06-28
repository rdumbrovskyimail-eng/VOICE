// Путь: app/src/main/java/com/learnde/app/ui/theme/Theme.kt
//
// Единая дизайн-система. Один источник цвета/типографики/отступов/формы/движения для всех
// экранов — устраняет разнобой literal-ов (Color(0xFF0F1114), 14.dp, 13.sp…), разбросанных по UI.
//
// Ключевая идея: палитра СОСТОЯНИЙ. У голосового ассистента «контент» — это его состояние
// (слушает/думает/говорит), поэтому цвет кодирует состояние, а не служит единственным акцентом.
//
// Бонус: здесь реализована рабочая светлая/тёмная тема — чинит «мёртвую» настройку themeMode.

package com.learnde.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
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

// ─────────────────────────────────────────────────────────────────────────────
// 1. ЦВЕТА — сырые токены (через Palette ниже, не напрямую в UI)
// ─────────────────────────────────────────────────────────────────────────────
private object Ink {
    val base      = Color(0xFF0B0D11)
    val surface   = Color(0xFF14171D)
    val surfaceHi = Color(0xFF1C2027)
    val outline   = Color(0x1FFFFFFF)
    val outlineHi = Color(0x33FFFFFF)
    val textHi    = Color(0xFFF2F4F7)
    val textMid   = Color(0xFFAEB4BF)
    val textDim   = Color(0xFF7B828D)
}

private object Light {
    val base      = Color(0xFFF7F8FA)
    val surface   = Color(0xFFFFFFFF)
    val surfaceHi = Color(0xFFEEF1F5)
    val outline   = Color(0x14000000)
    val outlineHi = Color(0x29000000)
    val textHi    = Color(0xFF111418)
    val textMid   = Color(0xFF454B54)
    val textDim   = Color(0xFF727983)
}

// Палитра СОСТОЯНИЙ — общая для обеих тем.
private object StateHue {
    val idle      = Color(0xFF5B6573)
    val listening = Color(0xFF34C7E3)
    val thinking  = Color(0xFFF5B445)
    val speaking  = Color(0xFF6E8BFF)
    val error     = Color(0xFFF06A5D)
    val success   = Color(0xFF49C18A)
}

@Immutable
data class Palette(
    val background: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val outline: Color,
    val outlineStrong: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textDim: Color,
    val accent: Color,
    val onAccent: Color,
    val stateIdle: Color,
    val stateListening: Color,
    val stateThinking: Color,
    val stateSpeaking: Color,
    val error: Color,
    val success: Color,
)

private val DarkPalette = Palette(
    background = Ink.base, surface = Ink.surface, surfaceElevated = Ink.surfaceHi,
    outline = Ink.outline, outlineStrong = Ink.outlineHi,
    textPrimary = Ink.textHi, textSecondary = Ink.textMid, textDim = Ink.textDim,
    accent = StateHue.speaking, onAccent = Color(0xFF0B0D11),
    stateIdle = StateHue.idle, stateListening = StateHue.listening,
    stateThinking = StateHue.thinking, stateSpeaking = StateHue.speaking,
    error = StateHue.error, success = StateHue.success,
)

private val LightPalette = Palette(
    background = Light.base, surface = Light.surface, surfaceElevated = Light.surfaceHi,
    outline = Light.outline, outlineStrong = Light.outlineHi,
    textPrimary = Light.textHi, textSecondary = Light.textMid, textDim = Light.textDim,
    accent = StateHue.speaking, onAccent = Color.White,
    stateIdle = StateHue.idle, stateListening = StateHue.listening,
    stateThinking = StateHue.thinking, stateSpeaking = StateHue.speaking,
    error = StateHue.error, success = StateHue.success,
)

val LocalPalette = staticCompositionLocalOf { DarkPalette }

// ─────────────────────────────────────────────────────────────────────────────
// 2. ОТСТУПЫ / ФОРМА / ДВИЖЕНИЕ (сетка 4dp)
// ─────────────────────────────────────────────────────────────────────────────
object Space { val xs = 4.dp; val sm = 8.dp; val md = 12.dp; val lg = 16.dp; val xl = 24.dp; val xxl = 32.dp }
object Radius { val sm = 8.dp; val md = 12.dp; val lg = 16.dp; val xl = 20.dp; val pill = 999.dp }
object Motion { const val fast = 150; const val medium = 250; const val slow = 400; const val breath = 4200 }

// ─────────────────────────────────────────────────────────────────────────────
// 3. ТИПОГРАФИКА — единая шкала
// ─────────────────────────────────────────────────────────────────────────────
private fun appTypography(p: Palette) = Typography(
    headlineMedium = TextStyle(color = p.textPrimary, fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp),
    titleLarge     = TextStyle(color = p.textPrimary, fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.1).sp),
    titleMedium    = TextStyle(color = p.textPrimary, fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    bodyLarge      = TextStyle(color = p.textPrimary, fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.Normal),
    bodyMedium     = TextStyle(color = p.textSecondary, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Normal),
    labelLarge     = TextStyle(color = p.textSecondary, fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.1.sp),
    labelSmall     = TextStyle(color = p.textDim, fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.3.sp),
)

// ─────────────────────────────────────────────────────────────────────────────
// 4. ТЕМА. darkOverride: null=AUTO(по системе), true=DARK, false=LIGHT
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun GeminiVoiceTheme(
    darkOverride: Boolean? = null,
    content: @Composable () -> Unit,
) {
    val dark = darkOverride ?: isSystemInDarkTheme()
    val palette = if (dark) DarkPalette else LightPalette

    val colorScheme = if (dark) {
        darkColorScheme(
            primary = palette.accent, onPrimary = palette.onAccent,
            background = palette.background, onBackground = palette.textPrimary,
            surface = palette.surface, onSurface = palette.textPrimary,
            error = palette.error,
        )
    } else {
        lightColorScheme(
            primary = palette.accent, onPrimary = palette.onAccent,
            background = palette.background, onBackground = palette.textPrimary,
            surface = palette.surface, onSurface = palette.textPrimary,
            error = palette.error,
        )
    }

    CompositionLocalProvider(LocalPalette provides palette) {
        MaterialTheme(colorScheme = colorScheme, typography = appTypography(palette), content = content)
    }
}

/** Доступ: AppTheme.palette.accent / AppTheme.palette.stateListening. */
object AppTheme {
    val palette: Palette
        @androidx.compose.runtime.Composable get() = LocalPalette.current
}
