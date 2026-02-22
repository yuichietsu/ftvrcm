package com.ftvrcm.ui

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.widget.TextView
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.ftvrcm.R

/**
 * 設定画面トップに表示するステータスダッシュボード。
 * タッチ操作の有効/無効とアクセシビリティサービスのON/OFFを一覧表示する。
 * クリック不可（isSelectable = false）。
 */
class StatusDashboardPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = androidx.preference.R.attr.preferenceStyle,
) : Preference(context, attrs, defStyleAttr) {

    var touchEnabled: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                notifyChanged()
            }
        }

    var accessibilityOn: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                notifyChanged()
            }
        }

    init {
        layoutResource = R.layout.preference_status_dashboard
        isSelectable = false
        isIconSpaceReserved = false
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)

        val tvTouch = holder.itemView.findViewById<TextView>(R.id.tv_touch_status)
        val tvAccessibility = holder.itemView.findViewById<TextView>(R.id.tv_accessibility_status)

        if (touchEnabled) {
            tvTouch?.text = context.getString(R.string.prefs_dashboard_touch_enabled)
            tvTouch?.setTextColor(Color.parseColor("#4CAF50")) // green
        } else {
            tvTouch?.text = context.getString(R.string.prefs_dashboard_touch_disabled)
            tvTouch?.setTextColor(Color.parseColor("#9E9E9E")) // gray
        }

        if (accessibilityOn) {
            tvAccessibility?.text = context.getString(R.string.prefs_dashboard_accessibility_on)
            tvAccessibility?.setTextColor(Color.parseColor("#4CAF50")) // green
        } else {
            tvAccessibility?.text = context.getString(R.string.prefs_dashboard_accessibility_off)
            tvAccessibility?.setTextColor(Color.parseColor("#FF9800")) // orange
        }
    }
}
