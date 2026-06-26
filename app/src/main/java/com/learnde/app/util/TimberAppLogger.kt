package com.learnde.app.util

import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TimberAppLogger @Inject constructor() : AppLogger {
    override fun d(message: String) = Timber.d(message)
    override fun i(message: String) = Timber.i(message)
    override fun w(message: String) = Timber.w(message)
    override fun e(message: String, throwable: Throwable?) {
        if (throwable != null) Timber.e(throwable, message) else Timber.e(message)
    }
}