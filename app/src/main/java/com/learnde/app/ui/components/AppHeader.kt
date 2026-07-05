package com.learnde.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
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
    
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(pal.background)
            .padding(horizontal = Space.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Иконка меню/настроек слева
        IconButton(onClick = onSettings) {
            Icon(Icons.Filled.Menu, "Меню", tint = pal.textPrimary)
        }
        
        Spacer(Modifier.weight(1f))
        
        // Центральная кнопка старта/стопа сессии
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(Radius.pill))
                .clickable { onToggleConnection() }
                .background(if (isLinkActive) pal.accentGreenBg else pal.surfaceVariant)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Зеленая или серая точка статуса
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (isLinkActive) pal.accentGreen else pal.textDim)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (isLinkActive) "Gemini Live" else "Подключиться",
                style = MaterialTheme.typography.labelLarge,
                color = if (isLinkActive) pal.accentGreen else pal.textPrimary,
                fontWeight = FontWeight.SemiBold
            )
        }
        
        Spacer(Modifier.weight(1f))

        // Иконка камеры справа
        IconButton(onClick = onToggleCam) {
            Icon(
                imageVector = Icons.Filled.Videocam, 
                contentDescription = "Камера", 
                tint = if (camMode) pal.textPrimary else pal.textSecondary
            )
        }
    }
}