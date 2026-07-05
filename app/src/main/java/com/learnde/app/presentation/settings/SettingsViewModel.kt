// Путь: app/src/main/java/com/learnde/app/presentation/settings/SettingsViewModel.kt
//
// ★ ДОПОЛНЕНО ★
//   • updateThemeMode        — тема (Авто/Светлая/Тёмная) теперь реально меняется;
//   • updateForvoBoost       — цифровое усиление произношений Forvo (0–100 %);
//   • updateTranslatorLangA/B — языковая пара переводчика; при совпадении
//     языков пара автоматически меняется местами (a==b недопустимо).

package com.learnde.app.presentation.settings

import androidx.datastore.core.DataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.learnde.app.data.settings.AppSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsStore: DataStore<AppSettings>
) : ViewModel() {

    val settings = settingsStore.data.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        AppSettings()
    )

    fun updateApiKey(key: String) = viewModelScope.launch { settingsStore.updateData { it.copy(apiKey = key.trim()) } }
    fun updateForvoApiKey(key: String) =
        viewModelScope.launch { settingsStore.updateData { it.copy(forvoApiKey = key.trim()) } }
    fun updateModel(model: String) = viewModelScope.launch { settingsStore.updateData { it.copy(model = model) } }

    fun updateVoice(voice: String) = viewModelScope.launch { settingsStore.updateData { it.copy(voiceId = voice) } }
    fun updateVolume(vol: Float) = viewModelScope.launch { settingsStore.updateData { it.copy(playbackVolume = vol.toInt()) } }
    fun updateMicGain(gain: Float) = viewModelScope.launch { settingsStore.updateData { it.copy(micGain = gain.toInt()) } }

    fun updateSystemInstruction(instruction: String) = viewModelScope.launch { settingsStore.updateData { it.copy(systemInstruction = instruction) } }
    fun updateTemperature(temp: Float) = viewModelScope.launch { settingsStore.updateData { it.copy(temperature = temp) } }
    fun updateLatencyProfile(profile: String) = viewModelScope.launch { settingsStore.updateData { it.copy(latencyProfile = profile) } }
    fun updateGoogleSearch(enable: Boolean) = viewModelScope.launch { settingsStore.updateData { it.copy(enableGoogleSearch = enable) } }
    fun updateBargeIn(enable: Boolean) = viewModelScope.launch { settingsStore.updateData { it.copy(bargeInEnabled = enable) } }

    fun updateContextCompression(enable: Boolean) = viewModelScope.launch { settingsStore.updateData { it.copy(enableContextCompression = enable) } }
    fun updateVadSilence(ms: String) = viewModelScope.launch {
        val value = ms.toIntOrNull() ?: 800
        settingsStore.updateData { it.copy(vadSilenceDurationMs = value) }
    }
    fun updateTranscriptions(enable: Boolean) = viewModelScope.launch {
        settingsStore.updateData { it.copy(inputTranscription = enable, outputTranscription = enable) }
    }

    // ─────────────── ★ Новое ───────────────

    /** Усиление произношений Forvo, % (0–100). */
    fun updateForvoBoost(percent: Float) =
        viewModelScope.launch { settingsStore.updateData { it.copy(forvoBoostPercent = percent.toInt().coerceIn(0, 100)) } }

    /** Язык A переводчика. Совпадение с B → языки меняются местами. */
    fun updateTranslatorLangA(code: String) = viewModelScope.launch {
        settingsStore.updateData {
            if (code == it.translatorLangB)
                it.copy(translatorLangA = code, translatorLangB = it.translatorLangA)
            else it.copy(translatorLangA = code)
        }
    }

    /** Язык B переводчика. Совпадение с A → языки меняются местами. */
    fun updateTranslatorLangB(code: String) = viewModelScope.launch {
        settingsStore.updateData {
            if (code == it.translatorLangA)
                it.copy(translatorLangB = code, translatorLangA = it.translatorLangB)
            else it.copy(translatorLangB = code)
        }
    }
}
