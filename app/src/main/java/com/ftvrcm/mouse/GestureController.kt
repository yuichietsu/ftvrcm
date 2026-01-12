package com.ftvrcm.mouse

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import kotlin.math.abs
import androidx.core.content.edit
import com.ftvrcm.data.SettingsKeys

class GestureController(
    private val service: AccessibilityService,
) {

    private val handler = Handler(Looper.getMainLooper())

    private fun recordGesture(type: String, status: String, detail: String) {
        try {
            service.getSharedPreferences(SettingsKeys.PREFS_NAME, AccessibilityService.MODE_PRIVATE)
                .edit {
                    putString(SettingsKeys.LAST_GESTURE_TYPE, type)
                    putString(SettingsKeys.LAST_GESTURE_STATUS, status)
                    putString(SettingsKeys.LAST_GESTURE_DETAIL, detail)
                    putLong(SettingsKeys.LAST_GESTURE_AT_MS, android.os.SystemClock.uptimeMillis())
                }
        } catch (_: Exception) {
            // ignore
        }
    }

    fun tap(x: Int, y: Int): Boolean {
        val path = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, TAP_DURATION_MS))
            .build()

        recordGesture(type = "tap", status = "DISPATCHING", detail = "x=$x y=$y")

        val accepted = service.dispatchGesture(
            gesture,
            object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    recordGesture(type = "tap", status = "COMPLETED", detail = "x=$x y=$y")
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    // Fire OS may cancel gesture injection even when dispatchGesture() returned true.
                    // Fallback: try ACTION_CLICK on the node at the cursor position.
                    val ok = try {
                        performNodeClickAt(x, y)
                    } catch (_: Exception) {
                        null
                    }
                    recordGesture(
                        type = "tap",
                        status = "CANCELLED",
                        detail = if (ok == true) "fallback=CLICK ok" else "fallback=CLICK failed",
                    )
                }
            },
            handler,
        )

        if (!accepted) {
            recordGesture(type = "tap", status = "REJECTED", detail = "dispatchGesture returned false")
        }
        return accepted
    }

    fun longPress(x: Int, y: Int, durationMs: Long = 600): Boolean {
        val path = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs))
            .build()

        recordGesture(type = "long_press", status = "DISPATCHING", detail = "x=$x y=$y durationMs=$durationMs")

        val accepted = service.dispatchGesture(
            gesture,
            object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    recordGesture(type = "long_press", status = "COMPLETED", detail = "x=$x y=$y")
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    // Fire OS may cancel gesture injection. Fallback to ACTION_LONG_CLICK.
                    val ok = try {
                        performNodeLongClickAt(x, y)
                    } catch (_: Exception) {
                        null
                    }
                    recordGesture(
                        type = "long_press",
                        status = "CANCELLED",
                        detail = if (ok == true) "fallback=LONG_CLICK ok" else "fallback=LONG_CLICK failed",
                    )
                }
            },
            handler,
        )

        if (!accepted) {
            recordGesture(type = "long_press", status = "REJECTED", detail = "dispatchGesture returned false")
        }
        return accepted
    }

    fun doubleTap(x: Int, y: Int, intervalMs: Long = DOUBLE_TAP_INTERVAL_MS): Boolean {
        val p1 = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
        val p2 = Path().apply { moveTo(x.toFloat(), y.toFloat()) }

        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(p1, 0, TAP_DURATION_MS))
            .addStroke(GestureDescription.StrokeDescription(p2, TAP_DURATION_MS + intervalMs, TAP_DURATION_MS))
            .build()

        recordGesture(type = "double_tap", status = "DISPATCHING", detail = "x=$x y=$y")

        val accepted = service.dispatchGesture(
            gesture,
            object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    recordGesture(type = "double_tap", status = "COMPLETED", detail = "x=$x y=$y")
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    // Best-effort fallback: do nothing.
                    recordGesture(type = "double_tap", status = "CANCELLED", detail = "fallback=NONE")
                }
            },
            handler,
        )

        if (!accepted) {
            recordGesture(type = "double_tap", status = "REJECTED", detail = "dispatchGesture returned false")
        }
        return accepted
    }

    fun swipe(
        startX: Int,
        startY: Int,
        endX: Int,
        endY: Int,
        durationMs: Long = 260,
        enableFallback: Boolean = true,
    ): Boolean {
        val path = Path().apply {
            moveTo(startX.toFloat(), startY.toFloat())
            lineTo(endX.toFloat(), endY.toFloat())
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs))
            .build()

        recordGesture(
            type = "swipe",
            status = "DISPATCHING",
            detail = "start=($startX,$startY) end=($endX,$endY) durationMs=$durationMs",
        )

        val accepted = service.dispatchGesture(
            gesture,
            object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    recordGesture(
                        type = "swipe",
                        status = "COMPLETED",
                        detail = "start=($startX,$startY) end=($endX,$endY)",
                    )
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    if (!enableFallback) {
                        recordGesture(type = "swipe", status = "CANCELLED", detail = "fallback=DISABLED")
                        return
                    }

                    val dx = endX - startX
                    val dy = endY - startY
                    if (abs(dy) > abs(dx)) {
                        // Swipe up -> scroll down, swipe down -> scroll up.
                        // Prefer non-horizontal scroll containers so that vertical swipes don't become horizontal moves.
                        val primary = if (dy < 0) {
                            intArrayOf(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_DOWN.id)
                        } else {
                            intArrayOf(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_UP.id)
                        }
                        val secondary = if (dy < 0) {
                            intArrayOf(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                        } else {
                            intArrayOf(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
                        }
                        val ok = performNodeScrollAt(
                            x = startX,
                            y = startY,
                            primaryActions = primary,
                            secondaryActions = secondary,
                            preferTarget = { !isHorizontalScrollContainer(it) },
                        )
                        recordGesture(
                            type = "swipe",
                            status = "CANCELLED",
                            detail = if (ok == true) "fallback=SCROLL_VERTICAL ok" else "fallback=SCROLL_VERTICAL failed",
                        )
                    } else {
                        // Swipe left -> scroll right, swipe right -> scroll left.
                        // Prefer horizontal containers; fall back to forward/backward if needed.
                        val primary = if (dx < 0) {
                            intArrayOf(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_RIGHT.id)
                        } else {
                            intArrayOf(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_LEFT.id)
                        }
                        val secondary = if (dx < 0) {
                            intArrayOf(
                                AccessibilityNodeInfo.ACTION_SCROLL_FORWARD,
                                AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_LEFT.id,
                            )
                        } else {
                            intArrayOf(
                                AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD,
                                AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_RIGHT.id,
                            )
                        }
                        val ok = performNodeScrollAt(
                            x = startX,
                            y = startY,
                            primaryActions = primary,
                            secondaryActions = secondary,
                            preferTarget = { isHorizontalScrollContainer(it) },
                        )
                        recordGesture(
                            type = "swipe",
                            status = "CANCELLED",
                            detail = if (ok == true) "fallback=SCROLL_HORIZONTAL ok" else "fallback=SCROLL_HORIZONTAL failed",
                        )
                    }
                }
            },
            handler,
        )

        if (!accepted) {
            recordGesture(type = "swipe", status = "REJECTED", detail = "dispatchGesture returned false")
        }

        return accepted
    }

    private companion object {
        private const val TAP_DURATION_MS = 60L
        private const val DOUBLE_TAP_INTERVAL_MS = 80L
    }

    private fun performNodeClickAt(x: Int, y: Int): Boolean? {
        val root = service.rootInActiveWindow ?: return null
        var best: AccessibilityNodeInfo? = null
        try {
            val bounds = Rect()
            var bestArea = Long.MAX_VALUE

            val stack = ArrayDeque<AccessibilityNodeInfo>()
            stack.add(root)

            while (stack.isNotEmpty()) {
                val node = stack.removeLast()
                try {
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
                } finally {
                    // recycle traversal nodes; 'best' is a separate obtained copy.
                    node.recycle()
                }
            }

            val bestNode = best ?: return null

            // Prefer clicking the closest clickable ancestor.
            var target: AccessibilityNodeInfo? = bestNode
            while (target != null && !(target.isClickable && target.isEnabled)) {
                val parent = target.parent
                if (parent == null) break
                if (target !== bestNode) {
                    target.recycle()
                }
                target = parent
            }

            val ret = target?.performAction(AccessibilityNodeInfo.ACTION_CLICK)

            if (target != null && target !== bestNode) {
                target.recycle()
            }
            bestNode.recycle()
            return ret
        } catch (_: Exception) {
            try {
                best?.recycle()
            } catch (_: Exception) {
            }
            return null
        }
    }

    private fun performNodeLongClickAt(x: Int, y: Int): Boolean? {
        val root = service.rootInActiveWindow ?: return null
        var best: AccessibilityNodeInfo? = null
        try {
            val bounds = Rect()
            var bestArea = Long.MAX_VALUE

            val stack = ArrayDeque<AccessibilityNodeInfo>()
            stack.add(root)

            while (stack.isNotEmpty()) {
                val node = stack.removeLast()
                try {
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
                } finally {
                    node.recycle()
                }
            }

            val bestNode = best ?: return null

            // Prefer long-clickable ancestor.
            var target: AccessibilityNodeInfo? = bestNode
            while (target != null && !(target.isLongClickable && target.isEnabled)) {
                val parent = target.parent
                if (parent == null) break
                if (target !== bestNode) {
                    target.recycle()
                }
                target = parent
            }

            val ret = target?.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)

            if (target != null && target !== bestNode) {
                target.recycle()
            }
            bestNode.recycle()
            return ret
        } catch (_: Exception) {
            try {
                best?.recycle()
            } catch (_: Exception) {
            }
            return null
        }
    }

    private fun isHorizontalScrollContainer(node: AccessibilityNodeInfo): Boolean {
        val cn = node.className?.toString() ?: return false
        return cn.contains("HorizontalScrollView")
    }

    private fun performNodeScrollAt(
        x: Int,
        y: Int,
        primaryActions: IntArray,
        secondaryActions: IntArray,
        preferTarget: (AccessibilityNodeInfo) -> Boolean,
    ): Boolean? {
        val root = service.rootInActiveWindow ?: return null
        var best: AccessibilityNodeInfo? = null
        try {
            val bounds = Rect()
            var bestArea = Long.MAX_VALUE

            val stack = ArrayDeque<AccessibilityNodeInfo>()
            stack.add(root)

            while (stack.isNotEmpty()) {
                val node = stack.removeLast()
                try {
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
                } finally {
                    node.recycle()
                }
            }

            val bestNode = best ?: return null

            val chain = mutableListOf<AccessibilityNodeInfo>()
            var current: AccessibilityNodeInfo? = bestNode
            while (current != null) {
                chain.add(AccessibilityNodeInfo.obtain(current))
                val parent = current.parent
                if (current !== bestNode) current.recycle()
                current = parent
            }

            fun tryOnChain(onlyPreferred: Boolean): Boolean {
                for (n in chain) {
                    if (!n.isScrollable) continue
                    if (onlyPreferred && !preferTarget(n)) continue

                    for (a in primaryActions) {
                        val ok = try {
                            n.performAction(a)
                        } catch (_: Exception) {
                            null
                        }
                        if (ok == true) return true
                    }

                    for (a in secondaryActions) {
                        val ok = try {
                            n.performAction(a)
                        } catch (_: Exception) {
                            null
                        }
                        if (ok == true) return true
                    }
                }
                return false
            }

            val ret = tryOnChain(onlyPreferred = true) || tryOnChain(onlyPreferred = false)

            for (n in chain) {
                try {
                    n.recycle()
                } catch (_: Exception) {
                }
            }
            bestNode.recycle()
            return if (ret) true else null
        } catch (_: Exception) {
            try {
                best?.recycle()
            } catch (_: Exception) {
            }
            return null
        }
    }
}
