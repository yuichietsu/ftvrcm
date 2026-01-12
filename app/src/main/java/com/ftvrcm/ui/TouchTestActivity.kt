package com.ftvrcm.ui

import android.graphics.Color
import android.os.Bundle
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.ftvrcm.R
import kotlin.math.abs

class TouchTestActivity : AppCompatActivity() {

    private lateinit var lastEvent: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_touch_test)

        lastEvent = findViewById(R.id.lastEvent)
        val btnA = findViewById<Button>(R.id.btnA)
        val btnB = findViewById<Button>(R.id.btnB)
        val btnC = findViewById<Button>(R.id.btnC)
        val list = findViewById<ListView>(R.id.list)
        val scrollArea = findViewById<ScrollView>(R.id.scrollArea)
        val patternContainer = findViewById<LinearLayout>(R.id.patternContainer)

        fun setEvent(text: String) {
            lastEvent.text = "最終イベント: $text"
        }

        fun attachButtonHandlers(button: Button, name: String) {
            button.isAllCaps = false
            button.isLongClickable = true
            button.setOnClickListener { setEvent("$name: クリック") }
            button.setOnLongClickListener {
                setEvent("$name: ロングクリック")
                true
            }
        }

        attachButtonHandlers(btnA, "ボタンA")
        attachButtonHandlers(btnB, "ボタンB")
        attachButtonHandlers(btnC, "ボタンC")

        val items = (1..30).map { "項目 $it" }
        list.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_list_item_single_choice,
            items,
        )

        list.setOnItemClickListener { _, _, position, _ ->
            list.setItemChecked(position, true)
            setEvent("リスト: クリック (${items[position]})")
        }

        list.setOnItemLongClickListener { _, _, position, _ ->
            list.setItemChecked(position, true)
            setEvent("リスト: ロングクリック (${items[position]})")
            true
        }

        val listDetector = GestureDetector(
            this,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onDown(e: MotionEvent): Boolean = false

                override fun onDoubleTap(e: MotionEvent): Boolean {
                    val pos = list.pointToPosition(e.x.toInt(), e.y.toInt())
                    if (pos != ListView.INVALID_POSITION) {
                        list.setItemChecked(pos, true)
                        setEvent("リスト: ダブルタップ (${items[pos]})")
                        return true
                    }
                    setEvent("リスト: ダブルタップ")
                    return true
                }
            },
        )
        list.setOnTouchListener { _, event ->
            listDetector.onTouchEvent(event)
            false
        }

        // Scroll area: build a visible stripe pattern.
        for (i in 1..60) {
            val row = TextView(this).apply {
                text = "模様 $i"
                setPadding(16, 18, 16, 18)
                setTextColor(Color.WHITE)
                setBackgroundColor(if (i % 2 == 0) Color.parseColor("#334455") else Color.parseColor("#223344"))
            }
            patternContainer.addView(
                row,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ),
            )
        }

        scrollArea.setOnScrollChangeListener { _, _, scrollY, _, oldScrollY ->
            if (scrollY != oldScrollY) {
                setEvent("スクロール: y=$scrollY")
            }
        }

        val swipeDetector = GestureDetector(
            this,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onDown(e: MotionEvent): Boolean = false

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
                    setEvent("スクロール領域: $dir")
                    return false
                }
            },
        )
        scrollArea.setOnTouchListener { _, event ->
            swipeDetector.onTouchEvent(event)
            false
        }
    }
}
