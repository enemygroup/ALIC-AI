package com.alic.ai.device

import android.accessibilityservice.AccessibilityService
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import android.provider.ContactsContract
import android.provider.MediaStore
import android.provider.Settings
import com.alic.ai.service.AlicAccessibilityService
import java.net.URLEncoder

sealed interface Command {
    data class OpenApp(val name: String) : Command
    data class YouTube(val query: String, val music: Boolean) : Command
    data class OpenUrl(val url: String) : Command
    data class Global(val action: Int, val label: String) : Command
}

data class AppInfo(val label: String, val pkg: String)

object CommandRouter {
    private val openVerbs = setOf("open", "kholo", "khol", "kholiye", "khole", "launch", "start", "chalu", "खोलो", "खोल", "ओपन")
    private val searchWords = setOf("search", "play", "chalao", "bajao", "dhundo", "dhoondo", "dikhao", "find", "sunao", "laga", "lagao", "chala", "चलाओ", "बजाओ", "सर्च")
    private val filler = setOf(
        "alic", "please", "pls", "par", "pe", "me", "mein", "on", "in", "karo", "kar", "kro", "do", "de", "dena", "ki", "ka", "ke", "ko",
        "the", "for", "a", "an", "to", "hai", "zara", "mujhe", "youtube", "yt", "music", "video", "videos", "gaana", "gana", "song", "songs",
        "dekho", "app", "application", "करो", "यूट्यूब", "पर", "में", "म्यूजिक"
    ) + openVerbs + searchWords

    fun parse(input: String): Command? {
        val t = input.trim().lowercase()
        Regex("https?://(www\\.|m\\.|music\\.)?(youtu\\.be|youtube\\.com)\\S*").find(t)?.let { return Command.OpenUrl(it.value) }
        val words = t.replace(Regex("[^\\p{L}\\p{M}\\p{N}\\s]"), " ").split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return null
        val doWords = setOf("jao", "go", "karo", "press", "dabao", "kholo", "open", "show", "dikhao")
        if (words.size <= 4 && words.any { it in doWords }) {
            if (words.any { it == "back" || it == "peeche" || it == "wapas" })
                return Command.Global(AccessibilityService.GLOBAL_ACTION_BACK, "Go back")
            if (words.any { it == "home" } && words.none { it == "youtube" || it == "yt" })
                return Command.Global(AccessibilityService.GLOBAL_ACTION_HOME, "Go to the home screen")
            if (words.any { it == "recents" || it == "recent" })
                return Command.Global(AccessibilityService.GLOBAL_ACTION_RECENTS, "Show recent apps")
            if (words.any { it.startsWith("notification") })
                return Command.Global(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS, "Open notifications")
        }
        val hasYt = words.any { it == "youtube" || it == "yt" || it == "यूट्यूब" }
        val music = words.any { it == "music" || it == "म्यूजिक" }
        val video = words.any { it == "video" || it == "videos" }
        val hasSearch = words.any { it in searchWords }
        if (hasSearch && (hasYt || music)) {
            val q = words.filter { it !in filler }.joinToString(" ")
            return if (q.isBlank()) Command.OpenApp(if (music && !video) "youtube music" else "youtube")
            else Command.YouTube(q, music && !video)
        }
        if (words.size <= 5 && words.any { it in openVerbs }) {
            val name = words.filter { it !in filler }.joinToString(" ")
            if (name.isNotBlank()) return Command.OpenApp(name)
            if (hasYt) return Command.OpenApp("youtube")
        }
        if (hasYt && words.size <= 3) return Command.OpenApp("youtube")
        return null
    }

    fun describe(c: Command): String = when (c) {
        is Command.OpenApp -> "Open ${c.name.replaceFirstChar { it.uppercase() }}?"
        is Command.YouTube -> "Search ${if (c.music) "YouTube Music" else "YouTube"} for \"${c.query}\"?"
        is Command.OpenUrl -> "Open this YouTube link?"
        is Command.Global -> c.label + "?"
    }
}

class AppLauncher(private val ctx: Context) {
    private val pm get() = ctx.packageManager

    fun installedApps(): List<AppInfo> {
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(i, 0)
            .map { AppInfo(it.loadLabel(pm).toString(), it.activityInfo.packageName) }
            .filter { it.pkg != ctx.packageName }
            .distinctBy { it.pkg }
            .sortedBy { it.label.lowercase() }
    }

    private fun start(i: Intent) {
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(i)
    }

    fun launchPackage(pkg: String): Boolean {
        val i = pm.getLaunchIntentForPackage(pkg) ?: return false
        start(i)
        return true
    }

    private fun firstInstalled(vararg pkgs: String): Boolean = pkgs.any { launchPackage(it) }

    fun execute(c: Command): String = try {
        when (c) {
            is Command.OpenApp -> openByName(c.name)
            is Command.OpenUrl -> { start(Intent(Intent.ACTION_VIEW, Uri.parse(c.url))); "Opening the link." }
            is Command.YouTube -> youtube(c.query, c.music)
            is Command.Global -> {
                val svc = AlicAccessibilityService.instance
                if (svc == null) "Turn on ALIC AI in Accessibility settings first (Tools > Permissions)."
                else if (svc.performGlobalAction(c.action)) "Done: ${c.label}." else "Android refused that action."
            }
        }
    } catch (e: ActivityNotFoundException) {
        "No installed app can handle that request."
    } catch (e: Exception) {
        "Couldn't complete that: ${e.message}"
    }

    private fun selector(category: String): Boolean = try {
        start(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, category)); true
    } catch (e: Exception) { false }

    private fun openByName(raw: String): String {
        val n = raw.trim().lowercase()
        val ok = when {
            n in setOf("camera", "kamera", "कैमरा") ->
                try { start(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)); true } catch (e: Exception) { false }
            n in setOf("gallery", "photos", "photo", "गैलरी") -> selector(Intent.CATEGORY_APP_GALLERY) ||
                try { start(Intent(Intent.ACTION_VIEW).setType("image/*")); true } catch (e: Exception) { false }
            n in setOf("calculator", "calc", "कैलकुलेटर") -> selector(Intent.CATEGORY_APP_CALCULATOR)
            n in setOf("settings", "setting", "सेटिंग", "सेटिंग्स") ->
                try { start(Intent(Settings.ACTION_SETTINGS)); true } catch (e: Exception) { false }
            n in setOf("maps", "map", "google maps", "मैप्स") -> selector(Intent.CATEGORY_APP_MAPS) ||
                try { start(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0"))); true } catch (e: Exception) { false }
            n == "chrome" || n == "browser" || n == "क्रोम" -> launchPackage("com.android.chrome") ||
                try { start(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com"))); true } catch (e: Exception) { false }
            n == "whatsapp" || n == "व्हाट्सएप" -> firstInstalled("com.whatsapp", "com.whatsapp.w4b")
            n == "youtube" -> firstInstalled("com.google.android.youtube") ||
                try { start(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com"))); true } catch (e: Exception) { false }
            n == "youtube music" -> firstInstalled("com.google.android.apps.youtube.music")
            n in setOf("phone", "dialer", "dial pad", "फोन") ->
                try { start(Intent(Intent.ACTION_DIAL)); true } catch (e: Exception) { false }
            n in setOf("contacts", "contact", "कॉन्टैक्ट्स") ->
                try { start(Intent(Intent.ACTION_VIEW, ContactsContract.Contacts.CONTENT_URI)); true } catch (e: Exception) { false }
            n in setOf("messages", "message", "sms", "मैसेज") -> selector(Intent.CATEGORY_APP_MESSAGING)
            n in setOf("clock", "alarm", "घड़ी") ->
                try { start(Intent(AlarmClock.ACTION_SHOW_ALARMS)); true } catch (e: Exception) { false }
            n in setOf("play store", "playstore") -> launchPackage("com.android.vending")
            n in setOf("email", "mail") -> selector(Intent.CATEGORY_APP_EMAIL) || fuzzyLaunch("gmail")
            n in setOf("music", "gaana") -> selector(Intent.CATEGORY_APP_MUSIC)
            n in setOf("calendar") -> selector(Intent.CATEGORY_APP_CALENDAR)
            else -> fuzzyLaunch(n)
        }
        return if (ok) "Opening ${raw.trim()}." else "\"${raw.trim()}\" doesn't seem to be installed on this device."
    }

    private fun fuzzyLaunch(n: String): Boolean {
        val apps = installedApps()
        val hit = apps.firstOrNull { it.label.lowercase() == n }
            ?: apps.firstOrNull { it.label.lowercase().contains(n) }
            ?: apps.firstOrNull { n.contains(it.label.lowercase()) && it.label.length > 3 }
            ?: return false
        return launchPackage(hit.pkg)
    }

    fun youtube(query: String, music: Boolean): String {
        val q = URLEncoder.encode(query, "UTF-8")
        if (music) {
            try {
                start(Intent(Intent.ACTION_VIEW, Uri.parse("https://music.youtube.com/search?q=$q"))
                    .setPackage("com.google.android.apps.youtube.music"))
                return "Searching YouTube Music for $query."
            } catch (e: Exception) { /* fall through to YouTube */ }
        }
        try {
            start(Intent(Intent.ACTION_SEARCH).setPackage("com.google.android.youtube").putExtra("query", query))
            return "Searching YouTube for $query."
        } catch (e: Exception) { /* fall through */ }
        start(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=$q")))
        return "Searching YouTube for $query."
    }
}
