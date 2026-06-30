package com.learnde.app.util

import android.media.AudioAttributes
import android.media.MediaPlayer
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Проигрывает короткие mp3 произношений по URL. Один экземпляр на приложение:
 * новый тап останавливает предыдущее воспроизведение.
 */
@Singleton
class PronunciationPlayer @Inject constructor(
    private val logger: AppLogger,
) {
    private var player: MediaPlayer? = null

    fun play(url: String) {
        stop()
        runCatching {
            player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                setOnPreparedListener { it.start() }
                setOnCompletionListener { stop() }
                setOnErrorListener { _, what, extra ->
                    logger.w("PronunciationPlayer error what=$what extra=$extra"); stop(); true
                }
                setDataSource(url)
                prepareAsync()
            }
        }.onFailure { logger.w("PronunciationPlayer: не удалось воспроизвести: ${it.message}") }
    }

    fun stop() {
        player?.let {
            runCatching { if (it.isPlaying) it.stop() }
            runCatching { it.release() }
        }
        player = null
    }
}