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
import kotlin.math.sqrt

enum class TranslatorStatus { Idle, Connecting, Listening, Translating, Error }
enum class Direction { RU_TO_DE, DE_TO_RU }

data class TranslatorUiState(
    val status: TranslatorStatus = TranslatorStatus.Idle,
    val sourceText: String = "",
    val translationText: String = "",
    val direction: Direction? = null,
    val error: String? = null,
    val bidirectional: Boolean = true,
)

@Singleton
class TranslatorManager @Inject constructor(
    private val audioEngine: AudioEngine,
    private val settingsStore: DataStore<AppSettings>,
    private val logger: AppLogger,
) {
    companion object {
        const val LANG_DE = "de"
        const val LANG_RU = "ru"
        private const val VIS_GAIN = 3.5f
        private const val AMP_DECAY = 0.82f
        private const val AMP_TICK_MS = 60L
        private const val PLAYBACK_GUARD_MS = 200L
    }

    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default +
            kotlinx.coroutines.CoroutineExceptionHandler { _, e -> logger.e("TranslatorManager uncaught: ${e.message}", e) }
    )

    private val _state = MutableStateFlow(TranslatorUiState())
    val state: StateFlow<TranslatorUiState> = _state.asStateFlow()

    private val _amplitude = MutableStateFlow(0f)
    val amplitude: StateFlow<Float> = _amplitude.asStateFlow()

    private val lifecycleMutex = Mutex()
    @Volatile private var running = false

    private var clientDe: LiveTranslateClient? = null
    private var clientRu: LiveTranslateClient? = null

    private var micJob: Job? = null
    private var decayJob: Job? = null
    private val collectJobs = mutableListOf<Job>()

    @Volatile private var lastInputDe: String = ""
    @Volatile private var lastInputRu: String = ""

    fun start(bidirectional: Boolean = true) {
        scope.launch {
            lifecycleMutex.withLock {
                if (running) return@withLock

                val apiKey = settingsStore.data.first().apiKey
                if (apiKey.isBlank()) {
                    _state.value = TranslatorUiState(
                        status = TranslatorStatus.Error,
                        error = "API ключ не задан в настройках",
                        bidirectional = bidirectional
                    )
                    return@withLock
                }

                running = true
                lastInputDe = ""; lastInputRu = ""
                _state.value = TranslatorUiState(status = TranslatorStatus.Connecting, bidirectional = bidirectional)

                audioEngine.setAecEnabled(true)
                audioEngine.setFullDuplexMode(true)
                audioEngine.setSpeakerRouting(true)
                audioEngine.resetPlaybackClock()
                runCatching { audioEngine.initPlayback() }

                LiveTranslateClient(LANG_DE, echoTargetLanguage = false, logger = logger).also {
                    clientDe = it
                    collectJobs += scope.launch { collectClient(it) }
                    it.connect(apiKey)
                }

                if (bidirectional) {
                    LiveTranslateClient(LANG_RU, echoTargetLanguage = false, logger = logger).also {
                        clientRu = it
                        collectJobs += scope.launch { collectClient(it) }
                        it.connect(apiKey)
                    }
                }

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
                clientDe?.disconnect(); clientDe = null
                clientRu?.disconnect(); clientRu = null
                _amplitude.value = 0f
                _state.value = TranslatorUiState(status = TranslatorStatus.Idle)
            }
        }
    }

    private suspend fun startMic() {
        val ok = runCatching { audioEngine.startCapture() }.isSuccess
        if (!ok) {
            _state.update { it.copy(status = TranslatorStatus.Error, error = "Микрофон занят. Закройте другие приложения и повторите.") }
            return
        }
        micJob = scope.launch {
            audioEngine.micOutput.collect { chunk ->
                val now = System.currentTimeMillis()
                val playing = now <= audioEngine.playbackAudibleUntilMs + PLAYBACK_GUARD_MS
                if (!playing) {
                    clientDe?.sendAudio(chunk)
                    clientRu?.sendAudio(chunk)
                    pushLevel(visLevel(rms(chunk)))
                }
            }
        }
    }

    private suspend fun collectClient(client: LiveTranslateClient) {
        client.events.collect { ev ->
            when (ev) {
                is TranslateEvent.Ready -> {
                    if (_state.value.status == TranslatorStatus.Connecting) {
                        _state.update { it.copy(status = TranslatorStatus.Listening, error = null) }
                    }
                }
                is TranslateEvent.InputText -> {
                    if (client.target == LANG_DE) lastInputDe = ev.text else lastInputRu = ev.text
                }
                is TranslateEvent.OutputText -> {
                    val dir = if (client.target == LANG_DE) Direction.RU_TO_DE else Direction.DE_TO_RU
                    val src = if (client.target == LANG_DE) lastInputDe else lastInputRu
                    _state.update {
                        it.copy(
                            status = TranslatorStatus.Translating,
                            direction = dir,
                            sourceText = src,
                            translationText = ev.text,
                            error = null,
                        )
                    }
                }
                is TranslateEvent.Audio -> {
                    runCatching { audioEngine.enqueuePlayback(ev.pcm) }
                    pushLevel(visLevel(rms(ev.pcm)))
                }
                is TranslateEvent.Closed -> {
                    if (running && ev.code != 1000 && ev.code != 1001) {
                        _state.update {
                            it.copy(status = TranslatorStatus.Error,
                                error = "Соединение прервано (${ev.code}). Нажмите микрофон, чтобы переподключиться.")
                        }
                    }
                }
                is TranslateEvent.Error -> {
                    if (running) _state.update { it.copy(status = TranslatorStatus.Error, error = ev.message) }
                }
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
                    _state.update { if (it.status == TranslatorStatus.Translating) it.copy(status = TranslatorStatus.Listening) else it }
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