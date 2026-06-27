// Путь: app/src/main/java/com/learnde/app/history/HistoryDao.kt
package com.learnde.app.history

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {

    /** Реактивный поток всей истории (для UI). */
    @Query("SELECT * FROM history_messages ORDER BY id ASC")
    fun observeAll(): Flow<List<HistoryMessage>>

    /** Снимок всей истории (для посева в модель при старте сессии). */
    @Query("SELECT * FROM history_messages ORDER BY id ASC")
    suspend fun getAll(): List<HistoryMessage>

    @Insert
    suspend fun insert(msg: HistoryMessage): Long



    /** Полная очистка (кнопка Clear). */
    @Query("DELETE FROM history_messages")
    suspend fun clear()

    @Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun insertAll(messages: List<HistoryMessage>)

    @androidx.room.Transaction
    suspend fun replaceHistory(messages: List<HistoryMessage>) {
        clear()
        insertAll(messages)
    }

    @Query("SELECT COUNT(*) FROM history_messages")
    suspend fun count(): Int
}
