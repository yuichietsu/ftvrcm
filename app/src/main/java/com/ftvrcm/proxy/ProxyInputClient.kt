package com.ftvrcm.proxy

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.core.content.edit
import com.ftvrcm.data.SettingsKeys
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class ProxyInputClient(
    private val context: Context,
    private val host: String,
    private val port: Int,
    private val token: String,
) {

    private val tag = "ProxyInputClient"

    fun tap(x: Int, y: Int) {
        postJson(
            path = "/tap",
            type = "proxy_tap",
            jsonBody = "{\"x\":$x,\"y\":$y}",
        )
    }

    fun longPress(x: Int, y: Int, durationMs: Int = 600) {
        postJson(
            path = "/longPress",
            type = "proxy_long_press",
            jsonBody = "{\"x\":$x,\"y\":$y,\"durationMs\":$durationMs}",
        )
    }

    fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Int = 200) {
        postJson(
            path = "/swipe",
            type = "proxy_swipe",
            jsonBody = "{\"x1\":$x1,\"y1\":$y1,\"x2\":$x2,\"y2\":$y2,\"durationMs\":$durationMs}",
        )
    }

    private fun record(type: String, status: String, detail: String) {
        try {
            context.getSharedPreferences(SettingsKeys.PREFS_NAME, Context.MODE_PRIVATE)
                .edit {
                    putString(SettingsKeys.LAST_GESTURE_TYPE, type)
                    putString(SettingsKeys.LAST_GESTURE_STATUS, status)
                    putString(SettingsKeys.LAST_GESTURE_DETAIL, detail)
                    putLong(SettingsKeys.LAST_GESTURE_AT_MS, SystemClock.uptimeMillis())
                }
        } catch (_: Throwable) {
        }
    }

    private fun postJson(path: String, type: String, jsonBody: String) {
        record(type = type, status = "DISPATCHING", detail = "POST $path $jsonBody")

        val normalizedHost = host.trim()
        if (normalizedHost.isEmpty()) {
            record(type = type, status = "FAILED", detail = "proxy_host is empty")
            return
        }

        val url = URL("http://$normalizedHost:$port$path")
        try {
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 2500
                readTimeout = 2500
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                if (token.isNotBlank()) {
                    setRequestProperty("X-Auth-Token", token)
                }
            }

            conn.outputStream.use { os ->
                os.write(jsonBody.toByteArray(Charsets.UTF_8))
            }

            val code = conn.responseCode
            val body = try {
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                stream?.let {
                    BufferedReader(InputStreamReader(it, Charsets.UTF_8)).use { r -> r.readText() }
                } ?: ""
            } catch (_: Throwable) {
                ""
            }

            val ok = code in 200..299
            Log.i(tag, "proxy $path http=$code")
            record(
                type = type,
                status = if (ok) "COMPLETED" else "FAILED",
                detail = "http=$code url=$url\n${body.take(800)}",
            )
        } catch (t: Throwable) {
            Log.w(tag, "proxy $path failed (${t.javaClass.simpleName}: ${t.message})")
            record(type = type, status = "FAILED", detail = "${t.javaClass.simpleName}: ${t.message}")
        }
    }
}
