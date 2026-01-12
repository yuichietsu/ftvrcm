package com.ftvrcm.ui

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.content.SharedPreferences
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
}
