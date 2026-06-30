// Путь: app/src/main/java/com/learnde/app/data/AndroidAudioEngine.kt
//
// ★ ГЛАВНОЕ ИСПРАВЛЕНИЕ ТИХОГО ЗВУКА ★
// Было: AudioTrack с USAGE_VOICE_COMMUNICATION → воспроизведение уходило в поток
//       телефонного разговора (STREAM_VOICE_CALL) с тихой "звонковой" громкостью.
// Стало: USAGE_MEDIA + CONTENT_TYPE_SPEECH → воспроизведение идёт через медиа-поток
//       (STREAM_MUSIC), который по умолчанию ГРОМКИЙ и идёт на динамик/наушники.
// Микрофон по-прежнему использует VOICE_COMMUNICATION (для аппаратного AEC), а эхо
// гасится half-duplex логикой во ViewModel/SessionManager (мик не шлётся, пока говорит AI).

package com.learnde.app.data

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.os.Build
import com.learnde.app.domain.AudioEngine
import dagger.hilt.android.qualifiers.ApplicationContext
import com.learnde.app.domain.model.SessionConfig
import javax.inject.Inject
import com.learnde.app.util.AppLogger
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.yield
import java.nio.ByteBuffer
import java.nio.ByteOrder

class AndroidAudioEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val logger: AppLogger
) : AudioEngine {

    // ═══ CONFIG ═══
    @Volatile private var playbackQueueCapacity = 256
    @Volatile private var jitterPreBufferChunks = 3
    @Volatile private var jitterTimeoutMs = 150L

    @Volatile private var playbackGain: Float = 1.0f   // было 0.9f → 1.0f (без аттенюации)
    @Volatile private var micGain: Float = 1.4f
    @Volatile private var forceSpeakerOutput: Boolean = true
    @Volatile private var aecEnabled: Boolean = true   // управляется настройкой useAec

    @Volatile private var fullDuplexMode: Boolean = false
    @Volatile private var usingCommRoute: Boolean = false
    @Volatile private var savedVoiceCallVolume: Int = -1

    private val audioManager: AudioManager?
        get() = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    // ═══ FLOWS ═══
    private val _micOutput = MutableSharedFlow<ByteArray>(
        replay = 0, extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    override val micOutput: Flow<ByteArray> = _micOutput.asSharedFlow()

    private val _playbackSync = MutableSharedFlow<ByteArray>(
        replay = 0, extraBufferCapacity = 128,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    override val playbackSync: Flow<ByteArray> = _playbackSync.asSharedFlow()

    @Volatile override var isCapturing: Boolean = false; private set
    @Volatile override var isPlaying: Boolean = false; private set

    // ═══ STATE ═══
    private var engineScope: CoroutineScope = newEngineScope()
    private var captureJob: Job? = null
    private var playbackJob: Job? = null

    @Volatile private var audioRecord: AudioRecord? = null
    @Volatile private var echoCanceler: AcousticEchoCanceler? = null
    @Volatile private var noiseSuppressor: NoiseSuppressor? = null
    @Volatile private var audioTrack: AudioTrack? = null
    private val trackLock = Any()
    private val captureLifecycleMutex = Mutex()

    private var playbackChannel: Channel<ByteArray> =
        Channel(Channel.UNLIMITED)

    @Volatile private var isFirstBatch = true
    @Volatile private var awaitingDrain = false
    @Volatile private var playbackLoopGen = 0
    @Volatile private var estimatedPlaybackEndMs = 0L

    @Volatile private var audibleUntilMs: Long = 0L
    override val playbackAudibleUntilMs: Long get() = audibleUntilMs

    override fun resetPlaybackClock() {
        audibleUntilMs = 0L
        estimatedPlaybackEndMs = 0L
        isFirstBatch = true
    }

    private fun newEngineScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, e -> logger.e("engineScope uncaught: ${e.message}", e) })

    override fun updateJitterConfig(preBufferChunks: Int, timeoutMs: Long, queueCapacity: Int) {
        jitterPreBufferChunks = preBufferChunks.coerceIn(1, 10)
        jitterTimeoutMs = timeoutMs.coerceIn(50L, 500L)
        playbackQueueCapacity = queueCapacity.coerceIn(64, 512)
        logger.d("Jitter config: preBuffer=$jitterPreBufferChunks, timeout=${jitterTimeoutMs}ms, queue=$playbackQueueCapacity")
    }

    override fun setPlaybackVolume(gain: Float) {
        playbackGain = gain.coerceIn(0f, 1f)
        runCatching { audioTrack?.setVolume(playbackGain) }
    }

    override fun setMicGain(gain: Float) {
        micGain = gain.coerceIn(0.5f, 1.5f)
    }

    override fun setSpeakerRouting(forceSpeaker: Boolean) {
        forceSpeakerOutput = forceSpeaker
    }

    override fun setAecEnabled(enabled: Boolean) {
        aecEnabled = enabled
    }

    override fun setFullDuplexMode(enabled: Boolean) { fullDuplexMode = enabled }

    override val echoCancellationActive: Boolean get() = usingCommRoute

    private fun isHeadsetConnected(): Boolean {
        val am = audioManager ?: return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return false
        return am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any { d ->
            d.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
            d.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
            d.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
            d.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
            d.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                d.type == AudioDeviceInfo.TYPE_BLE_HEADSET)
        }
    }

    private fun enterCommunicationRoute() {
        val am = audioManager ?: return
        runCatching { am.mode = AudioManager.MODE_IN_COMMUNICATION }
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                am.availableCommunicationDevices
                    .firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                    ?.let { am.setCommunicationDevice(it) }
            } else {
                @Suppress("DEPRECATION") am.isSpeakerphoneOn = true
            }
        }
        runCatching {
            if (savedVoiceCallVolume < 0)
                savedVoiceCallVolume = am.getStreamVolume(AudioManager.STREAM_VOICE_CALL)
            val max = am.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL)
            am.setStreamVolume(AudioManager.STREAM_VOICE_CALL, max, 0)
        }
        logger.d("Audio route: COMMUNICATION + speaker (AEC reference active)")
    }

    private fun restoreAudioRoute() {
        val am = audioManager ?: return
        usingCommRoute = false
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) am.clearCommunicationDevice()
            else { @Suppress("DEPRECATION") am.isSpeakerphoneOn = false }
        }
        if (savedVoiceCallVolume >= 0) {
            runCatching { am.setStreamVolume(AudioManager.STREAM_VOICE_CALL, savedVoiceCallVolume, 0) }
            savedVoiceCallVolume = -1
        }
        runCatching { am.mode = AudioManager.MODE_NORMAL }
    }

    @Suppress("MissingPermission")
    override suspend fun startCapture() = captureLifecycleMutex.withLock {
        if (isCapturing) {
            logger.d("startCapture skipped — already capturing")
            return@withLock
        }
        if (!engineScope.isActive) engineScope = newEngineScope()

        val sampleRate = SessionConfig.INPUT_SAMPLE_RATE
        val minBuf = AudioRecord.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuf == AudioRecord.ERROR || minBuf == AudioRecord.ERROR_BAD_VALUE) {
            logger.e("AudioRecord.getMinBufferSize failed: $minBuf")
            return
        }

        val recorder = try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                AudioRecord.Builder()
                    .setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(minBuf * 2)
                    .build()
            } else {
                @Suppress("DEPRECATION")
                AudioRecord(
                    MediaRecorder.AudioSource.VOICE_COMMUNICATION, sampleRate,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, minBuf * 2
                )
            }
        } catch (e: SecurityException) {
            logger.e("SECURITY on AudioRecord ctor: ${e.message}")
            return
        } catch (e: Exception) {
            logger.e("AudioRecord ctor failed: ${e.message}", e)
            return
        }

        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            logger.e("AudioRecord init failed")
            runCatching { recorder.release() }
            return
        }

        if (aecEnabled && AcousticEchoCanceler.isAvailable()) {
            runCatching {
                echoCanceler = AcousticEchoCanceler.create(recorder.audioSessionId)?.apply {
                    enabled = true
                }
                logger.d("AEC: enabled=${echoCanceler?.enabled}")
            }.onFailure { logger.e("AEC init error: ${it.message}") }
        }

        if (NoiseSuppressor.isAvailable()) {
            runCatching {
                noiseSuppressor = NoiseSuppressor.create(recorder.audioSessionId)?.apply {
                    enabled = true
                }
                logger.d("NS: enabled=${noiseSuppressor?.enabled}")
            }.onFailure { logger.e("NoiseSuppressor init error: ${it.message}") }
        }

        try {
            recorder.startRecording()
        } catch (e: Exception) {
            logger.e("startRecording failed: ${e.message}", e)
            runCatching { recorder.release() }
            return
        }

        audioRecord = recorder
        isCapturing = true
        logger.d("Recording started (rate=$sampleRate, minBuf=$minBuf)")

        captureJob = engineScope.launch {
            val buffer = ShortArray(minBuf)
            val byteBuffer = ByteBuffer.allocate(minBuf * 2).order(ByteOrder.LITTLE_ENDIAN)
            val rawBytes = byteBuffer.array()

            try {
                val shortBuffer = byteBuffer.asShortBuffer()
                while (isActive && isCapturing) {
                    val read = kotlinx.coroutines.runInterruptible { recorder.read(buffer, 0, buffer.size) }
                    when {
                        read > 0 -> {
                            for (i in 0 until read) {
                                val amplified = (buffer[i] * micGain).toInt()
                                buffer[i] = when {
                                    amplified > Short.MAX_VALUE -> Short.MAX_VALUE
                                    amplified < Short.MIN_VALUE -> Short.MIN_VALUE
                                    else -> amplified.toShort()
                                }
                            }

                            byteBuffer.clear()
                            shortBuffer.clear()
                            shortBuffer.put(buffer, 0, read)
                            _micOutput.tryEmit(rawBytes.copyOf(read * 2))
                        }
                        read == 0 -> {
                            yield()
                        }
                        else -> {
                            logger.e("AudioRecord.read returned $read — exiting loop")
                            isCapturing = false
                            break
                        }
                    }
                }
            } catch (e: Exception) {
                logger.e("CAPTURE LOOP ERROR: ${e.message}", e)
            } finally {
                logger.d("Capture loop exited")
            }
        }
    }

    override suspend fun stopCapture() = captureLifecycleMutex.withLock {
        if (!isCapturing && audioRecord == null) return@withLock
        isCapturing = false

        val rec = audioRecord
        val aec = echoCanceler

        runCatching { rec?.stop() }

        runCatching {
            withTimeoutOrNull(800L) { captureJob?.cancelAndJoin() }
        }
        captureJob = null

        val ns = noiseSuppressor
        withContext(Dispatchers.IO) {
            runCatching { aec?.enabled = false }
            runCatching { aec?.release() }
            echoCanceler = null
            runCatching { ns?.enabled = false }
            runCatching { ns?.release() }
            noiseSuppressor = null
            runCatching { rec?.release() }
            audioRecord = null
        }
        logger.d("Capture stopped")
    }

    override suspend fun initPlayback() {
        if (isPlaying) {
            logger.d("initPlayback skipped — already playing")
            return
        }
        if (!engineScope.isActive) engineScope = newEngineScope()
        if (playbackChannel.isClosedForSend) {
            playbackChannel = Channel(Channel.UNLIMITED)
        }

        val sampleRate = SessionConfig.OUTPUT_SAMPLE_RATE
        val minBuf = AudioTrack.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuf == AudioTrack.ERROR || minBuf == AudioTrack.ERROR_BAD_VALUE) {
            logger.e("Device does not support ${sampleRate}Hz!")
            return
        }

        val useCommRoute = fullDuplexMode && !isHeadsetConnected()
        usingCommRoute = useCommRoute

        val track = try {
            val attrs = if (useCommRoute) {
                enterCommunicationRoute()
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
            } else {
                runCatching { audioManager?.mode = AudioManager.MODE_NORMAL }
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
            }
            AudioTrack.Builder()
                .setAudioAttributes(attrs)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build()
                )
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setBufferSizeInBytes(minBuf * 2).build()
        } catch (e: Exception) {
            logger.e("AudioTrack build failed: ${e.message}", e)
            restoreAudioRoute()
            return
        }

        audioTrack = track
        runCatching { track.setVolume(playbackGain) }
        track.play()
        isPlaying = true
        logger.d("Speaker ready (rate=$sampleRate, commRoute=$useCommRoute, gain=$playbackGain)")
        val myGen = ++playbackLoopGen
        playbackJob = engineScope.launch {
            try {
                for (chunk in playbackChannel) {
                    if (!isActive || myGen != playbackLoopGen) break
                    if (isFirstBatch) {
                        val preBuffer = mutableListOf(chunk)
                        repeat(jitterPreBufferChunks - 1) {
                            try {
                                val next = withTimeoutOrNull(jitterTimeoutMs) {
                                    playbackChannel.receive()
                                }
                                if (next != null) preBuffer.add(next)
                            } catch (_: ClosedReceiveChannelException) { return@repeat }
                            catch (_: Exception) { return@repeat }
                        }
                        for (buffered in preBuffer) {
                            _playbackSync.tryEmit(buffered)
                            runCatching { track.write(buffered, 0, buffered.size) }
                        }
                        isFirstBatch = false
                    } else {
                        _playbackSync.tryEmit(chunk)
                        runCatching { track.write(chunk, 0, chunk.size) }
                    }
                    if (awaitingDrain && playbackChannel.isEmpty) {
                        awaitingDrain = false
                        isFirstBatch = true
                    }
                }
            } catch (e: Exception) {
                logger.e("PLAYBACK LOOP ERROR: ${e.message}", e)
            } finally {
                logger.d("Playback loop exited")
            }
        }
    }

    override suspend fun enqueuePlayback(pcmData: ByteArray) {
        if (pcmData.isEmpty()) return

        val durationMs = pcmData.size / 48L
        val now = System.currentTimeMillis()
        val preBufferLeadMs = if (isFirstBatch) jitterPreBufferChunks * jitterTimeoutMs else 0L
        audibleUntilMs = maxOf(audibleUntilMs, now + preBufferLeadMs) + durationMs

        val durationLegacyMs = (pcmData.size / 2) * 1000L / SessionConfig.OUTPUT_SAMPLE_RATE
        estimatedPlaybackEndMs = maxOf(estimatedPlaybackEndMs, now) + durationLegacyMs

        val result = playbackChannel.trySend(pcmData)
        if (result.isFailure) logger.w("enqueuePlayback: channel closed, chunk dropped")
        awaitingDrain = false
    }

    override suspend fun flushPlayback() {
        while (playbackChannel.tryReceive().isSuccess) { /* drain */ }
        isFirstBatch = true
        awaitingDrain = false
        estimatedPlaybackEndMs = 0L
        audibleUntilMs = 0L
        synchronized(trackLock) {
            audioTrack?.apply {
                runCatching { 
                    if (state == AudioTrack.STATE_INITIALIZED) {
                        pause(); flush(); play()
                    }
                }
            }
        }
    }

    override suspend fun onTurnComplete() {
        awaitingDrain = true
        val padMs = 120
        val silence = ByteArray((SessionConfig.OUTPUT_SAMPLE_RATE * 2 * padMs) / 1000)
        playbackChannel.trySend(silence)
    }

    override suspend fun releaseAll() {
        playbackLoopGen++
        stopCapture()
        isPlaying = false
        estimatedPlaybackEndMs = 0L
        audibleUntilMs = 0L
        runCatching { playbackChannel.close() }
        val pJob = playbackJob
        playbackJob = null
        runCatching {
            withTimeoutOrNull(800L) { pJob?.cancelAndJoin() }
        }
        synchronized(trackLock) {
            audioTrack?.let {
                runCatching { it.pause(); it.flush(); it.stop(); it.release() }
            }
            audioTrack = null
        }
        runCatching {
            withTimeoutOrNull(800L) { engineScope.coroutineContext[Job]?.cancelAndJoin() }
        }
        restoreAudioRoute()
        logger.d("Engine released")
    }
}
