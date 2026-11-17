package com.alic.ai.service

import android.content.ComponentName
import android.content.Context
import android.media.session.MediaSessionManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Notification access (with the user's permission). Keeps only the last 30 notifications IN MEMORY,
 * never on disk, and they are returned only when the user asks for a summary.
 */
class MediaListenerService : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val n = sbn ?: return
        if (n.packageName == packageName) return
        val extras = n.notification.extras
        val title = extras.getCharSequence("android.title")?.toString().orEmpty()
        val text = extras.getCharSequence("android.text")?.toString().orEmpty()
        if (title.isBlank() && text.isBlank()) return
        NotificationStore.add(AppNotice(n.packageName, title, text))
    }
}

data class AppNotice(val pkg: String, val title: String, val text: String)

object NotificationStore {
    private const val MAX = 30
    private val items = ArrayDeque<AppNotice>()

    @Synchronized fun add(n: AppNotice) {
        items.addLast(n)
        while (items.size > MAX) items.removeFirst()
    }

    @Synchronized fun latest(limit: Int): List<AppNotice> = items.toList().takeLast(limit.coerceIn(1, MAX)).reversed()
}

object MediaControl {
    fun run(ctx: Context, action: String): String {
        val mgr = ctx.getSystemService(MediaSessionManager::class.java) ?: return "Media control is not available."
        val sessions = try {
            mgr.getActiveSessions(ComponentName(ctx, MediaListenerService::class.java))
        } catch (e: SecurityException) {
            return "Turn on notification access for ALIC AI in Permissions first."
        }
        val c = sessions.firstOrNull() ?: return "No music or video is playing right now."
        val t = c.transportControls
        val pos = c.playbackState?.position ?: 0L
        return when (action.trim().lowercase()) {
            "play", "resume" -> { t.play(); "Playing." }
            "pause", "stop" -> { t.pause(); "Paused." }
            "next" -> { t.skipToNext(); "Next track." }
            "previous", "back_track" -> { t.skipToPrevious(); "Previous track." }
            "forward" -> { t.seekTo(pos + 10_000L); "Forward 10 seconds." }
            "rewind" -> { t.seekTo((pos - 10_000L).coerceAtLeast(0L)); "Back 10 seconds." }
            else -> "Unknown media action \"$action\"."
        }
    }
}
