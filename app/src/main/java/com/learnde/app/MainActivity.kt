// Путь: app/src/main/java/com/learnde/app/MainActivity.kt
//
// Изменение: добавлен маршрут онбординга и «гейт» первого запуска.
//   • GateViewModel читает onboardingDone из настроек; пока значение неизвестно (null) —
//     никуда не переходим (без ложного мигания).
//   • Если онбординг не пройден → один раз навигируемся на Routes.ONBOARDING.
//   • OnboardingScreen по завершении возвращает на Routes.CLIENT и убирает себя из стека.

package com.learnde.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import com.learnde.app.domain.model.ThemeMode
import com.learnde.app.ui.theme.GeminiVoiceTheme
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.learnde.app.presentation.GateViewModel
import com.learnde.app.presentation.client.ClientScreen
import com.learnde.app.presentation.navigation.Routes
import com.learnde.app.presentation.onboarding.OnboardingScreen
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val gate: GateViewModel = hiltViewModel()
            val themeMode by gate.themeMode.collectAsStateWithLifecycle()
            val dark = when (themeMode) { ThemeMode.DARK -> true; ThemeMode.LIGHT -> false; else -> null }

            GeminiVoiceTheme(darkOverride = dark) {
                Surface(
                    modifier = Modifier.fillMaxSize()
                ) {
                    val navController = rememberNavController()
                    val needsOnboarding by gate.needsOnboarding.collectAsStateWithLifecycle()

                    if (needsOnboarding == null) {
                        Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0F1114)))
                    } else {
                        NavHost(
                            navController = navController,
                            startDestination = if (needsOnboarding == true) Routes.ONBOARDING else Routes.CLIENT
                        ) {
                            composable(Routes.CLIENT) {
                                ClientScreen(navController = navController)
                            }
                            composable(Routes.SETTINGS) {
                                com.learnde.app.presentation.settings.SettingsScreen(
                                    onBack = { navController.popBackStack() }
                                )
                            }
                            composable(Routes.ONBOARDING) {
                                OnboardingScreen(
                                    onDone = {
                                        navController.navigate(Routes.CLIENT) {
                                            popUpTo(Routes.ONBOARDING) { inclusive = true }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
