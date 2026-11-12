package com.alic.ai.data

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.os.SystemClock
import android.util.Base64
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

data class LiveConfig(
    val apiKey: String, val baseUrl: String, val model: String, val voice: String, val system: String,
    val tools: JSONArray = JSONArray(),
    val onTool: suspend (String, JSONObject) -> String = { _, _ -> "Tools are not available." }
)

/**
 * Real-time voice session over the official Gemini Live API WebSocket protocol (BidiGenerateContent).
 * Streams 16 kHz PCM microphone audio up, plays 24 kHz PCM audio down, supports interruption,
 * transcripts, reconnection and automatic setup-format negotiation.
 */
class GeminiLive(baseHttp: OkHttpClient) {
    sealed interface Ev {
        data class Status(val text: String, val connected: Boolean) : Ev
        data class Transcript(val user: Boolean, val text: String) : Ev
        data class Level(val v: Float) : Ev
        data class Speaking(val on: Boolean) : Ev
        data class Action(val text: String) : Ev
        data class Failed(val reason: String) : Ev
    }

    val events = MutableSharedFlow<Ev>(extraBufferCapacity = 256)
    @Volatile var muted = false
    /** true = mic audio is sent while ALIC speaks (user can interrupt). false = silence frames are sent (echo protection). */
    @Volatile var allowBargeIn = true
    @Volatile private var playEndMs = 0L

    private val http = baseHttp.newBuilder().pingInterval(20, TimeUnit.SECONDS).readTimeout(0, TimeUnit.MILLISECONDS).build()

    @Volatile private var running = false
    @Volatile private var ready = false
    private var scope: CoroutineScope? = null
    private var ws: WebSocket? = null
    private var record: AudioRecord? = null
    private var track: AudioTrack? = null
    private var audioJob: Job? = null
    private var playJob: Job? = null
    private var playCh = Channel<ByteArray>(Channel.UNLIMITED)
    private var attempt = 0
    private var goodLayout = -1
    private var currentLayout = 0
    private var cfgRef: LiveConfig? = null

    fun start(cfg: LiveConfig) {
        stop(silent = true)
        cfgRef = cfg
        running = true
        attempt = 0
        val s = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope = s
        s.launch {
            while (running) {
                val closed = CompletableDeferred<String?>()
                currentLayout = if (goodLayout >= 0) goodLayout else attempt % 3
                events.tryEmit(Ev.Status("Connecting to Gemini Live…", false))
                connect(cfg, currentLayout, closed)
                val reason = closed.await()
                ready = false
                stopAudio()
                try { ws?.cancel() } catch (e: Exception) {}
                ws = null
                if (!running) break
                attempt++
                if (attempt >= 4) {
                    running = false
                    events.tryEmit(Ev.Failed(reason ?: "Connection closed"))
                    break
                }
                events.tryEmit(Ev.Status("Reconnecting ($attempt/3)…", false))
                delay(1200)
            }
        }
    }

    fun stop(silent: Boolean = false) {
        running = false
        ready = false
        try { ws?.close(1000, "bye") } catch (e: Exception) {}
        ws = null
        stopAudio()
        scope?.cancel()
        scope = null
        if (!silent) events.tryEmit(Ev.Status("Conversation ended", false))
    }

    private fun setupJson(cfg: LiveConfig, layout: Int): String {
        val sys = JSONObject().put("parts", JSONArray().put(JSONObject().put("text", cfg.system)))
        val speech = JSONObject().put(
            "voiceConfig",
            JSONObject().put("prebuiltVoiceConfig", JSONObject().put("voiceName", cfg.voice))
        )
        val audio = JSONArray().put("AUDIO")
        val s = JSONObject()
            .put("model", "models/" + cfg.model.removePrefix("models/"))
            .put("systemInstruction", sys)
        when (layout) {
            0 -> {
                s.put("generationConfig", JSONObject().put("responseModalities", audio).put("speechConfig", speech))
                s.put("inputAudioTranscription", JSONObject())
                s.put("outputAudioTranscription", JSONObject())
            }
            1 -> {
                s.put("responseModalities", audio).put("speechConfig", speech)
                s.put("inputAudioTranscription", JSONObject())
                s.put("outputAudioTranscription", JSONObject())
            }
            else -> s.put("generationConfig", JSONObject().put("responseModalities", audio))
        }
        if (cfg.tools.length() > 0) s.put("tools", JSONArray().put(JSONObject().put("functionDeclarations", cfg.tools)))
        return JSONObject().put("setup", s).toString()
    }

    private fun connect(cfg: LiveConfig, layout: Int, closed: CompletableDeferred<String?>) {
        val base = cfg.baseUrl.trimEnd('/').replaceFirst("http", "ws")
        val url = base + "/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent" +
            "?key=" + URLEncoder.encode(cfg.apiKey, "UTF-8")
        val req = okhttp3.Request.Builder().url(url.replaceFirst("ws://", "http://").replaceFirst("wss://", "https://")).build()
        ws = http.newWebSocket(req, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocket.send(setupJson(cfg, layout))
            }
            override fun onMessage(webSocket: WebSocket, text: String) = handle(text)
            override fun onMessage(webSocket: WebSocket, bytes: ByteString) = handle(bytes.utf8())
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                closed.complete(if (code == 1000) null else "Closed $code: $reason")
            }
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
                closed.complete(if (code == 1000) null else "Closed $code: $reason")
            }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                closed.complete("${t.message ?: "Network error"}${response?.let { " (HTTP ${it.code})" } ?: ""}")
            }
        })
    }

    private fun handle(raw: String) {
        try {
            val o = JSONObject(raw)
            if (o.has("setupComplete")) {
                ready = true
                goodLayout = currentLayout
                attempt = 0
                startAudio()
                events.tryEmit(Ev.Status("Live — listening", true))
                return
            }
            val tc = o.optJSONObject("toolCall")
            if (tc != null) {
                val calls = tc.optJSONArray("functionCalls") ?: return
                for (i in 0 until calls.length()) {
                    val c = calls.getJSONObject(i)
                    val id = c.optString("id")
                    val fname = c.optString("name")
                    val args = c.optJSONObject("args") ?: JSONObject()
                    scope?.launch {
                        val res = try { cfgRef?.onTool?.invoke(fname, args) ?: "Tools unavailable." } catch (e: Exception) { "Failed: ${e.message}" }
                        events.tryEmit(Ev.Action(res))
                        val fr = JSONObject().put("id", id).put("name", fname).put("response", JSONObject().put("output", res))
                        ws?.send(JSONObject().put("toolResponse", JSONObject().put("functionResponses", JSONArray().put(fr))).toString())
                    }
                }
                return
            }
            val sc = o.optJSONObject("serverContent") ?: return
            if (sc.optBoolean("interrupted")) {
                flushPlayback()
                events.tryEmit(Ev.Speaking(false))
            }
            val parts = sc.optJSONObject("modelTurn")?.optJSONArray("parts")
            if (parts != null) {
                for (i in 0 until parts.length()) {
                    val data = parts.optJSONObject(i)?.optJSONObject("inlineData")?.optString("data").orEmpty()
                    if (data.isNotEmpty()) {
                        events.tryEmit(Ev.Speaking(true))
                        val pcm = Base64.decode(data, Base64.DEFAULT)
                        // 24 kHz, 16-bit mono -> ms of audio queued; used for echo protection when barge-in is OFF
                        val ms = pcm.size * 1000L / (24000 * 2)
                        playEndMs = maxOf(playEndMs, SystemClock.elapsedRealtime()) + ms
                        playCh.trySend(pcm)
                    }
                }
            }
            sc.optJSONObject("inputTranscription")?.optString("text")?.takeIf { it.isNotBlank() }
                ?.let { events.tryEmit(Ev.Transcript(true, it)) }
            sc.optJSONObject("outputTranscription")?.optString("text")?.takeIf { it.isNotBlank() }
                ?.let { events.tryEmit(Ev.Transcript(false, it)) }
            if (sc.optBoolean("turnComplete")) events.tryEmit(Ev.Speaking(false))
        } catch (e: Exception) {
        }
    }

    @SuppressLint("MissingPermission")
    private fun startAudio() {
        if (audioJob?.isActive == true) return
        val s = scope ?: return
        // Playback
        val outMin = AudioTrack.getMinBufferSize(24000, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val t = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
            )
            .setAudioFormat(
                AudioFormat.Builder().setSampleRate(24000).setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT).build()
            )
            .setBufferSizeInBytes(max(outMin, 48000))
            .setTransferMode(AudioTrack.MODE_STREAM).build()
        t.play()
        track = t
        playCh = Channel(Channel.UNLIMITED)
        val ch = playCh
        playJob = s.launch { for (b in ch) { t.write(b, 0, b.size) } }

        // Capture
        val inMin = AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val rec = AudioRecord(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION, 16000,
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, max(inMin, 6400)
        )
        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            rec.release()
            events.tryEmit(Ev.Failed("Microphone could not be started. Check microphone permission."))
            running = false
            return
        }
        try {
            if (AcousticEchoCanceler.isAvailable()) AcousticEchoCanceler.create(rec.audioSessionId)?.enabled = true
        } catch (e: Exception) {}
        record = rec
        rec.startRecording()
        audioJob = s.launch {
            val buf = ByteArray(3200)
            while (isActive && running) {
                val n = rec.read(buf, 0, buf.size)
                if (n <= 0) continue
                if (muted || !ready) { events.tryEmit(Ev.Level(0f)); continue }
                // Voice Interruption OFF: while queued speech is still playing, send silence instead of the mic signal.
                if (!allowBargeIn && SystemClock.elapsedRealtime() < playEndMs) java.util.Arrays.fill(buf, 0, n, 0.toByte())
                var sum = 0.0
                var i = 0
                while (i + 1 < n) {
                    val v = ((buf[i + 1].toInt() shl 8) or (buf[i].toInt() and 0xFF)).toShort().toInt()
                    sum += v.toDouble() * v
                    i += 2
                }
                events.tryEmit(Ev.Level(min(1f, (sqrt(sum / (n / 2)) / 6000.0).toFloat())))
                val msg = JSONObject().put(
                    "realtimeInput", JSONObject().put(
                        "audio", JSONObject()
                            .put("data", Base64.encodeToString(buf, 0, n, Base64.NO_WRAP))
                            .put("mimeType", "audio/pcm;rate=16000")
                    )
                )
                ws?.send(msg.toString())
            }
        }
    }

    private fun flushPlayback() {
        playEndMs = 0L
        while (playCh.tryReceive().isSuccess) { /* drop queued audio */ }
        try {
            track?.pause(); track?.flush(); track?.play()
        } catch (e: Exception) {}
    }

    private fun stopAudio() {
        audioJob?.cancel(); audioJob = null
        playJob?.cancel(); playJob = null
        try { record?.stop() } catch (e: Exception) {}
        try { record?.release() } catch (e: Exception) {}
        record = null
        try { track?.stop() } catch (e: Exception) {}
        try { track?.release() } catch (e: Exception) {}
        track = null
    }
}
