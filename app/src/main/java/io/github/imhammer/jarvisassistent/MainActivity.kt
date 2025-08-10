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

        val settingsDataStore = SettingsDataStore(applicationContext)

        setContent {
            JarvisAssistentTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    val navController = rememberNavController()

                    NavHost(navController = navController, startDestination = "main") {
                        composable("main") {
                            VoiceToTextScreen(navController = navController)
                        }
                        composable("settings") {
                            SettingsScreen(navController = navController, settingsDataStore = settingsDataStore)
                        }
                    }
                }
            }
        }
    }
}