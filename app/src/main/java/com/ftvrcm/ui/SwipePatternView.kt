package com.ftvrcm.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View

class SwipePatternView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private val basePaintA = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF2A2A2A.toInt() }
    private val basePaintB = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF3A3A3A.toInt() }

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0x66FFFFFF
        strokeWidth = dp(1f)
    }

    private val stripePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0x33FFFFFF
        strokeWidth = dp(3f)
        strokeCap = Paint.Cap.ROUND
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val tile = dp(48f).toInt().coerceAtLeast(16)

        // Checkerboard base
        var y = 0
        var row = 0
        while (y < height) {
            var x = 0
            var col = 0
            while (x < width) {
                val p = if ((row + col) % 2 == 0) basePaintA else basePaintB
                canvas.drawRect(
                    x.toFloat(),
                    y.toFloat(),
                    (x + tile).coerceAtMost(width).toFloat(),
                    (y + tile).coerceAtMost(height).toFloat(),
                    p,
                )
                x += tile
                col += 1
            }
            y += tile
            row += 1
        }

        // Grid lines
        var gx = 0
        while (gx <= width) {
            canvas.drawLine(gx.toFloat(), 0f, gx.toFloat(), height.toFloat(), gridPaint)
            gx += tile
        }
        var gy = 0
        while (gy <= height) {
            canvas.drawLine(0f, gy.toFloat(), width.toFloat(), gy.toFloat(), gridPaint)
            gy += tile
        }

        // Diagonal stripes to make direction obvious
        val step = dp(72f)
        var offset = -height.toFloat()
        while (offset < width.toFloat() + height.toFloat()) {
            canvas.drawLine(offset, 0f, offset + height.toFloat(), height.toFloat(), stripePaint)
            offset += step
        }
    }

    private fun dp(value: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics)
}
