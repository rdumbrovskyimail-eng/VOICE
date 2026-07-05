package com.learnde.app.presentation.client

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.learnde.app.domain.model.ConversationMessage
import com.learnde.app.domain.model.PronunciationItem
import com.learnde.app.presentation.camera.CameraLayer
import com.learnde.app.presentation.navigation.Routes
import com.learnde.app.session.ClientMode
import com.learnde.app.ui.components.*
import com.learnde.app.ui.theme.AppTheme
import com.learnde.app.ui.theme.Radius
import com.learnde.app.ui.theme.Space
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ClientScreen(navController: NavController, viewModel: ClientViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val presence = presenceOf(state.isConnected, state.isConnecting, state.isRecovering, state.isMicActive, state.isAiSpeaking, state.error != null)
    val chatPrefs by viewModel.chatPrefs.collectAsStateWithLifecycle()
    val cameraActive = state.cameraOn || state.mode == ClientMode.CAM
    val context = LocalContext.current
    val pal = AppTheme.palette

    var promptText by remember { mutableStateOf("") }
    var chatInput by remember { mutableStateOf("") }
    var frontCamera by remember { mutableStateOf(true) }
    
    var promptAttachments by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var chatAttachments by remember { mutableStateOf<List<Uri>>(emptyList()) }
    
    val promptPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { u -> promptAttachments = (promptAttachments + u).distinct().take(20) }
    val chatPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { u -> chatAttachments = (chatAttachments + u).distinct().take(20) }

    LaunchedEffect(state.activePrompt) { if (state.activePrompt.isNotEmpty() && promptText.isEmpty()) promptText = state.activePrompt }

    fun hasRecord() = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    val connectLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        val recOk = r[Manifest.permission.RECORD_AUDIO] ?: hasRecord()
        if (recOk) viewModel.toggleConnection() // POST_NOTIFICATIONS опционален — отказ сессию не блокирует
    }
    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { g -> if (g) viewModel.toggleMic() }

    Box(modifier = Modifier.fillMaxSize().background(pal.background)) {
        Column(modifier = Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
            
            // 1. ШАПКА
            AppHeader(
                presence = presence, isLinkActive = state.isConnected || state.isConnecting, camMode = state.cameraOn,
                onToggleConnection = {
                    val need = buildList {
                        if (!hasRecord()) add(Manifest.permission.RECORD_AUDIO)
                        if (android.os.Build.VERSION.SDK_INT >= 33 &&
                            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                                != PackageManager.PERMISSION_GRANTED
                        ) add(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    if (need.isEmpty()) viewModel.toggleConnection()
                    else connectLauncher.launch(need.toTypedArray())
                },
                onToggleCam = { viewModel.toggleCamera() }, onSettings = { navController.navigate(Routes.SETTINGS) },
                onTranslate = { navController.navigate(Routes.TRANSLATOR) },
                modifier = Modifier.padding(horizontal = Space.lg, vertical = Space.sm)
            )

            HorizontalDivider(color = pal.outline, thickness = 1.dp)

            // 2. ОШИБКИ
            if (state.error != null) {
                Box(Modifier.fillMaxWidth().padding(Space.lg).clip(RoundedCornerShape(Radius.md)).background(pal.errorBg).padding(Space.md)) {
                    Text(state.error ?: "", color = pal.error, style = MaterialTheme.typography.bodyMedium)
                }
            }

            // 3. ПРОМПТ
            Column(modifier = Modifier.padding(horizontal = Space.lg, vertical = Space.md)) {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.md)).background(pal.surface).padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { promptPicker.launch(arrayOf("*/*")) }, modifier = Modifier.size(36.dp).clip(RoundedCornerShape(Radius.sm))) {
                        Icon(Icons.Filled.AttachFile, "Прикрепить", tint = pal.textSecondary, modifier = Modifier.size(18.dp))
                    }
                    
                    OutlinedTextField(
                        value = promptText, onValueChange = { promptText = it }, modifier = Modifier.weight(1f),
                        placeholder = { Text("Системный промпт...", color = pal.textDim, style = MaterialTheme.typography.bodyMedium) },
                        textStyle = MaterialTheme.typography.bodyLarge,
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent, unfocusedBorderColor = Color.Transparent,
                            focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent
                        )
                    )
                    
                    IconButton(
                        onClick = { 
                            val p = promptText.trim()
                            val hadAttach = promptAttachments.isNotEmpty()
                            viewModel.applyPrompt(p, promptAttachments)
                            promptAttachments = emptyList()
                            if (p.isNotEmpty() || hadAttach)
                                Toast.makeText(context, "Промпт установлен!", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.size(36.dp).clip(RoundedCornerShape(Radius.sm)).background(pal.accentBlueBg)
                    ) { Icon(Icons.Filled.Check, "Применить", tint = pal.accentBlue, modifier = Modifier.size(18.dp)) }
                }
                if (promptAttachments.isNotEmpty()) {
                    AttachmentChips(promptAttachments) { promptAttachments = promptAttachments - it }
                }
            }

            // 4. ДАШБОРД (Элегантная информационная карточка)
            if (state.dashboardText.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Space.lg, vertical = Space.sm)
                        .clip(RoundedCornerShape(Radius.lg))
                        .background(pal.accentBlueBg)
                        .padding(Space.md)
                ) {
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(
                            Icons.Filled.Info, 
                            contentDescription = "Информация", 
                            tint = pal.accentBlue, 
                            modifier = Modifier.size(20.dp).padding(top = 2.dp)
                        )
                        Spacer(Modifier.width(Space.sm))
                        Text(
                            text = state.dashboardText, 
                            color = pal.textPrimary, 
                            style = MaterialTheme.typography.bodyLarge, 
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(Space.sm))
                        IconButton(
                            onClick = { viewModel.clearDashboard() },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Filled.Close, "Закрыть", tint = pal.accentBlue, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            // 4b. ПРОИЗНОШЕНИЕ (Forvo) — тапаемые чипы со словами
            if (state.pronunciations.isNotEmpty()) {
                PronunciationChips(
                    items = state.pronunciations,
                    onPlay = { viewModel.playPronunciation(it) },
                    onClear = { viewModel.clearPronunciations() }
                )
            }

            // 5. КАМЕРА (С индикатором LIVE)
            if (cameraActive) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Space.lg, vertical = Space.sm)
                        .clip(RoundedCornerShape(Radius.lg))
                        .background(pal.surfaceElevated)
                ) {
                    CameraLayer(
                        active = true, onFrame = { viewModel.sendCameraFrame(it) },
                        front = frontCamera,
                        modifier = Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(Radius.lg)),
                    )
                    IconButton(
                        onClick = { frontCamera = !frontCamera },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(Space.sm)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.6f))
                            .size(36.dp)
                    ) {
                        Icon(Icons.Filled.Cameraswitch, "Перевернуть камеру", tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                    // Индикатор LIVE
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(Space.sm)
                            .clip(RoundedCornerShape(Radius.pill))
                            .background(Color.Black.copy(alpha = 0.6f))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.size(6.dp).clip(CircleShape).background(pal.error))
                        Spacer(Modifier.width(4.dp))
                        Text("LIVE", color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // 6. ЧАТ
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                val messages = state.transcript
                if (messages.isEmpty()) ChatEmptyState(modifier = Modifier.fillMaxSize())
                else ChatList(messages = messages, prefs = chatPrefs, modifier = Modifier.fillMaxSize().padding(horizontal = Space.lg))
            }

            // 7. ВВОД И ВЛОЖЕНИЯ
            Column(modifier = Modifier.fillMaxWidth().background(pal.background)) {
                // Элегантный разделитель, прижимающий чат
                HorizontalDivider(color = pal.outline, thickness = 1.dp)
                
                if (chatAttachments.isNotEmpty()) {
                    AttachmentChips(chatAttachments) { chatAttachments = chatAttachments - it }
                }
                ChatInputBar(
                    value = chatInput, onValueChange = { chatInput = it }, onAttach = { chatPicker.launch(arrayOf("*/*")) },
                    onSend = { viewModel.sendText(chatInput, chatAttachments); chatInput = ""; chatAttachments = emptyList() },
                    isMicActive = state.isMicActive, onToggleMic = { if (hasRecord()) viewModel.toggleMic() else micLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                    modifier = Modifier.padding(horizontal = Space.lg, vertical = Space.md)
                )
            }
        }
    }
}

@Composable
private fun ChatList(messages: List<ConversationMessage>, prefs: ChatPrefs, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    val timeFormatter = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    
    // Плавный скролл при появлении новых сообщений ИЛИ при стриминге текста
    LaunchedEffect(messages.size, messages.lastOrNull()?.text?.length) {
        if (prefs.autoScroll && messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    LazyColumn(
        modifier = modifier, state = listState,
        verticalArrangement = Arrangement.spacedBy(Space.sm),
        contentPadding = PaddingValues(vertical = Space.md)
    ) {
        items(messages) { msg -> 
            MessageBubble(msg, prefs.fontScale, prefs.showRoleLabels, prefs.showTimestamps, timeFormatter)
        }
    }
}

@Composable
private fun AttachmentChips(uris: List<Uri>, onRemove: (Uri) -> Unit) {
    val ctx = LocalContext.current
    val pal = AppTheme.palette
    LazyRow(
        Modifier.fillMaxWidth().padding(horizontal = Space.lg, vertical = Space.xs), 
        horizontalArrangement = Arrangement.spacedBy(Space.sm)
    ) {
        items(uris, key = { it.toString() }) { uri ->
            AttachmentTile(ctx, uri, onRemove)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PronunciationChips(
    items: List<PronunciationItem>,
    onPlay: (PronunciationItem) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pal = AppTheme.palette
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = Space.lg, vertical = Space.sm)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Произношение · Forvo",
                color = pal.textSecondary,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onClear, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Filled.Close, "Скрыть", tint = pal.textSecondary, modifier = Modifier.size(16.dp))
            }
        }
        Spacer(Modifier.height(6.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Space.sm),
            verticalArrangement = Arrangement.spacedBy(Space.sm)
        ) {
            items.forEach { item ->
                val ready = item.status == PronunciationItem.Status.Ready
                val isError = item.status == PronunciationItem.Status.Error
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(Radius.pill))
                        .background(if (isError) pal.errorBg else pal.accentBlueBg)
                        .clickable(enabled = ready) { onPlay(item) }
                        .padding(horizontal = Space.md, vertical = Space.sm)
                ) {
                    when (item.status) {
                        PronunciationItem.Status.Loading ->
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                                color = pal.accentBlue
                            )
                        PronunciationItem.Status.Ready ->
                            Icon(Icons.Filled.PlayArrow, "Слушать", tint = pal.accentBlue, modifier = Modifier.size(18.dp))
                        PronunciationItem.Status.Error -> Unit
                    }
                    if (item.status != PronunciationItem.Status.Error) Spacer(Modifier.width(4.dp))
                    Text(
                        item.word,
                        color = if (isError) pal.textSecondary else pal.textPrimary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun AttachmentTile(ctx: android.content.Context, uri: Uri, onRemove: (Uri) -> Unit) {
    val density = LocalDensity.current
    val reqPx = remember { with(density) { 56.dp.roundToPx() } * 2 }
    var bmp by remember(uri) { mutableStateOf<android.graphics.Bitmap?>(null) }
    var tried by remember(uri) { mutableStateOf(false) }
    val pal = AppTheme.palette

    LaunchedEffect(uri) {
        bmp = runCatching { decodeThumb(ctx, uri, reqPx) }.getOrNull()
        tried = true
    }

    Box(
        Modifier
            .size(56.dp)
            .clip(RoundedCornerShape(Radius.md))
            .background(pal.surfaceElevated),
        contentAlignment = Alignment.Center,
    ) {
        val b = bmp
        if (b != null) {
            androidx.compose.foundation.Image(
                bitmap = b.asImageBitmap(), 
                contentDescription = null, 
                modifier = Modifier.fillMaxSize(), 
                contentScale = androidx.compose.ui.layout.ContentScale.Crop
            )
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    if (!tried) Icons.Filled.HourglassEmpty else Icons.Filled.InsertDriveFile,
                    null, tint = pal.textSecondary, modifier = Modifier.size(18.dp),
                )
                if (tried) {
                    val ext = remember(uri) { fileExt(ctx, uri) }
                    if (ext.isNotEmpty()) { 
                        Spacer(Modifier.height(2.dp))
                        Text(ext, color = pal.textSecondary, style = MaterialTheme.typography.labelSmall, maxLines = 1) 
                    }
                }
            }
        }
        
        // Элегантная кнопка удаления поверх картинки
        IconButton(
            onClick = { onRemove(uri) },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
                .size(18.dp)
                .clip(RoundedCornerShape(Radius.sm))
                .background(Color.Black.copy(alpha = 0.5f))
        ) {
            Icon(Icons.Filled.Close, "Убрать", tint = Color.White, modifier = Modifier.size(10.dp))
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
    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
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
fun ChatEmptyState(modifier: Modifier = Modifier) {
    val pal = AppTheme.palette
    Column(
        modifier.fillMaxSize().padding(Space.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Filled.Mic, contentDescription = null, tint = pal.textDim, modifier = Modifier.size(32.dp))
        Spacer(Modifier.height(Space.md))
        Text("Ассистент готов к работе", style = MaterialTheme.typography.titleLarge, color = pal.textPrimary)
        Spacer(Modifier.height(Space.xs))
        Text("Нажмите на микрофон, чтобы начать диалог,\nили напишите сообщение текстом.", style = MaterialTheme.typography.bodyMedium, color = pal.textSecondary, textAlign = TextAlign.Center)
    }
}

@Composable
fun MessageBubble(
    msg: ConversationMessage, fontScale: Float, showRoleLabels: Boolean,
    showTimestamps: Boolean, timeFormatter: SimpleDateFormat, modifier: Modifier = Modifier,
) {
    val pal = AppTheme.palette
    val isUser = msg.role == ConversationMessage.ROLE_USER
    val bg = if (isUser) pal.accentBlueBg else pal.surfaceElevated
    val textColor = if (isUser) pal.accentBlue else pal.textPrimary
    val shape = RoundedCornerShape(
        topStart = Radius.lg, topEnd = Radius.lg,
        bottomStart = if (isUser) Radius.lg else 2.dp, bottomEnd = if (isUser) 2.dp else Radius.lg
    )

    Column(modifier = modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalAlignment = if (isUser) Alignment.End else Alignment.Start) {
        if (showRoleLabels) {
            Text(
                text = if (isUser) "Вы" else "Ассистент",
                style = MaterialTheme.typography.labelSmall,
                color = pal.textDim,
                modifier = Modifier.padding(bottom = 2.dp, start = 4.dp, end = 4.dp)
            )
        }
        Box(
            Modifier.widthIn(max = 300.dp).clip(shape).background(bg)
                .animateContentSize(animationSpec = tween(durationMillis = 250, easing = LinearOutSlowInEasing))
                .padding(horizontal = Space.lg, vertical = Space.md),
        ) {
            Column {
                if (msg.text.isNotEmpty()) {
                    Text(text = msg.text, style = MaterialTheme.typography.bodyLarge, color = textColor, fontSize = (14 * fontScale).sp)
                }
                if (msg.attachmentUris.isNotEmpty()) {
                    if (msg.text.isNotEmpty()) Spacer(Modifier.height(Space.xs))
                    Text("📎 ${msg.attachmentUris.size} вложение(й)", style = MaterialTheme.typography.labelSmall, color = if (isUser) pal.accentBlue.copy(alpha = 0.7f) else pal.textSecondary)
                }
            }
        }
        if (showTimestamps) {
            Text(text = timeFormatter.format(Date(msg.timestamp)), style = MaterialTheme.typography.labelSmall, color = pal.textDim, modifier = Modifier.padding(top = 2.dp, start = 4.dp, end = 4.dp))
        }
    }
}

@Composable
fun ChatInputBar(
    value: String, onValueChange: (String) -> Unit, onAttach: () -> Unit, onSend: () -> Unit,
    isMicActive: Boolean, onToggleMic: () -> Unit, modifier: Modifier = Modifier,
) {
    val pal = AppTheme.palette
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        Row(
            modifier = Modifier.weight(1f).clip(RoundedCornerShape(Radius.lg)).background(pal.surfaceElevated).padding(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onAttach, modifier = Modifier.size(40.dp).clip(RoundedCornerShape(Radius.md))) { 
                Icon(Icons.Filled.AttachFile, "Прикрепить", tint = pal.textSecondary, modifier = Modifier.size(20.dp)) 
            }
            OutlinedTextField(
                value = value, onValueChange = onValueChange, modifier = Modifier.weight(1f),
                placeholder = { Text("Написать...", style = MaterialTheme.typography.bodyMedium, color = pal.textDim) },
                textStyle = MaterialTheme.typography.bodyLarge, singleLine = false, maxLines = 4,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = pal.textPrimary, unfocusedTextColor = pal.textPrimary, cursorColor = pal.accentBlue, 
                    focusedBorderColor = Color.Transparent, unfocusedBorderColor = Color.Transparent,
                    focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                )
            )
            IconButton(
                onClick = onSend, enabled = value.isNotBlank(),
                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(Radius.md)).background(if (value.isNotBlank()) pal.accentBlue else Color.Transparent)
            ) { 
                Icon(Icons.Filled.Send, "Отправить", tint = if (value.isNotBlank()) Color.White else pal.textDim, modifier = Modifier.size(18.dp)) 
            }
        }
        Spacer(Modifier.width(Space.sm))
        IconButton(
            onClick = onToggleMic,
            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(Radius.lg)).background(if (isMicActive) pal.accentGreenBg else pal.surfaceElevated)
        ) { 
            Icon(if (isMicActive) Icons.Filled.Mic else Icons.Filled.MicOff, "Микрофон", tint = if (isMicActive) pal.accentGreen else pal.textSecondary, modifier = Modifier.size(24.dp)) 
        }
    }
}