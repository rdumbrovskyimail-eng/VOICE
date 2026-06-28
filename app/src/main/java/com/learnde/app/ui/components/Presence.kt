package com.learnde.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.learnde.app.ui.theme.AppTheme
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
    Presence.Offline -> "Отключено"
    Presence.Connecting -> "Подключение…"
    Presence.Reconnecting -> "Восстановление…"
    Presence.Listening -> "Слушаю"
    Presence.Thinking -> "На связи"
    Presence.Speaking -> "Говорит…"
    Presence.Error -> "Ошибка"
}

@Composable
fun StatusLabel(presence: Presence, modifier: Modifier = Modifier) {
    val pal = AppTheme.palette
    val isActive = presence == Presence.Listening || presence == Presence.Speaking
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(if (isActive) pal.textPrimary else pal.surfaceElevated).border(1.dp, pal.outline, CircleShape))
        Spacer(Modifier.width(Space.sm))
        Text(presenceLabel(presence), style = MaterialTheme.typography.labelLarge, color = pal.textPrimary)
    }
}