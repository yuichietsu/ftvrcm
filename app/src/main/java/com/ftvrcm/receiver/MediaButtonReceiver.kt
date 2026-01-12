package com.ftvrcm.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.view.KeyEvent
import com.ftvrcm.data.SettingsStore
import com.ftvrcm.domain.OperationMode

class MediaButtonReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MEDIA_BUTTON) return

        val event = if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT) as? KeyEvent
        } ?: return

        val store = SettingsStore(context)
        store.setDebugLastMediaEvent(
            "Receiver ${event.action} ${KeyEvent.keyCodeToString(event.keyCode)}",
        )

        if (event.action != KeyEvent.ACTION_DOWN) return

        if (!store.isMediaToggleEnabled()) {
            store.setDebugLastMediaEvent("Receiver ignore (disabled)")
            return
        }

        val isPlayPause = event.keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE ||
            event.keyCode == KeyEvent.KEYCODE_MEDIA_PLAY ||
            event.keyCode == KeyEvent.KEYCODE_MEDIA_PAUSE

        if (!isPlayPause) {
            store.setDebugLastMediaEvent("ignore ${KeyEvent.keyCodeToString(event.keyCode)}")
            return
        }

        val now = SystemClock.elapsedRealtime()
        val lastDown = store.getMediaLastPlayPauseDownAtElapsed()
        val delta = if (lastDown > 0L) now - lastDown else Long.MAX_VALUE

        val toggled = delta in 1..DOUBLE_PRESS_WINDOW_MS

        if (toggled) {
            store.setMediaLastPlayPauseDownAtElapsed(0L)
            val next = when (store.getOperationMode()) {
                OperationMode.NORMAL -> OperationMode.MOUSE
                OperationMode.MOUSE -> OperationMode.NORMAL
            }
            store.setOperationMode(next)
            store.setDebugLastMediaEvent("PlayPause double (${delta}ms) -> ${next.name}")
        } else {
            store.setMediaLastPlayPauseDownAtElapsed(now)
            store.setDebugLastMediaEvent("PlayPause single (wait) delta=${if (delta == Long.MAX_VALUE) "-" else delta}")
        }

        // Do not abort broadcast: two play/pause toggles usually cancel out in playback apps.
    }

    companion object {
        private const val DOUBLE_PRESS_WINDOW_MS = 900L
    }
}
