package io.github.imhammer.jarvisassistent.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// Cria uma instância do DataStore para o app
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsDataStore(private val context: Context) {

    // Define as chaves para salvar os dados
    companion object {
        val ASSISTANT_NAME_KEY = stringPreferencesKey("assistant_name")
        val ASSISTANT_GENDER_KEY = stringPreferencesKey("assistant_gender")
    }

    // Fluxo para obter o nome do assistente
    val getAssistantName: Flow<String> = context.dataStore.data
        .map { preferences ->
            preferences[ASSISTANT_NAME_KEY] ?: "Jarvis" // Valor padrão "Jarvis"
        }

    // Fluxo para obter o gênero do assistente
    val getAssistantGender: Flow<String> = context.dataStore.data
        .map { preferences ->
            preferences[ASSISTANT_GENDER_KEY] ?: "Masculino" // Valor padrão "Masculino"
        }

    // Função para salvar as configurações
    suspend fun saveSettings(name: String, gender: String) {
        context.dataStore.edit { preferences ->
            preferences[ASSISTANT_NAME_KEY] = name
            preferences[ASSISTANT_GENDER_KEY] = gender
        }
    }
}