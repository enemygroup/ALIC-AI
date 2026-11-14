package com.alic.ai.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.job
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

class ApiException(message: String, val code: Int = 0) : IOException(message)

/** ChatGPT (OpenAI Chat Completions) with server-sent-event streaming. The key is passed in, never stored here. */
class OpenAIClient(private val http: OkHttpClient) {
    private val json = "application/json; charset=utf-8".toMediaType()

    private fun errorText(body: String?): String {
        if (body.isNullOrBlank()) return "Empty error response from OpenAI"
        return try {
            JSONObject(body).optJSONObject("error")?.optString("message")?.takeIf { it.isNotBlank() } ?: body.take(300)
        } catch (e: Exception) {
            body.take(300)
        }
    }

    fun stream(apiKey: String, model: String, system: String, history: List<ChatMessage>): Flow<String> = flow {
        if (apiKey.isBlank()) throw ApiException("No OpenAI API key. Add it in Settings > Gemini API configuration.")
        val msgs = JSONArray().put(JSONObject().put("role", "system").put("content", system))
        history.filter { it.text.isNotBlank() }.forEach {
            msgs.put(JSONObject().put("role", if (it.isUser) "user" else "assistant").put("content", it.text))
        }
        val body = JSONObject()
            .put("model", model.ifBlank { "gpt-4o" })
            .put("stream", true)
            .put("messages", msgs)
            .toString()
        val req = Request.Builder()
            .url("https://api.openai.com/v1/chat/completions")
            .header("Authorization", "Bearer $apiKey")
            .post(body.toRequestBody(json))
            .build()
        val call = http.newCall(req)
        val handle = currentCoroutineContext().job.invokeOnCompletion { call.cancel() }
        try {
            call.execute().use { resp ->
                if (!resp.isSuccessful) throw ApiException(errorText(resp.body?.string()), resp.code)
                val src = resp.body!!.source()
                while (true) {
                    val line = src.readUtf8Line() ?: break
                    if (!line.startsWith("data:")) continue
                    val data = line.removePrefix("data:").trim()
                    if (data.isEmpty()) continue
                    if (data == "[DONE]") break
                    val piece = JSONObject(data).optJSONArray("choices")?.optJSONObject(0)
                        ?.optJSONObject("delta")?.optString("content", "") ?: ""
                    if (piece.isNotEmpty()) emit(piece)
                }
            }
        } finally {
            handle.dispose()
        }
    }.flowOn(Dispatchers.IO)
}
