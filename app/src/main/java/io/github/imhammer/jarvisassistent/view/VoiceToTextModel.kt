package io.github.imhammer.jarvisassistent.view

import android.app.Application
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class VoiceToTextState(
    val text: String = "",
    val isListening: Boolean = false,
    val error: String? = null,
    val hasPermission: Boolean = false
)

class VoiceToTextViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(VoiceToTextState())
    val state = _state.asStateFlow()

    private val recognizer: SpeechRecognizer = SpeechRecognizer.createSpeechRecognizer(application)

    init {
        setupRecognizer()
    }

    fun onPermissionResult(isGranted: Boolean) {
        _state.update { it.copy(hasPermission = isGranted) }
        if (!isGranted) {
            _state.update { it.copy(error = "Permissão para usar o microfone foi negada.") }
        }
    }

    fun startListening(languageCode: String = "pt-BR") {
        if (!state.value.hasPermission) {
            _state.update { it.copy(error = "Não é possível iniciar. Permissão de áudio não concedida.") }
            return
        }

        // Limpa o texto e erro anterior
        _state.update { it.copy(text = "", error = null) }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageCode)
            // Chave para o funcionamento offline!
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        }
        recognizer.startListening(intent)
    }

    fun stopListening() {
        recognizer.stopListening()
    }

    private fun setupRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(getApplication())) {
            _state.update { it.copy(error = "Reconhecimento de voz não está disponível neste dispositivo.") }
            return
        }

        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                _state.update { it.copy(isListening = true, error = null) }
            }

            override fun onBeginningOfSpeech() {
                // Opcional: pode ser útil para feedback visual
            }

            override fun onRmsChanged(rmsdB: Float) {
                // Opcional: para visualizações de áudio
            }

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                _state.update { it.copy(isListening = false) }
            }

            override fun onError(error: Int) {
                val errorMessage = when (error) {
                    SpeechRecognizer.ERROR_AUDIO -> "Erro de áudio."
                    SpeechRecognizer.ERROR_CLIENT -> "Erro no cliente."
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Permissões insuficientes."
                    SpeechRecognizer.ERROR_NETWORK -> "Erro de rede (tente baixar o pacote de idioma para uso offline)."
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Timeout de rede."
                    SpeechRecognizer.ERROR_NO_MATCH -> "Nenhuma correspondência encontrada."
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Serviço de reconhecimento ocupado."
                    SpeechRecognizer.ERROR_SERVER -> "Erro no servidor."
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Nenhuma fala detectada."
                    else -> "Ocorreu um erro desconhecido."
                }
                _state.update { it.copy(isListening = false, error = errorMessage) }
            }

            override fun onResults(results: Bundle?) {
                val transcribedText = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.getOrNull(0) ?: ""
                _state.update { it.copy(text = transcribedText) }
            }

            override fun onPartialResults(partialResults: Bundle?) {}

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
    }

    override fun onCleared() {
        super.onCleared()
        recognizer.destroy()
    }
}