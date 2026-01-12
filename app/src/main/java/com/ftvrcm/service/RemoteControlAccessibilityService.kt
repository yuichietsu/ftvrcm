package com.ftvrcm.service

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.ViewConfiguration
import android.view.KeyEvent
import android.graphics.Rect
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
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

    private var lastCursorX: Int = 0
    private var lastCursorY: Int = 0

    private var tapKeyIsDown: Boolean = false
    private var tapKeyLongPressTriggered: Boolean = false

    private var scrollSelectKeyIsDown: Boolean = false
    private var scrollSelectKeyLongPressTriggered: Boolean = false
    private var scrollSelectKeyDirection: Int = 0

    private var pendingCursorMoveAfterFocusChange: Boolean = false
    private var pendingCursorMoveDeadlineMs: Long = 0L

    private val scrollSelectKeyLongPressRunnable = Runnable {
        if (mode != OperationMode.MOUSE) return@Runnable
        if (!scrollSelectKeyIsDown) return@Runnable
        if (scrollSelectKeyLongPressTriggered) return@Runnable

        scrollSelectKeyLongPressTriggered = true
        clearMoveRepeat()

        // Long press: first move focus (DPAD), then move cursor to the newly focused control.
        pendingCursorMoveAfterFocusChange = true
        pendingCursorMoveDeadlineMs = SystemClock.uptimeMillis() + 800L

        when (scrollSelectKeyDirection) {
            0 -> gestures.dpadUp()
            1 -> gestures.dpadDown()
            2 -> gestures.dpadLeft()
            3 -> gestures.dpadRight()
        }
    }

    private val tapKeyLongPressRunnable = Runnable {
        if (mode != OperationMode.MOUSE) return@Runnable
        if (!tapKeyIsDown) return@Runnable
        if (tapKeyLongPressTriggered) return@Runnable

        tapKeyLongPressTriggered = true
        clearMoveRepeat()
        val c = cursor.center()
        gestures.longPress(c.x, c.y)
    }

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
            val p = cursor.position()
            lastCursorX = p.x
            lastCursorY = p.y
            mainHandler.postDelayed(this, MOVE_REPEAT_INTERVAL_MS)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        try {
            settings = SettingsStore(this).also { it.initializeDefaultsIfNeeded() }
            cursor = CursorOverlay(this)
            gestures = GestureController(this)
            actions = ActionFactory(this)

            mode = settings.getOperationMode()
            if (mode == OperationMode.MOUSE) {
                applyCursorStartPositionIfNeeded()
                cursor.show()
                val p = cursor.position()
                lastCursorX = p.x
                lastCursorY = p.y
            }
        } catch (t: Throwable) {
            try {
                disableSelf()
            } catch (_: Throwable) {
                // ignore
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!pendingCursorMoveAfterFocusChange) return
        if (mode != OperationMode.MOUSE) {
            pendingCursorMoveAfterFocusChange = false
            return
        }

        val now = SystemClock.uptimeMillis()
        if (now > pendingCursorMoveDeadlineMs) {
            pendingCursorMoveAfterFocusChange = false
            return
        }

        val e = event ?: return
        val t = e.eventType
        if (t != AccessibilityEvent.TYPE_VIEW_FOCUSED && t != AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED) return

        val src = e.source
        val moved = try {
            if (src != null) {
                moveCursorToNode(src)
            } else {
                moveCursorToFocusedControl()
            }
        } finally {
            try {
                src?.recycle()
            } catch (_: Exception) {
            }
        }

        if (moved) {
            pendingCursorMoveAfterFocusChange = false
        }
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

                    // Consume DOWN to avoid delivering only DOWN when long-press toggles.
                    return true
                }

                KeyEvent.ACTION_UP -> {
                    val wasTriggered = pendingToggleTriggered
                    clearPendingToggle()

                    // Short press: preserve BACK behavior via accessibility global action.
                    if (!wasTriggered && keyCode == KeyEvent.KEYCODE_BACK) {
                        performGlobalAction(GLOBAL_ACTION_BACK)
                    }

                    return true
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

        val mouseKeyUp = settings.getMouseKeyUp()
        val mouseKeyDown = settings.getMouseKeyDown()
        val mouseKeyLeft = settings.getMouseKeyLeft()
        val mouseKeyRight = settings.getMouseKeyRight()
        val mouseKeyClick = settings.getMouseKeyClick()
        val mouseKeyScrollUp = settings.getMouseKeyScrollUp()
        val mouseKeyScrollDown = settings.getMouseKeyScrollDown()
        val mouseKeyScrollLeft = settings.getMouseKeyScrollLeft()
        val mouseKeyScrollRight = settings.getMouseKeyScrollRight()

        val isHandledMouseKey = keyCode == mouseKeyUp ||
            keyCode == mouseKeyDown ||
            keyCode == mouseKeyLeft ||
            keyCode == mouseKeyRight ||
            keyCode == mouseKeyClick ||
            keyCode == mouseKeyScrollUp ||
            keyCode == mouseKeyScrollDown ||
            keyCode == mouseKeyScrollLeft ||
            keyCode == mouseKeyScrollRight

        // Tap key handling (single tap / long tap)
        if (keyCode == mouseKeyClick) {
            when (event.action) {
                KeyEvent.ACTION_DOWN -> {
                    if (event.repeatCount > 0) return true
                    tapKeyIsDown = true
                    tapKeyLongPressTriggered = false
                    mainHandler.removeCallbacks(tapKeyLongPressRunnable)
                    mainHandler.postDelayed(
                        tapKeyLongPressRunnable,
                        ViewConfiguration.getLongPressTimeout().toLong(),
                    )
                    return true
                }

                KeyEvent.ACTION_UP -> {
                    tapKeyIsDown = false
                    mainHandler.removeCallbacks(tapKeyLongPressRunnable)

                    if (tapKeyLongPressTriggered) {
                        tapKeyLongPressTriggered = false
                        return true
                    }

                    clearMoveRepeat()
                    val c = cursor.center()
                    gestures.tap(c.x, c.y)
                    return true
                }

                else -> return true
            }
        }

        // Scroll/Select keys:
        // - Short press: ACTION_SCROLL_(UP/DOWN/LEFT/RIGHT)
        // - Long press: move cursor to focused control, then DPAD_(UP/DOWN/LEFT/RIGHT)
        val isScrollSelectKey = keyCode == mouseKeyScrollUp ||
            keyCode == mouseKeyScrollDown ||
            keyCode == mouseKeyScrollLeft ||
            keyCode == mouseKeyScrollRight

        if (isScrollSelectKey) {
            when (event.action) {
                KeyEvent.ACTION_DOWN -> {
                    if (event.repeatCount > 0) return true
                    scrollSelectKeyIsDown = true
                    scrollSelectKeyLongPressTriggered = false
                    scrollSelectKeyDirection = when (keyCode) {
                        mouseKeyScrollUp -> 0
                        mouseKeyScrollDown -> 1
                        mouseKeyScrollLeft -> 2
                        else -> 3
                    }
                    mainHandler.removeCallbacks(scrollSelectKeyLongPressRunnable)
                    mainHandler.postDelayed(
                        scrollSelectKeyLongPressRunnable,
                        ViewConfiguration.getLongPressTimeout().toLong(),
                    )
                    return true
                }

                KeyEvent.ACTION_UP -> {
                    scrollSelectKeyIsDown = false
                    mainHandler.removeCallbacks(scrollSelectKeyLongPressRunnable)

                    if (scrollSelectKeyLongPressTriggered) {
                        scrollSelectKeyLongPressTriggered = false
                        return true
                    }

                    clearMoveRepeat()
                    val c = cursor.center()
                    when (keyCode) {
                        mouseKeyScrollUp -> gestures.scrollUp(c.x, c.y)
                        mouseKeyScrollDown -> gestures.scrollDown(c.x, c.y)
                        mouseKeyScrollLeft -> gestures.scrollLeft(c.x, c.y)
                        mouseKeyScrollRight -> gestures.scrollRight(c.x, c.y)
                    }
                    return true
                }

                else -> return true
            }
        }

        // Stop continuous movement on key up.
        if (event.action == KeyEvent.ACTION_UP) {
            val wasMovingKey = moveKeyCode == keyCode
            stopMoveRepeat(keyCode)
            return isHandledMouseKey || wasMovingKey
        }

        if (event.action != KeyEvent.ACTION_DOWN) return isHandledMouseKey

        return when (keyCode) {
            mouseKeyUp -> {
                startMoveRepeat(keyCode, dx = 0, dy = -1)
                true
            }
            mouseKeyDown -> {
                startMoveRepeat(keyCode, dx = 0, dy = 1)
                true
            }
            mouseKeyLeft -> {
                startMoveRepeat(keyCode, dx = -1, dy = 0)
                true
            }
            mouseKeyRight -> {
                startMoveRepeat(keyCode, dx = 1, dy = 0)
                true
            }
            // mouseKeyClick is handled above.
            else -> false
        }
    }

    private fun moveCursorToFocusedControl(): Boolean {
        val root = rootInActiveWindow ?: return false

        var focus: AccessibilityNodeInfo? = null
        try {
            focus = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
                ?: root.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)
            if (focus == null) return false

            val bounds = Rect()
            focus.getBoundsInScreen(bounds)
            if (bounds.isEmpty) return false

            val cx = bounds.centerX()
            val cy = bounds.centerY()
            // CursorOverlay is 48x48.
            cursor.setPosition(cx - 24, cy - 24)
            val p = cursor.position()
            lastCursorX = p.x
            lastCursorY = p.y
            return true
        } catch (_: Exception) {
            return false
        } finally {
            try {
                focus?.recycle()
            } catch (_: Exception) {
            }
        }
    }

    private fun moveCursorToNode(node: AccessibilityNodeInfo): Boolean {
        val bounds = Rect()
        return try {
            node.getBoundsInScreen(bounds)
            if (bounds.isEmpty) return false

            val cx = bounds.centerX()
            val cy = bounds.centerY()
            // CursorOverlay is 48x48.
            cursor.setPosition(cx - 24, cy - 24)
            val p = cursor.position()
            lastCursorX = p.x
            lastCursorY = p.y
            true
        } catch (_: Exception) {
            false
        }
    }

    override fun onDestroy() {
        clearPendingToggle()
        clearPendingTapKey()
        clearMoveRepeat()
        cursor.hide()
        super.onDestroy()
    }

    private fun toggleMode() {
        clearMoveRepeat()
        clearPendingTapKey()
        mode = mode.toggle()
        settings.setOperationMode(mode)
        if (mode == OperationMode.MOUSE) {
            applyCursorStartPositionIfNeeded()
            cursor.show()
            val p = cursor.position()
            lastCursorX = p.x
            lastCursorY = p.y
        } else {
            // Remember last cursor position for "previous" start.
            settings.setLastCursorPosition(lastCursorX, lastCursorY)
            cursor.hide()
        }
    }

    private fun clearPendingTapKey() {
        mainHandler.removeCallbacks(tapKeyLongPressRunnable)
        tapKeyIsDown = false
        tapKeyLongPressTriggered = false
    }

    private fun applyCursorStartPositionIfNeeded() {
        val dm = resources.displayMetrics
        val displayW = dm.widthPixels
        val displayH = dm.heightPixels
        val cursorW = 48
        val cursorH = 48

        val target = when (settings.getCursorStartPosition()) {
            "previous" -> {
                val last = settings.getLastCursorPositionOrNull()
                if (last != null) last.first to last.second else ((displayW - cursorW) / 2) to ((displayH - cursorH) / 2)
            }
            "top_left" -> 0 to 0
            "top" -> ((displayW - cursorW) / 2) to 0
            "top_right" -> (displayW - cursorW) to 0
            "left" -> 0 to ((displayH - cursorH) / 2)
            "right" -> (displayW - cursorW) to ((displayH - cursorH) / 2)
            "bottom_left" -> 0 to (displayH - cursorH)
            "bottom" -> ((displayW - cursorW) / 2) to (displayH - cursorH)
            "bottom_right" -> (displayW - cursorW) to (displayH - cursorH)
            else -> ((displayW - cursorW) / 2) to ((displayH - cursorH) / 2)
        }

        cursor.setPosition(target.first, target.second)
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
        val p = cursor.position()
        lastCursorX = p.x
        lastCursorY = p.y

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
