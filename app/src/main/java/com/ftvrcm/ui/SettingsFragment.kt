package com.ftvrcm.ui

import android.content.ComponentName
import android.content.Intent
import android.content.ActivityNotFoundException
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.content.SharedPreferences
import android.os.SystemClock
import android.widget.Toast
import android.view.accessibility.AccessibilityManager
import android.accessibilityservice.AccessibilityServiceInfo
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.ftvrcm.R
import com.ftvrcm.data.SettingsKeys
import com.ftvrcm.data.SettingsStore
import com.ftvrcm.domain.OperationMode
import com.ftvrcm.service.MediaKeyToggleService

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
                SettingsKeys.DEBUG_LAST_MEDIA_EVENT,
                SettingsKeys.DEBUG_LAST_MEDIA_EVENT_AT,
                SettingsKeys.DEBUG_LAST_GESTURE_EVENT,
                SettingsKeys.DEBUG_LAST_GESTURE_EVENT_AT,
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

                SettingsKeys.MEDIA_TOGGLE_ENABLED,
                -> {
                    val context = requireContext()
                    val store = SettingsStore(context)
                    if (store.isMediaToggleEnabled()) {
                        try {
                            MediaKeyToggleService.start(context)
                        } catch (e: Exception) {
                            store.setDebugLastCrash("start_media_toggle_service", e)
                        }
                    } else {
                        try {
                            MediaKeyToggleService.stop(context)
                        } catch (e: Exception) {
                            store.setDebugLastCrash("stop_media_toggle_service", e)
                        }
                    }
                }
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

        val connectedAt = store.getDebugServiceConnectedAtElapsed()
        val enabledViaAm = isServiceEnabledViaAccessibilityManager()

        modePref?.summary = when (mode) {
            OperationMode.NORMAL -> getString(R.string.mode_normal)
            OperationMode.MOUSE -> {
                val base = getString(R.string.mode_mouse)
                val needsService = enabledViaAm != true || connectedAt <= 0L
                if (needsService) {
                    "$base（未接続: アクセシビリティを有効化してください）"
                } else {
                    base
                }
            }
        }
    }

    private fun refreshDebugSummary() {
        val store = SettingsStore(requireContext())

        val checksPref = findPreference<Preference>("debug_checks")
        checksPref?.summary = buildChecksSummary(store)

        val crashPref = findPreference<Preference>("debug_last_crash")
        val crash = store.getDebugLastCrash()
        crashPref?.summary = if (crash == null) {
            getString(R.string.prefs_debug_last_crash_summary)
        } else {
            val shortType = crash.type.substringAfterLast('.')
            val msg = crash.message.take(80)
            val hint = if (crash.type.endsWith("SecurityException")) "（権限不足の可能性）" else ""
            "${formatAge(crash.atElapsedRealtimeMs)} / ${crash.stage} / $shortType: $msg$hint"
        }

        val serviceStatusPref = findPreference<Preference>("debug_service_status")
        val connectedAt = store.getDebugServiceConnectedAtElapsed()
        val enabledViaAm = isServiceEnabledViaAccessibilityManager()
        val enabledViaSecure = isServiceEnabledViaSecureSettings()

        val enabledLabel = when {
            enabledViaAm == true -> "ON"
            enabledViaAm == false -> "OFF"
            enabledViaSecure == true -> "ON?"
            enabledViaSecure == false -> "OFF?"
            else -> "?"
        }

        serviceStatusPref?.summary = when {
            connectedAt <= 0L -> "有効化: $enabledLabel / 接続: 未接続"
            else -> "有効化: $enabledLabel / 接続: ${formatAge(connectedAt)}"
        }

        val mediaPref = findPreference<Preference>("debug_last_media")
        val media = store.getDebugLastMediaEvent()
        mediaPref?.summary = if (media == null) {
            getString(R.string.prefs_debug_last_media_summary)
        } else {
            "${formatAge(media.atElapsedRealtimeMs)} / ${media.summary}"
        }

        val gesturePref = findPreference<Preference>("debug_last_gesture")
        val gesture = store.getDebugLastGestureEvent()
        gesturePref?.summary = if (gesture == null) {
            getString(R.string.prefs_debug_last_gesture_summary)
        } else {
            "${formatAge(gesture.atElapsedRealtimeMs)} / ${gesture.summary}"
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

    private fun isServiceEnabledViaSecureSettings(): Boolean? {
        return try {
            val context = requireContext()
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ) ?: return false
            val me = ComponentName(context, com.ftvrcm.service.RemoteControlAccessibilityService::class.java)
            enabledServices.split(':').any { it.equals(me.flattenToString(), ignoreCase = true) }
        } catch (_: Exception) {
            null
        }
    }

    private fun isServiceEnabledViaAccessibilityManager(): Boolean? {
        return try {
            val context = requireContext()
            val am = context.getSystemService(AccessibilityManager::class.java) ?: return null
            val me = ComponentName(context, com.ftvrcm.service.RemoteControlAccessibilityService::class.java)
            am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                .any { it.id.equals(me.flattenToString(), ignoreCase = true) }
        } catch (_: Exception) {
            null
        }
    }

    private fun buildChecksSummary(store: SettingsStore): String {
        val context = requireContext()

        val am = context.getSystemService(AccessibilityManager::class.java)
        val accessibilityEnabled = am?.isEnabled
        val enabledViaSecure = isServiceEnabledViaSecureSettings()
        val enabledViaAm = isServiceEnabledViaAccessibilityManager()

        val me = ComponentName(context, com.ftvrcm.service.RemoteControlAccessibilityService::class.java)
        val info = try {
            am?.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                ?.firstOrNull { it.id.equals(me.flattenToString(), ignoreCase = true) }
        } catch (_: Exception) {
            null
        }

        val caps = info?.capabilities ?: 0
        val canFilterKeys = (caps and AccessibilityServiceInfo.CAPABILITY_CAN_REQUEST_FILTER_KEY_EVENTS) != 0
        val canGestures = (caps and AccessibilityServiceInfo.CAPABILITY_CAN_PERFORM_GESTURES) != 0

        val bg = store.isBackgroundMonitoringEnabled()
        val connectedAt = store.getDebugServiceConnectedAtElapsed()

        return buildString {
            append("accessibility:")
            append(
                when (accessibilityEnabled) {
                    true -> "ON"
                    false -> "OFF"
                    else -> "?"
                },
            )

            append(" / serviceEnabled.am:")
            append(
                when (enabledViaAm) {
                    true -> "ON"
                    false -> "OFF"
                    else -> "?"
                },
            )

            append(" / serviceEnabled.secure:")
            append(
                when (enabledViaSecure) {
                    true -> "ON"
                    false -> "OFF"
                    else -> "?"
                },
            )
            append(" / connected:")
            append(if (connectedAt > 0L) formatAge(connectedAt) else "NO")
            append(" / bgMonitor:")
            append(if (bg) "ON" else "OFF")
            append(" / cap.filterKeys:")
            append(if (info == null) "?" else if (canFilterKeys) "YES" else "NO")
            append(" / cap.gestures:")
            append(if (info == null) "?" else if (canGestures) "YES" else "NO")
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

        return startFirstAvailable(intents, stage = "open_accessibility_settings")
    }

    private fun openAppDetailsSettings(): Boolean {
        val context = requireContext()
        val intents = listOf(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.parse("package:${context.packageName}")),
            Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS),
            Intent(Settings.ACTION_SETTINGS),
        )
        return startFirstAvailable(intents, stage = "open_app_details_settings")
    }

    private fun openSystemSettings(): Boolean {
        val intents = listOf(
            Intent(Settings.ACTION_SETTINGS),
            Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS),
        )
        return startFirstAvailable(intents, stage = "open_system_settings")
    }

    private fun startFirstAvailable(intents: List<Intent>, stage: String): Boolean {
        val context = requireContext()
        val store = SettingsStore(context)

        var lastError: Throwable? = null
        for (rawIntent in intents) {
            val intent = Intent(rawIntent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                startActivity(intent)
                return true
            } catch (e: ActivityNotFoundException) {
                lastError = e
            } catch (e: SecurityException) {
                lastError = e
            } catch (e: Exception) {
                lastError = e
            }
        }

        if (lastError != null) {
            store.setDebugLastCrash(stage, lastError)
        }
        Toast.makeText(context, "設定画面を開けませんでした（端末側制限の可能性）", Toast.LENGTH_LONG).show()
        return true
    }
}
