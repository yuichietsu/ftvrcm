package com.ftvrcm.ui

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.content.SharedPreferences
import android.provider.Settings
import android.widget.Toast
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
            openAccessibilitySettings()
        }

        val openAppDetails = findPreference<Preference>("open_app_details_settings")
        openAppDetails?.setOnPreferenceClickListener {
            openAppDetailsSettings()
        }

        val openSystemSettings = findPreference<Preference>("open_system_settings")
        openSystemSettings?.setOnPreferenceClickListener {
            openSystemSettings()
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
                SettingsKeys.MOUSE_KEY_SWIPE_UP,
                SettingsKeys.MOUSE_KEY_SWIPE_DOWN,
                SettingsKeys.MOUSE_KEY_SWIPE_LEFT,
                SettingsKeys.MOUSE_KEY_SWIPE_RIGHT,
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

    private fun openAccessibilitySettings(): Boolean {
        val context = requireContext()
        val me = ComponentName(context, com.ftvrcm.service.RemoteControlAccessibilityService::class.java)

        val intents = listOf(
            // Newer Android builds can open the details page for a specific service.
            Intent("android.settings.ACCESSIBILITY_DETAILS_SETTINGS")
                .putExtra("android.provider.extra.ACCESSIBILITY_COMPONENT_NAME", me.flattenToString()),
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS),
            // Fire OS variants sometimes hide/relocate accessibility; at least open Settings.
            Intent(Settings.ACTION_SETTINGS),
            // As a last resort, guide user to this app's details page.
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.parse("package:${context.packageName}")),
        )

        return startFirstAvailable(intents)
    }

    private fun openAppDetailsSettings(): Boolean {
        val context = requireContext()
        val intents = listOf(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.parse("package:${context.packageName}")),
            Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS),
            Intent(Settings.ACTION_SETTINGS),
        )
        return startFirstAvailable(intents)
    }

    private fun openSystemSettings(): Boolean {
        val intents = listOf(
            Intent(Settings.ACTION_SETTINGS),
            Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS),
        )
        return startFirstAvailable(intents)
    }

    private fun startFirstAvailable(intents: List<Intent>): Boolean {
        val context = requireContext()
        for (rawIntent in intents) {
            val intent = Intent(rawIntent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                startActivity(intent)
                return true
            } catch (e: ActivityNotFoundException) {
            } catch (e: SecurityException) {
            } catch (e: Exception) {
            }
        }

        Toast.makeText(context, "設定画面を開けませんでした（端末側制限の可能性）", Toast.LENGTH_LONG).show()
        return true
    }
}
