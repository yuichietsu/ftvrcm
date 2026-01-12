package com.ftvrcm.service

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.ViewConfiguration
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import com.ftvrcm.adb.AdbInputClient
import com.ftvrcm.data.SettingsStore
import com.ftvrcm.domain.EmulationMethod
import com.ftvrcm.domain.OperationMode
import com.ftvrcm.mouse.CursorOverlay
import com.ftvrcm.mouse.GestureController

class RemoteControlAccessibilityService : AccessibilityService() {

    private val tag = "RCAccessibilityService"

    private lateinit var settings: SettingsStore
    private lateinit var cursor: CursorOverlay
    private lateinit var gestures: GestureController
    private var adbInput: AdbInputClient? = null
    private var adbHost: String? = null
    private var adbPort: Int? = null

    private var mode: OperationMode = OperationMode.NORMAL

    private var lastCursorX: Int = 0
    private var lastCursorY: Int = 0

    private var tapKeyIsDown: Boolean = false
    private var tapKeyLongPressTriggered: Boolean = false

    private var scrollSelectKeyIsDown: Boolean = false

    private var isDpadMode: Boolean = false

    private val tapKeyLongPressRunnable = Runnable {
        if (mode != OperationMode.MOUSE) return@Runnable
        if (!tapKeyIsDown) return@Runnable
        if (tapKeyLongPressTriggered) return@Runnable

        tapKeyLongPressTriggered = true
        clearMoveRepeat()
        val c = cursor.center()
        when (settings.getEmulationMethod()) {
            EmulationMethod.ACCESSIBILITY_SERVICE -> gestures.longPress(c.x, c.y)
            EmulationMethod.ADB -> adb()?.longPress(c.x, c.y)
        }
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
            // Create lazily to avoid unnecessary ADB connections on startup.
            adbInput = null
            adbHost = null
            adbPort = null

            Log.i(tag, "service connected")

            mode = settings.getOperationMode()
            if (mode == OperationMode.MOUSE) {
                applyCursorStartPositionIfNeeded()
                cursor.show()
                updateCursorStyleForInputMode()
                val p = cursor.position()
                lastCursorX = p.x
                lastCursorY = p.y
            }
        } catch (t: Throwable) {
            Log.e(tag, "service init failed (${t.javaClass.simpleName}: ${t.message})")
            try {
                disableSelf()
            } catch (_: Throwable) {
                // ignore
            }
        }
    }

    private fun adb(): AdbInputClient? {
        val host = settings.getAdbHost()
        val port = settings.getAdbPort()

        val current = adbInput
        if (current != null && adbHost == host && adbPort == port) return current

        try {
            current?.close()
        } catch (_: Throwable) {
        }

        return try {
            AdbInputClient(this, host = host, port = port).also {
                adbInput = it
                adbHost = host
                adbPort = port
            }
        } catch (_: Throwable) {
            adbInput = null
            adbHost = null
            adbPort = null
            null
        }
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
        val isToggleKey = keyCode == toggleKey
        if (isToggleKey) {
            if (!toggleLongPress) {
                if (event.action == KeyEvent.ACTION_DOWN) {
                    Log.i(tag, "toggle key DOWN (no-longpress mode)")
                    toggleMode()
                    return true
                }
                return false
            }

            when (event.action) {
                KeyEvent.ACTION_DOWN -> {
                    Log.i(tag, "toggle key DOWN (longpress enabled)")
                    // If platform reports long-press/repeat, toggle immediately.
                    if (event.isLongPress || event.repeatCount > 0) {
                        Log.i(tag, "toggle key reported longpress/repeat -> toggle now")
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
                        Log.i(tag, "toggle key schedule longpress timeoutMs=$timeoutMs")
                    }

                    // Consume DOWN to avoid delivering only DOWN when long-press toggles.
                    return true
                }

                KeyEvent.ACTION_UP -> {
                    Log.i(tag, "toggle key UP")
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

        // 2) Mouse mode key mapping
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
        val mouseKeyCursorDpadToggle = settings.getMouseKeyCursorDpadToggle()

        val isHandledMouseKey = keyCode == mouseKeyUp ||
            keyCode == mouseKeyDown ||
            keyCode == mouseKeyLeft ||
            keyCode == mouseKeyRight ||
            keyCode == mouseKeyClick ||
            keyCode == mouseKeyScrollUp ||
            keyCode == mouseKeyScrollDown ||
            keyCode == mouseKeyScrollLeft ||
            keyCode == mouseKeyScrollRight ||
            keyCode == mouseKeyCursorDpadToggle

        // Toggle cursor/dpad mode.
        if (keyCode == mouseKeyCursorDpadToggle) {
            when (event.action) {
                KeyEvent.ACTION_DOWN -> {
                    if (event.repeatCount > 0) return true
                    isDpadMode = !isDpadMode
                    updateCursorStyleForInputMode()
                    return true
                }
                KeyEvent.ACTION_UP -> return true
                else -> return true
            }
        }

        // In DPAD mode, let physical DPAD keys behave as normal system navigation.
        if (isDpadMode && (keyCode == KeyEvent.KEYCODE_DPAD_UP ||
                keyCode == KeyEvent.KEYCODE_DPAD_DOWN ||
                keyCode == KeyEvent.KEYCODE_DPAD_LEFT ||
                keyCode == KeyEvent.KEYCODE_DPAD_RIGHT ||
                keyCode == KeyEvent.KEYCODE_DPAD_CENTER)) {
            clearMoveRepeat()
            clearPendingTapKey()
            return false
        }

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
                    when (settings.getEmulationMethod()) {
                        EmulationMethod.ACCESSIBILITY_SERVICE -> {
                            Log.i(tag, "tap via accessibility at (${c.x},${c.y})")
                            gestures.tap(c.x, c.y)
                        }
                        EmulationMethod.ADB -> {
                            Log.i(tag, "tap via adb at (${c.x},${c.y})")
                            adb()?.tap(c.x, c.y)
                        }
                    }
                    return true
                }

                else -> return true
            }
        }

        // Scroll keys (always): ACTION_SCROLL_(UP/DOWN/LEFT/RIGHT)
        val isScrollSelectKey = keyCode == mouseKeyScrollUp ||
            keyCode == mouseKeyScrollDown ||
            keyCode == mouseKeyScrollLeft ||
            keyCode == mouseKeyScrollRight

        if (isScrollSelectKey) {
            when (event.action) {
                KeyEvent.ACTION_DOWN -> {
                    if (event.repeatCount > 0) return true
                    scrollSelectKeyIsDown = true
                    return true
                }

                KeyEvent.ACTION_UP -> {
                    scrollSelectKeyIsDown = false

                    clearMoveRepeat()
                    val c = cursor.center()
                    when (settings.getEmulationMethod()) {
                        EmulationMethod.ACCESSIBILITY_SERVICE -> {
                            when (keyCode) {
                                mouseKeyScrollUp -> gestures.scrollUp(c.x, c.y)
                                mouseKeyScrollDown -> gestures.scrollDown(c.x, c.y)
                                mouseKeyScrollLeft -> gestures.scrollLeft(c.x, c.y)
                                mouseKeyScrollRight -> gestures.scrollRight(c.x, c.y)
                            }
                        }
                        EmulationMethod.ADB -> {
                            val dm = resources.displayMetrics
                            val w = dm.widthPixels
                            val h = dm.heightPixels
                            val distance = (minOf(w, h) * 0.28).toInt().coerceAtLeast(120)

                            fun clampX(x: Int) = x.coerceIn(0, w - 1)
                            fun clampY(y: Int) = y.coerceIn(0, h - 1)

                            // Swipe around cursor center.
                            val half = distance / 2
                            when (keyCode) {
                                mouseKeyScrollUp -> adb()?.swipe(
                                    clampX(c.x),
                                    clampY(c.y + half),
                                    clampX(c.x),
                                    clampY(c.y - half),
                                )
                                mouseKeyScrollDown -> adb()?.swipe(
                                    clampX(c.x),
                                    clampY(c.y - half),
                                    clampX(c.x),
                                    clampY(c.y + half),
                                )
                                mouseKeyScrollLeft -> adb()?.swipe(
                                    clampX(c.x + half),
                                    clampY(c.y),
                                    clampX(c.x - half),
                                    clampY(c.y),
                                )
                                mouseKeyScrollRight -> adb()?.swipe(
                                    clampX(c.x - half),
                                    clampY(c.y),
                                    clampX(c.x + half),
                                    clampY(c.y),
                                )
                            }

                            Log.i(tag, "swipe via adb keyCode=$keyCode center=(${c.x},${c.y}) distance=$distance")
                        }
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

    override fun onDestroy() {
        clearPendingToggle()
        clearPendingTapKey()
        clearMoveRepeat()
        cursor.hide()
        adbInput?.close()
        adbInput = null
        adbHost = null
        adbPort = null
        super.onDestroy()
    }

    private fun toggleMode() {
        clearMoveRepeat()
        clearPendingTapKey()
        mode = mode.toggle()
        settings.setOperationMode(mode)

        Log.i(tag, "toggle mode -> $mode")

        if (mode == OperationMode.MOUSE) {
            applyCursorStartPositionIfNeeded()
            cursor.show()
            updateCursorStyleForInputMode()
            val p = cursor.position()
            lastCursorX = p.x
            lastCursorY = p.y
        } else {
            // Remember last cursor position for "previous" start.
            settings.setLastCursorPosition(lastCursorX, lastCursorY)
            cursor.hide()
        }
    }

    private fun updateCursorStyleForInputMode() {
        cursor.setStyle(if (isDpadMode) CursorOverlay.CursorStyle.DPAD else CursorOverlay.CursorStyle.POINTER)
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
