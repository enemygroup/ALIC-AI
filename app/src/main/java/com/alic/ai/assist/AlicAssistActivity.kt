package com.alic.ai.assist

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.alic.ai.MainActivity
import com.alic.ai.live.LiveSessionController
import com.alic.ai.wake.AlicOverlay

/**
 * Transparent entry point for android.intent.action.ASSIST and VOICE_COMMAND. Its existence is what makes ALIC AI
 * eligible for the "Default digital assistant app" list. It never shows its own UI: it validates prerequisites,
 * starts the assistant session (popup overlay), then finishes so no blank task is left behind.
 */
class AlicAssistActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LiveSessionController.init(application)
        val mic = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (!mic || !LiveSessionController.hasApiKey()) {
            // prerequisites missing -> open the app's setup (permissions / API key)
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("alic_setup", true))
            finish(); return
        }
        if (!Settings.canDrawOverlays(this)) {
            // no overlay permission: use the app's own Live screen instead of a popup
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("alic_live", true))
            finish(); return
        }
        val shown = AlicOverlay.show(this, LiveSessionController.OWNER_ASSIST)
        val r = LiveSessionController.start(LiveSessionController.OWNER_ASSIST)
        if (shown && r != LiveSessionController.StartResult.OK && r != LiveSessionController.StartResult.ALREADY_RUNNING) {
            AlicOverlay.close("start failed", false)
        }
        finish()
    }
}
