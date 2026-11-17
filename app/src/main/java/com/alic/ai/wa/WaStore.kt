package com.alic.ai.wa

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Live status and activity log, shown on the WhatsApp Automation screen. In memory only. */
object WaLog {
    val status = MutableStateFlow("Stopped")
    val lines = MutableStateFlow<List<String>>(emptyList())
    fun log(msg: String) {
        val t = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        lines.value = (listOf("$t  $msg") + lines.value).take(60)
    }
}

data class Rule(val id: String, val contact: String, val trigger: String, val reply: String, val enabled: Boolean)

/** All automation settings, rules and short per-contact memory. Stored in its own SharedPreferences file. */
class WaStore(ctx: Context) {
    private val p = ctx.getSharedPreferences("wa_automation", Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = p.getBoolean("enabled", false)
        set(v) { p.edit().putBoolean("enabled", v).apply() }
    var autoReply: Boolean
        get() = p.getBoolean("auto_reply", false)
        set(v) { p.edit().putBoolean("auto_reply", v).apply() }
    var voice: Boolean
        get() = p.getBoolean("voice", false)
        set(v) { p.edit().putBoolean("voice", v).apply() }
    var readMessages: Boolean
        get() = p.getBoolean("read_messages", false)
        set(v) { p.edit().putBoolean("read_messages", v).apply() }
    var delaySec: Int
        get() = p.getInt("delay", 3)
        set(v) { p.edit().putInt("delay", v).apply() }
    var maxPerContact: Int
        get() = p.getInt("max_per_contact", 20)
        set(v) { p.edit().putInt("max_per_contact", v).apply() }

    fun rules(): List<Rule> {
        val arr = JSONArray(p.getString("rules", "[]") ?: "[]")
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            Rule(o.getString("id"), o.getString("c"), o.optString("t"), o.getString("r"), o.optBoolean("e", true))
        }
    }

    fun saveRules(list: List<Rule>) {
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("id", it.id).put("c", it.contact).put("t", it.trigger).put("r", it.reply).put("e", it.enabled)) }
        p.edit().putString("rules", arr.toString()).apply()
    }

    /** First enabled rule for this sender whose trigger (if any) appears in the message. Rules have priority over AI. */
    fun matchRule(contact: String, text: String): Rule? = rules().firstOrNull { r ->
        r.enabled && r.contact.equals(contact, ignoreCase = true) && (r.trigger.isBlank() || text.contains(r.trigger, ignoreCase = true))
    }

    /** Recent messages with this contact: role "in" (them) or "out" (ALIC's replies). Last 12 only. */
    fun history(contact: String): List<Pair<String, String>> {
        val arr = JSONArray(p.getString("h_" + contact.lowercase(), "[]") ?: "[]")
        return (0 until arr.length()).map { val o = arr.getJSONObject(it); o.getString("r") to o.getString("t") }
    }

    fun addHistory(contact: String, role: String, text: String) {
        val list = (history(contact) + (role to text)).takeLast(12)
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("r", it.first).put("t", it.second)) }
        p.edit().putString("h_" + contact.lowercase(), arr.toString()).apply()
    }
}
