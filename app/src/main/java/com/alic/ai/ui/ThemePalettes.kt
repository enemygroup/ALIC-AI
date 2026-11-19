package com.alic.ai.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/** A complete app colour set. When chosen, it replaces the built-in mode/accent colours everywhere. */
data class Palette(
    val id: String, val name: String,
    val bg: Color, val primary: Color, val secondary: Color, val accent: Color, val light: Boolean
) {
    val text: Color get() = if (light) Color(0xFF14141A) else Color.White
    val sub: Color get() = if (light) Color(0xFF5E6068) else lerp(Color.White, bg, 0.35f)
    val card: Color get() = if (light) Color.White else lerp(bg, secondary, 0.22f)
    val border: Color get() = secondary
    val orb: List<Color> get() = listOf(primary, accent, lerp(primary, accent, 0.5f), lerp(primary, Color.White, 0.3f))
}

object ThemePalettes {
    val all: List<Palette> = listOf(
        Palette("red", "Red & Black", Color(0xFF09090B), Color(0xFFEF233C), Color(0xFF45121D), Color(0xFFFF5268), false),
        Palette("purple", "Purple Galaxy", Color(0xFF10091F), Color(0xFF9333EA), Color(0xFF3B1765), Color(0xFFD8B4FE), false),
        Palette("cyberblue", "Cyber Blue", Color(0xFF061426), Color(0xFF168BFF), Color(0xFF123C67), Color(0xFF55D6FF), false),
        Palette("neongreen", "Neon Green", Color(0xFF07140D), Color(0xFF22C55E), Color(0xFF14532D), Color(0xFF86EFAC), false),
        Palette("goldlux", "Royal Gold", Color(0xFF15110A), Color(0xFFD4A72C), Color(0xFF493715), Color(0xFFFFE08A), false),
        Palette("pinkai", "Pink AI", Color(0xFF190C19), Color(0xFFEC4899), Color(0xFF70244F), Color(0xFFFFA6D5), false),
        Palette("orangefire", "Orange Fire", Color(0xFF1B0D07), Color(0xFFF97316), Color(0xFF71330F), Color(0xFFFDBA74), false),
        Palette("cyanholo", "Cyan Hologram", Color(0xFF061417), Color(0xFF06B6D4), Color(0xFF164E63), Color(0xFF67E8F9), false),
        Palette("whitesilver", "White Silver", Color(0xFFF1F3F7), Color(0xFF64748B), Color(0xFFD9DEE8), Color(0xFF2563EB), true),
        Palette("midnight", "Midnight Blue", Color(0xFF080F26), Color(0xFF4F6BFF), Color(0xFF1E2B5B), Color(0xFF91A7FF), false),
        Palette("emeraldlux", "Emerald Luxury", Color(0xFF071A16), Color(0xFF10B981), Color(0xFF145C49), Color(0xFF6EE7B7), false),
        Palette("lavender", "Lavender Dream", Color(0xFF171126), Color(0xFFA78BFA), Color(0xFF55417C), Color(0xFFE9D5FF), false),
        Palette("crimson", "Crimson Dark", Color(0xFF16070C), Color(0xFFBE123C), Color(0xFF571329), Color(0xFFFB7185), false),
        Palette("icewhite", "Ice White", Color(0xFFEAF6FF), Color(0xFF0284C7), Color(0xFFC8E6F5), Color(0xFF38BDF8), true),
        Palette("cyberpunk", "Cyberpunk", Color(0xFF10091B), Color(0xFFD946EF), Color(0xFF4C1D95), Color(0xFF22D3EE), false)
    )

    /** Returns null for "" (the built-in mode and accent colours are used). */
    fun byId(id: String): Palette? = all.firstOrNull { it.id == id }
}
