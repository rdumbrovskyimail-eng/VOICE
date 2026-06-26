package com.learnde.app.di

import com.learnde.app.util.AppLogger
import com.learnde.app.util.TimberAppLogger
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class LoggingModule {
    @Binds
    @Singleton
    abstract fun bindAppLogger(impl: TimberAppLogger): AppLogger
}