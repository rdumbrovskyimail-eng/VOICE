package com.learnde.app.domain

import com.learnde.app.domain.model.ConversationMessage
import com.learnde.app.domain.model.GeminiEvent
import com.learnde.app.domain.model.SessionConfig
import kotlinx.coroutines.flow.Flow

interface LiveClient {

    val events: Flow<GeminiEvent>
    val sessionHandle: String?

    val isReady: Boolean

    suspend fun connect(apiKey: String, config: SessionConfig, logRaw: Boolean = false)

    fun sendAudio(pcmData: ByteArray)

    fun sendRealtimeText(text: String)

    fun sendVideoFrame(jpegBytes: ByteArray)

    fun sendClientTurn(text: String, jpegImages: List<ByteArray>, turnComplete: Boolean = true)

    fun sendAudioStreamEnd()

    fun sendToolResponse(responses: List<ToolResponse>)

    fun restoreContext(history: List<ConversationMessage>)

    suspend fun disconnect()
}

data class ToolResponse(
    val name: String,
    val id: String,
    val result: String
)