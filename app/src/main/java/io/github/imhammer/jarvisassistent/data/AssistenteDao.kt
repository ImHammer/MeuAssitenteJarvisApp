package io.github.imhammer.jarvisassistent.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AssistenteDao {
    // --- Funções de Tarefa ---
    @Insert
    suspend fun insertTask(task: Task)

    @Query("SELECT * FROM tasks WHERE isCompleted = 0 ORDER BY date ASC")
    fun getPendingTasks(): Flow<List<Task>>

    @Query("UPDATE tasks SET isCompleted = 1 WHERE id = :taskId")
    suspend fun completeTask(taskId: Int)

    // --- Funções de Evento ---
    @Insert
    suspend fun insertEvent(event: Event)

    @Query("SELECT * FROM events WHERE date = :date")
    fun getEventsForDate(date: String): Flow<List<Event>>
}