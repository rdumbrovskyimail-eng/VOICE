package com.learnde.app.presentation.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.learnde.app.data.voice.VoiceCatalog
import com.learnde.app.domain.model.LatencyProfile
import com.learnde.app.ui.theme.AppTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    val pal = AppTheme.palette

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Настройки", color = pal.textPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад", tint = pal.textPrimary) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = pal.background)
            )
        },
        containerColor = pal.background
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SettingsSection("Подключение") {
                SettingsTextField("API Ключ", settings.apiKey, viewModel::updateApiKey, "AIzaSy...")
                SettingsTextField("Модель", settings.model, viewModel::updateModel, "models/gemini-3.1-flash-live-preview")
            }
            SettingsSection("Аудио") {
                VoicePickerField(settings.voiceId, viewModel::updateVoice)
                SettingsSlider("Громкость: ${settings.playbackVolume}%", settings.playbackVolume.toFloat(), viewModel::updateVolume, 0f..100f)
                SettingsSlider("Микрофон: ${settings.micGain}%", settings.micGain.toFloat(), viewModel::updateMicGain, 50f..150f)
            }
            SettingsSection("Поведение") {
                SettingsTextField("Системная инструкция", settings.systemInstruction, viewModel::updateSystemInstruction, "Ты ассистент...", false)
                SettingsSlider("Креативность: ${String.format("%.1f", settings.temperature)}", settings.temperature, viewModel::updateTemperature, 0f..2f)
                ThinkingPickerField(settings.latencyProfile, viewModel::updateLatencyProfile)
                SettingsSwitch("Google Search", settings.enableGoogleSearch, viewModel::updateGoogleSearch)
                SettingsSwitch("Перебивание", settings.bargeInEnabled, viewModel::updateBargeIn)
            }
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    val pal = AppTheme.palette
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(title, color = MaterialTheme.colorScheme.primary, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
        Column(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(pal.surfaceElevated).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp), content = content
        )
    }
}

@Composable
private fun SettingsTextField(label: String, value: String, onValueChange: (String) -> Unit, placeholder: String, singleLine: Boolean = true) {
    val pal = AppTheme.palette
    Column {
        Text(label, color = pal.textPrimary, fontSize = 11.sp, modifier = Modifier.padding(bottom = 4.dp))
        OutlinedTextField(
            value = value, onValueChange = onValueChange, modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(placeholder, color = pal.textDim, fontSize = 11.sp) },
            singleLine = singleLine, minLines = if (singleLine) 1 else 3,
            textStyle = LocalTextStyle.current.copy(fontSize = 11.sp),
            shape = RoundedCornerShape(8.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = pal.textPrimary, unfocusedTextColor = pal.textPrimary,
                focusedBorderColor = MaterialTheme.colorScheme.primary, unfocusedBorderColor = Color.Transparent,
                focusedContainerColor = pal.background, unfocusedContainerColor = pal.background
            )
        )
    }
}

@Composable
private fun SettingsSlider(label: String, value: Float, onValueChange: (Float) -> Unit, valueRange: ClosedFloatingPointRange<Float>) {
    val pal = AppTheme.palette
    Column {
        Text(label, color = pal.textPrimary, fontSize = 11.sp)
        Slider(value = value, onValueChange = onValueChange, valueRange = valueRange, colors = SliderDefaults.colors(thumbColor = MaterialTheme.colorScheme.primary, activeTrackColor = MaterialTheme.colorScheme.primary, inactiveTrackColor = pal.textDim))
    }
}

@Composable
private fun SettingsSwitch(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val pal = AppTheme.palette
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = pal.textPrimary, fontSize = 11.sp)
        Switch(checked = checked, onCheckedChange = onCheckedChange, colors = SwitchDefaults.colors(checkedThumbColor = pal.background, checkedTrackColor = MaterialTheme.colorScheme.primary, uncheckedTrackColor = pal.textDim))
    }
}

@Composable
private fun VoicePickerField(selectedId: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val current = VoiceCatalog.byId(selectedId) ?: VoiceCatalog.all.first()
    val pal = AppTheme.palette
    Column {
        Text("Голос", color = pal.textPrimary, fontSize = 11.sp, modifier = Modifier.padding(bottom = 4.dp))
        Box {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(pal.background).clickable { expanded = true }.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("${current.id} · ${current.trait}", color = pal.textPrimary, fontSize = 11.sp, modifier = Modifier.weight(1f))
                Icon(Icons.Filled.ArrowDropDown, null, tint = pal.textPrimary)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.background(pal.surfaceElevated).heightIn(max = 300.dp)) {
                VoiceCatalog.all.forEach { v ->
                    DropdownMenuItem(text = { Text(v.id, color = pal.textPrimary, fontSize = 11.sp) }, onClick = { onSelect(v.id); expanded = false })
                }
            }
        }
    }
}

@Composable
private fun ThinkingPickerField(selectedName: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val profiles = LatencyProfile.values().toList()
    val current = profiles.firstOrNull { it.name == selectedName } ?: LatencyProfile.Low
    val pal = AppTheme.palette
    Column {
        Text("Мышление", color = pal.textPrimary, fontSize = 11.sp, modifier = Modifier.padding(bottom = 4.dp))
        Box {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(pal.background).clickable { expanded = true }.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(current.displayName, color = pal.textPrimary, fontSize = 11.sp, modifier = Modifier.weight(1f))
                Icon(Icons.Filled.ArrowDropDown, null, tint = pal.textPrimary)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.background(pal.surfaceElevated)) {
                profiles.forEach { p ->
                    DropdownMenuItem(text = { Text(p.displayName, color = pal.textPrimary, fontSize = 11.sp) }, onClick = { onSelect(p.name); expanded = false })
                }
            }
        }
    }
}