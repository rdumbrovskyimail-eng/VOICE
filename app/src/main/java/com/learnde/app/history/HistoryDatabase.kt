// Путь: app/src/main/java/com/learnde/app/history/HistoryDatabase.kt
package com.learnde.app.history

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [HistoryMessage::class], version = 1, exportSchema = false)
abstract class HistoryDatabase : RoomDatabase() {
    abstract fun historyDao(): HistoryDao
}
