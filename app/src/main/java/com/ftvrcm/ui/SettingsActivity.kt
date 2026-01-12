package com.ftvrcm.ui

import android.os.Bundle
import android.view.KeyEvent
import androidx.appcompat.app.AppCompatActivity
import com.ftvrcm.R
import com.ftvrcm.data.SettingsStore

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SettingsStore(this).initializeDefaultsIfNeeded()

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
        }
        return super.dispatchKeyEvent(event)
    }
}
