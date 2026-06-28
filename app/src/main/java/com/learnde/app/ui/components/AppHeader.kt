package com.learnde.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.learnde.app.ui.theme.AppTheme
import com.learnde.app.ui.theme.Radius
import com.learnde.app.ui.theme.Space

@Composable
fun AppHeader(
    presence: Presence,
    isLinkActive: Boolean,
    camMode: Boolean,
    onToggleConnection: () -> Unit,
    onToggleCam: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pal = AppTheme.palette
    Row(modifier = modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
        StatusLabel(presence)
        Spacer(Modifier.weight(1f))

        val (btnBg, btnIconTint) = if (isLinkActive) pal.errorBg to pal.error else pal.accentBlue to Color.White

        IconButton(
            onClick = onToggleConnection,
            modifier = Modifier.size(40.dp).clip(RoundedCornerShape(Radius.md)).background(btnBg),
        ) {
            Icon(
                imageVector = if (isLinkActive) Icons.Filled.Stop else Icons.Filled.PlayArrow, 
                contentDescription = "Подключение", tint = btnIconTint, modifier = Modifier.size(22.dp)
            )
        }
        Spacer(Modifier.width(Space.sm))
        ToolbarIcon(icon = Icons.Filled.Videocam, label = "Камера", active = camMode, onClick = onToggleCam)
        Spacer(Modifier.width(Space.sm))
        ToolbarIcon(icon = Icons.Filled.Settings, label = "Настройки", active = false, onClick = onSettings)
    }
}

@Composable
private fun ToolbarIcon(icon: ImageVector, label: String, active: Boolean, onClick: () -> Unit) {
    val pal = AppTheme.palette
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(40.dp).clip(RoundedCornerShape(Radius.md)).background(if (active) pal.accentBlueBg else pal.surfaceElevated)
    ) {
        Icon(imageVector = icon, contentDescription = label, tint = if (active) pal.accentBlue else pal.textSecondary, modifier = Modifier.size(20.dp))
    }
}