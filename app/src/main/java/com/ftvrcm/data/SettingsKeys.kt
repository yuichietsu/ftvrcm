package com.ftvrcm.data

object SettingsKeys {
    const val PREFS_NAME = "ftvrcm_settings"

    const val OPERATION_MODE = "operation_mode" // String: NORMAL|MOUSE

    const val TOGGLE_KEYCODE = "toggle_keycode" // String (ListPreference) / store as Int
    const val TOGGLE_LONGPRESS = "toggle_longpress" // boolean

    const val MOUSE_POINTER_SPEED = "mouse_pointer_speed" // int

    const val EMULATION_METHOD = "emulation_method" // String: ACCESSIBILITY_SERVICE|ADB

    // ADB (used when EMULATION_METHOD=ADB)
    const val ADB_HOST = "adb_host" // String: "auto" | hostname/IP
    const val ADB_PORT = "adb_port" // String (EditTextPreference) / store as Int

    const val MOUSE_KEY_UP = "mouse_key_up" // String/int
    const val MOUSE_KEY_DOWN = "mouse_key_down"
    const val MOUSE_KEY_LEFT = "mouse_key_left"
    const val MOUSE_KEY_RIGHT = "mouse_key_right"
    const val MOUSE_KEY_CLICK = "mouse_key_click"

    const val MOUSE_KEY_SCROLL_UP = "mouse_key_scroll_up"
    const val MOUSE_KEY_SCROLL_DOWN = "mouse_key_scroll_down"
    const val MOUSE_KEY_SCROLL_LEFT = "mouse_key_scroll_left"
    const val MOUSE_KEY_SCROLL_RIGHT = "mouse_key_scroll_right"

    const val MOUSE_KEY_CURSOR_DPAD_TOGGLE = "mouse_key_cursor_dpad_toggle" // String/int

    const val MOUSE_CURSOR_START_POSITION = "mouse_cursor_start_position" // String
    const val MOUSE_CURSOR_LAST_X = "mouse_cursor_last_x" // int
    const val MOUSE_CURSOR_LAST_Y = "mouse_cursor_last_y" // int

    // docs/06
    const val KEY_MAPPING = "key_mapping" // StringSet: {keyCode}:{actionId}

    const val BACKGROUND_MONITORING_ENABLED = "background_monitoring_enabled" // boolean

    // Gesture debug (for TouchTestActivity)
    const val LAST_GESTURE_TYPE = "last_gesture_type" // String
    const val LAST_GESTURE_STATUS = "last_gesture_status" // String
    const val LAST_GESTURE_DETAIL = "last_gesture_detail" // String
    const val LAST_GESTURE_AT_MS = "last_gesture_at_ms" // Long
}
