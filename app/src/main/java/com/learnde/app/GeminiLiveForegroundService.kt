// Путь: app/src/main/java/com/learnde/app/GeminiLiveForegroundService.kt
//
// Изменения относительно исходной версии:
//   • Аудио-фокус запрашивается через AudioFocusRequest c USAGE_MEDIA (а не STREAM_VOICE_CALL),
//     согласованно с воспроизведением AudioTrack (которое теперь тоже USAGE_MEDIA).
//   • MODE_NORMAL (убран MODE_IN_COMMUNICATION) — он завышал маршрут в тихий звонковый поток.
//   • Убрана ручная маршрутизация setCommunicationDevice/Bluetooth SCO: для USAGE_MEDIA
//     система сама шлёт звук на динамик (или наушники, если подключены) на полной громкости.
//   • Сохранены startIntent(context, forceSpeaker) / stopIntent(context), нотификация,
//     FOREGROUND_SERVICE_TYPE_MICROPHONE|MEDIA_PLAYBACK — сервис стартует ДО захвата мика
//     (требование Android 12+ для микрофона из foreground).

package com.learnde.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class GeminiLiveForegroundService : Service() {

    companion object {
        private const val CHANNEL_ID = "gemini_live_channel"
        private const val NOTIFICATION_ID = 2026

        const val ACTION_START = "com.learnde.app.ACTION_START_SESSION"
        const val ACTION_STOP = "com.learnde.app.ACTION_STOP_SESSION"
        const val EXTRA_FORCE_SPEAKER = "extra_force_speaker"

        fun startIntent(context: Context, forceSpeaker: Boolean = true): Intent =
            Intent(context, GeminiLiveForegroundService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_FORCE_SPEAKER, forceSpeaker)
            }

        fun stopIntent(context: Context): Intent =
            Intent(context, GeminiLiveForegroundService::class.java).apply {
                action = ACTION_STOP
            }
    }

    private var audioManager: AudioManager? = null
    private var focusRequest: AudioFocusRequest? = null

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundSafe()

        when (intent?.action) {
            ACTION_START -> requestMediaAudioFocus()
            ACTION_STOP -> {
                releaseAudioFocus()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            else -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun startForegroundSafe() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    buildNotification(),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or
                            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                )
            } else {
                startForeground(NOTIFICATION_ID, buildNotification())
            }
        } catch (e: Exception) {
            android.util.Log.e("FGS", "startForeground failed: ${e.message}")
        }
    }

    // Фокус под МЕДИА-воспроизведение: громкий поток, режим NORMAL.
    private fun requestMediaAudioFocus() {
        val am = audioManager ?: return
        am.mode = AudioManager.MODE_NORMAL

        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()

        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(attrs)
            .setWillPauseWhenDucked(false)
            .setOnAudioFocusChangeListener { /* half-duplex: реакция не требуется */ }
            .build()

        focusRequest = request
        runCatching { am.requestAudioFocus(request) }
    }

    private fun releaseAudioFocus() {
        val am = audioManager ?: return
        focusRequest?.let { runCatching { am.abandonAudioFocusRequest(it) } }
        focusRequest = null
        am.mode = AudioManager.MODE_NORMAL
    }

    private fun buildNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopPendingIntent = PendingIntent.getService(
            this, 1,
            Intent(this, GeminiLiveForegroundService::class.java).apply {
                action = ACTION_STOP
            },
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Голосовой ассистент активен")
            .setContentText("Микрофон включён")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_media_pause, "Стоп", stopPendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun createNotificationChannel() {
        val nm = getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            "Голосовой ассистент",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Уведомление активной голосовой сессии"
            setShowBadge(false)
        }
        nm.createNotificationChannel(channel)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        releaseAudioFocus()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        releaseAudioFocus()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
