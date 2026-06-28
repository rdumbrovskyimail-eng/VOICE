package com.learnde.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
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

@Composable
fun AppHeader(
    presence: Presence, isLinkActive: Boolean, camMode: Boolean,
    onToggleConnection: () -> Unit, onToggleCam: () -> Unit, onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pal = AppTheme.palette
    Row(modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
        StatusLabel(presence)
        Spacer(Modifier.weight(1f))

        IconButton(
            onClick = onToggleConnection,
            modifier = Modifier.size(36.dp).clip(CircleShape).background(if (isLinkActive) pal.textPrimary else pal.surfaceElevated).border(1.dp, pal.outline, CircleShape),
        ) {
            Icon(if (isLinkActive) Icons.Filled.Stop else Icons.Filled.PlayArrow, "Подключить", tint = if (isLinkActive) pal.surface else pal.textPrimary, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(8.dp))
        ToolbarIcon(Icons.Filled.Videocam, "Камера", active = camMode, onClick = onToggleCam)
        Spacer(Modifier.width(8.dp))
        ToolbarIcon(Icons.Filled.Settings, "Настройки", active = false, onClick = onSettings)
    }
}

@Composable
private fun ToolbarIcon(icon: ImageVector, label: String, active: Boolean, onClick: () -> Unit) {
    val pal = AppTheme.palette
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(36.dp).clip(CircleShape).background(if (active) pal.textPrimary else pal.surfaceElevated).border(1.dp, pal.outline, CircleShape)
    ) { Icon(icon, label, tint = if (active) pal.surface else pal.textPrimary, modifier = Modifier.size(18.dp)) }
}