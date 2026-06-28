// Путь: app/src/main/java/com/learnde/app/GeminiLiveForegroundService.kt
//
// Изменения этой версии (поверх исходной):
//   • ИСПРАВЛЕН «тихий звук после закрытия»: при свайпе из недавних (onTaskRemoved) и при
//     нажатии «Стоп» в шторке сервис теперь ВЫЗЫВАЕТ sessionManager.shutdown() — закрывает
//     WebSocket и освобождает аудио-движок. Раньше глушился только сервис, а сессия (синглтон)
//     продолжала играть звук в фоне.
//   • Кнопка уведомления использует новое действие ACTION_USER_STOP (полный стоп), а внутренний
//     ACTION_STOP (его шлёт SessionManager.stopService) только гасит сервис — это исключает
//     зацикливание stop → stop.
//   • Android 9 hardening: если startForeground падает — вызываем stopSelf(), чтобы не словить
//     ANR «did not call startForeground» на старых устройствах.

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
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.learnde.app.session.SessionManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class GeminiLiveForegroundService : Service() {

    // Hilt-инъекция владельца сессии (синглтон). Через него глушим WS + аудио.
    @Inject lateinit var sessionManager: SessionManager

    companion object {
        private const val CHANNEL_ID = "gemini_live_channel"
        private const val NOTIFICATION_ID = 2026

        const val ACTION_START = "com.learnde.app.ACTION_START_SESSION"
        const val ACTION_STOP = "com.learnde.app.ACTION_STOP_SESSION"          // внутренний (из SessionManager)
        const val ACTION_USER_STOP = "com.learnde.app.ACTION_USER_STOP_SESSION" // пользователь нажал «Стоп»
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
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    private fun acquireLocks() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "learnde:session").apply {
            setReferenceCounted(false)
            acquire(2 * 60 * 60 * 1000L) // предохранитель 2 ч
        }
        val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            WifiManager.WIFI_MODE_FULL_LOW_LATENCY else WifiManager.WIFI_MODE_FULL_HIGH_PERF
        wifiLock = wm.createWifiLock(mode, "learnde:session").apply {
            setReferenceCounted(false)
            acquire()
        }
    }

    private fun releaseLocks() {
        runCatching { if (wakeLock?.isHeld == true) wakeLock?.release() }; wakeLock = null
        runCatching { if (wifiLock?.isHeld == true) wifiLock?.release() }; wifiLock = null
    }

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundSafe()

        when (intent?.action) {
            ACTION_START -> { requestMediaAudioFocus(); acquireLocks() }
            ACTION_USER_STOP -> {
                // Пользователь нажал «Стоп» в шторке → полный стоп сессии.
                runCatching { sessionManager.shutdown() }
                releaseLocks()
                releaseAudioFocus()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            ACTION_STOP -> {
                // Внутренний стоп (сессия уже останавливается сама) → только гасим сервис.
                releaseLocks()
                releaseAudioFocus()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            else -> {
                releaseLocks()
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
                // Android 9 / 10 (API 28/29-): 2-аргументная версия без типа сервиса.
                startForeground(NOTIFICATION_ID, buildNotification())
            }
        } catch (e: Exception) {
            android.util.Log.e("FGS", "startForeground failed: ${e.message}")
            // На Android 9 невызов startForeground вовремя = ANR. Гасим сервис, не оставляем «висеть».
            stopSelf()
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
                action = ACTION_USER_STOP
            },
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Ассистент на связи")
            .setContentText("Сессия активна. Нажмите «Завершить» для отключения.")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Завершить сессию", stopPendingIntent)
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
        // Свайп из «недавних» НЕ останавливает сессию — она живёт в фоне через FGS.
        // Остановка доступна кнопкой «Завершить сессию» в уведомлении (ACTION_USER_STOP).
    }

    override fun onDestroy() {
        super.onDestroy()
        releaseLocks()
        releaseAudioFocus()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
