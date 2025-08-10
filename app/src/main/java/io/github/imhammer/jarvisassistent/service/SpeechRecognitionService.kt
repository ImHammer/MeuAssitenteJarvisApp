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
import io.github.imhammer.jarvisassistent.lib.AssistantIntent
import io.github.imhammer.jarvisassistent.lib.IntentParser
import io.github.imhammer.jarvisassistent.data.AppDatabase
import io.github.imhammer.jarvisassistent.data.Event
import io.github.imhammer.jarvisassistent.data.Task

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

    private val database by lazy { AppDatabase.getDatabase(this) }
    private val dao by lazy { database.assistenteDao() }

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

    // Função interna para processar o comando e evitar repetição de código
    private fun processCommand(payload: String) {
        val intent = IntentParser.parse(payload)
        isAwake = false

        serviceScope.launch { // Envolvemos tudo em uma coroutine
            when (intent) {
                is AssistantIntent.AddTask -> {
                    val task = Task(description = intent.taskDescription, date = "Hoje")
                    dao.insertTask(task)
                    speak("Ok, anotei: ${intent.taskDescription}")
                }
                is AssistantIntent.AddTaskForDate -> {
                    val task = Task(description = intent.taskDescription, date = intent.date)
                    dao.insertTask(task)
                    speak("Anotado para ${intent.date}: ${intent.taskDescription}")
                }
                is AssistantIntent.ListPendingTasks -> {
                    val tasks = dao.getPendingTasks().first() // .first() pega o valor atual do Flow
                    if (tasks.isEmpty()) {
                        speak("Você não tem nenhuma tarefa pendente.")
                    } else {
                        val taskList = tasks.joinToString(separator = ". ") { "Para ${it.date}: ${it.description}" }
                        speak("Suas tarefas são: $taskList")
                    }
                }
                is AssistantIntent.AddEventForDate -> {
                    val event = Event(description = intent.eventDescription, date = intent.date)
                    dao.insertEvent(event)
                    speak("Evento adicionado para ${intent.date}: ${intent.eventDescription}")
                }
                is AssistantIntent.ListTodaysEvents -> {
                    val events = dao.getEventsForDate("hoje").first()
                    if (events.isEmpty()) {
                        speak("Você não tem eventos para hoje.")
                    } else {
                        val eventList = events.joinToString(separator = ". ") { it.description }
                        speak("Seus eventos para hoje são: $eventList")
                    }
                }
                is AssistantIntent.Unknown -> {
                    speak("Desculpe, não entendi o comando.")
                }
            }
        }
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

                if (transcribedText.isNullOrBlank()) {
                    startListening()
                    return
                }

                _state.update { it.copy(text = transcribedText) }
                val command = transcribedText.lowercase()
                val assistantNameLower = assistantName.lowercase()

                if (isAwake) {
                    // Cenário 1: O assistente já estava acordado esperando um comando.
                    processCommand(command)
                } else if (command.contains(assistantNameLower)) {
                    // Cenário 2: O nome foi detectado na frase.
                    // Extrai o que foi dito DEPOIS do nome do assistente.
                    val commandPayload = command.substringAfter(assistantNameLower).trim()

                    if (commandPayload.isBlank()) {
                        // A pessoa só disse "Jarvis". Responda e espere o próximo comando.
                        isAwake = true
                        speak("Pois não?")
                    } else {
                        // A pessoa disse "Jarvis" e mais alguma coisa.
                        // Tenta processar o que veio depois como um comando direto.
                        processCommand(commandPayload)
                    }
                } else {
                    // Cenário 3: Não estava acordado e o nome não foi dito. Continue ouvindo.
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