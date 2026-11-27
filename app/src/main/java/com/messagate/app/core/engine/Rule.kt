package com.messagate.app.core.engine

import org.json.JSONObject
import java.util.UUID

/** Which part of the SMS a rule inspects. */
enum class MatchField(val label: String) {
    BODY("BODY"),
    SENDER("SENDER"),
    BOTH("BOTH")
}

/** How the pattern is compared against the selected field. */
enum class MatchType(val label: String) {
    CONTAINS("CONTAINS"),
    WORD("WORD"),
    PREFIX("PREFIX"),
    SUFFIX("SUFFIX"),
    EXACT("EXACT"),
    REGEX("REGEX")
}

/** What happens when a rule matches. */
enum class RuleAction(val label: String) {
    /** Silent pre-storage drop: never written to the Android SMS database. */
    BLOCK("BLOCK"),

    /** Whitelist. Evaluated before every other rule; can never be blocked. */
    ALLOW("ALLOW"),

    /** Tag the message with a category, delivery continues normally. */
    TAG("TAG"),

    /** Deliver normally but suppress the notification (no sound/vibration/popup). */
    MUTE("MUTE")
}

/**
 * A single user-owned detection rule.
 *
 * Rules are evaluated in [priority] order (lower first). ALLOW rules are checked
 * in a first pass so a trusted sender can always bypass broad BLOCK patterns.
 */
data class Rule(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val enabled: Boolean = true,
    val priority: Int = 0,
    val field: MatchField = MatchField.BODY,
    val type: MatchType = MatchType.CONTAINS,
    val pattern: String = "",
    val caseSensitive: Boolean = false,
    val normalizeUnicode: Boolean = true,
    val action: RuleAction = RuleAction.BLOCK,
    /** Category id/name for [RuleAction.TAG]. */
    val categoryId: String? = null,
    /** Stop evaluating further rules after this match. */
    val stopProcessing: Boolean = true,
    /** "keyword" for rules migrated from the old keyword list, "user" otherwise. */
    val origin: String = "user"
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("enabled", enabled)
        put("priority", priority)
        put("field", field.name)
        put("type", type.name)
        put("pattern", pattern)
        put("caseSensitive", caseSensitive)
        put("normalizeUnicode", normalizeUnicode)
        put("action", action.name)
        put("categoryId", categoryId ?: "")
        put("stopProcessing", stopProcessing)
        put("origin", origin)
    }

    companion object {
        fun fromJson(obj: JSONObject): Rule = Rule(
            id = obj.optString("id").ifBlank { UUID.randomUUID().toString() },
            name = obj.optString("name", ""),
            enabled = obj.optBoolean("enabled", true),
            priority = obj.optInt("priority", 0),
            field = runCatching { MatchField.valueOf(obj.optString("field", "BODY")) }
                .getOrDefault(MatchField.BODY),
            type = runCatching { MatchType.valueOf(obj.optString("type", "CONTAINS")) }
                .getOrDefault(MatchType.CONTAINS),
            pattern = obj.optString("pattern", ""),
            caseSensitive = obj.optBoolean("caseSensitive", false),
            normalizeUnicode = obj.optBoolean("normalizeUnicode", true),
            action = runCatching { RuleAction.valueOf(obj.optString("action", "BLOCK")) }
                .getOrDefault(RuleAction.BLOCK),
            categoryId = obj.optString("categoryId", "").ifBlank { null },
            stopProcessing = obj.optBoolean("stopProcessing", true),
            origin = obj.optString("origin", "user")
        )
    }
}

/** Outcome of evaluating a message against the rule list. */
data class DetectionResult(
    val isSpam: Boolean = false,
    /** Rule that produced the effective verdict, if any. */
    val matchedRule: Rule? = null,
    /** Human-readable matched token/pattern, used for the inbox badge. */
    val matchedText: String? = null,
    val matchedRuleName: String? = null,
    /** Category id contributed by a TAG rule. */
    val tagCategoryId: String? = null,
    /** Message should be delivered/logged but without a notification. */
    val muted: Boolean = false,
    /** Time budget was exhausted while evaluating rules. */
    val budgetExceeded: Boolean = false
) {
    companion object {
        val NONE = DetectionResult()
    }
}
