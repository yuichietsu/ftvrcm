package com.ftvrcm.mouse

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Point
import android.os.Build
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager

class CursorOverlay(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private var view: View? = null
    private var params: WindowManager.LayoutParams? = null

    private var x: Int = 200
    private var y: Int = 200

    fun show() {
        if (view != null) return

        val dot = CursorRingView(context)

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
        windowManager.addView(dot, layoutParams)
    }

    fun hide() {
        val v = view ?: return
        windowManager.removeView(v)
        view = null
        params = null
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

    private class CursorRingView(context: Context) : View(context) {
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

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val cx = width / 2f
            val cy = height / 2f
            val r = (minOf(width, height) / 2f) - dp(2.5f)
            canvas.drawCircle(cx, cy, r, outerPaint)
            canvas.drawCircle(cx, cy, r, innerPaint)
        }

        private fun dp(value: Float): Float =
            TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics)
    }
}
