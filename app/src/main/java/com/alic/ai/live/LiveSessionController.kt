package com.alic.ai.live

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.alic.ai.call.CallMonitor
import com.alic.ai.data.*
import com.alic.ai.device.AlicTools
import com.alic.ai.device.AppLauncher
import com.alic.ai.device.ToolExecutor
import com.alic.ai.service.SessionBus
import com.alic.ai.service.VoiceSessionService
import com.alic.ai.wake.WakeGate
import com.alic.ai.wake.WakeWordService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Process-wide owner of the Gemini Live session (microphone, speaker, websocket, transcript).
 * The app UI, the "Hey Alic" wake word, the assistant session and the transparent ASSIST activity all
 * start/stop the SAME session through this object, so only one Live AudioRecord can ever exist.
 *
 * Microphone ownership hand-off: start() first asks WakeWordService to release its AudioRecord (blocking,
 * synchronized) and only then opens Live audio; stop() releases Live audio first and then lets the wake
 * listener resume.
 */
object LiveSessionController {
    const val OWNER_APP = "app"
    const val OWNER_WAKE = "wake"
    const val OWNER_ASSIST = "assist"
    const val OWNER_SESSION = "session"

    enum class Phase { LISTENING, THINKING, SPEAKING }
    enum class StartResult { OK, ALREADY_RUNNING, NO_API_KEY, MIC_DENIED }

    // Compose-observable state (read by the app UI, the overlay and the assistant session popup)
    var active by mutableStateOf(false); private set
    var status by mutableStateOf(""); private set
    var connected by mutableStateOf(false); private set
    var muted by mutableStateOf(false); private set
    var log by mutableStateOf<List<ChatMessage>>(emptyList()); private set
    var level by mutableStateOf(0f); private set
    var phase by mutableStateOf(Phase.LISTENING); private set
    var endedTick by mutableStateOf(0); private set
    var lastError by mutableStateOf<String?>(null)

    @Volatile var owner: String? = null; private set
    @Volatile var settings: AppSettings = AppSettings(); private set

    val orb: OrbState
        get() = when {
            !active -> OrbState.IDLE
            phase == Phase.SPEAKING -> OrbState.SPEAKING
            phase == Phase.THINKING -> OrbState.THINKING
            else -> OrbState.LIVE
        }

    private var app: Application? = null
    private val lock = Any()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var live: GeminiLive
    private lateinit var tools: ToolExecutor
    private lateinit var history: HistoryRepository
    private lateinit var settingsStore: SettingsStore
    private val secure: SecureStore by lazy { SecureStore(app!!) }
    @Volatile private var settingsLoaded = false
    private var ticker: Job? = null
    private val userBuf = StringBuilder()
    private var userSpoke = false
    private var lastLoudMs = 0L

    fun init(application: Application) {
        if (app != null) return
        app = application
        val http = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(90, TimeUnit.SECONDS).build()
        live = GeminiLive(http)
        tools = ToolExecutor(application, AppLauncher(application))
        history = HistoryRepository(application)
        settingsStore = SettingsStore(application)
        scope.launch {
            settingsStore.flow.collect {
                settings = it; settingsLoaded = true
                live.allowBargeIn = it.voiceInterruption
            }
        }
        scope.launch { live.events.collect { onEvent(it) } }
        scope.launch { SessionBus.stop.collect { stop() } }
    }

    fun hasApiKey(): Boolean = app != null && secure.apiKey.isNotBlank()

    /** Starts the Live session. Must be called on the main thread. */
    fun start(ownerName: String): StartResult {
        val a = app ?: error("LiveSessionController.init() was not called")
        synchronized(lock) {
            if (active) return StartResult.ALREADY_RUNNING
            if (secure.apiKey.isBlank()) return StartResult.NO_API_KEY
            if (ContextCompat.checkSelfPermission(a, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                return StartResult.MIC_DENIED
            }
            if (!settingsLoaded) settings = runBlocking(Dispatchers.IO) { settingsStore.flow.first() }
            owner = ownerName
            active = true
            muted = false; live.muted = false
            log = emptyList(); connected = false; status = "Connecting…"; level = 0f
            phase = Phase.LISTENING
            userBuf.setLength(0); userSpoke = false; lastLoudMs = SystemClock.elapsedRealtime()
        }
        // 1) the wake listener must release its AudioRecord BEFORE Live opens its own
        WakeWordService.onSessionStarted()
        routeAudio(true)
        // WakeWordService (when running) already is a microphone foreground service
        if (WakeWordService.instance == null) VoiceSessionService.start(a)
        live.allowBargeIn = settings.voiceInterruption
        live.start(
            LiveConfig(
                secure.apiKey, settings.baseUrl, settings.liveModel, settings.liveVoice,
                Prompts.system(settings, Prompts.VOICE_HINT + CallMonitor.hint()), AlicTools.declarations()
            ) { name, args -> tools.run(name, args) }
        )
        ticker?.cancel()
        ticker = scope.launch {
            while (isActive) {
                delay(200)
                if (phase == Phase.LISTENING && userSpoke && SystemClock.elapsedRealtime() - lastLoudMs > 900) phase = Phase.THINKING
            }
        }
        return StartResult.OK
    }

    /** Stops the session. If [ifOwner] is given, only a session started by that owner is stopped. */
    fun stop(ifOwner: String? = null) {
        val a = app ?: return
        val finished: List<ChatMessage>
        synchronized(lock) {
            if (!active) return
            if (ifOwner != null && owner != ifOwner) return
            active = false
            owner = null
            finished = log
        }
        ticker?.cancel(); ticker = null
        live.stop(silent = true)            // releases Live AudioRecord / AudioTrack
        connected = false; level = 0f; phase = Phase.LISTENING
        routeAudio(false)
        VoiceSessionService.stop(a)
        log = emptyList()
        // 2) only now may the wake listener take the microphone back
        WakeWordService.onSessionEnded()
        if (finished.isNotEmpty()) {
            val conv = Conversation(title = "Live: " + finished.first().text.take(32), messages = finished)
            scope.launch {
                withContext(Dispatchers.IO) {
                    val cur = history.loadLocal("local")
                    history.saveLocal("local", (listOf(conv) + cur).sortedByDescending { it.createdAt })
                }
                endedTick++
            }
        } else endedTick++
    }

    fun toggleMute() { muted = !muted; live.muted = muted }

    private fun closeByVoice() {
        WakeGate.ignoreFor(4000)
        stop()
    }

    private fun onEvent(e: GeminiLive.Ev) {
        if (!active) return
        when (e) {
            is GeminiLive.Ev.Status -> { status = e.text; connected = e.connected }
            is GeminiLive.Ev.Level -> {
                level = e.v
                if (e.v > 0.06f) lastLoudMs = SystemClock.elapsedRealtime()
            }
            is GeminiLive.Ev.Speaking -> {
                phase = if (e.on) Phase.SPEAKING else Phase.LISTENING
                if (e.on || !e.on) { userBuf.setLength(0); userSpoke = false }
            }
            is GeminiLive.Ev.Transcript -> {
                if (e.user) {
                    userSpoke = true
                    userBuf.append(e.text)
                    if (CloseCommands.matches(userBuf.toString())) { closeByVoice(); return }
                } else {
                    userBuf.setLength(0); userSpoke = false
                }
                val role = if (e.user) "user" else "model"
                val last = log.lastOrNull()
                log = if (last != null && last.role == role) log.dropLast(1) + last.copy(text = last.text + e.text)
                else log + ChatMessage(role = role, text = e.text.trimStart())
            }
            is GeminiLive.Ev.Action -> {
                phase = Phase.THINKING
                log = log + ChatMessage(role = "tool", text = "⚙️ " + e.text)
            }
            is GeminiLive.Ev.Failed -> {
                lastError = "Live unavailable: " + e.reason.take(160)
                stop()
            }
        }
    }

    /**
     * Live audio plays on the voice-call stream through the loudspeaker, so the phone's volume buttons change
     * ALIC's voice volume while it talks (MainActivity sets volumeControlStream while a session is active).
     */
    private fun routeAudio(on: Boolean) {
        try {
            val am = app!!.getSystemService(AudioManager::class.java)
            if (on) {
                am.mode = AudioManager.MODE_IN_COMMUNICATION
                val external = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any {
                    it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET || it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                        it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO || it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                        it.type == AudioDeviceInfo.TYPE_USB_HEADSET
                }
                @Suppress("DEPRECATION")
                am.isSpeakerphoneOn = !external
                val max = am.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL)
                if (am.getStreamVolume(AudioManager.STREAM_VOICE_CALL) < (max * 0.7f).toInt()) {
                    am.setStreamVolume(AudioManager.STREAM_VOICE_CALL, (max * 0.7f).toInt(), 0)
                }
            } else {
                @Suppress("DEPRECATION")
                am.isSpeakerphoneOn = false
                am.mode = AudioManager.MODE_NORMAL
            }
        } catch (e: Exception) {
        }
    }
}
