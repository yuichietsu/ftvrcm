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

    fun showScrollArrow(direction: Direction, x: Int, y: Int) {
        feedbackView?.addArrow(direction = direction, x = x, y = y)
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

        private var fillAlpha: Float = 0f
        private var pressEndAtMs: Long = 0L

        fun setStyle(newStyle: CursorStyle) {
            style = newStyle
            invalidate()
        }

        fun showPressFeedback(holdMs: Long) {
            val now = SystemClock.uptimeMillis()
            pressEndAtMs = (now + holdMs).coerceAtLeast(now)
            fillAlpha = 0.38f
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

        private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = 0xFFFFFFFF.toInt()
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
            if (fillAlpha > 0f) {
                if (now <= pressEndAtMs) {
                    // keep
                } else {
                    // fade out quickly
                    fillAlpha = (fillAlpha - 0.08f).coerceAtLeast(0f)
                }
                if (fillAlpha > 0f) {
                    postInvalidateOnAnimation()
                }
            }

            when (style) {
                CursorStyle.POINTER -> {
                    val r = (minOf(width, height) / 2f) - dp(2.5f)
                    if (fillAlpha > 0f) {
                        fillPaint.alpha = (fillAlpha * 255).toInt().coerceIn(0, 255)
                        canvas.drawCircle(cx, cy, r - dp(3.5f), fillPaint)
                    }
                    canvas.drawCircle(cx, cy, r, outerPaint)
                    canvas.drawCircle(cx, cy, r, innerPaint)
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

        private sealed interface Effect {
            val startedAtMs: Long
            val durationMs: Long
            fun draw(canvas: Canvas, paint: Paint, nowMs: Long)
            fun isAlive(nowMs: Long): Boolean = (nowMs - startedAtMs) <= durationMs
        }

        private data class SwipeEffect(
            val x1: Float,
            val y1: Float,
            val x2: Float,
            val y2: Float,
            override val startedAtMs: Long,
            override val durationMs: Long,
        ) : Effect {
            override fun draw(canvas: Canvas, paint: Paint, nowMs: Long) {
                val t = ((nowMs - startedAtMs).toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                paint.alpha = ((1f - t) * 200).toInt().coerceIn(0, 255)
                canvas.drawLine(x1, y1, x2, y2, paint)
            }
        }

        private data class ArrowEffect(
            val direction: Direction,
            val x: Float,
            val y: Float,
            override val startedAtMs: Long,
            override val durationMs: Long,
        ) : Effect {
            override fun draw(canvas: Canvas, paint: Paint, nowMs: Long) {
                val t = ((nowMs - startedAtMs).toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                paint.alpha = ((1f - t) * 220).toInt().coerceIn(0, 255)

                val len = paint.strokeWidth * 8f
                val head = paint.strokeWidth * 3.5f

                val dx = when (direction) {
                    Direction.LEFT -> -1f
                    Direction.RIGHT -> 1f
                    else -> 0f
                }
                val dy = when (direction) {
                    Direction.UP -> -1f
                    Direction.DOWN -> 1f
                    else -> 0f
                }

                val x2 = x + dx * len
                val y2 = y + dy * len

                canvas.drawLine(x, y, x2, y2, paint)

                // Arrow head
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
                canvas.drawPath(path, paint)
            }
        }

        private val effects = ArrayDeque<Effect>()

        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = 0xFFFFFFFF.toInt()
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            strokeWidth = dp(3f)
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

        fun addArrow(direction: Direction, x: Int, y: Int) {
            val now = SystemClock.uptimeMillis()
            effects.addLast(
                ArrowEffect(
                    direction = direction,
                    x = x.toFloat(),
                    y = y.toFloat(),
                    startedAtMs = now,
                    durationMs = 240L,
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
                    e.draw(canvas, paint, now)
                }
            }

            if (effects.isNotEmpty()) {
                postInvalidateOnAnimation()
            }
        }

        private fun dp(value: Float): Float =
            TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics)
    }
}
