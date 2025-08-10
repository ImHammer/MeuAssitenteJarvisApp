package io.github.imhammer.jarvisassistent.lib

import java.time.LocalDate
import java.time.format.DateTimeFormatter

// --- Sealed class de Intenções expandida ---
sealed class AssistantIntent {
    // Tarefas
    data class AddTask(val taskDescription: String) : AssistantIntent()
    data class AddTaskForDate(val taskDescription: String, val date: String) : AssistantIntent()
    object ListPendingTasks : AssistantIntent()

    // Eventos
    data class AddEventForDate(val eventDescription: String, val date: String) : AssistantIntent()
    object ListTodaysEvents : AssistantIntent()

    // Fallback
    object Unknown : AssistantIntent()
}

object IntentParser {
    // --- Listas de Palavras-chave ---
    private val listTasksKeywords  = listOf("", "quais as tarefas", "minhas tarefas", "tarefas pendentes")
    private val addTaskKeywords    = listOf("adicionar tarefa", "anote para mim", "nova tarefa", "anote")
    private val listEventsKeywords = listOf("eventos de hoje", "qual a minha agenda", "o que tenho para hoje")
    private val addEventKeywords   = listOf("novo evento", "adicionar evento", "marcar um evento")

    fun parse(text: String): AssistantIntent {
        val lowercasedText = text.lowercase()

        // --- Lógica de Análise (Parser) ---
        listTasksKeywords.forEach { keyword ->
            if (lowercasedText.contains(keyword)) return AssistantIntent.ListPendingTasks
        }

        listEventsKeywords.forEach { keyword ->
            if (lowercasedText.contains(keyword)) return AssistantIntent.ListTodaysEvents
        }

        addEventKeywords.forEach { keyword ->
            if (lowercasedText.contains(keyword)) {
                val (payload, date) = extractPayloadAndDate(lowercasedText.substringAfter(keyword))
                if (payload.isNotBlank()) return AssistantIntent.AddEventForDate(payload, date)
            }
        }

        addTaskKeywords.forEach { keyword ->
            if (lowercasedText.contains(keyword)) {
                val (payload, date) = extractPayloadAndDate(lowercasedText.substringAfter(keyword))
                if (payload.isNotBlank()) {
                    return if (date == "hoje") AssistantIntent.AddTask(payload) // Para compatibilidade
                    else AssistantIntent.AddTaskForDate(payload, date)
                }
            }
        }

        return AssistantIntent.Unknown
    }

    // --- Função Auxiliar para Extrair Datas ---
    private fun extractPayloadAndDate(text: String): Pair<String, String> {
        val today = LocalDate.now()
        val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

        var date = "hoje" // Padrão
        var payload = text

        when {
            text.contains(" para amanhã") -> {
                date = today.plusDays(1).format(formatter)
                payload = text.replace(" para amanhã", "").trim()
            }
            text.contains(" para o dia ") -> {
                val day = text.substringAfter(" para o dia ").split(" ")[0]
                date = "dia $day" // Simplificado, poderia ser mais robusto
                payload = text.substringBefore(" para o dia ").trim()
            }
            text.contains(" para hoje") -> {
                payload = text.replace(" para hoje", "").trim()
            }
        }
        return Pair(payload, date)
    }
}