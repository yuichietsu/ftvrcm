package com.ftvrcm.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.ftvrcm.domain.EmulationMethod
import com.ftvrcm.domain.OperationMode
import com.ftvrcm.domain.ToggleTrigger

class SettingsStore(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(SettingsKeys.PREFS_NAME, Context.MODE_PRIVATE)

    fun resetToDefaults() {
        prefs.edit { clear() }
        initializeDefaultsIfNeeded()
    }

    fun getCursorStartPosition(): String =
        prefs.getString(SettingsKeys.MOUSE_CURSOR_START_POSITION, "center") ?: "center"

    fun setLastCursorPosition(x: Int, y: Int) {
        prefs.edit {
            putInt(SettingsKeys.MOUSE_CURSOR_LAST_X, x)
            putInt(SettingsKeys.MOUSE_CURSOR_LAST_Y, y)
        }
    }

    fun getLastCursorPositionOrNull(): Pair<Int, Int>? {
        if (!prefs.contains(SettingsKeys.MOUSE_CURSOR_LAST_X) || !prefs.contains(SettingsKeys.MOUSE_CURSOR_LAST_Y)) {
            return null
        }
        return prefs.getInt(SettingsKeys.MOUSE_CURSOR_LAST_X, 0) to prefs.getInt(SettingsKeys.MOUSE_CURSOR_LAST_Y, 0)
    }

    fun initializeDefaultsIfNeeded() {
        // NOTE: This app is still in active development; keep initialization simple.
        // If preferences look uninitialized, write defaults.
        if (prefs.contains(SettingsKeys.OPERATION_MODE)) return

        prefs.edit {
            putString(SettingsKeys.OPERATION_MODE, OperationMode.NORMAL.name)

            putString(SettingsKeys.TOGGLE_KEYCODE, "82") // MENU (Avoid Fire TV BACK long-press conflicts)
            putBoolean(SettingsKeys.TOGGLE_LONGPRESS, true)
            putString(SettingsKeys.TOGGLE_TRIGGER, ToggleTrigger.LONG_PRESS.name)

            putInt(SettingsKeys.MOUSE_POINTER_SPEED, 10)

            putString(SettingsKeys.EMULATION_METHOD, EmulationMethod.ACCESSIBILITY_SERVICE.name)

            // Proxy target (PC proxy server)
            putString(SettingsKeys.PROXY_HOST, "")
            putString(SettingsKeys.PROXY_PORT, "8787")
            putString(SettingsKeys.PROXY_TOKEN, "")

            putString(SettingsKeys.MOUSE_KEY_UP, "19")
            putString(SettingsKeys.MOUSE_KEY_DOWN, "20")
            putString(SettingsKeys.MOUSE_KEY_LEFT, "21")
            putString(SettingsKeys.MOUSE_KEY_RIGHT, "22")
            putString(SettingsKeys.MOUSE_KEY_CLICK, "23")

            // Scroll/select keys (default: CH+/CH- and REW/FF)
            putString(SettingsKeys.MOUSE_KEY_SCROLL_UP, "166")
            putString(SettingsKeys.MOUSE_KEY_SCROLL_DOWN, "167")
            putString(SettingsKeys.MOUSE_KEY_SCROLL_LEFT, "89")
            putString(SettingsKeys.MOUSE_KEY_SCROLL_RIGHT, "90")

            // Pinch keys (default: unassigned)
            putString(SettingsKeys.MOUSE_KEY_PINCH_IN, "0")
            putString(SettingsKeys.MOUSE_KEY_PINCH_OUT, "0")

            // Swipe/scroll tuning
            putInt(SettingsKeys.MOUSE_SWIPE_DISTANCE_PERCENT, 28)
            putString(SettingsKeys.MOUSE_SWIPE_DOUBLE_SCALE, "2.0")
            putInt(SettingsKeys.MOUSE_PINCH_DISTANCE_PERCENT, 28)
            putString(SettingsKeys.MOUSE_PINCH_DOUBLE_SCALE, "2.0")
            putBoolean(SettingsKeys.MOUSE_SCROLL_REPEAT_LONGPRESS, true)
            putInt(SettingsKeys.MOUSE_SCROLL_REPEAT_INTERVAL_MS, 120)

            // Toggle cursor/dpad behavior (default: MENU)
            // NOTE: default uses the same key as mode toggle for convenience.
            putString(SettingsKeys.MOUSE_KEY_CURSOR_DPAD_TOGGLE, "82")

            // Cursor start position in mouse mode
            putString(SettingsKeys.MOUSE_CURSOR_START_POSITION, "center")

            // Visual feedback
            putBoolean(SettingsKeys.TOUCH_VISUAL_FEEDBACK_ENABLED, true)

            putStringSet(
                SettingsKeys.KEY_MAPPING,
                setOf(
                    "19:mouse_up",
                    "20:mouse_down",
                    "21:mouse_left",
                    "22:mouse_right",
                    "23:mouse_click",
                    "166:mouse_scroll_up",
                    "167:mouse_scroll_down",
                    "89:mouse_scroll_left",
                    "90:mouse_scroll_right",
                ),
            )
        }
    }

    fun getKeyMappingSet(): Set<String> = prefs.getStringSet(SettingsKeys.KEY_MAPPING, emptySet()) ?: emptySet()

    fun upsertMouseKeyMapping() {
        val set = mutableSetOf<String>()
        fun addIfAssigned(keyCode: Int, actionId: String) {
            if (keyCode != 0) set += "$keyCode:$actionId"
        }

        addIfAssigned(getMouseKeyUp(), "mouse_up")
        addIfAssigned(getMouseKeyDown(), "mouse_down")
        addIfAssigned(getMouseKeyLeft(), "mouse_left")
        addIfAssigned(getMouseKeyRight(), "mouse_right")
        addIfAssigned(getMouseKeyClick(), "mouse_click")
        addIfAssigned(getMouseKeyScrollUp(), "mouse_scroll_up")
        addIfAssigned(getMouseKeyScrollDown(), "mouse_scroll_down")
        addIfAssigned(getMouseKeyScrollLeft(), "mouse_scroll_left")
        addIfAssigned(getMouseKeyScrollRight(), "mouse_scroll_right")
        addIfAssigned(getMouseKeyPinchIn(), "mouse_pinch_in")
        addIfAssigned(getMouseKeyPinchOut(), "mouse_pinch_out")
        prefs.edit { putStringSet(SettingsKeys.KEY_MAPPING, set) }
    }

    fun getOperationMode(): OperationMode {
        val value = prefs.getString(SettingsKeys.OPERATION_MODE, OperationMode.NORMAL.name)
        return if (value == OperationMode.MOUSE.name) OperationMode.MOUSE else OperationMode.NORMAL
    }

    fun setOperationMode(mode: OperationMode) {
        prefs.edit { putString(SettingsKeys.OPERATION_MODE, mode.name) }
    }

    fun getToggleKeyCode(): Int = prefs.getString(SettingsKeys.TOGGLE_KEYCODE, "82")?.toIntOrNull() ?: 82

    fun getToggleTrigger(): ToggleTrigger {
        val raw = prefs.getString(SettingsKeys.TOGGLE_TRIGGER, null)
        if (raw != null) {
            return runCatching { ToggleTrigger.valueOf(raw) }.getOrDefault(ToggleTrigger.LONG_PRESS)
        }

        // Legacy fallback: toggle_longpress
        val legacyLongPress = prefs.getBoolean(SettingsKeys.TOGGLE_LONGPRESS, true)
        return if (legacyLongPress) ToggleTrigger.LONG_PRESS else ToggleTrigger.SINGLE_TAP
    }

    fun getMousePointerSpeedPx(): Int = prefs.getInt(SettingsKeys.MOUSE_POINTER_SPEED, 10).coerceIn(1, 200)

    fun getEmulationMethod(): EmulationMethod {
        val value = prefs.getString(SettingsKeys.EMULATION_METHOD, EmulationMethod.ACCESSIBILITY_SERVICE.name)
        return if (value == EmulationMethod.PROXY.name) EmulationMethod.PROXY else EmulationMethod.ACCESSIBILITY_SERVICE
    }

    fun getProxyHost(): String = prefs.getString(SettingsKeys.PROXY_HOST, "") ?: ""

    fun getProxyPort(): Int = (prefs.getString(SettingsKeys.PROXY_PORT, "8787")?.toIntOrNull() ?: 8787)
        .coerceIn(1, 65535)

    fun getProxyToken(): String = prefs.getString(SettingsKeys.PROXY_TOKEN, "") ?: ""

    fun getMouseKeyUp(): Int = prefs.getString(SettingsKeys.MOUSE_KEY_UP, "19")?.toIntOrNull() ?: 19
    fun getMouseKeyDown(): Int = prefs.getString(SettingsKeys.MOUSE_KEY_DOWN, "20")?.toIntOrNull() ?: 20
    fun getMouseKeyLeft(): Int = prefs.getString(SettingsKeys.MOUSE_KEY_LEFT, "21")?.toIntOrNull() ?: 21
    fun getMouseKeyRight(): Int = prefs.getString(SettingsKeys.MOUSE_KEY_RIGHT, "22")?.toIntOrNull() ?: 22
    fun getMouseKeyClick(): Int = prefs.getString(SettingsKeys.MOUSE_KEY_CLICK, "23")?.toIntOrNull() ?: 23

    fun getMouseKeyScrollUp(): Int = prefs.getString(SettingsKeys.MOUSE_KEY_SCROLL_UP, "166")?.toIntOrNull() ?: 166
    fun getMouseKeyScrollDown(): Int = prefs.getString(SettingsKeys.MOUSE_KEY_SCROLL_DOWN, "167")?.toIntOrNull() ?: 167
    fun getMouseKeyScrollLeft(): Int = prefs.getString(SettingsKeys.MOUSE_KEY_SCROLL_LEFT, "89")?.toIntOrNull() ?: 89
    fun getMouseKeyScrollRight(): Int = prefs.getString(SettingsKeys.MOUSE_KEY_SCROLL_RIGHT, "90")?.toIntOrNull() ?: 90

    fun getMouseKeyPinchIn(): Int = prefs.getString(SettingsKeys.MOUSE_KEY_PINCH_IN, "0")?.toIntOrNull() ?: 0
    fun getMouseKeyPinchOut(): Int = prefs.getString(SettingsKeys.MOUSE_KEY_PINCH_OUT, "0")?.toIntOrNull() ?: 0

    fun getMouseSwipeDistancePercent(): Int =
        prefs.getInt(SettingsKeys.MOUSE_SWIPE_DISTANCE_PERCENT, 28).coerceIn(5, 95)

    fun getMouseSwipeDoubleScale(): Float =
        (prefs.getString(SettingsKeys.MOUSE_SWIPE_DOUBLE_SCALE, "2.0")?.toFloatOrNull() ?: 2.0f)
            .coerceIn(0.3f, 3.0f)

    fun getMousePinchDistancePercent(): Int =
        prefs.getInt(SettingsKeys.MOUSE_PINCH_DISTANCE_PERCENT, 28).coerceIn(5, 95)

    fun getMousePinchDoubleScale(): Float =
        (prefs.getString(SettingsKeys.MOUSE_PINCH_DOUBLE_SCALE, "2.0")?.toFloatOrNull() ?: 2.0f)
            .coerceIn(0.3f, 3.0f)

    fun isMouseScrollRepeatLongPress(): Boolean =
        prefs.getBoolean(SettingsKeys.MOUSE_SCROLL_REPEAT_LONGPRESS, true)

    fun getMouseScrollRepeatIntervalMs(): Int =
        prefs.getInt(SettingsKeys.MOUSE_SCROLL_REPEAT_INTERVAL_MS, 120).coerceIn(30, 2000)

    fun getMouseKeyCursorDpadToggle(): Int =
        prefs.getString(SettingsKeys.MOUSE_KEY_CURSOR_DPAD_TOGGLE, "82")?.toIntOrNull() ?: 82

    fun isTouchVisualFeedbackEnabled(): Boolean =
        prefs.getBoolean(SettingsKeys.TOUCH_VISUAL_FEEDBACK_ENABLED, true)

}
