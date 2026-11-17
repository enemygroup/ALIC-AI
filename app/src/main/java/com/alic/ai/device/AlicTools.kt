package com.alic.ai.device

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.alic.ai.call.CallActions
import com.alic.ai.data.SecureStore
import com.alic.ai.projectbuild.ProjectSaver
import android.content.ContentUris
import android.provider.MediaStore
import com.alic.ai.service.AlicAccessibilityService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Function declarations given to Gemini (both Live and chat) so it can actually operate the phone. */
object AlicTools {
    private fun fn(name: String, desc: String, vararg props: Triple<String, String, String>, required: List<String> = emptyList()): JSONObject {
        val o = JSONObject().put("name", name).put("description", desc)
        if (props.isNotEmpty()) {
            val p = JSONObject()
            props.forEach { (n, t, d) -> p.put(n, JSONObject().put("type", t).put("description", d)) }
            o.put("parameters", JSONObject().put("type", "OBJECT").put("properties", p).put("required", JSONArray(required)))
        }
        return o
    }

    fun declarations(): JSONArray = JSONArray()
        .put(fn("open_app", "Open any app installed on the phone (YouTube, WhatsApp, Instagram, Chrome, Camera, Gallery, Calculator, Settings, Maps, Phone dialer, Contacts, Messages, Clock, Play Store, etc.).",
            Triple("app_name", "STRING", "Name of the app, e.g. WhatsApp"), required = listOf("app_name")))
        .put(fn("call_contact", "Place a phone call to a saved contact name or a phone number.",
            Triple("name_or_number", "STRING", "Contact name (e.g. Mummy) or phone number"), required = listOf("name_or_number")))
        .put(fn("send_sms", "Prepare an SMS to a contact or number. The Messages app opens with the text typed; the user taps Send.",
            Triple("to", "STRING", "Contact name or phone number"), Triple("message", "STRING", "Message text"), required = listOf("to", "message")))
        .put(fn("send_whatsapp", "Prepare a WhatsApp message to a contact or number. WhatsApp opens with the text typed; the user taps Send.",
            Triple("to", "STRING", "Contact name or phone number"), Triple("message", "STRING", "Message text"), required = listOf("to", "message")))
        .put(fn("youtube_search", "Play a video or song on YouTube or YouTube Music: it finds the first result and starts it. Use for play, chalao, bajao.",
            Triple("query", "STRING", "What to search for"), Triple("music", "BOOLEAN", "true to use YouTube Music"), required = listOf("query")))
        .put(fn("web_search", "Search Google for a query in the browser.",
            Triple("query", "STRING", "Search text"), required = listOf("query")))
        .put(fn("open_url", "Open a web page link in the browser.",
            Triple("url", "STRING", "Full https:// link"), required = listOf("url")))
        .put(fn("navigate", "Start Google Maps navigation or show a place on the map.",
            Triple("destination", "STRING", "Place or address"), required = listOf("destination")))
        .put(fn("set_alarm", "Set an alarm clock for a time of day.",
            Triple("hour", "INTEGER", "Hour in 24-hour format 0-23"), Triple("minute", "INTEGER", "Minute 0-59"),
            Triple("label", "STRING", "Optional label"), required = listOf("hour", "minute")))
        .put(fn("set_timer", "Start a countdown timer.",
            Triple("seconds", "INTEGER", "Duration in seconds"), Triple("label", "STRING", "Optional label"), required = listOf("seconds")))
        .put(fn("phone_action", "Press a system button or gesture: back, home, recents, notifications, quick_settings, lock_screen, screenshot.",
            Triple("action", "STRING", "One of: back, home, recents, notifications, quick_settings, lock_screen, screenshot"), required = listOf("action")))
        .put(fn("flashlight", "Turn the phone flashlight (torch) on or off.",
            Triple("on", "BOOLEAN", "true = on, false = off"), required = listOf("on")))
        .put(fn("open_settings", "Open an Android settings page.",
            Triple("section", "STRING", "wifi, bluetooth, display, sound, battery, location, airplane, apps or general"), required = listOf("section")))
        .put(fn("set_volume", "Set the phone volume as a percentage (0-100).",
            Triple("percent", "INTEGER", "Volume percent 0-100"), required = listOf("percent")))
        .put(fn("end_call", "Hang up, reject or end the current or ringing phone call."))
        .put(fn("answer_call", "Answer the ringing phone call."))
        .put(fn("call_speaker", "Turn the speakerphone on or off during a phone call.",
            Triple("on", "BOOLEAN", "true = speaker on"), required = listOf("on")))
        .put(fn("add_calendar_event", "Create a calendar event (the calendar app opens with the details filled in for the user to save).",
            Triple("title", "STRING", "Event title"), Triple("start", "STRING", "Local start date-time like 2026-11-20T18:30"),
            Triple("duration_minutes", "INTEGER", "Length in minutes"), Triple("location", "STRING", "Optional place"),
            required = listOf("title", "start")))
        .put(fn("media_control", "Control the currently playing YouTube or music audio: play, pause, next, previous, forward (10 s), rewind (10 s).",
            Triple("action", "STRING", "play, pause, next, previous, forward or rewind"), required = listOf("action")))
        .put(fn("scroll_screen", "Scroll the current screen up or down.",
            Triple("direction", "STRING", "up or down"), required = listOf("direction")))
        .put(fn("read_notifications", "Read the most recent phone notifications so ALIC can summarise them. Use when the user asks what notifications they have.",
            Triple("limit", "INTEGER", "How many recent notifications, 1 to 10"), required = emptyList()))
        .put(fn("youtube_action", "Press Like or Subscribe on the YouTube video currently open on screen.",
            Triple("action", "STRING", "like or subscribe"), required = listOf("action")))
        .put(fn("send_photo", "Send a photo from the gallery to a contact on WhatsApp or SMS. which = latest, or a word from the photo name or album (for example Screenshots).",
            Triple("to", "STRING", "Contact name"), Triple("which", "STRING", "latest, or a name/album word"), Triple("app", "STRING", "whatsapp or sms"), required = listOf("to")))
        .put(fn("get_time", "Get the current date and time on the phone."))
}

class ToolExecutor(private val ctx: Context, private val launcher: AppLauncher) {
    private fun has(p: String) = ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED
    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
    private fun start(i: Intent) { i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); ctx.startActivity(i) }

    suspend fun run(name: String, a: JSONObject): String = withContext(Dispatchers.IO) {
        try {
            when (name) {
                "open_app" -> launcher.execute(Command.OpenApp(a.optString("app_name")))
                "call_contact" -> call(a.optString("name_or_number"))
                "send_sms" -> sms(a.optString("to"), a.optString("message"))
                "send_whatsapp" -> whatsapp(a.optString("to"), a.optString("message"))
                "youtube_search" -> playOrSearch(a.optString("query"), a.optBoolean("music", false))
                "media_control" -> com.alic.ai.service.MediaControl.run(ctx, a.optString("action"))
                "scroll_screen" -> AlicAccessibilityService.scroll(a.optString("direction", "down").lowercase() != "up")
                "web_search" -> openUrl("https://www.google.com/search?q=" + enc(a.optString("query")), "Searching Google")
                "open_url" -> openUrl(a.optString("url"), "Opening the link")
                "navigate" -> navigate(a.optString("destination"))
                "set_alarm" -> alarm(a.optInt("hour", -1), a.optInt("minute", 0), a.optString("label"))
                "set_timer" -> timer(a.optInt("seconds", 0), a.optString("label"))
                "phone_action" -> phoneAction(a.optString("action"))
                "flashlight" -> flashlight(a.optBoolean("on", true))
                "open_settings" -> settingsPage(a.optString("section"))
                "set_volume" -> volume(a.optInt("percent", -1))
                "end_call" -> CallActions.hangUp(ctx)
                "answer_call" -> CallActions.answer(ctx)
                "call_speaker" -> CallActions.speaker(ctx, a.optBoolean("on", true))
                "add_calendar_event" -> calendarEvent(a.optString("title"), a.optString("start"), a.optInt("duration_minutes", 60), a.optString("location"))
                "read_notifications" -> readNotifications(a.optInt("limit", 5))
                "youtube_action" -> youtubeAction(a.optString("action"))
                "send_photo" -> sendPhoto(a.optString("to"), a.optString("which"), a.optString("app") == "sms")
                "get_time" -> SimpleDateFormat("EEEE, d MMMM yyyy, hh:mm a", Locale.getDefault()).format(Date())
                else -> "Unknown function: $name"
            }
        } catch (e: ActivityNotFoundException) {
            "No app on this phone can do that."
        } catch (e: SecurityException) {
            "Permission missing: ${e.message}"
        } catch (e: Exception) {
            "Failed: ${e.message}"
        }
    }

    private fun playOrSearch(q: String, music: Boolean): String {
        val key = SecureStore(ctx).youtubeKey
        if (key.isNotBlank()) return YouTubePlayer.playFirst(ctx, key, q, music)
        return launcher.youtube(q, music) + " Add a free YouTube key in Settings to play it directly."
    }

    private fun readNotifications(limit: Int): String {
        val list = com.alic.ai.service.NotificationStore.latest(limit)
        if (list.isEmpty()) return "No recent notifications. If you expect some, turn on Media control (notification access) in Permissions."
        return list.joinToString("\n") { n ->
            "- ${n.pkg}: ${n.title}" + if (n.text.isNotBlank()) " — ${n.text}" else ""
        }
    }

    private fun youtubeAction(action: String): String {
        if (AlicAccessibilityService.instance == null) return "Turn on ALIC AI in Accessibility settings first, then open the video."
        val ok = when (action.trim().lowercase()) {
            "like" -> AlicAccessibilityService.clickByLabel { l ->
                val x = l.lowercase()
                x == "like" || x.contains("like this video")
            }
            "subscribe" -> AlicAccessibilityService.clickByLabel { l ->
                val x = l.lowercase()
                x == "subscribe" || (x.contains("subscribe") && !x.contains("subscribed") && !x.contains("unsubscribe"))
            }
            else -> return "Unknown action. Use like or subscribe."
        }
        return if (ok) "Done: ${action.trim()}." else "Could not find the ${action.trim()} button. Open the YouTube video and keep it on screen, then ask again."
    }

    /** Waits for the app to open the drafted message, then presses its Send button through Accessibility. */
    private fun pressSend(app: String, name: String, msg: String): String {
        Thread.sleep(2200)
        val clicked = AlicAccessibilityService.instance != null && AlicAccessibilityService.clickByLabel { l ->
            val x = l.trim().lowercase()
            x == "send" || x == "send message" || x == "send sms"
        }
        if (clicked) return "Message to $name sent through $app."
        val saved = ProjectSaver.write(ctx, "Drafts/draft_${System.currentTimeMillis()}.txt", "To: $name\n\n$msg".toByteArray(Charsets.UTF_8), "text/plain")
        return if (saved >= 0) "I could not press Send in $app, so the message is saved as a draft in Download/ALIC_AI/Drafts."
        else "I could not press Send, and saving the draft failed too. Please send it yourself."
    }

    /** Sends the newest photo (or one whose name/album matches) to a contact, via the share sheet. */
    private fun sendPhoto(to: String, which: String, viaSms: Boolean): String {
        val (name, _) = resolve(to) ?: return "No contact found for \"$to\"."
        val photoPerm = if (android.os.Build.VERSION.SDK_INT >= 33) "android.permission.READ_MEDIA_IMAGES" else "android.permission.READ_EXTERNAL_STORAGE"
        if (!has(photoPerm)) return "Allow Photos in ALIC's Permissions first."
        val uri = latestImage(which) ?: return "No photo found${if (which.isBlank()) "" else " matching \"$which\""}."
        val share = Intent(Intent.ACTION_SEND).setType("image/*").putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        if (!viaSms) share.setPackage("com.whatsapp")
        try { ctx.startActivity(share) } catch (e: android.content.ActivityNotFoundException) { return "That messaging app is not installed." }
        Thread.sleep(2500)
        if (AlicAccessibilityService.instance == null) return "The photo is ready to share. Turn on ALIC's Accessibility to pick $name and press Send."
        val first = name.trim().split(" ").firstOrNull().orEmpty()
        val picked = AlicAccessibilityService.clickByLabel { l ->
            val x = l.trim()
            x.equals(name.trim(), ignoreCase = true) || (first.isNotBlank() && x.equals(first, ignoreCase = true))
        }
        if (!picked) return "The photo is ready, but I could not find $name in the list. Please pick the contact yourself."
        Thread.sleep(1200)
        val sent = AlicAccessibilityService.clickByLabel { l -> l.trim().lowercase() == "send" }
        return if (sent) "Photo sent to $name." else "The photo is ready for $name. Please tap Send."
    }

    /** Newest image, or the newest whose file name or album contains [which]. */
    private fun latestImage(which: String): android.net.Uri? {
        val key = which.trim()
        val useAll = key.isBlank() || key.equals("latest", true) || key.equals("last", true)
        val sel: String? = if (useAll) null else
            "${MediaStore.Images.Media.DISPLAY_NAME} LIKE ? OR ${MediaStore.Images.Media.BUCKET_DISPLAY_NAME} LIKE ?"
        val args: Array<String>? = if (useAll) null else arrayOf("%$key%", "%$key%")
        ctx.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, arrayOf(MediaStore.Images.Media._ID), sel, args,
            "${MediaStore.Images.Media.DATE_ADDED} DESC"
        )?.use { c ->
            if (c.moveToFirst()) return ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, c.getLong(0))
        }
        return null
    }

    // ---- contacts ----
    private fun lookup(q: String): Pair<String, String>? {
        val cr = ctx.contentResolver
        val rows = mutableListOf<Pair<String, String>>()
        cr.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER),
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " LIKE ?", arrayOf("%" + q.trim() + "%"), null
        )?.use { c ->
            while (c.moveToNext() && rows.size < 25) rows.add(c.getString(0).orEmpty() to c.getString(1).orEmpty())
        }
        return rows.firstOrNull { it.first.equals(q.trim(), true) }
            ?: rows.firstOrNull { it.first.startsWith(q.trim(), true) }
            ?: rows.firstOrNull()
    }

    private fun resolve(q: String): Pair<String, String>? {
        val text = q.trim()
        if (text.count { it.isDigit() } >= 5 && text.all { it.isDigit() || it in "+ -()" }) return text to text.filter { it.isDigit() || it == '+' }
        if (!has(Manifest.permission.READ_CONTACTS)) throw SecurityException("Contacts permission is not granted. Ask the user to allow Contacts in ALIC's Permissions screen")
        lookup(text)?.let { return it }
        // try individual words, longest first ("Mummy ji" -> "Mummy")
        for (w in text.split(Regex("\\s+")).filter { it.length >= 3 }.sortedByDescending { it.length }) lookup(w)?.let { return it }
        return null
    }

    private fun call(q: String): String {
        val (name, num) = resolve(q) ?: return "No contact or number found for \"$q\"."
        val uri = Uri.parse("tel:" + Uri.encode(num))
        return if (has(Manifest.permission.CALL_PHONE)) {
            start(Intent(Intent.ACTION_CALL, uri)); "Calling $name."
        } else {
            start(Intent(Intent.ACTION_DIAL, uri)); "Opened the dialer for $name. The user must press the call button because Call permission is not granted."
        }
    }

    private fun sms(to: String, msg: String): String {
        val (name, num) = resolve(to) ?: return "No contact or number found for \"$to\"."
        start(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(num))).putExtra("sms_body", msg))
        return pressSend("Messages", name, msg)
    }

    private fun whatsapp(to: String, msg: String): String {
        val (name, num) = resolve(to) ?: return "No contact or number found for \"$to\"."
        val digits = num.filter { it.isDigit() }
        val uri = Uri.parse("https://wa.me/$digits?text=" + enc(msg))
        for (pkg in listOf("com.whatsapp", "com.whatsapp.w4b")) {
            try { start(Intent(Intent.ACTION_VIEW, uri).setPackage(pkg)); return pressSend("WhatsApp", name, msg) } catch (e: Exception) {}
        }
        start(Intent(Intent.ACTION_VIEW, uri))
        return pressSend("WhatsApp", name, msg)
    }

    // ---- web / maps ----
    private fun openUrl(url: String, label: String): String {
        val u = url.trim()
        if (!(u.startsWith("https://") || u.startsWith("http://"))) return "Only http/https links can be opened."
        start(Intent(Intent.ACTION_VIEW, Uri.parse(u)))
        return "$label."
    }

    private fun navigate(dest: String): String {
        if (dest.isBlank()) return "No destination given."
        try {
            start(Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=" + enc(dest))).setPackage("com.google.android.apps.maps"))
        } catch (e: Exception) {
            start(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + enc(dest))))
        }
        return "Opening Maps for $dest."
    }

    private fun calendarEvent(title: String, start: String, minutes: Int, location: String): String {
        if (title.isBlank()) return "No event title given."
        val begin = try {
            java.time.LocalDateTime.parse(start.trim().take(16)).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        } catch (e: Exception) {
            return "Could not understand the start time \"$start\". Use a format like 2026-11-20T18:30."
        }
        start(
            Intent(Intent.ACTION_INSERT).setData(CalendarContract.Events.CONTENT_URI)
                .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, begin)
                .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, begin + minutes.coerceIn(5, 1440) * 60_000L)
                .putExtra(CalendarContract.Events.TITLE, title)
                .putExtra(CalendarContract.Events.EVENT_LOCATION, location)
        )
        return "Calendar event \"$title\" is open for the user to save."
    }

    // ---- clock ----
    private fun alarm(h: Int, m: Int, label: String): String {
        if (h !in 0..23 || m !in 0..59) return "Invalid time."
        start(
            Intent(AlarmClock.ACTION_SET_ALARM).putExtra(AlarmClock.EXTRA_HOUR, h).putExtra(AlarmClock.EXTRA_MINUTES, m)
                .putExtra(AlarmClock.EXTRA_MESSAGE, label).putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        )
        return "Alarm set for %02d:%02d.".format(h, m)
    }

    private fun timer(sec: Int, label: String): String {
        if (sec !in 1..86400) return "Invalid duration."
        start(Intent(AlarmClock.ACTION_SET_TIMER).putExtra(AlarmClock.EXTRA_LENGTH, sec).putExtra(AlarmClock.EXTRA_MESSAGE, label).putExtra(AlarmClock.EXTRA_SKIP_UI, true))
        return "Timer started for $sec seconds."
    }

    // ---- device ----
    private fun phoneAction(action: String): String {
        val svc = AlicAccessibilityService.instance
            ?: return "The Accessibility service is off. Ask the user to turn on ALIC AI under Accessibility (Tools > Permissions)."
        val code = when (action.trim().lowercase()) {
            "back" -> AccessibilityService.GLOBAL_ACTION_BACK
            "home" -> AccessibilityService.GLOBAL_ACTION_HOME
            "recents" -> AccessibilityService.GLOBAL_ACTION_RECENTS
            "notifications" -> AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS
            "quick_settings" -> AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS
            "lock_screen" -> AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN
            "screenshot" -> AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT
            else -> return "Unknown action \"$action\"."
        }
        return if (svc.performGlobalAction(code)) "Done: $action." else "Android refused that action."
    }

    private fun flashlight(on: Boolean): String {
        val cm = ctx.getSystemService(CameraManager::class.java)
        val id = cm.cameraIdList.firstOrNull { cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true }
            ?: return "This phone has no flashlight."
        cm.setTorchMode(id, on)
        return "Flashlight ${if (on) "on" else "off"}."
    }

    private fun settingsPage(section: String): String {
        val action = when (section.trim().lowercase()) {
            "wifi", "wi-fi" -> Settings.ACTION_WIFI_SETTINGS
            "bluetooth" -> Settings.ACTION_BLUETOOTH_SETTINGS
            "display" -> Settings.ACTION_DISPLAY_SETTINGS
            "sound" -> Settings.ACTION_SOUND_SETTINGS
            "battery" -> Settings.ACTION_BATTERY_SAVER_SETTINGS
            "location" -> Settings.ACTION_LOCATION_SOURCE_SETTINGS
            "airplane" -> Settings.ACTION_AIRPLANE_MODE_SETTINGS
            "apps" -> Settings.ACTION_APPLICATION_SETTINGS
            else -> Settings.ACTION_SETTINGS
        }
        start(Intent(action))
        return "Opened $section settings."
    }

    private fun volume(percent: Int): String {
        if (percent !in 0..100) return "Volume must be 0-100."
        val am = ctx.getSystemService(AudioManager::class.java)
        for (stream in listOf(AudioManager.STREAM_MUSIC, AudioManager.STREAM_VOICE_CALL)) {
            try {
                val max = am.getStreamMaxVolume(stream)
                am.setStreamVolume(stream, (max * percent / 100f).toInt().coerceIn(0, max), if (stream == AudioManager.STREAM_MUSIC) AudioManager.FLAG_SHOW_UI else 0)
            } catch (e: Exception) {}
        }
        return "Volume set to $percent%."
    }
}
