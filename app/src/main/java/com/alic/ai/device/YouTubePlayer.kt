package com.alic.ai.device

import android.content.Context
import android.content.Intent
import android.net.Uri
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder

/**
 * Plays the first YouTube result. Uses the YouTube Data API v3 search (the user's own free API key) to find
 * the video id, then opens it in the YouTube (or YouTube Music) app, which starts playback.
 * Must be called off the main thread.
 */
object YouTubePlayer {
    private val http = OkHttpClient()

    fun playFirst(ctx: Context, key: String, query: String, music: Boolean): String {
        val url = "https://www.googleapis.com/youtube/v3/search?part=snippet&type=video&maxResults=1" +
            "&q=" + URLEncoder.encode(query, "UTF-8") + "&key=" + URLEncoder.encode(key, "UTF-8")
        return try {
            http.newCall(Request.Builder().url(url).build()).execute().use { resp ->
                val txt = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    val msg = try { JSONObject(txt).getJSONObject("error").optString("message") } catch (e: Exception) { "HTTP ${resp.code}" }
                    return "YouTube refused the search: $msg"
                }
                val item = JSONObject(txt).optJSONArray("items")?.optJSONObject(0) ?: return "No video found for $query."
                val id = item.getJSONObject("id").optString("videoId")
                val title = item.getJSONObject("snippet").optString("title")
                val host = if (music) "music.youtube.com" else "www.youtube.com"
                val link = Uri.parse("https://$host/watch?v=$id")
                val pkg = if (music) "com.google.android.apps.youtube.music" else "com.google.android.youtube"
                try {
                    ctx.startActivity(Intent(Intent.ACTION_VIEW, link).setPackage(pkg).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (e: Exception) {
                    ctx.startActivity(Intent(Intent.ACTION_VIEW, link).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
                "Playing: $title"
            }
        } catch (e: Exception) {
            "Could not reach YouTube: ${e.message}"
        }
    }
}
