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

    fun updateApiKey(key: String) {
        viewModelScope.launch { settingsStore.updateData { it.copy(apiKey = key) } }
    }

    fun updateVoice(voice: String) {
        viewModelScope.launch { settingsStore.updateData { it.copy(voiceId = voice) } }
    }
}