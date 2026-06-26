package com.learnde.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.learnde.app.presentation.client.ClientScreen
import com.learnde.app.presentation.navigation.Routes
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
                            // Заглушка для экрана настроек. 
                            // Если у вас есть SettingsScreen, добавьте его сюда.
                        }
                    }
                }
            }
        }
    }
}