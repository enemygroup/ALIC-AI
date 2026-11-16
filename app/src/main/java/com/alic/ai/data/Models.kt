package com.alic.ai.data

import java.util.UUID

enum class OrbState { IDLE, LISTENING, THINKING, SPEAKING, LIVE }

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: String, // "user" or "model"
    val text: String,
    val ts: Long = System.currentTimeMillis()
) {
    val isUser: Boolean get() = role == "user"
}

data class Conversation(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "New chat",
    val createdAt: Long = System.currentTimeMillis(),
    val messages: List<ChatMessage> = emptyList()
)

data class ImageData(val mime: String, val base64: String)

data class ApiConfig(val apiKey: String, val model: String, val baseUrl: String)

data class AppSettings(
    val model: String = "gemini-flash-latest",
    val liveModel: String = "gemini-3.8-live",
    val liveVoice: String = "Charon",
    val baseUrl: String = "https://generativelanguage.googleapis.com",
    val personality: String = "Powerful, calm, confident and respectful male assistant",
    val responseStyle: Int = 1, // 0 concise, 1 balanced, 2 detailed
    val ttsVoice: String = "",
    val deepVoice: Boolean = true,
    val pitch: Float = 1.0f,
    val speed: Float = 1.0f,
    val language: String = "auto", // auto, en, hi
    val haptics: Boolean = true,
    val animations: Boolean = true,
    val memoryEnabled: Boolean = true,
    val memoryNotes: List<String> = emptyList(),
    val confirmActions: Boolean = false,
    val notifications: Boolean = true,
    val guest: Boolean = false,
    val themeMode: String = "dark",   // dark, light, sunset
    val accent: String = "emerald",
    // --- wake word / popup / barge-in ---
    val wakeEnabled: Boolean = false,
    val wakeAlwaysOn: Boolean = true,
    val wakeSound: Boolean = true,
    val wakeSensitivity: String = "medium", // kam, medium, zyada -> VAD RMS 600 / 300 / 120
    val popupAnimation: Boolean = true,
    val silenceTimeoutSec: Int = 8,          // 5, 8 or 15
    val voiceInterruption: Boolean = true,
    val announceCalls: Boolean = true,       // speak who is calling
    val persona: String = "ultron",          // eden, ultron, edait, rtk, ragan, custom
    val userName: String = "",
    val imageModel: String = "gemini-2.5-flash-image",
    val codingProvider: String = "gemini",   // gemini (default, free tier) or openai (ChatGPT)
    val openaiModel: String = "gpt-4o",
    val clapEnabled: Boolean = false,
    val clapMode: String = "double",         // single, double
    val clapSensitivity: String = "medium"   // low, medium, high     // aurora, red, cyan, blue, purple
)
