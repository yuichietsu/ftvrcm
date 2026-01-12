package com.ftvrcm.ui

import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
import androidx.appcompat.app.AppCompatActivity
import com.ftvrcm.R
import com.ftvrcm.data.SettingsStore
import com.ftvrcm.domain.OperationMode
import com.ftvrcm.service.MediaKeyToggleService

class SettingsActivity : AppCompatActivity() {

    private var lastPlayPauseDownAtMs: Long = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = SettingsStore(this)
        store.initializeDefaultsIfNeeded()

        // Best-effort: keep media-key toggle alive if enabled.
        if (store.isMediaToggleEnabled()) {
            MediaKeyToggleService.start(this)
        }

        setContentView(R.layout.activity_settings)

        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.container, SettingsFragment())
                .commit()
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // Fallback: capture key codes while Settings UI is focused.
        // This helps diagnose cases where AccessibilityService does not receive key events on some Fire OS builds.
        if (event.action == KeyEvent.ACTION_DOWN) {
            val store = SettingsStore(this)
            if (store.isDebugShowKeyCodeEnabled()) {
                store.setDebugLastKey(event.keyCode, KeyEvent.keyCodeToString(event.keyCode))
            }

            if (store.isMediaToggleEnabled()) {
                val isPlayPause = event.keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE ||
                    event.keyCode == KeyEvent.KEYCODE_MEDIA_PLAY ||
                    event.keyCode == KeyEvent.KEYCODE_MEDIA_PAUSE

                if (isPlayPause) {
                    val now = SystemClock.elapsedRealtime()
                    val delta = now - lastPlayPauseDownAtMs
                    lastPlayPauseDownAtMs = now

                    if (delta in 1..900L) {
                        lastPlayPauseDownAtMs = 0L
                        val next = when (store.getOperationMode()) {
                            OperationMode.NORMAL -> OperationMode.MOUSE
                            OperationMode.MOUSE -> OperationMode.NORMAL
                        }
                        store.setOperationMode(next)
                        store.setDebugLastMediaEvent("SettingsActivity double (${delta}ms) -> ${next.name}")
                    } else {
                        store.setDebugLastMediaEvent("SettingsActivity single (wait) delta=${if (delta <= 0) "-" else delta}")
                    }
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }
}
