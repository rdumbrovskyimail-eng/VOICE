package com.learnde.app.util

import android.media.AudioAttributes
import android.media.MediaPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Проигрывает короткие mp3 по URL. Новый тап останавливает предыдущее воспроизведение. */
@Singleton
class PronunciationPlayer @Inject constructor(
    private val logger: AppLogger,
) {
    private var player: MediaPlayer? = null

    // Флаг состояния: играет ли сейчас Forvo
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    fun play(url: String) {
        stop()
        runCatching {
            player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        // ИСПОЛЬЗУЕМ КАНАЛ СВЯЗИ: звук не будет приглушаться Android-ом
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                setOnPreparedListener { 
                    _isPlaying.value = true
                    it.start() 
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
        player?.let {
            runCatching { if (it.isPlaying) it.stop() }
            runCatching { it.release() }
        }
        player = null
    }
}