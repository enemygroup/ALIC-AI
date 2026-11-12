package com.alic.ai.live

import java.util.Locale

/**
 * Voice "close" commands matched against Gemini inputTranscription text.
 *
 * Matching is intentionally conservative: the whole utterance (after dropping polite filler words and
 * collapsing repeats such as "bye bye") must equal one of the phrases, compared token by token. So "sleep"
 * closes the session but "I can't sleep at night" or "sleeping" do not. Speech transcription varies, so
 * this is a best-effort list, not perfect recognition.
 */
object CloseCommands {
    private val fillers = setOf(
        "alic", "alik", "alick", "alec", "please", "pls", "ok", "okay", "hey", "ab", "abhi", "ji", "thanks", "thank", "you",
        "अब", "जी", "प्लीज", "एलिक", "एलेक"
    )

    private val phrases: Set<String> by lazy {
        listOf(
            // English / Hinglish
            "sleep", "so jao", "band ho jao", "sleep mode", "chale jao", "band karo", "bye", "good night", "goodnight",
            // Devanagari forms a transcriber may return (the brief's own Hindi list was corrupted, so these are chosen explicitly)
            "सो जाओ", "बंद हो जाओ", "बन्द हो जाओ", "चले जाओ", "बंद करो", "बन्द करो",
            "स्लीप", "स्लीप मोड", "बाय", "गुड नाइट", "गुडनाइट"
        ).map { normalize(it) }.toSet()
    }

    fun normalize(s: String): String =
        s.lowercase(Locale.ROOT)
            .replace(Regex("[^\\p{L}\\p{M}\\p{N}\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    fun matches(raw: String): Boolean {
        val tokens = normalize(raw).split(' ').filter { it.isNotEmpty() && it !in fillers }
        if (tokens.isEmpty() || tokens.size > 4) return false
        val collapsed = mutableListOf<String>()
        tokens.forEach { if (collapsed.lastOrNull() != it) collapsed.add(it) }
        return collapsed.joinToString(" ") in phrases
    }
}
