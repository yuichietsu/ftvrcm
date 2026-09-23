package com.ftvrcm.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class SwipePatternView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    // Background & World
    private val worldBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF0B0F19.toInt()
        style = Paint.Style.FILL
    }
    private val basePaintA = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF172033.toInt()
        style = Paint.Style.FILL
    }
    private val basePaintB = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF1E293B.toInt()
        style = Paint.Style.FILL
    }

    // Grid Lines
    private val minorGridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0x2EFFFFFF.toInt()
    }
    private val majorGridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0x59FFFFFF.toInt()
    }
    private val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0xFF38BDF8.toInt()
    }

    // Radar Concentric Rings
    private val radarRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0x5506B6D4.toInt()
    }
    private val radarTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF38BDF8.toInt()
        textAlign = Paint.Align.CENTER
    }

    // World Boundary
    private val worldBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0xFF3B82F6.toInt()
    }

    // Center Crosshair
    private val centerCrosshairPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0xFF22D3EE.toInt()
    }
    private val centerDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xFF22D3EE.toInt()
    }

    // Screen HUD
    private val hudCardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xE60F172A.toInt()
    }
    private val hudBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0xFF334155.toInt()
        strokeWidth = dp(1f)
    }
    private val hudTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFF8FAFC.toInt()
        textSize = sp(13f)
        isFakeBoldText = true
    }
    private val hudSubPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF94A3B8.toInt()
        textSize = sp(11f)
    }
    private val hudButtonBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xE61E293B.toInt()
    }
    private val hudButtonBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0xFF3B82F6.toInt()
        strokeWidth = dp(1.2f)
    }
    private val hudButtonTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF60A5FA.toInt()
        textSize = sp(12f)
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    // Transform State
    private var scaleFactor = 1.0f
    private var translateX = 0f
    private var translateY = 0f
    private var initialCentered = false

    private val minScale = 0.05f
    private val maxScale = 8.0f

    // Touch & Multi-touch State
    private var activePointerId = MotionEvent.INVALID_POINTER_ID
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var downX = 0f
    private var downY = 0f
    private var isDragging = false
    private var lastFocusX = 0f
    private var lastFocusY = 0f

    // HUD Button Rect
    private val resetButtonRect = RectF()
    private val textBounds = Rect()

    // World size (±2400dp, total 4800dp width & height)
    private val worldHalfSize: Float
        get() = dp(2400f)

    private val radarRadiiDp = floatArrayOf(150f, 300f, 600f, 1000f, 1500f, 2000f)

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            lastFocusX = detector.focusX
            lastFocusY = detector.focusY
            return true
        }

        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val prev = scaleFactor
            val targetScale = (scaleFactor * detector.scaleFactor).coerceIn(minScale, maxScale)
            val focusX = detector.focusX
            val focusY = detector.focusY

            if (prev != 0f) {
                val factor = targetScale / prev
                translateX = focusX - (focusX - translateX) * factor
                translateY = focusY - (focusY - translateY) * factor
            }

            // Also track focal movement during pinch
            val deltaFocusX = focusX - lastFocusX
            val deltaFocusY = focusY - lastFocusY
            translateX += deltaFocusX
            translateY += deltaFocusY

            lastFocusX = focusX
            lastFocusY = focusY
            scaleFactor = targetScale

            invalidate()
            return true
        }
    }).apply {
        isQuickScaleEnabled = false
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (!initialCentered && w > 0 && h > 0) {
            translateX = w / 2f
            translateY = h / 2f
            initialCentered = true
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        // 1. Fill entire view background
        canvas.drawRect(0f, 0f, w, h, worldBgPaint)

        // Calculate visible viewport in world coordinates
        val invScale = if (scaleFactor == 0f) 1f else 1f / scaleFactor
        val viewLeft = (0f - translateX) * invScale
        val viewTop = (0f - translateY) * invScale
        val viewRight = (w - translateX) * invScale
        val viewBottom = (h - translateY) * invScale

        val minX = min(viewLeft, viewRight)
        val maxX = max(viewLeft, viewRight)
        val minY = min(viewTop, viewBottom)
        val maxY = max(viewTop, viewBottom)

        // 2. Transform world
        canvas.save()
        canvas.translate(translateX, translateY)
        canvas.scale(scaleFactor, scaleFactor)

        val halfSize = worldHalfSize

        // Maintain constant screen stroke widths regardless of zoom
        minorGridPaint.strokeWidth = dp(0.9f) * invScale
        majorGridPaint.strokeWidth = dp(1.5f) * invScale
        axisPaint.strokeWidth = dp(2.2f) * invScale
        radarRingPaint.strokeWidth = dp(1.4f) * invScale
        radarTextPaint.textSize = sp(10f) * invScale
        worldBorderPaint.strokeWidth = dp(2.5f) * invScale
        centerCrosshairPaint.strokeWidth = dp(1.8f) * invScale

        // Level-Of-Detail (LOD) tile size: scale tile by powers of 2 when zoomed out
        val baseTile = dp(60f)
        val tileStep = when {
            scaleFactor < 0.08f -> 8
            scaleFactor < 0.16f -> 4
            scaleFactor < 0.35f -> 2
            else -> 1
        }
        val tile = baseTile * tileStep

        // Constrain drawing within world bounds
        val drawMinX = max(minX, -halfSize)
        val drawMaxX = min(maxX, halfSize)
        val drawMinY = max(minY, -halfSize)
        val drawMaxY = min(maxY, halfSize)

        if (drawMinX < drawMaxX && drawMinY < drawMaxY) {
            val startX = (floor(drawMinX / tile) * tile)
            val endX = (ceil(drawMaxX / tile) * tile)
            val startY = (floor(drawMinY / tile) * tile)
            val endY = (ceil(drawMaxY / tile) * tile)

            // A. Stable Checkerboard (Parity based on world-space coordinates, NOT loop counter!)
            var y = startY
            while (y < endY) {
                val nextY = min(y + tile, halfSize)
                val curY = max(y, -halfSize)
                if (curY < nextY) {
                    var x = startX
                    while (x < endX) {
                        val nextX = min(x + tile, halfSize)
                        val curX = max(x, -halfSize)
                        if (curX < nextX) {
                            val tileCol = Math.floorDiv(x.roundToInt(), tile.roundToInt())
                            val tileRow = Math.floorDiv(y.roundToInt(), tile.roundToInt())
                            val paint = if ((tileCol + tileRow) % 2 == 0) basePaintA else basePaintB
                            canvas.drawRect(curX, curY, nextX, nextY, paint)
                        }
                        x += tile
                    }
                }
                y += tile
            }

            // B. Minor & Major Grid Lines
            val gridStep = baseTile * (if (scaleFactor < 0.2f) 4 else if (scaleFactor < 0.4f) 2 else 1)
            val gStartX = (floor(drawMinX / gridStep) * gridStep)
            val gEndX = (ceil(drawMaxX / gridStep) * gridStep)
            val gStartY = (floor(drawMinY / gridStep) * gridStep)
            val gEndY = (ceil(drawMaxY / gridStep) * gridStep)

            var gx = gStartX
            while (gx <= gEndX) {
                if (gx in -halfSize..halfSize) {
                    val isMajor = (Math.floorDiv(gx.roundToInt(), (baseTile * 4).roundToInt()) % 2 == 0)
                    canvas.drawLine(
                        gx,
                        max(gStartY, -halfSize),
                        gx,
                        min(gEndY, halfSize),
                        if (isMajor) majorGridPaint else minorGridPaint,
                    )
                }
                gx += gridStep
            }

            var gy = gStartY
            while (gy <= gEndY) {
                if (gy in -halfSize..halfSize) {
                    val isMajor = (Math.floorDiv(gy.roundToInt(), (baseTile * 4).roundToInt()) % 2 == 0)
                    canvas.drawLine(
                        max(gStartX, -halfSize),
                        gy,
                        min(gEndX, halfSize),
                        gy,
                        if (isMajor) majorGridPaint else minorGridPaint,
                    )
                }
                gy += gridStep
            }
        }

        // C. World Coordinate Axes (X & Y axes through origin)
        if (minY <= 0f && maxY >= 0f) {
            canvas.drawLine(-halfSize, 0f, halfSize, 0f, axisPaint)
        }
        if (minX <= 0f && maxX >= 0f) {
            canvas.drawLine(0f, -halfSize, 0f, halfSize, axisPaint)
        }

        // D. Concentric Radar Distance Rings & Labels
        val textOffset = sp(4f) * invScale
        for (radiusDp in radarRadiiDp) {
            val r = dp(radiusDp)
            if (r <= halfSize) {
                canvas.drawCircle(0f, 0f, r, radarRingPaint)
            }
        }

        // Draw distance labels from largest to smallest; if a smaller label overlaps with a larger label
        // or collides with the center reticle, hide the smaller label.
        val padScreen = dp(6f)
        val centerMinScreen = dp(32f)
        var nextAcceptedLeftScreen = Float.POSITIVE_INFINITY

        for (i in radarRadiiDp.indices.reversed()) {
            val radiusDp = radarRadiiDp[i]
            val r = dp(radiusDp)
            if (r > halfSize) continue

            val text = "${radiusDp.toInt()}dp"
            val textWidthScreen = radarTextPaint.measureText(text) * scaleFactor
            val centerXScreen = r * scaleFactor
            val leftScreen = centerXScreen - (textWidthScreen / 2f) - padScreen
            val rightScreen = centerXScreen + (textWidthScreen / 2f) + padScreen

            val isWithinViewport = (r in minX..maxX && 0f in minY..maxY)
            if (isWithinViewport && rightScreen < nextAcceptedLeftScreen && leftScreen >= centerMinScreen) {
                canvas.drawText(text, r, -textOffset, radarTextPaint)
                nextAcceptedLeftScreen = leftScreen
            }
        }

        // E. World Boundary Box (Outer region perimeter)
        canvas.drawRect(-halfSize, -halfSize, halfSize, halfSize, worldBorderPaint)

        // F. Center Crosshair (Target Reticle at 0, 0)
        drawCenterCrosshair(canvas, invScale)

        canvas.restore()

        // 3. Screen HUD (Drawn in Screen Space on top of canvas)
        drawScreenHud(canvas, w, h)
    }

    private fun drawCenterCrosshair(canvas: Canvas, invScale: Float) {
        val crossLen = dp(24f) * invScale
        val circleR = dp(10f) * invScale
        val dotR = dp(3f) * invScale

        canvas.drawLine(-crossLen, 0f, crossLen, 0f, centerCrosshairPaint)
        canvas.drawLine(0f, -crossLen, 0f, crossLen, centerCrosshairPaint)
        canvas.drawCircle(0f, 0f, circleR, centerCrosshairPaint)
        canvas.drawCircle(0f, 0f, dotR, centerDotPaint)

        radarTextPaint.textSize = sp(11f) * invScale
        canvas.drawText("中心 (0, 0)", 0f, circleR + dp(14f) * invScale, radarTextPaint)
    }

    private fun drawScreenHud(canvas: Canvas, w: Float, h: Float) {
        val margin = dp(10f)
        val padH = dp(12f)
        val padV = dp(8f)

        // Left HUD Card: Scale & Center coordinate
        val percent = (scaleFactor * 100f).roundToInt()
        val titleText = "ズーム: $percent% (${String.format("%.2f", scaleFactor)}x)"
        val curWorldCenterX = ((w / 2f - translateX) / scaleFactor).roundToInt()
        val curWorldCenterY = ((h / 2f - translateY) / scaleFactor).roundToInt()
        val subText = "中心: ($curWorldCenterX, $curWorldCenterY)"

        val cardW = dp(180f)
        val cardH = dp(46f)
        val cardRect = RectF(margin, margin, margin + cardW, margin + cardH)
        val corner = dp(8f)

        canvas.drawRoundRect(cardRect, corner, corner, hudCardPaint)
        canvas.drawRoundRect(cardRect, corner, corner, hudBorderPaint)

        canvas.drawText(titleText, margin + padH, margin + padV + sp(12f), hudTitlePaint)
        canvas.drawText(subText, margin + padH, margin + padV + sp(12f) + sp(15f), hudSubPaint)

        // Right HUD Button: "中央に戻す / 1.0x"
        val btnW = dp(126f)
        val btnH = dp(36f)
        resetButtonRect.set(w - margin - btnW, margin, w - margin, margin + btnH)

        canvas.drawRoundRect(resetButtonRect, corner, corner, hudButtonBgPaint)
        canvas.drawRoundRect(resetButtonRect, corner, corner, hudButtonBorderPaint)

        val btnLabel = "中央に戻す"
        hudButtonTextPaint.getTextBounds(btnLabel, 0, btnLabel.length, textBounds)
        val btnTextY = resetButtonRect.centerY() + (textBounds.height() / 2f)
        canvas.drawText(btnLabel, resetButtonRect.centerX(), btnTextY, hudButtonTextPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                activePointerId = event.getPointerId(0)
                lastTouchX = event.x
                lastTouchY = event.y
                downX = event.x
                downY = event.y
                isDragging = false
                return true
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                // Multi-touch started: suppress single-finger dragging to prevent sudden jumps
                isDragging = false
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                // Only pan with single pointer when scale detector is not active and only 1 pointer is down
                if (!scaleDetector.isInProgress && event.pointerCount == 1 && activePointerId != MotionEvent.INVALID_POINTER_ID) {
                    val index = event.findPointerIndex(activePointerId)
                    if (index >= 0) {
                        val x = event.getX(index)
                        val y = event.getY(index)
                        val dx = x - lastTouchX
                        val dy = y - lastTouchY

                        if (!isDragging) {
                            val totalDx = x - downX
                            val totalDy = y - downY
                            if ((totalDx * totalDx + totalDy * totalDy) > dp(8f) * dp(8f)) {
                                isDragging = true
                            }
                        }

                        if (isDragging) {
                            translateX += dx
                            translateY += dy
                            invalidate()
                        }
                        lastTouchX = x
                        lastTouchY = y
                    }
                }
                return true
            }

            MotionEvent.ACTION_POINTER_UP -> {
                val actionIndex = event.actionIndex
                val pointerId = event.getPointerId(actionIndex)
                if (pointerId == activePointerId) {
                    val newIndex = if (actionIndex == 0) 1 else 0
                    if (newIndex < event.pointerCount) {
                        activePointerId = event.getPointerId(newIndex)
                        lastTouchX = event.getX(newIndex)
                        lastTouchY = event.getY(newIndex)
                    } else {
                        activePointerId = MotionEvent.INVALID_POINTER_ID
                    }
                } else {
                    // Update remaining active pointer position to prevent delta jump
                    val index = event.findPointerIndex(activePointerId)
                    if (index >= 0) {
                        lastTouchX = event.getX(index)
                        lastTouchY = event.getY(index)
                    }
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                // Check if user tapped the Reset button (with generous touch slop)
                val hitRect = RectF(resetButtonRect).apply { inset(-dp(10f), -dp(10f)) }
                if (!isDragging && hitRect.contains(event.x, event.y)) {
                    resetToDefault()
                }
                activePointerId = MotionEvent.INVALID_POINTER_ID
                isDragging = false
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                activePointerId = MotionEvent.INVALID_POINTER_ID
                isDragging = false
                return true
            }
        }

        return true
    }

    /**
     * Resets zoom to 1.0x and centers the origin (0, 0) in the viewport.
     */
    fun resetToDefault() {
        scaleFactor = 1.0f
        translateX = width / 2f
        translateY = height / 2f
        invalidate()
    }

    private fun dp(value: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics)

    private fun sp(value: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, resources.displayMetrics)
}
