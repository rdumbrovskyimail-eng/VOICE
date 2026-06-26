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

    fun updateApiKey(key: String) = viewModelScope.launch { settingsStore.updateData { it.copy(apiKey = key) } }
    fun updateModel(model: String) = viewModelScope.launch { settingsStore.updateData { it.copy(model = model) } }
    
    fun updateVoice(voice: String) = viewModelScope.launch { settingsStore.updateData { it.copy(voiceId = voice) } }
    fun updateVolume(vol: Float) = viewModelScope.launch { settingsStore.updateData { it.copy(playbackVolume = vol.toInt()) } }
    fun updateMicGain(gain: Float) = viewModelScope.launch { settingsStore.updateData { it.copy(micGain = gain.toInt()) } }
    
    fun updateSystemInstruction(instruction: String) = viewModelScope.launch { settingsStore.updateData { it.copy(systemInstruction = instruction) } }
    fun updateTemperature(temp: Float) = viewModelScope.launch { settingsStore.updateData { it.copy(temperature = temp) } }
    fun updateLatencyProfile(profile: String) = viewModelScope.launch { settingsStore.updateData { it.copy(latencyProfile = profile) } }
    fun updateGoogleSearch(enable: Boolean) = viewModelScope.launch { settingsStore.updateData { it.copy(enableGoogleSearch = enable) } }
    
    fun updateContextCompression(enable: Boolean) = viewModelScope.launch { settingsStore.updateData { it.copy(enableContextCompression = enable) } }
    fun updateVadSilence(ms: String) = viewModelScope.launch { 
        val value = ms.toIntOrNull() ?: 800
        settingsStore.updateData { it.copy(vadSilenceDurationMs = value) } 
    }
    fun updateTranscriptions(enable: Boolean) = viewModelScope.launch { 
        settingsStore.updateData { it.copy(inputTranscription = enable, outputTranscription = enable) } 
    }
}