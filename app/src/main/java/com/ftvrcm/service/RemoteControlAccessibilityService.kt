package com.ftvrcm.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.ViewConfiguration
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import com.ftvrcm.data.SettingsKeys
import com.ftvrcm.data.SettingsStore
import com.ftvrcm.domain.EmulationMethod
import com.ftvrcm.domain.OperationMode
import com.ftvrcm.domain.ToggleTrigger
import com.ftvrcm.mouse.CursorOverlay
import com.ftvrcm.mouse.GestureController
import com.ftvrcm.shizuku.ShizukuTouchInjector
import com.ftvrcm.util.KeyCaptureState
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class RemoteControlAccessibilityService : AccessibilityService() {

    private val tag = "RCAccessibilityService"

    private enum class SwipeAction {
        UP,
        DOWN,
        LEFT,
        RIGHT,
    }

    private enum class PinchAction {
        IN,
        OUT,
    }

    private lateinit var settings: SettingsStore
    private lateinit var cursor: CursorOverlay
    private lateinit var gestures: GestureController

    private var shizukuInjector: ShizukuTouchInjector? = null
    private val shizukuExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    private var mode: OperationMode = OperationMode.NORMAL

    private var lastCursorX: Int = 0
    private var lastCursorY: Int = 0

    private var tapKeyIsDown: Boolean = false
    private var tapKeyLongPressTriggered: Boolean = false

    private var scrollSelectKeyIsDown: Boolean = false
    private var scrollSelectAction: SwipeAction? = null
    private var scrollSelectKeyLongPressTriggered: Boolean = false

    private var isDpadMode: Boolean = false

    private val tapKeyLongPressRunnable = Runnable {
        if (mode != OperationMode.MOUSE) return@Runnable
        if (!tapKeyIsDown) return@Runnable
        if (tapKeyLongPressTriggered) return@Runnable

        tapKeyLongPressTriggered = true
        clearMoveRepeat()
        val c = cursor.center()

        if (settings.isTouchVisualFeedbackEnabled()) {
            cursor.showTapFeedback(isLongPress = true)
        }

        when (settings.getEmulationMethod()) {
            EmulationMethod.ACCESSIBILITY_SERVICE -> {
                gestures.longPress(c.x, c.y)
            }

            EmulationMethod.SHIZUKU -> {
                shizukuExecutor.execute {
                    val ok = shizuku().longPress(c.x, c.y)
                    if (!ok) {
                        mainHandler.post { showShizukuErrorToast() }
                    }
                }
            }
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

    private var pendingToggleTapAtMs: Long = 0L
    private var pendingToggleTapKeyCode: Int? = null
    private val commitToggleTapRunnable = Runnable {
        pendingToggleTapAtMs = 0L
        pendingToggleTapKeyCode = null
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

    private val scrollRepeatRunnable = object : Runnable {
        override fun run() {
            if (mode != OperationMode.MOUSE) return
            if (!scrollSelectKeyIsDown) return
            val action = scrollSelectAction ?: return

            dispatchScrollOrSwipe(action)
            mainHandler.postDelayed(this, settings.getMouseScrollRepeatIntervalMs().toLong())
        }
    }

    private val scrollKeyLongPressRunnable = Runnable {
        if (mode != OperationMode.MOUSE) return@Runnable
        if (!scrollSelectKeyIsDown) return@Runnable
        if (scrollSelectKeyLongPressTriggered) return@Runnable
        if (!settings.isMouseScrollRepeatLongPress()) return@Runnable

        scrollSelectKeyLongPressTriggered = true
        val action = scrollSelectAction ?: return@Runnable
        dispatchScrollOrSwipe(action)
        mainHandler.postDelayed(scrollRepeatRunnable, settings.getMouseScrollRepeatIntervalMs().toLong())
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        try {
            settings = SettingsStore(this).also { it.initializeDefaultsIfNeeded() }
            cursor = CursorOverlay(this)
            gestures = GestureController(this)

            // Some Fire OS builds are flaky about key filtering unless explicitly requested at runtime.
            try {
                val info = serviceInfo
                info.flags = info.flags or AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
                serviceInfo = info
            } catch (_: Throwable) {
            }

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

            try {
                val filter = IntentFilter("com.ftvrcm.CMD")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    registerReceiver(cmdReceiver, filter, Context.RECEIVER_EXPORTED)
                } else {
                    registerReceiver(cmdReceiver, filter)
                }
            } catch (_: Throwable) {
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

    private fun shizuku(): ShizukuTouchInjector {
        return shizukuInjector ?: ShizukuTouchInjector(this).also { shizukuInjector = it }
    }


    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // no-op
    }

    override fun onInterrupt() {
        // no-op
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (KeyCaptureState.isCapturing) return false
        val keyCode = event.keyCode

        // Keep mode in sync with preferences even if they were changed externally (e.g. via ADB).
        syncModeFromSettingsIfNeeded()

        // 1) Toggle mode (always available)
        val toggleKey = settings.getToggleKeyCode()
        val toggleTrigger = settings.getToggleTrigger()
        val mouseKeyCursorDpadToggle = settings.getMouseKeyCursorDpadToggle()
        val isToggleKey = matchesAssignedKey(toggleKey, event)
        val allowMouseToggleFallthrough = mode == OperationMode.MOUSE && matchesAssignedKey(mouseKeyCursorDpadToggle, event)
        val allowTogglePassThrough = toggleTrigger != ToggleTrigger.SINGLE_TAP &&
            (event.keyCode == KeyEvent.KEYCODE_DPAD_CENTER || mode != OperationMode.MOUSE)
        if (isToggleKey) {
            when (toggleTrigger) {
                ToggleTrigger.SINGLE_TAP -> {
                    if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                        Log.i(tag, "toggle key DOWN (single-tap)")
                        toggleMode()
                        return true
                    }
                    return true
                }

                ToggleTrigger.DOUBLE_TAP -> {
                    when (event.action) {
                        KeyEvent.ACTION_DOWN -> if (!allowMouseToggleFallthrough) return !allowTogglePassThrough
                        KeyEvent.ACTION_UP -> {
                            Log.i(tag, "toggle key UP (double-tap)")
                            scheduleToggleTap(toggleKey)
                            if (!allowMouseToggleFallthrough) return !allowTogglePassThrough
                        }
                        else -> if (!allowMouseToggleFallthrough) return !allowTogglePassThrough
                    }
                }

                ToggleTrigger.LONG_PRESS -> {
                    when (event.action) {
                        KeyEvent.ACTION_DOWN -> {
                            Log.i(tag, "toggle key DOWN (longpress enabled)")
                            // Fire TV may not report long-press; use our own detection.
                            if (pendingToggleKeyCode == null) {
                                pendingToggleKeyCode = toggleKey
                                pendingToggleTriggered = false
                                val timeoutMs = ViewConfiguration.getLongPressTimeout().toLong()
                                mainHandler.postDelayed(pendingToggleRunnable, timeoutMs)
                                Log.i(tag, "toggle key schedule longpress timeoutMs=$timeoutMs")
                            }
                            return !allowTogglePassThrough
                        }

                        KeyEvent.ACTION_UP -> {
                            Log.i(tag, "toggle key UP")
                            val wasTriggered = pendingToggleTriggered
                            clearPendingToggle()

                            // Short press: when the toggle key is also used as cursor/DPAD toggle,
                            // switch input mode in MOUSE mode.
                            val cursorDpadToggleKey = settings.getMouseKeyCursorDpadToggle()
                            if (!wasTriggered && mode == OperationMode.MOUSE && matchesAssignedKey(cursorDpadToggleKey, event)) {
                                isDpadMode = !isDpadMode
                                clearMoveRepeat()
                                clearPendingTapKey()
                                updateCursorStyleForInputMode()
                                return true
                            }

                            // Short press: preserve BACK behavior via accessibility global action.
                            if (!wasTriggered && event.keyCode == KeyEvent.KEYCODE_BACK && !allowTogglePassThrough) {
                                performGlobalAction(GLOBAL_ACTION_BACK)
                                return true
                            }

                            return !allowTogglePassThrough
                        }

                        else -> return !allowTogglePassThrough
                    }
                }
            }
        }

        // 2) Mouse mode key mapping
        if (mode != OperationMode.MOUSE) return false

        // 2.1) Screenshot capture (mouse mode only)
        val screenshotKey = settings.getScreenshotKey()
        if (matchesAssignedKey(screenshotKey, event)) {
            return when (event.action) {
                KeyEvent.ACTION_DOWN -> {
                    if (event.repeatCount > 0) return true
                    triggerScreenshot()
                    true
                }
                KeyEvent.ACTION_UP -> true
                else -> true
            }
        }

        val mouseKeyUp = settings.getMouseKeyUp()
        val mouseKeyDown = settings.getMouseKeyDown()
        val mouseKeyLeft = settings.getMouseKeyLeft()
        val mouseKeyRight = settings.getMouseKeyRight()
        val mouseKeyClick = settings.getMouseKeyClick()
        val mouseKeyScrollUp = settings.getMouseKeyScrollUp()
        val mouseKeyScrollDown = settings.getMouseKeyScrollDown()
        val mouseKeyScrollLeft = settings.getMouseKeyScrollLeft()
        val mouseKeyScrollRight = settings.getMouseKeyScrollRight()
        val mouseKeyPinchIn = settings.getMouseKeyPinchIn()
        val mouseKeyPinchOut = settings.getMouseKeyPinchOut()

        val isHandledMouseKey = matchesAssignedKey(mouseKeyUp, event) ||
            matchesAssignedKey(mouseKeyDown, event) ||
            matchesAssignedKey(mouseKeyLeft, event) ||
            matchesAssignedKey(mouseKeyRight, event) ||
            matchesAssignedKey(mouseKeyClick, event) ||
            matchesAssignedKey(mouseKeyScrollUp, event) ||
            matchesAssignedKey(mouseKeyScrollDown, event) ||
            matchesAssignedKey(mouseKeyScrollLeft, event) ||
            matchesAssignedKey(mouseKeyScrollRight, event) ||
            matchesAssignedKey(mouseKeyPinchIn, event) ||
            matchesAssignedKey(mouseKeyPinchOut, event) ||
            matchesAssignedKey(mouseKeyCursorDpadToggle, event)

        // Toggle cursor/dpad mode.
        if (matchesAssignedKey(mouseKeyCursorDpadToggle, event)) {
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
                keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                keyCode == KeyEvent.KEYCODE_BACK)) {
            clearMoveRepeat()
            clearPendingTapKey()
            return false
        }

        // Tap key handling (single tap / long tap)
        if (matchesAssignedKey(mouseKeyClick, event)) {
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

                    dispatchTap(c.x, c.y)
                    return true
                }

                else -> return true
            }
        }

        // Scroll keys (always): ACTION_SCROLL_(UP/DOWN/LEFT/RIGHT)
        val scrollAction = when {
            matchesAssignedKey(mouseKeyScrollUp, event) -> SwipeAction.UP
            matchesAssignedKey(mouseKeyScrollDown, event) -> SwipeAction.DOWN
            matchesAssignedKey(mouseKeyScrollLeft, event) -> SwipeAction.LEFT
            matchesAssignedKey(mouseKeyScrollRight, event) -> SwipeAction.RIGHT
            else -> null
        }

        if (scrollAction != null) {
            when (event.action) {
                KeyEvent.ACTION_DOWN -> {
                    if (event.repeatCount > 0) return true
                    scrollSelectKeyIsDown = true
                    scrollSelectAction = scrollAction
                    scrollSelectKeyLongPressTriggered = false
                    clearPendingScrollRepeat()

                    if (settings.isMouseScrollRepeatLongPress()) {
                        val timeoutMs = ViewConfiguration.getLongPressTimeout().toLong()
                        mainHandler.postDelayed(scrollKeyLongPressRunnable, timeoutMs)
                    }
                    return true
                }

                KeyEvent.ACTION_UP -> {
                    scrollSelectKeyIsDown = false
                    val wasLongPress = scrollSelectKeyLongPressTriggered
                    clearPendingScrollRepeat()
                    scrollSelectAction = null
                    scrollSelectKeyLongPressTriggered = false

                    if (wasLongPress) {
                        return true
                    }

                    clearMoveRepeat()

                    dispatchScrollOrSwipe(scrollAction)
                    return true
                }

                else -> return true
            }
        }

        val pinchAction = when {
            matchesAssignedKey(mouseKeyPinchIn, event) -> PinchAction.IN
            matchesAssignedKey(mouseKeyPinchOut, event) -> PinchAction.OUT
            else -> null
        }

        if (pinchAction != null) {
            when (event.action) {
                KeyEvent.ACTION_DOWN -> {
                    if (event.repeatCount > 0) return true
                    return true
                }
                KeyEvent.ACTION_UP -> {
                    dispatchPinch(pinchAction)
                    return true
                }
                else -> return true
            }
        }

        // Stop continuous movement on key up.
        if (event.action == KeyEvent.ACTION_UP) {
            val wasMovingKey = moveKeyCode?.let { matchesAssignedKey(it, event) } == true
            stopMoveRepeat(event)
            return isHandledMouseKey || wasMovingKey
        }

        if (event.action != KeyEvent.ACTION_DOWN) return isHandledMouseKey

        return when {
            matchesAssignedKey(mouseKeyUp, event) -> {
                startMoveRepeat(mouseKeyUp, dx = 0, dy = -1)
                true
            }
            matchesAssignedKey(mouseKeyDown, event) -> {
                startMoveRepeat(mouseKeyDown, dx = 0, dy = 1)
                true
            }
            matchesAssignedKey(mouseKeyLeft, event) -> {
                startMoveRepeat(mouseKeyLeft, dx = -1, dy = 0)
                true
            }
            matchesAssignedKey(mouseKeyRight, event) -> {
                startMoveRepeat(mouseKeyRight, dx = 1, dy = 0)
                true
            }
            // mouseKeyClick is handled above.
            else -> false
        }
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(cmdReceiver)
        } catch (_: Throwable) {
        }
        clearPendingToggle()
        clearPendingToggleTap()
        clearPendingTapKey()
        clearPendingScrollRepeat()
        clearMoveRepeat()
        cursor.hide()
        try {
            shizukuExecutor.shutdownNow()
        } catch (_: Throwable) {
        }
        super.onDestroy()
    }

    private fun toggleMode() {
        clearMoveRepeat()
        clearPendingTapKey()
        clearPendingToggleTap()
        val target = mode.toggle()

        if (target == OperationMode.MOUSE) {
            when (settings.getEmulationMethod()) {
                EmulationMethod.ACCESSIBILITY_SERVICE -> {
                    if (!canPerformGesturesViaAccessibility()) {
                        showToast("タッチ操作へ切り替えできません（アクセシビリティサービスがジェスチャを実行できません）")
                        return
                    }

                    applyMode(target)
                    return
                }

                EmulationMethod.SHIZUKU -> {
                    if (!ShizukuTouchInjector.isShizukuAvailable()) {
                        showToast("タッチ操作へ切り替えできません（Shizukuサービスが起動していません）")
                        return
                    }
                    if (!ShizukuTouchInjector.isPermissionGranted()) {
                        showToast("タッチ操作へ切り替えできません（Shizukuの権限が付与されていません）")
                        return
                    }

                    applyMode(target)
                    return
                }
            }
        }

        applyMode(target)
    }

    private fun applyMode(newMode: OperationMode) {
        mode = newMode
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

    private fun canPerformGesturesViaAccessibility(): Boolean {
        return try {
            val info = serviceInfo
            (info.capabilities and AccessibilityServiceInfo.CAPABILITY_CAN_PERFORM_GESTURES) != 0
        } catch (_: Throwable) {
            false
        }
    }

    private fun showToast(message: String) {
        try {
            Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()
        } catch (_: Throwable) {
        }
    }


    private fun showShizukuErrorToast() {
        val detail = getLastShizukuErrorDetail()
        showToast("Shizuku操作に失敗しました${if (!detail.isNullOrBlank()) ": $detail" else ""}")
    }

    private fun getLastShizukuErrorDetail(maxLen: Int = 120): String? {
        return try {
            val prefs = getSharedPreferences(SettingsKeys.PREFS_NAME, MODE_PRIVATE)
            val status = prefs.getString(SettingsKeys.LAST_GESTURE_STATUS, "") ?: ""
            if (status != "FAILED") return null
            val detail = prefs.getString(SettingsKeys.LAST_GESTURE_DETAIL, "") ?: ""
            detail.trim().take(maxLen).ifEmpty { null }
        } catch (_: Throwable) {
            null
        }
    }


    private fun syncModeFromSettingsIfNeeded() {
        val current = settings.getOperationMode()
        if (current == mode) return

        clearMoveRepeat()
        clearPendingTapKey()
        clearPendingToggle()
        clearPendingToggleTap()

        mode = current
        Log.i(tag, "mode synced from prefs -> $mode")

        if (mode == OperationMode.MOUSE) {
            applyCursorStartPositionIfNeeded()
            cursor.show()
            updateCursorStyleForInputMode()
            val p = cursor.position()
            lastCursorX = p.x
            lastCursorY = p.y
        } else {
            settings.setLastCursorPosition(lastCursorX, lastCursorY)
            cursor.hide()
        }
    }

    private fun updateCursorStyleForInputMode() {
        cursor.setStyle(if (isDpadMode) CursorOverlay.CursorStyle.DPAD else CursorOverlay.CursorStyle.POINTER)
    }

    private fun triggerScreenshot() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val ok = performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)
            if (!ok) {
                showToast("スクリーンショットの撮影に失敗しました")
            }
        } else {
            showToast("スクリーンショットはAndroid 9以上で利用可能です")
        }
    }

    private fun matchesAssignedKey(assigned: Int, event: KeyEvent): Boolean {
        if (assigned == 0) return false
        return if (assigned < 0) event.scanCode == -assigned else event.keyCode == assigned
    }

    private fun scheduleToggleTap(triggerKeyCode: Int) {
        val now = SystemClock.uptimeMillis()
        val doubleTapTimeoutMs = ViewConfiguration.getDoubleTapTimeout().toLong()

        if (pendingToggleTapAtMs != 0L && pendingToggleTapKeyCode == triggerKeyCode && now - pendingToggleTapAtMs <= doubleTapTimeoutMs) {
            mainHandler.removeCallbacks(commitToggleTapRunnable)
            pendingToggleTapAtMs = 0L
            pendingToggleTapKeyCode = null
            toggleMode()
            return
        }

        pendingToggleTapAtMs = now
        pendingToggleTapKeyCode = triggerKeyCode
        mainHandler.postDelayed(commitToggleTapRunnable, doubleTapTimeoutMs)
    }

    private fun clearPendingTapKey() {
        mainHandler.removeCallbacks(tapKeyLongPressRunnable)
        tapKeyIsDown = false
        tapKeyLongPressTriggered = false
    }

    private fun dispatchTap(x: Int, y: Int) {
        if (settings.isTouchVisualFeedbackEnabled()) {
            cursor.showTapFeedback(isLongPress = false)
        }
        when (settings.getEmulationMethod()) {
            EmulationMethod.ACCESSIBILITY_SERVICE -> {
                Log.i(tag, "tap via accessibility at (${x},${y})")
                gestures.tap(x, y)
            }

            EmulationMethod.SHIZUKU -> {
                Log.i(tag, "tap via Shizuku at (${x},${y})")
                shizukuExecutor.execute {
                    val ok = shizuku().tap(x, y)
                    if (!ok) {
                        mainHandler.post { showShizukuErrorToast() }
                    }
                }
            }
        }
    }

    private fun dispatchDoubleTap(x: Int, y: Int) {
        if (settings.isTouchVisualFeedbackEnabled()) {
            cursor.showTapFeedback(isLongPress = false)
            mainHandler.postDelayed({
                cursor.showTapFeedback(isLongPress = false)
            }, 90L)
        }
        when (settings.getEmulationMethod()) {
            EmulationMethod.ACCESSIBILITY_SERVICE -> {
                Log.i(tag, "doubleTap via accessibility at (${x},${y})")
                gestures.doubleTap(x, y)
            }

            EmulationMethod.SHIZUKU -> {
                Log.i(tag, "doubleTap via Shizuku at (${x},${y})")
                shizukuExecutor.execute {
                    val ok = shizuku().doubleTap(x, y)
                    if (!ok) {
                        mainHandler.post { showShizukuErrorToast() }
                    }
                }
            }
        }
    }

    private fun clearPendingScrollRepeat() {
        mainHandler.removeCallbacks(scrollKeyLongPressRunnable)
        mainHandler.removeCallbacks(scrollRepeatRunnable)
    }

    private fun dispatchScrollOrSwipe(action: SwipeAction) {
        val c = cursor.center()
        val visualFeedback = settings.isTouchVisualFeedbackEnabled()
        when (settings.getEmulationMethod()) {
            EmulationMethod.ACCESSIBILITY_SERVICE -> {
                when (action) {
                    SwipeAction.UP -> {
                        if (visualFeedback) cursor.showScrollArrow(CursorOverlay.Direction.UP)
                        gestures.scrollUp(c.x, c.y)
                    }
                    SwipeAction.DOWN -> {
                        if (visualFeedback) cursor.showScrollArrow(CursorOverlay.Direction.DOWN)
                        gestures.scrollDown(c.x, c.y)
                    }
                    SwipeAction.LEFT -> {
                        if (visualFeedback) cursor.showScrollArrow(CursorOverlay.Direction.LEFT)
                        gestures.scrollLeft(c.x, c.y)
                    }
                    SwipeAction.RIGHT -> {
                        if (visualFeedback) cursor.showScrollArrow(CursorOverlay.Direction.RIGHT)
                        gestures.scrollRight(c.x, c.y)
                    }
                }
            }

            EmulationMethod.SHIZUKU -> {
                val dm = resources.displayMetrics
                val w = dm.widthPixels
                val h = dm.heightPixels
                val distancePercent = settings.getMouseSwipeDistancePercent()
                val distance = ((minOf(w, h) * (distancePercent / 100.0))).toInt().coerceIn(40, minOf(w, h) - 1)

                fun clampX(x: Int) = x.coerceIn(0, w - 1)
                fun clampY(y: Int) = y.coerceIn(0, h - 1)

                val x1: Int
                val y1: Int
                val x2: Int
                val y2: Int

                when (action) {
                    SwipeAction.UP -> {
                        x1 = clampX(c.x); y1 = clampY(c.y)
                        x2 = clampX(c.x); y2 = clampY(c.y - distance)
                    }
                    SwipeAction.DOWN -> {
                        x1 = clampX(c.x); y1 = clampY(c.y)
                        x2 = clampX(c.x); y2 = clampY(c.y + distance)
                    }
                    SwipeAction.LEFT -> {
                        x1 = clampX(c.x); y1 = clampY(c.y)
                        x2 = clampX(c.x - distance); y2 = clampY(c.y)
                    }
                    SwipeAction.RIGHT -> {
                        x1 = clampX(c.x); y1 = clampY(c.y)
                        x2 = clampX(c.x + distance); y2 = clampY(c.y)
                    }
                }

                if (visualFeedback) cursor.showSwipeTrail(x1, y1, x2, y2)

                shizukuExecutor.execute {
                    val ok = shizuku().swipe(x1, y1, x2, y2)
                    if (!ok) {
                        mainHandler.post { showShizukuErrorToast() }
                    }
                }

                Log.i(
                    tag,
                    "swipe via Shizuku action=$action center=(${c.x},${c.y}) distance=$distance (${distancePercent}%)",
                )
            }
        }
    }

    private fun dispatchPinch(action: PinchAction) {
        val dm = resources.displayMetrics
        val w = dm.widthPixels
        val h = dm.heightPixels
        val minSide = minOf(w, h)
        val distancePercent = settings.getMousePinchDistancePercent()
        val baseDistance = ((minSide * (distancePercent / 100.0))).toInt().coerceIn(40, minSide - 1)
        val innerOffset = (baseDistance * 0.15f).toInt().coerceIn(24, (baseDistance / 3).coerceAtLeast(25))
        val travel = ((baseDistance / 2) - innerOffset).coerceAtLeast(20).coerceAtMost((minSide / 2) - innerOffset - 1)
        val outerOffset = innerOffset + travel

        val c = cursor.center()
        fun clampX(x: Int) = x.coerceIn(0, w - 1)
        fun clampY(y: Int) = y.coerceIn(0, h - 1)

        val (startOffset, endOffset) = if (action == PinchAction.IN) {
            outerOffset to innerOffset
        } else {
            innerOffset to outerOffset
        }

        val x1Start = clampX(c.x - startOffset)
        val y1Start = clampY(c.y)
        val x1End = clampX(c.x - endOffset)
        val y1End = clampY(c.y)

        val x2Start = clampX(c.x + startOffset)
        val y2Start = clampY(c.y)
        val x2End = clampX(c.x + endOffset)
        val y2End = clampY(c.y)

        when (settings.getEmulationMethod()) {
            EmulationMethod.ACCESSIBILITY_SERVICE -> {
                if (!canPerformGesturesViaAccessibility()) {
                    showToast("ピンチ操作はアクセシビリティで実行できません")
                    return
                }

                when (action) {
                    PinchAction.IN -> gestures.pinchIn(x1Start, y1Start, x1End, y1End, x2Start, y2Start, x2End, y2End)
                    PinchAction.OUT -> gestures.pinchOut(x1Start, y1Start, x1End, y1End, x2Start, y2Start, x2End, y2End)
                }

                if (settings.isTouchVisualFeedbackEnabled()) {
                    cursor.showPinchFeedback(
                        x1Start = x1Start, y1Start = y1Start, x1End = x1End, y1End = y1End,
                        x2Start = x2Start, y2Start = y2Start, x2End = x2End, y2End = y2End,
                    )
                }

                Log.i(
                    tag,
                    "pinch via accessibility action=$action center=(${c.x},${c.y}) travel=$travel inner=$innerOffset outer=$outerOffset",
                )
            }

            EmulationMethod.SHIZUKU -> {
                if (settings.isTouchVisualFeedbackEnabled()) {
                    cursor.showPinchFeedback(
                        x1Start = x1Start, y1Start = y1Start, x1End = x1End, y1End = y1End,
                        x2Start = x2Start, y2Start = y2Start, x2End = x2End, y2End = y2End,
                    )
                }

                shizukuExecutor.execute {
                    val ok = when (action) {
                        PinchAction.IN -> shizuku().pinchIn(
                            x1Start, y1Start, x1End, y1End,
                            x2Start, y2Start, x2End, y2End,
                        )
                        PinchAction.OUT -> shizuku().pinchOut(
                            x1Start, y1Start, x1End, y1End,
                            x2Start, y2Start, x2End, y2End,
                        )
                    }
                    if (!ok) {
                        mainHandler.post { showShizukuErrorToast() }
                    }
                }

                Log.i(
                    tag,
                    "pinch via Shizuku action=$action center=(${c.x},${c.y}) travel=$travel inner=$innerOffset outer=$outerOffset",
                )
            }
        }
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

    private fun clearPendingToggleTap() {
        mainHandler.removeCallbacks(commitToggleTapRunnable)
        pendingToggleTapAtMs = 0L
        pendingToggleTapKeyCode = null
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

    private fun stopMoveRepeat(event: KeyEvent) {
        val current = moveKeyCode
        if (current != null && matchesAssignedKey(current, event)) {
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

    private val cmdReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val op = intent?.getStringExtra("op") ?: return
            Log.i(tag, "received cmd broadcast: op=$op")
            mainHandler.post {
                when (op) {
                    "toggle_mode" -> toggleMode()
                    "set_mode" -> {
                        val target = intent.getStringExtra("mode")
                        if (target == "MOUSE" && mode != OperationMode.MOUSE) toggleMode()
                        else if (target == "NORMAL" && mode != OperationMode.NORMAL) toggleMode()
                    }
                    "move_cursor" -> {
                        val dx = intent.getIntExtra("dx", 0)
                        val dy = intent.getIntExtra("dy", 0)
                        cursor.moveBy(dx, dy)
                        val p = cursor.position()
                        lastCursorX = p.x
                        lastCursorY = p.y
                    }
                    "set_cursor" -> {
                        val x = intent.getIntExtra("x", 0)
                        val y = intent.getIntExtra("y", 0)
                        cursor.setPosition(x, y)
                        lastCursorX = x
                        lastCursorY = y
                    }
                    "tap" -> {
                        val c = cursor.center()
                        dispatchTap(c.x, c.y)
                    }
                    "double_tap" -> {
                        val c = cursor.center()
                        dispatchDoubleTap(c.x, c.y)
                    }
                    "long_press" -> {
                        val c = cursor.center()
                        when (settings.getEmulationMethod()) {
                            EmulationMethod.SHIZUKU -> {
                                if (settings.isTouchVisualFeedbackEnabled()) {
                                    cursor.showTapFeedback(isLongPress = true)
                                }
                                shizukuExecutor.execute {
                                    val ok = shizuku().longPress(c.x, c.y)
                                    if (!ok) mainHandler.post { showShizukuErrorToast() }
                                }
                            }
                            EmulationMethod.ACCESSIBILITY_SERVICE -> {
                                if (settings.isTouchVisualFeedbackEnabled()) {
                                    cursor.showTapFeedback(isLongPress = true)
                                }
                                gestures.longPress(c.x, c.y)
                            }
                        }
                    }
                    "swipe_up" -> dispatchScrollOrSwipe(SwipeAction.UP)
                    "swipe_down" -> dispatchScrollOrSwipe(SwipeAction.DOWN)
                    "swipe_left" -> dispatchScrollOrSwipe(SwipeAction.LEFT)
                    "swipe_right" -> dispatchScrollOrSwipe(SwipeAction.RIGHT)
                    "pinch_in" -> dispatchPinch(PinchAction.IN)
                    "pinch_out" -> dispatchPinch(PinchAction.OUT)
                }
            }
        }
    }

    private companion object {
        private const val MOVE_REPEAT_INITIAL_DELAY_MS = 120L
        private const val MOVE_REPEAT_INTERVAL_MS = 33L
    }
}
