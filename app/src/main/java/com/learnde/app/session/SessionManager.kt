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
import com.learnde.app.domain.model.PronunciationItem
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
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
    private val attachmentProcessor: com.learnde.app.attach.AttachmentProcessor,
    private val logger: AppLogger,
    private val toolRegistry: com.learnde.app.domain.ToolRegistry,
    private val forvoRepository: com.learnde.app.data.forvo.ForvoRepository,
    private val pronunciationPlayer: com.learnde.app.util.PronunciationPlayer,
) {

    data class State(
        val link: LinkState = LinkState.IDLE,
        val isConnected: Boolean = false,
        val isConnecting: Boolean = false,
        val isRecovering: Boolean = false,
        val isMicActive: Boolean = false,
        val isAiSpeaking: Boolean = false,
        val activePrompt: String = "",
        val dashboardText: String = "",
        val transcript: List<ConversationMessage> = emptyList(),
        val error: String? = null,
        val mode: ClientMode = ClientMode.NORMAL,
        val cameraOn: Boolean = false,
        val totalTokens: Int = 0,
        val searchUsed: Boolean = false,
        val pronunciations: List<PronunciationItem> = emptyList(),
    )

    companion object {
        const val TYPED_PREFIX = "[Пользователь написал это текстом ⌨]"
        const val CAM_SYSTEM_PROMPT = "Ты видишь живую камеру пользователя в реальном времени (кадры идут потоком). Помогай с тем, что в кадре: коротко описывай, читай и переводи надписи, отвечай на вопросы о происходящем. Будь лаконичен. Если кадр не виден — скажи об этом."
        private const val MAX_MSGS = 80
        private const val AMP_DECAY = 0.82f       // плавное затухание орба
        private const val AMP_TICK_MS = 60L
        private const val VIS_GAIN = 3.5f         // речь тихая по RMS → усиливаем для визуализации
    }

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default + kotlinx.coroutines.CoroutineExceptionHandler { _, e -> logger.e("appScope uncaught (проглочено, без краша): ${e.message}", e) })

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private val _amplitude = MutableStateFlow(0f)
    val amplitude: StateFlow<Float> = _amplitude.asStateFlow()

    @Volatile private var micJob: Job? = null
    @Volatile private var streamingRole: String? = null
    @Volatile private var activePrompt: String = ""
    @Volatile private var pendingPromptAttachments: List<android.net.Uri> = emptyList()
    @Volatile private var sendStreamEndOnStop: Boolean = true

    @Volatile private var bargeInEnabled: Boolean = false
    @Volatile private var aiTurnStartedAt: Long = 0L
    private val aecConvergeMs: Long = 150L
    private val connectionMutex = Mutex()

    init {
        wireOrchestratorCallbacks()
        observeLinkState()
        observeEvents()
        observePlaybackAmplitude()
        startAmplitudeDecay()
    }

    // ───────────────────────── Публичный API ─────────────────────────

    /** Применить промпт со страниц учебника. Если сессия активна — пересоздаёт её с новым заданием. */
    fun applyPrompt(prompt: String, attachments: List<android.net.Uri> = emptyList()) {
        appScope.launch {
            activePrompt = prompt
            pendingPromptAttachments = attachments
            _state.update { it.copy(activePrompt = prompt) }
            val s = _state.value
            if (s.isConnected || s.isConnecting) { stopInternal(); startInternal(prompt) }
            else if (attachments.isNotEmpty()) startInternal(prompt) // поднимаемся, чтобы засеять файлы
        }
    }

    fun toggleConnection() {
        appScope.launch {
            connectionMutex.withLock {
                val s = _state.value
                if (s.isConnected || s.isConnecting) stopInternal()
                else startInternal(activePrompt)
            }
        }
    }

    // ───────────────────────── Режим History (H) ─────────────────────────

    /** Переключить режим. NORMAL = чистый инкогнито; HISTORY = накопительная история. */
    fun setMode(newMode: ClientMode) {
        appScope.launch {
            if (_state.value.mode == newMode) return@launch
            _state.update { it.copy(mode = newMode) }
            val s = _state.value
            if (s.isConnected || s.isConnecting) { stopInternal(); startInternal(activePrompt) }
        }
    }

    // ───────────────────────── Камера ─────────────────────────

    /** Включить/выключить камеру в NORMAL/HISTORY. Перезапуск сессии не нужен — кадры идут поверх. */
    fun setCameraOn(on: Boolean) { _state.update { it.copy(cameraOn = on) } }

    /** Переключить режим CAM (без привязки к промпту, камера всегда включена). */
    fun toggleCamMode() {
        val next = if (_state.value.mode == ClientMode.CAM) ClientMode.NORMAL else ClientMode.CAM
        setMode(next)
    }

    /** Отправить JPEG-кадр камеры (источник троттлит до 1 FPS). */
    fun sendCameraFrame(jpeg: ByteArray) {
        if (!liveClient.isReady) return
        if (_state.value.mode != ClientMode.CAM && !_state.value.cameraOn) return
        runCatching { liveClient.sendVideoFrame(jpeg) }
    }

    /** Отправить текстовое сообщение — модель «прочитает» его как SMS и ответит голосом. */
    fun sendText(text: String, attachments: List<android.net.Uri> = emptyList()) {
        val t = text.trim()
        if ((t.isEmpty() && attachments.isEmpty()) || !liveClient.isReady) return
        streamingRole = null
        appScope.launch {
            if (attachments.isEmpty()) {
                addFinalMessage(ConversationMessage.user(t))            // в чате — чистый текст
                liveClient.sendRealtimeText("$TYPED_PREFIX $t")         // модель видит метку
                orchestrator.onUserTurnEnded()
                return@launch
            }
            val r = attachmentProcessor.process(attachments)
            if (r.images.isEmpty() && r.extractedText.isEmpty() && t.isEmpty()) {
                _state.update { it.copy(error = "Не удалось прочитать: " + r.skipped.joinToString(", ")) }
                return@launch
            }
            val composed = buildString {
                append(TYPED_PREFIX).append(" ")
                if (t.isNotEmpty()) append(t)
                if (r.extractedText.isNotEmpty()) { append("\n"); append(r.extractedText) }
                if (length <= TYPED_PREFIX.length + 1) append("Посмотри на прикреплённые файлы и помоги.")
            }
            liveClient.sendClientTurn(composed, r.images, turnComplete = true)
            addFinalMessage(
                ConversationMessage.user(t.ifEmpty { "📎 Вложения" })
                    .copy(
                        attachmentNote = warnNote(r),
                        attachmentUris = attachments.map { it.toString() }
                    )
            )
            orchestrator.onUserTurnEnded()
        }
    }

    private fun warnNote(r: com.learnde.app.attach.AttachmentProcessor.Result): String? =
        if (r.skipped.isEmpty()) null
        else "⚠ Модель не понимает: " + r.skipped.joinToString(", ")

    private fun flushPromptAttachments() {
        val uris = pendingPromptAttachments
        if (uris.isEmpty()) return
        pendingPromptAttachments = emptyList()  // сеем один раз, не на каждый реконнект
        appScope.launch {
            val r = attachmentProcessor.process(uris)
            if (r.images.isEmpty() && r.extractedText.isEmpty()) {
                _state.update { it.copy(error = "Файлы промпта не прочитаны: " + r.skipped.joinToString(", ")) }
                return@launch
            }
            val framing = buildString {
                append("Это материалы для текущей сессии. Изучи их и кратко подтверди, что ознакомился.")
                if (r.extractedText.isNotEmpty()) { append("\n"); append(r.extractedText) }
            }
            liveClient.sendClientTurn(framing, r.images, turnComplete = true)
            addFinalMessage(
                ConversationMessage.user("📎 Материалы сессии")
                    .copy(
                        attachmentNote = warnNote(r),
                        attachmentUris = uris.map { it.toString() }
                    )
            )
        }
    }

    fun toggleMic() {
        if (_state.value.isMicActive) stopMic() else startMic()
    }

    /** Скрыть/очистить табло (крестик в карточке дашборда). */
    fun clearDashboard() {
        _state.update { it.copy(dashboardText = "") }
    }

    /** Тап по чипу произношения — проиграть mp3 (Forvo). */
    fun playPronunciation(item: PronunciationItem) {
        val url = item.audioUrl ?: return
        pronunciationPlayer.play(url)
    }

    /** Скрыть ряд произношения. */
    fun clearPronunciations() {
        pronunciationPlayer.stop()
        _state.update { it.copy(pronunciations = emptyList()) }
    }

    // Мгновенно показываем слова, аудио тянем параллельно и обновляем чипы по готовности.
    private fun handleShowPronunciations(rawWords: String?) {
        val words = parseWordsArg(rawWords)
        _state.update { it.copy(pronunciations = words.map { w -> PronunciationItem(w) }) }
        words.forEach { w ->
            appScope.launch {
                val url = forvoRepository.standardPronunciationUrl(w)
                _state.update { st ->
                    st.copy(pronunciations = st.pronunciations.map { item ->
                        if (item.word == w) item.copy(
                            audioUrl = url,
                            status = if (url != null) PronunciationItem.Status.Ready
                                     else PronunciationItem.Status.Error
                        ) else item
                    })
                }
            }
        }
    }

    // args["words"] приходит как JSON-строка: ["der Hund","die Katze"]
    private fun parseWordsArg(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            Json.parseToJsonElement(raw).jsonArray.map { it.jsonPrimitive.content }
        }.getOrElse {
            raw.split(",").map { it.trim() }
        }.map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(20)
    }

    /** Сбросить ошибку после показа в Snackbar. */
    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    fun retry() = appScope.launch { orchestrator.retryAfterFailure() }

    fun shutdown() {
        appScope.launch { stopInternal() }
    }

    /** Полная СИНХРОННАЯ остановка сессии ассистента (мик + аудио-движок + foreground-сервис).
     *  Нужна переводчику: он работает на том же общем AudioEngine. */
    suspend fun shutdownBlocking() = connectionMutex.withLock { stopInternal() }

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
                totalTokens = 0,
                searchUsed = false,
            )
        }

        // Применяем аудио-настройки к движку (раньше игнорировались).
        audioEngine.setPlaybackVolume(settings.playbackVolume / 100f)
        audioEngine.setMicGain(settings.micGain / 100f)
        audioEngine.setSpeakerRouting(settings.forceSpeakerOutput)
        audioEngine.setAecEnabled(settings.useAec || settings.bargeInEnabled) // для barge-in AEC обязателен
        audioEngine.updateJitterConfig(
            settings.jitterPreBufferChunks,
            settings.jitterTimeoutMs,
            settings.playbackQueueCapacity,
        )
        sendStreamEndOnStop = settings.sendAudioStreamEnd
        bargeInEnabled = settings.bargeInEnabled
        audioEngine.setFullDuplexMode(settings.bargeInEnabled)

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
                transcript = emptyList(),
                pronunciations = emptyList()
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
            activityHandling = if (settings.bargeInEnabled) "START_OF_ACTIVITY_INTERRUPTS"
                               else "NO_INTERRUPTION",
            systemInstruction = when (_state.value.mode) {
                ClientMode.CAM -> CAM_SYSTEM_PROMPT
                else -> buildSystemInstruction(settings.systemInstruction, prompt)
            },
            inputTranscription = settings.inputTranscription,
            outputTranscription = settings.outputTranscription,
            enableSessionResumption = settings.enableSessionResumption,
            enableContextCompression = settings.enableContextCompression,
            compressionTriggerTokens = settings.compressionTriggerTokens,
            compressionTargetTokens = settings.compressionTargetTokens,
            enableGoogleSearch = settings.enableGoogleSearch,
            functionDeclarations = toolRegistry.getDeclarations(),
            sendAudioStreamEnd = settings.sendAudioStreamEnd,
            seedHistoryInClientContent = false,
        )
    }

    private fun buildSystemInstruction(base: String, prompt: String): String {
        val modalityHint = "\n\nКАНАЛ ВВОДА: реплики, начинающиеся с «$TYPED_PREFIX», пользователь НАПЕЧАТАЛ вручную (как SMS); всё остальное он произнёс ГОЛОСОМ. Никогда не произноси и не повторяй сам тег «$TYPED_PREFIX» — это служебная пометка. На печатный ввод уместно отвечать чуть короче."
        
        // НОВЫЙ ЖЕСТКИЙ PROMPT ДЛЯ ИНСТРУМЕНТОВ
        val toolHint = "\n\nУ тебя есть доступ к инструментам. Если ты объясняешь сложное понятие, переводишь фразу, диктуешь номер или формулу — ОБЯЗАТЕЛЬНО используй функцию update_dashboard для вывода информации на экран. Если в выводимом тексте есть немецкие слова — ТЫ ОБЯЗАН заполнить массив german_words. Это критически важно для работы аудио-плеера пользователя!"

        return if (prompt.isBlank()) base + modalityHint + toolHint
        else base + modalityHint + toolHint + "\n\n[Промпт для текущей сессии]:\n" + prompt
    }

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
                _state.update { it.copy(
                    link = link,
                    isConnected = link == LinkState.READY,
                    // Первичный коннект — только CONNECTING. RECOVERING/ROTATING — это уже
                    // восстановление, его стоит показывать иначе (см. UI ниже), а не как "Подключение…".
                    isConnecting = link == LinkState.CONNECTING,
                    isRecovering = link == LinkState.RECOVERING || link == LinkState.ROTATING,
                ) }
                if (link == LinkState.READY) {
                    if (liveClient.sessionHandle == null) {
                        appScope.launch {
                            flushPromptAttachments()
                        }
                    }
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
                        if (!_state.value.isAiSpeaking) aiTurnStartedAt = System.currentTimeMillis()
                        orchestrator.onModelActivity()
                        _state.update { it.copy(isAiSpeaking = true) }
                        audioEngine.enqueuePlayback(event.pcmData)
                    }

                    is GeminiEvent.TurnComplete -> {
                        aiTurnStartedAt = 0L
                        streamingRole = null
                        _state.update { it.copy(isAiSpeaking = false) }
                        audioEngine.onTurnComplete()
                    }

                    is GeminiEvent.Interrupted -> {
                        aiTurnStartedAt = 0L
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

                    is GeminiEvent.UsageMetadata -> _state.update { it.copy(totalTokens = event.totalTokens) }

                    is GeminiEvent.GroundingMetadata -> _state.update { it.copy(searchUsed = true) }

                    is GeminiEvent.ToolCall -> {
                        orchestrator.onModelActivity()
                        appScope.launch {
                            val responses = event.functionCalls.map { call ->
                                val result = toolRegistry.execute(call.name, call.args)
                                
                                if (call.name == "update_dashboard") {
                                    // 1. Выводим текст на дашборд
                                    val text = call.args["text"] ?: ""
                                    _state.update { it.copy(dashboardText = text) }
                                    
                                    // 2. Автоматически ищем немецкие слова и показываем кнопки
                                    val germanWords = call.args["german_words"]
                                    if (!germanWords.isNullOrBlank() && germanWords != "[]") {
                                        handleShowPronunciations(germanWords)
                                    } else {
                                        // Если слов нет, очищаем старые кнопки, чтобы не путать пользователя
                                        clearPronunciations()
                                    }
                                }
                                
                                com.learnde.app.domain.ToolResponse(
                                    name = call.name,
                                    id = call.id,
                                    result = result
                                )
                            }
                            liveClient.sendToolResponse(responses)
                        }
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
            runCatching { audioEngine.startCapture() }
            if (!audioEngine.isCapturing) { delay(250); runCatching { audioEngine.startCapture() } }
            if (!audioEngine.isCapturing) {
                _state.update { it.copy(error = "Не удалось включить микрофон. Закройте приложения, занимающие микрофон, и нажмите 🎤 снова.", isMicActive = false) }
                return@launch
            }
            _state.update { it.copy(isMicActive = true, error = null) }
            audioEngine.resetPlaybackClock()
            var wasAiPlaying = false
            
            audioEngine.micOutput.collect { chunk ->
                val now = System.currentTimeMillis()
                val isAiPlaying = now <= audioEngine.playbackAudibleUntilMs + 400L
                // Проверяем, играет ли сейчас плеер Forvo
                val isForvoPlaying = pronunciationPlayer.isPlaying.value

                when {
                    isForvoPlaying -> {
                        // ЖЕСТКИЙ МЬЮТ: Если играет Forvo, мы НЕ отправляем звук с микрофона Гемини.
                        if (!wasAiPlaying) {
                            liveClient.sendAudioStreamEnd()
                            wasAiPlaying = true
                        }
                    }
                    bargeInEnabled -> {
                        val needGuard = audioEngine.echoCancellationActive
                        val converging = needGuard && aiTurnStartedAt > 0L &&
                                         (now - aiTurnStartedAt) < aecConvergeMs
                        if (!converging) {
                            liveClient.sendAudio(chunk)
                            if (!_state.value.isAiSpeaking) pushLevel(visLevel(rms(chunk)))
                        }
                        wasAiPlaying = false
                    }
                    isAiPlaying -> {
                        if (!wasAiPlaying) {
                            liveClient.sendAudioStreamEnd()
                            wasAiPlaying = true
                        }
                    }
                    else -> {
                        wasAiPlaying = false
                        liveClient.sendAudio(chunk)
                        if (!_state.value.isAiSpeaking) pushLevel(visLevel(rms(chunk)))
                    }
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
