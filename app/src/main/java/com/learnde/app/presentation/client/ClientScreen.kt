package com.learnde.app.presentation.client

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.learnde.app.domain.model.ConversationMessage
import com.learnde.app.presentation.navigation.Routes
import com.learnde.app.session.SessionManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

private val BgColor = Color(0xFF0F1114)
private val AccentBlue = Color(0xFF6EA8FE)
private val SurfaceCtrl = Color(0xFF181B20)
private val TextDim = Color(0xFF8B919A)
private val SuccessGreen = Color(0xFF4CAF50)

@Composable
fun ClientScreen(
    navController: NavController,
    viewModel: ClientViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val amplitude by viewModel.amplitude.collectAsStateWithLifecycle()
    val chatPrefs by viewModel.chatPrefs.collectAsStateWithLifecycle()
    val historyMessages by viewModel.historyMessages.collectAsStateWithLifecycle()
    val historyInfo by viewModel.historyInfo.collectAsStateWithLifecycle()
    val isHistory = state.mode == ClientMode.HISTORY
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var promptText by remember { mutableStateOf("") }
    var chatInput by remember { mutableStateOf("") }
    var showPromptSuccess by remember { mutableStateOf(false) }
    var promptAttachments by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var chatAttachments by remember { mutableStateOf<List<Uri>>(emptyList()) }
    val promptPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { u ->
        promptAttachments = (promptAttachments + u).distinct().take(20)
    }
    val chatPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { u ->
        chatAttachments = (chatAttachments + u).distinct().take(20)
    }

    LaunchedEffect(state.activePrompt) {
        if (state.activePrompt.isNotEmpty() && promptText.isEmpty()) {
            promptText = state.activePrompt
        }
    }

    fun hasRecord() = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    fun neededPerms(): Array<String> {
        val list = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= 33) list.add(Manifest.permission.POST_NOTIFICATIONS)
        return list.toTypedArray()
    }

    val connectLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result[Manifest.permission.RECORD_AUDIO] == true) viewModel.toggleConnection()
    }
    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> 
        if (granted) viewModel.toggleMic() 
    }

    fun onToggleConnection() {
        if (hasRecord()) viewModel.toggleConnection() else connectLauncher.launch(neededPerms())
    }

    fun onToggleMic() {
        if (hasRecord()) viewModel.toggleMic() else micLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    Box(modifier = Modifier.fillMaxSize().background(BgColor)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .imePadding()
        ) {
            // 1. HEADER
            Header(
                state = state,
                onToggleConnection = { onToggleConnection() },
                onToggleHistory = { viewModel.toggleHistoryMode() },
                onSettings = { navController.navigate(Routes.SETTINGS) },
                modifier = Modifier.padding(horizontal = 14.dp)
            )

            // 2. PROMPT ZONE (скрыт в History) / HISTORY CONTROLS
            if (isHistory) HistoryControls(
                info = historyInfo,
                onApplyPrompt = { viewModel.applyHistoryPrompt(it) },
                onClear = { viewModel.clearHistory() },
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            )
            if (!isHistory) Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) {
                PromptZone(
                    value = promptText,
                    onValueChange = { promptText = it },
                    onApply = {
                        viewModel.applyPrompt(promptText.trim(), promptAttachments)
                        promptAttachments = emptyList()
                        scope.launch {
                            showPromptSuccess = true
                            delay(2500)
                            showPromptSuccess = false
                        }
                    },
                    attachments = promptAttachments,
                    onAttach = { promptPicker.launch(arrayOf("*/*")) },
                    onRemoveAttachment = { promptAttachments = promptAttachments - it },
                )
                
                AnimatedVisibility(
                    visible = showPromptSuccess,
                    enter = fadeIn() + slideInVertically { -20 },
                    exit = fadeOut() + slideOutVertically { -20 }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 8.dp, start = 4.dp)
                    ) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Промпт успешно установлен и применен", color = SuccessGreen, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }

            // 3. DASHBOARD (ЦИФЕРБЛАТ)
            AnimatedVisibility(visible = state.dashboardText.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF1E2229))
                        .padding(16.dp)
                ) {
                    Column {
                        Text("💡 От ассистента", color = AccentBlue, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(state.dashboardText, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }

            // 4. CHAT AREA (занимает оставшееся место)
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                ChatList(
                    messages = if (isHistory) historyMessages else state.transcript,
                    prefs = chatPrefs,
                    modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp)
                )
            }

            // 5. BOTTOM AREA: WAVES + INPUT
            Column(modifier = Modifier.fillMaxWidth()) {
                if (chatAttachments.isNotEmpty()) {
                    AttachmentChips(chatAttachments) { chatAttachments = chatAttachments - it }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    GeminiWaves(
                        amplitude = amplitude,
                        isActive = state.isConnected || state.isConnecting,
                        modifier = Modifier.fillMaxSize()
                    )
                    InputRow(
                        value = chatInput,
                        onValueChange = { chatInput = it },
                        onAttach = { chatPicker.launch(arrayOf("*/*")) },
                        onSend = {
                            viewModel.sendText(chatInput, chatAttachments)
                            chatInput = ""
                            chatAttachments = emptyList()
                        },
                        isMicActive = state.isMicActive,
                        onToggleMic = { onToggleMic() },
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun Header(state: SessionManager.State, onToggleConnection: () -> Unit, onToggleHistory: () -> Unit, onSettings: () -> Unit, modifier: Modifier = Modifier) {
    val dotColor = when {
        state.isConnected -> Color(0xFF66BB6A)
        state.isConnecting -> Color(0xFFFFC107)
        else -> Color(0xFF9E9E9E)
    }
    Row(modifier = modifier.fillMaxWidth().height(52.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(dotColor))
        Spacer(Modifier.width(8.dp))
        Text(
            when {
                state.isConnected -> if (state.isAiSpeaking) "Ассистент говорит…" else "На связи"
                state.isConnecting -> "Подключение…"
                else -> "Отключено"
            },
            color = TextDim, fontSize = 13.sp, fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onToggleConnection) {
            Icon(
                if (state.isConnected || state.isConnecting) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                contentDescription = "Подключить/Отключить",
                tint = if (state.isConnected || state.isConnecting) Color(0xFFEF5350) else AccentBlue,
            )
        }
        val historyOn = state.mode == ClientMode.HISTORY
        IconButton(onClick = onToggleHistory) {
            Icon(
                Icons.Filled.History,
                contentDescription = "Режим истории",
                tint = if (historyOn) AccentBlue else TextDim,
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
    attachments: List<Uri>,
    onAttach: () -> Unit,
    onRemoveAttachment: (Uri) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f).fillMaxHeight(),
                placeholder = { Text("Системный промпт сессии…", color = TextDim, fontSize = 13.sp) },
                textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 14.sp),
                shape = RoundedCornerShape(12.dp),
                colors = darkFieldColors(),
            )
            Spacer(Modifier.width(8.dp))
            Box {
                IconButton(
                    onClick = onAttach,
                    modifier = Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)).background(SurfaceCtrl),
                ) { Icon(Icons.Filled.AttachFile, contentDescription = "Прикрепить к промпту", tint = TextDim) }
                if (attachments.isNotEmpty()) {
                    Box(
                        Modifier.align(Alignment.TopEnd).padding(3.dp).size(18.dp)
                            .clip(CircleShape).background(AccentBlue),
                        contentAlignment = Alignment.Center
                    ) { Text("${attachments.size}", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                }
            }
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = onApply,
                modifier = Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)).background(AccentBlue),
            ) { Icon(Icons.Filled.Check, contentDescription = "Применить промпт", tint = Color.White) }
        }
        if (attachments.isNotEmpty()) AttachmentChips(attachments, onRemoveAttachment)
    }
}

@Composable
private fun ChatList(messages: List<ConversationMessage>, prefs: ChatPrefs, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    val timeFormatter = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    var stickToBottom by remember { mutableStateOf(true) }
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }.collect { scrolling ->
            if (!scrolling) stickToBottom = !listState.canScrollForward
        }
    }

    val lastText = messages.lastOrNull()?.text
    LaunchedEffect(messages.size, lastText, prefs.autoScroll) {
        if (prefs.autoScroll && stickToBottom && messages.isNotEmpty()) {
            listState.scrollToItem(messages.lastIndex, Int.MAX_VALUE)
        }
    }

    LazyColumn(
        modifier = modifier,
        state = listState,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        items(messages) { msg -> MessageBubble(msg, prefs, timeFormatter) }
    }
}

@Composable
private fun MessageBubble(msg: ConversationMessage, prefs: ChatPrefs, timeFormatter: SimpleDateFormat) {
    val isUser = msg.role == ConversationMessage.ROLE_USER
    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (isUser) Alignment.End else Alignment.Start) {
        if (prefs.showRoleLabels) {
            Text(if (isUser) "Вы" else "Ассистент", color = TextDim, fontSize = (11 * prefs.fontScale).sp, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
        }
        Box(
            Modifier.clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = if (isUser) 16.dp else 4.dp, bottomEnd = if (isUser) 4.dp else 16.dp))
                .background(if (isUser) AccentBlue.copy(alpha = 0.2f) else SurfaceCtrl.copy(alpha = 0.8f))
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Column {
                if (msg.attachmentUris.isNotEmpty()) AttachmentThumbnails(msg.attachmentUris)
                if (msg.text.isNotEmpty())
                    Text(msg.text, color = Color(0xFFF0F1F3), fontSize = (15 * prefs.fontScale).sp, lineHeight = 22.sp)
                if (msg.attachmentNote != null) {
                    if (msg.text.isNotEmpty() || msg.attachmentUris.isNotEmpty()) Spacer(Modifier.height(6.dp))
                    Text(msg.attachmentNote, color = Color(0xFFF0F1F3).copy(alpha = 0.75f), fontSize = (12 * prefs.fontScale).sp)
                }
            }
        }
    }
}

@Composable
private fun InputRow(value: String, onValueChange: (String) -> Unit, onAttach: () -> Unit, onSend: () -> Unit, isMicActive: Boolean, onToggleMic: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onAttach, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Filled.AttachFile, contentDescription = "Прикрепить", tint = TextDim)
        }
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text("Сообщение…", color = TextDim, fontSize = 14.sp) },
            textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 14.sp),
            shape = RoundedCornerShape(24.dp),
            singleLine = true,
            colors = darkFieldColors().copy(
                focusedContainerColor = Color(0xFF131519).copy(alpha = 0.8f),
                unfocusedContainerColor = Color(0xFF131519).copy(alpha = 0.8f)
            ),
        )
        Spacer(Modifier.width(8.dp))
        IconButton(
            onClick = onSend,
            modifier = Modifier.size(48.dp).clip(CircleShape).background(AccentBlue),
        ) {
            Icon(Icons.Filled.Send, contentDescription = "Отправить", tint = Color.White, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(8.dp))
        IconButton(
            onClick = onToggleMic,
            modifier = Modifier.size(56.dp).clip(CircleShape).background(if (isMicActive) Color(0xFF4CAF50) else SurfaceCtrl),
        ) {
            Icon(if (isMicActive) Icons.Filled.Mic else Icons.Filled.MicOff, contentDescription = "Микрофон", tint = if (isMicActive) Color.White else TextDim, modifier = Modifier.size(28.dp))
        }
    }
}

@Composable
private fun darkFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Color.White, unfocusedTextColor = Color.White,
    cursorColor = AccentBlue, focusedBorderColor = AccentBlue, unfocusedBorderColor = Color(0x33FFFFFF),
    focusedContainerColor = Color(0xFF131519), unfocusedContainerColor = Color(0xFF131519),
)

@Composable
private fun AttachmentChips(uris: List<Uri>, onRemove: (Uri) -> Unit) {
    val ctx = LocalContext.current
    LazyRow(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(uris, key = { it.toString() }) { uri ->
            val name = remember(uri) { fileLabel(ctx, uri) }
            Row(
                Modifier.clip(RoundedCornerShape(10.dp)).background(SurfaceCtrl)
                    .padding(start = 10.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.InsertDriveFile, null, tint = AccentBlue, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(6.dp))
                Text(name, color = Color.White, fontSize = 12.sp, maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 130.dp))
                Spacer(Modifier.width(2.dp))
                IconButton(onClick = { onRemove(uri) }, modifier = Modifier.size(20.dp)) {
                    Icon(Icons.Filled.Close, "Убрать", tint = TextDim, modifier = Modifier.size(13.dp))
                }
            }
        }
    }
}

private fun fileLabel(ctx: android.content.Context, uri: Uri): String = runCatching {
    ctx.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
        if (it.moveToFirst()) it.getString(0) else null
    }
}.getOrNull() ?: uri.lastPathSegment ?: "файл"

@Composable
private fun AttachmentThumbnails(uriStrings: List<String>) {
    val ctx = LocalContext.current
    Row(
        Modifier.horizontalScroll(rememberScrollState()).padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) { uriStrings.forEach { AttachmentTile(ctx, it) } }
}

@Composable
private fun AttachmentTile(ctx: android.content.Context, uriString: String) {
    val uri = remember(uriString) { Uri.parse(uriString) }
    val density = LocalDensity.current
    val reqPx = remember { with(density) { 72.dp.roundToPx() } * 2 }
    var bmp by remember(uriString) { mutableStateOf<android.graphics.Bitmap?>(null) }
    var tried by remember(uriString) { mutableStateOf(false) }

    LaunchedEffect(uriString) {
        bmp = runCatching { decodeThumb(ctx, uri, reqPx) }.getOrNull()
        tried = true
    }

    Box(
        Modifier.size(72.dp).clip(RoundedCornerShape(12.dp)).background(SurfaceCtrl),
        contentAlignment = Alignment.Center,
    ) {
        val b = bmp
        if (b != null) {
            Image(b.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    if (!tried) Icons.Filled.HourglassEmpty else Icons.Filled.InsertDriveFile,
                    null, tint = if (!tried) TextDim else AccentBlue, modifier = Modifier.size(22.dp),
                )
                if (tried) {
                    val ext = remember(uriString) { fileExt(ctx, uri) }
                    if (ext.isNotEmpty()) { Spacer(Modifier.height(4.dp)); Text(ext, color = TextDim, fontSize = 9.sp, maxLines = 1) }
                }
            }
        }
    }
}

private object ThumbCache {
    private val lru = object : android.util.LruCache<String, android.graphics.Bitmap>(6 * 1024 * 1024) {
        override fun sizeOf(key: String, v: android.graphics.Bitmap) = v.byteCount
    }
    fun get(k: String): android.graphics.Bitmap? = lru.get(k)
    fun put(k: String, v: android.graphics.Bitmap) { if (lru.get(k) == null) lru.put(k, v) }
}

private suspend fun decodeThumb(ctx: android.content.Context, uri: Uri, reqPx: Int): android.graphics.Bitmap? =
    withContext(Dispatchers.IO) {
        val key = "$uri@$reqPx"
        ThumbCache.get(key)?.let { return@withContext it }
        val cr = ctx.contentResolver
        if (!cr.getType(uri).orEmpty().startsWith("image/")) return@withContext null
        val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        cr.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0) return@withContext null
        var sample = 1
        val longest = maxOf(bounds.outWidth, bounds.outHeight)
        while (longest / (sample * 2) >= reqPx) sample *= 2
        val opts = android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }
        val bmp = cr.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it, null, opts) }
            ?: return@withContext null
        ThumbCache.put(key, bmp); bmp
    }

private fun fileExt(ctx: android.content.Context, uri: Uri): String {
    val name = runCatching {
        ctx.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        }
    }.getOrNull() ?: uri.lastPathSegment ?: ""
    return name.substringAfterLast('.', "").uppercase().take(4)
}

@Composable
private fun HistoryControls(
    info: HistoryInfo,
    onApplyPrompt: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var draft by remember { mutableStateOf("") }
    var confirmClear by remember { mutableStateOf(false) }

    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.History, null, tint = AccentBlue, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Режим истории — диалоги сохраняются", color = AccentBlue, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(8.dp))

        if (!info.locked) {
            Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    placeholder = { Text("Постоянный промпт истории…", color = TextDim, fontSize = 13.sp) },
                    textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 14.sp),
                    shape = RoundedCornerShape(12.dp),
                    colors = darkFieldColors(),
                )
                Spacer(Modifier.width(8.dp))
                IconButton(
                    onClick = { if (draft.isNotBlank()) onApplyPrompt(draft) },
                    modifier = Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)).background(AccentBlue),
                ) { Icon(Icons.Filled.Check, "Зафиксировать промпт", tint = Color.White) }
            }
        } else {
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFF1E2229)).padding(12.dp)
            ) {
                Text(
                    if (info.prompt.isBlank()) "Промпт не задан" else info.prompt,
                    color = Color.White, fontSize = 13.sp,
                )
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { confirmClear = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.DeleteOutline, null, tint = Color(0xFFEF5350), modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Очистить историю и сменить промпт", color = Color(0xFFEF5350), fontSize = 13.sp)
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Очистить историю?") },
            text = { Text("Вся сохранённая история будет удалена без возможности восстановления. После этого можно задать новый промпт.") },
            confirmButton = {
                TextButton(onClick = { confirmClear = false; onClear() }) { Text("Удалить", color = Color(0xFFEF5350)) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Отмена") } },
        )
    }
}