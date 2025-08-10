package io.github.imhammer.jarvisassistent.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tasks")
data class Task(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val description: String,
    val date: String,
    val isCompleted: Boolean = false
)

@Entity(tableName = "events")
data class Event(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val description: String,
    val date: String
)