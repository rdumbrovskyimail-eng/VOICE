package com.learnde.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.learnde.app.ui.theme.AppTheme
import com.learnde.app.ui.theme.Radius
import com.learnde.app.ui.theme.Space

enum class Presence { Offline, Connecting, Reconnecting, Listening, Thinking, Speaking, Error }

fun presenceOf(isConnected: Boolean, isConnecting: Boolean, isRecovering: Boolean, isMicActive: Boolean, isAiSpeaking: Boolean, hasError: Boolean): Presence = when {
    hasError && !isConnected && !isConnecting -> Presence.Error
    isRecovering -> Presence.Reconnecting
    isConnecting -> Presence.Connecting
    !isConnected -> Presence.Offline
    isAiSpeaking -> Presence.Speaking
    isMicActive -> Presence.Listening
    else -> Presence.Thinking
}

private fun presenceLabel(p: Presence): String = when (p) {
    Presence.Offline -> "Сессия завершена"
    Presence.Connecting -> "Подключение..."
    Presence.Reconnecting -> "Восстановление..."
    Presence.Listening -> "Слушаю вас"
    Presence.Thinking -> "Обработка"
    Presence.Speaking -> "Ассистент отвечает"
    Presence.Error -> "Сбой сети"
}

@Composable
fun StatusLabel(presence: Presence, modifier: Modifier = Modifier) {
    val pal = AppTheme.palette
    val (bgColor, textColor) = when (presence) {
        Presence.Listening -> pal.accentGreenBg to pal.accentGreen
        Presence.Speaking -> pal.accentBlueBg to pal.accentBlue
        Presence.Error -> pal.errorBg to pal.error
        Presence.Offline -> pal.surface to pal.textSecondary
        else -> pal.surfaceElevated to pal.textPrimary
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(Radius.sm))
            .background(bgColor)
            .padding(horizontal = Space.sm, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (presence == Presence.Speaking) {
            GeminiSpeakingDots()
            Spacer(Modifier.width(6.dp))
        }
        Text(presenceLabel(presence), style = MaterialTheme.typography.labelLarge, color = textColor)
    }
}

@Composable
private fun GeminiSpeakingDots() {
    val pal = AppTheme.palette
    val infiniteTransition = rememberInfiniteTransition(label = "dots")

    @Composable
    fun animateDot(delay: Int): Float {
        val scale by infiniteTransition.animateFloat(
            initialValue = 0.4f, targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(500, delayMillis = delay, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ), label = "dot"
        )
        return scale
    }

    Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(4.dp).clip(CircleShape).background(pal.accentBlue.copy(alpha = animateDot(0))))
        Box(Modifier.size(4.dp).clip(CircleShape).background(pal.accentBlue.copy(alpha = animateDot(150))))
        Box(Modifier.size(4.dp).clip(CircleShape).background(pal.accentBlue.copy(alpha = animateDot(300))))
    }
}