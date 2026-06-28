// Путь: app/src/main/java/com/learnde/app/ui/components/AppHeader.kt
//
// Верхний тулбар на токенах Theme.kt. Слева — статус присутствия (StatusLabel),
// справа — действия. Главное действие (подключение) выделено заливкой-акцентом,
// второстепенные — иконки, активные подсвечиваются акцентом. Поведение 1:1 с прежним
// Header (те же иконки/коллбэки), меняется только оформление и консистентность.

package com.learnde.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.learnde.app.ui.theme.AppTheme
import com.learnde.app.ui.theme.Radius

@Composable
fun AppHeader(
    presence: Presence,
    isLinkActive: Boolean,          // isConnected || isConnecting → показываем Stop
    cameraOn: Boolean,
    camMode: Boolean,
    historyMode: Boolean,
    onToggleConnection: () -> Unit,
    onToggleCamera: () -> Unit,
    onToggleCam: () -> Unit,
    onToggleHistory: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pal = AppTheme.palette
    Row(
        modifier.fillMaxWidth().height(52.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusLabel(presence)
        Spacer(Modifier.weight(1f))

        // Главное действие — подключение. Заливка-акцент, чтобы выделялось среди иконок.
        IconButton(
            onClick = onToggleConnection,
            modifier = Modifier.size(40.dp).clip(RoundedCornerShape(Radius.md))
                .background(if (isLinkActive) pal.error.copy(alpha = 0.16f) else pal.accent),
        ) {
            Icon(
                if (isLinkActive) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                contentDescription = if (isLinkActive) "Отключить" else "Подключить",
                tint = if (isLinkActive) pal.error else pal.onAccent,
            )
        }

        ToolbarIcon(Icons.Filled.PhotoCamera, "Камера", active = cameraOn, onClick = onToggleCamera)
        ToolbarIcon(Icons.Filled.Videocam, "Режим камеры", active = camMode, onClick = onToggleCam)
        ToolbarIcon(Icons.Filled.History, "История", active = historyMode, onClick = onToggleHistory)
        ToolbarIcon(Icons.Filled.Settings, "Настройки", active = false, onClick = onSettings)
    }
}

@Composable
private fun ToolbarIcon(icon: ImageVector, label: String, active: Boolean, onClick: () -> Unit) {
    val pal = AppTheme.palette
    IconButton(onClick = onClick, modifier = Modifier.size(40.dp)) {
        Icon(icon, contentDescription = label, tint = if (active) pal.accent else pal.textSecondary)
    }
}
