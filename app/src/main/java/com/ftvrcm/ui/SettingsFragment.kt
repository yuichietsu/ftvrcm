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
import com.ftvrcm.domain.OperationMode
import com.ftvrcm.domain.ToggleTrigger
import com.ftvrcm.service.RemoteControlAccessibilityService
import com.ftvrcm.shizuku.ShizukuTouchInjector
import rikka.shizuku.Shizuku

private const val TAG = "SettingsFragment"

class SettingsFragment : PreferenceFragmentCompat() {


    private var listener: SharedPreferences.OnSharedPreferenceChangeListener? = null

    private val shizukuPermissionListener = Shizuku.OnRequestPermissionResultListener { _, _ ->
        activity?.runOnUiThread {
            if (isAdded) refreshShizukuPreferences()
        }
    }

    private val shizukuBinderReceivedListener = Shizuku.OnBinderReceivedListener {
        activity?.runOnUiThread {
            if (isAdded) refreshShizukuPreferences()
        }
    }

    private val shizukuBinderDeadListener = Shizuku.OnBinderDeadListener {
        activity?.runOnUiThread {
            if (isAdded) refreshShizukuPreferences()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            Shizuku.addRequestPermissionResultListener(shizukuPermissionListener)
            Shizuku.addBinderReceivedListener(shizukuBinderReceivedListener)
            Shizuku.addBinderDeadListener(shizukuBinderDeadListener)
        } catch (_: Throwable) {
        }
    }

    /**
     * hide 直前にフォーカスしていた View の弱参照。
     * hide/show でViewは生きているため、アダプター位置でなく View 導第で直接復元する。
     */
    private var savedFocusedView = WeakReference<android.view.View>(null)

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {

        preferenceManager.sharedPreferencesName = SettingsKeys.PREFS_NAME
        setPreferencesFromResource(R.xml.preferences, rootKey)

        preferenceScreen?.let { disableIconSpaceReservedRecursively(it) }


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

        val enableA11yTop = findPreference<Preference>("enable_accessibility_shizuku")
        enableA11yTop?.setOnPreferenceClickListener {
            enableAccessibilityViaShizuku()
            true
        }

        val enableA11ySub = findPreference<Preference>("shizuku_enable_accessibility")
        enableA11ySub?.setOnPreferenceClickListener {
            enableAccessibilityViaShizuku()
            true
        }

        val requestPermPref = findPreference<Preference>("shizuku_request_permission")
        requestPermPref?.setOnPreferenceClickListener {
            requestShizukuPermission()
            true
        }

        val shizukuOpenApp = findPreference<Preference>("shizuku_open_app")
        shizukuOpenApp?.setOnPreferenceClickListener {
            openShizukuApp()
            true
        }

        val shizukuTest = findPreference<Preference>("shizuku_test_injection")
        shizukuTest?.setOnPreferenceClickListener {
            runShizukuTestInjection()
            true
        }

        refreshModeSummary()
        refreshRequiredStateSummary()
        refreshToggleKeySummary()
        refreshShizukuPreferences()

        val prefs = preferenceManager.sharedPreferences ?: return
        val l = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            when (key) {
                SettingsKeys.OPERATION_MODE,
                SettingsKeys.TOGGLE_KEYCODE,
                SettingsKeys.TOGGLE_TRIGGER,
                SettingsKeys.MOUSE_POINTER_SPEED,
                SettingsKeys.USE_SHIZUKU,
                SettingsKeys.EMULATION_METHOD,
                -> {
                    refreshModeSummary()
                    refreshToggleKeySummary()
                    refreshShizukuPreferences()
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
        refreshShizukuPreferences()
    }

    private fun refreshShizukuPreferences() {
        val isShizuku = SettingsStore(requireContext()).isUseShizuku()
        val a11yEnabled = isAccessibilityServiceEnabled()

        // トップレベル: Shizuku設定サブスクリーンの表示/非表示を切り替える
        findPreference<Preference>("screen_shizuku")?.isVisible = isShizuku

        // トップレベル: アクセシビリティ有効化（未有効かつShizuku使用時のみ表示）
        findPreference<Preference>("enable_accessibility_shizuku")?.isVisible = !a11yEnabled && isShizuku

        val isAlive = ShizukuTouchInjector.isShizukuAvailable()
        val isGranted = ShizukuTouchInjector.isPermissionGranted()

        findPreference<Preference>("shizuku_service_status")?.summary =
            if (isAlive) getString(R.string.prefs_shizuku_status_running)
            else getString(R.string.prefs_shizuku_status_stopped)

        findPreference<Preference>("shizuku_permission_status")?.summary =
            if (isGranted) getString(R.string.prefs_shizuku_permission_granted)
            else getString(R.string.prefs_shizuku_permission_denied)

        findPreference<Preference>("shizuku_request_permission")?.apply {
            isEnabled = isAlive && !isGranted
            isVisible = !isGranted
        }

        findPreference<Preference>("shizuku_enable_accessibility")?.apply {
            isEnabled = isAlive && isGranted && !a11yEnabled
            isVisible = !a11yEnabled
        }

        findPreference<Preference>("shizuku_test_injection")?.isEnabled = isAlive && isGranted

        refreshDashboard()
    }

    private fun requestShizukuPermission() {
        if (!ShizukuTouchInjector.isShizukuAvailable()) {
            Toast.makeText(requireContext(), getString(R.string.prefs_shizuku_status_stopped), Toast.LENGTH_LONG).show()
            return
        }
        ShizukuTouchInjector.requestPermission(1001)
    }

    private fun enableAccessibilityViaShizuku() {
        if (!ShizukuTouchInjector.isShizukuAvailable()) {
            Toast.makeText(requireContext(), getString(R.string.prefs_shizuku_status_stopped), Toast.LENGTH_LONG).show()
            return
        }
        if (!ShizukuTouchInjector.isPermissionGranted()) {
            Toast.makeText(requireContext(), getString(R.string.prefs_shizuku_not_ready), Toast.LENGTH_LONG).show()
            requestShizukuPermission()
            return
        }
        val context = requireContext()
        Thread {
            val (success, message) = ShizukuTouchInjector.enableAccessibilityService()
            activity?.runOnUiThread {
                if (!isAdded) return@runOnUiThread
                if (success) {
                    Toast.makeText(context, getString(R.string.prefs_enable_accessibility_shizuku_success), Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, getString(R.string.prefs_enable_accessibility_shizuku_failed, message), Toast.LENGTH_LONG).show()
                }
                refreshRequiredStateSummary()
                refreshShizukuPreferences()
            }
        }.start()
    }

    private fun openShizukuApp() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.prefs_shizuku_open_app_dialog_title)
            .setMessage(R.string.prefs_shizuku_open_app_dialog_message)
            .setPositiveButton(R.string.prefs_shizuku_open_app) { _, _ ->
                launchShizukuActivity()
            }
            .setNegativeButton(R.string.prefs_common_cancel, null)
            .show()
    }

    private fun launchShizukuActivity() {
        val pm = requireContext().packageManager
        var intent = pm.getLaunchIntentForPackage("moe.shizuku.privileged.api")
        if (intent == null) {
            intent = Intent().apply {
                component = ComponentName("moe.shizuku.privileged.api", "moe.shizuku.manager.MainActivity")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
        try {
            startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(requireContext(), "Shizukuアプリが見つかりません (moe.shizuku.privileged.api)", Toast.LENGTH_LONG).show()
        }
    }

    private fun runShizukuTestInjection() {
        val context = requireContext()
        Thread {
            val injector = ShizukuTouchInjector(context.applicationContext)
            val result = injector.testConnection()
            activity?.runOnUiThread {
                if (!isAdded) return@runOnUiThread
                AlertDialog.Builder(context)
                    .setTitle(if (result.ok) "Shizuku接続テスト成功" else "Shizuku接続テスト失敗")
                    .setMessage(result.detail)
                    .setPositiveButton(getString(android.R.string.ok)) { _, _ -> }
                    .show()
                refreshShizukuPreferences()
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
        try {
            Shizuku.removeRequestPermissionResultListener(shizukuPermissionListener)
            Shizuku.removeBinderReceivedListener(shizukuBinderReceivedListener)
            Shizuku.removeBinderDeadListener(shizukuBinderDeadListener)
        } catch (_: Throwable) {
        }
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
        val store = SettingsStore(requireContext())
        val mode = store.getOperationMode()
        val shizukuStatus = if (!store.isUseShizuku()) {
            SettingsActivity.ShizukuStatus.OFF
        } else if (ShizukuTouchInjector.isShizukuAvailable() && ShizukuTouchInjector.isPermissionGranted()) {
            SettingsActivity.ShizukuStatus.ON
        } else {
            SettingsActivity.ShizukuStatus.UNAVAILABLE
        }

        activity.updateDashboard(
            touchEnabled = (mode == OperationMode.MOUSE),
            accessibilityOn = isAccessibilityServiceEnabled(),
            shizukuStatus = shizukuStatus,
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

}
