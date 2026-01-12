package com.ftvrcm.adb

import android.content.Context
import android.util.Log
import dadb.AdbKeyPair
import dadb.AdbShellResponse
import dadb.Dadb
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
            try {
                val d = getOrCreateDadb()
                // Use shell_v2 output; Dadb.shell returns AdbShellResponse.
                val resp = d.shell(command)
                Log.d(tag, "shell ok: $command (exitCode=${resp.exitCode})")
            } catch (t: Throwable) {
                Log.w(tag, "shell failed: $command (${t.javaClass.simpleName}: ${t.message})")
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
        result += "127.0.0.1"
        result += "localhost"

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

        return result.toList()
    }
}
