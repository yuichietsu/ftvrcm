package com.ftvrcm.data

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import org.json.JSONObject
import androidx.core.content.edit
import com.ftvrcm.domain.OperationMode
import java.io.PrintWriter
import java.io.StringWriter

class SettingsStore(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(SettingsKeys.PREFS_NAME, Context.MODE_PRIVATE)

    fun initializeDefaultsIfNeeded() {
        val version = prefs.getInt(SettingsKeys.SETTINGS_VERSION, 0)
        if (version != 0) return

        prefs.edit {
            putInt(SettingsKeys.SETTINGS_VERSION, SettingsKeys.SETTINGS_VERSION_CURRENT)
            putString(SettingsKeys.OPERATION_MODE, OperationMode.NORMAL.name)

            putString(SettingsKeys.TOGGLE_KEYCODE, "82") // MENU
            putBoolean(SettingsKeys.TOGGLE_LONGPRESS, true)

            putInt(SettingsKeys.MOUSE_POINTER_SPEED, 10)

            putString(SettingsKeys.MOUSE_KEY_UP, "19")
            putString(SettingsKeys.MOUSE_KEY_DOWN, "20")
            putString(SettingsKeys.MOUSE_KEY_LEFT, "21")
            putString(SettingsKeys.MOUSE_KEY_RIGHT, "22")
            putString(SettingsKeys.MOUSE_KEY_CLICK, "23")
            putString(SettingsKeys.MOUSE_KEY_LONGCLICK, "4")

            putStringSet(
                SettingsKeys.KEY_MAPPING,
                setOf(
                    "19:mouse_up",
                    "20:mouse_down",
                    "21:mouse_left",
                    "22:mouse_right",
                    "23:mouse_click",
                ),
            )

            putStringSet(SettingsKeys.BUTTON_ACTIONS, emptySet())

            putString(SettingsKeys.ACTION_KEYCODE, "4")
            putString(SettingsKeys.ACTION_TYPE, "none")
            putString(SettingsKeys.ACTION_PARAM, "")

            putBoolean(SettingsKeys.BACKGROUND_MONITORING_ENABLED, true)

            putBoolean(SettingsKeys.MEDIA_TOGGLE_ENABLED, false)

            putBoolean(SettingsKeys.DEBUG_SHOW_KEYCODE, false)
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

    fun getToggleKeyCode(): Int = prefs.getString(SettingsKeys.TOGGLE_KEYCODE, "82")?.toIntOrNull() ?: 82
    fun isToggleLongPress(): Boolean = prefs.getBoolean(SettingsKeys.TOGGLE_LONGPRESS, true)

    fun getMousePointerSpeedPx(): Int = prefs.getInt(SettingsKeys.MOUSE_POINTER_SPEED, 10).coerceIn(1, 200)

    fun getMouseKeyUp(): Int = prefs.getString(SettingsKeys.MOUSE_KEY_UP, "19")?.toIntOrNull() ?: 19
    fun getMouseKeyDown(): Int = prefs.getString(SettingsKeys.MOUSE_KEY_DOWN, "20")?.toIntOrNull() ?: 20
    fun getMouseKeyLeft(): Int = prefs.getString(SettingsKeys.MOUSE_KEY_LEFT, "21")?.toIntOrNull() ?: 21
    fun getMouseKeyRight(): Int = prefs.getString(SettingsKeys.MOUSE_KEY_RIGHT, "22")?.toIntOrNull() ?: 22
    fun getMouseKeyClick(): Int = prefs.getString(SettingsKeys.MOUSE_KEY_CLICK, "23")?.toIntOrNull() ?: 23
    fun getMouseKeyLongClick(): Int = prefs.getString(SettingsKeys.MOUSE_KEY_LONGCLICK, "4")?.toIntOrNull() ?: 4

    fun getActionKeyCode(): Int = prefs.getString(SettingsKeys.ACTION_KEYCODE, "4")?.toIntOrNull() ?: 4
    fun getActionType(): String = prefs.getString(SettingsKeys.ACTION_TYPE, "none") ?: "none"
    fun getActionParam(): String = prefs.getString(SettingsKeys.ACTION_PARAM, "") ?: ""

    fun isBackgroundMonitoringEnabled(): Boolean = prefs.getBoolean(SettingsKeys.BACKGROUND_MONITORING_ENABLED, true)

    fun isMediaToggleEnabled(): Boolean = prefs.getBoolean(SettingsKeys.MEDIA_TOGGLE_ENABLED, false)

    fun getMediaLastPlayPauseDownAtElapsed(): Long =
        prefs.getLong(SettingsKeys.MEDIA_LAST_PLAYPAUSE_DOWN_AT, 0L)

    fun setMediaLastPlayPauseDownAtElapsed(value: Long) {
        prefs.edit { putLong(SettingsKeys.MEDIA_LAST_PLAYPAUSE_DOWN_AT, value) }
    }

    fun setDebugLastMediaEvent(summary: String) {
        prefs.edit {
            putString(SettingsKeys.DEBUG_LAST_MEDIA_EVENT, summary)
            putLong(SettingsKeys.DEBUG_LAST_MEDIA_EVENT_AT, SystemClock.elapsedRealtime())
        }
    }

    data class DebugLastMediaEvent(
        val summary: String,
        val atElapsedRealtimeMs: Long,
    )

    fun getDebugLastMediaEvent(): DebugLastMediaEvent? {
        val at = prefs.getLong(SettingsKeys.DEBUG_LAST_MEDIA_EVENT_AT, 0L)
        if (at <= 0L) return null
        val summary = prefs.getString(SettingsKeys.DEBUG_LAST_MEDIA_EVENT, "") ?: ""
        return DebugLastMediaEvent(summary = summary, atElapsedRealtimeMs = at)
    }

    fun setDebugLastGestureEvent(summary: String) {
        prefs.edit {
            putString(SettingsKeys.DEBUG_LAST_GESTURE_EVENT, summary)
            putLong(SettingsKeys.DEBUG_LAST_GESTURE_EVENT_AT, SystemClock.elapsedRealtime())
        }
    }

    data class DebugLastGestureEvent(
        val summary: String,
        val atElapsedRealtimeMs: Long,
    )

    fun getDebugLastGestureEvent(): DebugLastGestureEvent? {
        val at = prefs.getLong(SettingsKeys.DEBUG_LAST_GESTURE_EVENT_AT, 0L)
        if (at <= 0L) return null
        val summary = prefs.getString(SettingsKeys.DEBUG_LAST_GESTURE_EVENT, "") ?: ""
        return DebugLastGestureEvent(summary = summary, atElapsedRealtimeMs = at)
    }

    fun isDebugShowKeyCodeEnabled(): Boolean = prefs.getBoolean(SettingsKeys.DEBUG_SHOW_KEYCODE, false)

    fun setDebugLastKey(keyCode: Int, keyName: String) {
        prefs.edit {
            putInt(SettingsKeys.DEBUG_LAST_KEYCODE, keyCode)
            putString(SettingsKeys.DEBUG_LAST_KEYNAME, keyName)
            putLong(SettingsKeys.DEBUG_LAST_KEY_AT, SystemClock.elapsedRealtime())
        }
    }

    fun setDebugServiceConnected() {
        prefs.edit {
            putLong(SettingsKeys.DEBUG_SERVICE_CONNECTED_AT, SystemClock.elapsedRealtime())
        }
    }

    fun setDebugLastCrash(stage: String, throwable: Throwable) {
        val type = throwable::class.java.name
        val message = throwable.message ?: ""
        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw))
        val stack = sw.toString().take(16_000)

        prefs.edit {
            putLong(SettingsKeys.DEBUG_LAST_CRASH_AT, SystemClock.elapsedRealtime())
            putString(SettingsKeys.DEBUG_LAST_CRASH_STAGE, stage)
            putString(SettingsKeys.DEBUG_LAST_CRASH_TYPE, type)
            putString(SettingsKeys.DEBUG_LAST_CRASH_MESSAGE, message)
            putString(SettingsKeys.DEBUG_LAST_CRASH_STACK, stack)
        }
    }

    data class DebugLastKey(
        val keyCode: Int,
        val keyName: String,
        val atElapsedRealtimeMs: Long,
    )

    fun getDebugLastKey(): DebugLastKey? {
        val keyCode = prefs.getInt(SettingsKeys.DEBUG_LAST_KEYCODE, -1)
        if (keyCode <= 0) return null
        val keyName = prefs.getString(SettingsKeys.DEBUG_LAST_KEYNAME, "") ?: ""
        val at = prefs.getLong(SettingsKeys.DEBUG_LAST_KEY_AT, 0L)
        return DebugLastKey(keyCode = keyCode, keyName = keyName, atElapsedRealtimeMs = at)
    }

    fun getDebugServiceConnectedAtElapsed(): Long =
        prefs.getLong(SettingsKeys.DEBUG_SERVICE_CONNECTED_AT, 0L)

    data class DebugLastCrash(
        val stage: String,
        val type: String,
        val message: String,
        val stack: String,
        val atElapsedRealtimeMs: Long,
    )

    fun getDebugLastCrash(): DebugLastCrash? {
        val at = prefs.getLong(SettingsKeys.DEBUG_LAST_CRASH_AT, 0L)
        if (at <= 0L) return null
        val stage = prefs.getString(SettingsKeys.DEBUG_LAST_CRASH_STAGE, "") ?: ""
        val type = prefs.getString(SettingsKeys.DEBUG_LAST_CRASH_TYPE, "") ?: ""
        val message = prefs.getString(SettingsKeys.DEBUG_LAST_CRASH_MESSAGE, "") ?: ""
        val stack = prefs.getString(SettingsKeys.DEBUG_LAST_CRASH_STACK, "") ?: ""
        return DebugLastCrash(stage = stage, type = type, message = message, stack = stack, atElapsedRealtimeMs = at)
    }
}
