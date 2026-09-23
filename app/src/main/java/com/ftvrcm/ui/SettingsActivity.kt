package com.ftvrcm.ui

import android.graphics.Color
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceScreen
import com.ftvrcm.R
import com.ftvrcm.data.SettingsStore

class SettingsActivity : AppCompatActivity(),
    PreferenceFragmentCompat.OnPreferenceStartScreenCallback {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = SettingsStore(this)
        store.initializeDefaultsIfNeeded()

        setContentView(R.layout.activity_settings)

        // 初期ダッシュボード表示（実際の状態はFragment起動後に更新される）
        updateDashboard(accessibilityOn = false, shizukuRunning = false)

        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.container, SettingsFragment())
                .commit()
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            val topFragment = supportFragmentManager.fragments.lastOrNull { it.isAdded && !it.isHidden } as? SettingsFragment
            topFragment?.restorePreferenceFocus()
        }
    }

    /**
     * 画面下部の固定ステータスバーを更新する。
     * SettingsFragment の refreshDashboard() から呼び出される。
     */
    fun updateDashboard(accessibilityOn: Boolean, shizukuRunning: Boolean) {
        val tvAccessibility = findViewById<TextView>(R.id.tv_accessibility_status) ?: return
        val tvShizuku = findViewById<TextView>(R.id.tv_shizuku_status) ?: return

        if (accessibilityOn) {
            tvAccessibility.text = getString(R.string.prefs_dashboard_accessibility_on)
            tvAccessibility.setTextColor(Color.parseColor("#4CAF50"))
        } else {
            tvAccessibility.text = getString(R.string.prefs_dashboard_accessibility_off)
            tvAccessibility.setTextColor(Color.parseColor("#FF9800"))
        }

        if (shizukuRunning) {
            tvShizuku.text = getString(R.string.prefs_dashboard_shizuku_running)
            tvShizuku.setTextColor(Color.parseColor("#4CAF50"))
        } else {
            tvShizuku.text = getString(R.string.prefs_dashboard_shizuku_stopped)
            tvShizuku.setTextColor(Color.parseColor("#FF9800"))
        }
    }

    /**
     * ネストされた PreferenceScreen をタップしたときにサブスクリーンへ遷移する。
     * hide+add 方式を用いて前スクリーンのスクロール位置を保持する。
     */
    override fun onPreferenceStartScreen(
        caller: PreferenceFragmentCompat,
        pref: PreferenceScreen,
    ): Boolean {
        val fragment = SettingsFragment().apply {
            arguments = Bundle().apply {
                putString(PreferenceFragmentCompat.ARG_PREFERENCE_ROOT, pref.key)
            }
        }
        supportFragmentManager
            .beginTransaction()
            .hide(caller)
            .add(R.id.container, fragment)
            .addToBackStack(pref.key)
            .commit()
        return true
    }
}
