package com.alic.ai.wake

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.alic.ai.live.LiveSessionController
import com.alic.ai.live.LiveSessionController.Phase

/**
 * Feeds real session state into a PopupView every 100 ms and enforces the silence timeout.
 * The timer is reset while the user is speaking (mic level), while ALIC is speaking, or when new transcript arrives.
 * stop() removes every pending callback.
 */
class PopupDriver(private val view: PopupView, private val onEnd: (reason: String, stopSession: Boolean) -> Unit) {
    private val handler = Handler(Looper.getMainLooper())
    private var running = false
    private var lastActivity = 0L
    private var lastSignature = 0

    private val tick = object : Runnable {
        override fun run() {
            if (!running) return
            val c = LiveSessionController
            if (!c.active) { running = false; onEnd("session ended", false); return }
            view.render(c.phase, c.level)
            val now = SystemClock.elapsedRealtime()
            val sig = c.log.size * 31 + (c.log.lastOrNull()?.text?.length ?: 0)
            if (c.level > 0.08f || c.phase == Phase.SPEAKING || sig != lastSignature) lastActivity = now
            lastSignature = sig
            val timeoutMs = c.settings.silenceTimeoutSec.coerceIn(5, 15) * 1000L
            if (now - lastActivity > timeoutMs) { running = false; onEnd("silence timeout", true); return }
            handler.postDelayed(this, 100)
        }
    }

    fun start() {
        running = true
        lastActivity = SystemClock.elapsedRealtime()
        handler.removeCallbacks(tick)
        handler.post(tick)
    }

    fun stop() {
        running = false
        handler.removeCallbacks(tick)
    }
}
