package com.learnde.app.translate

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.learnde.app.session.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TranslatorViewModel @Inject constructor(
    private val manager: TranslatorManager,
    private val session: SessionManager,
) : ViewModel() {

    val state = manager.state
    val amplitude = manager.amplitude

    @Volatile private var started = false

    /** Автозапуск при открытии экрана: глушим ассистента → запускаем синхронный перевод. */
    fun startAuto(bidirectional: Boolean = true) {
        if (started) return
        started = true
        viewModelScope.launch {
            session.shutdownBlocking()
            manager.start(bidirectional)
        }
    }

    fun stop() {
        started = false
        manager.stop()
    }

    fun retry(bidirectional: Boolean = true) {
        started = true
        viewModelScope.launch {
            session.shutdownBlocking()
            manager.stop()
            manager.start(bidirectional)
        }
    }

    override fun onCleared() {
        manager.stop()
        super.onCleared()
    }
}