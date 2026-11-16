package com.alic.ai.service

import android.content.ComponentName
import android.content.Context
import android.media.session.MediaSessionManager
import android.service.notification.NotificationListenerService

/** Needed only to read the active media sessions (YouTube, YouTube Music). It does not read notifications. */
class MediaListenerService : NotificationListenerService()

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
