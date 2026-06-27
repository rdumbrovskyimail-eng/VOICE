// Путь: app/src/main/java/com/learnde/app/presentation/client/ClientViewModel.kt
//
// Тонкая обёртка над SessionManager.
//   • Состояние и логика сессии — в SessionManager (singleton): устраняет рассинхрон LiveClient
//     и сохраняет сессию при пересоздании ViewModel (поворот, переход в настройки).
//   • Дополнительно отдаёт настройки отображения чата (chatPrefs), чтобы экран их применял.
//   • Намеренно НЕ останавливаем сессию в onCleared: ею владеет singleton + foreground service.

package com.learnde.app.presentation.client

import androidx.datastore.core.DataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.learnde.app.data.settings.AppSettings
import com.learnde.app.session.ClientMode
import com.learnde.app.session.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Настройки отображения чата (берутся из AppSettings, применяются в ClientScreen). */
data class ChatPrefs(
    val fontScale: Float = 1f,
    val showRoleLabels: Boolean = true,
    val showTimestamps: Boolean = false,
    val autoScroll: Boolean = true,
)

@HiltViewModel
class ClientViewModel @Inject constructor(
    private val session: SessionManager,
    settingsStore: DataStore<AppSettings>,
) : ViewModel() {

    val state = session.state
    val amplitude = session.amplitude
    val historyMessages = session.historyMessages

    val historyInfo: StateFlow<HistoryInfo> = settingsStore.data
        .map { HistoryInfo(prompt = it.historyPrompt, locked = it.historyPromptLocked) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryInfo())

    val chatPrefs: StateFlow<ChatPrefs> = settingsStore.data
        .map {
            ChatPrefs(
                fontScale = it.chatFontScale,
                showRoleLabels = it.chatShowRoleLabels,
                showTimestamps = it.chatShowTimestamps,
                autoScroll = it.chatAutoScroll,
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChatPrefs())

    fun applyPrompt(prompt: String, attachments: List<android.net.Uri> = emptyList()) = session.applyPrompt(prompt, attachments)
    fun toggleConnection() = session.toggleConnection()
    fun sendText(text: String, attachments: List<android.net.Uri> = emptyList()) = session.sendText(text, attachments)
    fun toggleMic() = session.toggleMic()
    fun clearDashboard() = session.clearDashboard()
    fun clearError() = session.clearError()
    fun retry() = session.retry()

    fun toggleHistoryMode() {
        val next = if (state.value.mode == ClientMode.HISTORY) ClientMode.NORMAL else ClientMode.HISTORY
        session.setMode(next)
    }
    fun applyHistoryPrompt(prompt: String) = session.setHistoryPrompt(prompt)
    fun clearHistory() = session.clearHistory()
}

data class HistoryInfo(val prompt: String = "", val locked: Boolean = false)
