// Путь: app/src/main/java/com/learnde/app/history/HistoryMessage.kt
package com.learnde.app.history

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Одно сохранённое сообщение режима History (накопительно). */
@Entity(tableName = "history_messages")
data class HistoryMessage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val role: String,                       // "user" | "model"
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
)
