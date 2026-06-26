package com.learnde.app.domain

import kotlinx.coroutines.flow.Flow

interface AudioEngine {

    val micOutput: Flow<ByteArray>
    val isCapturing: Boolean
    val isPlaying: Boolean
    val playbackAudibleUntilMs: Long

    suspend fun startCapture()
    suspend fun stopCapture()
    suspend fun enqueuePlayback(pcmData: ByteArray)
    suspend fun flushPlayback()
    suspend fun onTurnComplete()
    suspend fun initPlayback()
    suspend fun releaseAll()

    fun updateJitterConfig(preBufferChunks: Int, timeoutMs: Long, queueCapacity: Int)

    val playbackSync: Flow<ByteArray>

    fun setPlaybackVolume(gain: Float)

    fun setMicGain(gain: Float)

    fun setSpeakerRouting(forceSpeaker: Boolean)

    fun setAecEnabled(enabled: Boolean)
}