package com.ftvrcm.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject
import androidx.core.content.edit
import com.ftvrcm.domain.OperationMode

class SettingsStore(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(SettingsKeys.PREFS_NAME, Context.MODE_PRIVATE)

    fun initializeDefaultsIfNeeded() {
        val version = prefs.getInt(SettingsKeys.SETTINGS_VERSION, 0)
        if (version != 0) return

        prefs.edit {
            putInt(SettingsKeys.SETTINGS_VERSION, SettingsKeys.SETTINGS_VERSION_CURRENT)
            putString(SettingsKeys.OPERATION_MODE, OperationMode.NORMAL.name)

            putString(SettingsKeys.TOGGLE_KEYCODE, "4") // BACK
            putBoolean(SettingsKeys.TOGGLE_LONGPRESS, true)

            putInt(SettingsKeys.MOUSE_POINTER_SPEED, 10)

            putString(SettingsKeys.MOUSE_KEY_UP, "19")
            putString(SettingsKeys.MOUSE_KEY_DOWN, "20")
            putString(SettingsKeys.MOUSE_KEY_LEFT, "21")
            putString(SettingsKeys.MOUSE_KEY_RIGHT, "22")
            putString(SettingsKeys.MOUSE_KEY_CLICK, "23")
            putString(SettingsKeys.MOUSE_KEY_LONGCLICK, "82")

            // Swipe keys (default: CH+/CH- and REW/FF)
            putString(SettingsKeys.MOUSE_KEY_SWIPE_UP, "166")
            putString(SettingsKeys.MOUSE_KEY_SWIPE_DOWN, "167")
            putString(SettingsKeys.MOUSE_KEY_SWIPE_LEFT, "89")
            putString(SettingsKeys.MOUSE_KEY_SWIPE_RIGHT, "90")

            putStringSet(
                SettingsKeys.KEY_MAPPING,
                setOf(
                    "19:mouse_up",
                    "20:mouse_down",
                    "21:mouse_left",
                    "22:mouse_right",
                    "23:mouse_click",
                    "166:mouse_swipe_up",
                    "167:mouse_swipe_down",
                    "89:mouse_swipe_left",
                    "90:mouse_swipe_right",
                ),
            )

            putStringSet(SettingsKeys.BUTTON_ACTIONS, emptySet())

            putString(SettingsKeys.ACTION_KEYCODE, "4")
            putString(SettingsKeys.ACTION_TYPE, "none")
            putString(SettingsKeys.ACTION_PARAM, "")

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
        set += "${getMouseKeyLongClick()}:mouse_long_click"
        set += "${getMouseKeySwipeUp()}:mouse_swipe_up"
        set += "${getMouseKeySwipeDown()}:mouse_swipe_down"
        set += "${getMouseKeySwipeLeft()}:mouse_swipe_left"
        set += "${getMouseKeySwipeRight()}:mouse_swipe_right"
        prefs.edit { putStringSet(SettingsKeys.KEY_MAPPING, set) }
    }

    fun getButtonActions(): Map<Int, String> {
        val set = prefs.getStringSet(SettingsKeys.BUTTON_ACTIONS, emptySet()) ?: emptySet()
        val map = mutableMapOf<Int, String>()
        for (raw in set) {
            try {
                val json = JSONObject(raw)
                val keyCode = json.optInt("keyCode", -1)
                val actionId = json.optString("actionId", "")
                if (keyCode > 0 && actionId.isNotBlank()) {
                    map[keyCode] = actionId
                }
            } catch (_: Exception) {
                // ignore broken entry
            }
        }
        return map
    }

    fun upsertButtonActionFromUi() {
        val keyCode = getActionKeyCode()
        val type = getActionType()
        val param = getActionParam()
        upsertButtonAction(keyCode, type, param)
    }

    fun upsertButtonAction(keyCode: Int, type: String, param: String) {
        val actionId = when (type) {
            "none" -> "none"
            "launch_app" -> "launch_app_${param.trim()}"
            "open_url" -> "open_url_${param.trim()}"
            "volume_up" -> "adjust_volume_1"
            "volume_down" -> "adjust_volume_-1"
            else -> "none"
        }

        val current = prefs.getStringSet(SettingsKeys.BUTTON_ACTIONS, emptySet())?.toMutableSet() ?: mutableSetOf()
        val filtered = current.filterNot {
            try {
                JSONObject(it).optInt("keyCode", -1) == keyCode
            } catch (_: Exception) {
                false
            }
        }.toMutableSet()

        if (actionId != "none") {
            val json = JSONObject().apply {
                put("keyCode", keyCode)
                put("actionId", actionId)
            }
            filtered.add(json.toString())
        }

        prefs.edit { putStringSet(SettingsKeys.BUTTON_ACTIONS, filtered) }
    }

    fun getOperationMode(): OperationMode {
        val value = prefs.getString(SettingsKeys.OPERATION_MODE, OperationMode.NORMAL.name)
        return if (value == OperationMode.MOUSE.name) OperationMode.MOUSE else OperationMode.NORMAL
    }

    fun setOperationMode(mode: OperationMode) {
        prefs.edit { putString(SettingsKeys.OPERATION_MODE, mode.name) }
    }

    fun getToggleKeyCode(): Int = prefs.getString(SettingsKeys.TOGGLE_KEYCODE, "4")?.toIntOrNull() ?: 4
    fun isToggleLongPress(): Boolean = prefs.getBoolean(SettingsKeys.TOGGLE_LONGPRESS, true)

    fun getMousePointerSpeedPx(): Int = prefs.getInt(SettingsKeys.MOUSE_POINTER_SPEED, 10).coerceIn(1, 200)

    fun getMouseKeyUp(): Int = prefs.getString(SettingsKeys.MOUSE_KEY_UP, "19")?.toIntOrNull() ?: 19
    fun getMouseKeyDown(): Int = prefs.getString(SettingsKeys.MOUSE_KEY_DOWN, "20")?.toIntOrNull() ?: 20
    fun getMouseKeyLeft(): Int = prefs.getString(SettingsKeys.MOUSE_KEY_LEFT, "21")?.toIntOrNull() ?: 21
    fun getMouseKeyRight(): Int = prefs.getString(SettingsKeys.MOUSE_KEY_RIGHT, "22")?.toIntOrNull() ?: 22
    fun getMouseKeyClick(): Int = prefs.getString(SettingsKeys.MOUSE_KEY_CLICK, "23")?.toIntOrNull() ?: 23
    fun getMouseKeyLongClick(): Int = prefs.getString(SettingsKeys.MOUSE_KEY_LONGCLICK, "82")?.toIntOrNull() ?: 82

    fun getMouseKeySwipeUp(): Int = prefs.getString(SettingsKeys.MOUSE_KEY_SWIPE_UP, "166")?.toIntOrNull() ?: 166
    fun getMouseKeySwipeDown(): Int = prefs.getString(SettingsKeys.MOUSE_KEY_SWIPE_DOWN, "167")?.toIntOrNull() ?: 167
    fun getMouseKeySwipeLeft(): Int = prefs.getString(SettingsKeys.MOUSE_KEY_SWIPE_LEFT, "89")?.toIntOrNull() ?: 89
    fun getMouseKeySwipeRight(): Int = prefs.getString(SettingsKeys.MOUSE_KEY_SWIPE_RIGHT, "90")?.toIntOrNull() ?: 90

    fun getActionKeyCode(): Int = prefs.getString(SettingsKeys.ACTION_KEYCODE, "4")?.toIntOrNull() ?: 4
    fun getActionType(): String = prefs.getString(SettingsKeys.ACTION_TYPE, "none") ?: "none"
    fun getActionParam(): String = prefs.getString(SettingsKeys.ACTION_PARAM, "") ?: ""

    fun isBackgroundMonitoringEnabled(): Boolean = prefs.getBoolean(SettingsKeys.BACKGROUND_MONITORING_ENABLED, true)
}
