package com.heimdall.app.util

import com.heimdall.app.data.SpamRule

data class CompiledSpamRule(
    val rule: SpamRule,
    val regex: Regex? = null,
    val lowercaseLiteral: String = ""
)

object SpamRuleHelper {
    private val INVISIBLE_CHAR_REGEX = Regex("[\\p{Cf}\\u200B-\\u200D\\uFEFF]")
    private val REGEX_META_CHARS = setOf('[', ']', '(', ')', '*', '+', '?', '{', '}', '\\', '|', '^', '$')

    fun stripInvisibleChars(text: String): String {
        return text.replace(INVISIBLE_CHAR_REGEX, "")
    }

    /**
     * Checks whether [input] contains regex syntax (excluding lone '.') and compiles cleanly.
     */
    fun looksLikeRegex(input: String): Boolean {
        if (!input.any { it in REGEX_META_CHARS }) return false
        return compileRegex(input) != null
    }

    fun compileRegex(pattern: String): Regex? {
        return try {
            Regex(pattern, RegexOption.IGNORE_CASE)
        } catch (_: Exception) {
            null
        }
    }

    fun compileRules(rules: List<SpamRule>): List<CompiledSpamRule> {
        return rules.mapNotNull { rule ->
            if (rule.isRegex) {
                val regex = compileRegex(rule.pattern) ?: return@mapNotNull null
                CompiledSpamRule(rule = rule, regex = regex)
            } else {
                CompiledSpamRule(rule = rule, lowercaseLiteral = rule.pattern.lowercase())
            }
        }
    }

    /**
     * Evaluates pre-compiled rules against a message body (after stripping invisible evasion chars).
     * Returns the first matching [SpamRule], or null if clean.
     */
    fun findFirstMatch(
        strippedBody: String,
        lowerStrippedBody: String,
        compiledRules: List<CompiledSpamRule>
    ): SpamRule? {
        for (compiled in compiledRules) {
            val matched = if (compiled.rule.isRegex) {
                compiled.regex?.containsMatchIn(strippedBody) == true
            } else {
                lowerStrippedBody.contains(compiled.lowercaseLiteral)
            }
            if (matched) return compiled.rule
        }
        return null
    }
}
