package com.ftvrcm.action

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri

class ActionFactory(private val context: Context) {

    fun createFromActionId(actionId: String): Action? {
        if (actionId.isBlank() || actionId == "none") return null

        return when {
            actionId.startsWith("launch_app_") -> {
                val packageName = actionId.substringAfter("launch_app_")
                LaunchAppAction(context, packageName)
            }
            actionId.startsWith("open_url_") -> {
                val url = actionId.substringAfter("open_url_")
                OpenUrlAction(context, url)
            }
            actionId.startsWith("adjust_volume_") -> {
                val delta = actionId.substringAfter("adjust_volume_").toIntOrNull() ?: return null
                val direction = when {
                    delta > 0 -> AudioManager.ADJUST_RAISE
                    delta < 0 -> AudioManager.ADJUST_LOWER
                    else -> AudioManager.ADJUST_SAME
                }
                AdjustVolumeAction(context, direction)
            }
            else -> null
        }
    }

    fun create(type: String, param: String): Action? {
        return when (type) {
            "none" -> null
            "launch_app" -> LaunchAppAction(context, param)
            "open_url" -> OpenUrlAction(context, param)
            "volume_up" -> AdjustVolumeAction(context, AudioManager.ADJUST_RAISE)
            "volume_down" -> AdjustVolumeAction(context, AudioManager.ADJUST_LOWER)
            else -> null
        }
    }

    private class LaunchAppAction(
        private val context: Context,
        private val packageName: String,
    ) : Action {
        override val id: String = "launch_app:$packageName"
        override val description: String = "アプリ起動: $packageName"

        override fun execute() {
            if (packageName.isBlank()) return
            val intent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }

    private class OpenUrlAction(
        private val context: Context,
        private val url: String,
    ) : Action {
        override val id: String = "open_url:$url"
        override val description: String = "URLを開く: $url"

        override fun execute() {
            if (url.isBlank()) return
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }

    private class AdjustVolumeAction(
        private val context: Context,
        private val direction: Int,
    ) : Action {
        override val id: String = "adjust_volume:$direction"
        override val description: String = when (direction) {
            AudioManager.ADJUST_RAISE -> "音量上げ"
            AudioManager.ADJUST_LOWER -> "音量下げ"
            else -> "音量調整"
        }

        override fun execute() {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
            audioManager.adjustVolume(direction, AudioManager.FLAG_SHOW_UI)
        }
    }
}
