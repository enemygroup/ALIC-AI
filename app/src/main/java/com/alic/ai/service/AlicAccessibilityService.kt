package com.alic.ai.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent

/**
 * Accessibility service: Back/Home/Recents and screen scrolling on command.
 * It does NOT read screen content (canRetrieveWindowContent=false, events are ignored).
 */
class AlicAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() { instance = this }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}
    override fun onUnbind(intent: Intent?): Boolean { instance = null; return super.onUnbind(intent) }

    companion object {
        @Volatile var instance: AlicAccessibilityService? = null

        fun isEnabled(ctx: Context): Boolean {
            val enabled = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
            val me = ComponentName(ctx, AlicAccessibilityService::class.java).flattenToString()
            return enabled.split(':').any { it.equals(me, ignoreCase = true) }
        }

        /** down = show lower content (the finger swipes up). Returns a status text. */
        fun scroll(down: Boolean): String {
            val svc = instance ?: return "Turn on ALIC AI in Accessibility settings first."
            val dm = svc.resources.displayMetrics
            val x = dm.widthPixels / 2f
            val yFrom = if (down) dm.heightPixels * 0.72f else dm.heightPixels * 0.28f
            val yTo = if (down) dm.heightPixels * 0.28f else dm.heightPixels * 0.72f
            val path = Path().apply { moveTo(x, yFrom); lineTo(x, yTo) }
            val gesture = GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0, 350)).build()
            return if (svc.dispatchGesture(gesture, null, null)) "Scrolled ${if (down) "down" else "up"}." else "Android refused the scroll."
        }
    }
}
