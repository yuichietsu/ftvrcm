package com.ftvrcm.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.view.KeyEvent
import androidx.core.app.NotificationCompat
import com.ftvrcm.R
import com.ftvrcm.data.SettingsStore
import com.ftvrcm.domain.OperationMode
import com.ftvrcm.receiver.MediaButtonReceiver

/**
 * Experimental global-ish toggle using MediaSession.
 * Many TV remotes send the Play/Pause button as a media key event.
 */
class MediaKeyToggleService : Service() {

    private var mediaSession: MediaSession? = null
    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var hasAudioFocus: Boolean = false

    private var registeredMediaButtonReceiver: Boolean = false

    private var lastPlayPauseDownAtMs: Long = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, buildNotification())

        audioManager = getSystemService(AudioManager::class.java)
        requestAudioFocus()
        registerMediaButtonReceiverFallback()

        mediaSession = MediaSession(this, TAG).apply {
            val receiverIntent = Intent(Intent.ACTION_MEDIA_BUTTON).apply {
                setClass(this@MediaKeyToggleService, MediaButtonReceiver::class.java)
            }
            val receiverPendingIntent = PendingIntent.getBroadcast(
                this@MediaKeyToggleService,
                0,
                receiverIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

            // Some builds deliver media keys only through this receiver route.
            setMediaButtonReceiver(receiverPendingIntent)
            SettingsStore(this@MediaKeyToggleService)
                .setDebugLastMediaEvent("MediaSession setMediaButtonReceiver: OK")

            setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS or MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS)
            setCallback(object : MediaSession.Callback() {
                override fun onMediaButtonEvent(mediaButtonIntent: Intent): Boolean {
                    val event = if (Build.VERSION.SDK_INT >= 33) {
                        mediaButtonIntent.getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        mediaButtonIntent.getParcelableExtra(Intent.EXTRA_KEY_EVENT) as? KeyEvent
                    }

                    if (event != null) {
                        // Do not rely on this path on Fire OS; keep diagnostics.
                        SettingsStore(this@MediaKeyToggleService)
                            .setDebugLastMediaEvent("MediaSession ${KeyEvent.keyCodeToString(event.keyCode)}")
                        handleKeyEvent(event)
                    }
                    return super.onMediaButtonEvent(mediaButtonIntent)
                }
            })

            // Keep session "eligible" for media keys.
            setPlaybackState(
                PlaybackState.Builder()
                    .setActions(
                        PlaybackState.ACTION_PLAY_PAUSE or
                            PlaybackState.ACTION_PLAY or
                            PlaybackState.ACTION_PAUSE,
                    )
                    .setState(PlaybackState.STATE_PLAYING, 0L, 1.0f, SystemClock.elapsedRealtime())
                    .build(),
            )

            isActive = true
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Keep running until explicitly stopped.
        return START_STICKY
    }

    override fun onDestroy() {
        try {
            mediaSession?.isActive = false
            mediaSession?.release()
        } catch (_: Exception) {
        }
        mediaSession = null

        unregisterMediaButtonReceiverFallback()
        abandonAudioFocus()

        super.onDestroy()
    }

    private fun handleKeyEvent(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) return false

        // Most Fire TV remotes report Play/Pause here.
        val isPlayPause = event.keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE ||
            event.keyCode == KeyEvent.KEYCODE_MEDIA_PLAY ||
            event.keyCode == KeyEvent.KEYCODE_MEDIA_PAUSE

        if (!isPlayPause) return false

        val now = SystemClock.elapsedRealtime()
        val delta = now - lastPlayPauseDownAtMs
        lastPlayPauseDownAtMs = now

        if (delta in 1..DOUBLE_PRESS_WINDOW_MS) {
            toggleMode()
            lastPlayPauseDownAtMs = 0L
            SettingsStore(this).setDebugLastMediaEvent("PlayPause double (${delta}ms) -> toggled")
            return false
        }

        SettingsStore(this).setDebugLastMediaEvent("PlayPause single (wait) delta=${if (delta <= 0) "-" else delta}")
        return false
    }

    private fun toggleMode() {
        val store = SettingsStore(this)
        val next = when (store.getOperationMode()) {
            OperationMode.NORMAL -> OperationMode.MOUSE
            OperationMode.MOUSE -> OperationMode.NORMAL
        }
        store.setOperationMode(next)
        store.setDebugLastMediaEvent("mode -> ${next.name}")
    }

    private fun registerMediaButtonReceiverFallback() {
        val am = audioManager ?: return
        try {
            @Suppress("DEPRECATION")
            am.registerMediaButtonEventReceiver(
                android.content.ComponentName(this, MediaButtonReceiver::class.java),
            )
            registeredMediaButtonReceiver = true
            SettingsStore(this).setDebugLastMediaEvent("AudioManager registerMediaButtonEventReceiver: OK")
        } catch (e: Exception) {
            SettingsStore(this).setDebugLastCrash("register_media_button_receiver", e)
            SettingsStore(this).setDebugLastMediaEvent("AudioManager registerMediaButtonEventReceiver: FAIL")
            registeredMediaButtonReceiver = false
        }
    }

    private fun unregisterMediaButtonReceiverFallback() {
        if (!registeredMediaButtonReceiver) return
        val am = audioManager ?: return
        try {
            @Suppress("DEPRECATION")
            am.unregisterMediaButtonEventReceiver(
                android.content.ComponentName(this, MediaButtonReceiver::class.java),
            )
        } catch (_: Exception) {
        }
        registeredMediaButtonReceiver = false
    }

    private fun requestAudioFocus() {
        val am = audioManager ?: return
        try {
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(attrs)
                .setOnAudioFocusChangeListener { /* no-op */ }
                .build()

            audioFocusRequest = request
            hasAudioFocus = am.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } catch (_: Exception) {
            // Best-effort: session may still receive some events.
            hasAudioFocus = false
        }
    }

    private fun abandonAudioFocus() {
        val am = audioManager
        val req = audioFocusRequest
        if (am != null && req != null) {
            try {
                am.abandonAudioFocusRequest(req)
            } catch (_: Exception) {
            }
        }
        audioFocusRequest = null
        hasAudioFocus = false
    }

    private fun buildNotification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "ftvrcm",
                NotificationManager.IMPORTANCE_LOW,
            )
            nm.createNotificationChannel(channel)
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_ftvrcm)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("メディアキー監視中（再生2度押しで切替）")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    companion object {
        private const val TAG = "MediaKeyToggleService"
        private const val CHANNEL_ID = "ftvrcm_media_toggle"
        private const val NOTIFICATION_ID = 1001

        private const val DOUBLE_PRESS_WINDOW_MS = 900L

        fun start(context: Context) {
            val intent = Intent(context, MediaKeyToggleService::class.java)
            if (Build.VERSION.SDK_INT >= 26) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, MediaKeyToggleService::class.java))
        }
    }
}
