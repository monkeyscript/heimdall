package com.messagate.app.core.engine

import com.messagate.app.util.TextNormalizer

/**
 * Deterministic, ordered rule evaluation.
 *
 * Semantics (also shown to the user in the editor):
 *  1. ALLOW rules run in a first pass, in priority order. A match means clean.
 *  2. Remaining rules run in priority order. First BLOCK wins unless the rule
 *     has stopProcessing = false. TAG only assigns a category.
 *
 * The engine is synchronous and allocation-light because it runs inside the
 * SMS broadcast receiver. A time budget bounds the total work so one
 * pathological regex cannot stall delivery of the message.
 */
object RuleEngine {

    const val DEFAULT_TIME_BUDGET_MS = 40L
    private const val NANOS_PER_MS = 1_000_000L

    fun compile(rules: List<Rule>): List<CompiledRule> =
        rules.asSequence()
            .filter { it.enabled }
            .sortedBy { it.priority }
            .mapNotNull { CompiledRule.compileSafely(it) }
            .toList()

    fun evaluate(
        rules: List<Rule>,
        sender: String,
        rawBody: String,
        budgetMs: Long = DEFAULT_TIME_BUDGET_MS
    ): DetectionResult = evaluateCompiled(compile(rules), sender, rawBody, budgetMs)

    fun evaluateCompiled(
        compiled: List<CompiledRule>,
        sender: String,
        rawBody: String,
        budgetMs: Long = DEFAULT_TIME_BUDGET_MS
    ): DetectionResult {
        if (compiled.isEmpty()) return DetectionResult.NONE

        val body = TextNormalizer.normalizeBody(rawBody)
        val normalizedSender = TextNormalizer.stripInvisible(sender)
        val deadline = System.nanoTime() + budgetMs * NANOS_PER_MS

        // Pass 1: whitelist. A trusted sender can never be blocked.
        for (entry in compiled) {
            if (entry.rule.action != RuleAction.ALLOW) continue
            if (entry.matches(normalizedSender, body)) {
                return DetectionResult(
                    isSpam = false,
                    matchedRule = entry.rule,
                    matchedText = entry.rule.pattern,
                    matchedRuleName = entry.rule.name.ifBlank { entry.rule.pattern }
                )
            }
            if (System.nanoTime() > deadline) {
                return DetectionResult(budgetExceeded = true)
            }
        }

        var blocked: Rule? = null
        var blockedText: String? = null
        var muted: Boolean = false
        var tagCategoryId: String? = null
        var budgetExceeded = false

        for (entry in compiled) {
            val rule = entry.rule
            if (rule.action == RuleAction.ALLOW) continue

            val matched = try {
                entry.matches(normalizedSender, body)
            } catch (e: StackOverflowError) {
                false
            } catch (e: Exception) {
                false
            }

            if (matched) {
                when (rule.action) {
                    RuleAction.BLOCK -> if (blocked == null) {
                        blocked = rule
                        blockedText = rule.pattern
                    }
                    RuleAction.MUTE -> muted = true
                    RuleAction.TAG -> if (tagCategoryId == null) {
                        tagCategoryId = rule.categoryId
                    }
                    RuleAction.ALLOW -> Unit
                }
                if (rule.stopProcessing) break
            }

            if (System.nanoTime() > deadline) {
                budgetExceeded = true
                break
            }
        }

        val effective = blocked
        return DetectionResult(
            isSpam = effective != null,
            matchedRule = effective,
            matchedText = blockedText,
            matchedRuleName = effective?.let { it.name.ifBlank { it.pattern } },
            tagCategoryId = tagCategoryId,
            muted = muted,
            budgetExceeded = budgetExceeded
        )
    }

    /** Single-rule check used by the editor's test bench. */
    fun testRule(rule: Rule, sender: String, body: String): Boolean {
        val compiled = CompiledRule.compileSafely(rule) ?: return false
        return try {
            compiled.matches(
                TextNormalizer.stripInvisible(sender),
                TextNormalizer.normalizeBody(body)
            )
        } catch (e: StackOverflowError) {
            false
        } catch (e: Exception) {
            false
        }
    }
}
