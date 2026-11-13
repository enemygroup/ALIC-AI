package com.alic.ai.wake

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.ToneGenerator
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.alic.ai.AlicApp
import com.alic.ai.MainActivity
import com.alic.ai.call.CallAction
import com.alic.ai.call.CallMonitor
import com.alic.ai.data.AppSettings
import com.alic.ai.data.SettingsStore
import com.alic.ai.live.CloseCommands
import com.alic.ai.live.LiveSessionController
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import org.vosk.LibVosk
import org.vosk.LogLevel
import org.vosk.Model
import org.vosk.Recognizer
import kotlin.math.max
import kotlin.math.sqrt

/** What happens after a real wake-word detection. Runs on the main thread. */
object WakeActions {
    fun onWake(ctx: Context, s: AppSettings) {
        if (SystemClock.elapsedRealtime() < WakeGate.ignoreUntil) return
        if (LiveSessionController.active) return
        if (s.wakeSound) beep()
        // overlay permission missing -> only the visual popup is skipped; the voice flow still starts
        val overlayShown = Settings.canDrawOverlays(ctx) && AlicOverlay.show(ctx, LiveSessionController.OWNER_WAKE)
        when (LiveSessionController.start(LiveSessionController.OWNER_WAKE)) {
            LiveSessionController.StartResult.OK, LiveSessionController.StartResult.ALREADY_RUNNING -> {}
            LiveSessionController.StartResult.NO_API_KEY -> {
                if (overlayShown) AlicOverlay.close("no api key", false)
                needsSetup(ctx, "Add your Gemini API key in ALIC AI to use “Hey Alic”.")
            }
            LiveSessionController.StartResult.MIC_DENIED -> {
                if (overlayShown) AlicOverlay.close("mic denied", false)
                needsSetup(ctx, "Microphone permission is needed for “Hey Alic”.")
            }
        }
    }

    private fun beep() {
        try {
            val tg = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
            tg.startTone(ToneGenerator.TONE_PROP_BEEP, 150)
            Handler(Looper.getMainLooper()).postDelayed({ try { tg.release() } catch (e: Exception) {} }, 400)
        } catch (e: Exception) {
        }
    }

    private fun needsSetup(ctx: Context, text: String) {
        try {
            if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
                android.os.Build.VERSION.SDK_INT >= 33) return
            val pi = PendingIntent.getActivity(ctx, 5, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
            val n = NotificationCompat.Builder(ctx, AlicApp.CHANNEL_WAKE)
                .setSmallIcon(android.R.drawable.ic_btn_speak_now).setContentTitle("ALIC AI").setContentText(text)
                .setContentIntent(pi).setAutoCancel(true).build()
            NotificationManagerCompat.from(ctx).notify(79, n)
        } catch (e: SecurityException) {
        }
    }
}

/**
 * Foreground (microphone) service that listens for "Hey Alic" fully offline with Vosk.
 *
 * - exactly one AudioRecord (VOICE_RECOGNITION, 16 kHz, mono, PCM16), owned by exactly one worker thread
 * - model load, asset copy, audio reads and recognition all happen on that background thread
 * - start/pause/resume are idempotent and synchronized; pause() blocks until the AudioRecord is released
 *
 * Limitations (Android, not bugs): a third-party app cannot guarantee always-on mic listening on the lock screen,
 * Android 14+ refuses to START a microphone foreground service from the background (we start it only from a
 * visible app / user action and report if Android blocks it), and a force-stopped app does not restart itself.
 * No Accessibility tricks, hidden APIs or device-admin workarounds are used.
 */
class WakeWordService : Service() {
    companion object {
        const val RATE = 16000
        const val ACTION_STOP = "com.alic.ai.WAKE_STOP"
        const val NOTIF_ID = 78
        private const val WAKE_COOLDOWN_MS = 3000L

        /** Single shared session flag: true while a Gemini Live session owns the microphone. */
        @Volatile var sessionActiveFlag = false
        @Volatile var instance: WakeWordService? = null
        val status = MutableStateFlow("Off")
        val phrasesInfo = MutableStateFlow("")

        /** Starts the service from an eligible (visible / user-initiated) context. Returns false if Android refused. */
        fun start(ctx: Context): Boolean = try {
            ContextCompat.startForegroundService(ctx, Intent(ctx, WakeWordService::class.java))
            true
        } catch (e: Exception) {
            status.value = "Android blocked starting the microphone service (${e.javaClass.simpleName}). Open ALIC AI and try again."
            false
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, WakeWordService::class.java))
            if (instance == null) status.value = "Off"
        }

        fun onSessionStarted() { sessionActiveFlag = true; instance?.pauseListening() }
        fun onSessionEnded() { sessionActiveFlag = false; instance?.resumeListening() }
    }

    private val lock = Any()
    @Volatile private var generation = 0
    private var worker: Thread? = null
    @Volatile private var model: Model? = null
    @Volatile private var phrases: List<String> = emptyList()
    @Volatile private var callPhrases: List<Pair<String, CallAction>> = emptyList()
    @Volatile private var cfg = AppSettings()
    @Volatile private var lastWakeMs = 0L
    private val main = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        scope.launch { SettingsStore(applicationContext).flow.collect { cfg = it } }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            scope.launch { SettingsStore(applicationContext).update { it.copy(wakeEnabled = false) } }
            status.value = "Off"
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        try {
            ServiceCompat.startForeground(this, NOTIF_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } catch (e: Exception) {
            status.value = "Android blocked the microphone service (${e.javaClass.simpleName}). Open the app and enable Wake word again."
            stopSelf()
            return START_NOT_STICKY
        }
        if (!hasMic()) {
            status.value = "Microphone permission is denied, so background wake listening cannot run."
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        if (sessionActiveFlag) status.value = "Paused while a Gemini session is active" else resumeListening()
        return START_STICKY
    }

    override fun onDestroy() {
        synchronized(lock) { generation++; joinWorker() }
        try { model?.close() } catch (e: Throwable) {}
        model = null
        scope.cancel()
        instance = null
        if (!sessionActiveFlag) status.value = "Off"
        super.onDestroy()
    }

    private fun hasMic() = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun buildNotification(): Notification {
        val open = PendingIntent.getActivity(this, 2, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(
            this, 3, Intent(this, WakeWordService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, AlicApp.CHANNEL_WAKE)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("ALIC AI is listening for “Hey Alic”")
            .setContentText("Offline wake word. Tap Turn off to stop.")
            .setOngoing(true).setPriority(NotificationCompat.PRIORITY_LOW).setContentIntent(open)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Turn off", stop)
            .build()
    }

    // ---------------- listener lifecycle (idempotent + synchronized) ----------------

    /** Stops the wake AudioRecord. Returns only after the worker thread has released it. */
    fun pauseListening() {
        synchronized(lock) {
            generation++
            joinWorker()
        }
        status.value = "Paused while a Gemini session is active"
    }

    fun resumeListening() {
        synchronized(lock) {
            if (instance !== this || sessionActiveFlag || !hasMic()) return
            if (worker?.isAlive == true) return           // already listening: nothing to do
            val gen = ++generation
            worker = Thread({ listenLoop(gen) }, "alic-wake").also { it.start() }
        }
    }

    private fun joinWorker() {
        val w = worker ?: return
        worker = null
        if (w !== Thread.currentThread()) try { w.join(2000) } catch (e: InterruptedException) {}
    }

    private fun isCurrent(gen: Int) = gen == generation && !sessionActiveFlag && instance === this

    // ---------------- model ----------------

    private fun ensureModel(): Model? {
        model?.let { return it }
        status.value = "Preparing speech model…"
        val dir = try {
            VoskModelManager.ensureInstalled(applicationContext)
        } catch (e: Throwable) {
            status.value = "Model problem: ${e.message}"
            return null
        }
        return try {
            // Uncertainty: LibVosk.setLogLevel / LogLevel names are taken from the vosk-android 0.3.47 sources and could not be
            // verified here (no build); they are only used to quiet logs, so failure is ignored.
            try { LibVosk.setLogLevel(LogLevel.WARNINGS) } catch (e: Throwable) {}
            val usable = VoskModelManager.usablePhrases(dir)
            if (usable.isEmpty()) {
                status.value = "This model's vocabulary has no word usable for “Hey Alic”. Try the English model (assets/model-en-us)."
                return null
            }
            val m = Model(dir.absolutePath)
            phrases = usable
            callPhrases = VoskModelManager.usableCallCommands(dir, usable)
            phrasesInfo.value = usable.joinToString(" · ")
            model = m
            m
        } catch (e: Throwable) {
            status.value = "Speech model failed to load (corrupt or incompatible): ${e.message}"
            null
        }
    }

    // ---------------- worker thread ----------------

    @SuppressLint("MissingPermission")
    private fun createRecord(): AudioRecord? {
        val min = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (min <= 0) return null
        val r = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION, RATE,
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, max(min, RATE)
        )
        return if (r.state == AudioRecord.STATE_INITIALIZED) r else { r.release(); null }
    }

    private fun release(r: AudioRecord?) {
        try { r?.stop() } catch (e: Exception) {}
        try { r?.release() } catch (e: Exception) {}
    }

    private fun listenLoop(gen: Int) {
        var recognizer: Recognizer? = null
        var ar: AudioRecord? = null
        try {
            cfg = runBlocking { SettingsStore(applicationContext).flow.first() }
            if (!cfg.wakeEnabled) { status.value = "Off (wake word is disabled in settings)"; return }
            val m = ensureModel() ?: return
            if (!isCurrent(gen)) return
            // grammar equivalent to ["hey alic", ..., "[unk]"]: recognition is constrained to the wake phrase.
            // While a call is ringing it also contains "hey alic + hang up / answer" commands.
            fun grammar(call: Boolean): String = JSONArray().also { a ->
                phrases.forEach { a.put(it) }
                if (call) callPhrases.forEach { a.put(it.first) }
                a.put("[unk]")
            }.toString()
            var callMode = false
            var rec = Recognizer(m, RATE.toFloat(), grammar(false))
            recognizer = rec
            var lastCallCmdMs = 0L
            status.value = "Listening for “Hey Alic”"

            val buf = ByteArray(2560)       // 80 ms of 16 kHz PCM16
            val prev = ByteArray(2560)
            var prevN = 0
            var hang = 0
            var feeding = false
            var failures = 0
            var backoff = 500L

            while (isCurrent(gen)) {
                if (ar == null) {
                    ar = createRecord()
                    if (ar == null) {
                        failures++
                        if (failures > 6) { status.value = "Microphone unavailable (another app may be using it)"; break }
                        Thread.sleep(backoff); backoff = minOf(backoff * 2, 8000L)
                        continue
                    }
                    ar.startRecording()
                    if (ar.recordingState != AudioRecord.RECORDSTATE_RECORDING) { release(ar); ar = null; failures++; Thread.sleep(backoff); continue }
                    backoff = 500L; failures = 0
                    status.value = "Listening for “Hey Alic”"
                }
                val wantCall = CallMonitor.ringing && callPhrases.isNotEmpty()
                if (wantCall != callMode) {
                    callMode = wantCall
                    try { rec.close() } catch (e: Throwable) {}
                    rec = Recognizer(m, RATE.toFloat(), grammar(callMode))
                    recognizer = rec
                    hang = 0; feeding = false
                    status.value = if (callMode) "Phone is ringing: listening for “Hey Alic, hang up / answer”" else "Listening for “Hey Alic”"
                }
                val cur = ar ?: continue
                val n = cur.read(buf, 0, buf.size)
                if (n <= 0) {                       // 0 or an error code: rebuild the recorder with back-off
                    release(ar); ar = null
                    failures++
                    if (failures > 6) { status.value = "Microphone read keeps failing"; break }
                    Thread.sleep(backoff); backoff = minOf(backoff * 2, 8000L)
                    continue
                }
                if (!isCurrent(gen)) break

                // simple VAD: only run recognition around real voice activity (threshold from sensitivity setting)
                val thr = when (cfg.wakeSensitivity) { "kam" -> 600; "zyada" -> 120; else -> 300 }
                var sum = 0.0
                var i = 0
                while (i + 1 < n) {
                    val v = ((buf[i + 1].toInt() shl 8) or (buf[i].toInt() and 0xFF)).toShort().toInt()
                    sum += v.toDouble() * v
                    i += 2
                }
                val rms = sqrt(sum / (n / 2))
                if (rms >= thr) hang = 10
                if (hang > 0) {
                    hang--
                    if (!feeding) { feeding = true; if (prevN > 0) rec.acceptWaveForm(prev, prevN) }
                    val isFinal = rec.acceptWaveForm(buf, n)
                    val text = try {
                        if (isFinal) JSONObject(rec.result).optString("text") else JSONObject(rec.partialResult).optString("partial")
                    } catch (e: Exception) { "" }
                    val cmd = if (callMode && text.isNotEmpty()) matchCall(text) else null
                    if (cmd != null) {
                        val now = SystemClock.elapsedRealtime()
                        if (now - lastCallCmdMs >= 2500) {
                            lastCallCmdMs = now
                            rec.reset(); hang = 0; feeding = false
                            main.post { CallMonitor.runVoiceCommand(applicationContext, cmd) }
                        }
                    } else if (text.isNotEmpty() && matchesWake(text) && (!callMode || isFinal)) {
                        // while ringing, a bare "Hey Alic" waits for the final result so it cannot pre-empt "Hey Alic, hang up"
                        val now = SystemClock.elapsedRealtime()
                        if (now - lastWakeMs >= WAKE_COOLDOWN_MS && now >= WakeGate.ignoreUntil && !sessionActiveFlag) {
                            lastWakeMs = now
                            rec.reset()
                            hang = 0; feeding = false
                            val s = cfg
                            main.post { WakeActions.onWake(applicationContext, s) }
                        }
                    }
                } else if (feeding) {
                    feeding = false
                    rec.reset()
                }
                System.arraycopy(buf, 0, prev, 0, n); prevN = n
            }
        } catch (e: Throwable) {
            status.value = "Wake listener stopped: ${e.javaClass.simpleName}: ${e.message}"
        } finally {
            release(ar)
            try { recognizer?.close() } catch (e: Throwable) {}
        }
    }

    private fun matchCall(raw: String): CallAction? {
        val tokens = CloseCommands.normalize(raw).split(' ').filter { it.isNotEmpty() }
        for ((phrase, action) in callPhrases) {
            val pt = CloseCommands.normalize(phrase).split(' ')
            if ((0..tokens.size - pt.size).any { st -> pt.indices.all { tokens[st + it] == pt[it] } }) return action
        }
        return null
    }

    private fun matchesWake(raw: String): Boolean {
        val tokens = CloseCommands.normalize(raw).split(' ').filter { it.isNotEmpty() }
        if (tokens.isEmpty() || tokens.all { it == "[unk]" || it == "unk" }) return false
        return phrases.any { p ->
            val pt = CloseCommands.normalize(p).split(' ')
            (0..tokens.size - pt.size).any { s -> pt.indices.all { tokens[s + it] == pt[it] } }
        }
    }
}
