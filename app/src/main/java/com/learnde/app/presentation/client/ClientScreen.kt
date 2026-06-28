package com.learnde.app.presentation.client

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    var chatAttachments by remember { mutableStateOf<List<Uri>>(emptyList()) }
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
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )

            // 2. ОШИБКИ
            if (state.error != null) {
                Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).border(1.dp, pal.outline, RoundedCornerShape(8.dp)).padding(8.dp)) {
                    Text(state.error ?: "", color = pal.error, fontSize = 11.sp)
                }
            }

            // 3. ПРОМПТ
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = promptText, onValueChange = { promptText = it }, modifier = Modifier.weight(1f),
                        placeholder = { Text("Системный промпт...", fontSize = 11.sp) }, textStyle = LocalTextStyle.current.copy(fontSize = 11.sp),
                        shape = RoundedCornerShape(8.dp), singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = pal.outline, unfocusedBorderColor = pal.outline)
                    )
                    Spacer(Modifier.width(8.dp))
                    IconButton(
                        onClick = { viewModel.applyPrompt(promptText.trim()) },
                        modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(pal.surfaceElevated).border(1.dp, pal.outline, RoundedCornerShape(8.dp)),
                    ) { Icon(Icons.Filled.Check, "Применить", tint = pal.textPrimary, modifier = Modifier.size(18.dp)) }
                }
            }

            // 4. ДАШБОРД
            if (state.dashboardText.isNotEmpty()) {
                Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).border(1.dp, pal.outline, RoundedCornerShape(8.dp)).padding(12.dp)) {
                    Text(state.dashboardText, color = pal.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            // 5. КАМЕРА
            if (cameraActive) {
                CameraLayer(
                    active = true, onFrame = { viewModel.sendCameraFrame(it) },
                    modifier = Modifier.fillMaxWidth().height(180.dp).padding(horizontal = 12.dp, vertical = 4.dp).border(1.dp, pal.outline, RoundedCornerShape(8.dp)).clip(RoundedCornerShape(8.dp)),
                )
            }

            // 6. ЧАТ
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                val messages = state.transcript
                if (messages.isEmpty()) ChatEmptyState(modifier = Modifier.fillMaxSize())
                else ChatList(messages = messages, prefs = chatPrefs, modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp))
            }

            // 7. ВВОД И ВЛОЖЕНИЯ
            Column(modifier = Modifier.fillMaxWidth()) {
                if (chatAttachments.isNotEmpty()) {
                    AttachmentChips(chatAttachments) { chatAttachments = chatAttachments - it }
                }
                ChatInputBar(
                    value = chatInput, onValueChange = { chatInput = it }, onAttach = { chatPicker.launch(arrayOf("*/*")) },
                    onSend = { viewModel.sendText(chatInput, chatAttachments); chatInput = ""; chatAttachments = emptyList() },
                    isMicActive = state.isMicActive, onToggleMic = { if (hasRecord()) viewModel.toggleMic() else micLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun ChatList(messages: List<ConversationMessage>, prefs: ChatPrefs, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    val timeFormatter = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    
    LaunchedEffect(messages.size) { 
        if (messages.isNotEmpty()) listState.scrollToItem(messages.lastIndex) 
    }

    LazyColumn(
        modifier = modifier, state = listState,
        verticalArrangement = Arrangement.spacedBy(2.dp), // Очень плотная компоновка
        contentPadding = PaddingValues(vertical = 4.dp)
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
    LazyRow(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        items(uris, key = { it.toString() }) { uri ->
            val name = remember(uri) { fileLabel(ctx, uri) }
            Row(
                Modifier.clip(RoundedCornerShape(8.dp)).background(pal.surfaceElevated).border(1.dp, pal.outline, RoundedCornerShape(8.dp)).padding(start = 8.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.InsertDriveFile, null, tint = pal.textPrimary, modifier = Modifier.size(12.dp))
                Spacer(Modifier.width(4.dp))
                Text(name, color = pal.textPrimary, fontSize = 10.sp, maxLines = 1, modifier = Modifier.widthIn(max = 100.dp))
                Spacer(Modifier.width(2.dp))
                IconButton(onClick = { onRemove(uri) }, modifier = Modifier.size(16.dp)) {
                    Icon(Icons.Filled.Close, "Убрать", tint = pal.textSecondary, modifier = Modifier.size(10.dp))
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

// --- Логика миниатюр (оставлена для корректной работы вложений) ---

@Composable
fun AttachmentThumbnails(uriStrings: List<String>) {
    val ctx = LocalContext.current
    Row(
        Modifier.horizontalScroll(rememberScrollState()).padding(bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) { uriStrings.forEach { AttachmentTile(ctx, it) } }
}

@Composable
private fun AttachmentTile(ctx: android.content.Context, uriString: String) {
    val uri = remember(uriString) { Uri.parse(uriString) }
    val density = LocalDensity.current
    val reqPx = remember { with(density) { 48.dp.roundToPx() } * 2 }
    var bmp by remember(uriString) { mutableStateOf<android.graphics.Bitmap?>(null) }
    var tried by remember(uriString) { mutableStateOf(false) }
    val pal = AppTheme.palette

    LaunchedEffect(uriString) {
        bmp = runCatching { decodeThumb(ctx, uri, reqPx) }.getOrNull()
        tried = true
    }

    Box(
        Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(pal.surfaceElevated).border(1.dp, pal.outline, RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center,
    ) {
        val b = bmp
        if (b != null) {
            Image(b.asImageBitmap(), null, Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    if (!tried) Icons.Filled.HourglassEmpty else Icons.Filled.InsertDriveFile,
                    null, tint = pal.textSecondary, modifier = Modifier.size(16.dp),
                )
                if (tried) {
                    val ext = remember(uriString) { fileExt(ctx, uri) }
                    if (ext.isNotEmpty()) { Spacer(Modifier.height(2.dp)); Text(ext, color = pal.textSecondary, fontSize = 8.sp, maxLines = 1) }
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