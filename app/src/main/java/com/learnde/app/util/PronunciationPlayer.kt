package com.learnde.app.util

import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.audiofx.LoudnessEnhancer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Проигрывает короткие mp3 по URL. Новый вызов останавливает предыдущее воспроизведение. */
@Singleton
class PronunciationPlayer @Inject constructor(
    private val logger: AppLogger,
) {
    private var player: MediaPlayer? = null
    private var enhancer: LoudnessEnhancer? = null

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    /**
     * @param url        HTTPS-ссылка на mp3 из Forvo (http автоматически заменяется в P4).
     * @param boostPercent 0–100 % → 0–3000 мБ цифрового усиления через LoudnessEnhancer.
     */
    fun play(url: String, boostPercent: Int = 0) {
        stop()
        runCatching {
            player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        // MEDIA: громкий поток по умолчанию, идёт на динамик/наушники.
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                setOnPreparedListener { mp ->
                    if (boostPercent > 0) runCatching {
                        enhancer = LoudnessEnhancer(mp.audioSessionId).apply {
                            setTargetGain(boostPercent.coerceIn(0, 100) * 30) // мБ
                            enabled = true
                        }
                    }
                    _isPlaying.value = true
                    mp.start()
                }
                setOnCompletionListener { stop() }
                setOnErrorListener { _, what, extra ->
                    logger.w("PronunciationPlayer error what=$what extra=$extra"); stop(); true
                }
                setDataSource(url)
                prepareAsync()
            }
        }.onFailure {
            logger.w("PronunciationPlayer: не удалось воспроизвести: ${it.message}")
            stop()
        }
    }

    fun stop() {
        _isPlaying.value = false
        runCatching { enhancer?.release() }; enhancer = null
        player?.let {
            runCatching { if (it.isPlaying) it.stop() }
            runCatching { it.release() }
        }
        player = null
    }
}