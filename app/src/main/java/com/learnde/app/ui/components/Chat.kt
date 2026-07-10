package com.learnde.app.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
        Box(
            Modifier.widthIn(max = 300.dp).clip(shape).background(bg)
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