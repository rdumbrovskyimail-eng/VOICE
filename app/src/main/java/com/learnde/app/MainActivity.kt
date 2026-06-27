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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
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
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()

                    NavHost(
                        navController = navController,
                        startDestination = Routes.CLIENT
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

                    // Гейт первого запуска.
                    val gate: GateViewModel = hiltViewModel()
                    val needsOnboarding by gate.needsOnboarding.collectAsStateWithLifecycle()
                    LaunchedEffect(needsOnboarding) {
                        if (needsOnboarding == true) {
                            navController.navigate(Routes.ONBOARDING)
                        }
                    }
                }
            }
        }
    }
}
