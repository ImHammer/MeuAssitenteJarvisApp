package io.github.imhammer.jarvisassistent.screen

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import io.github.imhammer.jarvisassistent.components.SoundWaveSphere
import io.github.imhammer.jarvisassistent.service.SpeechRecognitionService
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.navigation.NavController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceToTextScreen(navController: NavController) {
    val context = LocalContext.current
    var service: SpeechRecognitionService? by remember { mutableStateOf(null) }

    DisposableEffect(Unit) {
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                service = (binder as SpeechRecognitionService.LocalBinder).getService()
                service?.startListening()
            }
            override fun onServiceDisconnected(name: ComponentName?) {
                service = null
            }
        }
        Intent(context, SpeechRecognitionService::class.java).also { intent ->
            context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }
        onDispose {
            context.unbindService(connection)
        }
    }

    val state by service?.state?.collectAsState() ?: remember { mutableStateOf(SpeechRecognitionService.VoiceToTextState()) }

    // Launcher de permissão
    val recordAudioLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            if (isGranted) {
                ContextCompat.startForegroundService(context, Intent(context, SpeechRecognitionService::class.java))
            } else {
                // TODO: Informa o usuário que a permissão é necessária
            }
        }
    )

    LaunchedEffect(key1 = true) {
        recordAudioLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Assistente Jarvis") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { navController.navigate("settings") }) {
                Icon(Icons.Default.Settings, contentDescription = "Configurações")
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            SoundWaveSphere(
                rmsDb = state.rmsDb,
                isListening = state.isListening,
                modifier = Modifier.weight(1f) // Faz a esfera ocupar o espaço disponível
            )

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = state.text.ifBlank { "Ouvindo..." },
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 50.dp) // Adiciona um padding inferior
            )
        }
    }
}
