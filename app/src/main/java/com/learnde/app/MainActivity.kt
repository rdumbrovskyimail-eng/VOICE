// Путь: app/src/main/java/com/learnde/app/MainActivity.kt
package com.learnde.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.learnde.app.data.settings.ThemeMode
import com.learnde.app.presentation.GateViewModel
import com.learnde.app.presentation.client.ClientScreen
import com.learnde.app.presentation.navigation.Routes
import com.learnde.app.ui.theme.GeminiVoiceTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val navController = rememberNavController()
            val gate: GateViewModel = hiltViewModel()
            val themeMode by gate.themeMode.collectAsStateWithLifecycle()

            val darkOverride: Boolean? = when (themeMode) {
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
                ThemeMode.AUTO -> null            // по системе
            }

            GeminiVoiceTheme(darkOverride = darkOverride) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    NavHost(
                        navController = navController,
                        startDestination = Routes.CLIENT,
                    ) {
                        composable(Routes.CLIENT) {
                            ClientScreen(navController = navController)
                        }
                        composable(Routes.SETTINGS) {
                            com.learnde.app.presentation.settings.SettingsScreen(
                                onBack = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }
}
