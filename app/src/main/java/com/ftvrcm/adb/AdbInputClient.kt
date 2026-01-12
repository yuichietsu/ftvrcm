package com.ftvrcm.adb

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.core.content.edit
import dadb.AdbKeyPair
import dadb.AdbShellResponse
import dadb.Dadb
import com.ftvrcm.data.SettingsKeys
import java.io.File
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Collections
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class AdbInputClient(
    private val context: Context,
    private val host: String = "auto",
    private val port: Int = 5555,
) : AutoCloseable {

    private val tag = "AdbInputClient"

    private val executor: ExecutorService = Executors.newSingleThreadExecutor { r ->
        Thread(r, "ftvrcm-adb-input").apply { isDaemon = true }
    }

    @Volatile
    private var dadb: Dadb? = null

    fun tap(x: Int, y: Int) {
        submitShell("input tap $x $y")
    }

    fun longPress(x: Int, y: Int, durationMs: Int = 600) {
        // Long press via swipe-to-self with duration.
        submitShell("input swipe $x $y $x $y $durationMs")
    }

    fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Int = 200) {
        submitShell("input swipe $x1 $y1 $x2 $y2 $durationMs")
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

    private fun inferType(command: String): String {
        val c = command.trim()
        return when {
            c.startsWith("input tap ") -> "adb_tap"
            c.startsWith("input swipe ") -> {
                // crude heuristic: swipe-to-self is used for long press
                val parts = c.split(' ').filter { it.isNotBlank() }
                if (parts.size >= 6 && parts[2] == parts[4] && parts[3] == parts[5]) "adb_long_press" else "adb_swipe"
            }
            c.startsWith("settings put ") || c.startsWith("settings get ") -> "adb_settings"
            else -> "adb_shell"
        }
    }

    /**
     * Runs a shell command synchronously.
     *
     * Call from a background thread.
     */
    fun runShellBlocking(command: String): AdbShellResponse {
        val d = getOrCreateDadb()
        return d.shell(command)
    }

    override fun close() {
        try {
            dadb?.close()
        } catch (_: Throwable) {
        }
        dadb = null
        executor.shutdownNow()
    }

    private fun submitShell(command: String) {
        executor.execute {
            val type = inferType(command)
            record(type = type, status = "DISPATCHING", detail = command)
            try {
                val d = getOrCreateDadb()
                // Use shell_v2 output; Dadb.shell returns AdbShellResponse.
                val resp = d.shell(command)
                val ok = resp.exitCode == 0
                Log.d(tag, "shell ok: $command (exitCode=${resp.exitCode})")
                record(
                    type = type,
                    status = if (ok) "COMPLETED" else "FAILED",
                    detail = buildString {
                        append(command)
                        append("\nexitCode=")
                        append(resp.exitCode)
                        val err = resp.errorOutput.trim()
                        if (err.isNotEmpty()) {
                            append("\nstderr=")
                            append(err.take(400))
                        }
                        val out = resp.output.trim()
                        if (out.isNotEmpty()) {
                            append("\nstdout=")
                            append(out.take(400))
                        }
                    },
                )
            } catch (t: Throwable) {
                Log.w(tag, "shell failed: $command (${t.javaClass.simpleName}: ${t.message})")
                record(
                    type = type,
                    status = "FAILED",
                    detail = "$command\n${t.javaClass.simpleName}: ${t.message}",
                )
                // Reset connection so next command retries.
                try {
                    dadb?.close()
                } catch (_: Throwable) {
                }
                dadb = null
            }
        }
    }

    @Synchronized
    private fun getOrCreateDadb(): Dadb {
        val existing = dadb
        if (existing != null) return existing

        val keyDir = File(context.filesDir, "adb").apply { mkdirs() }
        val privateKey = File(keyDir, "adbkey")
        val publicKey = File(keyDir, "adbkey.pub")
        if (!privateKey.exists() || !publicKey.exists()) {
            AdbKeyPair.generate(privateKey, publicKey)
        }

        val keyPair = AdbKeyPair.read(privateKey, publicKey)

        val candidates = buildHostCandidates(host)
        var lastError: Throwable? = null
        for (candidate in candidates) {
            try {
                Log.i(tag, "adb connect try: $candidate:$port")
                val created = Dadb.create(
                    host = candidate,
                    port = port,
                    keyPair = keyPair,
                    connectTimeout = 5000,
                    socketTimeout = 5000,
                    keepAlive = true,
                )
                dadb = created
                Log.i(tag, "adb connected: $candidate:$port")
                return created
            } catch (t: Throwable) {
                lastError = t
                Log.w(tag, "adb connect failed: $candidate:$port (${t.javaClass.simpleName}: ${t.message})")
            }
        }

        throw lastError ?: IllegalStateException("adb connect failed")
    }

    private fun buildHostCandidates(rawHost: String): List<String> {
        val normalized = rawHost.trim()
        if (normalized.isNotEmpty() && !normalized.equals("auto", ignoreCase = true)) {
            return listOf(normalized)
        }

        val result = LinkedHashSet<String>()
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return result.toList()
            for (ni in Collections.list(interfaces)) {
                if (!ni.isUp || ni.isLoopback) continue
                for (addr in Collections.list(ni.inetAddresses)) {
                    val v4 = addr as? Inet4Address ?: continue
                    if (v4.isLoopbackAddress) continue
                    val hostAddress = v4.hostAddress ?: continue
                    // Skip link-local.
                    if (hostAddress.startsWith("169.254.")) continue
                    result += hostAddress
                }
            }
        } catch (t: Throwable) {
            Log.w(tag, "failed to enumerate local IPs (${t.javaClass.simpleName}: ${t.message})")
        }

        // Try loopback last.
        result += "127.0.0.1"
        result += "localhost"

        return result.toList()
    }
}
