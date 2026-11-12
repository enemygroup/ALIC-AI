package com.alic.ai.service

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.alic.ai.AlicApp
import com.alic.ai.MainActivity
import kotlinx.coroutines.flow.MutableSharedFlow

/** Lets the notification's Stop button end the active session inside the app. */
object SessionBus {
    val stop = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
}

/** Foreground service shown ONLY while a live voice session is using the microphone. */
class VoiceSessionService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            SessionBus.stop.tryEmit(Unit)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        val stopPi = PendingIntent.getService(
            this, 0, Intent(this, VoiceSessionService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val openPi = PendingIntent.getActivity(
            this, 1, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(this, AlicApp.CHANNEL)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("ALIC AI — microphone active")
            .setContentText("Live conversation in progress. Tap Stop to end it.")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openPi)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPi)
            .build()
        try {
            ServiceCompat.startForeground(this, 77, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } catch (e: Exception) {
            stopSelf()
        }
        return START_NOT_STICKY
    }

    companion object {
        const val ACTION_STOP = "com.alic.ai.STOP_SESSION"
        fun start(ctx: Context) {
            try {
                ContextCompat.startForegroundService(ctx, Intent(ctx, VoiceSessionService::class.java))
            } catch (e: Exception) { /* notification is a convenience; session still works */ }
        }
        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, VoiceSessionService::class.java))
        }
    }
}
