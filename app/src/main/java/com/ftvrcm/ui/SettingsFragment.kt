package com.ftvrcm.ui

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.content.SharedPreferences
import android.os.SystemClock
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.ftvrcm.R
import com.ftvrcm.data.SettingsKeys
import com.ftvrcm.data.SettingsStore
import com.ftvrcm.domain.OperationMode

class SettingsFragment : PreferenceFragmentCompat() {

    private var listener: SharedPreferences.OnSharedPreferenceChangeListener? = null

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        preferenceManager.sharedPreferencesName = SettingsKeys.PREFS_NAME
        setPreferencesFromResource(R.xml.preferences, rootKey)

        val openAccessibility = findPreference<Preference>("open_accessibility_settings")
        openAccessibility?.setOnPreferenceClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            true
        }

        refreshModeSummary()
        refreshDebugSummary()

        val prefs = preferenceManager.sharedPreferences ?: return
        val l = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            when (key) {
                SettingsKeys.OPERATION_MODE,
                SettingsKeys.TOGGLE_KEYCODE,
                SettingsKeys.MOUSE_POINTER_SPEED,
                SettingsKeys.ACTION_TYPE,
                SettingsKeys.ACTION_PARAM,
                -> refreshModeSummary()
            }

            when (key) {
                SettingsKeys.DEBUG_SHOW_KEYCODE,
                SettingsKeys.DEBUG_LAST_KEYCODE,
                SettingsKeys.DEBUG_LAST_KEYNAME,
                SettingsKeys.DEBUG_LAST_KEY_AT,
                -> refreshDebugSummary()
            }

            when (key) {
                SettingsKeys.MOUSE_KEY_UP,
                SettingsKeys.MOUSE_KEY_DOWN,
                SettingsKeys.MOUSE_KEY_LEFT,
                SettingsKeys.MOUSE_KEY_RIGHT,
                SettingsKeys.MOUSE_KEY_CLICK,
                SettingsKeys.MOUSE_KEY_LONGCLICK,
                -> SettingsStore(requireContext()).upsertMouseKeyMapping()

                SettingsKeys.ACTION_KEYCODE,
                SettingsKeys.ACTION_TYPE,
                SettingsKeys.ACTION_PARAM,
                -> SettingsStore(requireContext()).upsertButtonActionFromUi()
            }
        }

        listener = l
        prefs.registerOnSharedPreferenceChangeListener(l)
    }

    override fun onDestroy() {
        val l = listener
        if (l != null) {
            preferenceManager.sharedPreferences?.unregisterOnSharedPreferenceChangeListener(l)
        }
        listener = null
        super.onDestroy()
    }

    private fun refreshModeSummary() {
        val store = SettingsStore(requireContext())
        val modePref = findPreference<Preference>("operation_mode_current")
        val mode = store.getOperationMode()

        modePref?.summary = when (mode) {
            OperationMode.NORMAL -> getString(R.string.mode_normal)
            OperationMode.MOUSE -> getString(R.string.mode_mouse)
        }
    }

    private fun refreshDebugSummary() {
        val store = SettingsStore(requireContext())

        val serviceStatusPref = findPreference<Preference>("debug_service_status")
        val connectedAt = store.getDebugServiceConnectedAtElapsed()
        serviceStatusPref?.summary = if (connectedAt <= 0L) {
            getString(R.string.prefs_debug_service_status_summary)
        } else {
            "接続済み（${formatAge(connectedAt)}）"
        }

        val pref = findPreference<Preference>(SettingsKeys.DEBUG_LAST_KEYCODE)
        val last = store.getDebugLastKey()
        pref?.summary = if (last == null) {
            getString(R.string.prefs_debug_last_key_summary)
        } else {
            val name = last.keyName.ifBlank { "KEYCODE_${last.keyCode}" }
            "$name (${last.keyCode})（${formatAge(last.atElapsedRealtimeMs)}）"
        }
    }

    private fun formatAge(atElapsedRealtimeMs: Long): String {
        val deltaMs = (SystemClock.elapsedRealtime() - atElapsedRealtimeMs).coerceAtLeast(0L)
        val sec = deltaMs / 1000
        return when {
            sec < 60 -> "${sec}秒前"
            sec < 60 * 60 -> "${sec / 60}分前"
            else -> "${sec / 3600}時間前"
        }
    }
}
