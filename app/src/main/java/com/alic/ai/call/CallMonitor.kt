package com.alic.ai.call

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import android.telecom.TelecomManager
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import com.alic.ai.data.AppSettings
import com.alic.ai.data.SettingsStore
import com.alic.ai.voice.VoiceEngine
import com.alic.ai.wake.WakeWordService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

enum class CallAction { ANSWER, HANGUP }

/**
 * Answer / end phone calls through the platform TelecomManager.
 * Needs the runtime permission ANSWER_PHONE_CALLS. endCall() exists from Android 9 (API 28);
 * acceptRingingCall() from Android 8 (API 26). Nothing here bypasses Android security.
 */
object CallActions {
    private fun has(ctx: Context, p: String) = ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    fun hangUp(ctx: Context): String {
        if (Build.VERSION.SDK_INT < 28) return "Ending a call by voice needs Android 9 or newer."
        if (!has(ctx, Manifest.permission.ANSWER_PHONE_CALLS)) return "Allow “Answer and end calls” in ALIC's Permissions screen first."
        return try {
            val tm = ctx.getSystemService(TelecomManager::class.java)
            if (tm != null && tm.endCall()) "Call ended." else "Android did not end the call (is a call in progress?)."
        } catch (e: SecurityException) {
            "Permission missing: ${e.message}"
        } catch (e: Exception) {
            "Could not end the call: ${e.message}"
        }
    }

    @SuppressLint("MissingPermission")
    fun answer(ctx: Context): String {
        if (!has(ctx, Manifest.permission.ANSWER_PHONE_CALLS)) return "Allow “Answer and end calls” in ALIC's Permissions screen first."
        return try {
            ctx.getSystemService(TelecomManager::class.java).acceptRingingCall()
            "Call answered."
        } catch (e: SecurityException) {
            "Permission missing: ${e.message}"
        } catch (e: Exception) {
            "Could not answer the call: ${e.message}"
        }
    }

    /** Best effort: some phones ignore speakerphone changes made by other apps during a call. */
    fun speaker(ctx: Context, on: Boolean): String = try {
        val am = ctx.getSystemService(AudioManager::class.java)
        @Suppress("DEPRECATION")
        am.isSpeakerphoneOn = on
        "Speaker ${if (on) "on" else "off"} (some phones ignore this during a call)."
    } catch (e: Exception) {
        "Could not change the speaker: ${e.message}"
    }
}

/**
 * Tracks the phone-call state from the system PHONE_STATE broadcast, announces incoming calls by voice
 * and runs the offline "Hey Alic, hang up / answer" commands heard by the wake listener.
 *
 * Android limits (cannot be worked around): caller numbers need READ_CALL_LOG; once a call is connected,
 * Android usually blocks other apps' microphone input, so voice hang-up is reliable while the phone is RINGING
 * and may not hear you during an active call.
 */
object CallMonitor {
    @Volatile var ringing = false; private set
    @Volatile var inCall = false; private set
    @Volatile var number: String? = null; private set
    @Volatile var callerName: String? = null; private set

    private val main = Handler(Looper.getMainLooper())
    private var announcer: VoiceEngine? = null
    private var announceTask: Runnable? = null
    private var repeats = 0

    /** Extra system-prompt text so the Live model knows about a ringing / active call. */
    fun hint(): String = when {
        ringing -> "\nAn incoming phone call from ${display()} is ringing right now. If the user asks to answer or pick up, call answer_call; " +
            "if they ask to hang up, reject or cut it, call end_call."
        inCall -> "\nThe user is on a phone call. If they ask to end or hang up, call end_call."
        else -> ""
    }

    private fun display(): String = callerName ?: number ?: "an unknown number"

    fun onPhoneState(ctx: Context, state: String, num: String?, pending: BroadcastReceiver.PendingResult?) {
        when (state) {
            TelephonyManager.EXTRA_STATE_RINGING -> {
                if (!num.isNullOrBlank()) number = num          // a second broadcast may carry the number
                if (!ringing) {
                    ringing = true; inCall = false; repeats = 0
                    if (num.isNullOrBlank()) number = null
                    val st = settings(ctx)
                    if (st.announceCalls) schedule(ctx, st, 700, pending) else finish(pending)
                } else finish(pending)
            }
            TelephonyManager.EXTRA_STATE_OFFHOOK -> { ringing = false; inCall = true; stopAnnouncing(); finish(pending) }
            else -> { ringing = false; inCall = false; number = null; callerName = null; stopAnnouncing(); finish(pending) }
        }
    }

    private fun finish(p: BroadcastReceiver.PendingResult?) { try { p?.finish() } catch (e: Exception) {} }

    private fun settings(ctx: Context): AppSettings =
        try { runBlocking(Dispatchers.IO) { SettingsStore(ctx).flow.first() } } catch (e: Exception) { AppSettings() }

    private fun has(ctx: Context, p: String) = ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED

    private fun nameFor(ctx: Context, num: String?): String? {
        if (num.isNullOrBlank() || !has(ctx, Manifest.permission.READ_CONTACTS)) return null
        return try {
            val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(num))
            ctx.contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        } catch (e: Exception) { null }
    }

    private fun schedule(ctx: Context, st: AppSettings, delayMs: Long, pending: BroadcastReceiver.PendingResult?) {
        val t = Runnable { announce(ctx, st, pending) }
        announceTask = t
        main.postDelayed(t, delayMs)
    }

    private fun announce(ctx: Context, st: AppSettings, pending: BroadcastReceiver.PendingResult?) {
        if (!ringing) { finish(pending); return }
        if (callerName == null) callerName = nameFor(ctx, number)
        val engine = announcer ?: VoiceEngine(ctx).also { it.init(); announcer = it }
        var done = false
        fun complete() { if (!done) { done = true; finish(pending) } }
        main.postDelayed({ complete() }, 9000)           // never hold the broadcast longer than ~9 s
        engine.speakWhenReady(buildText(st), st) {
            complete()
            if (ringing && repeats < 2) {                 // repeat gently while it keeps ringing
                repeats++
                val t = Runnable { announce(ctx, st, null) }
                announceTask = t
                main.postDelayed(t, 6500)
            }
        }
    }

    private fun buildText(st: AppSettings): String {
        val en = st.language == "en"
        val digits = number?.filter { it.isDigit() }?.toList()?.joinToString(" ")
        val canVoice = WakeWordService.instance != null
        return if (en) {
            val who = callerName ?: if (digits.isNullOrBlank()) "an unknown number" else "the number $digits"
            "Incoming call from $who." + if (canVoice) " Say Hey Alic, answer, to pick up, or Hey Alic, hang up, to cut the call." else ""
        } else {
            val who = callerName?.let { "$it का" } ?: if (digits.isNullOrBlank()) "एक अनजान नंबर से" else "नंबर $digits से"
            "$who कॉल आ रहा है।" + if (canVoice) " उठाने के लिए बोलिए, हे एलिक, उठाओ। काटने के लिए बोलिए, हे एलिक, हैंग अप।" else ""
        }
    }

    private fun stopAnnouncing() {
        announceTask?.let { main.removeCallbacks(it) }
        announceTask = null
        try { announcer?.stopSpeaking() } catch (e: Exception) {}
    }

    /** Runs a command heard offline ("Hey Alic, hang up"). Call on the main thread. */
    fun runVoiceCommand(ctx: Context, action: CallAction) {
        stopAnnouncing()
        val result = if (action == CallAction.HANGUP) CallActions.hangUp(ctx) else CallActions.answer(ctx)
        val ok = result == "Call ended." || result == "Call answered."
        val st = settings(ctx)
        val en = st.language == "en"
        val say = when {
            ok && action == CallAction.HANGUP -> if (en) "Okay, call ended." else "ठीक है, कॉल काट दिया।"
            ok -> if (en) "Okay, call answered." else "ठीक है, कॉल उठा लिया।"
            else -> result
        }
        val engine = announcer ?: VoiceEngine(ctx).also { it.init(); announcer = it }
        engine.speakWhenReady(say, st) {}
    }
}

/** Receives the system PHONE_STATE broadcast (needs READ_PHONE_STATE). */
class CallStateReceiver : BroadcastReceiver() {
    @Suppress("DEPRECATION")
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return
        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return
        val number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)   // null without READ_CALL_LOG
        CallMonitor.onPhoneState(context.applicationContext, state, number, goAsync())
    }
}
