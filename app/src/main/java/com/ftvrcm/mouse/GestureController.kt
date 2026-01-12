package com.ftvrcm.mouse

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.ftvrcm.data.SettingsStore
import java.util.concurrent.atomic.AtomicInteger

class GestureController(
    private val service: AccessibilityService,
    private val store: SettingsStore,
) {

    private val handler = Handler(Looper.getMainLooper())
    private val seq = AtomicInteger(0)

    fun tap(x: Int, y: Int): Boolean {
        val path = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
        val id = seq.incrementAndGet()
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, TAP_DURATION_MS))
            .build()

        store.setDebugLastGestureEvent("tap#$id start x=$x y=$y")
        val accepted = service.dispatchGesture(
            gesture,
            object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    store.setDebugLastGestureEvent("tap#$id completed x=$x y=$y")
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    // Fire OS may cancel gesture injection even when dispatchGesture() returned true.
                    // Fallback: try ACTION_CLICK on the node at the cursor position.
                    store.setDebugLastGestureEvent("tap#$id cancelled x=$x y=$y -> nodeClick")
                    val ok = performNodeClickAt(x, y)
                    store.setDebugLastGestureEvent(
                        if (ok) "tap#$id cancelled -> nodeClick OK" else "tap#$id cancelled -> nodeClick FAIL",
                    )
                }
            },
            handler,
        )
        if (!accepted) {
            store.setDebugLastGestureEvent("tap#$id rejected (dispatchGesture=false)")
        }
        return accepted
    }

    fun longPress(x: Int, y: Int, durationMs: Long = 600): Boolean {
        val path = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
        val id = seq.incrementAndGet()
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs))
            .build()

        store.setDebugLastGestureEvent("longPress#$id start x=$x y=$y dur=${durationMs}ms")
        val accepted = service.dispatchGesture(
            gesture,
            object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    store.setDebugLastGestureEvent("longPress#$id completed x=$x y=$y")
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    store.setDebugLastGestureEvent("longPress#$id cancelled x=$x y=$y")
                }
            },
            handler,
        )
        if (!accepted) {
            store.setDebugLastGestureEvent("longPress#$id rejected (dispatchGesture=false)")
        }
        return accepted
    }

    private companion object {
        private const val TAP_DURATION_MS = 60L
    }

    private fun performNodeClickAt(x: Int, y: Int): Boolean {
        val root = service.rootInActiveWindow ?: return false
        try {
            val bounds = Rect()
            var best: AccessibilityNodeInfo? = null
            var bestArea = Long.MAX_VALUE

            val stack = ArrayDeque<AccessibilityNodeInfo>()
            stack.add(root)

            while (stack.isNotEmpty()) {
                val node = stack.removeLast()

                node.getBoundsInScreen(bounds)
                if (bounds.contains(x, y)) {
                    val area = bounds.width().toLong() * bounds.height().toLong()
                    if (area in 1 until bestArea) {
                        best?.recycle()
                        best = AccessibilityNodeInfo.obtain(node)
                        bestArea = area
                    }
                }

                for (i in 0 until node.childCount) {
                    val child = node.getChild(i)
                    if (child != null) stack.add(child)
                }
            }

            if (best == null) return false

            // Prefer clicking the deepest clickable ancestor.
            var target: AccessibilityNodeInfo? = best
            while (target != null && !(target.isClickable && target.isEnabled)) {
                val parent = target.parent
                if (parent == null) break
                target = parent
            }

            val clicked = target?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
            best.recycle()
            target?.recycle()
            return clicked
        } catch (_: Exception) {
            return false
        }
    }
}
