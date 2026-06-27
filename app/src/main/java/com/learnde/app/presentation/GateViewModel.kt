// Путь: app/src/main/java/com/learnde/app/presentation/GateViewModel.kt
package com.learnde.app.presentation

import androidx.datastore.core.DataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.learnde.app.data.settings.AppSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Решает, нужно ли показать онбординг при старте.
 * needsOnboarding == null → ещё не знаем (не дёргаем навигацию до первой эмиссии,
 * чтобы не было ложного перехода).
 */
@HiltViewModel
class GateViewModel @Inject constructor(
    store: DataStore<AppSettings>
) : ViewModel() {

    val needsOnboarding: StateFlow<Boolean?> = store.data
        .map { !it.onboardingDone }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
}
