package com.alic.ai.data

/** Languages the user can pick for ALIC's replies. [id] is saved; [instruction] goes into the AI prompt. */
data class ReplyLanguage(val id: String, val label: String, val instruction: String)

object ReplyLanguages {
    val all: List<ReplyLanguage> = listOf(
        ReplyLanguage("auto", "Automatic (same as yours)", ""),
        ReplyLanguage("english", "English", "English"),
        ReplyLanguage("hindi", "हिंदी (Hindi)", "Hindi in Devanagari script"),
        ReplyLanguage("hinglish", "Hinglish (Hindi in English letters)", "Hinglish: Hindi written in Roman letters mixed with English"),
        ReplyLanguage("bhojpuri", "भोजपुरी (Bhojpuri)", "Bhojpuri"),
        ReplyLanguage("maithili", "मैथिली (Maithili)", "Maithili"),
        ReplyLanguage("rajasthani", "राजस्थानी (Rajasthani)", "Rajasthani"),
        ReplyLanguage("bengali", "বাংলা (Bengali)", "Bengali"),
        ReplyLanguage("urdu", "اردو (Urdu)", "Urdu in Urdu script"),
        ReplyLanguage("punjabi", "ਪੰਜਾਬੀ (Punjabi)", "Punjabi"),
        ReplyLanguage("gujarati", "ગુજરાતી (Gujarati)", "Gujarati"),
        ReplyLanguage("marathi", "मराठी (Marathi)", "Marathi"),
        ReplyLanguage("tamil", "தமிழ் (Tamil)", "Tamil"),
        ReplyLanguage("telugu", "తెలుగు (Telugu)", "Telugu"),
        ReplyLanguage("kannada", "ಕನ್ನಡ (Kannada)", "Kannada"),
        ReplyLanguage("malayalam", "മലയാളം (Malayalam)", "Malayalam"),
        ReplyLanguage("odia", "ଓଡ଼ିଆ (Odia)", "Odia")
    )

    fun byId(id: String): ReplyLanguage = all.firstOrNull { it.id == id } ?: all.first()
}
