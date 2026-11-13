package com.alic.ai.data

/**
 * Assistant personas. The NAME is what the user sees (E-DEN, ULTRON, ...); behind the scenes every persona is
 * just Gemini with a different voice (Gemini Live prebuilt voice) and a different personality / gender in the prompt.
 */
data class Persona(
    val id: String, val name: String, val gender: String, val tags: String,
    val voice: String,     // Gemini Live prebuilt voice name
    val style: String      // personality used in the system prompt
) {
    val female: Boolean get() = gender == "Female"
    val label: String get() = "$name ($gender)"
}

object Personas {
    val all = listOf(
        Persona("eden", "E-DEN", "Female", "Friendly • Smart", "Kore", "warm, friendly and smart"),
        Persona("ultron", "ULTRON", "Male", "Deep Voice • Powerful", "Charon", "deep, powerful, confident and commanding"),
        Persona("edait", "E.D.A.I.T", "Male", "Calm • Focused", "Orus", "calm, focused and precise"),
        Persona("rtk", "RTK", "Male", "Energetic • Fun", "Fenrir", "energetic, playful and fun"),
        Persona("ragan", "RAGAN", "Female", "Caring • Emotional", "Aoede", "caring, gentle and emotionally warm")
    )
    val custom = Persona("custom", "Custom", "Male", "Your own voice", "", "natural, helpful and friendly")

    /** Gemini Live voices offered under "Custom Voice". */
    val voices = listOf("Charon", "Puck", "Fenrir", "Orus", "Kore", "Aoede", "Leda", "Zephyr", "Callirrhoe", "Umbriel", "Algieba", "Achird")

    fun byId(id: String): Persona = if (id == "custom") custom else all.firstOrNull { it.id == id } ?: all[1]
}
