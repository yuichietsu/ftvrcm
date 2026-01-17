package com.ftvrcm.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
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
import com.ftvrcm.proxy.ProxyInputClient
import com.ftvrcm.util.KeyCaptureState
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

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

    private var proxyInput: ProxyInputClient? = null
    private var proxyHost: String? = null
    private var proxyPort: Int? = null
    private var proxyToken: String? = null

    private var mode: OperationMode = OperationMode.NORMAL

    private var lastCursorX: Int = 0
    private var lastCursorY: Int = 0

    private var tapKeyIsDown: Boolean = false
    private var tapKeyLongPressTriggered: Boolean = false

    private var pendingTapAtMs: Long = 0L
    private var pendingTapX: Int = 0
    private var pendingTapY: Int = 0
    private val commitSingleTapRunnable = Runnable {
        val x = pendingTapX
        val y = pendingTapY
        pendingTapAtMs = 0L
        dispatchTap(x, y)
    }

    private var scrollSelectKeyIsDown: Boolean = false
    private var scrollSelectAction: SwipeAction? = null
    private var scrollSelectKeyLongPressTriggered: Boolean = false

    private var isDpadMode: Boolean = false

    private val proxyExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val proxyInputInFlight = AtomicBoolean(false)

    private var enterMouseModeInProgress: Boolean = false

    private val tapKeyLongPressRunnable = Runnable {
        // If long-press was recognized, it should not later become a single-tap.
        mainHandler.removeCallbacks(commitSingleTapRunnable)
        pendingTapAtMs = 0L

        if (mode != OperationMode.MOUSE) return@Runnable
        if (!tapKeyIsDown) return@Runnable
        if (tapKeyLongPressTriggered) return@Runnable

        tapKeyLongPressTriggered = true
        clearMoveRepeat()
        val c = cursor.center()

        when (settings.getEmulationMethod()) {
            EmulationMethod.ACCESSIBILITY_SERVICE -> {
                if (settings.isTouchVisualFeedbackEnabled()) {
                    cursor.showTapFeedback(isLongPress = true)
                }
                gestures.longPress(c.x, c.y)
            }

            EmulationMethod.PROXY -> {
                val accepted = dispatchProxyInput(
                    op = "longPress",
                    block = { proxy()?.longPress(c.x, c.y) == true },
                    onCompletedOnMainThread = { _ -> },
                )

                if (accepted && settings.isTouchVisualFeedbackEnabled()) {
                    cursor.showTapFeedback(isLongPress = true)
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

    private var pendingSwipeAtMs: Long = 0L
    private var pendingSwipeAction: SwipeAction? = null
    private val commitSwipeRunnable = Runnable {
        val action = pendingSwipeAction ?: return@Runnable
        pendingSwipeAtMs = 0L
        pendingSwipeAction = null
        dispatchScrollOrSwipe(action, distanceScale = 1.0f)
    }

    private var pendingPinchAtMs: Long = 0L
    private var pendingPinchAction: PinchAction? = null
    private val commitPinchRunnable = Runnable {
        val action = pendingPinchAction ?: return@Runnable
        pendingPinchAtMs = 0L
        pendingPinchAction = null
        dispatchPinch(action, distanceScale = 1.0f)
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

            dispatchScrollOrSwipe(action, distanceScale = 1.0f)
            mainHandler.postDelayed(this, settings.getMouseScrollRepeatIntervalMs().toLong())
        }
    }

    private val scrollKeyLongPressRunnable = Runnable {
        if (mode != OperationMode.MOUSE) return@Runnable
        if (!scrollSelectKeyIsDown) return@Runnable
        if (scrollSelectKeyLongPressTriggered) return@Runnable
        if (!settings.isMouseScrollRepeatLongPress()) return@Runnable

        scrollSelectKeyLongPressTriggered = true
        clearPendingSwipe()
        val action = scrollSelectAction ?: return@Runnable
        dispatchScrollOrSwipe(action, distanceScale = 1.0f)
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
        } catch (t: Throwable) {
            Log.e(tag, "service init failed (${t.javaClass.simpleName}: ${t.message})")
            try {
                disableSelf()
            } catch (_: Throwable) {
                // ignore
            }
        }
    }

    private fun proxy(): ProxyInputClient? {
        val host = settings.getProxyHost()
        val port = settings.getProxyPort()
        val token = settings.getProxyToken()

        val current = proxyInput
        if (current != null && proxyHost == host && proxyPort == port && proxyToken == token) return current

        proxyInput = ProxyInputClient(this, host = host, port = port, token = token).also {
            proxyHost = host
            proxyPort = port
            proxyToken = token
        }

        return proxyInput
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

                    // For accessibility injection, show feedback immediately for responsiveness.
                    if (settings.getEmulationMethod() == EmulationMethod.ACCESSIBILITY_SERVICE && settings.isTouchVisualFeedbackEnabled()) {
                        cursor.showTapFeedback(isLongPress = false)
                    }

                    scheduleTapOrDoubleTap(c.x, c.y)
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

                    scheduleSwipeOrDoubleSwipe(scrollAction)
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
                    schedulePinchOrDoublePinch(pinchAction)
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
        clearPendingToggle()
        clearPendingToggleTap()
        clearPendingTapKey()
        clearPendingScrollRepeat()
        clearPendingSwipe()
        clearPendingPinch()
        clearMoveRepeat()
        cursor.hide()
        try {
            proxyExecutor.shutdownNow()
        } catch (_: Throwable) {
        }
        super.onDestroy()
    }

    private fun dispatchProxy(op: String, block: () -> Unit) {
        // Never do network I/O on the main thread (Fire OS throws NetworkOnMainThreadException).
        proxyExecutor.execute {
            try {
                block()
            } catch (t: Throwable) {
                Log.w(tag, "proxy dispatch failed op=$op (${t.javaClass.simpleName}: ${t.message})")
            }
        }
    }

    private fun dispatchProxyInput(
        op: String,
        block: () -> Boolean,
        onCompletedOnMainThread: (ok: Boolean) -> Unit,
    ): Boolean {
        // Avoid request queues: allow only one in-flight proxy input; drop subsequent inputs.
        if (!proxyInputInFlight.compareAndSet(false, true)) {
            Log.i(tag, "proxy input dropped (busy) op=$op")
            return false
        }

        proxyExecutor.execute {
            val ok = try {
                block()
            } catch (t: Throwable) {
                Log.w(tag, "proxy input failed op=$op (${t.javaClass.simpleName}: ${t.message})")
                false
            } finally {
                proxyInputInFlight.set(false)
            }

            mainHandler.post {
                try {
                    onCompletedOnMainThread(ok)
                } catch (_: Throwable) {
                }
                if (!ok) {
                    showProxyErrorToast()
                }
            }
        }

        return true
    }

    private fun toggleMode() {
        clearMoveRepeat()
        clearPendingTapKey()
        clearPendingSwipe()
        clearPendingPinch()
        clearPendingToggleTap()
        val target = mode.toggle()

        if (target == OperationMode.MOUSE) {
            if (enterMouseModeInProgress) {
                showToast("切り替え処理中です…")
                return
            }

            when (settings.getEmulationMethod()) {
                EmulationMethod.ACCESSIBILITY_SERVICE -> {
                    if (!canPerformGesturesViaAccessibility()) {
                        showToast("タッチ操作へ切り替えできません（アクセシビリティサービスがジェスチャを実行できません）")
                        return
                    }

                    applyMode(target)
                    return
                }

                EmulationMethod.PROXY -> {
                    enterMouseModeInProgress = true

                    // Run health check off the main thread; apply mode only if it succeeds.
                    proxyExecutor.execute {
                        val result = try {
                            proxy()?.healthCheck()
                        } catch (t: Throwable) {
                            ProxyInputClient.HealthCheckResult(
                                ok = false,
                                detail = "${t.javaClass.simpleName}: ${t.message}",
                            )
                        }

                        mainHandler.post {
                            enterMouseModeInProgress = false
                            if (result?.ok == true) {
                                applyMode(target)
                            } else {
                                val detail = (result?.detail ?: "unknown error").trim().take(200)
                                showToast("タッチ操作へ切り替えできません（ADBプロキシに接続できません）: $detail")
                            }
                        }
                    }

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

    private fun showProxyErrorToast() {
        val detail = getLastProxyErrorDetail()
        showToast("ADB操作に失敗しました${if (!detail.isNullOrBlank()) ": $detail" else ""}")
    }

    private fun getLastProxyErrorDetail(maxLen: Int = 120): String? {
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
        clearPendingSwipe()
        clearPendingPinch()

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
        if (settings.getEmulationMethod() != EmulationMethod.PROXY) {
            showToast("スクリーンショットはADBプロキシが必要です")
            return
        }

        val wasVisible = cursor.isVisible()
        if (wasVisible) {
            cursor.hide()
        }

        proxyExecutor.execute {
            val result = runCatching { proxy()?.captureScreenshot() }
                .getOrNull()

            mainHandler.post {
                if (wasVisible && mode == OperationMode.MOUSE) {
                    cursor.show()
                    updateCursorStyleForInputMode()
                }

                if (result?.ok == true) {
                    showToast("スクリーンショットを保存しました")
                } else {
                    val detail = result?.detail?.trim()?.take(120)
                    showToast("スクリーンショットに失敗しました（ADB）${if (!detail.isNullOrEmpty()) ": $detail" else ""}")
                }
            }
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

    private fun scheduleSwipeOrDoubleSwipe(action: SwipeAction) {
        val now = SystemClock.uptimeMillis()
        val doubleTapTimeoutMs = ViewConfiguration.getDoubleTapTimeout().toLong()

        if (pendingSwipeAtMs != 0L && pendingSwipeAction == action && now - pendingSwipeAtMs <= doubleTapTimeoutMs) {
            mainHandler.removeCallbacks(commitSwipeRunnable)
            pendingSwipeAtMs = 0L
            pendingSwipeAction = null
            dispatchScrollOrSwipe(action, distanceScale = settings.getMouseSwipeDoubleScale())
            return
        }

        pendingSwipeAtMs = now
        pendingSwipeAction = action
        mainHandler.postDelayed(commitSwipeRunnable, doubleTapTimeoutMs)
    }

    private fun schedulePinchOrDoublePinch(action: PinchAction) {
        val now = SystemClock.uptimeMillis()
        val doubleTapTimeoutMs = ViewConfiguration.getDoubleTapTimeout().toLong()

        if (pendingPinchAtMs != 0L && pendingPinchAction == action && now - pendingPinchAtMs <= doubleTapTimeoutMs) {
            mainHandler.removeCallbacks(commitPinchRunnable)
            pendingPinchAtMs = 0L
            pendingPinchAction = null
            dispatchPinch(action, distanceScale = settings.getMousePinchDoubleScale())
            return
        }

        pendingPinchAtMs = now
        pendingPinchAction = action
        mainHandler.postDelayed(commitPinchRunnable, doubleTapTimeoutMs)
    }

    private fun clearPendingTapKey() {
        mainHandler.removeCallbacks(tapKeyLongPressRunnable)
        mainHandler.removeCallbacks(commitSingleTapRunnable)
        tapKeyIsDown = false
        tapKeyLongPressTriggered = false
        pendingTapAtMs = 0L
    }

    private fun dispatchTap(x: Int, y: Int) {
        when (settings.getEmulationMethod()) {
            EmulationMethod.ACCESSIBILITY_SERVICE -> {
                Log.i(tag, "tap via accessibility at (${x},${y})")
                gestures.tap(x, y)
            }

            EmulationMethod.PROXY -> {
                Log.i(tag, "tap via proxy at (${x},${y})")
                val accepted = dispatchProxyInput(
                    op = "tap",
                    block = { proxy()?.tap(x, y) == true },
                    onCompletedOnMainThread = { _ -> },
                )

                if (accepted && settings.isTouchVisualFeedbackEnabled()) {
                    cursor.showTapFeedback(isLongPress = false)
                }
            }
        }
    }

    private fun dispatchDoubleTap(x: Int, y: Int) {
        when (settings.getEmulationMethod()) {
            EmulationMethod.ACCESSIBILITY_SERVICE -> {
                Log.i(tag, "doubleTap via accessibility at (${x},${y})")
                gestures.doubleTap(x, y)
            }

            EmulationMethod.PROXY -> {
                Log.i(tag, "doubleTap via proxy at (${x},${y})")
                val accepted = dispatchProxyInput(
                    op = "doubleTap",
                    block = { proxy()?.doubleTap(x, y) == true },
                    onCompletedOnMainThread = { _ -> },
                )

                if (accepted && settings.isTouchVisualFeedbackEnabled()) {
                    cursor.showTapFeedback(isLongPress = false)
                    mainHandler.postDelayed({
                        cursor.showTapFeedback(isLongPress = false)
                    }, 90L)
                }
            }
        }
    }

    private fun scheduleTapOrDoubleTap(x: Int, y: Int) {
        val now = SystemClock.uptimeMillis()
        val doubleTapTimeoutMs = ViewConfiguration.getDoubleTapTimeout().toLong()

        if (pendingTapAtMs != 0L && now - pendingTapAtMs <= doubleTapTimeoutMs) {
            // Convert to double tap.
            mainHandler.removeCallbacks(commitSingleTapRunnable)
            pendingTapAtMs = 0L
            dispatchDoubleTap(x, y)
            return
        }

        pendingTapAtMs = now
        pendingTapX = x
        pendingTapY = y
        mainHandler.postDelayed(commitSingleTapRunnable, doubleTapTimeoutMs)
    }

    private fun clearPendingScrollRepeat() {
        mainHandler.removeCallbacks(scrollKeyLongPressRunnable)
        mainHandler.removeCallbacks(scrollRepeatRunnable)
    }

    private fun dispatchScrollOrSwipe(action: SwipeAction, distanceScale: Float) {
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

            EmulationMethod.PROXY -> {
                val dm = resources.displayMetrics
                val w = dm.widthPixels
                val h = dm.heightPixels
                val distancePercent = settings.getMouseSwipeDistancePercent()
                val baseDistance = ((minOf(w, h) * (distancePercent / 100.0))).toInt().coerceIn(40, minOf(w, h) - 1)
                val distance = (baseDistance * distanceScale).toInt().coerceIn(40, minOf(w, h) - 1)

                fun clampX(x: Int) = x.coerceIn(0, w - 1)
                fun clampY(y: Int) = y.coerceIn(0, h - 1)

                // Swipe starting at the current cursor center.
                when (action) {
                    SwipeAction.UP -> {
                        val x1 = clampX(c.x)
                        val y1 = clampY(c.y)
                        val x2 = clampX(c.x)
                        val y2 = clampY(c.y - distance)
                        val accepted = dispatchProxyInput(
                            op = "swipe_up",
                            block = { proxy()?.swipe(x1, y1, x2, y2) == true },
                            onCompletedOnMainThread = { _ -> },
                        )
                        if (accepted && visualFeedback) cursor.showSwipeTrail(x1, y1, x2, y2)
                    }

                    SwipeAction.DOWN -> {
                        val x1 = clampX(c.x)
                        val y1 = clampY(c.y)
                        val x2 = clampX(c.x)
                        val y2 = clampY(c.y + distance)
                        val accepted = dispatchProxyInput(
                            op = "swipe_down",
                            block = { proxy()?.swipe(x1, y1, x2, y2) == true },
                            onCompletedOnMainThread = { _ -> },
                        )
                        if (accepted && visualFeedback) cursor.showSwipeTrail(x1, y1, x2, y2)
                    }

                    SwipeAction.LEFT -> {
                        val x1 = clampX(c.x)
                        val y1 = clampY(c.y)
                        val x2 = clampX(c.x - distance)
                        val y2 = clampY(c.y)
                        val accepted = dispatchProxyInput(
                            op = "swipe_left",
                            block = { proxy()?.swipe(x1, y1, x2, y2) == true },
                            onCompletedOnMainThread = { _ -> },
                        )
                        if (accepted && visualFeedback) cursor.showSwipeTrail(x1, y1, x2, y2)
                    }

                    SwipeAction.RIGHT -> {
                        val x1 = clampX(c.x)
                        val y1 = clampY(c.y)
                        val x2 = clampX(c.x + distance)
                        val y2 = clampY(c.y)
                        val accepted = dispatchProxyInput(
                            op = "swipe_right",
                            block = { proxy()?.swipe(x1, y1, x2, y2) == true },
                            onCompletedOnMainThread = { _ -> },
                        )
                        if (accepted && visualFeedback) cursor.showSwipeTrail(x1, y1, x2, y2)
                    }
                }

                Log.i(
                    tag,
                    "swipe via proxy action=$action center=(${c.x},${c.y}) distance=$distance (${distancePercent}%)",
                )
            }
        }
    }

    private fun dispatchPinch(action: PinchAction, distanceScale: Float) {
        val dm = resources.displayMetrics
        val w = dm.widthPixels
        val h = dm.heightPixels
        val minSide = minOf(w, h)
        val distancePercent = settings.getMousePinchDistancePercent()
        val baseDistance = ((minSide * (distancePercent / 100.0))).toInt().coerceIn(40, minSide - 1)
        val distance = (baseDistance * distanceScale).toInt().coerceIn(40, minSide - 1)
        val minGap = (distance * 0.35f).toInt().coerceAtLeast(24)
        val half = (distance / 2).coerceAtLeast(minGap + 1)

        val c = cursor.center()
        fun clampX(x: Int) = x.coerceIn(0, w - 1)
        fun clampY(y: Int) = y.coerceIn(0, h - 1)

        val (startOffset, endOffset) = if (action == PinchAction.IN) {
            half to minGap
        } else {
            minGap to half
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
                    cursor.showPinchFeedback(isZoomOut = action == PinchAction.OUT)
                }

                Log.i(
                    tag,
                    "pinch via accessibility action=$action center=(${c.x},${c.y}) distance=$distance (${distancePercent}%)",
                )
            }

            EmulationMethod.PROXY -> {
                val accepted = dispatchProxyInput(
                    op = if (action == PinchAction.IN) "pinch_in" else "pinch_out",
                    block = {
                        when (action) {
                            PinchAction.IN -> proxy()?.pinchIn(
                                x1Start,
                                y1Start,
                                x1End,
                                y1End,
                                x2Start,
                                y2Start,
                                x2End,
                                y2End,
                            ) == true
                            PinchAction.OUT -> proxy()?.pinchOut(
                                x1Start,
                                y1Start,
                                x1End,
                                y1End,
                                x2Start,
                                y2Start,
                                x2End,
                                y2End,
                            ) == true
                        }
                    },
                    onCompletedOnMainThread = { _ -> },
                )

                if (accepted && settings.isTouchVisualFeedbackEnabled()) {
                    cursor.showPinchFeedback(isZoomOut = action == PinchAction.OUT)
                }

                Log.i(
                    tag,
                    "pinch via proxy action=$action center=(${c.x},${c.y}) distance=$distance (${distancePercent}%)",
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

    private fun clearPendingSwipe() {
        mainHandler.removeCallbacks(commitSwipeRunnable)
        pendingSwipeAtMs = 0L
        pendingSwipeAction = null
    }

    private fun clearPendingPinch() {
        mainHandler.removeCallbacks(commitPinchRunnable)
        pendingPinchAtMs = 0L
        pendingPinchAction = null
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

    private companion object {
        private const val MOVE_REPEAT_INITIAL_DELAY_MS = 120L
        private const val MOVE_REPEAT_INTERVAL_MS = 33L
    }
}
