// Путь: app/src/main/java/com/learnde/app/presentation/client/VoiceVisualizer.kt
//
// НОВЫЙ ФАЙЛ. Аудио-реактивный «орб» в стиле Gemini Voice.
//   • Реагирует на реальную амплитуду звука (amplitude 0..1 из SessionManager — RMS PCM).
//   • Свечение собрано из нескольких полупрозрачных радиальных градиентов (Modifier.blur
//     требует API 31+, а minSdk=26 — поэтому имитируем glow слоями).
//   • В покое мягко «дышит»; при активной сессии пульсирует ярче и крупнее.

package com.learnde.app.presentation.client

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

@Composable
fun VoiceVisualizer(
    amplitude: Float,
    isActive: Boolean,
    modifier: Modifier = Modifier,
) {
    val infinite = rememberInfiniteTransition(label = "orb")
    val breath by infinite.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(2600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "breath",
    )

    // Сглаженная реакция на амплитуду (резкие скачки RMS превращаем в плавную пульсацию).
    val reactive by animateFloatAsState(
        targetValue = if (isActive) amplitude.coerceIn(0f, 1f) else 0f,
        animationSpec = tween(120),
        label = "amp",
    )

    // Палитра: активная — тёплый Gemini-голубой со светлым ядром; неактивная — приглушённая.
    val core = if (isActive) Color(0xFFEAF2FF) else Color(0xFF8A93A3)
    val mid = if (isActive) Color(0xFF6EA8FE) else Color(0xFF55607A)
    val edge = if (isActive) Color(0xFF3B6FE0) else Color(0xFF3A4256)

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val base = minOf(size.width, size.height) / 2f
            // Защита: при нулевом размере Canvas radialGradient с radius=0 бросает исключение.
            if (base <= 0f) return@Canvas
            val scale = breath * (1f + reactive * 0.55f)
            val r = (base * 0.60f * scale).coerceAtLeast(1f)
            val center = Offset(cx, cy)

            // Внешнее свечение — несколько слоёв вместо blur.
            for (i in 3 downTo 1) {
                val rr = (r * (1f + i * 0.22f)).coerceAtLeast(0.1f)
                val a = (0.10f * i * (0.5f + reactive)).coerceIn(0f, 0.6f)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(mid.copy(alpha = a), Color.Transparent),
                        center = center,
                        radius = rr,
                    ),
                    radius = rr,
                    center = center,
                )
            }

            // Тело орба (смещённый центр градиента даёт объём).
            val bodyCenter = Offset(cx - r * 0.16f, cy - r * 0.20f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(core, mid, edge, edge.copy(alpha = 0f)),
                    center = bodyCenter,
                    radius = r * 1.25f,
                ),
                radius = r,
                center = center,
            )

            // Блик.
            val hr = r * 0.42f
            val hl = Offset(cx - r * 0.30f, cy - r * 0.34f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color.White.copy(alpha = 0.55f), Color.Transparent),
                    center = hl,
                    radius = hr,
                ),
                radius = hr,
                center = hl,
            )
        }
    }
}
