package com.ftvrcm.service

import android.accessibilityservice.AccessibilityService
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import com.ftvrcm.action.ActionFactory
import com.ftvrcm.data.SettingsStore
import com.ftvrcm.domain.OperationMode
import com.ftvrcm.mouse.CursorOverlay
import com.ftvrcm.mouse.GestureController

class RemoteControlAccessibilityService : AccessibilityService() {

    private lateinit var settings: SettingsStore
    private lateinit var cursor: CursorOverlay
    private lateinit var gestures: GestureController
    private lateinit var actions: ActionFactory

    private var mode: OperationMode = OperationMode.NORMAL

    override fun onServiceConnected() {
        super.onServiceConnected()
        settings = SettingsStore(this).also { it.initializeDefaultsIfNeeded() }
        cursor = CursorOverlay(this)
        gestures = GestureController(this)
        actions = ActionFactory(this)

        mode = settings.getOperationMode()
        if (mode == OperationMode.MOUSE) cursor.show()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // no-op
    }

    override fun onInterrupt() {
        // no-op
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (!settings.isBackgroundMonitoringEnabled()) return false

        val keyCode = event.keyCode

        // 1) Toggle mode (always available)
        val toggleKey = settings.getToggleKeyCode()
        val toggleLongPress = settings.isToggleLongPress()
        if (keyCode == toggleKey && event.action == KeyEvent.ACTION_DOWN) {
            val shouldToggle = if (toggleLongPress) {
                event.isLongPress || event.repeatCount > 0
            } else {
                true
            }
            if (shouldToggle) {
                mode = mode.toggle()
                settings.setOperationMode(mode)
                if (mode == OperationMode.MOUSE) cursor.show() else cursor.hide()
                return true
            }
        }

        // 2) Custom action (available in both modes)
        if (event.action == KeyEvent.ACTION_DOWN) {
            val mapped = settings.getButtonActions()[keyCode]
            if (mapped != null) {
                val action = actions.createFromActionId(mapped)
                action?.execute()
                return action != null
            }

            // Backward-compatible: single mapping in UI
            if (keyCode == settings.getActionKeyCode()) {
                val action = actions.create(settings.getActionType(), settings.getActionParam())
                action?.execute()
                return action != null
            }
        }

        // 3) Mouse mode key mapping
        if (mode != OperationMode.MOUSE) return false

        if (event.action != KeyEvent.ACTION_DOWN) return true

        val step = settings.getMousePointerSpeedPx()
        return when (keyCode) {
            settings.getMouseKeyUp() -> {
                cursor.moveBy(0, -step)
                true
            }
            settings.getMouseKeyDown() -> {
                cursor.moveBy(0, step)
                true
            }
            settings.getMouseKeyLeft() -> {
                cursor.moveBy(-step, 0)
                true
            }
            settings.getMouseKeyRight() -> {
                cursor.moveBy(step, 0)
                true
            }
            settings.getMouseKeyClick() -> {
                val c = cursor.center()
                gestures.tap(c.x, c.y)
                true
            }
            settings.getMouseKeyLongClick() -> {
                val c = cursor.center()
                gestures.longPress(c.x, c.y)
                true
            }
            else -> false
        }
    }

    override fun onDestroy() {
        cursor.hide()
        super.onDestroy()
    }
}
