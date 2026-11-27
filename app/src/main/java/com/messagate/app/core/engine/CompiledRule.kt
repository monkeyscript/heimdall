package com.messagate.app.core.engine

import com.messagate.app.util.TextNormalizer
import java.util.Locale

/**
 * A [Rule] with its pattern compiled once. Creating this object throws
 * [java.util.regex.PatternSyntaxException] for an invalid REGEX pattern;
 * callers must validate in the editor and use [compileSafely] on hot paths.
 */
class CompiledRule(val rule: Rule) {

    private val regex: Regex?
    private val literal: String?

    init {
        require(rule.pattern.length <= MAX_PATTERN_LENGTH) {
            "Pattern longer than $MAX_PATTERN_LENGTH characters"
        }
        if (rule.type == MatchType.REGEX || rule.type == MatchType.WORD) {
            val options = if (rule.caseSensitive) emptySet() else setOf(RegexOption.IGNORE_CASE)
            regex = when (rule.type) {
                MatchType.REGEX -> Regex(rule.pattern, options)
                else -> Regex(
                    "(?<![\\p{L}\\p{N}_])" + Regex.escape(rule.pattern) + "(?![\\p{L}\\p{N}_])",
                    options
                )
            }
            literal = null
        } else {
            regex = null
            literal = if (rule.caseSensitive) rule.pattern else TextNormalizer.lower(rule.pattern)
        }
    }

    fun matches(sender: String, body: String): Boolean = when (rule.field) {
        MatchField.BODY -> matchesText(body)
        MatchField.SENDER -> matchesText(sender)
        MatchField.BOTH -> matchesText(body) || matchesText(sender)
    }

    private fun matchesText(raw: String): Boolean {
        val text = if (rule.normalizeUnicode) TextNormalizer.stripInvisible(raw) else raw
        val re = regex
        if (re != null) return re.containsMatchIn(text)

        val needle = literal ?: return false
        val haystack = if (rule.caseSensitive) text else TextNormalizer.lower(text)
        return when (rule.type) {
            MatchType.CONTAINS -> haystack.contains(needle)
            MatchType.PREFIX -> haystack.startsWith(needle)
            MatchType.SUFFIX -> haystack.endsWith(needle)
            MatchType.EXACT -> haystack.trim() == needle.trim()
            else -> false
        }
    }

    companion object {
        const val MAX_PATTERN_LENGTH = 256

        fun compileSafely(rule: Rule): CompiledRule? =
            try {
                if (rule.pattern.isBlank()) null else CompiledRule(rule)
            } catch (e: Exception) {
                null
            } catch (e: StackOverflowError) {
                null
            }

        /** Validates a rule pattern and returns an error message, or null when usable. */
        fun validate(rule: Rule): String? {
            if (rule.pattern.isBlank()) return "Pattern is empty"
            if (rule.pattern.length > MAX_PATTERN_LENGTH) {
                return "Pattern exceeds $MAX_PATTERN_LENGTH characters"
            }
            if (rule.type == MatchType.REGEX || rule.type == MatchType.WORD) {
                return try {
                    val options = if (rule.caseSensitive) emptySet() else setOf(RegexOption.IGNORE_CASE)
                    Regex(rule.pattern, options)
                    null
                } catch (e: Exception) {
                    e.message ?: "Invalid regular expression"
                }
            }
            return null
        }
    }
}

/** Lower-case literal used by the tests and the "explain" view. */
internal fun Rule.previewPattern(): String =
    if (caseSensitive) pattern else pattern.lowercase(Locale.ROOT)
