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

    data class HealthCheckResult(
        val ok: Boolean,
        val detail: String,
    )

    data class CommandResult(
        val ok: Boolean,
        val detail: String,
    )

    private val tag = "ProxyInputClient"

    fun tap(x: Int, y: Int): Boolean {
        return postJson(
            path = "/tap",
            type = "proxy_tap",
            jsonBody = "{\"x\":$x,\"y\":$y}",
        )
    }

    fun doubleTap(x: Int, y: Int): Boolean {
        return postJson(
            path = "/doubleTap",
            type = "proxy_double_tap",
            jsonBody = "{\"x\":$x,\"y\":$y}",
        )
    }

    fun longPress(x: Int, y: Int, durationMs: Int = 600): Boolean {
        return postJson(
            path = "/longPress",
            type = "proxy_long_press",
            jsonBody = "{\"x\":$x,\"y\":$y,\"durationMs\":$durationMs}",
        )
    }

    fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Int = 200): Boolean {
        return postJson(
            path = "/swipe",
            type = "proxy_swipe",
            jsonBody = "{\"x1\":$x1,\"y1\":$y1,\"x2\":$x2,\"y2\":$y2,\"durationMs\":$durationMs}",
        )
    }

    fun pinchIn(
        x1Start: Int,
        y1Start: Int,
        x1End: Int,
        y1End: Int,
        x2Start: Int,
        y2Start: Int,
        x2End: Int,
        y2End: Int,
        durationMs: Int = 240,
    ): Boolean {
        return postJson(
            path = "/pinchIn",
            type = "proxy_pinch_in",
            jsonBody = "{\"x1Start\":$x1Start,\"y1Start\":$y1Start,\"x1End\":$x1End,\"y1End\":$y1End," +
                "\"x2Start\":$x2Start,\"y2Start\":$y2Start,\"x2End\":$x2End,\"y2End\":$y2End,\"durationMs\":$durationMs}",
        )
    }

    fun pinchOut(
        x1Start: Int,
        y1Start: Int,
        x1End: Int,
        y1End: Int,
        x2Start: Int,
        y2Start: Int,
        x2End: Int,
        y2End: Int,
        durationMs: Int = 240,
    ): Boolean {
        return postJson(
            path = "/pinchOut",
            type = "proxy_pinch_out",
            jsonBody = "{\"x1Start\":$x1Start,\"y1Start\":$y1Start,\"x1End\":$x1End,\"y1End\":$y1End," +
                "\"x2Start\":$x2Start,\"y2Start\":$y2Start,\"x2End\":$x2End,\"y2End\":$y2End,\"durationMs\":$durationMs}",
        )
    }

    fun healthCheck(): HealthCheckResult {
        val type = "proxy_health"
        record(type = type, status = "DISPATCHING", detail = "GET /health")

        val normalizedHost = host.trim()
        if (normalizedHost.isEmpty()) {
            val detail = "proxy_host is empty"
            record(type = type, status = "FAILED", detail = detail)
            return HealthCheckResult(ok = false, detail = detail)
        }

        val url = URL("http://$normalizedHost:$port/health")
        try {
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 2500
                readTimeout = 2500
                if (token.isNotBlank()) {
                    setRequestProperty("X-Auth-Token", token)
                }
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
            Log.i(tag, "proxy /health http=$code")
            val detail = "http=$code url=$url\n${body.take(800)}"
            record(
                type = type,
                status = if (ok) "COMPLETED" else "FAILED",
                detail = detail,
            )
            return HealthCheckResult(ok = ok, detail = detail)
        } catch (t: Throwable) {
            Log.w(tag, "proxy /health failed (${t.javaClass.simpleName}: ${t.message})")
            val detail = "${t.javaClass.simpleName}: ${t.message}"
            record(type = type, status = "FAILED", detail = detail)
            return HealthCheckResult(ok = false, detail = detail)
        }
    }

    fun grantAccessibility(component: String): CommandResult {
        val body = "{\"component\":\"${component}\"}"
        return postJsonForResult(
            path = "/grantAccessibility",
            type = "proxy_grant_accessibility",
            jsonBody = body,
        )
    }

    fun rotateScreen(): CommandResult {
        return postJsonForResult(
            path = "/rotateScreen",
            type = "proxy_rotate_screen",
            jsonBody = "{}",
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

    private fun postJson(path: String, type: String, jsonBody: String): Boolean {
        record(type = type, status = "DISPATCHING", detail = "POST $path $jsonBody")

        val normalizedHost = host.trim()
        if (normalizedHost.isEmpty()) {
            record(type = type, status = "FAILED", detail = "proxy_host is empty")
            return false
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
            return ok
        } catch (t: Throwable) {
            Log.w(tag, "proxy $path failed (${t.javaClass.simpleName}: ${t.message})")
            record(type = type, status = "FAILED", detail = "${t.javaClass.simpleName}: ${t.message}")
            return false
        }
    }

    private fun postJsonForResult(path: String, type: String, jsonBody: String): CommandResult {
        record(type = type, status = "DISPATCHING", detail = "POST $path $jsonBody")

        val normalizedHost = host.trim()
        if (normalizedHost.isEmpty()) {
            val detail = "proxy_host is empty"
            record(type = type, status = "FAILED", detail = detail)
            return CommandResult(ok = false, detail = detail)
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
            val detail = "http=$code url=$url\n${body.take(2000)}"
            record(
                type = type,
                status = if (ok) "COMPLETED" else "FAILED",
                detail = detail,
            )
            return CommandResult(ok = ok, detail = detail)
        } catch (t: Throwable) {
            Log.w(tag, "proxy $path failed (${t.javaClass.simpleName}: ${t.message})")
            val detail = "${t.javaClass.simpleName}: ${t.message}"
            record(type = type, status = "FAILED", detail = detail)
            return CommandResult(ok = false, detail = detail)
        }
    }
}
