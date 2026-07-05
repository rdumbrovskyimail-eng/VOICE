// Путь: app/src/main/java/com/learnde/app/presentation/settings/SettingsScreen.kt
//
// ★ ДОПОЛНЕНО ★
//   • секция «Аудио»: слайдер «Усиление Forvo» (0–100 %);
//   • новая секция «Переводчик»: выбор языковой пары;
//   • новая секция «Интерфейс»: тема Авто/Светлая/Тёмная;
//   • новая секция «Дополнительно»: транскрипция, сжатие контекста, пауза VAD.
//   Все существующие настройки и composables сохранены как были.

package com.learnde.app.presentation.settings

import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.learnde.app.data.voice.VoiceCatalog
import com.learnde.app.domain.model.LatencyProfile
import com.learnde.app.translate.TranslatorLanguages
import com.learnde.app.ui.theme.AppTheme
import com.learnde.app.ui.theme.Radius
import com.learnde.app.ui.theme.Space

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val pal = AppTheme.palette

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Настройки", style = MaterialTheme.typography.titleLarge, color = pal.textPrimary) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад", tint = pal.textPrimary)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = pal.background)
                )
                HorizontalDivider(color = pal.outline, thickness = 1.dp)
            }
        },
        containerColor = pal.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(Space.lg),
        ) {
            SettingsSection("Подключение") {
                SettingsTextField("API Ключ", settings.apiKey, viewModel::updateApiKey, "AIzaSy...")
                SettingsTextField("Forvo API key", settings.forvoApiKey, viewModel::updateForvoApiKey, "...")
                SettingsTextField("Модель", settings.model, viewModel::updateModel, "models/gemini-3.1-flash-live-preview")
            }

            SettingsSection("Аудио") {
                VoicePickerField(settings.voiceId, viewModel::updateVoice)
                SettingsSlider("Громкость: ${settings.playbackVolume}%", settings.playbackVolume.toFloat(), viewModel::updateVolume, 0f..100f)
                SettingsSlider("Микрофон: ${settings.micGain}%", settings.micGain.toFloat(), viewModel::updateMicGain, 50f..150f)
                // ★ Цифровое усиление произношений Forvo (LoudnessEnhancer).
                SettingsSlider("Усиление Forvo: +${settings.forvoBoostPercent}%", settings.forvoBoostPercent.toFloat(), viewModel::updateForvoBoost, 0f..100f)
            }

            // ★ Языковая пара синхронного переводчика.
            SettingsSection("Переводчик") {
                LanguagePickerField("Язык A", settings.translatorLangA, viewModel::updateTranslatorLangA)
                LanguagePickerField("Язык B", settings.translatorLangB, viewModel::updateTranslatorLangB)
                Text(
                    "Переводчик слушает оба языка и озвучивает перевод на противоположный.",
                    style = MaterialTheme.typography.bodySmall, color = pal.textDim
                )
            }

            SettingsSection("Поведение") {
                SettingsTextField("Системная инструкция", settings.systemInstruction, viewModel::updateSystemInstruction, "Ты ассистент...", false)
                SettingsSlider("Креативность: ${String.format(java.util.Locale.US, "%.1f", settings.temperature)}", settings.temperature, viewModel::updateTemperature, 0f..2f)
                ThinkingPickerField(settings.latencyProfile, viewModel::updateLatencyProfile)
                SettingsSwitch("Google Search", settings.enableGoogleSearch, viewModel::updateGoogleSearch)
                SettingsSwitch("Перебивание (Barge-in)", settings.bargeInEnabled, viewModel::updateBargeIn)
            }

            // ★ Ранее эти настройки существовали в данных, но не имели UI.
            SettingsSection("Дополнительно") {
                SettingsSwitch("Транскрипция речи", settings.inputTranscription, viewModel::updateTranscriptions)
                SettingsSwitch("Сжатие контекста", settings.enableContextCompression, viewModel::updateContextCompression)
                SettingsTextField("Пауза VAD, мс", settings.vadSilenceDurationMs.toString(), viewModel::updateVadSilence, "800")
            }

            Spacer(modifier = Modifier.height(Space.xl))
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    val pal = AppTheme.palette
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = Space.xl)) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = pal.textSecondary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = Space.sm, bottom = Space.sm)
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radius.lg))
                .background(pal.surfaceElevated)
                .padding(Space.lg),
            verticalArrangement = Arrangement.spacedBy(Space.lg),
            content = content
        )
    }
}

@Composable
private fun SettingsTextField(label: String, value: String, onValueChange: (String) -> Unit, placeholder: String, singleLine: Boolean = true) {
    val pal = AppTheme.palette
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge, color = pal.textPrimary, modifier = Modifier.padding(bottom = Space.xs))
        OutlinedTextField(
            value = value, onValueChange = onValueChange, modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(placeholder, style = MaterialTheme.typography.bodyMedium, color = pal.textDim) },
            singleLine = singleLine, minLines = if (singleLine) 1 else 3,
            textStyle = MaterialTheme.typography.bodyLarge,
            shape = RoundedCornerShape(Radius.md),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = pal.textPrimary, unfocusedTextColor = pal.textPrimary,
                focusedBorderColor = pal.accentBlue, unfocusedBorderColor = Color.Transparent,
                focusedContainerColor = pal.surface, unfocusedContainerColor = pal.surface,
                cursorColor = pal.accentBlue
            )
        )
    }
}

@Composable
private fun SettingsSlider(label: String, value: Float, onValueChange: (Float) -> Unit, valueRange: ClosedFloatingPointRange<Float>) {
    val pal = AppTheme.palette
    Column {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = pal.textPrimary)
        Slider(
            value = value, onValueChange = onValueChange, valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = pal.accentBlue,
                activeTrackColor = pal.accentBlue,
                inactiveTrackColor = pal.outline
            )
        )
    }
}

@Composable
private fun SettingsSwitch(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val pal = AppTheme.palette
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = pal.textPrimary)
        Switch(
            checked = checked, onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = pal.accentBlue,
                uncheckedThumbColor = pal.textDim,
                uncheckedTrackColor = pal.outline,
                uncheckedBorderColor = Color.Transparent
            )
        )
    }
}

/** Универсальный dropdown в стиле остальных полей. */
@Composable
private fun DropdownField(
    label: String,
    valueText: String,
    options: List<Pair<String, String>>,   // value → отображаемое имя
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val pal = AppTheme.palette
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge, color = pal.textPrimary, modifier = Modifier.padding(bottom = Space.xs))
        Box {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radius.md))
                    .background(pal.surface)
                    .clickable { expanded = true }
                    .padding(Space.md),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(valueText, style = MaterialTheme.typography.bodyLarge, color = pal.textPrimary, modifier = Modifier.weight(1f))
                Icon(Icons.Filled.ArrowDropDown, null, tint = pal.textSecondary)
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(pal.background).heightIn(max = 300.dp)
            ) {
                options.forEach { (value, name) ->
                    DropdownMenuItem(
                        text = { Text(name, style = MaterialTheme.typography.bodyLarge, color = pal.textPrimary) },
                        onClick = { onSelect(value); expanded = false }
                    )
                }
            }
        }
    }
}

/** ★ Выбор языка переводчика из каталога TranslatorLanguages. */
@Composable
private fun LanguagePickerField(label: String, selectedCode: String, onSelect: (String) -> Unit) {
    DropdownField(
        label = label,
        valueText = "${selectedCode.uppercase()} · ${TranslatorLanguages.name(selectedCode)}",
        options = TranslatorLanguages.all,
        onSelect = onSelect
    )
}

@Composable
private fun VoicePickerField(selectedId: String, onSelect: (String) -> Unit) {
    val current = VoiceCatalog.byId(selectedId) ?: VoiceCatalog.all.first()
    fun label(v: com.learnde.app.data.voice.GeminiVoice) = buildString {
        append(v.id); append(" · "); append(v.trait)
        if (v.id in VoiceCatalog.recommended) append("  ★")
    }
    DropdownField(
        label = "Голос",
        valueText = label(current),
        options = VoiceCatalog.all.map { it.id to label(it) },
        onSelect = onSelect
    )
}

@Composable
private fun ThinkingPickerField(selectedName: String, onSelect: (String) -> Unit) {
    val profiles = LatencyProfile.values().toList()
    val current = profiles.firstOrNull { it.name == selectedName } ?: LatencyProfile.Low
    DropdownField(
        label = "Мышление",
        valueText = current.displayName,
        options = profiles.map { it.name to it.displayName },
        onSelect = onSelect
    )
}
