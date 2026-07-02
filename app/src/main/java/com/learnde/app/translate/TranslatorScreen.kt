// Путь: app/src/main/java/com/learnde/app/translate/TranslatorScreen.kt
//
// ★ ЗАМЕНА ★ Редизайн под новый TranslatorManager:
//   • лента истории сегментов вместо двух перезатираемых карточек;
//   • живой сегмент подсвечен и дописывается по мере перевода;
//   • чип «восстановление…» при автопереподключении (WS живёт ~10 мин);
//   • экран не гаснет (keepScreenOn), автоскролл к последней реплике;
//   • языковая пара берётся из настроек (state.langA / state.langB).

package com.learnde.app.translate

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.learnde.app.ui.theme.AppTheme
import com.learnde.app.ui.theme.Radius
import com.learnde.app.ui.theme.Space
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TranslatorScreen(
    onBack: () -> Unit,
    viewModel: TranslatorViewModel = hiltViewModel(),
) {
    val pal = AppTheme.palette
    val context = LocalContext.current
    val view = LocalView.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val amplitude by viewModel.amplitude.collectAsStateWithLifecycle()

    fun hasMic() = ContextCompat.checkSelfPermission(
        context, Manifest.permission.RECORD_AUDIO
    ) == PackageManager.PERMISSION_GRANTED

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) viewModel.startAuto() }

    LaunchedEffect(Unit) {
        if (hasMic()) viewModel.startAuto()
        else permLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    // Живой перевод — экран не должен гаснуть посреди разговора.
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose {
            view.keepScreenOn = false
            viewModel.stop()
        }
    }

    val running = state.status != TranslatorStatus.Idle && state.status != TranslatorStatus.Error
    val hasContent = state.history.isNotEmpty() ||
        state.liveSource.isNotBlank() || state.liveTranslation.isNotBlank()

    Box(Modifier.fillMaxSize().background(pal.background)) {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {

            // ── Шапка ──────────────────────────────────────────────
            Row(
                Modifier.fillMaxWidth()
                    .padding(horizontal = Space.lg, vertical = Space.sm)
                    .height(56.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(40.dp).clip(RoundedCornerShape(Radius.md))
                        .background(pal.surfaceElevated)
                        .clickable { viewModel.stop(); onBack() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack, "Назад",
                        tint = pal.textSecondary, modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(Modifier.width(Space.md))
                Column(Modifier.weight(1f)) {
                    Text("Переводчик", style = MaterialTheme.typography.titleLarge, color = pal.textPrimary)
                    Text(
                        "${TranslatorLanguages.name(state.langA)} ⇄ ${TranslatorLanguages.name(state.langB)} · синхронно",
                        style = MaterialTheme.typography.labelSmall, color = pal.textDim
                    )
                }
                if (state.reconnecting) ReconnectChip()
            }

            HorizontalDivider(color = pal.outline, thickness = 1.dp)

            // ── Контент: пустое состояние или лента реплик ─────────
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (!hasContent) {
                    EmptyState(amplitude, running, statusText(state))
                } else {
                    val listState = rememberLazyListState()
                    val liveVisible =
                        state.liveSource.isNotBlank() || state.liveTranslation.isNotBlank()
                    // Автоскролл к последней реплике при любом дописывании.
                    LaunchedEffect(state.history.size, state.liveSource.length, state.liveTranslation.length) {
                        val last = state.history.size + (if (liveVisible) 1 else 0) - 1
                        if (last >= 0) listState.scrollToItem(last)
                    }
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = Space.lg, vertical = Space.md),
                        verticalArrangement = Arrangement.spacedBy(Space.sm)
                    ) {
                        items(state.history) { seg -> SegmentCard(seg) }
                        if (liveVisible) item { LiveCard(state) }
                    }
                }
            }

            // ── Ошибка + «Повторить» ───────────────────────────────
            state.error?.let { err ->
                Row(
                    Modifier.fillMaxWidth()
                        .padding(horizontal = Space.lg, vertical = Space.sm)
                        .clip(RoundedCornerShape(Radius.md))
                        .background(pal.errorBg).padding(Space.md),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        err, color = pal.error,
                        style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(Space.md))
                    Text(
                        "Повторить", color = pal.error,
                        style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clip(RoundedCornerShape(Radius.pill))
                            .clickable {
                                if (hasMic()) viewModel.retry()
                                else permLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                            .padding(horizontal = Space.sm, vertical = Space.xs)
                    )
                }
            }

            // ── Нижняя панель управления ───────────────────────────
            Column(
                Modifier.fillMaxWidth().padding(horizontal = Space.xl, vertical = Space.lg),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (running) {
                    DirectionChip(state)
                    Spacer(Modifier.height(Space.md))
                }
                Box(
                    Modifier.size(68.dp).clip(CircleShape)
                        .background(if (running) pal.errorBg else pal.accentBlue)
                        .clickable {
                            when {
                                running -> viewModel.stop()
                                hasMic() -> viewModel.retry()
                                else -> permLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (running) Icons.Filled.Stop else Icons.Filled.Mic,
                        contentDescription = if (running) "Остановить" else "Начать",
                        tint = if (running) pal.error else Color.White,
                        modifier = Modifier.size(30.dp)
                    )
                }
                val bottomLabel = when {
                    !running -> "Нажмите, чтобы начать"
                    hasContent -> statusText(state)
                    else -> null // в пустом состоянии статус уже крупно в центре
                }
                bottomLabel?.let {
                    Spacer(Modifier.height(Space.sm))
                    Text(
                        it, style = MaterialTheme.typography.bodySmall,
                        color = pal.textDim, textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

private fun statusText(state: TranslatorUiState): String = when (state.status) {
    TranslatorStatus.Idle -> "Готов к работе"
    TranslatorStatus.Connecting -> "Подключение…"
    TranslatorStatus.Listening ->
        "Слушаю — ${TranslatorLanguages.name(state.langA)} или ${TranslatorLanguages.name(state.langB)}"
    TranslatorStatus.Translating -> "Перевожу…"
    TranslatorStatus.Error -> "Ошибка соединения"
}

@Composable
private fun DirectionChip(state: TranslatorUiState) {
    val pal = AppTheme.palette
    val a = state.langA.uppercase(); val b = state.langB.uppercase()
    val label = when (state.direction) {
        Direction.A_TO_B -> "$a → $b"
        Direction.B_TO_A -> "$b → $a"
        null -> "$a ⇄ $b"
    }
    Box(
        Modifier.clip(RoundedCornerShape(Radius.pill)).background(pal.accentBlueBg)
            .padding(horizontal = Space.lg, vertical = 6.dp)
    ) {
        Text(
            label, style = MaterialTheme.typography.labelLarge,
            color = pal.accentBlue, fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun ReconnectChip() {
    val pal = AppTheme.palette
    Box(
        Modifier.clip(RoundedCornerShape(Radius.pill)).background(pal.surfaceElevated)
            .padding(horizontal = Space.md, vertical = 5.dp)
    ) {
        Text("восстановление…", style = MaterialTheme.typography.labelSmall, color = pal.textSecondary)
    }
}

@Composable
private fun EmptyState(amplitude: Float, active: Boolean, status: String) {
    val pal = AppTheme.palette
    Column(
        Modifier.fillMaxSize().padding(Space.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Orb(amplitude, active)
        Spacer(Modifier.height(Space.xl))
        Text(
            status, style = MaterialTheme.typography.bodyLarge,
            color = pal.textSecondary, textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(Space.sm))
        Text(
            "Говорите естественно. Перевод озвучится через 2–4 секунды.",
            style = MaterialTheme.typography.bodySmall,
            color = pal.textDim, textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun SegmentCard(seg: TranslationSegment) {
    val pal = AppTheme.palette
    val time = remember(seg.timeMs) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(seg.timeMs))
    }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.md))
            .background(pal.surface).padding(Space.md)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${seg.fromCode.uppercase()} → ${seg.toCode.uppercase()}",
                style = MaterialTheme.typography.labelSmall,
                color = pal.accentBlue, fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.weight(1f))
            Text(time, style = MaterialTheme.typography.labelSmall, color = pal.textDim)
        }
        if (seg.source.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(seg.source, fontSize = 13.sp, lineHeight = 18.sp, color = pal.textSecondary)
        }
        Spacer(Modifier.height(4.dp))
        Text(seg.translation, style = MaterialTheme.typography.bodyLarge, color = pal.textPrimary)
    }
}

@Composable
private fun LiveCard(state: TranslatorUiState) {
    val pal = AppTheme.palette
    val label = when (state.direction) {
        Direction.A_TO_B -> "${state.langA.uppercase()} → ${state.langB.uppercase()}"
        Direction.B_TO_A -> "${state.langB.uppercase()} → ${state.langA.uppercase()}"
        null -> "СЕЙЧАС"
    }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.md))
            .background(pal.accentBlueBg).padding(Space.md)
    ) {
        Text(
            label, style = MaterialTheme.typography.labelSmall,
            color = pal.accentBlue, fontWeight = FontWeight.SemiBold
        )
        if (state.liveSource.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(state.liveSource, fontSize = 13.sp, lineHeight = 18.sp, color = pal.textSecondary)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            state.liveTranslation.ifBlank { "…" },
            style = MaterialTheme.typography.bodyLarge, color = pal.textPrimary
        )
    }
}

@Composable
private fun Orb(amplitude: Float, active: Boolean) {
    val pal = AppTheme.palette
    val infinite = rememberInfiniteTransition(label = "orb")
    val pulse by infinite.animateFloat(
        initialValue = 0.96f, targetValue = 1.05f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Reverse),
        label = "pulse"
    )
    val scale = (1f + amplitude * 0.6f) * (if (active) pulse else 1f)
    Box(Modifier.size(180.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(120.dp).scale(scale).clip(CircleShape)
                .background(if (active) pal.accentBlue else pal.surfaceElevated)
        )
    }
}
