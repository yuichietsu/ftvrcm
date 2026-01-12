package com.ftvrcm.adb

import android.content.Context
import dadb.AdbKeyPair
import dadb.Dadb
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class AdbInputClient(
    private val context: Context,
    private val host: String = "127.0.0.1",
    private val port: Int = 5555,
) : AutoCloseable {

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
                d.shell(command)
            } catch (_: Throwable) {
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
        val created = Dadb.create(
            host = host,
            port = port,
            keyPair = keyPair,
            connectTimeout = 700,
            socketTimeout = 700,
            keepAlive = true,
        )
        dadb = created
        return created
    }
}
