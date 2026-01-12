package com.ftvrcm.mouse

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.graphics.Rect
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
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
        recordGesture(type = "tap", status = "DISPATCHING", detail = "x=$x y=$y")
        val ok = try {
            performNodeClickAt(x, y)
        } catch (_: Exception) {
            null
        }
        recordGesture(
            type = "tap",
            status = if (ok == true) "COMPLETED" else "REJECTED",
            detail = if (ok == true) "via=CLICK x=$x y=$y" else "via=CLICK failed x=$x y=$y",
        )
        return ok == true
    }

    fun longPress(x: Int, y: Int, durationMs: Long = 600): Boolean {
        recordGesture(type = "long_press", status = "DISPATCHING", detail = "x=$x y=$y durationMs=$durationMs")
        val ok = try {
            performNodeLongClickAt(x, y)
        } catch (_: Exception) {
            null
        }
        recordGesture(
            type = "long_press",
            status = if (ok == true) "COMPLETED" else "REJECTED",
            detail = if (ok == true) "via=LONG_CLICK x=$x y=$y" else "via=LONG_CLICK failed x=$x y=$y",
        )
        return ok == true
    }

    fun doubleTap(x: Int, y: Int, intervalMs: Long = DOUBLE_TAP_INTERVAL_MS): Boolean {
        recordGesture(type = "double_tap", status = "DISPATCHING", detail = "x=$x y=$y")

        val firstOk = try {
            performNodeClickAt(x, y)
        } catch (_: Exception) {
            null
        }

        handler.postDelayed(
            {
                val secondOk = try {
                    performNodeClickAt(x, y)
                } catch (_: Exception) {
                    null
                }

                recordGesture(
                    type = "double_tap",
                    status = if (firstOk == true && secondOk == true) "COMPLETED" else "REJECTED",
                    detail = "via=CLICKx2 x=$x y=$y",
                )
            },
            intervalMs.coerceAtLeast(40L),
        )

        if (firstOk != true) {
            recordGesture(type = "double_tap", status = "REJECTED", detail = "via=CLICKx2 failed (first click)")
        }

        return firstOk == true
    }

    fun scrollUp(x: Int, y: Int): Boolean {
        recordGesture(type = "scroll_up", status = "DISPATCHING", detail = "x=$x y=$y")
        val ok = try {
            performNodeScrollAt(
                x = x,
                y = y,
                primaryActions = intArrayOf(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_UP.id),
                secondaryActions = intArrayOf(),
                preferTarget = { !isHorizontalScrollContainer(it) },
            )
        } catch (_: Exception) {
            null
        }
        recordGesture(
            type = "scroll_up",
            status = if (ok == true) "COMPLETED" else "REJECTED",
            detail = if (ok == true) "via=SCROLL_UP ok" else "via=SCROLL_UP failed",
        )
        return ok == true
    }

    fun scrollDown(x: Int, y: Int): Boolean {
        recordGesture(type = "scroll_down", status = "DISPATCHING", detail = "x=$x y=$y")
        val ok = try {
            performNodeScrollAt(
                x = x,
                y = y,
                primaryActions = intArrayOf(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_DOWN.id),
                secondaryActions = intArrayOf(),
                preferTarget = { !isHorizontalScrollContainer(it) },
            )
        } catch (_: Exception) {
            null
        }
        recordGesture(
            type = "scroll_down",
            status = if (ok == true) "COMPLETED" else "REJECTED",
            detail = if (ok == true) "via=SCROLL_DOWN ok" else "via=SCROLL_DOWN failed",
        )
        return ok == true
    }

    fun scrollLeft(x: Int, y: Int): Boolean {
        recordGesture(type = "scroll_left", status = "DISPATCHING", detail = "x=$x y=$y")
        val ok = try {
            performNodeScrollAt(
                x = x,
                y = y,
                primaryActions = intArrayOf(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_LEFT.id),
                secondaryActions = intArrayOf(),
                preferTarget = { isHorizontalScrollContainer(it) },
            )
        } catch (_: Exception) {
            null
        }
        recordGesture(
            type = "scroll_left",
            status = if (ok == true) "COMPLETED" else "REJECTED",
            detail = if (ok == true) "via=SCROLL_LEFT ok" else "via=SCROLL_LEFT failed",
        )
        return ok == true
    }

    fun scrollRight(x: Int, y: Int): Boolean {
        recordGesture(type = "scroll_right", status = "DISPATCHING", detail = "x=$x y=$y")
        val ok = try {
            performNodeScrollAt(
                x = x,
                y = y,
                primaryActions = intArrayOf(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_RIGHT.id),
                secondaryActions = intArrayOf(),
                preferTarget = { isHorizontalScrollContainer(it) },
            )
        } catch (_: Exception) {
            null
        }
        recordGesture(
            type = "scroll_right",
            status = if (ok == true) "COMPLETED" else "REJECTED",
            detail = if (ok == true) "via=SCROLL_RIGHT ok" else "via=SCROLL_RIGHT failed",
        )
        return ok == true
    }

    fun dpadUp(): Boolean {
        recordGesture(type = "dpad_up", status = "DISPATCHING", detail = "")
        val ok = try {
            performFocusMove(View.FOCUS_UP)
        } catch (_: Exception) {
            null
        }
        recordGesture(
            type = "dpad_up",
            status = if (ok == true) "COMPLETED" else "REJECTED",
            detail = if (ok == true) "via=FOCUS_UP ok" else "via=FOCUS_UP failed",
        )
        return ok == true
    }

    fun dpadDown(): Boolean {
        recordGesture(type = "dpad_down", status = "DISPATCHING", detail = "")
        val ok = try {
            performFocusMove(View.FOCUS_DOWN)
        } catch (_: Exception) {
            null
        }
        recordGesture(
            type = "dpad_down",
            status = if (ok == true) "COMPLETED" else "REJECTED",
            detail = if (ok == true) "via=FOCUS_DOWN ok" else "via=FOCUS_DOWN failed",
        )
        return ok == true
    }

    fun dpadLeft(): Boolean {
        recordGesture(type = "dpad_left", status = "DISPATCHING", detail = "")
        val ok = try {
            performFocusMove(View.FOCUS_LEFT)
        } catch (_: Exception) {
            null
        }
        recordGesture(
            type = "dpad_left",
            status = if (ok == true) "COMPLETED" else "REJECTED",
            detail = if (ok == true) "via=FOCUS_LEFT ok" else "via=FOCUS_LEFT failed",
        )
        return ok == true
    }

    fun dpadRight(): Boolean {
        recordGesture(type = "dpad_right", status = "DISPATCHING", detail = "")
        val ok = try {
            performFocusMove(View.FOCUS_RIGHT)
        } catch (_: Exception) {
            null
        }
        recordGesture(
            type = "dpad_right",
            status = if (ok == true) "COMPLETED" else "REJECTED",
            detail = if (ok == true) "via=FOCUS_RIGHT ok" else "via=FOCUS_RIGHT failed",
        )
        return ok == true
    }

    private companion object {
        private const val DOUBLE_TAP_INTERVAL_MS = 80L
    }

    private fun performFocusMove(direction: Int): Boolean? {
        val root = service.rootInActiveWindow ?: return null

        var current: AccessibilityNodeInfo? = null
        var next: AccessibilityNodeInfo? = null
        try {
            current = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
                ?: root.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)
                ?: root

            next = current.focusSearch(direction) ?: return false

            val okInput = next.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            if (okInput) return true

            return next.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS)
        } finally {
            try {
                next?.recycle()
            } catch (_: Exception) {
            }
            try {
                current?.recycle()
            } catch (_: Exception) {
            }
        }
    }

    fun focusAt(x: Int, y: Int): Boolean? {
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

            // Prefer focusing a focusable ancestor; fallback to clickable.
            var target: AccessibilityNodeInfo? = bestNode
            while (target != null && target.isEnabled && !(target.isFocusable || target.isClickable)) {
                val parent = target.parent
                if (parent == null) break
                if (target !== bestNode) {
                    target.recycle()
                }
                target = parent
            }

            val okInput = target?.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            val okA11y = if (okInput == true) true else target?.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS)

            if (target != null && target !== bestNode) {
                target.recycle()
            }
            bestNode.recycle()
            return okA11y
        } catch (_: Exception) {
            try {
                best?.recycle()
            } catch (_: Exception) {
            }
            return null
        }
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
