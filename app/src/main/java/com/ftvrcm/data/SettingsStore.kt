package com.ftvrcm.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.ftvrcm.domain.EmulationMethod
import com.ftvrcm.domain.OperationMode

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

            putString(SettingsKeys.TOGGLE_KEYCODE, "85") // PLAY/PAUSE (Avoid Fire TV BACK long-press conflicts)
            putBoolean(SettingsKeys.TOGGLE_LONGPRESS, true)

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

            // Toggle cursor/dpad behavior (default: PLAY/PAUSE)
            putString(SettingsKeys.MOUSE_KEY_CURSOR_DPAD_TOGGLE, "85")

            // Cursor start position in mouse mode
            putString(SettingsKeys.MOUSE_CURSOR_START_POSITION, "center")

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

            putBoolean(SettingsKeys.BACKGROUND_MONITORING_ENABLED, true)
        }
    }

    fun getKeyMappingSet(): Set<String> = prefs.getStringSet(SettingsKeys.KEY_MAPPING, emptySet()) ?: emptySet()

    fun upsertMouseKeyMapping() {
        val set = mutableSetOf<String>()
        set += "${getMouseKeyUp()}:mouse_up"
        set += "${getMouseKeyDown()}:mouse_down"
        set += "${getMouseKeyLeft()}:mouse_left"
        set += "${getMouseKeyRight()}:mouse_right"
        set += "${getMouseKeyClick()}:mouse_click"
        set += "${getMouseKeyScrollUp()}:mouse_scroll_up"
        set += "${getMouseKeyScrollDown()}:mouse_scroll_down"
        set += "${getMouseKeyScrollLeft()}:mouse_scroll_left"
        set += "${getMouseKeyScrollRight()}:mouse_scroll_right"
        prefs.edit { putStringSet(SettingsKeys.KEY_MAPPING, set) }
    }

    fun getOperationMode(): OperationMode {
        val value = prefs.getString(SettingsKeys.OPERATION_MODE, OperationMode.NORMAL.name)
        return if (value == OperationMode.MOUSE.name) OperationMode.MOUSE else OperationMode.NORMAL
    }

    fun setOperationMode(mode: OperationMode) {
        prefs.edit { putString(SettingsKeys.OPERATION_MODE, mode.name) }
    }

    fun getToggleKeyCode(): Int = prefs.getString(SettingsKeys.TOGGLE_KEYCODE, "85")?.toIntOrNull() ?: 85
    fun isToggleLongPress(): Boolean = prefs.getBoolean(SettingsKeys.TOGGLE_LONGPRESS, true)

    fun getMousePointerSpeedPx(): Int = prefs.getInt(SettingsKeys.MOUSE_POINTER_SPEED, 10).coerceIn(1, 200)

    fun getEmulationMethod(): EmulationMethod {
        val value = prefs.getString(SettingsKeys.EMULATION_METHOD, EmulationMethod.ACCESSIBILITY_SERVICE.name)
        return if (value == EmulationMethod.PROXY.name || value == "ADB") {
            EmulationMethod.PROXY
        } else {
            EmulationMethod.ACCESSIBILITY_SERVICE
        }
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

    fun getMouseKeyCursorDpadToggle(): Int =
        prefs.getString(SettingsKeys.MOUSE_KEY_CURSOR_DPAD_TOGGLE, "85")?.toIntOrNull() ?: 85

    // Always enabled: this app's purpose is global key monitoring.
    fun isBackgroundMonitoringEnabled(): Boolean = true
}
