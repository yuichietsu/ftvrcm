package com.ftvrcm.ui

import android.content.Context
import android.view.KeyEvent
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.ftvrcm.R

class KeyCapturePreference @JvmOverloads constructor(
    context: Context,
    attrs: android.util.AttributeSet? = null,
    defStyleAttr: Int = androidx.preference.R.attr.preferenceStyle,
) : Preference(context, attrs, defStyleAttr) {

    private var currentValue: String? = null

    override fun onSetInitialValue(defaultValue: Any?) {
        val value = getPersistedString(defaultValue?.toString())
        currentValue = value
        updateSummary(value)
    }

    override fun onGetDefaultValue(a: android.content.res.TypedArray, index: Int): Any? {
        return a.getString(index)
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        updateSummary(currentValue)
    }

    override fun onClick() {
        super.onClick()
        showActionDialog()
    }

    private fun showActionDialog() {
        AlertDialog.Builder(context)
            .setTitle(title)
            .setMessage(R.string.prefs_key_capture_action_message)
            .setPositiveButton(R.string.prefs_key_capture_assign) { _, _ ->
                showKeyCaptureDialog()
            }
            .setNeutralButton(R.string.prefs_key_capture_clear) { _, _ ->
                val storedValue = "0"
                if (callChangeListener(storedValue)) {
                    persistString(storedValue)
                    currentValue = storedValue
                    updateSummary(storedValue)
                }
            }
            .setNegativeButton(R.string.prefs_common_cancel, null)
            .show()
    }

    private fun showKeyCaptureDialog() {
        val messageView = TextView(context).apply {
            text = context.getString(R.string.prefs_key_capture_dialog_message)
            setPadding(48, 32, 48, 32)
            textSize = 16f
            isFocusable = true
            isFocusableInTouchMode = true
        }

        val dialog = AlertDialog.Builder(context)
            .setTitle(title)
            .setView(messageView)
            .setNegativeButton(R.string.prefs_common_cancel) { _, _ -> }
            .create()

        dialog.setOnShowListener {
            messageView.requestFocus()
        }

        dialog.setOnKeyListener { _, keyCode, event ->
            if (event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener true

            val storedValue = if (keyCode == KeyEvent.KEYCODE_UNKNOWN && event.scanCode > 0) {
                (-event.scanCode).toString()
            } else {
                keyCode.toString()
            }

            if (callChangeListener(storedValue)) {
                persistString(storedValue)
                currentValue = storedValue
                updateSummary(storedValue)
            }
            dialog.dismiss()
            true
        }

        dialog.setCanceledOnTouchOutside(false)
        dialog.show()
    }

    private fun updateSummary(value: String?) {
        val label = formatKeyLabel(context, value)
        summary = context.getString(R.string.prefs_key_capture_summary, label)
    }

    companion object {
        fun formatKeyLabel(context: Context, value: String?): String {
            val code = value?.toIntOrNull() ?: return "-"
            if (code == 0) return context.getString(R.string.prefs_key_capture_unassigned)
            return if (code < 0) {
                "SCANCODE_${-code}"
            } else {
                val label = KeyEvent.keyCodeToString(code)
                if (label == "KEYCODE_UNKNOWN") "UNKNOWN($code)" else "$label ($code)"
            }
        }
    }
}
