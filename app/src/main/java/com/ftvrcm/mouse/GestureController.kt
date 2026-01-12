package com.ftvrcm.mouse

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import kotlin.math.abs

class GestureController(
    private val service: AccessibilityService,
) {

    private val handler = Handler(Looper.getMainLooper())

    private fun dispatchGestureWithRetry(
        gesture: GestureDescription,
        retries: Int,
        onFinalCancelled: () -> Unit,
    ): Boolean {
        fun attempt(remaining: Int): Boolean {
            return service.dispatchGesture(
                gesture,
                object : AccessibilityService.GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) {
                        // no-op
                    }

                    override fun onCancelled(gestureDescription: GestureDescription?) {
                        if (remaining > 0) {
                            // Retry shortly after cancellation. This reduces cases where Fire OS cancels sporadically.
                            handler.postDelayed({ attempt(remaining - 1) }, 30L)
                        } else {
                            onFinalCancelled()
                        }
                    }
                },
                handler,
            )
        }

        return attempt(retries)
    }

    fun tap(x: Int, y: Int): Boolean {
        val path = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, TAP_DURATION_MS))
            .build()

        val accepted = service.dispatchGesture(
            gesture,
            object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    // Fire OS may cancel gesture injection even when dispatchGesture() returned true.
                    // Fallback: try ACTION_CLICK on the node at the cursor position.
                    performNodeClickAt(x, y)
                }
            },
            handler,
        )
        return accepted
    }

    fun longPress(x: Int, y: Int, durationMs: Long = 600): Boolean {
        val path = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs))
            .build()

        val accepted = service.dispatchGesture(
            gesture,
            object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    // Fire OS may cancel gesture injection. Fallback to ACTION_LONG_CLICK.
                    performNodeLongClickAt(x, y)
                }
            },
            handler,
        )
        return accepted
    }

    fun doubleTap(x: Int, y: Int, intervalMs: Long = DOUBLE_TAP_INTERVAL_MS): Boolean {
        val p1 = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
        val p2 = Path().apply { moveTo(x.toFloat(), y.toFloat()) }

        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(p1, 0, TAP_DURATION_MS))
            .addStroke(GestureDescription.StrokeDescription(p2, TAP_DURATION_MS + intervalMs, TAP_DURATION_MS))
            .build()

        val accepted = service.dispatchGesture(
            gesture,
            object : AccessibilityService.GestureResultCallback() {
                override fun onCancelled(gestureDescription: GestureDescription?) {
                    // Best-effort fallback: do nothing.
                }
            },
            handler,
        )
        return accepted
    }

    fun swipe(startX: Int, startY: Int, endX: Int, endY: Int, durationMs: Long = 260): Boolean {
        val path = Path().apply {
            moveTo(startX.toFloat(), startY.toFloat())
            lineTo(endX.toFloat(), endY.toFloat())
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs))
            .build()

        // Fire OS may cancel gesture injection sporadically.
        // Retry a few times before falling back to accessibility scroll actions.
        return dispatchGestureWithRetry(
            gesture = gesture,
            retries = 2,
            onFinalCancelled = {
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
                    performNodeScrollAt(
                        x = startX,
                        y = startY,
                        primaryActions = primary,
                        secondaryActions = secondary,
                        preferTarget = { !isHorizontalScrollContainer(it) },
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
                    performNodeScrollAt(
                        x = startX,
                        y = startY,
                        primaryActions = primary,
                        secondaryActions = secondary,
                        preferTarget = { isHorizontalScrollContainer(it) },
                    )
                }
            },
        )
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
