package com.ftvrcm.service

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.view.ViewConfiguration
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

    private val mainHandler = Handler(Looper.getMainLooper())
    private var pendingToggleKeyCode: Int? = null
    private var pendingToggleTriggered: Boolean = false
    private val pendingToggleRunnable = Runnable {
        if (pendingToggleKeyCode != null && !pendingToggleTriggered) {
            pendingToggleTriggered = true
            toggleMode()
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        try {
            settings = SettingsStore(this).also { it.initializeDefaultsIfNeeded() }
            settings.setDebugServiceConnected()
            cursor = CursorOverlay(this)
            gestures = GestureController(this, settings)
            actions = ActionFactory(this)

            mode = settings.getOperationMode()
            if (mode == OperationMode.MOUSE) cursor.show()
        } catch (t: Throwable) {
            try {
                SettingsStore(this).setDebugLastCrash("onServiceConnected", t)
            } catch (_: Throwable) {
                // ignore
            }
            try {
                disableSelf()
            } catch (_: Throwable) {
                // ignore
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // no-op
    }

    override fun onInterrupt() {
        // no-op
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        try {
        // Debug: record key codes even if background monitoring is OFF (so user can diagnose keys).
        if (event.action == KeyEvent.ACTION_DOWN && settings.isDebugShowKeyCodeEnabled()) {
            settings.setDebugLastKey(event.keyCode, KeyEvent.keyCodeToString(event.keyCode))
        }

        if (!settings.isBackgroundMonitoringEnabled()) return false

        val keyCode = event.keyCode

        // 1) Toggle mode (always available)
        val toggleKey = settings.getToggleKeyCode()
        val toggleLongPress = settings.isToggleLongPress()
        val isToggleKey = keyCode == toggleKey || (toggleKey == KeyEvent.KEYCODE_MENU && keyCode == KeyEvent.KEYCODE_SETTINGS)
        if (isToggleKey) {
            if (!toggleLongPress) {
                if (event.action == KeyEvent.ACTION_DOWN) {
                    toggleMode()
                    return true
                }
                return false
            }

            when (event.action) {
                KeyEvent.ACTION_DOWN -> {
                    // If platform reports long-press/repeat, toggle immediately.
                    if (event.isLongPress || event.repeatCount > 0) {
                        clearPendingToggle()
                        toggleMode()
                        return true
                    }

                    // Fallback: schedule our own long-press detection.
                    if (pendingToggleKeyCode == null) {
                        pendingToggleKeyCode = keyCode
                        pendingToggleTriggered = false
                        val timeoutMs = ViewConfiguration.getLongPressTimeout().toLong()
                        mainHandler.postDelayed(pendingToggleRunnable, timeoutMs)
                    }

                    // Allow short press to pass through to the system/app.
                    return false
                }

                KeyEvent.ACTION_UP -> {
                    val wasTriggered = pendingToggleTriggered
                    clearPendingToggle()
                    // If we toggled by long-press, consume the UP to reduce side effects.
                    return wasTriggered
                }
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
        // Acceleration: scale movement based on repeat count (key held down).
        // Formula: base_step * (1 + sqrt(repeatCount)) for smooth acceleration.
        val accel = 1.0 + kotlin.math.sqrt(event.repeatCount.toDouble())
        val scaledStep = (step * accel).toInt()

        return when (keyCode) {
            settings.getMouseKeyUp() -> {
                cursor.moveBy(0, -scaledStep)
                true
            }
            settings.getMouseKeyDown() -> {
                cursor.moveBy(0, scaledStep)
                true
            }
            settings.getMouseKeyLeft() -> {
                cursor.moveBy(-scaledStep, 0)
                true
            }
            settings.getMouseKeyRight() -> {
                cursor.moveBy(scaledStep, 0)
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
        } catch (t: Throwable) {
            try {
                settings.setDebugLastCrash("onKeyEvent", t)
            } catch (_: Throwable) {
                // ignore
            }
            return false
        }
    }

    override fun onDestroy() {
        clearPendingToggle()
        cursor.hide()
        super.onDestroy()
    }

    private fun toggleMode() {
        mode = mode.toggle()
        settings.setOperationMode(mode)
        if (mode == OperationMode.MOUSE) cursor.show() else cursor.hide()
    }

    private fun clearPendingToggle() {
        mainHandler.removeCallbacks(pendingToggleRunnable)
        pendingToggleKeyCode = null
        pendingToggleTriggered = false
    }
}
