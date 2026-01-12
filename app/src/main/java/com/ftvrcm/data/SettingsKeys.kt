package com.ftvrcm.data

object SettingsKeys {
    const val PREFS_NAME = "ftvrcm_settings"

    const val SETTINGS_VERSION = "settings_version"
    const val SETTINGS_VERSION_CURRENT = 1

    const val OPERATION_MODE = "operation_mode" // String: NORMAL|MOUSE

    const val TOGGLE_KEYCODE = "toggle_keycode" // String (ListPreference) / store as Int
    const val TOGGLE_LONGPRESS = "toggle_longpress" // boolean

    const val MOUSE_POINTER_SPEED = "mouse_pointer_speed" // int

    const val MOUSE_KEY_UP = "mouse_key_up" // String/int
    const val MOUSE_KEY_DOWN = "mouse_key_down"
    const val MOUSE_KEY_LEFT = "mouse_key_left"
    const val MOUSE_KEY_RIGHT = "mouse_key_right"
    const val MOUSE_KEY_CLICK = "mouse_key_click"
    const val MOUSE_KEY_LONGCLICK = "mouse_key_longclick"

    const val MOUSE_KEY_SWIPE_UP = "mouse_key_swipe_up"
    const val MOUSE_KEY_SWIPE_DOWN = "mouse_key_swipe_down"
    const val MOUSE_KEY_SWIPE_LEFT = "mouse_key_swipe_left"
    const val MOUSE_KEY_SWIPE_RIGHT = "mouse_key_swipe_right"

    const val MOUSE_CURSOR_START_POSITION = "mouse_cursor_start_position" // String
    const val MOUSE_CURSOR_LAST_X = "mouse_cursor_last_x" // int
    const val MOUSE_CURSOR_LAST_Y = "mouse_cursor_last_y" // int

    // docs/06
    const val KEY_MAPPING = "key_mapping" // StringSet: {keyCode}:{actionId}
    const val BUTTON_ACTIONS = "button_actions" // StringSet: JSON {keyCode, actionId}

    const val ACTION_KEYCODE = "action_keycode" // String/int
    const val ACTION_TYPE = "action_type" // String
    const val ACTION_PARAM = "action_param" // String

    const val BACKGROUND_MONITORING_ENABLED = "background_monitoring_enabled" // boolean
}
