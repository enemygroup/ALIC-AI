package com.alic.ai.licence

/** Premium level of a licence. The plan text comes from the website: "lifetime", "15 days", "6 months", "365 days" ... */
enum class PlanTier(val label: String) {
    BASIC("Standard"),
    PREMIUM("Premium"),
    LIFETIME("Lifetime Premium");

    companion object {
        fun of(plan: String): PlanTier {
            val p = plan.lowercase()
            val days = Regex("(\\d+)").find(p)?.value?.toIntOrNull() ?: 0
            return when {
                p.contains("life") -> LIFETIME
                p.contains("year") || p.contains("6 month") || days >= 180 -> PREMIUM
                else -> BASIC
            }
        }
    }
}
