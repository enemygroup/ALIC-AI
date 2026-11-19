package com.alic.ai.wake

import android.content.Context
import android.os.SystemClock
import com.alic.ai.live.CloseCommands
import org.json.JSONArray
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer

/**
 * Lets speech reach Gemini Live only after "Hey Alic" (or its Hindi form). It reads the SAME microphone frames the
 * Live session already captures, so no second AudioRecord is created. Frames outside the open window are replaced
 * with silence. The window stays open for 20 seconds after the wake phrase.
 * If the speech model is missing, the gate lets audio through so Live still works (see README).
 */
class LiveWakeGate(private val ctx: Context) {
    private var rec: Recognizer? = null
    private var model: Model? = null
    private var phrases: List<List<String>> = emptyList()
    private var failed = false
    @Volatile private var openUntil = 0L
    private val windowMs = 20_000L

    private fun init(): Boolean {
        if (rec != null) return true
        if (failed) return false
        return try {
            val dir = VoskModelManager.ensureInstalled(ctx)
            val usable = VoskModelManager.usablePhrases(dir)
            if (usable.isEmpty()) { failed = true; return false }
            phrases = usable.map { CloseCommands.normalize(it).split(' ').filter { t -> t.isNotEmpty() } }
            val m = Model(dir.absolutePath)
            val grammar = JSONArray().also { a -> usable.forEach { a.put(it) }; a.put("[unk]") }.toString()
            model = m
            rec = Recognizer(m, 16000f, grammar)
            true
        } catch (e: Throwable) {
            failed = true
            false
        }
    }

    /** true = send this audio to Gemini; false = replace it with silence. Called on the Live audio thread. */
    fun feed(buf: ByteArray, n: Int): Boolean {
        if (!init()) return true
        val r = rec ?: return true
        val now = SystemClock.elapsedRealtime()
        if (now < openUntil) {
            r.acceptWaveForm(buf, n)
            return true
        }
        val isFinal = r.acceptWaveForm(buf, n)
        val text = try {
            val o = JSONObject(if (isFinal) r.result else r.partialResult)
            if (isFinal) o.optString("text") else o.optString("partial")
        } catch (e: Exception) { "" }
        if (text.isNotEmpty() && isWake(text)) {
            openUntil = now + windowMs
            r.reset()
            return true
        }
        return false
    }

    private fun isWake(raw: String): Boolean {
        val t = CloseCommands.normalize(raw).split(' ').filter { it.isNotEmpty() }
        return phrases.any { p ->
            p.isNotEmpty() && (0..t.size - p.size).any { s -> p.indices.all { t[s + it] == p[it] } }
        }
    }

    fun close() {
        try { rec?.close() } catch (e: Throwable) {}
        try { model?.close() } catch (e: Throwable) {}
        rec = null; model = null
    }
}
