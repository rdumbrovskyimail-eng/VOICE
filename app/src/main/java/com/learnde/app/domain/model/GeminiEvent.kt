package com.learnde.app.domain.model

sealed class GeminiEvent {
    object Connected : GeminiEvent()
    object SetupComplete : GeminiEvent()
    
    data class ConnectionError(val message: String) : GeminiEvent()
    data class Disconnected(val code: Int, val reason: String) : GeminiEvent()
    
    // Управление сессией (Session Resumption & GoAway)
    data class SessionHandleUpdate(
        val handle: String, 
        val resumable: Boolean, 
        val lastConsumedIndex: Long?
    ) : GeminiEvent()
    data class GoAway(val timeLeft: String?) : GeminiEvent()
    
    // Транскрипция (Input/Output)
    data class InputTranscript(val text: String) : GeminiEvent()
    data class OutputTranscript(val text: String) : GeminiEvent()
    
    // Состояния генерации
    object Interrupted : GeminiEvent() // Barge-in (перебивание)
    object TurnComplete : GeminiEvent()
    object GenerationComplete : GeminiEvent()
    
    // Контент от модели
    data class ModelText(val text: String) : GeminiEvent()
    data class AudioChunk(val pcmData: ByteArray) : GeminiEvent()
    
    // Инструменты (Function Calling)
    data class ToolCall(val functionCalls: List<FunctionCall>) : GeminiEvent()
    data class ToolCallCancellation(val ids: List<String>) : GeminiEvent()
    
    // Метаданные
    data class GroundingMetadata(val json: String) : GeminiEvent()
    data class UsageMetadata(
        val promptTokens: Int, 
        val responseTokens: Int, 
        val totalTokens: Int
    ) : GeminiEvent()
}