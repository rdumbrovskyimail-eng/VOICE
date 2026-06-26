// Путь: app/src/main/java/com/learnde/app/data/settings/AppSettings.kt
//
// Изменения относительно исходной версии:
//   • voiceId по умолчанию → "Sulafat" (Chirp 3 HD, характер "Warm") — тёплый,
//     приятный естественный голос, хорош для универсального ассистента.
//   • playbackVolume → 100 (без аттенюации на уровне AudioTrack = максимально громко).
//   • micGain → 140 (совпадает с тюнингом движка, чувствительный захват тихой речи).
//   • Включено сжатие контекста по умолчанию: compressionTriggerTokens=25600,
//     compressionTargetTokens=8192 → снимает лимит сессии ~15 мин (см. справочник §16/§18).
//   • ДОБАВЛЕНЫ корректные строковые поля VAD (API ждёт enum-строки, а НЕ Float):
//       vadStartSensitivity / vadEndSensitivity / vadPrefixPaddingMs / vadSilenceDurationMs.
//     Старые Float-поля (vadStartOfSpeechSensitivity и т.п.) СОХРАНЕНЫ ради совместимости
//     с экраном настроек, но движок сессии теперь использует новые строковые поля.
//   • ДОБАВЛЕНЫ поля: activityHandling, mediaResolution, thinkingIncludeThoughts.
//
// Все эти настройки реально подключаются в SessionManager → SessionConfig / AudioEngine.

package com.learnde.app.data.settings

import kotlinx.serialization.Serializable

@Serializable
enum class ThemeMode { AUTO, LIGHT, DARK }

@Serializable
data class AppSettings(
    // ─────────── Профиль / обучение ───────────
    val userName: String = "",
    val learningGoals: String = "",
    val learningTopics: String = "",
    val a1DataImported: Boolean = false,
    val a1DataVersion: Int = 0,
    val testPassed: Boolean = false,

    // ─────────── Ключи / модель ───────────
    val apiKey: String = "",
    val tutorApiKey: String = "",
    val tutorModel: String = "gemini-3.1-flash-lite",
    val enableTutorHints: Boolean = true,
    val model: String = "models/gemini-3.1-flash-live-preview",

    // ─────────── Генерация ───────────
    val temperature: Float = 0.8f,
    val topP: Float = 0.95f,
    val topK: Int = 0,                 // 0 = не отправлять topK (валидно для Live API)
    val maxOutputTokens: Int = 8192,
    val responseModality: String = "AUDIO",

    // ─────────── Голос ───────────
    // Sulafat = "Warm" (тёплый, естественный). Приятный универсальный голос для ассистента.
    val voiceId: String = "Sulafat",

    // ─────────── Аудио-движок ───────────
    val useAec: Boolean = true,
    val jitterPreBufferChunks: Int = 3,
    val jitterTimeoutMs: Long = 150L,
    val playbackQueueCapacity: Int = 256,
    val sendAudioStreamEnd: Boolean = true,
    val playbackVolume: Int = 100,     // 0..100 → AudioTrack.setVolume(0..1). 100 = громко.
    val micGain: Int = 140,            // 0..150 → множитель 0.5..1.5 (коэрсится в движке).
    val forceSpeakerOutput: Boolean = true,

    // ─────────── Жизненный цикл сессии ───────────
    val enableSessionResumption: Boolean = true,
    val transparentResumption: Boolean = true,
    val enableContextCompression: Boolean = true,
    // Включено по умолчанию → снимает лимит ~15 минут (sliding window).
    val compressionTriggerTokens: Long = 25_600L,
    val compressionTargetTokens: Long = 8_192L,
    val maxReconnectAttempts: Int = 5,
    val reconnectBaseDelayMs: Long = 2_000L,
    val reconnectMaxDelayMs: Long = 30_000L,
    val sessionHeartbeatMs: Long = 0L,

    // ─────────── VAD (детекция речи) ───────────
    val enableServerVad: Boolean = true,
    // НОВЫЕ корректные строковые поля (API принимает именно enum-строки):
    //   START_SENSITIVITY_HIGH — легко ловит начало речи (включая первый слог).
    //   END_SENSITIVITY_LOW    — терпим к естественным паузам (ученик думает / делает вдох).
    val vadStartSensitivity: String = "START_SENSITIVITY_HIGH",
    val vadEndSensitivity: String = "END_SENSITIVITY_LOW",
    val vadPrefixPaddingMs: Int = 300,    // look-back, чтобы не обрезать начало слова.
    val vadSilenceDurationMs: Int = 800,  // рекомендованный сервером баланс качество/задержка.
    // СТАРЫЕ Float-поля — оставлены только для совместимости со старым UI настроек.
    // SessionManager их НЕ использует (они были источником бага: API ждёт строки).
    val vadStartOfSpeechSensitivity: Float = 0.5f,
    val vadEndOfSpeechSensitivity: Float = 0.5f,
    val vadSilenceTimeoutMs: Int = 0,     // если >0 — переопределяет vadSilenceDurationMs.
    val activityHandling: String = "", // Пустая строка включает дефолтное поведение (Barge-in разрешен)

    // ─────────── Транскрипция (нужна для чата) ───────────
    val inputTranscription: Boolean = true,
    val outputTranscription: Boolean = true,

    // ─────────── Инструменты / мышление ───────────
    val enableGoogleSearch: Boolean = false,
    val latencyProfile: String = "Low",   // light thinking — баланс точность/скорость.
    val thinkingIncludeThoughts: Boolean = false,
    val mediaResolution: String = "",      // для видео (на будущее), пусто = по умолчанию.

    // ─────────── Системная инструкция ───────────
    val systemInstruction: String = DEFAULT_SYSTEM_INSTRUCTION,

    // ─────────── UI / тема / чат ───────────
    val themeMode: ThemeMode = ThemeMode.AUTO,
    val chatFontScale: Float = 1.0f,
    val chatShowTimestamps: Boolean = false,
    val chatShowRoleLabels: Boolean = true,
    val chatAutoScroll: Boolean = true,
    val chatBackgroundAlpha: Int = 30,

    // ─────────── Отладка ───────────
    val showDebugLog: Boolean = false,
    val logRawWebSocketFrames: Boolean = false,
    val showUsageMetadata: Boolean = false
) {
    companion object {
        // Универсальный голосовой ассистент. Конкретную задачу (урок языка, помощь по
        // фото учебника и т.д.) пользователь задаёт сам через поле промпта на главном экране.
        const val DEFAULT_SYSTEM_INSTRUCTION =
            "Ты — полезный голосовой ассистент. Отвечай естественно, дружелюбно и по делу, " +
            "кратко и в хорошем темпе. Всё, что ты говоришь, произносится вслух, поэтому " +
            "избегай длинных списков и разметки. По умолчанию отвечай на русском языке, " +
            "если пользователь не попросит другой язык."
    }
}
