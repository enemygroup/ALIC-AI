package com.alic.ai.wake

import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import com.alic.ai.live.LiveSessionController

/**
 * Single WindowManager overlay (TYPE_APPLICATION_OVERLAY) anchored to the bottom of the screen.
 * Only one instance can exist. close() removes the view immediately and never waits for an animation.
 * Needs the "Display over other apps" special permission; without it show() returns false and the caller
 * continues without a visual popup. Note: Android may not draw overlays over the lock screen.
 */
object AlicOverlay {
    private val main = Handler(Looper.getMainLooper())
    private var wm: WindowManager? = null
    private var view: PopupView? = null
    private var driver: PopupDriver? = null
    private var ownerTag: String? = null

    fun isShowing() = view != null

    /** Returns true if an overlay is (now) showing. Always executes on the main thread. */
    fun show(ctx: Context, owner: String): Boolean {
        if (Looper.myLooper() != Looper.getMainLooper()) { main.post { show(ctx, owner) }; return true }
        if (view != null) return true                                   // never a second overlay
        val app = ctx.applicationContext
        if (!Settings.canDrawOverlays(app)) return false
        val manager = app.getSystemService(WindowManager::class.java) ?: return false
        val dm = app.resources.displayMetrics
        val v = PopupView(app, LiveSessionController.settings.popupAnimation) { close("close button", true) }
        val width = minOf(dm.widthPixels - (32 * dm.density).toInt(), (420 * dm.density).toInt())
        val lp = WindowManager.LayoutParams(
            width, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL; y = (28 * dm.density).toInt() }
        try {
            manager.addView(v, lp)
        } catch (e: Exception) {
            return false          // e.g. permission revoked meanwhile: continue without overlay, no crash
        }
        wm = manager; view = v; ownerTag = owner
        v.playEntrance()
        driver = PopupDriver(v) { reason, stop -> close(reason, stop) }.also { it.start() }
        return true
    }

    /** Removes the overlay now, clears all references/callbacks and ignores wake detections for 4 s. */
    fun close(reason: String, stopSession: Boolean = true) {
        if (Looper.myLooper() != Looper.getMainLooper()) { main.post { close(reason, stopSession) }; return }
        val v = view; val m = wm; val tag = ownerTag
        driver?.stop(); driver = null
        view = null; wm = null; ownerTag = null
        if (v != null) {
            try { v.release() } catch (e: Exception) {}
            try { m?.removeViewImmediate(v) } catch (e: Exception) {}
        }
        WakeGate.ignoreFor(4000)
        if (stopSession && tag != null) LiveSessionController.stop(ifOwner = tag)
    }
}
