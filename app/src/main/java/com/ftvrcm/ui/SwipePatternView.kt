package com.ftvrcm.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

class SwipePatternView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private val basePaintA = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF1E1E1E.toInt() }
    private val basePaintB = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF2A2A2A.toInt() }

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0x33FFFFFF.toInt()
        strokeWidth = dp(1.2f)
    }


    private var scaleFactor = 1.0f
    private var translateX = 0f
    private var translateY = 0f
    private var activePointerId = MotionEvent.INVALID_POINTER_ID
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var isScaling = false

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            isScaling = true
            return true
        }

        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val prev = scaleFactor
            scaleFactor = (scaleFactor * detector.scaleFactor).coerceIn(0.3f, 6.0f)
            val focusX = detector.focusX
            val focusY = detector.focusY
            val factor = scaleFactor / prev
            translateX = focusX - (focusX - translateX) * factor
            translateY = focusY - (focusY - translateY) * factor
            invalidate()
            return true
        }

        override fun onScaleEnd(detector: ScaleGestureDetector) {
            isScaling = false
        }
    })

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val tile = dp(72f).toInt().coerceAtLeast(24)
        val invScale = if (scaleFactor == 0f) 1f else 1f / scaleFactor
        val viewLeft = (0f - translateX) * invScale
        val viewTop = (0f - translateY) * invScale
        val viewRight = (width.toFloat() - translateX) * invScale
        val viewBottom = (height.toFloat() - translateY) * invScale

        val pad = dp(160f)
        val minX = min(viewLeft, viewRight) - pad
        val maxX = max(viewLeft, viewRight) + pad
        val minY = min(viewTop, viewBottom) - pad
        val maxY = max(viewTop, viewBottom) + pad

        val startX = (floor(minX / tile) * tile).toInt()
        val endX = (ceil(maxX / tile) * tile).toInt()
        val startY = (floor(minY / tile) * tile).toInt()
        val endY = (ceil(maxY / tile) * tile).toInt()

        canvas.save()
        canvas.translate(translateX, translateY)
        canvas.scale(scaleFactor, scaleFactor)

        // Checkerboard base
        var y = startY
        var row = 0
        while (y < endY) {
            var x = startX
            var col = 0
            while (x < endX) {
                val p = if ((row + col) % 2 == 0) basePaintA else basePaintB
                canvas.drawRect(
                    x.toFloat(),
                    y.toFloat(),
                    (x + tile).toFloat(),
                    (y + tile).toFloat(),
                    p,
                )
                x += tile
                col += 1
            }
            y += tile
            row += 1
        }

        // Grid lines
        var gx = startX
        while (gx <= endX) {
            canvas.drawLine(gx.toFloat(), startY.toFloat(), gx.toFloat(), endY.toFloat(), gridPaint)
            gx += tile
        }
        var gy = startY
        while (gy <= endY) {
            canvas.drawLine(startX.toFloat(), gy.toFloat(), endX.toFloat(), gy.toFloat(), gridPaint)
            gy += tile
        }

        canvas.restore()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                activePointerId = event.getPointerId(0)
                lastTouchX = event.x
                lastTouchY = event.y
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (!isScaling && activePointerId != MotionEvent.INVALID_POINTER_ID) {
                    val index = event.findPointerIndex(activePointerId)
                    if (index >= 0) {
                        val x = event.getX(index)
                        val y = event.getY(index)
                        translateX += x - lastTouchX
                        translateY += y - lastTouchY
                        lastTouchX = x
                        lastTouchY = y
                        invalidate()
                    }
                }
                return true
            }

            MotionEvent.ACTION_POINTER_UP -> {
                val pointerId = event.getPointerId(event.actionIndex)
                if (pointerId == activePointerId) {
                    val newIndex = if (event.actionIndex == 0) 1 else 0
                    if (newIndex < event.pointerCount) {
                        activePointerId = event.getPointerId(newIndex)
                        lastTouchX = event.getX(newIndex)
                        lastTouchY = event.getY(newIndex)
                    } else {
                        activePointerId = MotionEvent.INVALID_POINTER_ID
                    }
                }
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                activePointerId = MotionEvent.INVALID_POINTER_ID
                return true
            }
        }

        return true
    }

    private fun dp(value: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics)
}
