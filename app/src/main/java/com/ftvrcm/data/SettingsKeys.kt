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

    // docs/06
    const val KEY_MAPPING = "key_mapping" // StringSet: {keyCode}:{actionId}
    const val BUTTON_ACTIONS = "button_actions" // StringSet: JSON {keyCode, actionId}

    const val ACTION_KEYCODE = "action_keycode" // String/int
    const val ACTION_TYPE = "action_type" // String
    const val ACTION_PARAM = "action_param" // String

    const val BACKGROUND_MONITORING_ENABLED = "background_monitoring_enabled" // boolean

    // Experimental: toggle via media key double-press
    const val MEDIA_TOGGLE_ENABLED = "media_toggle_enabled" // boolean

    // Media toggle internal state (elapsedRealtime)
    const val MEDIA_LAST_PLAYPAUSE_DOWN_AT = "media_last_playpause_down_at" // long

    // Debug
    const val DEBUG_SHOW_KEYCODE = "debug_show_keycode" // boolean
    const val DEBUG_LAST_KEYCODE = "debug_last_keycode" // int
    const val DEBUG_LAST_KEYNAME = "debug_last_keyname" // String
    const val DEBUG_LAST_KEY_AT = "debug_last_key_at" // long (elapsedRealtime)
    const val DEBUG_SERVICE_CONNECTED_AT = "debug_service_connected_at" // long (elapsedRealtime)

    const val DEBUG_LAST_CRASH_AT = "debug_last_crash_at" // long (elapsedRealtime)
    const val DEBUG_LAST_CRASH_STAGE = "debug_last_crash_stage" // String
    const val DEBUG_LAST_CRASH_TYPE = "debug_last_crash_type" // String
    const val DEBUG_LAST_CRASH_MESSAGE = "debug_last_crash_message" // String
    const val DEBUG_LAST_CRASH_STACK = "debug_last_crash_stack" // String

    const val DEBUG_LAST_MEDIA_EVENT = "debug_last_media_event" // String
    const val DEBUG_LAST_MEDIA_EVENT_AT = "debug_last_media_event_at" // long

    const val DEBUG_LAST_GESTURE_EVENT = "debug_last_gesture_event" // String
    const val DEBUG_LAST_GESTURE_EVENT_AT = "debug_last_gesture_event_at" // long
}
