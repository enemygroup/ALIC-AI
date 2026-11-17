package com.alic.ai.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class AlicRule(val id: String, val text: String, val enabled: Boolean)

/** Global rules the user writes for ALIC. They are added to every AI prompt (chat, voice and WhatsApp). */
object AlicRuleState {
    const val SILENCE = "Built-in rule: if more than one person is talking or chatting, stay silent and do not reply in between. " +
        "Reply only when the user or a single conversation partner speaks directly to you."
    @Volatile var block: String = "\n" + SILENCE

    fun refresh(list: List<AlicRule>) {
        val active = list.filter { it.enabled }
        block = "\n" + SILENCE + if (active.isEmpty()) "" else
            "\nThe user's own rules, always follow them:\n" + active.joinToString("\n") { "- " + it.text }
    }
}

class AlicRuleStore(ctx: Context) {
    private val p = ctx.getSharedPreferences("alic_rules", Context.MODE_PRIVATE)

    fun all(): List<AlicRule> {
        val arr = JSONArray(p.getString("rules", "[]") ?: "[]")
        return (0 until arr.length()).map { val o = arr.getJSONObject(it); AlicRule(o.getString("id"), o.getString("t"), o.optBoolean("e", true)) }
    }

    fun save(list: List<AlicRule>) {
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("id", it.id).put("t", it.text).put("e", it.enabled)) }
        p.edit().putString("rules", arr.toString()).apply()
        AlicRuleState.refresh(list)
    }
}
