package com.ftvrcm.data

object SettingsKeys {
    const val PREFS_NAME = "ftvrcm_settings"

    const val OPERATION_MODE = "operation_mode" // String: NORMAL|MOUSE

    const val TOGGLE_KEYCODE = "toggle_keycode" // String / store as Int or -scanCode
    const val TOGGLE_LONGPRESS = "toggle_longpress" // boolean (legacy)
    const val TOGGLE_TRIGGER = "toggle_trigger" // String: LONG_PRESS|DOUBLE_TAP|SINGLE_TAP

    const val MOUSE_POINTER_SPEED = "mouse_pointer_speed" // int

    const val EMULATION_METHOD = "emulation_method" // String: ACCESSIBILITY_SERVICE|PROXY

    // Proxy: FireTV app -> PC proxy -> adb -> FireTV
    const val PROXY_HOST = "proxy_host" // String: hostname/IP
    const val PROXY_PORT = "proxy_port" // String (EditTextPreference) / store as Int
    const val PROXY_TOKEN = "proxy_token" // String

    const val MOUSE_KEY_UP = "mouse_key_up" // String/int
    const val MOUSE_KEY_DOWN = "mouse_key_down"
    const val MOUSE_KEY_LEFT = "mouse_key_left"
    const val MOUSE_KEY_RIGHT = "mouse_key_right"
    const val MOUSE_KEY_CLICK = "mouse_key_click"

    const val MOUSE_KEY_SCROLL_UP = "mouse_key_scroll_up"
    const val MOUSE_KEY_SCROLL_DOWN = "mouse_key_scroll_down"
    const val MOUSE_KEY_SCROLL_LEFT = "mouse_key_scroll_left"
    const val MOUSE_KEY_SCROLL_RIGHT = "mouse_key_scroll_right"

    const val MOUSE_KEY_PINCH_IN = "mouse_key_pinch_in"
    const val MOUSE_KEY_PINCH_OUT = "mouse_key_pinch_out"

    // Swipe/scroll tuning
    const val MOUSE_SWIPE_DISTANCE_PERCENT = "mouse_swipe_distance_percent" // int (SeekBarPreference)
    const val MOUSE_SWIPE_DOUBLE_SCALE = "mouse_swipe_double_scale" // String (ListPreference) / Float
    const val MOUSE_PINCH_DISTANCE_PERCENT = "mouse_pinch_distance_percent" // int (SeekBarPreference)
    const val MOUSE_PINCH_DOUBLE_SCALE = "mouse_pinch_double_scale" // String (ListPreference) / Float
    const val MOUSE_SCROLL_REPEAT_LONGPRESS = "mouse_scroll_repeat_longpress" // boolean
    const val MOUSE_SCROLL_REPEAT_INTERVAL_MS = "mouse_scroll_repeat_interval_ms" // int (SeekBarPreference)

    const val MOUSE_KEY_CURSOR_DPAD_TOGGLE = "mouse_key_cursor_dpad_toggle" // String/int

    const val SCREEN_ROTATE_KEY = "screen_rotate_key" // String/int

    const val MOUSE_CURSOR_START_POSITION = "mouse_cursor_start_position" // String
    const val MOUSE_CURSOR_LAST_X = "mouse_cursor_last_x" // int
    const val MOUSE_CURSOR_LAST_Y = "mouse_cursor_last_y" // int

    // Visual feedback (cursor overlay)
    const val TOUCH_VISUAL_FEEDBACK_ENABLED = "touch_visual_feedback_enabled" // boolean

    // docs/06
    const val KEY_MAPPING = "key_mapping" // StringSet: {keyCode}:{actionId}

    // Gesture debug (for TouchTestActivity)
    const val LAST_GESTURE_TYPE = "last_gesture_type" // String
    const val LAST_GESTURE_STATUS = "last_gesture_status" // String
    const val LAST_GESTURE_DETAIL = "last_gesture_detail" // String
    const val LAST_GESTURE_AT_MS = "last_gesture_at_ms" // Long
}
