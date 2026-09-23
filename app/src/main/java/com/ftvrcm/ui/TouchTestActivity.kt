package com.ftvrcm.ui

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.os.SystemClock
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.ftvrcm.R
import com.ftvrcm.data.SettingsKeys
import com.ftvrcm.shizuku.ShizukuTouchInjector

class TouchTestActivity : AppCompatActivity() {

    private lateinit var lastEvent: TextView
    private lateinit var lastGesture: TextView

    private var gestureListener: SharedPreferences.OnSharedPreferenceChangeListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_touch_test)

        lastEvent = findViewById(R.id.lastEvent)
        lastGesture = findViewById(R.id.lastGesture)
        val btnA = findViewById<Button>(R.id.btnA)
        val btnB = findViewById<Button>(R.id.btnB)
        val btnC = findViewById<Button>(R.id.btnC)
        val list = findViewById<ListView>(R.id.list)

        fun setEvent(text: String) {
            lastEvent.text = "最終イベント: $text"
        }

        fun refreshLastGesture() {
            val prefs = getSharedPreferences(SettingsKeys.PREFS_NAME, MODE_PRIVATE)
            val type = prefs.getString(SettingsKeys.LAST_GESTURE_TYPE, "-") ?: "-"
            val status = prefs.getString(SettingsKeys.LAST_GESTURE_STATUS, "-") ?: "-"
            val detail = prefs.getString(SettingsKeys.LAST_GESTURE_DETAIL, "") ?: ""
            val text = if (detail.isBlank()) {
                "最終ジェスチャ: $type / $status"
            } else {
                "最終ジェスチャ: $type / $status ($detail)"
            }

            // Keep 2-line fixed height; overflow is ellipsized by TextView.
            lastGesture.text = text
        }

        refreshLastGesture()

        // SwipePatternView handles pinch/drag internally.

        val prefs = getSharedPreferences(SettingsKeys.PREFS_NAME, MODE_PRIVATE)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (
                key == SettingsKeys.LAST_GESTURE_TYPE ||
                key == SettingsKeys.LAST_GESTURE_STATUS ||
                key == SettingsKeys.LAST_GESTURE_DETAIL ||
                key == SettingsKeys.LAST_GESTURE_AT_MS
            ) {
                refreshLastGesture()
            }
        }
        gestureListener = listener
        prefs.registerOnSharedPreferenceChangeListener(listener)

        fun attachButtonHandlers(button: Button, name: String) {
            button.isAllCaps = false
            button.isLongClickable = true
            button.setOnClickListener { setEvent("$name: タップ") }
            button.setOnLongClickListener {
                setEvent("$name: 長押し")
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

        val doubleTapTimeoutMs = ViewConfiguration.getDoubleTapTimeout().toLong()

        var pendingClickAtMs = 0L
        var pendingClickPos = ListView.INVALID_POSITION
        var suppressNextItemClick = false
        val commitSingleClick = Runnable {
            val pos = pendingClickPos
            if (pos != ListView.INVALID_POSITION) {
                setEvent("リスト: タップ (${items[pos]})")
            }
            pendingClickAtMs = 0L
            pendingClickPos = ListView.INVALID_POSITION
        }

        fun cancelPendingSingleClick() {
            lastEvent.removeCallbacks(commitSingleClick)
            pendingClickAtMs = 0L
            pendingClickPos = ListView.INVALID_POSITION
        }

        fun commitDoubleTap(position: Int) {
            cancelPendingSingleClick()
            setEvent("リスト: ダブルタップ (${items[position]})")
        }

        list.setOnItemClickListener { _, _, position, _ ->
            list.setItemChecked(position, true)

            // When MotionEvent-based double tap is detected, ListView may still emit
            // an item click afterward. Suppress that click to avoid overwriting the double tap event.
            if (suppressNextItemClick) {
                suppressNextItemClick = false
                return@setOnItemClickListener
            }

            val now = SystemClock.uptimeMillis()

            // If injection falls back to ACTION_CLICK, MotionEvent-based double tap detection may not run.
            // Detect double tap using consecutive item clicks as a backup.
            if (position == pendingClickPos && now - pendingClickAtMs <= doubleTapTimeoutMs) {
                commitDoubleTap(position)
            } else {
                cancelPendingSingleClick()
                pendingClickAtMs = now
                pendingClickPos = position
                lastEvent.postDelayed(commitSingleClick, doubleTapTimeoutMs)
            }
        }

        list.setOnItemLongClickListener { _, _, position, _ ->
            list.setItemChecked(position, true)
            cancelPendingSingleClick()
            setEvent("リスト: 長押し (${items[position]})")
            true
        }

        val listDetector = GestureDetector(
            this,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onDown(e: MotionEvent): Boolean = true

                // Confirm double tap on ACTION_UP (closer to how item click is emitted) to avoid
                // the double tap event being overwritten by a later item click.
                override fun onDoubleTapEvent(e: MotionEvent): Boolean {
                    if (e.action != MotionEvent.ACTION_UP) return false

                    val pos = list.pointToPosition(e.x.toInt(), e.y.toInt())
                    if (pos != ListView.INVALID_POSITION) {
                        list.setItemChecked(pos, true)
                        suppressNextItemClick = true
                        commitDoubleTap(pos)
                        return true
                    }

                    suppressNextItemClick = true
                    cancelPendingSingleClick()
                    setEvent("リスト: ダブルタップ")
                    return true
                }
            },
        )
        list.setOnTouchListener { _, event ->
            listDetector.onTouchEvent(event)
            false
        }

        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val op = intent?.getStringExtra("op") ?: return
        Thread {
            val injector = ShizukuTouchInjector(applicationContext)
            when (op) {
                "tap" -> {
                    val x = intent.getIntExtra("x", 336)
                    val y = intent.getIntExtra("y", 469)
                    injector.tap(x, y)
                }
                "double_tap" -> {
                    val x = intent.getIntExtra("x", 490)
                    val y = intent.getIntExtra("y", 676)
                    injector.doubleTap(x, y)
                }
                "long_press" -> {
                    val x = intent.getIntExtra("x", 960)
                    val y = intent.getIntExtra("y", 469)
                    injector.longPress(x, y)
                }
                "swipe" -> {
                    val x1 = intent.getIntExtra("x1", 1430)
                    val y1 = intent.getIntExtra("y1", 900)
                    val x2 = intent.getIntExtra("x2", 1430)
                    val y2 = intent.getIntExtra("y2", 650)
                    injector.swipe(x1, y1, x2, y2)
                }
                "pinch_in" -> {
                    val cX = intent.getIntExtra("cx", 1430)
                    val cY = intent.getIntExtra("cy", 838)
                    val span = intent.getIntExtra("span", 300)
                    injector.pinchIn(
                        x1Start = cX - span, y1Start = cY,
                        x1End = cX - 60, y1End = cY,
                        x2Start = cX + span, y2Start = cY,
                        x2End = cX + 60, y2End = cY,
                    )
                }
                "pinch_out" -> {
                    val cX = intent.getIntExtra("cx", 1430)
                    val cY = intent.getIntExtra("cy", 838)
                    val span = intent.getIntExtra("span", 300)
                    injector.pinchOut(
                        x1Start = cX - 60, y1Start = cY,
                        x1End = cX - span, y1End = cY,
                        x2Start = cX + 60, y2Start = cY,
                        x2End = cX + span, y2End = cY,
                    )
                }
            }
        }.start()
    }

    override fun onDestroy() {
        val listener = gestureListener
        if (listener != null) {
            try {
                getSharedPreferences(SettingsKeys.PREFS_NAME, MODE_PRIVATE)
                    .unregisterOnSharedPreferenceChangeListener(listener)
            } catch (_: Exception) {
            }
        }
        gestureListener = null
        super.onDestroy()
    }
}
