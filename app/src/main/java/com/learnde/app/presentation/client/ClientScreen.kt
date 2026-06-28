package com.learnde.app.presentation.client

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.learnde.app.domain.model.ConversationMessage
import com.learnde.app.presentation.camera.CameraLayer
import com.learnde.app.presentation.navigation.Routes
import com.learnde.app.session.ClientMode
import com.learnde.app.ui.components.*
import com.learnde.app.ui.theme.AppTheme
import com.learnde.app.ui.theme.Radius
import com.learnde.app.ui.theme.Space
import java.text.SimpleDateFormat
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
    
    var promptAttachments by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var chatAttachments by remember { mutableStateOf<List<Uri>>(emptyList()) }
    
    val promptPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { u -> promptAttachments = (promptAttachments + u).distinct().take(20) }
    val chatPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { u -> chatAttachments = (chatAttachments + u).distinct().take(20) }

    LaunchedEffect(state.activePrompt) { if (state.activePrompt.isNotEmpty() && promptText.isEmpty()) promptText = state.activePrompt }

    fun hasRecord() = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    val connectLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r -> if (r[Manifest.permission.RECORD_AUDIO] == true) viewModel.toggleConnection() }
    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { g -> if (g) viewModel.toggleMic() }

    Box(modifier = Modifier.fillMaxSize().background(pal.background)) {
        Column(modifier = Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
            
            // 1. ШАПКА
            AppHeader(
                presence = presence, isLinkActive = state.isConnected || state.isConnecting, camMode = state.mode == ClientMode.CAM,
                onToggleConnection = { if (hasRecord()) viewModel.toggleConnection() else connectLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO)) },
                onToggleCam = { viewModel.toggleCamMode() }, onSettings = { navController.navigate(Routes.SETTINGS) },
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
                        onClick = { viewModel.applyPrompt(promptText.trim(), promptAttachments); promptAttachments = emptyList() },
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
                        modifier = Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(Radius.lg)),
                    )
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
        if (messages.isNotEmpty()) {
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