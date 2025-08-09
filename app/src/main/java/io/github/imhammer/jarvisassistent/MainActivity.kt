package io.github.imhammer.jarvisassistent

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
import io.github.imhammer.jarvisassistent.screen.SettingsScreen
import io.github.imhammer.jarvisassistent.screen.VoiceToTextScreen
import io.github.imhammer.jarvisassistent.ui.theme.JarvisAssistentTheme
import io.github.imhammer.jarvisassistent.data.SettingsDataStore

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Instancia o DataStore aqui
        val settingsDataStore = SettingsDataStore(applicationContext)

        setContent {
            JarvisAssistentTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    // 1. Crie o NavController
                    val navController = rememberNavController()

                    // 2. Crie o NavHost (roteador de telas)
                    NavHost(navController = navController, startDestination = "main") {
                        // Rota para a tela principal
                        composable("main") {
                            VoiceToTextScreen(navController = navController)
                        }
                        // Rota para a tela de configurações
                        composable("settings") {
                            SettingsScreen(navController = navController, settingsDataStore = settingsDataStore)
                        }
                    }
                }
            }
        }
    }
}