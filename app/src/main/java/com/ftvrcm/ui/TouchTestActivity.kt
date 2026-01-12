package com.ftvrcm.ui

import android.graphics.Color
import android.os.Bundle
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.ftvrcm.R
import android.os.SystemClock
import android.view.ViewConfiguration
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
        val scrollAreaH = findViewById<HorizontalScrollView>(R.id.scrollAreaH)
        val scrollAreaV = findViewById<ScrollView>(R.id.scrollAreaV)
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

        var lastClickAtMs = 0L
        var lastClickPos = ListView.INVALID_POSITION
        val doubleTapTimeoutMs = ViewConfiguration.getDoubleTapTimeout().toLong()

        list.setOnItemClickListener { _, _, position, _ ->
            list.setItemChecked(position, true)
            val now = SystemClock.uptimeMillis()

            // If injection falls back to ACTION_CLICK, MotionEvent-based double tap detection may not run.
            // Detect double tap using consecutive item clicks as a backup.
            if (position == lastClickPos && now - lastClickAtMs <= doubleTapTimeoutMs) {
                setEvent("リスト: ダブルタップ (${items[position]})")
                lastClickAtMs = 0L
                lastClickPos = ListView.INVALID_POSITION
            } else {
                setEvent("リスト: クリック (${items[position]})")
                lastClickAtMs = now
                lastClickPos = position
            }
        }

        list.setOnItemLongClickListener { _, _, position, _ ->
            list.setItemChecked(position, true)
            setEvent("リスト: ロングクリック (${items[position]})")
            true
        }

        val listDetector = GestureDetector(
            this,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onDown(e: MotionEvent): Boolean = true

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

        // Scroll area: build a 4-color pattern grid (for vertical + horizontal scroll check).
        val density = resources.displayMetrics.density
        val cellSizePx = (36f * density).toInt()
        val cellMarginPx = (2f * density).toInt()

        val colors = intArrayOf(
            Color.parseColor("#E53935"), // red
            Color.parseColor("#43A047"), // green
            Color.parseColor("#1E88E5"), // blue
            Color.parseColor("#FDD835"), // yellow
        )

        val rows = 40
        val cols = 40

        // Ensure content width exceeds viewport so horizontal scroll is actually possible.
        // (Some view hierarchies may otherwise measure to viewport width.)
        val totalWidthPx = cols * (cellSizePx + cellMarginPx * 2)
        patternContainer.minimumWidth = totalWidthPx
        for (r in 0 until rows) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                minimumWidth = totalWidthPx
            }
            for (c in 0 until cols) {
                val idx = (r + c) % colors.size
                val cell = View(this).apply {
                    setBackgroundColor(colors[idx])
                }
                row.addView(
                    cell,
                    LinearLayout.LayoutParams(cellSizePx, cellSizePx).apply {
                        setMargins(cellMarginPx, cellMarginPx, cellMarginPx, cellMarginPx)
                    },
                )
            }
            patternContainer.addView(
                row,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ),
            )
        }

        scrollAreaV.setOnScrollChangeListener { _, _, scrollY, _, oldScrollY ->
            if (scrollY != oldScrollY) {
                setEvent("縦スクロール: y=$scrollY")
            }
        }

        scrollAreaH.setOnScrollChangeListener { _, scrollX, _, oldScrollX, _ ->
            if (scrollX != oldScrollX) {
                setEvent("横スクロール: x=$scrollX")
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

        val feedSwipeDetector = View.OnTouchListener { _, event ->
            swipeDetector.onTouchEvent(event)
            false
        }
        scrollAreaH.setOnTouchListener(feedSwipeDetector)
        scrollAreaV.setOnTouchListener(feedSwipeDetector)
        patternContainer.setOnTouchListener(feedSwipeDetector)
    }
}
