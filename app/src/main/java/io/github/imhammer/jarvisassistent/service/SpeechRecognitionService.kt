package io.github.imhammer.jarvisassistent.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import io.github.imhammer.jarvisassistent.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.Locale
import io.github.imhammer.jarvisassistent.data.SettingsDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

// 1. Implementar a interface do TTS OnInitListener
class SpeechRecognitionService : Service(), TextToSpeech.OnInitListener {

    private lateinit var settingsDataStore: SettingsDataStore
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var assistantName = "Jarvis" // Valor padrão
    private var assistantGender = "Masculino"

    // Novo estado para controlar se o assistente foi "ativado"
    private var isAwake = false

    private val binder = LocalBinder()
    private lateinit var speechRecognizer: SpeechRecognizer

    // 2. Declarar a variável do TextToSpeech
    private lateinit var tts: TextToSpeech

    private val _state = MutableStateFlow(VoiceToTextState())
    val state = _state.asStateFlow()

    companion object {
        const val CHANNEL_ID = "SpeechRecognitionServiceChannel"
        private const val UTTERANCE_ID = "JarvisUtterance"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val notification = createNotification()
        startForeground(1, notification)

        // Inicializa o SpeechRecognizer
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        setupRecognizer()

        // Inicializa o DataStore e carrega as configs
        settingsDataStore = SettingsDataStore(applicationContext)
        serviceScope.launch {
            assistantName = settingsDataStore.getAssistantName.first()
            assistantGender = settingsDataStore.getAssistantGender.first()
        }

        tts = TextToSpeech(this, this)
    }

    // 4. Implementar o onInit para configurar o TTS quando estiver pronto
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            // Define o idioma para Português do Brasil
            val locale = Locale("pt", "BR")
            tts.language = locale

            // ESCOLHE A VOZ COM BASE NO GÊNERO
            val voice = tts.voices.find {
                it.locale == locale &&
                        if (assistantGender == "Masculino") it.name.contains("male", ignoreCase = true)
                        else it.name.contains("female", ignoreCase = true)
            }
            tts.voice = voice ?: tts.defaultVoice

            // Configura um listener para saber quando a fala termina
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    // A fala começou
                }

                @RequiresApi(Build.VERSION_CODES.P)
                override fun onDone(utteranceId: String?) {
                    // A fala terminou, podemos voltar a ouvir
                    if (utteranceId == UTTERANCE_ID) {
                        // É preciso rodar na thread principal para iniciar a escuta
                        mainExecutor.execute {
                            startListening()
                        }
                    }
                }

                @RequiresApi(Build.VERSION_CODES.P)
                override fun onError(utteranceId: String?) {
                    // Se der erro na fala, voltamos a ouvir
                    mainExecutor.execute {
                        startListening()
                    }
                }
            })
        } else {
            _state.update { it.copy(error = "Falha ao inicializar TTS.") }
        }
    }

    // ... outros métodos do serviço como onStartCommand, onBind ...
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }


    fun startListening(languageCode: String = "pt-BR") {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageCode)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        }
        speechRecognizer.startListening(intent)
    }

    fun stopListening() {
        speechRecognizer.stopListening()
    }

    private fun speak(text: String) {
        // Usa um ID único para rastrear a fala no UtteranceProgressListener
        val params = Bundle()
        params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, UTTERANCE_ID)
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, params, UTTERANCE_ID)
    }


    private fun setupRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(application)) {
            _state.update { it.copy(error = "Reconhecimento de voz não está disponível.") }
            return
        }

        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                _state.update { it.copy(isListening = true, error = null) }
            }

            override fun onBeginningOfSpeech() {}

            override fun onRmsChanged(rmsdB: Float) {
                _state.update { it.copy(rmsDb = rmsdB) }
            }

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                _state.update { it.copy(isListening = false) }
            }

            override fun onError(error: Int) {
                val errorMessage = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH -> "Nenhuma correspondência."
                    else -> "Erro no reconhecimento."
                }
                _state.update { it.copy(isListening = false, error = errorMessage) }
                startListening()
            }

            override fun onResults(results: Bundle?) {
                val transcribedText = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.getOrNull(0)

                if (!transcribedText.isNullOrBlank()) {
                    _state.update { it.copy(text = transcribedText) }

                    // LÓGICA DO "WAKE WORD"
                    val command = transcribedText.lowercase()
                    if (isAwake) {
                        // Se já está "acordado", processa o comando (aqui, só repete)
                        speak(command)
                        isAwake = false // Volta a dormir depois de executar
                    } else if (command.contains(assistantName.lowercase())) {
                        // Se o nome foi chamado, acorda e responde
                        isAwake = true
                        speak("Pois não?") // Ou "Sim?", "À sua disposição?"
                    } else {
                        // Se não é o nome e não está acordado, apenas volta a ouvir
                        startListening()
                    }
                } else {
                    startListening()
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
    }

    // ... métodos de notificação ...
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Canal do Serviço de Reconhecimento",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Assistente Ativo")
            .setContentText("O reconhecimento de voz está rodando.")
            .setSmallIcon(R.drawable.ic_launcher_foreground) // Use um ícone apropriado
            .build()
    }


    inner class LocalBinder : Binder() {
        fun getService(): SpeechRecognitionService = this@SpeechRecognitionService
    }

    data class VoiceToTextState(
        val text: String = "",
        val isListening: Boolean = false,
        val error: String? = null,
        val rmsDb: Float = 0f
    )

    override fun onDestroy() {
        // 6. Desligar o TTS e o SpeechRecognizer ao destruir o serviço
        tts.stop()
        tts.shutdown()
        speechRecognizer.destroy()
        SupervisorJob().cancel()
        super.onDestroy()
    }
}