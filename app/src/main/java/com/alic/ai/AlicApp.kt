package com.alic.ai

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.util.Log
import com.alic.ai.live.LiveSessionController
import java.io.File

class AlicApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                File(filesDir, "crash.txt").writeText(Log.getStackTraceString(error))
            } catch (e: Exception) {
            }
            previous?.uncaughtException(thread, error)
        }
        initLive()
        try {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Active voice session", NotificationManager.IMPORTANCE_LOW)
            )
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_WAKE, "Wake word listening", NotificationManager.IMPORTANCE_LOW)
            )
        } catch (e: Exception) {
        }
    }

    // shared Live session owner, so the wake word / assistant can use it without the UI
    private fun initLive() { LiveSessionController.init(this) }

    companion object {
        const val CHANNEL = "alic_session"
        const val CHANNEL_WAKE = "alic_wake"
    }
}
