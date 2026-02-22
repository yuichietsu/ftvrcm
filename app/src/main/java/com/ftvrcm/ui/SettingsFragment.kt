package com.ftvrcm.ui

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.content.SharedPreferences
import android.provider.Settings
import android.accessibilityservice.AccessibilityServiceInfo
import android.view.KeyEvent
import android.view.accessibility.AccessibilityManager
import android.widget.Toast
import androidx.core.content.getSystemService
import androidx.appcompat.app.AlertDialog
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceGroup
import java.lang.ref.WeakReference
import com.ftvrcm.R
import com.ftvrcm.data.SettingsKeys
import com.ftvrcm.data.SettingsStore
import com.ftvrcm.domain.EmulationMethod
import com.ftvrcm.domain.OperationMode
import com.ftvrcm.domain.ToggleTrigger
import com.ftvrcm.proxy.ProxyInputClient
import com.ftvrcm.service.RemoteControlAccessibilityService

private const val TAG = "SettingsFragment"

class SettingsFragment : PreferenceFragmentCompat() {

    private var listener: SharedPreferences.OnSharedPreferenceChangeListener? = null
    @Volatile private var proxyHealthCheckRunning: Boolean = false

    /**
     * hide 直前にフォーカスしていた View の弱参照。
     * hide/show でViewは生きているため、アダプター位置でなく View 導第で直接復元する。
     */
    private var savedFocusedView = WeakReference<android.view.View>(null)

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

        val proxyHealth = findPreference<Preference>("proxy_health_check")
        proxyHealth?.setOnPreferenceClickListener {
            runProxyHealthCheck()
            true
        }

        val proxyGrantAccessibility = findPreference<Preference>("proxy_grant_accessibility")
        proxyGrantAccessibility?.setOnPreferenceClickListener {
            runProxyGrantAccessibility()
            true
        }

        refreshModeSummary()
        refreshRequiredStateSummary()
        refreshToggleKeySummary()
        refreshProxyPreferences()

        val prefs = preferenceManager.sharedPreferences ?: return
        val l = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            when (key) {
                SettingsKeys.OPERATION_MODE,
                SettingsKeys.TOGGLE_KEYCODE,
                SettingsKeys.TOGGLE_TRIGGER,
                SettingsKeys.MOUSE_POINTER_SPEED,
                SettingsKeys.EMULATION_METHOD,
                SettingsKeys.PROXY_HOST,
                SettingsKeys.PROXY_PORT,
                SettingsKeys.PROXY_TOKEN,
                -> {
                    refreshModeSummary()
                    refreshToggleKeySummary()
                    refreshProxyPreferences()
                }
            }

            when (key) {
                SettingsKeys.MOUSE_KEY_UP,
                SettingsKeys.MOUSE_KEY_DOWN,
                SettingsKeys.MOUSE_KEY_LEFT,
                SettingsKeys.MOUSE_KEY_RIGHT,
                SettingsKeys.MOUSE_KEY_CLICK,
                SettingsKeys.MOUSE_KEY_SCROLL_UP,
                SettingsKeys.MOUSE_KEY_SCROLL_DOWN,
                SettingsKeys.MOUSE_KEY_SCROLL_LEFT,
                SettingsKeys.MOUSE_KEY_SCROLL_RIGHT,
                SettingsKeys.MOUSE_KEY_PINCH_IN,
                SettingsKeys.MOUSE_KEY_PINCH_OUT,
                SettingsKeys.MOUSE_KEY_CURSOR_DPAD_TOGGLE,
                -> SettingsStore(requireContext()).upsertMouseKeyMapping()
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

    /**
     * hide 時にフォーカスViewを保存し、show 時にそのまま復元する。
     *
     * Androidは hide 時にフォーカスを自動保存しない。show 後はシステムが先頭項目を
     * 探してフォーカスするため、明示的に requestFocus() する必要がある。
     * hide/show でViewは破棄されないため、WeakReference で直接 View を保持して
     * post{} なしで即時復元できる。
     */
    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (hidden) {
            savedFocusedView = WeakReference(listView?.focusedChild)
        } else {
            savedFocusedView.get()?.requestFocus()
        }
    }

    override fun onResume() {
        super.onResume()
        refreshModeSummary()
        refreshRequiredStateSummary()
        refreshToggleKeySummary()
        refreshProxyPreferences()

    }

    private fun refreshProxyPreferences() {
        val isProxy = SettingsStore(requireContext()).getEmulationMethod() == EmulationMethod.PROXY

        // トップレベル: ADBプロキシ設定サブスクリーンの表示/非表示を切り替える
        findPreference<Preference>("screen_proxy")?.isVisible = isProxy

        // サブスクリーン内コンテキスト: ボタンは常に有効（プロキシモード前提で表示されるため）
        findPreference<Preference>("proxy_health_check")?.let {
            it.isEnabled = true
            it.summary = getString(R.string.prefs_proxy_health_check_summary)
        }
        findPreference<Preference>("proxy_grant_accessibility")?.let {
            it.isEnabled = true
            it.summary = getString(R.string.prefs_proxy_grant_accessibility_summary)
        }
    }

    private fun runProxyGrantAccessibility() {
        val context = requireContext()
        val pref = findPreference<Preference>("proxy_grant_accessibility")

        if (proxyHealthCheckRunning) {
            Toast.makeText(context, getString(R.string.prefs_proxy_grant_accessibility_running), Toast.LENGTH_SHORT).show()
            return
        }

        proxyHealthCheckRunning = true
        pref?.isEnabled = false
        pref?.summary = getString(R.string.prefs_proxy_grant_accessibility_running)

        Thread {
            val store = SettingsStore(context.applicationContext)
            val host = store.getProxyHost()
            val port = store.getProxyPort()
            val token = store.getProxyToken()
            val component = ComponentName(context.applicationContext, RemoteControlAccessibilityService::class.java).flattenToString()

            val result = ProxyInputClient(
                context.applicationContext,
                host = host,
                port = port,
                token = token,
            ).grantAccessibility(component)

            activity?.runOnUiThread {
                if (!isAdded) return@runOnUiThread
                proxyHealthCheckRunning = false
                refreshProxyPreferences()

                if (result.ok) {
                    Toast.makeText(context, "ADBでアクセシビリティを有効化しました", Toast.LENGTH_LONG).show()
                    refreshRequiredStateSummary()
                    restorePreferenceFocusSoon()
                } else {
                    val dialog = AlertDialog.Builder(context)
                        .setTitle("ADB付与に失敗")
                        .setMessage(result.detail.trim().take(2000))
                        .setPositiveButton(getString(android.R.string.ok)) { _, _ -> }
                        .create()

                    dialog.setOnDismissListener {
                        restorePreferenceFocusSoon()
                    }
                    dialog.show()
                }
            }
        }.start()
    }

    private fun runProxyHealthCheck() {
        val context = requireContext()
        val pref = findPreference<Preference>("proxy_health_check")

        if (proxyHealthCheckRunning) {
            Toast.makeText(context, getString(R.string.prefs_proxy_health_check_running), Toast.LENGTH_SHORT).show()
            return
        }

        proxyHealthCheckRunning = true
        pref?.isEnabled = false
        pref?.summary = getString(R.string.prefs_proxy_health_check_running)

        Thread {
            val store = SettingsStore(context.applicationContext)
            val host = store.getProxyHost()
            val port = store.getProxyPort()
            val token = store.getProxyToken()

            val result = ProxyInputClient(
                context.applicationContext,
                host = host,
                port = port,
                token = token,
            ).healthCheck()

            activity?.runOnUiThread {
                if (!isAdded) return@runOnUiThread
                proxyHealthCheckRunning = false
                refreshProxyPreferences()

                if (result.ok) {
                    Toast.makeText(context, "プロキシ疎通OK", Toast.LENGTH_LONG).show()
                    restorePreferenceFocusSoon()
                } else {
                    val dialog = AlertDialog.Builder(context)
                        .setTitle("プロキシ疎通NG")
                        .setMessage(result.detail.trim().take(2000))
                        .setPositiveButton(getString(android.R.string.ok)) { _, _ -> }
                        .create()

                    dialog.setOnDismissListener {
                        restorePreferenceFocusSoon()
                    }
                    dialog.show()
                }
            }
        }.start()
    }

    private fun restorePreferenceFocusSoon() {
        try {
            // On TV devices, dialogs/toasts can steal focus from Preference's RecyclerView.
            // Restore focus so DPAD key navigation works without restarting the activity.
            listView?.post { listView?.requestFocus() }
        } catch (_: Throwable) {
        }
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
        // operation_mode_current は削除済み。ダッシュボードのみ更新する。
        refreshDashboard()
    }

    private fun refreshToggleKeySummary() {
        val toggleKeyPref = findPreference<KeyCapturePreference>(SettingsKeys.TOGGLE_KEYCODE) ?: return
        val prefs = preferenceManager.sharedPreferences ?: return
        val rawValue = prefs.getString(SettingsKeys.TOGGLE_KEYCODE, "82")
        val label = KeyCapturePreference.formatKeyLabel(requireContext(), rawValue)
        val trigger = SettingsStore(requireContext()).getToggleTrigger()
        val keyCode = rawValue?.toIntOrNull()

        val warning = if (
            isAmazonDevice() &&
            trigger == ToggleTrigger.LONG_PRESS &&
            keyCode == KeyEvent.KEYCODE_BACK
        ) {
            getString(R.string.prefs_toggle_key_warning_back_longpress)
        } else {
            null
        }

        val base = getString(R.string.prefs_key_capture_summary, label)
        toggleKeyPref.summary = if (warning == null) base else "$base\n$warning"
    }

    private fun isAmazonDevice(): Boolean {
        val manufacturer = Build.MANUFACTURER ?: ""
        val brand = Build.BRAND ?: ""
        return manufacturer.equals("Amazon", ignoreCase = true) || brand.equals("Amazon", ignoreCase = true)
    }

    private fun refreshRequiredStateSummary() {
        // status_accessibility_service は削除済み。ダッシュボードのみ更新する。
        refreshDashboard()
    }

    private fun refreshDashboard() {
        val activity = activity as? SettingsActivity ?: return
        val mode = SettingsStore(requireContext()).getOperationMode()
        activity.updateDashboard(
            touchEnabled = (mode == OperationMode.MOUSE),
            accessibilityOn = isAccessibilityServiceEnabled(),
        )
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val context = requireContext()

        // Prefer AccessibilityManager because some Fire OS builds may not reliably expose
        // Settings.Secure.ACCESSIBILITY_ENABLED / ENABLED_ACCESSIBILITY_SERVICES.
        val expected = ComponentName(context, RemoteControlAccessibilityService::class.java)
        val expectedId = "${expected.packageName}/${expected.className}"

        try {
            val am = context.getSystemService<AccessibilityManager>()
            if (am != null) {
                val enabled = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                for (info in enabled) {
                    if (info.id == expectedId) return true
                }
            }
        } catch (_: Throwable) {
        }

        val enabled = try {
            Settings.Secure.getInt(context.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED)
        } catch (_: Exception) {
            0
        }
        if (enabled != 1) return false

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
            .setMessage(getString(R.string.prefs_reset_confirm_message))
            .setPositiveButton(getString(R.string.prefs_reset_confirm_positive)) { _, _ ->
                SettingsStore(requireContext()).resetToDefaults()
                Toast.makeText(requireContext(), getString(R.string.prefs_reset_done), Toast.LENGTH_SHORT).show()
                activity?.recreate()
            }
            .setNegativeButton(getString(R.string.prefs_common_cancel), null)
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
