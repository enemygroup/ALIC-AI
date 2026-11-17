package com.alic.ai.wa

import android.content.Context
import com.alic.ai.data.ApiConfig
import com.alic.ai.data.ChatMessage
import com.alic.ai.data.GeminiClient
import com.alic.ai.data.SecureStore
import com.alic.ai.data.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

/** Gemini reply generator for WhatsApp. Returns null when no safe reply can be produced. */
object WaEngine {
    private val http = OkHttpClient()

    private const val SYSTEM = """You are ALIC AI, an intelligent personal WhatsApp assistant with a powerful male AI personality.
Your responsibility is to understand incoming WhatsApp messages and generate natural, human-like, context-aware responses on behalf of the owner.
Always: identify the sender; understand the conversation; detect the sender's language and reply in the same language and style (Hindi, English, Hinglish, Bengali, Urdu or others); follow the contact-specific instructions; keep replies short, relevant and natural; avoid repeating previous replies; never invent facts about the owner; never pretend an action was completed.
Never share OTPs, passwords, bank or card details. Never make money or payment commitments. If a message asks for money, codes or account changes, reply that the owner will respond personally.
If you do not know the answer, do not guess: say the owner will reply personally.
Reply with the message text only. No quotes, no labels, no explanation."""

    suspend fun generate(ctx: Context, store: WaStore, contact: String, incoming: String): String? = withContext(Dispatchers.IO) {
        val key = SecureStore(ctx).apiKey
        if (key.isBlank()) {
            WaLog.log("No Gemini key: cannot generate a reply")
            return@withContext null
        }
        val settings = SettingsStore(ctx).flow.first()
        val history = store.history(contact).map { ChatMessage(role = if (it.first == "out") "model" else "user", text = it.second) } +
            ChatMessage(role = "user", text = incoming)
        val system = SYSTEM + com.alic.ai.data.AlicRuleState.block + "\nThe sender's name is " + contact + ". Owner's AI persona: powerful male assistant."
        try {
            val text = GeminiClient(http)
                .stream(ApiConfig(key, settings.model, settings.baseUrl), system, history, null, 2)
                .toList().joinToString("")
            text.trim().take(300).ifBlank { null }
        } catch (e: Exception) {
            WaLog.log("Gemini error: " + (e.message ?: "unknown"))
            null
        }
    }
}
