package com.ftvrcm.ui

import android.os.Bundle
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.ftvrcm.R
import kotlin.math.abs

class TouchTestActivity : AppCompatActivity() {

    private lateinit var lastEvent: TextView
    private lateinit var detector: GestureDetector

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_touch_test)

        lastEvent = findViewById(R.id.lastEvent)
        val pad = findViewById<View>(R.id.pad)
        pad.isClickable = true
        pad.isFocusable = true

        fun setEvent(text: String) {
            lastEvent.text = "最終イベント: $text"
        }

        detector = GestureDetector(
            this,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onDown(e: MotionEvent): Boolean = true

                override fun onSingleTapUp(e: MotionEvent): Boolean {
                    setEvent("タップ")
                    return true
                }

                override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                    setEvent("タップ")
                    return true
                }

                override fun onDoubleTap(e: MotionEvent): Boolean {
                    setEvent("ダブルタップ")
                    return true
                }

                override fun onLongPress(e: MotionEvent) {
                    setEvent("ロングタップ")
                }

                override fun onFling(
                    e1: MotionEvent?,
                    e2: MotionEvent,
                    velocityX: Float,
                    velocityY: Float,
                ): Boolean {
                    if (e1 == null) return false
                    val dx = e2.x - e1.x
                    val dy = e2.y - e1.y
                    val adx = abs(dx)
                    val ady = abs(dy)

                    val minDist = 120f
                    if (adx < minDist && ady < minDist) return false

                    val dir = if (adx > ady) {
                        if (dx > 0) "右スワイプ" else "左スワイプ"
                    } else {
                        if (dy > 0) "下スワイプ" else "上スワイプ"
                    }
                    setEvent(dir)
                    return true
                }
            },
        )
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
            lastEvent.text = "最終イベント: タッチ開始 (x=${ev.x.toInt()}, y=${ev.y.toInt()})"
        }
        detector.onTouchEvent(ev)
        return super.dispatchTouchEvent(ev)
    }
}
