// Путь: app/src/main/java/com/learnde/app/history/HistoryModule.kt
package com.learnde.app.history

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object HistoryModule {

    @Provides
    @Singleton
    fun provideHistoryDatabase(@ApplicationContext ctx: Context): HistoryDatabase =
        Room.databaseBuilder(ctx, HistoryDatabase::class.java, "history.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    @Singleton
    fun provideHistoryDao(db: HistoryDatabase): HistoryDao = db.historyDao()
}
