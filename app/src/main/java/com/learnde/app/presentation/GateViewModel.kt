// Путь: app/src/main/java/com/learnde/app/presentation/GateViewModel.kt
package com.learnde.app.presentation

import androidx.datastore.core.DataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.learnde.app.data.settings.AppSettings
import com.learnde.app.data.settings.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Отдаёт выбранную тему.
 */
@HiltViewModel
class GateViewModel @Inject constructor(
    store: DataStore<AppSettings>
) : ViewModel() {

    /** Тема приложения (AUTO/LIGHT/DARK) — оживляет настройку themeMode. */
    val themeMode: StateFlow<ThemeMode> = store.data
        .map { it.themeMode }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ThemeMode.AUTO)
}
