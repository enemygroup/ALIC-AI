package com.alic.ai.emotion

import com.alic.ai.data.AppSettings

enum class Mood(val label: String) { NEUTRAL("Neutral"), HAPPY("Happy"), SAD("Sad"), ANGRY("Angry"), STRESSED("Stressed"), CONFUSED("Confused"), EXCITED("Excited") }

/**
 * Estimates mood from the user's WORDS only (Hindi, English, Hinglish). This is a low-confidence estimate:
 * it is never shown as a fact and is passed to the AI as internal context. Voice tone, face and crying detection
 * are NOT implemented here: they need on-device ML models and camera/microphone analysis permission.
 */
object EmotionEngine {
    // Order matters: the first matching rule wins.
    private val rules: List<Pair<Mood, Regex>> = listOf(
        Mood.SAD to Regex("उदास|दुखी|दुःखी|रो रहा|रो रही|रोना|बुरा लग|अकेला|sad|crying|cry|depressed|lonely|heartbroken|hurt", RegexOption.IGNORE_CASE),
        Mood.ANGRY to Regex("गुस्सा|गुस्से|चिढ़|पागल कर|angry|furious|annoyed|irritat|hate this|shut up", RegexOption.IGNORE_CASE),
        Mood.STRESSED to Regex("टेंशन|तनाव|परेशान|stress|anxious|overwhelm|deadline|pressure|can't cope|cant cope", RegexOption.IGNORE_CASE),
        Mood.CONFUSED to Regex("समझ नहीं|समझ नही|कन्फ्यूज|confus|don't understand|dont understand|what do you mean|kya matlab|क्या मतलब", RegexOption.IGNORE_CASE),
        Mood.EXCITED to Regex("wow|omg|can't wait|cant wait|let's go|lets go|!!!", RegexOption.IGNORE_CASE),
        Mood.HAPPY to Regex("खुश|मज़ा|मजा|बढ़िया|शानदार|happy|great news|awesome|yay|celebrat|हाहा|haha|lol", RegexOption.IGNORE_CASE)
    )

    fun estimate(text: String): Mood {
        if (text.isBlank()) return Mood.NEUTRAL
        return rules.firstOrNull { it.second.containsMatchIn(text) }?.first ?: Mood.NEUTRAL
    }

    private fun strategy(m: Mood): String = when (m) {
        Mood.SAD -> "Speak gently and calmly. Listen first, ask what happened, give realistic encouragement, avoid jokes, never judge. " +
            "If the user mentions harming themselves or not wanting to live, respond with care and urge them to contact a trusted person or local emergency helpline right away."
        Mood.ANGRY -> "Stay calm, do not argue, do not be sarcastic. Understand the reason for the frustration and suggest one practical step."
        Mood.STRESSED -> "Speak slowly and simply. Break the problem into small steps and offer one or two practical suggestions. Do not overwhelm."
        Mood.CONFUSED -> "Explain simply, step by step, and ask one short clarifying question."
        Mood.HAPPY -> "Respond with warmth and enthusiasm. Celebrate the good news and ask what made their day."
        Mood.EXCITED -> "Match the energy with an upbeat, friendly reply."
        Mood.NEUTRAL -> ""
    }

    /** Internal context for the AI brain. Never shown to the user. */
    fun context(m: Mood): String {
        if (m == Mood.NEUTRAL) return ""
        return "\n[Internal emotional context, never mention or quote it] Possible mood from the user's words: ${m.label}. " +
            "Confidence: low, this is only an estimate, so do not assume it is certain. Strategy: ${strategy(m)} " +
            "If unsure how the user feels, gently ask. Never shame the user for feelings."
    }

    /** Voice adjustment for spoken replies. Only speed and pitch change; the male voice identity stays. */
    fun voiceFor(m: Mood, s: AppSettings): AppSettings = when (m) {
        Mood.SAD -> s.copy(speed = s.speed * 0.88f, pitch = s.pitch * 0.96f)
        Mood.STRESSED, Mood.CONFUSED -> s.copy(speed = s.speed * 0.92f)
        Mood.ANGRY -> s.copy(speed = s.speed * 0.9f, pitch = s.pitch * 0.97f)
        Mood.HAPPY, Mood.EXCITED -> s.copy(speed = s.speed * 1.07f, pitch = s.pitch * 1.04f)
        Mood.NEUTRAL -> s
    }
}
