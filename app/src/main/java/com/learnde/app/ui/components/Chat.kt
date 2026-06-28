// Путь: app/src/main/java/com/learnde/app/ui/components/Chat.kt
//
// Чат на токенах Theme.kt: пузыри сообщений, пустое состояние, строка ввода.
// • Таймстемпы реально рендерятся (раньше настройка была «мёртвой»).
// • Пустой экран — приглашение к действию, а не пустота (принцип хорошего UX-копирайта).
// • Декомпонован: принимает простые поля, не тянет ChatPrefs/SessionManager.

package com.learnde.app.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.learnde.app.domain.model.ConversationMessage
import com.learnde.app.ui.theme.AppTheme
import com.learnde.app.ui.theme.Radius
import com.learnde.app.ui.theme.Space
import java.text.SimpleDateFormat
import java.util.Date

/** Пустой чат — приглашение начать, а не «пусто». */
@Composable
fun ChatEmptyState(modifier: Modifier = Modifier) {
    val pal = AppTheme.palette
    Column(
        modifier.fillMaxSize().padding(Space.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(pal.accent.copy(alpha = 0.9f)))
        Spacer(Modifier.height(Space.lg))
        Text("Готов слушать", style = MaterialTheme.typography.titleLarge, color = pal.textPrimary)
        Spacer(Modifier.height(Space.xs))
        Text(
            "Нажмите ▶, затем говорите — или напишите сообщение ниже.",
            style = MaterialTheme.typography.bodyMedium,
            color = pal.textSecondary,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Пузырь одного сообщения.
 * @param timeFormatter общий форматтер (создавайте один раз на список).
 */
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
    val bubbleColor = if (isUser) pal.accent.copy(alpha = 0.18f) else pal.surfaceElevated
    val shape = RoundedCornerShape(
        topStart = Radius.lg, topEnd = Radius.lg,
        bottomStart = if (isUser) Radius.lg else Radius.sm,
        bottomEnd = if (isUser) Radius.sm else Radius.lg,
    )

    Column(
        modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
    ) {
        if (showRoleLabels) {
            Text(
                if (isUser) "Вы" else "Ассистент",
                style = MaterialTheme.typography.labelSmall,
                color = pal.textDim,
                modifier = Modifier.padding(horizontal = Space.xs, vertical = 2.dp),
            )
        }
        Box(
            Modifier
                .widthIn(max = 320.dp)
                .clip(shape)
                .background(bubbleColor)
                .padding(horizontal = Space.md + 2.dp, vertical = Space.md - 2.dp),
        ) {
            Column {
                if (msg.text.isNotEmpty()) {
                    Text(
                        msg.text,
                        style = MaterialTheme.typography.bodyLarge,
                        color = pal.textPrimary,
                        fontSize = (15 * fontScale).sp,
                    )
                }
                if (msg.attachmentUris.isNotEmpty()) {
                    if (msg.text.isNotEmpty()) Spacer(Modifier.height(Space.xs))
                    Text(
                        "📎 ${msg.attachmentUris.size} вложение(й)",
                        style = MaterialTheme.typography.labelSmall,
                        color = pal.textSecondary,
                    )
                }
                msg.attachmentNote?.let { note ->
                    Spacer(Modifier.height(Space.xs))
                    Text(note, style = MaterialTheme.typography.labelSmall, color = pal.textSecondary)
                }
            }
        }
        if (showTimestamps) {
            Text(
                timeFormatter.format(Date(msg.timestamp)),
                style = MaterialTheme.typography.labelSmall,
                color = pal.textDim,
                modifier = Modifier.padding(horizontal = Space.xs, vertical = 2.dp),
            )
        }
    }
}

/** Нижняя строка ввода: прикрепить · поле · отправить · микрофон. */
@Composable
fun ChatInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    onAttach: () -> Unit,
    onSend: () -> Unit,
    isMicActive: Boolean,
    onToggleMic: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pal = AppTheme.palette
    Row(
        modifier.fillMaxWidth().padding(horizontal = Space.lg, vertical = Space.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onAttach, modifier = Modifier.size(44.dp)) {
            Icon(Icons.Filled.AttachFile, "Прикрепить", tint = pal.textSecondary)
        }
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text("Сообщение…", style = MaterialTheme.typography.bodyMedium, color = pal.textDim) },
            textStyle = MaterialTheme.typography.bodyLarge,
            shape = RoundedCornerShape(Radius.pill),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = pal.textPrimary, unfocusedTextColor = pal.textPrimary,
                cursorColor = pal.accent,
                focusedBorderColor = pal.accent, unfocusedBorderColor = pal.outline,
                focusedContainerColor = pal.surface, unfocusedContainerColor = pal.surface,
            ),
        )
        Spacer(Modifier.width(Space.sm))
        IconButton(
            onClick = onSend,
            modifier = Modifier.size(44.dp).clip(CircleShape).background(pal.accent),
        ) {
            Icon(Icons.Filled.Send, "Отправить", tint = pal.onAccent, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(Space.sm))
        IconButton(
            onClick = onToggleMic,
            modifier = Modifier.size(52.dp).clip(CircleShape)
                .background(if (isMicActive) pal.stateListening else pal.surfaceElevated),
        ) {
            Icon(
                if (isMicActive) Icons.Filled.Mic else Icons.Filled.MicOff,
                "Микрофон",
                tint = if (isMicActive) pal.onAccent else pal.textSecondary,
                modifier = Modifier.size(26.dp),
            )
        }
    }
}
