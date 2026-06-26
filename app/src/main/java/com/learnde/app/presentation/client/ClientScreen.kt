// Путь: app/src/main/java/com/learnde/app/presentation/client/ClientScreen.kt
//
// ЭТАП 2 — главный экран универсального голосового клиента. Три зоны по вертикали:
//   • 20% — поле ввода системного промпта + кнопка «Применить»: в активной сессии
//           пересоздаёт её с новым промптом (например, промпт по фото учебника).
//   • 20% — аудио-реактивный орб (VoiceVisualizer). Тап по орбу = подключить/отключить.
//   • 60% — чат: транскрипция речи в реальном времени + поле ввода текста (модель «читает»
//           сообщение как SMS) + кнопка микрофона.
// Применяет настройки отображения чата (шрифт, метки ролей, таймстемпы, авто-скролл).

package com.learnde.app.presentation.client

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.learnde.app.domain.model.ConversationMessage
import com.learnde.app.presentation.navigation.Routes
import com.learnde.app.session.SessionManager
import java.text.SimpleDateFormat
import java.util.Locale

private val BgColor = Color(0xFF0F1114)
private val AccentBlue = Color(0xFF6EA8FE)
private val SurfaceCtrl = Color(0xFF181B20)
private val TextDim = Color(0xFF8B919A)

@Composable
fun ClientScreen(
    navController: NavController,
    viewModel: ClientViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val amplitude by viewModel.amplitude.collectAsStateWithLifecycle()
    val chatPrefs by viewModel.chatPrefs.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var promptText by remember { mutableStateOf("") }
    var chatInput by remember { mutableStateOf("") }

    // Один раз подставляем активный промпт (если сессия уже шла).
    LaunchedEffect(state.activePrompt) {
        if (state.activePrompt.isNotEmpty() && promptText.isEmpty()) {
            promptText = state.activePrompt
        }
    }

    fun hasRecord() = ContextCompat.checkSelfPermission(
        context, Manifest.permission.RECORD_AUDIO
    ) == PackageManager.PERMISSION_GRANTED

    fun neededPerms(): Array<String> {
        val list = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= 33) {
            list.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        return list.toTypedArray()
    }

    val connectLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result[Manifest.permission.RECORD_AUDIO] == true) viewModel.toggleConnection()
    }

    val micLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) viewModel.toggleMic() }

    fun onToggleConnection() {
        if (hasRecord()) viewModel.toggleConnection() else connectLauncher.launch(neededPerms())
    }

    fun onToggleMic() {
        if (hasRecord()) viewModel.toggleMic() else micLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(BgColor)
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .imePadding()
                .padding(horizontal = 14.dp)
        ) {
            // ─────── Slim header ───────
            Header(
                state = state,
                onToggleConnection = { onToggleConnection() },
                onSettings = { navController.navigate(Routes.SETTINGS) },
            )

            // ─────── 20% — Системный промпт ───────
            Box(Modifier.weight(0.20f).fillMaxWidth().padding(vertical = 6.dp)) {
                PromptZone(
                    value = promptText,
                    onValueChange = { promptText = it },
                    onApply = { viewModel.applyPrompt(promptText.trim()) },
                )
            }

            // ─────── 20% — Орб (тап = подключить/отключить) ───────
            Box(
                Modifier
                    .weight(0.20f)
                    .fillMaxWidth()
                    .clickable { onToggleConnection() },
                contentAlignment = Alignment.Center,
            ) {
                VoiceVisualizer(
                    amplitude = amplitude,
                    isActive = state.isConnected || state.isConnecting,
                    modifier = Modifier.fillMaxSize(),
                )
                if (!state.isConnected && !state.isConnecting) {
                    Text("Нажмите, чтобы начать", color = TextDim, fontSize = 13.sp)
                }
            }

            // ─────── 60% — Чат ───────
            Column(Modifier.weight(0.60f).fillMaxWidth()) {
                state.error?.let {
                    Text(
                        it,
                        color = Color(0xFFEF5350),
                        fontSize = 13.sp,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }

                ChatList(
                    messages = state.transcript,
                    prefs = chatPrefs,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )

                InputRow(
                    value = chatInput,
                    onValueChange = { chatInput = it },
                    onSend = {
                        viewModel.sendText(chatInput)
                        chatInput = ""
                    },
                    isMicActive = state.isMicActive,
                    onToggleMic = { onToggleMic() },
                )
            }
        }
    }
}

@Composable
private fun Header(
    state: SessionManager.State,
    onToggleConnection: () -> Unit,
    onSettings: () -> Unit,
) {
    val dotColor = when {
        state.isConnected -> Color(0xFF66BB6A)
        state.isConnecting -> Color(0xFFFFC107)
        else -> Color(0xFF9E9E9E)
    }
    Row(
        Modifier
            .fillMaxWidth()
            .height(52.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            when {
                state.isConnected -> if (state.isAiSpeaking) "Ассистент говорит…" else "На связи"
                state.isConnecting -> "Подключение…"
                else -> "Отключено"
            },
            color = TextDim,
            fontSize = 13.sp,
        )

        Spacer(Modifier.weight(1f))

        IconButton(onClick = onToggleConnection) {
            Icon(
                if (state.isConnected || state.isConnecting) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                contentDescription = "Подключить/Отключить",
                tint = if (state.isConnected || state.isConnecting) Color(0xFFEF5350) else AccentBlue,
            )
        }
        IconButton(onClick = onSettings) {
            Icon(Icons.Filled.Settings, contentDescription = "Настройки", tint = TextDim)
        }
    }
}

@Composable
private fun PromptZone(
    value: String,
    onValueChange: (String) -> Unit,
    onApply: () -> Unit,
) {
    Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f).fillMaxSize(),
            placeholder = { Text("Системный промпт сессии…", color = TextDim, fontSize = 13.sp) },
            textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 14.sp),
            shape = RoundedCornerShape(12.dp),
            colors = darkFieldColors(),
        )
        Spacer(Modifier.width(8.dp))
        IconButton(
            onClick = onApply,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(AccentBlue),
        ) {
            Icon(Icons.Filled.Send, contentDescription = "Применить промпт", tint = Color.White)
        }
    }
}

@Composable
private fun ChatList(
    messages: List<ConversationMessage>,
    prefs: ChatPrefs,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val timeFormatter = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    LaunchedEffect(messages.size, prefs.autoScroll) {
        if (prefs.autoScroll && messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }
    LazyColumn(
        modifier = modifier,
        state = listState,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(messages) { msg -> MessageBubble(msg, prefs, timeFormatter) }
    }
}

@Composable
private fun MessageBubble(
    msg: ConversationMessage,
    prefs: ChatPrefs,
    timeFormatter: SimpleDateFormat,
) {
    val isUser = msg.role == ConversationMessage.ROLE_USER
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
    ) {
        if (prefs.showRoleLabels) {
            Text(
                if (isUser) "Вы" else "Ассистент",
                color = TextDim,
                fontSize = (11 * prefs.fontScale).sp,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
            )
        }
        Box(
            Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(if (isUser) AccentBlue.copy(alpha = 0.22f) else SurfaceCtrl)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text(msg.text, color = Color(0xFFF0F1F3), fontSize = (14 * prefs.fontScale).sp)
        }
        if (prefs.showTimestamps) {
            Text(
                timeFormatter.format(java.util.Date(msg.timestamp)),
                color = TextDim,
                fontSize = (10 * prefs.fontScale).sp,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
            )
        }
    }
}

@Composable
private fun InputRow(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    isMicActive: Boolean,
    onToggleMic: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text("Сообщение…", color = TextDim, fontSize = 14.sp) },
            textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 14.sp),
            shape = RoundedCornerShape(24.dp),
            singleLine = true,
            colors = darkFieldColors(),
        )
        Spacer(Modifier.width(6.dp))
        IconButton(
            onClick = onSend,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(AccentBlue),
        ) {
            Icon(Icons.Filled.Send, contentDescription = "Отправить", tint = Color.White)
        }
        Spacer(Modifier.width(6.dp))
        IconButton(
            onClick = onToggleMic,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(if (isMicActive) Color(0xFF4CAF50) else SurfaceCtrl),
        ) {
            Icon(
                if (isMicActive) Icons.Filled.Mic else Icons.Filled.MicOff,
                contentDescription = "Микрофон",
                tint = if (isMicActive) Color.White else TextDim,
            )
        }
    }
}

@Composable
private fun darkFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    cursorColor = AccentBlue,
    focusedBorderColor = AccentBlue,
    unfocusedBorderColor = Color(0x33FFFFFF),
    focusedContainerColor = Color(0xFF131519),
    unfocusedContainerColor = Color(0xFF131519),
)
