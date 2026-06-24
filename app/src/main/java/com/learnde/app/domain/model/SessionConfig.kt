package com.learnde.app.domain.model

data class FunctionDeclarationConfig(
    val name: String,
    val description: String,
    val parameters: Map<String, ParameterConfig> = emptyMap(),
    val required: List<String> = emptyList()
)

data class ParameterConfig(
    val type: String = "STRING",
    val description: String = "",
    val enumValues: List<String> = emptyList(),
    val items: ParameterConfig? = null,
    val properties: Map<String, ParameterConfig> = emptyMap(),
    val required: List<String> = emptyList()
)

data class SessionConfig(
    val model: String = DEFAULT_MODEL,
    val responseModality: String = "AUDIO",
    val temperature: Float = 0.7f,
    val topP: Float = 0.95f,
    val topK: Int = 0,
    val maxOutputTokens: Int = 4096,
    val voiceId: String = "Aoede",
    val languageCode: String = "",
    val latencyProfile: LatencyProfile = LatencyProfile.UltraLow,
    val thinkingIncludeThoughts: Boolean = false,
    val mediaResolution: String = "",
    val autoActivityDetection: Boolean = true,
    val vadStartSensitivity: String = "START_SENSITIVITY_HIGH",
    val vadEndSensitivity: String = "END_SENSITIVITY_LOW",
    val vadPrefixPaddingMs: Int = 300,
    val vadSilenceDurationMs: Int = 900,
    val activityHandling: String = "NO_INTERRUPTION",
    val systemInstruction: String = DEFAULT_SYSTEM_INSTRUCTION,
    val inputTranscription: Boolean = true,
    val outputTranscription: Boolean = true,
    val enableSessionResumption: Boolean = true,
    val sessionHandle: String? = null,
    val enableContextCompression: Boolean = true,
    val compressionTriggerTokens: Long = 0L,
    val compressionTargetTokens: Long = 0L,
    val enableGoogleSearch: Boolean = false,
    val functionDeclarations: List<FunctionDeclarationConfig> = emptyList(),
    val sendAudioStreamEnd: Boolean = true,
    val setupTimeoutMs: Long = 10_000L
) {
    companion object {
        const val DEFAULT_MODEL = "gemini-3.1-flash-live-preview"
        const val DEFAULT_SYSTEM_INSTRUCTION = "Ты — полезный голосовой ассистент."
        const val INPUT_SAMPLE_RATE = 16_000
        const val OUTPUT_SAMPLE_RATE = 24_000
        const val WS_HOST = "generativelanguage.googleapis.com"
        const val WS_PATH = "ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent"
    }
}

enum class LatencyProfile(val thinkingLevel: String?, val displayName: String) {
    Off      (null,      "Off — мгновенный ответ"),
    UltraLow ("minimal", "Ultra Low — minimal thinking"),
    Low      ("low",     "Low — light thinking"),
    Balanced ("medium",  "Balanced — medium thinking"),
    Reasoning("high",    "Reasoning — deep thinking")
}