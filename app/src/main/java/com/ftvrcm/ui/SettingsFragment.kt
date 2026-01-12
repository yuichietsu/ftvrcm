package com.ftvrcm.ui

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.content.SharedPreferences
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceGroup
import com.ftvrcm.R
import com.ftvrcm.data.SettingsKeys
import com.ftvrcm.data.SettingsStore
import com.ftvrcm.domain.OperationMode
import com.ftvrcm.service.RemoteControlAccessibilityService

class SettingsFragment : PreferenceFragmentCompat() {

    private var listener: SharedPreferences.OnSharedPreferenceChangeListener? = null

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        preferenceManager.sharedPreferencesName = SettingsKeys.PREFS_NAME
        setPreferencesFromResource(R.xml.preferences, rootKey)

        preferenceScreen?.let { disableIconSpaceReservedRecursively(it) }

        val openAccessibility = findPreference<Preference>("open_accessibility_settings")
        openAccessibility?.setOnPreferenceClickListener {
            openAccessibilitySettings()
        }

        val openAppDetails = findPreference<Preference>("open_app_details_settings")
        openAppDetails?.setOnPreferenceClickListener {
            openAppDetailsSettings()
        }

        val resetDefaults = findPreference<Preference>("reset_defaults")
        resetDefaults?.setOnPreferenceClickListener {
            confirmResetDefaults()
            true
        }

        val openTouchTest = findPreference<Preference>("open_touch_test")
        openTouchTest?.setOnPreferenceClickListener {
            startActivity(Intent(requireContext(), TouchTestActivity::class.java))
            true
        }

        refreshModeSummary()
        refreshRequiredStateSummary()

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
                SettingsKeys.MOUSE_KEY_DOUBLE_TAP,
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

    private fun disableIconSpaceReservedRecursively(pref: Preference) {
        pref.isIconSpaceReserved = false

        val group = pref as? PreferenceGroup ?: return
        for (i in 0 until group.preferenceCount) {
            disableIconSpaceReservedRecursively(group.getPreference(i))
        }
    }

    override fun onResume() {
        super.onResume()
        refreshModeSummary()
        refreshRequiredStateSummary()
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

    private fun refreshRequiredStateSummary() {
        val statusPref = findPreference<Preference>("status_accessibility_service")
        statusPref?.summary = if (isAccessibilityServiceEnabled()) {
            getString(R.string.prefs_status_accessibility_service_on)
        } else {
            getString(R.string.prefs_status_accessibility_service_off)
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val context = requireContext()

        val enabled = try {
            Settings.Secure.getInt(context.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED)
        } catch (_: Exception) {
            0
        }
        if (enabled != 1) return false

        val expected = ComponentName(context, RemoteControlAccessibilityService::class.java)
        val raw = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false

        val services = raw.split(':')
        for (s in services) {
            val cn = ComponentName.unflattenFromString(s) ?: continue
            if (cn.packageName == expected.packageName && cn.className == expected.className) {
                return true
            }
        }

        return false
    }

    private fun confirmResetDefaults() {
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.prefs_reset_defaults))
            .setMessage("設定をデフォルトに戻します。よろしいですか？")
            .setPositiveButton("戻す") { _, _ ->
                SettingsStore(requireContext()).resetToDefaults()
                Toast.makeText(requireContext(), "デフォルト設定に戻しました", Toast.LENGTH_SHORT).show()
                activity?.recreate()
            }
            .setNegativeButton("キャンセル", null)
            .show()
    }

    private fun openAccessibilitySettings(): Boolean {
        val context = requireContext()
        val me = ComponentName(context, RemoteControlAccessibilityService::class.java)

        val intents = listOf(
            // Newer Android builds can open the details page for a specific service.
            Intent("android.settings.ACCESSIBILITY_DETAILS_SETTINGS")
                .putExtra("android.provider.extra.ACCESSIBILITY_COMPONENT_NAME", me.flattenToString()),
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS),
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
