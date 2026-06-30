package com.learnde.app.translate

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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

private const val BIDIRECTIONAL = true

@Composable
fun TranslatorScreen(
    onBack: () -> Unit,
    viewModel: TranslatorViewModel = hiltViewModel(),
) {
    val pal = AppTheme.palette
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val amplitude by viewModel.amplitude.collectAsStateWithLifecycle()

    fun hasMic() = ContextCompat.checkSelfPermission(
        context, Manifest.permission.RECORD_AUDIO
    ) == PackageManager.PERMISSION_GRANTED

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) viewModel.startAuto(BIDIRECTIONAL) }

    LaunchedEffect(Unit) {
        if (hasMic()) viewModel.startAuto(BIDIRECTIONAL)
        else permLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }
    DisposableEffect(Unit) { onDispose { viewModel.stop() } }

    val running = state.status != TranslatorStatus.Idle && state.status != TranslatorStatus.Error

    Box(Modifier.fillMaxSize().background(pal.background)) {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {

            Row(
                Modifier.fillMaxWidth().padding(horizontal = Space.lg, vertical = Space.sm).height(56.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(40.dp).clip(RoundedCornerShape(Radius.md))
                        .background(pal.surfaceElevated)
                        .clickable { viewModel.stop(); onBack() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад", tint = pal.textSecondary, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(Space.md))
                Column {
                    Text("Переводчик", style = MaterialTheme.typography.titleLarge, color = pal.textPrimary)
                    Text("Русский ⇄ Deutsch · синхронно", style = MaterialTheme.typography.labelSmall, color = pal.textDim)
                }
            }

            HorizontalDivider(color = pal.outline, thickness = 1.dp)

            Column(
                Modifier.weight(1f).fillMaxWidth().padding(Space.xl),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                DirectionChip(state.direction)
                Spacer(Modifier.height(Space.xl))
                Orb(amplitude = amplitude, active = running)
                Spacer(Modifier.height(Space.xl))
                Text(
                    statusText(state.status),
                    style = MaterialTheme.typography.bodyLarge,
                    color = pal.textSecondary,
                    textAlign = TextAlign.Center
                )
            }

            if (state.sourceText.isNotEmpty() || state.translationText.isNotEmpty()) {
                Column(Modifier.fillMaxWidth().padding(horizontal = Space.lg)) {
                    TranscriptCard("Вы сказали", state.sourceText, pal.surface, pal.textSecondary)
                    Spacer(Modifier.height(Space.sm))
                    TranscriptCard("Перевод", state.translationText, pal.accentBlueBg, pal.accentBlue)
                }
            }

            state.error?.let { err ->
                Row(
                    Modifier.fillMaxWidth().padding(Space.lg).clip(RoundedCornerShape(Radius.md))
                        .background(pal.errorBg).padding(Space.md),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(err, color = pal.error, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                }
            }

            Box(Modifier.fillMaxWidth().padding(Space.xl), contentAlignment = Alignment.Center) {
                Box(
                    Modifier.size(72.dp).clip(CircleShape)
                        .background(if (running) pal.errorBg else pal.accentBlue)
                        .clickable {
                            when {
                                running -> viewModel.stop()
                                hasMic() -> viewModel.retry(BIDIRECTIONAL)
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
            }
        }
    }
}

private fun statusText(s: TranslatorStatus): String = when (s) {
    TranslatorStatus.Idle -> "Нажмите микрофон, чтобы начать"
    TranslatorStatus.Connecting -> "Подключение…"
    TranslatorStatus.Listening -> "Слушаю — говорите по-русски или по-немецки"
    TranslatorStatus.Translating -> "Перевод…"
    TranslatorStatus.Error -> "Ошибка соединения"
}

@Composable
private fun DirectionChip(direction: Direction?) {
    val pal = AppTheme.palette
    val label = when (direction) {
        Direction.RU_TO_DE -> "RU → DE"
        Direction.DE_TO_RU -> "DE → RU"
        null -> "RU ⇄ DE"
    }
    Box(
        Modifier.clip(RoundedCornerShape(Radius.pill)).background(pal.accentBlueBg)
            .padding(horizontal = Space.lg, vertical = Space.sm)
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = pal.accentBlue, fontWeight = FontWeight.SemiBold)
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

@Composable
private fun TranscriptCard(label: String, text: String, bg: Color, accent: Color) {
    val pal = AppTheme.palette
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.md)).background(bg).padding(Space.md)
    ) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = accent.copy(alpha = 0.75f))
        Spacer(Modifier.height(2.dp))
        Text(text.ifEmpty { "…" }, style = MaterialTheme.typography.bodyLarge, color = pal.textPrimary)
    }
}