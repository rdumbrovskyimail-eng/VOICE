package com.learnde.app.di

import com.learnde.app.data.AndroidAudioEngine
import com.learnde.app.domain.AudioEngine
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AudioModule {
    @Binds
    @Singleton
    abstract fun bindAudioEngine(impl: AndroidAudioEngine): AudioEngine
}