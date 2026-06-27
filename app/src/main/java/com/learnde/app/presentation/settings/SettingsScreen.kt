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

private val BgColor = Color(0xFF0F1114)
private val SurfaceColor = Color(0xFF181B20)
private val AccentBlue = Color(0xFF6EA8FE)
private val TextDim = Color(0xFF8B919A)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Настройки", color = Color.White, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BgColor)
            )
        },
        containerColor = BgColor
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // --- СЕКЦИЯ 1: ПОДКЛЮЧЕНИЕ ---
            SettingsSection("Подключение и Модель") {
                SettingsTextField(
                    label = "API Ключ Gemini",
                    value = settings.apiKey,
                    onValueChange = viewModel::updateApiKey,
                    placeholder = "AIzaSy..."
                )
                TextButton(onClick = { uriHandler.openUri("https://aistudio.google.com/apikey") }) {
                    Text("🔑 Получить бесплатный ключ в Google AI Studio →", color = AccentBlue, fontSize = 13.sp)
                }
                SettingsTextField(
                    label = "Модель",
                    value = settings.model,
                    onValueChange = viewModel::updateModel,
                    placeholder = "models/gemini-3.1-flash-live-preview",
                    hint = "По умолчанию: models/gemini-3.1-flash-live-preview"
                )
            }

            // --- СЕКЦИЯ 2: ГОЛОС И АУДИО ---
            SettingsSection("Голос и Аудио") {
                VoicePickerField(selectedId = settings.voiceId, onSelect = viewModel::updateVoice)
                SettingsSlider(
                    label = "Громкость ИИ: ${settings.playbackVolume}%",
                    value = settings.playbackVolume.toFloat(),
                    onValueChange = viewModel::updateVolume,
                    valueRange = 0f..100f
                )
                SettingsSlider(
                    label = "Чувствительность микрофона: ${settings.micGain}%",
                    value = settings.micGain.toFloat(),
                    onValueChange = viewModel::updateMicGain,
                    valueRange = 50f..200f
                )
            }

            // --- СЕКЦИЯ 3: ПОВЕДЕНИЕ ИИ ---
            SettingsSection("Поведение ИИ") {
                SettingsTextField(
                    label = "Системная инструкция (Persona)",
                    value = settings.systemInstruction,
                    onValueChange = viewModel::updateSystemInstruction,
                    placeholder = "Ты полезный ассистент...",
                    singleLine = false
                )
                SettingsSlider(
                    label = "Креативность (Temperature): ${String.format("%.1f", settings.temperature)}",
                    value = settings.temperature,
                    onValueChange = viewModel::updateTemperature,
                    valueRange = 0f..2f
                )
                ThinkingPickerField(selectedName = settings.latencyProfile, onSelect = viewModel::updateLatencyProfile)
                SettingsSwitch(
                    label = "Google Search Grounding",
                    description = "Позволяет ИИ искать актуальную информацию в интернете.",
                    checked = settings.enableGoogleSearch,
                    onCheckedChange = viewModel::updateGoogleSearch
                )
            }

            // --- СЕКЦИЯ 4: СЕССИЯ И VAD ---
            SettingsSection("Сессия и Распознавание (VAD)") {
                SettingsSwitch(
                    label = "Сжатие контекста",
                    description = "Снимает лимит сессии в 15 минут (Context Window Compression).",
                    checked = settings.enableContextCompression,
                    onCheckedChange = viewModel::updateContextCompression
                )
                SettingsSwitch(
                    label = "Транскрипция (Чат)",
                    description = "Отображать текст речи пользователя и ИИ на экране.",
                    checked = settings.inputTranscription,
                    onCheckedChange = viewModel::updateTranscriptions
                )
                SettingsTextField(
                    label = "Пауза конца речи (VAD Silence, мс)",
                    value = settings.vadSilenceDurationMs.toString(),
                    onValueChange = viewModel::updateVadSilence,
                    placeholder = "800",
                    hint = "Рекомендовано 500-800 мс. Меньше 500 мс приведет к обрывам фраз."
                )
            }
            
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(title, color = AccentBlue, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(SurfaceColor)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = content
        )
    }
}

@Composable
private fun SettingsTextField(label: String, value: String, onValueChange: (String) -> Unit, placeholder: String, hint: String? = null, singleLine: Boolean = true) {
    Column {
        Text(label, color = Color.White, fontSize = 14.sp, modifier = Modifier.padding(bottom = 6.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(placeholder, color = Color.DarkGray) },
            singleLine = singleLine,
            minLines = if (singleLine) 1 else 3,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                focusedBorderColor = AccentBlue, unfocusedBorderColor = Color(0x33FFFFFF),
                focusedContainerColor = BgColor, unfocusedContainerColor = BgColor
            )
        )
        if (hint != null) {
            Text(hint, color = TextDim, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun SettingsSlider(label: String, value: Float, onValueChange: (Float) -> Unit, valueRange: ClosedFloatingPointRange<Float>) {
    Column {
        Text(label, color = Color.White, fontSize = 14.sp)
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            colors = SliderDefaults.colors(thumbColor = AccentBlue, activeTrackColor = AccentBlue, inactiveTrackColor = Color.DarkGray)
        )
    }
}

@Composable
private fun SettingsSwitch(label: String, description: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
            Text(label, color = Color.White, fontSize = 14.sp)
            Text(description, color = TextDim, fontSize = 12.sp, lineHeight = 16.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = AccentBlue, uncheckedTrackColor = Color.DarkGray)
        )
    }
}

@Composable
private fun VoicePickerField(selectedId: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val current = VoiceCatalog.byId(selectedId) ?: VoiceCatalog.all.first()
    Column {
        Text("Голос ассистента", color = Color.White, fontSize = 14.sp, modifier = Modifier.padding(bottom = 6.dp))
        Box {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(BgColor)
                    .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(8.dp))
                    .clickable { expanded = true }.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("${current.id} · ${current.trait}", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    Text(current.description, color = TextDim, fontSize = 12.sp, maxLines = 2, lineHeight = 15.sp)
                }
                Icon(Icons.Filled.ArrowDropDown, null, tint = TextDim)
            }
            DropdownMenu(
                expanded = expanded, onDismissRequest = { expanded = false },
                modifier = Modifier.background(SurfaceColor).heightIn(max = 460.dp)
            ) {
                VoiceCatalog.all.forEach { v ->
                    DropdownMenuItem(
                        text = {
                            Column(Modifier.padding(vertical = 2.dp).widthIn(max = 320.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(v.id, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                                    Spacer(Modifier.width(8.dp)); Text(v.trait, color = AccentBlue, fontSize = 12.sp)
                                    if (v.id in VoiceCatalog.recommended) {
                                        Spacer(Modifier.width(6.dp)); Text("★", color = Color(0xFFFFC107), fontSize = 13.sp)
                                    }
                                }
                                Text(v.description, color = TextDim, fontSize = 12.sp, lineHeight = 15.sp)
                            }
                        },
                        onClick = { onSelect(v.id); expanded = false }
                    )
                }
            }
        }
        Text("★ — рекомендуемые. Послушать все голоса можно в Google AI Studio.", color = TextDim, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun ThinkingPickerField(selectedName: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val profiles = LatencyProfile.values().toList()
    val current = profiles.firstOrNull { it.name == selectedName } ?: LatencyProfile.Low
    Column {
        Text("Уровень мышления (Thinking)", color = Color.White, fontSize = 14.sp, modifier = Modifier.padding(bottom = 6.dp))
        Box {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(BgColor)
                    .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(8.dp))
                    .clickable { expanded = true }.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(current.displayName, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    Text(thinkingHint(current), color = TextDim, fontSize = 12.sp, lineHeight = 15.sp)
                }
                Icon(Icons.Filled.ArrowDropDown, null, tint = TextDim)
            }
            DropdownMenu(
                expanded = expanded, onDismissRequest = { expanded = false },
                modifier = Modifier.background(SurfaceColor)
            ) {
                profiles.forEach { p ->
                    DropdownMenuItem(
                        text = {
                            Column(Modifier.padding(vertical = 2.dp).widthIn(max = 300.dp)) {
                                Text(p.displayName, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                                Text(thinkingHint(p), color = TextDim, fontSize = 12.sp, lineHeight = 15.sp)
                            }
                        },
                        onClick = { onSelect(p.name); expanded = false }
                    )
                }
            }
        }
    }
}

private fun thinkingHint(p: LatencyProfile): String = when (p) {
    LatencyProfile.Off       -> "Без размышления. Самый быстрый ответ, минимум задержки."
    LatencyProfile.UltraLow  -> "Почти без размышления. Очень быстро, для простых реплик."
    LatencyProfile.Low       -> "Лёгкое размышление. Баланс скорости и качества (по умолчанию)."
    LatencyProfile.Balanced  -> "Среднее размышление. Точнее на сложных вопросах, чуть медленнее."
    LatencyProfile.Reasoning -> "Глубокое размышление. Лучшее качество рассуждений, выше задержка."
}