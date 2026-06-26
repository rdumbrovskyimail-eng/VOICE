package com.learnde.app.learn.core

import com.learnde.app.data.GeminiLiveClient
import com.learnde.app.domain.LiveClient
import com.learnde.app.util.AppLogger
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object LearnClientModule {
    @Provides
    @Singleton
    @LearnScope
    fun provideLearnLiveClient(logger: AppLogger): LiveClient =
        GeminiLiveClient(logger)
}