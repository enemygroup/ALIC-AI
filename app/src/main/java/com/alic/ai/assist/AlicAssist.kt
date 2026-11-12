package com.alic.ai.assist

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.service.voice.VoiceInteractionService
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService
import android.speech.RecognitionService
import android.speech.SpeechRecognizer
import android.view.View
import com.alic.ai.MainActivity
import com.alic.ai.live.LiveSessionController
import com.alic.ai.wake.PopupDriver
import com.alic.ai.wake.PopupView
import com.alic.ai.wake.WakeGate

/**
 * Registers ALIC AI in Android's "Default digital assistant app" list (Settings.ACTION_VOICE_INPUT_SETTINGS).
 * Whether it is listed depends on the OEM/Android build and must be tested on the physical phone.
 */
class AlicVoiceInteractionService : VoiceInteractionService()

class AlicSessionService : VoiceInteractionSessionService() {
    override fun onNewSession(args: Bundle?): VoiceInteractionSession = AlicSession(this)
}

/**
 * Assistant session: the content view is the same compact popup used by the wake overlay (shared PopupView).
 * Ownership: this session only stops a Live session that THIS instance started (ownsSession); a session already
 * running for the main app, the wake word or another caller is never stopped from here.
 */
class AlicSession(private val ctx: Context) : VoiceInteractionSession(ctx) {
    private var popup: PopupView? = null
    private var driver: PopupDriver? = null
    private var ownsSession = false

    override fun onCreateContentView(): View {
        val v = PopupView(ctx, LiveSessionController.settings.popupAnimation) { endInteraction("close button") }
        popup = v
        return v
    }

    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        LiveSessionController.init(ctx.applicationContext as Application)
        when (LiveSessionController.start(LiveSessionController.OWNER_SESSION)) {
            LiveSessionController.StartResult.OK -> ownsSession = true
            LiveSessionController.StartResult.ALREADY_RUNNING -> ownsSession = false   // someone else owns it: only observe
            LiveSessionController.StartResult.NO_API_KEY, LiveSessionController.StartResult.MIC_DENIED -> {
                // prerequisites missing: open the app so the user can finish setup
                val i = Intent(ctx, MainActivity::class.java).putExtra("alic_setup", true)
                try { startAssistantActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Exception) {
                    try { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e2: Exception) {}
                }
                hide()
                return
            }
        }
        popup?.let { p ->
            p.playEntrance()
            driver = PopupDriver(p) { reason, stop -> endInteraction(reason, stop) }.also { it.start() }
        }
    }

    private fun endInteraction(reason: String, stopSession: Boolean = true) {
        driver?.stop(); driver = null
        if (stopSession && ownsSession) { LiveSessionController.stop(ifOwner = LiveSessionController.OWNER_SESSION); ownsSession = false }
        WakeGate.ignoreFor(4000)
        hide()
    }

    override fun onHide() {
        driver?.stop(); driver = null
        if (ownsSession) { LiveSessionController.stop(ifOwner = LiveSessionController.OWNER_SESSION); ownsSession = false }
        popup?.release()
        super.onHide()
    }
}

/**
 * Stub RecognitionService required by the voice-interaction metadata on some Android versions.
 * It does NOT perform recognition: every request is answered with ERROR_CLIENT and nothing is kept running.
 * Uncertainty: a platform permission named BIND_RECOGNITION_SERVICE could not be verified, so the manifest
 * does not use one for this service.
 */
class AlicRecognitionService : RecognitionService() {
    override fun onStartListening(recognizerIntent: Intent?, listener: Callback?) {
        try { listener?.error(SpeechRecognizer.ERROR_CLIENT) } catch (e: Exception) {}
    }
    override fun onCancel(listener: Callback?) {}
    override fun onStopListening(listener: Callback?) {}
}
