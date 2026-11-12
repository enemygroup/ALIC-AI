package com.alic.ai.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

class GeminiException(message: String, val code: Int = 0) : IOException(message)

class GeminiClient(private val http: OkHttpClient) {
    private val json = "application/json; charset=utf-8".toMediaType()

    private fun url(cfg: ApiConfig, path: String) = cfg.baseUrl.trimEnd('/') + "/v1beta/" + path

    /** Converts chat history (+ optional image on the last user message) to Gemini "contents". */
    fun contentsOf(history: List<ChatMessage>, image: ImageData? = null): JSONArray {
        val contents = JSONArray()
        val h = history.filter { it.text.isNotBlank() }
        h.forEachIndexed { i, m ->
            val parts = JSONArray().put(JSONObject().put("text", m.text))
            if (image != null && i == h.lastIndex && m.isUser) {
                parts.put(JSONObject().put("inlineData", JSONObject().put("mimeType", image.mime).put("data", image.base64)))
            }
            contents.put(JSONObject().put("role", if (m.isUser) "user" else "model").put("parts", parts))
        }
        return contents
    }

    private fun buildBody(system: String, contents: JSONArray, tools: JSONArray?): String {
        val o = JSONObject()
            .put("contents", contents)
            .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
        if (tools != null && tools.length() > 0) {
            o.put("tools", JSONArray().put(JSONObject().put("functionDeclarations", tools)))
        }
        return o.toString()
    }

    private fun parseError(body: String?): String {
        if (body.isNullOrBlank()) return "Empty error response"
        return try {
            JSONObject(body).optJSONObject("error")?.optString("message")?.takeIf { it.isNotBlank() } ?: body.take(300)
        } catch (e: Exception) {
            body.take(300)
        }
    }

    /** Returns the raw response parts (text and functionCall), skipping hidden "thought" summaries. */
    private fun extractParts(data: String): List<JSONObject> {
        val o = JSONObject(data)
        val cands = o.optJSONArray("candidates")
        if (cands == null || cands.length() == 0) {
            val block = o.optJSONObject("promptFeedback")?.optString("blockReason").orEmpty()
            if (block.isNotEmpty()) throw GeminiException("Request blocked by safety filters: $block")
            return emptyList()
        }
        val parts = cands.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts") ?: return emptyList()
        val out = mutableListOf<JSONObject>()
        for (i in 0 until parts.length()) {
            val p = parts.optJSONObject(i) ?: continue
            if (p.optBoolean("thought") && !p.has("functionCall")) continue
            out.add(p)
        }
        return out
    }

    /** Streams raw parts. Retries transient failures before any output is produced. */
    fun streamParts(cfg: ApiConfig, system: String, contents: JSONArray, tools: JSONArray? = null): Flow<JSONObject> = flow {
        if (cfg.apiKey.isBlank() && cfg.baseUrl.contains("googleapis.com")) {
            throw GeminiException("No Gemini API key. Open Settings > Gemini API configuration.")
        }
        val body = buildBody(system, contents, tools)
        var attempt = 0
        while (true) {
            attempt++
            var emitted = false
            val req = Request.Builder()
                .url(url(cfg, "models/${cfg.model.removePrefix("models/")}:streamGenerateContent?alt=sse"))
                .header("x-goog-api-key", cfg.apiKey)
                .post(body.toRequestBody(json))
                .build()
            val call = http.newCall(req)
            val handle = currentCoroutineContext().job.invokeOnCompletion { call.cancel() }
            try {
                call.execute().use { resp ->
                    if (!resp.isSuccessful) throw GeminiException(parseError(resp.body?.string()), resp.code)
                    val src = resp.body!!.source()
                    while (true) {
                        val line = src.readUtf8Line() ?: break
                        if (!line.startsWith("data:")) continue
                        val data = line.removePrefix("data:").trim()
                        if (data.isEmpty()) continue
                        for (p in extractParts(data)) {
                            emitted = true
                            emit(p)
                        }
                    }
                }
                return@flow
            } catch (e: GeminiException) {
                if (!emitted && e.code in listOf(429, 500, 502, 503, 504) && attempt < 3) delay(1500L * attempt) else throw e
            } catch (e: IOException) {
                if (!emitted && attempt < 3 && !call.isCanceled()) delay(1500L * attempt) else throw e
            } finally {
                handle.dispose()
            }
        }
    }.flowOn(Dispatchers.IO)

    /** Plain text streaming (used by Coding Studio and Vision). */
    fun stream(cfg: ApiConfig, system: String, history: List<ChatMessage>, image: ImageData? = null): Flow<String> =
        streamParts(cfg, system, contentsOf(history, image))
            .map { it.optString("text", "") }
            .filter { it.isNotEmpty() }

    /** Returns (textModels, liveModels) available to this key. */
    suspend fun listModels(cfg: ApiConfig): Pair<List<String>, List<String>> = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(url(cfg, "models?pageSize=200"))
            .header("x-goog-api-key", cfg.apiKey).get().build()
        http.newCall(req).execute().use { resp ->
            val txt = resp.body?.string()
            if (!resp.isSuccessful) throw GeminiException(parseError(txt), resp.code)
            val arr = JSONObject(txt ?: "{}").optJSONArray("models") ?: JSONArray()
            val text = mutableListOf<String>()
            val live = mutableListOf<String>()
            for (i in 0 until arr.length()) {
                val m = arr.getJSONObject(i)
                val name = m.optString("name").removePrefix("models/")
                val methods = m.optJSONArray("supportedGenerationMethods") ?: continue
                val set = (0 until methods.length()).map { methods.getString(it) }
                if ("generateContent" in set) text.add(name)
                if ("bidiGenerateContent" in set) live.add(name)
            }
            text to live
        }
    }

    /** Tiny request to verify key + model. Returns the model's short reply. */
    suspend fun test(cfg: ApiConfig): String = withContext(Dispatchers.IO) {
        val body = JSONObject().put(
            "contents", JSONArray().put(
                JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", "Reply with the single word: OK")))
            )
        ).toString()
        val req = Request.Builder()
            .url(url(cfg, "models/${cfg.model.removePrefix("models/")}:generateContent"))
            .header("x-goog-api-key", cfg.apiKey)
            .post(body.toRequestBody(json)).build()
        http.newCall(req).execute().use { resp ->
            val txt = resp.body?.string()
            if (!resp.isSuccessful) throw GeminiException(parseError(txt), resp.code)
            val o = JSONObject(txt ?: "{}")
            val parts = o.optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")
            (0 until (parts?.length() ?: 0)).joinToString("") { parts!!.getJSONObject(it).optString("text", "") }.ifBlank { "OK" }.trim()
        }
    }
}
