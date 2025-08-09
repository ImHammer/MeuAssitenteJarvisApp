package io.github.imhammer.jarvisassistent.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import io.github.imhammer.jarvisassistent.data.SettingsDataStore
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(navController: NavController, settingsDataStore: SettingsDataStore) {
    var name by rememberSaveable { mutableStateOf("") }
    val genderOptions = listOf("Masculino", "Feminino") // Simplificado por enquanto
    var selectedGender by rememberSaveable { mutableStateOf(genderOptions[0]) }
    val scope = rememberCoroutineScope()

    // Carrega as configurações salvas quando a tela abre
    LaunchedEffect(key1 = true) {
        settingsDataStore.getAssistantName.collect { savedName ->
            name = savedName
        }
    }
    LaunchedEffect(key1 = true) {
        settingsDataStore.getAssistantGender.collect { savedGender ->
            selectedGender = savedGender
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Configurações") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Voltar"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text(
                text = "Personalize seu assistente",
                style = MaterialTheme.typography.titleLarge
            )

            // Campo de texto para o nome
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Nome do Assistente") },
                modifier = Modifier.fillMaxWidth()
            )

            // Opções de sexo
            Column(Modifier.fillMaxWidth()) {
                Text(text = "Voz do assistente:", style = MaterialTheme.typography.bodyLarge)
                genderOptions.forEach { gender ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = (gender == selectedGender),
                                onClick = { selectedGender = gender }
                            )
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (gender == selectedGender),
                            onClick = { selectedGender = gender }
                        )
                        Text(
                            text = gender,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(start = 16.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.weight(1f)) // Empurra o botão para baixo

            // Botão Salvar
            Button(
                onClick = {
                    scope.launch {
                        settingsDataStore.saveSettings(name, selectedGender)
                        navController.popBackStack() // Volta para a tela principal
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp)
            ) {
                Text("Salvar", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}