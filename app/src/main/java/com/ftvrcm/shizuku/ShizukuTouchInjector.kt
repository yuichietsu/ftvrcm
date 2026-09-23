package com.ftvrcm.shizuku

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import android.view.InputDevice
import android.view.InputEvent
import android.view.MotionEvent
import androidx.core.content.edit
import com.ftvrcm.data.SettingsKeys
import org.lsposed.hiddenapibypass.HiddenApiBypass
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper
import java.lang.reflect.Method

class ShizukuTouchInjector(
    private val context: Context,
) {
    private val tag = "ShizukuTouchInjector"

    private var iInputManager: Any? = null
    private var injectInputEventMethod: Method? = null
    private var isThreeParamMethod: Boolean = false

    init {
        exemptHiddenApisIfNeeded()
    }

    companion object {
        private var hiddenApiExempted = false

        fun exemptHiddenApisIfNeeded() {
            if (hiddenApiExempted) return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                try {
                    HiddenApiBypass.addHiddenApiExemptions("L")
                    hiddenApiExempted = true
                } catch (t: Throwable) {
                    Log.w("ShizukuTouchInjector", "Failed to add HiddenApiExemptions", t)
                }
            }
        }

        fun isShizukuAvailable(): Boolean {
            return try {
                Shizuku.pingBinder()
            } catch (_: Throwable) {
                false
            }
        }

        fun isPermissionGranted(): Boolean {
            return try {
                if (!isShizukuAvailable()) return false
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            } catch (_: Throwable) {
                false
            }
        }
    }

    @Synchronized
    private fun getOrInitInputManager(): Boolean {
        if (iInputManager != null && injectInputEventMethod != null) {
            val binder = (iInputManager as? android.os.IInterface)?.asBinder()
            if (binder?.isBinderAlive == true) {
                return true
            }
        }

        iInputManager = null
        injectInputEventMethod = null

        if (!isPermissionGranted()) {
            Log.w(tag, "Shizuku permission is not granted or service unavailable")
            return false
        }

        exemptHiddenApisIfNeeded()

        try {
            val inputBinder = SystemServiceHelper.getSystemService("input")
            if (inputBinder == null) {
                Log.e(tag, "SystemServiceHelper.getSystemService(\"input\") returned null")
                return false
            }


            val wrappedBinder = ShizukuBinderWrapper(inputBinder)
            val stubClass = Class.forName("android.hardware.input.IInputManager\$Stub")
            val asInterfaceMethod = stubClass.getMethod("asInterface", IBinder::class.java)
            val managerInstance = asInterfaceMethod.invoke(null, wrappedBinder)
            if (managerInstance == null) {
                Log.e(tag, "IInputManager.Stub.asInterface returned null")
                return false
            }

            // Find injectInputEvent method
            val methods = managerInstance.javaClass.methods
            var foundMethod: Method? = null
            var threeParam = false

            for (m in methods) {
                if (m.name == "injectInputEvent") {
                    val pTypes = m.parameterTypes
                    if (pTypes.isNotEmpty() && InputEvent::class.java.isAssignableFrom(pTypes[0])) {
                        if (pTypes.size == 2 && pTypes[1] == Int::class.javaPrimitiveType) {
                            foundMethod = m
                            threeParam = false
                            break
                        } else if (pTypes.size == 3 && pTypes[1] == Int::class.javaPrimitiveType && pTypes[2] == Int::class.javaPrimitiveType) {
                            foundMethod = m
                            threeParam = true
                            break
                        }
                    }
                }
            }

            if (foundMethod == null) {
                Log.e(tag, "injectInputEvent method not found on ${managerInstance.javaClass.name}")
                return false
            }

            foundMethod.isAccessible = true
            iInputManager = managerInstance
            injectInputEventMethod = foundMethod
            isThreeParamMethod = threeParam
            Log.i(tag, "Successfully initialized IInputManager proxy via Shizuku (threeParam=$threeParam)")
            return true
        } catch (t: Throwable) {
            Log.e(tag, "Failed to initialize IInputManager proxy (${t.javaClass.simpleName}: ${t.message})", t)
            return false
        }
    }

    fun injectInputEvent(event: InputEvent, mode: Int = 0): Boolean {
        if (!getOrInitInputManager()) return false
        val method = injectInputEventMethod ?: return false
        val manager = iInputManager ?: return false

        return try {
            val result = if (isThreeParamMethod) {
                // targetUid = -1 (Process.INVALID_UID)
                method.invoke(manager, event, mode, -1)
            } else {
                method.invoke(manager, event, mode)
            }
            (result as? Boolean) ?: true
        } catch (t: Throwable) {
            Log.w(tag, "injectInputEvent failed (${t.javaClass.simpleName}: ${t.message})", t)
            iInputManager = null
            injectInputEventMethod = null
            false
        }
    }

    fun tap(x: Int, y: Int): Boolean {
        val type = "shizuku_tap"
        record(type = type, status = "DISPATCHING", detail = "x=$x y=$y")

        val downTime = SystemClock.uptimeMillis()
        val prop = createPointerProperties(0)
        val coord = createPointerCoords(x.toFloat(), y.toFloat())

        val eventDown = obtainMotionEvent(
            downTime = downTime,
            eventTime = downTime,
            action = MotionEvent.ACTION_DOWN,
            pointerCount = 1,
            properties = arrayOf(prop),
            coords = arrayOf(coord),
        )
        val okDown = try {
            injectInputEvent(eventDown)
        } finally {
            eventDown.recycle()
        }

        try {
            Thread.sleep(35)
        } catch (_: InterruptedException) {
        }

        val eventTime = SystemClock.uptimeMillis()
        val eventUp = obtainMotionEvent(
            downTime = downTime,
            eventTime = eventTime,
            action = MotionEvent.ACTION_UP,
            pointerCount = 1,
            properties = arrayOf(prop),
            coords = arrayOf(coord),
        )
        val okUp = try {
            injectInputEvent(eventUp)
        } finally {
            eventUp.recycle()
        }

        val ok = okDown && okUp
        record(
            type = type,
            status = if (ok) "COMPLETED" else "FAILED",
            detail = "x=$x y=$y okDown=$okDown okUp=$okUp",
        )
        return ok
    }

    fun doubleTap(x: Int, y: Int): Boolean {
        val type = "shizuku_double_tap"
        record(type = type, status = "DISPATCHING", detail = "x=$x y=$y")

        val firstOk = tap(x, y)
        try {
            Thread.sleep(70)
        } catch (_: InterruptedException) {
        }
        val secondOk = tap(x, y)

        val ok = firstOk && secondOk
        record(
            type = type,
            status = if (ok) "COMPLETED" else "FAILED",
            detail = "x=$x y=$y firstOk=$firstOk secondOk=$secondOk",
        )
        return ok
    }

    fun longPress(x: Int, y: Int, durationMs: Long = 600): Boolean {
        val type = "shizuku_long_press"
        record(type = type, status = "DISPATCHING", detail = "x=$x y=$y durationMs=$durationMs")

        val downTime = SystemClock.uptimeMillis()
        val prop = createPointerProperties(0)
        val coord = createPointerCoords(x.toFloat(), y.toFloat())

        val eventDown = obtainMotionEvent(
            downTime = downTime,
            eventTime = downTime,
            action = MotionEvent.ACTION_DOWN,
            pointerCount = 1,
            properties = arrayOf(prop),
            coords = arrayOf(coord),
        )
        val okDown = try {
            injectInputEvent(eventDown)
        } finally {
            eventDown.recycle()
        }

        try {
            Thread.sleep(durationMs.coerceAtLeast(100L))
        } catch (_: InterruptedException) {
        }

        val eventTime = SystemClock.uptimeMillis()
        val eventUp = obtainMotionEvent(
            downTime = downTime,
            eventTime = eventTime,
            action = MotionEvent.ACTION_UP,
            pointerCount = 1,
            properties = arrayOf(prop),
            coords = arrayOf(coord),
        )
        val okUp = try {
            injectInputEvent(eventUp)
        } finally {
            eventUp.recycle()
        }

        val ok = okDown && okUp
        record(
            type = type,
            status = if (ok) "COMPLETED" else "FAILED",
            detail = "x=$x y=$y okDown=$okDown okUp=$okUp",
        )
        return ok
    }

    fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Long = 200): Boolean {
        val type = "shizuku_swipe"
        record(type = type, status = "DISPATCHING", detail = "x1=$x1,y1=$y1 -> x2=$x2,y2=$y2 durationMs=$durationMs")

        val downTime = SystemClock.uptimeMillis()
        val prop = createPointerProperties(0)
        val coord = createPointerCoords(x1.toFloat(), y1.toFloat())

        val eventDown = obtainMotionEvent(
            downTime = downTime,
            eventTime = downTime,
            action = MotionEvent.ACTION_DOWN,
            pointerCount = 1,
            properties = arrayOf(prop),
            coords = arrayOf(coord),
        )
        var allOk = try {
            injectInputEvent(eventDown)
        } finally {
            eventDown.recycle()
        }

        val stepCount = (durationMs / 16).toInt().coerceIn(5, 50)
        val intervalMs = (durationMs / stepCount).coerceAtLeast(8L)

        for (i in 1..stepCount) {
            try {
                Thread.sleep(intervalMs)
            } catch (_: InterruptedException) {
                break
            }
            val t = i.toFloat() / stepCount
            val curX = x1 + (x2 - x1) * t
            val curY = y1 + (y2 - y1) * t
            coord.x = curX
            coord.y = curY

            val now = SystemClock.uptimeMillis()
            val eventMove = obtainMotionEvent(
                downTime = downTime,
                eventTime = now,
                action = MotionEvent.ACTION_MOVE,
                pointerCount = 1,
                properties = arrayOf(prop),
                coords = arrayOf(coord),
            )
            val okMove = try {
                injectInputEvent(eventMove)
            } finally {
                eventMove.recycle()
            }
            if (!okMove) allOk = false
        }

        try {
            Thread.sleep(12)
        } catch (_: InterruptedException) {
        }

        coord.x = x2.toFloat()
        coord.y = y2.toFloat()
        val eventUpTime = SystemClock.uptimeMillis()
        val eventUp = obtainMotionEvent(
            downTime = downTime,
            eventTime = eventUpTime,
            action = MotionEvent.ACTION_UP,
            pointerCount = 1,
            properties = arrayOf(prop),
            coords = arrayOf(coord),
        )
        val okUp = try {
            injectInputEvent(eventUp)
        } finally {
            eventUp.recycle()
        }
        if (!okUp) allOk = false

        record(
            type = type,
            status = if (allOk) "COMPLETED" else "FAILED",
            detail = "x1=$x1,y1=$y1 -> x2=$x2,y2=$y2 durationMs=$durationMs allOk=$allOk",
        )
        return allOk
    }

    /**
     * Native 2-finger multi-touch Pinch gesture injection.
     * Accurately constructs the full MotionEvent lifecycle:
     * 1. ACTION_DOWN (Pointer 0)
     * 2. ACTION_POINTER_DOWN (Pointer 1, index 1)
     * 3. Multiple ACTION_MOVE (Pointers 0 and 1 moving simultaneously)
     * 4. ACTION_POINTER_UP (Pointer 1, index 1)
     * 5. ACTION_UP (Pointer 0)
     */
    fun pinch(
        x1Start: Int,
        y1Start: Int,
        x1End: Int,
        y1End: Int,
        x2Start: Int,
        y2Start: Int,
        x2End: Int,
        y2End: Int,
        durationMs: Long = 240,
        isZoomOut: Boolean = false,
    ): Boolean {
        val type = if (isZoomOut) "shizuku_pinch_out" else "shizuku_pinch_in"
        val detail = "p1=($x1Start,$y1Start->$x1End,$y1End) p2=($x2Start,$y2Start->$x2End,$y2End) durationMs=$durationMs"
        record(type = type, status = "DISPATCHING", detail = detail)

        val downTime = SystemClock.uptimeMillis()

        val prop0 = createPointerProperties(0)
        val prop1 = createPointerProperties(1)

        val coord0 = createPointerCoords(x1Start.toFloat(), y1Start.toFloat())
        val coord1 = createPointerCoords(x2Start.toFloat(), y2Start.toFloat())

        // 1) Finger 0 DOWN
        val eventDown = obtainMotionEvent(
            downTime = downTime,
            eventTime = downTime,
            action = MotionEvent.ACTION_DOWN,
            pointerCount = 1,
            properties = arrayOf(prop0),
            coords = arrayOf(coord0),
        )
        var allOk = try {
            injectInputEvent(eventDown)
        } finally {
            eventDown.recycle()
        }

        try {
            Thread.sleep(16)
        } catch (_: InterruptedException) {
        }

        // 2) Finger 1 POINTER_DOWN (actionIndex = 1)
        val nowPointerDown = SystemClock.uptimeMillis()
        val actionPointerDown = MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)
        val eventPointerDown = obtainMotionEvent(
            downTime = downTime,
            eventTime = nowPointerDown,
            action = actionPointerDown,
            pointerCount = 2,
            properties = arrayOf(prop0, prop1),
            coords = arrayOf(coord0, coord1),
        )
        val okPointerDown = try {
            injectInputEvent(eventPointerDown)
        } finally {
            eventPointerDown.recycle()
        }
        if (!okPointerDown) allOk = false

        // 3) Smooth MOVE steps
        val stepCount = (durationMs / 16).toInt().coerceIn(6, 40)
        val intervalMs = (durationMs / stepCount).coerceAtLeast(8L)

        for (i in 1..stepCount) {
            try {
                Thread.sleep(intervalMs)
            } catch (_: InterruptedException) {
                break
            }

            val t = i.toFloat() / stepCount
            coord0.x = x1Start + (x1End - x1Start) * t
            coord0.y = y1Start + (y1End - y1Start) * t
            coord1.x = x2Start + (x2End - x2Start) * t
            coord1.y = y2Start + (y2End - y2Start) * t

            val nowMove = SystemClock.uptimeMillis()
            val eventMove = obtainMotionEvent(
                downTime = downTime,
                eventTime = nowMove,
                action = MotionEvent.ACTION_MOVE,
                pointerCount = 2,
                properties = arrayOf(prop0, prop1),
                coords = arrayOf(coord0, coord1),
            )
            val okMove = try {
                injectInputEvent(eventMove)
            } finally {
                eventMove.recycle()
            }
            if (!okMove) allOk = false
        }

        try {
            Thread.sleep(16)
        } catch (_: InterruptedException) {
        }

        // Ensure final positions
        coord0.x = x1End.toFloat()
        coord0.y = y1End.toFloat()
        coord1.x = x2End.toFloat()
        coord1.y = y2End.toFloat()

        // 4) Finger 1 POINTER_UP (actionIndex = 1)
        val nowPointerUp = SystemClock.uptimeMillis()
        val actionPointerUp = MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)
        val eventPointerUp = obtainMotionEvent(
            downTime = downTime,
            eventTime = nowPointerUp,
            action = actionPointerUp,
            pointerCount = 2,
            properties = arrayOf(prop0, prop1),
            coords = arrayOf(coord0, coord1),
        )
        val okPointerUp = try {
            injectInputEvent(eventPointerUp)
        } finally {
            eventPointerUp.recycle()
        }
        if (!okPointerUp) allOk = false

        try {
            Thread.sleep(16)
        } catch (_: InterruptedException) {
        }

        // 5) Finger 0 UP
        val nowUp = SystemClock.uptimeMillis()
        val eventUp = obtainMotionEvent(
            downTime = downTime,
            eventTime = nowUp,
            action = MotionEvent.ACTION_UP,
            pointerCount = 1,
            properties = arrayOf(prop0),
            coords = arrayOf(coord0),
        )
        val okUp = try {
            injectInputEvent(eventUp)
        } finally {
            eventUp.recycle()
        }
        if (!okUp) allOk = false

        record(
            type = type,
            status = if (allOk) "COMPLETED" else "FAILED",
            detail = "$detail allOk=$allOk",
        )
        return allOk
    }

    fun pinchIn(
        x1Start: Int,
        y1Start: Int,
        x1End: Int,
        y1End: Int,
        x2Start: Int,
        y2Start: Int,
        x2End: Int,
        y2End: Int,
        durationMs: Long = 240,
    ): Boolean {
        return pinch(
            x1Start = x1Start,
            y1Start = y1Start,
            x1End = x1End,
            y1End = y1End,
            x2Start = x2Start,
            y2Start = y2Start,
            x2End = x2End,
            y2End = y2End,
            durationMs = durationMs,
            isZoomOut = false,
        )
    }

    fun pinchOut(
        x1Start: Int,
        y1Start: Int,
        x1End: Int,
        y1End: Int,
        x2Start: Int,
        y2Start: Int,
        x2End: Int,
        y2End: Int,
        durationMs: Long = 240,
    ): Boolean {
        return pinch(
            x1Start = x1Start,
            y1Start = y1Start,
            x1End = x1End,
            y1End = y1End,
            x2Start = x2Start,
            y2Start = y2Start,
            x2End = x2End,
            y2End = y2End,
            durationMs = durationMs,
            isZoomOut = true,
        )
    }

    data class TestResult(
        val ok: Boolean,
        val detail: String,
    )

    fun testConnection(): TestResult {
        if (!isShizukuAvailable()) {
            return TestResult(ok = false, detail = "Shizukuサービスが起動していません。Shizukuアプリを起動してください。")
        }
        if (!isPermissionGranted()) {
            return TestResult(ok = false, detail = "Shizukuの権限が付与されていません。「Shizuku権限をリクエスト」を押してください。")
        }
        if (!getOrInitInputManager()) {
            return TestResult(ok = false, detail = "InputManager特権バインダーの初期化に失敗しました。")
        }
        return TestResult(ok = true, detail = "Shizuku特権Binderへの接続に成功しました（injectInputEvent利用可能）。")
    }

    private fun createPointerProperties(id: Int): MotionEvent.PointerProperties {
        return MotionEvent.PointerProperties().apply {
            this.id = id
            this.toolType = MotionEvent.TOOL_TYPE_FINGER
        }
    }

    private fun createPointerCoords(x: Float, y: Float): MotionEvent.PointerCoords {
        return MotionEvent.PointerCoords().apply {
            this.x = x
            this.y = y
            this.pressure = 1.0f
            this.size = 1.0f
        }
    }

    private fun obtainMotionEvent(
        downTime: Long,
        eventTime: Long,
        action: Int,
        pointerCount: Int,
        properties: Array<MotionEvent.PointerProperties>,
        coords: Array<MotionEvent.PointerCoords>,
    ): MotionEvent {
        return MotionEvent.obtain(
            downTime,
            eventTime,
            action,
            pointerCount,
            properties,
            coords,
            0, // metaState
            0, // buttonState
            1.0f, // xPrecision
            1.0f, // yPrecision
            0, // deviceId
            0, // edgeFlags
            InputDevice.SOURCE_TOUCHSCREEN,
            0, // flags
        )
    }

    private fun record(type: String, status: String, detail: String) {
        try {
            context.getSharedPreferences(SettingsKeys.PREFS_NAME, Context.MODE_PRIVATE)
                .edit {
                    putString(SettingsKeys.LAST_GESTURE_TYPE, type)
                    putString(SettingsKeys.LAST_GESTURE_STATUS, status)
                    putString(SettingsKeys.LAST_GESTURE_DETAIL, detail)
                    putLong(SettingsKeys.LAST_GESTURE_AT_MS, SystemClock.uptimeMillis())
                }
        } catch (_: Throwable) {
        }
    }
}
