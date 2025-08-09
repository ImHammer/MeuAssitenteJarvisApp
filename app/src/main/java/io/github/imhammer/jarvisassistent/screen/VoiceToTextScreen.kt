package io.github.imhammer.jarvisassistent.screen

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.imhammer.jarvisassistent.view.VoiceToTextViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceToTextScreen(
    viewModel: VoiceToTextViewModel = viewModel()
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()

    // Launcher para solicitar a permissão de gravação de áudio
    val recordAudioLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            viewModel.onPermissionResult(isGranted)
        }
    )

    // Solicita a permissão assim que o Composable entra na composição
    LaunchedEffect(key1 = true) {
        recordAudioLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
    }

    // UI da tela
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Voz para Texto Offline") })
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Área de exibição do texto
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (state.text.isBlank() && state.error == null) {
                        Text(
                            text = "Pressione o microfone para começar a falar...",
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.Gray,
                            textAlign = TextAlign.Center
                        )
                    } else {
                        Text(
                            text = state.text,
                            style = MaterialTheme.typography.headlineSmall,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Exibição de erros
            state.error?.let { error ->
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(8.dp)
                )
            }

            // Botão de ação (gravar/parar)
            FloatingActionButton(
                onClick = {
                    if (state.hasPermission) {
                        if (state.isListening) {
                            viewModel.stopListening()
                        } else {
                            viewModel.startListening()
                        }
                    } else {
                        // Tenta pedir a permissão novamente se o usuário clicar no botão
                        recordAudioLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                    }
                },
                modifier = Modifier.size(72.dp)
            ) {
                Icon(
                    imageVector = if (state.isListening) Icons.Default.Star else Icons.Default.Phone,
                    contentDescription = if (state.isListening) "Parar gravação" else "Iniciar gravação",
                    modifier = Modifier.size(36.dp),
                    tint = if (state.isListening) Color.Red else LocalContentColor.current
                )
            }
        }
    }
}