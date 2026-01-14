package com.ftvrcm.ui

import android.os.Bundle
import android.view.GestureDetector
import android.view.MotionEvent
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.ftvrcm.R
import android.os.SystemClock
import android.view.ViewConfiguration
import android.content.SharedPreferences
import com.ftvrcm.data.SettingsKeys

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
