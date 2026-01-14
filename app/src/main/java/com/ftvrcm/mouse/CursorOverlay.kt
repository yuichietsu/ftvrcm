package com.ftvrcm.mouse

import android.content.Context
import android.graphics.Canvas
import android.graphics.Path
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Point
import android.os.Build
import android.os.SystemClock
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.core.graphics.withSave
import kotlin.math.atan2

class CursorOverlay(private val context: Context) {

    enum class CursorStyle {
        POINTER,
        DPAD,
    }

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private var view: View? = null
    private var params: WindowManager.LayoutParams? = null

    private var feedbackView: FeedbackView? = null
    private var feedbackParams: WindowManager.LayoutParams? = null

    private var style: CursorStyle = CursorStyle.POINTER

    private var x: Int = 200
    private var y: Int = 200

    fun show() {
        if (view != null) return

        val feedback = FeedbackView(context)
        val feedbackLayoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
        }

        val dot = CursorView(context).apply { setStyle(style) }

        val layoutParams = WindowManager.LayoutParams(
            48,
            48,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            this.x = x
            this.y = y
        }

        view = dot
        params = layoutParams
        feedbackView = feedback
        feedbackParams = feedbackLayoutParams

        // Add feedback first so cursor stays on top.
        windowManager.addView(feedback, feedbackLayoutParams)
        windowManager.addView(dot, layoutParams)

        // Some devices temporarily place the overlay at (0,0) on first add.
        // Re-apply position once params is attached.
        setPosition(x, y)
    }

    fun hide() {
        val v = view ?: return
        val fb = feedbackView
        if (fb != null) {
            try {
                windowManager.removeView(fb)
            } catch (_: Throwable) {
            }
        }
        windowManager.removeView(v)
        view = null
        params = null
        feedbackView = null
        feedbackParams = null
    }

    fun setStyle(newStyle: CursorStyle) {
        style = newStyle
        (view as? CursorView)?.setStyle(newStyle)
    }

    fun showTapFeedback(isLongPress: Boolean) {
        val holdMs = if (isLongPress) 420L else 120L
        (view as? CursorView)?.showPressFeedback(holdMs = holdMs)
    }

    enum class Direction { UP, DOWN, LEFT, RIGHT }

    fun showScrollArrow(direction: Direction) {
        (view as? CursorView)?.showScrollArrow(direction)
    }

    fun showSwipeTrail(x1: Int, y1: Int, x2: Int, y2: Int) {
        feedbackView?.addSwipeTrail(x1 = x1, y1 = y1, x2 = x2, y2 = y2)
    }

    fun moveBy(dx: Int, dy: Int) {
        val p = params ?: return
        val displaySize = getDisplaySize()
        x = (x + dx).coerceIn(0, displaySize.x - p.width)
        y = (y + dy).coerceIn(0, displaySize.y - p.height)

        p.x = x
        p.y = y
        windowManager.updateViewLayout(view, p)
    }

    fun setPosition(newX: Int, newY: Int) {
        val displaySize = getDisplaySize()
        val p = params
        val w = p?.width ?: 48
        val h = p?.height ?: 48

        x = newX.coerceIn(0, displaySize.x - w)
        y = newY.coerceIn(0, displaySize.y - h)

        if (p != null) {
            p.x = x
            p.y = y
            windowManager.updateViewLayout(view, p)
        }
    }

    fun position(): Point = Point(x, y)

    fun center(): Point {
        val p = params
        return if (p == null) Point(x, y) else Point(x + p.width / 2, y + p.height / 2)
    }

    private fun getDisplaySize(): Point {
        val point = Point()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val metrics = windowManager.currentWindowMetrics
            val bounds = metrics.bounds
            point.x = bounds.width()
            point.y = bounds.height()
        } else {
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getSize(point)
        }
        return point
    }

    private class CursorView(context: Context) : View(context) {

        private var style: CursorStyle = CursorStyle.POINTER

        private var pressHoldUntilMs: Long = 0L
        private var pressFadeUntilMs: Long = 0L

        private var scrollArrowDirection: Direction? = null
        private var scrollArrowUntilMs: Long = 0L

        fun setStyle(newStyle: CursorStyle) {
            style = newStyle
            invalidate()
        }

        fun showPressFeedback(holdMs: Long) {
            val now = SystemClock.uptimeMillis()
            pressHoldUntilMs = (now + holdMs).coerceAtLeast(now)
            pressFadeUntilMs = pressHoldUntilMs + 220L
            invalidate()
        }

        fun showScrollArrow(direction: Direction, holdMs: Long = 240L) {
            val now = SystemClock.uptimeMillis()
            scrollArrowDirection = direction
            scrollArrowUntilMs = (now + holdMs).coerceAtLeast(now)
            invalidate()
        }

        private val outerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = 0xCC000000.toInt() // translucent black
            strokeWidth = dp(5f)
        }

        private val innerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = 0xFFFFFFFF.toInt()
            strokeWidth = dp(2.5f)
        }

        private val pressFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = 0xFF000000.toInt()
        }

        private val invertOuterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = 0xFFFFFFFF.toInt()
            strokeWidth = dp(5f)
        }

        private val invertInnerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = 0xFF000000.toInt()
            strokeWidth = dp(2.5f)
        }

        private val arrowOuterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = 0xCC000000.toInt()
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            strokeWidth = dp(6f)
        }

        private val arrowInnerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = 0xFFFFFFFF.toInt()
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            strokeWidth = dp(3f)
        }

        private val dpadOuterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = 0xCC000000.toInt()
            strokeWidth = dp(6f)
            strokeCap = Paint.Cap.ROUND
        }

        private val dpadInnerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = 0xFFFFFFFF.toInt()
            strokeWidth = dp(3f)
            strokeCap = Paint.Cap.ROUND
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val cx = width / 2f
            val cy = height / 2f

            val now = SystemClock.uptimeMillis()

            val pressAlpha = when {
                now <= pressHoldUntilMs -> 1f
                now <= pressFadeUntilMs -> {
                    val t = (now - pressHoldUntilMs).toFloat() / (pressFadeUntilMs - pressHoldUntilMs).toFloat()
                    (1f - t).coerceIn(0f, 1f)
                }
                else -> 0f
            }

            val scrollArrowActive = now <= scrollArrowUntilMs && scrollArrowDirection != null

            if (pressAlpha > 0f || scrollArrowActive) {
                postInvalidateOnAnimation()
            }

            when (style) {
                CursorStyle.POINTER -> {
                    val r = (minOf(width, height) / 2f) - dp(2.5f)

                    if (scrollArrowActive) {
                        val dir = scrollArrowDirection ?: return
                        val len = (minOf(width, height) * 0.34f)
                        val head = (minOf(width, height) * 0.16f)

                        val dx = when (dir) {
                            Direction.LEFT -> -1f
                            Direction.RIGHT -> 1f
                            else -> 0f
                        }
                        val dy = when (dir) {
                            Direction.UP -> -1f
                            Direction.DOWN -> 1f
                            else -> 0f
                        }

                        val x1 = cx - dx * (len * 0.3f)
                        val y1 = cy - dy * (len * 0.3f)
                        val x2 = cx + dx * (len * 0.7f)
                        val y2 = cy + dy * (len * 0.7f)

                        fun drawArrow(p: Paint) {
                            canvas.drawLine(x1, y1, x2, y2, p)
                            val path = Path()
                            path.moveTo(x2, y2)
                            if (dx != 0f) {
                                path.lineTo(x2 - dx * head, y2 - head)
                                path.moveTo(x2, y2)
                                path.lineTo(x2 - dx * head, y2 + head)
                            } else {
                                path.lineTo(x2 - head, y2 - dy * head)
                                path.moveTo(x2, y2)
                                path.lineTo(x2 + head, y2 - dy * head)
                            }
                            canvas.drawPath(path, p)
                        }

                        drawArrow(arrowOuterPaint)
                        drawArrow(arrowInnerPaint)
                        return
                    }

                    // Base cursor ring
                    canvas.drawCircle(cx, cy, r, outerPaint)
                    canvas.drawCircle(cx, cy, r, innerPaint)

                    // Tap feedback: invert/negative-like overlay inside the ring
                    if (pressAlpha > 0f) {
                        pressFillPaint.alpha = (pressAlpha * 140).toInt().coerceIn(0, 255)
                        canvas.drawCircle(cx, cy, r - dp(3.5f), pressFillPaint)

                        invertOuterPaint.alpha = (pressAlpha * 255).toInt().coerceIn(0, 255)
                        invertInnerPaint.alpha = (pressAlpha * 255).toInt().coerceIn(0, 255)
                        canvas.drawCircle(cx, cy, r, invertOuterPaint)
                        canvas.drawCircle(cx, cy, r, invertInnerPaint)
                    }
                }

                CursorStyle.DPAD -> {
                    val arm = (minOf(width, height) / 2f) - dp(6f)
                    // Outer cross
                    canvas.drawLine(cx - arm, cy, cx + arm, cy, dpadOuterPaint)
                    canvas.drawLine(cx, cy - arm, cx, cy + arm, dpadOuterPaint)
                    // Inner cross
                    canvas.drawLine(cx - arm, cy, cx + arm, cy, dpadInnerPaint)
                    canvas.drawLine(cx, cy - arm, cx, cy + arm, dpadInnerPaint)
                    // Center dot
                    val r = dp(3.5f)
                    canvas.drawCircle(cx, cy, r, outerPaint)
                    canvas.drawCircle(cx, cy, r, innerPaint)
                }
            }
        }

        private fun dp(value: Float): Float =
            TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics)
    }

    private class FeedbackView(context: Context) : View(context) {

        private interface Effect {
            val startedAtMs: Long
            val durationMs: Long
            fun draw(canvas: Canvas, nowMs: Long)
            fun isAlive(nowMs: Long): Boolean = (nowMs - startedAtMs) <= durationMs
        }

        private inner class SwipeEffect(
            private val x1: Float,
            private val y1: Float,
            private val x2: Float,
            private val y2: Float,
            override val startedAtMs: Long,
            override val durationMs: Long,
        ) : Effect {
            override fun draw(canvas: Canvas, nowMs: Long) {
                val t = ((nowMs - startedAtMs).toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)

                // Keep the trace visible, then fade out near the end.
                val fade = when {
                    t < 0.75f -> 1f
                    else -> (1f - ((t - 0.75f) / 0.25f)).coerceIn(0f, 1f)
                }

                // Trace line in negative-like two-tone (white outer, black inner)
                val alpha = (fade * 255).toInt().coerceIn(0, 255)
                traceOuterPaint.alpha = alpha
                traceInnerPaint.alpha = alpha
                canvas.drawLine(x1, y1, x2, y2, traceOuterPaint)
                canvas.drawLine(x1, y1, x2, y2, traceInnerPaint)

                // Animate a simple dot from start -> end.
                val px = x1 + (x2 - x1) * t
                val py = y1 + (y2 - y1) * t
                drawMovingDot(canvas, px, py, alpha)
            }
        }

        private val effects = ArrayDeque<Effect>()

        private val traceOuterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = 0xFFFFFFFF.toInt()
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            strokeWidth = dp(4.2f)
        }

        private val traceInnerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = 0xFF000000.toInt()
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            strokeWidth = dp(2.2f)
        }

        private val dotFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = 0xFF000000.toInt()
        }

        private val dotStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = 0xFFFFFFFF.toInt()
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            strokeWidth = dp(2.2f)
        }

        fun addSwipeTrail(x1: Int, y1: Int, x2: Int, y2: Int) {
            val now = SystemClock.uptimeMillis()
            effects.addLast(
                SwipeEffect(
                    x1 = x1.toFloat(),
                    y1 = y1.toFloat(),
                    x2 = x2.toFloat(),
                    y2 = y2.toFloat(),
                    startedAtMs = now,
                    durationMs = 360L,
                ),
            )
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val now = SystemClock.uptimeMillis()

            // Draw newest on top.
            canvas.withSave {
                val it = effects.iterator()
                while (it.hasNext()) {
                    val e = it.next()
                    if (!e.isAlive(now)) {
                        it.remove()
                        continue
                    }
                    e.draw(canvas, now)
                }
            }

            if (effects.isNotEmpty()) {
                postInvalidateOnAnimation()
            }
        }

        private fun dp(value: Float): Float =
            TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics)

        private fun drawMovingDot(canvas: Canvas, x: Float, y: Float, alpha: Int) {
            val r = dp(7.5f)
            dotFillPaint.alpha = (alpha * 0.9f).toInt().coerceIn(0, 255)
            dotStrokePaint.alpha = alpha
            canvas.drawCircle(x, y, r, dotFillPaint)
            canvas.drawCircle(x, y, r, dotStrokePaint)
        }
    }
}
