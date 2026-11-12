package com.alic.ai.voice

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import com.alic.ai.data.AppSettings
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.Locale

/** Text-to-speech only. Voice input is handled exclusively by Gemini Live. */
class VoiceEngine(private val ctx: Context) {
    private val main = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    @Volatile private var ttsReady = false
    @Volatile private var pendingDone: (() -> Unit)? = null
    private var lastId = ""

    val ttsVoicesChanged = MutableStateFlow(0)

    fun init() {
        if (tts != null) return
        tts = TextToSpeech(ctx) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            ttsVoicesChanged.value = ttsVoicesChanged.value + 1
        }
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) { if (utteranceId == lastId) finish() }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) { finish() }
        })
    }

    private fun finish() {
        main.post {
            val d = pendingDone
            pendingDone = null
            d?.invoke()
        }
    }

    fun voices(): List<Voice> = try {
        tts?.voices?.toList().orEmpty().sortedWith(compareBy({ it.locale.toString() }, { it.name }))
    } catch (e: Exception) { emptyList() }

    /** Android has no gender API; only voices whose name says "male" count as male. */
    fun isMale(v: Voice): Boolean {
        val n = v.name.lowercase()
        return n.contains("male") && !n.contains("female")
    }

    fun maleVoices(): List<Voice> = voices().filter { isMale(it) }

    private fun clean(raw: String): String = raw
        .replace(Regex("```[\\s\\S]*?```"), " code block omitted. ")
        .replace(Regex("https?://\\S+"), " link ")
        .replace(Regex("[*#`_>|~]"), "")
        .replace(Regex("\\s+"), " ").trim()

    fun speak(raw: String, s: AppSettings, onDone: () -> Unit) {
        val t = tts
        val text = clean(raw)
        if (t == null || !ttsReady || text.isBlank()) { onDone(); return }
        val deva = text.any { it in '\u0900'..'\u097F' }
        val lang = when (s.language) {
            "hi" -> Locale("hi", "IN")
            "en" -> Locale("en", "IN")
            else -> if (deva) Locale("hi", "IN") else Locale("en", "IN")
        }
        val all = voices()
        val chosen = all.firstOrNull { it.name == s.ttsVoice }
            ?: if (s.deepVoice) all.firstOrNull { isMale(it) && it.locale.language == lang.language } else null
        if (chosen != null && chosen.locale.language == lang.language) {
            t.voice = chosen
        } else {
            val r = t.setLanguage(lang)
            if (r < TextToSpeech.LANG_AVAILABLE) t.setLanguage(if (deva) Locale("hi") else Locale.US)
        }
        t.setPitch(s.pitch.coerceIn(0.5f, 2f))
        t.setSpeechRate(s.speed.coerceIn(0.5f, 2f))
        val chunks = chunk(text)
        pendingDone = onDone
        t.stop()
        chunks.forEachIndexed { i, c ->
            val id = "alic_${System.nanoTime()}_$i"
            if (i == chunks.lastIndex) lastId = id
            t.speak(c, if (i == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, null, id)
        }
    }

    private fun chunk(text: String): List<String> {
        val out = mutableListOf<String>()
        val sb = StringBuilder()
        for (sentence in text.split(Regex("(?<=[.!?।])\\s+"))) {
            if (sb.length + sentence.length > 3000 && sb.isNotEmpty()) { out.add(sb.toString()); sb.clear() }
            if (sentence.length > 3000) sentence.chunked(3000).forEach { out.add(it) } else sb.append(sentence).append(' ')
        }
        if (sb.isNotBlank()) out.add(sb.toString().trim())
        return out.ifEmpty { listOf(text.take(3000)) }
    }

    fun stopSpeaking() {
        pendingDone = null
        try { tts?.stop() } catch (e: Exception) {}
    }

    fun shutdown() {
        stopSpeaking()
        try { tts?.shutdown() } catch (e: Exception) {}
        tts = null
        ttsReady = false
    }
}
