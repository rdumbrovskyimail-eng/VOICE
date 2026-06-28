package com.learnde.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.learnde.app.domain.model.ConversationMessage
import com.learnde.app.ui.theme.AppTheme
import com.learnde.app.ui.theme.Radius
import com.learnde.app.ui.theme.Space
import java.text.SimpleDateFormat
import java.util.Date

@Composable
fun ChatEmptyState(modifier: Modifier = Modifier) {
    val pal = AppTheme.palette
    Column(
        modifier.fillMaxSize().padding(Space.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Готов", style = MaterialTheme.typography.titleLarge, color = pal.textPrimary)
        Spacer(Modifier.height(Space.xs))
        Text("Нажмите ▶ или напишите сообщение.", style = MaterialTheme.typography.bodyMedium, color = pal.textSecondary, textAlign = TextAlign.Center)
    }
}

@Composable
fun MessageBubble(
    msg: ConversationMessage,
    fontScale: Float,
    showRoleLabels: Boolean,
    showTimestamps: Boolean,
    timeFormatter: SimpleDateFormat,
    modifier: Modifier = Modifier,
) {
    val pal = AppTheme.palette
    val isUser = msg.role == ConversationMessage.ROLE_USER
    val bg = if (isUser) pal.surfaceElevated else pal.surface
    val shape = RoundedCornerShape(Radius.lg)

    Column(modifier.fillMaxWidth(), horizontalAlignment = if (isUser) Alignment.End else Alignment.Start) {
        Box(
            Modifier
                .widthIn(max = 280.dp)
                .clip(shape)
                .background(bg)
                .border(1.dp, pal.outline, shape)
                .padding(horizontal = Space.md, vertical = Space.sm),
        ) {
            Column {
                if (msg.text.isNotEmpty()) {
                    Text(msg.text, style = MaterialTheme.typography.bodyLarge, color = pal.textPrimary, fontSize = (12 * fontScale).sp)
                }
                if (msg.attachmentUris.isNotEmpty()) {
                    Text("📎 ${msg.attachmentUris.size} вложение(й)", style = MaterialTheme.typography.labelSmall, color = pal.textSecondary)
                }
            }
        }
    }
}

@Composable
fun ChatInputBar(
    value: String, onValueChange: (String) -> Unit, onAttach: () -> Unit, onSend: () -> Unit,
    isMicActive: Boolean, onToggleMic: () -> Unit, modifier: Modifier = Modifier,
) {
    val pal = AppTheme.palette
    Row(modifier.fillMaxWidth().padding(horizontal = Space.lg, vertical = Space.md), verticalAlignment = Alignment.CenterVertically) {
        IconButton(
            onClick = onAttach,
            modifier = Modifier.size(36.dp).clip(CircleShape).background(pal.surfaceElevated).border(1.dp, pal.outline, CircleShape)
        ) { Icon(Icons.Filled.AttachFile, "Прикрепить", tint = pal.textPrimary, modifier = Modifier.size(16.dp)) }
        
        Spacer(Modifier.width(Space.sm))
        
        OutlinedTextField(
            value = value, onValueChange = onValueChange, modifier = Modifier.weight(1f).height(40.dp),
            placeholder = { Text("Сообщение…", style = MaterialTheme.typography.bodyMedium, color = pal.textDim) },
            textStyle = MaterialTheme.typography.bodyLarge, shape = RoundedCornerShape(Radius.pill), singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = pal.textPrimary, unfocusedTextColor = pal.textPrimary,
                cursorColor = pal.accent, focusedBorderColor = pal.outline, unfocusedBorderColor = pal.outline,
                focusedContainerColor = pal.surface, unfocusedContainerColor = pal.surface,
            ),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
        )
        
        Spacer(Modifier.width(Space.sm))
        
        IconButton(
            onClick = onSend,
            modifier = Modifier.size(36.dp).clip(CircleShape).background(pal.surfaceElevated).border(1.dp, pal.outline, CircleShape)
        ) { Icon(Icons.Filled.Send, "Отправить", tint = pal.textPrimary, modifier = Modifier.size(16.dp)) }
        
        Spacer(Modifier.width(Space.sm))
        
        IconButton(
            onClick = onToggleMic,
            modifier = Modifier.size(44.dp).clip(CircleShape).background(if (isMicActive) pal.textPrimary else pal.surfaceElevated).border(1.dp, pal.outline, CircleShape)
        ) { Icon(if (isMicActive) Icons.Filled.Mic else Icons.Filled.MicOff, "Микрофон", tint = if (isMicActive) pal.surface else pal.textPrimary, modifier = Modifier.size(20.dp)) }
    }
}