package com.alic.ai.ui

/**
 * Display-only styling: converts Latin letters to Unicode Mathematical Bold Serif (𝐀𝐁𝐂 / 𝐚𝐛𝐜).
 * Digits, symbols, emoji and anything else are left unchanged. Never use this on codes, links,
 * emails, API values or text that is compared in code.
 */
fun uiBold(s: String): String {
    val sb = StringBuilder(s.length * 2)
    for (ch in s) {
        when (ch) {
            in 'A'..'Z' -> sb.appendCodePoint(0x1D400 + (ch - 'A'))
            in 'a'..'z' -> sb.appendCodePoint(0x1D41A + (ch - 'a'))
            else -> sb.append(ch)
        }
    }
    return sb.toString()
}
