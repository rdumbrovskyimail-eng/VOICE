// Путь: app/src/main/java/com/learnde/app/ui/components/Presence.kt
//
// Signature-элемент: индикатор «присутствия» ассистента + строка статуса.
// Цвет и движение кодируют состояние (спит/слушает/думает/говорит). Всё на токенах Theme.kt;
// декомпонован от State (принимает простые поля) — переиспользуем и не тянет слои.

package com.learnde.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.learnde.app.ui.theme.AppTheme
import com.learnde.app.ui.theme.Motion
import com.learnde.app.ui.theme.Space
import kotlin.math.min

/** Состояние присутствия — единая модель для цвета/текста/движения. */
enum class Presence { Offline, Connecting, Reconnecting, Listening, Thinking, Speaking, Error }

/** Вывод присутствия из полей сессии (без зависимости от самого класса State). */
fun presenceOf(
    isConnected: Boolean,
    isConnecting: Boolean,
    isRecovering: Boolean,
    isMicActive: Boolean,
    isAiSpeaking: Boolean,
    hasError: Boolean,
): Presence = when {
    hasError && !isConnected && !isConnecting -> Presence.Error
    isRecovering -> Presence.Reconnecting
    isConnecting -> Presence.Connecting
    !isConnected -> Presence.Offline
    isAiSpeaking -> Presence.Speaking
    isMicActive -> Presence.Listening
    else -> Presence.Thinking
}

@Composable
private fun presenceColor(p: Presence): Color {
    val pal = AppTheme.palette
    return when (p) {
        Presence.Offline -> pal.stateIdle
        Presence.Connecting, Presence.Reconnecting -> pal.stateThinking
        Presence.Listening -> pal.stateListening
        Presence.Thinking -> pal.stateThinking
        Presence.Speaking -> pal.stateSpeaking
        Presence.Error -> pal.error
    }
}

private fun presenceLabel(p: Presence): String = when (p) {
    Presence.Offline -> "Отключено"
    Presence.Connecting -> "Подключение…"
    Presence.Reconnecting -> "Восстановление связи…"
    Presence.Listening -> "Слушаю"
    Presence.Thinking -> "На связи"
    Presence.Speaking -> "Ассистент говорит…"
    Presence.Error -> "Ошибка подключения"
}

/** Точка + подпись статуса, цвет завязан на присутствие. */
@Composable
fun StatusLabel(presence: Presence, modifier: Modifier = Modifier) {
    val color by animateColorAsState(presenceColor(presence), tween(Motion.medium), label = "statusColor")
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(Space.sm))
        Text(presenceLabel(presence), style = MaterialTheme.typography.labelLarge, color = AppTheme.palette.textSecondary)
    }
}

/**
 * Индикатор присутствия: «дышащее» ядро + реактивные кольца.
 *  • Offline/Thinking — медленное спокойное дыхание.
 *  • Listening/Speaking — кольца расходятся по амплитуде, ярче свечение.
 *  • Цвет плавно перетекает между состояниями.
 * amplitudeProvider читается ВНУТРИ Canvas → перерисовка без рекомпозиции.
 */
@Composable
fun PresenceIndicator(
    presence: Presence,
    amplitudeProvider: () -> Float,
    modifier: Modifier = Modifier,
) {
    val color by animateColorAsState(presenceColor(presence), tween(Motion.medium), label = "presenceColor")

    val breath = rememberInfiniteTransition(label = "breath")
    val breathScale by breath.animateFloat(
        initialValue = 0.94f, targetValue = 1.06f,
        animationSpec = infiniteRepeatable(tween(Motion.breath, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breathScale",
    )

    val active = presence == Presence.Listening || presence == Presence.Speaking
    val activeAmp by animateFloatAsState(if (active) 1f else 0f, tween(Motion.medium), label = "activeAmp")

    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val amp = amplitudeProvider().coerceIn(0f, 1f) * activeAmp
            val c = Offset(size.width / 2f, size.height / 2f)
            val unit = min(size.width, size.height) / 2f

            val glowR = unit * (0.55f + 0.35f * amp)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(color.copy(alpha = 0.28f + 0.30f * amp), Color.Transparent),
                    center = c, radius = glowR,
                ),
                radius = glowR, center = c,
            )

            if (activeAmp > 0.01f) {
                for (i in 1..2) {
                    val t = i / 3f
                    val r = unit * (0.42f + (0.45f * amp + 0.06f) * (1f + t))
                    drawCircle(
                        color = color.copy(alpha = (0.35f - 0.12f * i) * activeAmp),
                        radius = r, center = c, style = Stroke(width = 2f),
                    )
                }
            }

            val coreR = unit * 0.34f * breathScale * (1f + 0.18f * amp)
            drawCircle(color = color.copy(alpha = 0.92f), radius = coreR, center = c)
            drawCircle(color = Color.White.copy(alpha = 0.10f + 0.18f * amp), radius = coreR * 0.55f, center = c)
        }
    }
}
