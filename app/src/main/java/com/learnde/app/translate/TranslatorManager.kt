package com.learnde.app.translate

import androidx.datastore.core.DataStore
import com.learnde.app.data.settings.AppSettings
import com.learnde.app.domain.AudioEngine
import com.learnde.app.util.AppLogger
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min
import kotlin.math.sqrt

enum class TranslatorStatus { Idle, Connecting, Listening, Translating, Error }
enum class Direction { A_TO_B, B_TO_A }

/** Завершённая реплика в ленте переводчика. */
data class TranslationSegment(
    val fromCode: String,
    val toCode: String,
    val source: String,
    val translation: String,
    val timeMs: Long = System.currentTimeMillis(),
)

data class TranslatorUiState(
    val status: TranslatorStatus = TranslatorStatus.Idle,
    val langA: String = "ru",
    val langB: String = "de",
    val history: List<TranslationSegment> = emptyList(),
    val liveSource: String = "",
    val liveTranslation: String = "",
    val direction: Direction? = null,
    val reconnecting: Boolean = false,
    val error: String? = null,
)

@Singleton
class TranslatorManager @Inject constructor(
    private val audioEngine: AudioEngine,
    private val settingsStore: DataStore<AppSettings>,
    private val logger: AppLogger,
) {
    companion object {
        private const val VIS_GAIN = 3.5f
        private const val AMP_DECAY = 0.82f
        private const val AMP_TICK_MS = 60L
        private const val PLAYBACK_GUARD_MS = 200L
        private const val MAX_SEGMENTS = 60
        private const val RECONNECT_ATTEMPTS = 5
        private const val RECONNECT_BASE_MS = 1_500L
        private const val RECONNECT_MAX_MS = 15_000L
    }

    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default +
            kotlinx.coroutines.CoroutineExceptionHandler { _, e ->
                logger.e("TranslatorManager uncaught: ${e.message}", e)
            }
    )

    private val _state = MutableStateFlow(TranslatorUiState())
    val state: StateFlow<TranslatorUiState> = _state.asStateFlow()

    private val _amplitude = MutableStateFlow(0f)
    val amplitude: StateFlow<Float> = _amplitude.asStateFlow()

    private val lifecycleMutex = Mutex()
    @Volatile private var running = false

    private var apiKey: String = ""
    private var langA: String = "ru"
    private var langB: String = "de"

    private var clientToB: LiveTranslateClient? = null
    private var clientToA: LiveTranslateClient? = null

    private var micJob: Job? = null
    private var decayJob: Job? = null
    private val collectJobs = mutableListOf<Job>()

    @Volatile private var srcToB = ""; @Volatile private var outToB = ""
    @Volatile private var srcToA = ""; @Volatile private var outToA = ""

    fun start(bidirectional: Boolean = true) {
        scope.launch {
            lifecycleMutex.withLock {
                if (running) return@withLock

                val s = settingsStore.data.first()
                apiKey = s.apiKey
                langA = s.translatorLangA.ifBlank { "ru" }
                langB = s.translatorLangB.ifBlank { "de" }

                if (apiKey.isBlank()) {
                    _state.value = TranslatorUiState(
                        status = TranslatorStatus.Error,
                        langA = langA, langB = langB,
                        error = "API ключ не задан в настройках",
                    )
                    return@withLock
                }

                running = true
                srcToB = ""; outToB = ""; srcToA = ""; outToA = ""
                _state.value = TranslatorUiState(
                    status = TranslatorStatus.Connecting, langA = langA, langB = langB
                )

                audioEngine.setAecEnabled(true)
                audioEngine.setFullDuplexMode(true)
                audioEngine.setSpeakerRouting(true)
                audioEngine.resetPlaybackClock()
                runCatching { audioEngine.initPlayback() }

                spawnClient(target = langB) { clientToB = it }                     // A → B
                if (bidirectional) spawnClient(target = langA) { clientToA = it }  // B → A

                startMic()
                startAmplitudeLoop()
            }
        }
    }

    fun stop() {
        scope.launch {
            lifecycleMutex.withLock {
                if (!running) return@withLock
                running = false
                micJob?.cancel(); micJob = null
                decayJob?.cancel(); decayJob = null
                collectJobs.forEach { it.cancel() }; collectJobs.clear()
                runCatching { audioEngine.stopCapture() }
                runCatching { audioEngine.releaseAll() }
                clientToB?.disconnect(); clientToB = null
                clientToA?.disconnect(); clientToA = null
                _amplitude.value = 0f
                _state.value = TranslatorUiState(langA = langA, langB = langB)
            }
        }
    }

    private fun spawnClient(target: String, assign: (LiveTranslateClient) -> Unit) {
        val client = LiveTranslateClient(target, echoTargetLanguage = false, logger = logger)
        assign(client)
        collectJobs += scope.launch { collectClient(client) }
        client.connect(apiKey)
    }

    private suspend fun startMic() {
        val ok = runCatching { audioEngine.startCapture() }.isSuccess
        if (!ok || !audioEngine.isCapturing) {
            _state.update {
                it.copy(status = TranslatorStatus.Error,
                    error = "Микрофон занят. Закройте другие приложения и повторите.")
            }
            return
        }
        micJob = scope.launch {
            audioEngine.micOutput.collect { chunk ->
                val now = System.currentTimeMillis()
                val playing = now <= audioEngine.playbackAudibleUntilMs + PLAYBACK_GUARD_MS
                if (!playing) {
                    clientToB?.sendAudio(chunk)
                    clientToA?.sendAudio(chunk)
                    pushLevel(visLevel(rms(chunk)))
                }
            }
        }
    }

    private suspend fun collectClient(client: LiveTranslateClient) {
        val toB = client.target == langB
        val from = if (toB) langA else langB
        val to = client.target
        var attempts = 0

        client.events.collect { ev ->
            when (ev) {
                is TranslateEvent.Ready -> {
                    attempts = 0
                    _state.update {
                        it.copy(
                            status = if (it.status == TranslatorStatus.Connecting)
                                TranslatorStatus.Listening else it.status,
                            reconnecting = false,
                            error = null,
                        )
                    }
                }

                is TranslateEvent.InputText -> {
                    if (toB) srcToB += ev.text else srcToA += ev.text
                }

                is TranslateEvent.OutputText -> {
                    val src: String; val out: String
                    if (toB) { outToB += ev.text; src = srcToB; out = outToB }
                    else     { outToA += ev.text; src = srcToA; out = outToA }
                    _state.update {
                        it.copy(
                            status = TranslatorStatus.Translating,
                            direction = if (toB) Direction.A_TO_B else Direction.B_TO_A,
                            liveSource = src.trim(),
                            liveTranslation = out.trim(),
                            error = null,
                        )
                    }
                }

                is TranslateEvent.Audio -> {
                    runCatching { audioEngine.enqueuePlayback(ev.pcm) }
                    pushLevel(visLevel(rms(ev.pcm)))
                }

                is TranslateEvent.TurnComplete -> {
                    val src = (if (toB) srcToB else srcToA).trim()
                    val out = (if (toB) outToB else outToA).trim()
                    if (toB) { srcToB = ""; outToB = "" } else { srcToA = ""; outToA = "" }
                    if (out.isNotEmpty()) {
                        _state.update {
                            it.copy(
                                history = (it.history + TranslationSegment(from, to, src, out))
                                    .takeLast(MAX_SEGMENTS),
                                liveSource = "",
                                liveTranslation = "",
                            )
                        }
                    }
                }

                is TranslateEvent.Closed -> {
                    if (running) {
                        // Фатальные ошибки (неверный ключ, лимиты, кривой конфиг) — падаем сразу
                        if (ev.code == 1007 || ev.code == 1008 || ev.code == 4003) {
                            _state.update {
                                it.copy(
                                    status = TranslatorStatus.Error,
                                    reconnecting = false,
                                    error = "Ошибка конфигурации или ключа (${ev.code}).",
                                )
                            }
                        } else {
                            // Штатные разрывы (1000, 1001) и сетевые сбои — переподключаемся
                            attempts++
                            if (attempts <= RECONNECT_ATTEMPTS) {
                                _state.update { it.copy(reconnecting = true) }
                                val backoff = min(
                                    RECONNECT_BASE_MS * (1L shl (attempts - 1)),
                                    RECONNECT_MAX_MS
                                )
                                logger.w("Translate[$to]: reconnect #$attempts in ${backoff}ms (code=${ev.code})")
                                delay(backoff)
                                if (running) client.connect(apiKey)
                            } else {
                                _state.update {
                                    it.copy(
                                        status = TranslatorStatus.Error,
                                        reconnecting = false,
                                        error = "Соединение потеряно (${ev.code}). Нажмите кнопку, чтобы переподключиться.",
                                    )
                                }
                            }
                        }
                    }
                }

                is TranslateEvent.Error -> Unit // детали придут в Closed
            }
        }
    }

    private fun startAmplitudeLoop() {
        decayJob = scope.launch {
            while (running) {
                delay(AMP_TICK_MS)
                val cur = _amplitude.value
                if (cur > 0.001f) _amplitude.value = cur * AMP_DECAY
                else if (cur != 0f) _amplitude.value = 0f

                val now = System.currentTimeMillis()
                if (_state.value.status == TranslatorStatus.Translating &&
                    now > audioEngine.playbackAudibleUntilMs + 300L
                ) {
                    _state.update {
                        if (it.status == TranslatorStatus.Translating)
                            it.copy(status = TranslatorStatus.Listening) else it
                    }
                }
            }
        }
    }

    private fun pushLevel(level: Float) {
        val v = level.coerceIn(0f, 1f)
        if (v > _amplitude.value) _amplitude.value = v
    }

    private fun visLevel(r: Float): Float = (r * VIS_GAIN).coerceIn(0f, 1f)

    private fun rms(pcm: ByteArray): Float {
        if (pcm.size < 2) return 0f
        var sum = 0.0
        var n = 0
        var i = 0
        while (i + 1 < pcm.size) {
            val sample = (pcm[i].toInt() and 0xFF) or (pcm[i + 1].toInt() shl 8)
            sum += (sample * sample).toDouble()
            n++
            i += 2
        }
        if (n == 0) return 0f
        return (sqrt(sum / n) / 32768.0).toFloat().coerceIn(0f, 1f)
    }
}