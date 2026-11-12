package com.alic.ai.ui

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.ConnectivityManager
import android.net.Network
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Base64
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.alic.ai.data.*
import com.alic.ai.device.AppInfo
import com.alic.ai.device.AlicTools
import com.alic.ai.device.AppLauncher
import com.alic.ai.device.ToolExecutor
import com.alic.ai.device.Command
import com.alic.ai.device.CommandRouter
import com.alic.ai.live.LiveSessionController
import com.alic.ai.wake.WakeWordService
import com.alic.ai.service.VoiceSessionService
import com.alic.ai.voice.VoiceEngine
import androidx.compose.runtime.snapshotFlow
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import okhttp3.OkHttpClient
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.text.DateFormat
import java.util.Date
import java.util.UUID
import java.util.concurrent.TimeUnit

class AlicViewModel(app: Application) : AndroidViewModel(app) {
    private val ctx get() = getApplication<Application>()
    private val settingsStore = SettingsStore(ctx)
    private val secure = SecureStore(ctx)
    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS).readTimeout(90, TimeUnit.SECONDS).build()
    private val gemini = GeminiClient(http)
    val voice = VoiceEngine(ctx)
    val launcher = AppLauncher(ctx)
    private val tools = ToolExecutor(ctx, launcher)
    private val history = HistoryRepository(ctx)
    private val owner = "local"

    // ---------- global state ----------
    var settings by mutableStateOf(AppSettings()); private set
    var settingsLoaded by mutableStateOf(false); private set
    var hasKey by mutableStateOf(secure.apiKey.isNotBlank()); private set
    val secureStorageAvailable get() = secure.secureStorageAvailable
    private var chatOrb by mutableStateOf(OrbState.IDLE)
    private var chatLevel by mutableStateOf(0f)
    /** While a Live session runs (started by the app, the wake word or the assistant) the orb follows it. */
    val orb: OrbState get() = if (LiveSessionController.active) LiveSessionController.orb else chatOrb
    val level: Float get() = if (LiveSessionController.active) LiveSessionController.level else chatLevel
    var online by mutableStateOf(true); private set
    var toast by mutableStateOf<String?>(null)
    var pendingCommand by mutableStateOf<Command?>(null); private set

    // ---------- chat ----------
    var messages by mutableStateOf<List<ChatMessage>>(emptyList()); private set
    var conversations by mutableStateOf<List<Conversation>>(emptyList()); private set
    var busy by mutableStateOf(false); private set
    private var convId = UUID.randomUUID().toString()
    private var convCreated = System.currentTimeMillis()
    private var job: Job? = null

    // ---------- live ----------
    val liveActive: Boolean get() = LiveSessionController.active
    val liveStatus: String get() = LiveSessionController.status
    val liveConnected: Boolean get() = LiveSessionController.connected
    val liveMuted: Boolean get() = LiveSessionController.muted
    val liveLog: List<ChatMessage> get() = LiveSessionController.log
    var liveRequested by mutableStateOf(false)
    fun requestLive() { liveRequested = true }

    // ---------- coding & vision ----------
    var coding by mutableStateOf<List<ChatMessage>>(emptyList()); private set
    var codingBusy by mutableStateOf(false); private set
    private var codingId = UUID.randomUUID().toString()
    private var codingJob: Job? = null
    var visionBitmap by mutableStateOf<Bitmap?>(null); private set
    private var visionImage: ImageData? = null
    var visionAnswer by mutableStateOf(""); private set
    var visionBusy by mutableStateOf(false); private set

    // ---------- AI config ----------
    var testResult by mutableStateOf<String?>(null); private set
    var testBusy by mutableStateOf(false); private set
    var textModels by mutableStateOf<List<String>>(emptyList()); private set
    var liveModels by mutableStateOf<List<String>>(emptyList()); private set

    private val cm = ctx.getSystemService(ConnectivityManager::class.java)
    private val netCb = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) { online = true }
        override fun onLost(network: Network) { online = false }
    }

    init {
        voice.init()
        online = cm.activeNetwork != null
        try { cm.registerDefaultNetworkCallback(netCb, Handler(Looper.getMainLooper())) } catch (e: Exception) {}
        viewModelScope.launch { settingsStore.flow.collect { settings = it; ThemeState.apply(it.themeMode, it.accent); settingsLoaded = true } }
        viewModelScope.launch { snapshotFlow { LiveSessionController.lastError }.collect { e -> if (e != null) { toast = e; LiveSessionController.lastError = null } } }
        viewModelScope.launch {
            // a Live session saved its transcript to disk (possibly started by the wake word): refresh the history list
            snapshotFlow { LiveSessionController.endedTick }.drop(1).collect {
                val list = withContext(Dispatchers.IO) { history.loadLocal(owner) }
                conversations = list.sortedByDescending { it.createdAt }
            }
        }
        viewModelScope.launch {
            val list = withContext(Dispatchers.IO) { history.loadLocal(owner) }
            conversations = list.sortedByDescending { it.createdAt }
        }
    }

    override fun onCleared() {
        try { cm.unregisterNetworkCallback(netCb) } catch (e: Exception) {}
        LiveSessionController.stop(ifOwner = LiveSessionController.OWNER_APP)
        voice.shutdown()
    }

    // ---------- prompts ----------
    private fun systemPrompt(extra: String = ""): String = Prompts.system(settings, extra)

    private val voiceHint = "\nYour reply will be spoken aloud: use plain conversational sentences, no Markdown, no lists, and keep it brief."

    private fun cfg() = ApiConfig(secure.apiKey, settings.model, settings.baseUrl)

    private fun friendly(e: Throwable): String = when {
        e is UnknownHostException -> "No internet connection. Check your network and try again."
        e is SocketTimeoutException -> "The request timed out. Please try again."
        e is GeminiException && e.code == 400 && e.message.orEmpty().contains("API key", true) -> "Gemini rejected the API key. Check it in Settings > Gemini API configuration."
        e is GeminiException && (e.code == 401 || e.code == 403) -> "Access denied (${e.code}): ${e.message}"
        e is GeminiException && e.code == 404 -> "Model not found. Pick another model in Gemini API configuration. (${e.message})"
        e is GeminiException && e.code == 429 -> "Rate limit or quota reached. Wait a moment and retry."
        else -> e.message ?: "Something went wrong."
    }

    // ---------- chat ----------
    private fun replaceLast(text: String) {
        val l = messages
        if (l.isEmpty()) return
        messages = l.dropLast(1) + l.last().copy(text = text)
    }

    fun send(text: String) {
        val t = text.trim()
        if (t.isEmpty() || busy) return
        messages = messages + ChatMessage(role = "user", text = t)
        val cmd = CommandRouter.parse(t)
        if (cmd != null) {
            if (settings.confirmActions) pendingCommand = cmd else runCommand(cmd)
            return
        }
        ask()
    }

    fun confirmCommand(ok: Boolean) {
        val c = pendingCommand ?: return
        pendingCommand = null
        if (ok) runCommand(c) else { messages = messages + ChatMessage(role = "model", text = "Cancelled."); persist() }
    }

    private fun runCommand(c: Command) {
        val r = launcher.execute(c)
        messages = messages + ChatMessage(role = "model", text = r)
        persist()
        chatOrb = OrbState.IDLE
    }

    private fun ask() {
        job?.cancel()
        busy = true; chatOrb = OrbState.THINKING
        val hist = messages
        messages = messages + ChatMessage(role = "model", text = "")
        job = viewModelScope.launch {
            val sb = StringBuilder()
            try {
                val contents = gemini.contentsOf(hist)
                var rounds = 0
                while (true) {
                    val modelParts = JSONArray()
                    val calls = mutableListOf<Pair<String, JSONObject>>()
                    gemini.streamParts(cfg(), systemPrompt(), contents, AlicTools.declarations()).collect { part ->
                        modelParts.put(part)
                        val fc = part.optJSONObject("functionCall")
                        if (fc != null) {
                            calls.add(fc.optString("name") to (fc.optJSONObject("args") ?: JSONObject()))
                        } else {
                            val t = part.optString("text", "")
                            if (t.isNotEmpty()) { sb.append(t); replaceLast(sb.toString()) }
                        }
                    }
                    rounds++
                    if (calls.isEmpty() || rounds > 5) break
                    contents.put(JSONObject().put("role", "model").put("parts", modelParts))
                    val responses = JSONArray()
                    for ((name, args) in calls) {
                        val result = tools.run(name, args)
                        sb.append("⚙️ ").append(result).append("\n\n")
                        replaceLast(sb.toString())
                        responses.put(
                            JSONObject().put("functionResponse", JSONObject().put("name", name).put("response", JSONObject().put("output", result)))
                        )
                    }
                    contents.put(JSONObject().put("role", "user").put("parts", responses))
                }
                if (sb.isEmpty()) replaceLast("I couldn't generate a response. Please try again.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val msg = friendly(e)
                replaceLast((if (sb.isEmpty()) "" else sb.toString() + "\n\n") + "⚠️ $msg")
                toast = msg
            }
            busy = false
            persist()
            chatOrb = OrbState.IDLE
        }
    }

    fun regenerate() {
        if (busy) return
        val l = messages
        val lastUser = l.indexOfLast { it.isUser }
        if (lastUser < 0) return
        messages = l.take(lastUser + 1)
        ask()
    }

    fun speakMessage(text: String) {
        stopAll()
        chatOrb = OrbState.SPEAKING
        voice.speak(text, settings) { chatOrb = OrbState.IDLE }
    }

    /** Stops speaking and any in-flight generation. */
    fun stopAll() {
        job?.cancel(); busy = false
        voice.stopSpeaking()
        messages = messages.filterNot { !it.isUser && it.text.isBlank() }
        chatOrb = OrbState.IDLE
        chatLevel = 0f
    }

    fun newConversation() {
        stopAll()
        convId = UUID.randomUUID().toString(); convCreated = System.currentTimeMillis()
        messages = emptyList()
    }

    fun openConversation(id: String) {
        val c = conversations.firstOrNull { it.id == id } ?: return
        stopAll()
        convId = c.id; convCreated = c.createdAt; messages = c.messages
    }

    fun clearChat() { deleteConversation(convId); newConversation() }

    private fun persist() {
        val clean = messages.filter { it.text.isNotBlank() }
        if (clean.isEmpty()) return
        val title = clean.firstOrNull { it.isUser }?.text?.take(40)?.replace('\n', ' ') ?: "Chat"
        saveConversation(Conversation(convId, title, convCreated, clean))
    }

    private fun saveConversation(c: Conversation) {
        conversations = (listOf(c) + conversations.filter { it.id != c.id }).sortedByDescending { it.createdAt }
        val snap = conversations
        viewModelScope.launch { withContext(Dispatchers.IO) { history.saveLocal(owner, snap) } }
    }

    fun deleteConversation(id: String) {
        conversations = conversations.filter { it.id != id }
        val snap = conversations
        viewModelScope.launch { withContext(Dispatchers.IO) { history.saveLocal(owner, snap) } }
        if (id == convId) messages = emptyList()
    }

    fun clearAllHistory() {
        conversations = emptyList(); messages = emptyList()
        viewModelScope.launch {
            withContext(Dispatchers.IO) { history.saveLocal(owner, emptyList()) }
            toast = "History cleared."
        }
    }

    fun deleteAllMyData() {
        clearAllHistory()
        updateSettings { it.copy(memoryNotes = emptyList()) }
        toast = "Your data was deleted."
    }

    fun exportText(c: Conversation): String {
        val df = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
        return buildString {
            append("ALIC AI — ").append(c.title).append("\n\n")
            c.messages.forEach {
                append(if (it.isUser) "You" else "ALIC AI").append(" [").append(df.format(Date(it.ts))).append("]\n")
                append(it.text).append("\n\n")
            }
        }
    }

    fun exportCurrent(): String? =
        if (messages.none { it.text.isNotBlank() }) null
        else exportText(Conversation(convId, messages.firstOrNull { it.isUser }?.text?.take(40) ?: "Chat", convCreated, messages))

    fun exportAll(): String? = if (conversations.isEmpty()) null else conversations.joinToString("\n----------\n\n") { exportText(it) }

    // ---------- live: state and logic live in LiveSessionController so the wake word / assistant can start it too ----------
    fun startLive() {
        when (LiveSessionController.start(LiveSessionController.OWNER_APP)) {
            LiveSessionController.StartResult.NO_API_KEY -> toast = "Add your Gemini API key first (Settings > Gemini API configuration)."
            LiveSessionController.StartResult.MIC_DENIED -> toast = "Microphone permission is required for Live conversation."
            else -> {}
        }
    }

    fun toggleMute() = LiveSessionController.toggleMute()
    fun endLive() = LiveSessionController.stop()

    // ---------- wake word lifecycle (called from MainActivity onStart/onStop; the app is visible, so Android allows the mic service) ----------
    fun onAppStart(c: Context) {
        viewModelScope.launch {
            val st = settingsStore.flow.first()
            val mic = ContextCompat.checkSelfPermission(c, android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (st.wakeEnabled && mic) WakeWordService.start(c)
        }
    }

    fun onAppStop(c: Context) {
        viewModelScope.launch {
            val st = settingsStore.flow.first()
            if (st.wakeEnabled && !st.wakeAlwaysOn) WakeWordService.stop(c)
        }
    }

    // ---------- apps ----------
    fun installedApps(): List<AppInfo> = launcher.installedApps()

    fun launchApp(a: AppInfo) {
        if (!launcher.launchPackage(a.pkg)) toast = "Couldn't open ${a.label}."
    }

    fun youtube(query: String, music: Boolean) { toast = launcher.youtube(query, music) }
    fun runDirect(c: Command) { toast = launcher.execute(c) }

    // ---------- coding studio ----------
    fun codingSend(mode: String, lang: String, instruction: String, code: String) {
        if (codingBusy) return
        if (instruction.isBlank() && code.isBlank()) { toast = "Enter an instruction or some code first."; return }
        val text = buildString {
            append("[Mode: ").append(mode).append("] [Language: ").append(lang).append("]\n")
            if (instruction.isNotBlank()) append(instruction.trim()).append("\n")
            if (code.isNotBlank()) append("\n```\n").append(code.trim()).append("\n```\n")
        }
        coding = coding + ChatMessage(role = "user", text = text)
        runCoding()
    }

    fun codingRegenerate() {
        if (codingBusy) return
        val i = coding.indexOfLast { it.isUser }
        if (i < 0) return
        coding = coding.take(i + 1)
        runCoding()
    }

    private fun runCoding() {
        val hist = coding
        coding = coding + ChatMessage(role = "model", text = "")
        codingBusy = true
        codingJob?.cancel()
        codingJob = viewModelScope.launch {
            val sb = StringBuilder()
            try {
                gemini.stream(
                    cfg(),
                    systemPrompt("\nYou are in ALIC Coding Studio. Give complete, correct, runnable code in fenced blocks with the language tag, then a brief explanation. When debugging, find the root cause first. You never execute code; say so if asked to run it."),
                    hist
                ).collect { sb.append(it); coding = coding.dropLast(1) + coding.last().copy(text = sb.toString()) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val m = friendly(e); toast = m
                coding = coding.dropLast(1) + coding.last().copy(text = (if (sb.isEmpty()) "" else sb.toString() + "\n\n") + "⚠️ $m")
            }
            codingBusy = false
            val clean = coding.filter { it.text.isNotBlank() }
            if (clean.isNotEmpty()) saveConversation(Conversation(codingId, "Code: " + clean.first().text.lines().getOrElse(1) { "session" }.take(32), System.currentTimeMillis(), clean))
        }
    }

    fun codingClear() { codingJob?.cancel(); codingBusy = false; coding = emptyList(); codingId = UUID.randomUUID().toString() }

    // ---------- vision ----------
    fun loadImage(uri: Uri) {
        viewModelScope.launch {
            try {
                val (bmp, data) = withContext(Dispatchers.IO) { decode(uri) }
                visionBitmap = bmp; visionImage = data; visionAnswer = ""
            } catch (e: Exception) {
                toast = "Couldn't load the image: ${e.localizedMessage}"
            }
        }
    }

    private fun decode(uri: Uri): Pair<Bitmap, ImageData> {
        val cr = ctx.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 1600) sample *= 2
        var bmp = cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
            ?: throw IllegalStateException("unsupported image")
        val orient = try {
            cr.openInputStream(uri)?.use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
        } catch (e: Exception) { null } ?: ExifInterface.ORIENTATION_NORMAL
        val deg = when (orient) { ExifInterface.ORIENTATION_ROTATE_90 -> 90f; ExifInterface.ORIENTATION_ROTATE_180 -> 180f; ExifInterface.ORIENTATION_ROTATE_270 -> 270f; else -> 0f }
        if (deg != 0f) bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(deg) }, true)
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 85, out)
        return bmp to ImageData("image/jpeg", Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP))
    }

    fun askVision(question: String) {
        val img = visionImage ?: run { toast = "Choose or capture an image first."; return }
        if (visionBusy) return
        val q = question.ifBlank { "Describe this image in detail." }
        visionBusy = true; visionAnswer = ""
        viewModelScope.launch {
            val sb = StringBuilder()
            try {
                gemini.stream(
                    cfg(), systemPrompt("\nYou are analysing an image supplied by the user. Be accurate; say when something is unclear."),
                    listOf(ChatMessage(role = "user", text = q)), img
                ).collect { sb.append(it); visionAnswer = sb.toString() }
                if (sb.isNotEmpty()) saveConversation(
                    Conversation(title = "Vision: " + q.take(32), messages = listOf(ChatMessage(role = "user", text = "[Image] $q"), ChatMessage(role = "model", text = sb.toString())))
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val m = friendly(e); toast = m; visionAnswer = "⚠️ $m"
            }
            visionBusy = false
        }
    }

    fun clearVision() { visionBitmap = null; visionImage = null; visionAnswer = "" }

    // ---------- settings / API ----------
    fun updateSettings(block: (AppSettings) -> AppSettings) {
        val n = block(settings)
        settings = n
        ThemeState.apply(n.themeMode, n.accent)
        viewModelScope.launch { settingsStore.update { n } }
    }

    fun currentKeyHint(): String { val k = secure.apiKey; return if (k.length > 8) k.take(4) + "••••" + k.takeLast(4) else if (k.isNotEmpty()) "••••" else "" }

    fun saveApiKey(k: String) { secure.apiKey = k; hasKey = secure.apiKey.isNotBlank(); toast = if (hasKey) "API key saved securely." else "API key removed." }

    fun testApi() {
        if (testBusy) return
        testBusy = true; testResult = null
        viewModelScope.launch {
            testResult = try { "✅ Connected. Model replied: " + gemini.test(cfg()) } catch (e: Exception) { "❌ " + friendly(e) }
            testBusy = false
        }
    }

    fun fetchModels() {
        viewModelScope.launch {
            try {
                val (t, l) = gemini.listModels(cfg())
                textModels = t.sorted(); liveModels = l.sorted()
                if (t.isEmpty() && l.isEmpty()) toast = "No models returned for this key."
            } catch (e: Exception) { toast = friendly(e) }
        }
    }
}
