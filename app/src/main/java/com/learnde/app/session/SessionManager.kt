// Путь: app/src/main/java/com/learnde/app/session/SessionManager.kt
//
// НОВЫЙ ФАЙЛ. Центральный владелец голосовой сессии. Решает сразу несколько проблем:
//   1. Рассинхрон LiveClient: и SessionManager, и ConnectionOrchestrator используют ОДИН и тот же
//      @LearnScope-инстанс LiveClient → события гарантированно доходят (раньше ViewModel слушал
//      один инстанс, а orchestrator управлял другим).
//   2. Подключение ВСЕХ настроек: buildConfig() мапит каждое релевантное поле AppSettings в
//      SessionConfig, а аудио-настройки (громкость/усиление мика/jitter/маршрутизация) —
//      применяются к AudioEngine. Раньше подключались только 7 полей.
//   3. Foreground service: стартует ДО захвата мика и останавливается на stop() (раньше не
//      запускался вообще).
//   4. Амплитуда для анимации (RMS из PCM) и транскрипт для чата — единый источник для UI.
//   5. Промпт сессии (то, что пользователь вводит на главном экране) вшивается в системную
//      инструкцию; applyPrompt пересоздаёт сессию с новым промптом.

package com.learnde.app.session

import android.content.Context
import android.os.Build
import androidx.datastore.core.DataStore
import com.learnde.app.GeminiLiveForegroundService
import com.learnde.app.data.settings.AppSettings
import com.learnde.app.domain.AudioEngine
import com.learnde.app.domain.ConnectionOrchestrator
import com.learnde.app.domain.LinkState
import com.learnde.app.domain.LiveClient
import com.learnde.app.domain.model.ConversationMessage
import com.learnde.app.domain.model.GeminiEvent
import com.learnde.app.domain.model.LatencyProfile
import com.learnde.app.domain.model.SessionConfig
import com.learnde.app.learn.core.LearnScope
import com.learnde.app.util.AppLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

@Singleton
class SessionManager @Inject constructor(
    @ApplicationContext private val context: Context,
    @LearnScope private val liveClient: LiveClient,
    private val audioEngine: AudioEngine,
    private val orchestrator: ConnectionOrchestrator,
    private val settingsStore: DataStore<AppSettings>,
    private val logger: AppLogger,
) {

    data class State(
        val link: LinkState = LinkState.IDLE,
        val isConnected: Boolean = false,
        val isConnecting: Boolean = false,
        val isMicActive: Boolean = false,
        val isAiSpeaking: Boolean = false,
        val activePrompt: String = "",
        val dashboardText: String = "",
        val transcript: List<ConversationMessage> = emptyList(),
        val error: String? = null,
    )

    companion object {
        private const val MAX_MSGS = 80
        private const val AMP_DECAY = 0.82f       // плавное затухание орба
        private const val AMP_TICK_MS = 60L
        private const val VIS_GAIN = 3.5f         // речь тихая по RMS → усиливаем для визуализации
    }

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private val _amplitude = MutableStateFlow(0f)
    val amplitude: StateFlow<Float> = _amplitude.asStateFlow()

    @Volatile private var micJob: Job? = null
    @Volatile private var streamingRole: String? = null
    @Volatile private var activePrompt: String = ""
    @Volatile private var sendStreamEndOnStop: Boolean = true

    init {
        wireOrchestratorCallbacks()
        observeLinkState()
        observeEvents()
        observePlaybackAmplitude()
        startAmplitudeDecay()
    }

    // ───────────────────────── Публичный API ─────────────────────────

    /** Применить промпт со страниц учебника. Если сессия активна — пересоздаёт её с новым заданием. */
    fun applyPrompt(prompt: String) {
        appScope.launch {
            activePrompt = prompt
            _state.update { it.copy(activePrompt = prompt) }
            val s = _state.value
            if (s.isConnected || s.isConnecting) {
                stopInternal()
                startInternal(prompt)
            }
        }
    }

    fun toggleConnection() {
        appScope.launch {
            val s = _state.value
            if (s.isConnected || s.isConnecting) stopInternal()
            else startInternal(activePrompt)
        }
    }

    /** Отправить текстовое сообщение — модель «прочитает» его как SMS и ответит голосом. */
    fun sendText(text: String) {
        val t = text.trim()
        if (t.isEmpty() || !liveClient.isReady) return
        streamingRole = null
        addFinalMessage(ConversationMessage.user(t))
        liveClient.sendRealtimeText(t)
        orchestrator.onUserTurnEnded()
    }

    fun toggleMic() {
        if (_state.value.isMicActive) stopMic() else startMic()
    }

    fun shutdown() {
        appScope.launch { stopInternal() }
    }

    // ───────────────────────── Жизненный цикл ─────────────────────────

    private suspend fun startInternal(prompt: String) {
        val settings = settingsStore.data.first()
        if (settings.apiKey.isBlank()) {
            _state.update { it.copy(error = "API ключ не задан в настройках", isConnecting = false) }
            return
        }

        activePrompt = prompt
        streamingRole = null
        _state.update {
            it.copy(
                isConnecting = true,
                error = null,
                activePrompt = prompt,
                transcript = emptyList(),
            )
        }

        // Применяем аудио-настройки к движку (раньше игнорировались).
        audioEngine.setPlaybackVolume(settings.playbackVolume / 100f)
        audioEngine.setMicGain(settings.micGain / 100f)
        audioEngine.setSpeakerRouting(settings.forceSpeakerOutput)
        audioEngine.setAecEnabled(settings.useAec)
        audioEngine.updateJitterConfig(
            settings.jitterPreBufferChunks,
            settings.jitterTimeoutMs,
            settings.playbackQueueCapacity,
        )
        sendStreamEndOnStop = settings.sendAudioStreamEnd

        audioEngine.initPlayback()
        startService()

        val config = buildConfig(settings, prompt)
        runCatching {
            orchestrator.start(
                scope = appScope,
                apiKey = settings.apiKey,
                config = config,
                maxAttempts = settings.maxReconnectAttempts,
                baseDelayMs = settings.reconnectBaseDelayMs,
                maxDelayMs = settings.reconnectMaxDelayMs,
                logRaw = settings.logRawWebSocketFrames,
            )
        }.onFailure {
            logger.e("startInternal failed: ${it.message}", it)
            _state.update { st -> st.copy(error = it.message, isConnecting = false) }
        }
    }

    private suspend fun stopInternal() {
        stopMicBlocking()
        runCatching { orchestrator.stop() }
        runCatching { audioEngine.releaseAll() }
        stopService()
        _amplitude.value = 0f
        _state.update {
            it.copy(
                isConnected = false,
                isConnecting = false,
                isMicActive = false,
                isAiSpeaking = false,
            )
        }
    }

    // ───────────────────────── Конфигурация ─────────────────────────

    private fun buildConfig(settings: AppSettings, prompt: String): SessionConfig {
        val profile = runCatching { enumValueOf<LatencyProfile>(settings.latencyProfile) }
            .getOrDefault(LatencyProfile.Low)

        // Если задан явный таймаут тишины (>0) — он переопределяет рекомендованный.
        val silenceMs = (if (settings.vadSilenceTimeoutMs > 0) settings.vadSilenceTimeoutMs
            else settings.vadSilenceDurationMs).coerceAtLeast(500)

        return SessionConfig(
            model = settings.model,
            responseModality = settings.responseModality,
            temperature = settings.temperature,
            topP = settings.topP,
            topK = settings.topK,
            maxOutputTokens = settings.maxOutputTokens,
            voiceId = settings.voiceId,
            latencyProfile = profile,
            thinkingIncludeThoughts = settings.thinkingIncludeThoughts,
            mediaResolution = settings.mediaResolution,
            autoActivityDetection = settings.enableServerVad,
            vadStartSensitivity = settings.vadStartSensitivity,
            vadEndSensitivity = settings.vadEndSensitivity,
            vadPrefixPaddingMs = settings.vadPrefixPaddingMs,
            vadSilenceDurationMs = silenceMs,
            activityHandling = settings.activityHandling,
            systemInstruction = buildSystemInstruction(settings.systemInstruction, prompt),
            inputTranscription = settings.inputTranscription,
            outputTranscription = settings.outputTranscription,
            enableSessionResumption = settings.enableSessionResumption,
            enableContextCompression = settings.enableContextCompression,
            compressionTriggerTokens = settings.compressionTriggerTokens,
            compressionTargetTokens = settings.compressionTargetTokens,
            enableGoogleSearch = settings.enableGoogleSearch,
            sendAudioStreamEnd = settings.sendAudioStreamEnd,
        )
    }

    private fun buildSystemInstruction(base: String, prompt: String): String =
        if (prompt.isBlank()) base
        else base + "\n\n[Промпт для текущей сессии]:\n" + prompt

    // ───────────────────────── События / orchestrator ─────────────────────────

    private fun wireOrchestratorCallbacks() {
        // Мик стартует/останавливается ТОЛЬКО через эти колбэки (onResumeAudio фаерится на
        // SetupComplete и после успешной ротации) — чтобы не дублировать запуск.
        orchestrator.onPauseAudio = { stopMic() }
        orchestrator.onResumeAudio = { startMic() }
        orchestrator.onPermanentFailure = { msg ->
            _state.update { it.copy(error = msg, isConnecting = false, isConnected = false) }
        }
    }

    private fun observeLinkState() {
        appScope.launch {
            orchestrator.state.collect { link ->
                _state.update {
                    it.copy(
                        link = link,
                        isConnected = link == LinkState.READY,
                        isConnecting = link == LinkState.CONNECTING ||
                                link == LinkState.RECOVERING ||
                                link == LinkState.ROTATING,
                    )
                }
            }
        }
    }

    private fun observeEvents() {
        appScope.launch {
            liveClient.events.collect { event ->
                orchestrator.onEvent(event)
                when (event) {
                    is GeminiEvent.ConnectionError ->
                        _state.update { it.copy(error = event.message) }

                    is GeminiEvent.AudioChunk -> {
                        orchestrator.onModelActivity()
                        _state.update { it.copy(isAiSpeaking = true) }
                        audioEngine.enqueuePlayback(event.pcmData)
                    }

                    is GeminiEvent.TurnComplete -> {
                        streamingRole = null
                        _state.update { it.copy(isAiSpeaking = false) }
                        audioEngine.onTurnComplete()
                    }

                    is GeminiEvent.Interrupted -> {
                        streamingRole = null
                        _state.update { it.copy(isAiSpeaking = false) }
                        audioEngine.flushPlayback()
                    }

                    is GeminiEvent.InputTranscript ->
                        appendTranscript(ConversationMessage.ROLE_USER, event.text)

                    is GeminiEvent.OutputTranscript -> {
                        orchestrator.onModelActivity()
                        appendTranscript(ConversationMessage.ROLE_MODEL, event.text)
                    }

                    else -> Unit
                }
            }
        }
    }

    // ───────────────────────── Транскрипт ─────────────────────────

    private fun appendTranscript(role: String, text: String) {
        _state.update { st ->
            val list = st.transcript.toMutableList()
            if (streamingRole == role && list.isNotEmpty() && list.last().role == role) {
                val last = list.last()
                list[list.size - 1] = last.copy(text = last.text + text)
            } else {
                list.add(ConversationMessage(role, text))
                streamingRole = role
            }
            st.copy(transcript = list.takeLast(MAX_MSGS))
        }
    }

    private fun addFinalMessage(msg: ConversationMessage) {
        _state.update { st -> st.copy(transcript = (st.transcript + msg).takeLast(MAX_MSGS)) }
    }

    // ───────────────────────── Микрофон ─────────────────────────

    private fun startMic() {
        if (_state.value.isMicActive) return
        micJob = appScope.launch {
            val started = runCatching { audioEngine.startCapture() }.isSuccess
            if (!started || !audioEngine.isCapturing) {
                logger.e("startCapture failed — mic not activated")
                return@launch
            }
            _state.update { it.copy(isMicActive = true) }
            audioEngine.micOutput.collect { chunk ->
                if (System.currentTimeMillis() > audioEngine.playbackAudibleUntilMs + 400L) {
                    liveClient.sendAudio(chunk)
                    if (!_state.value.isAiSpeaking) pushLevel(visLevel(rms(chunk)))
                }
            }
        }
    }

    private fun stopMic() {
        micJob?.cancel()
        micJob = null
        appScope.launch {
            audioEngine.stopCapture()
            if (sendStreamEndOnStop && liveClient.isReady) {
                liveClient.sendAudioStreamEnd()
                orchestrator.onUserTurnEnded()
            }
            _state.update { it.copy(isMicActive = false) }
        }
    }

    private suspend fun stopMicBlocking() {
        micJob?.cancel()
        micJob = null
        runCatching { audioEngine.stopCapture() }
        _state.update { it.copy(isMicActive = false) }
    }

    // ───────────────────────── Амплитуда (визуализатор) ─────────────────────────

    private fun observePlaybackAmplitude() {
        appScope.launch {
            audioEngine.playbackSync.collect { chunk ->
                pushLevel(visLevel(rms(chunk)))
            }
        }
    }

    private fun startAmplitudeDecay() {
        appScope.launch {
            while (true) {
                delay(AMP_TICK_MS)
                val cur = _amplitude.value
                if (cur > 0.001f) _amplitude.value = cur * AMP_DECAY else if (cur != 0f) _amplitude.value = 0f
            }
        }
    }

    private fun pushLevel(level: Float) {
        val v = level.coerceIn(0f, 1f)
        if (v > _amplitude.value) _amplitude.value = v
    }

    private fun visLevel(rms: Float): Float = (rms * VIS_GAIN).coerceIn(0f, 1f)

    /** RMS из PCM16 little-endian, нормализованный в 0..1. */
    private fun rms(bytes: ByteArray): Float {
        if (bytes.size < 2) return 0f
        var sum = 0.0
        var n = 0
        var i = 0
        while (i + 1 < bytes.size) {
            val sample = ((bytes[i].toInt() and 0xFF) or (bytes[i + 1].toInt() shl 8)).toShort().toInt()
            sum += (sample * sample).toDouble()
            n++
            i += 2
        }
        if (n == 0) return 0f
        return (sqrt(sum / n) / 32768.0).toFloat()
    }

    // ───────────────────────── Foreground service ─────────────────────────

    private fun startService() {
        runCatching {
            val intent = GeminiLiveForegroundService.startIntent(context, forceSpeaker = true)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }.onFailure { logger.e("startService failed: ${it.message}") }
    }

    private fun stopService() {
        runCatching { context.startService(GeminiLiveForegroundService.stopIntent(context)) }
    }
}
