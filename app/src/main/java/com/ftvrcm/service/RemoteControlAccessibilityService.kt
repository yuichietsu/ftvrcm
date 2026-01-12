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

    private var moveKeyCode: Int? = null
    private var moveDx: Int = 0
    private var moveDy: Int = 0
    private var moveTicks: Int = 0
    private val moveRepeatRunnable = object : Runnable {
        override fun run() {
            if (moveKeyCode == null) return

            // Accelerate smoothly while held.
            moveTicks += 1
            val baseStep = settings.getMousePointerSpeedPx()
            val accel = (1.0 + kotlin.math.sqrt(moveTicks.toDouble()) / 2.0).coerceAtMost(6.0)
            val step = (baseStep * accel).toInt().coerceAtLeast(1)

            cursor.moveBy(moveDx * step, moveDy * step)
            mainHandler.postDelayed(this, MOVE_REPEAT_INTERVAL_MS)
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

        // Stop continuous movement on key up.
        if (event.action == KeyEvent.ACTION_UP) {
            stopMoveRepeat(keyCode)
            return true
        }

        if (event.action != KeyEvent.ACTION_DOWN) return true

        return when (keyCode) {
            settings.getMouseKeyUp() -> {
                startMoveRepeat(keyCode, dx = 0, dy = -1)
                true
            }
            settings.getMouseKeyDown() -> {
                startMoveRepeat(keyCode, dx = 0, dy = 1)
                true
            }
            settings.getMouseKeyLeft() -> {
                startMoveRepeat(keyCode, dx = -1, dy = 0)
                true
            }
            settings.getMouseKeyRight() -> {
                startMoveRepeat(keyCode, dx = 1, dy = 0)
                true
            }
            settings.getMouseKeyClick() -> {
                clearMoveRepeat()
                val c = cursor.center()
                gestures.tap(c.x, c.y)
                true
            }
            settings.getMouseKeyLongClick() -> {
                clearMoveRepeat()
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
        clearMoveRepeat()
        cursor.hide()
        super.onDestroy()
    }

    private fun toggleMode() {
        clearMoveRepeat()
        mode = mode.toggle()
        settings.setOperationMode(mode)
        if (mode == OperationMode.MOUSE) cursor.show() else cursor.hide()
    }

    private fun clearPendingToggle() {
        mainHandler.removeCallbacks(pendingToggleRunnable)
        pendingToggleKeyCode = null
        pendingToggleTriggered = false
    }

    private fun startMoveRepeat(keyCode: Int, dx: Int, dy: Int) {
        if (moveKeyCode == keyCode) return
        clearMoveRepeat()

        moveKeyCode = keyCode
        moveDx = dx
        moveDy = dy
        moveTicks = 0

        // Move immediately once for responsiveness.
        val baseStep = settings.getMousePointerSpeedPx()
        cursor.moveBy(dx * baseStep, dy * baseStep)

        mainHandler.postDelayed(moveRepeatRunnable, MOVE_REPEAT_INITIAL_DELAY_MS)
    }

    private fun stopMoveRepeat(keyCode: Int) {
        if (moveKeyCode == keyCode) {
            clearMoveRepeat()
        }
    }

    private fun clearMoveRepeat() {
        mainHandler.removeCallbacks(moveRepeatRunnable)
        moveKeyCode = null
        moveDx = 0
        moveDy = 0
        moveTicks = 0
    }

    private companion object {
        private const val MOVE_REPEAT_INITIAL_DELAY_MS = 120L
        private const val MOVE_REPEAT_INTERVAL_MS = 33L
    }
}
