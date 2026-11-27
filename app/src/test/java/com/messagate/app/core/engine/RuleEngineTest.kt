package com.messagate.app.core.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleEngineTest {

    private fun rule(
        pattern: String,
        type: MatchType = MatchType.CONTAINS,
        field: MatchField = MatchField.BODY,
        action: RuleAction = RuleAction.BLOCK,
        priority: Int = 0,
        caseSensitive: Boolean = false,
        categoryId: String? = null,
        stopProcessing: Boolean = true
    ) = Rule(
        name = pattern,
        pattern = pattern,
        type = type,
        field = field,
        action = action,
        priority = priority,
        caseSensitive = caseSensitive,
        categoryId = categoryId,
        stopProcessing = stopProcessing
    )

    @Test
    fun `contains is case insensitive by default`() {
        val result = RuleEngine.evaluate(
            listOf(rule("loan")),
            "AX-BANK",
            "Get a LOAN approved today"
        )
        assertTrue(result.isSpam)
        assertEquals("loan", result.matchedRule?.pattern)
    }

    @Test
    fun `case sensitive rule does not match different case`() {
        val result = RuleEngine.evaluate(
            listOf(rule("LOAN", caseSensitive = true)),
            "AX-BANK",
            "get a loan approved"
        )
        assertFalse(result.isSpam)
    }

    @Test
    fun `word rule ignores partial matches inside other words`() {
        val partial = RuleEngine.evaluate(
            listOf(rule("loan", type = MatchType.WORD)),
            "SENDER",
            "sloane street"
        )
        assertFalse(partial.isSpam)

        val exactWord = RuleEngine.evaluate(
            listOf(rule("loan", type = MatchType.WORD)),
            "SENDER",
            "your loan is approved"
        )
        assertTrue(exactWord.isSpam)
    }

    @Test
    fun `regex rule matches pattern groups`() {
        val result = RuleEngine.evaluate(
            listOf(rule("\\b(loan|kredit|pinjaman)\\b", type = MatchType.REGEX)),
            "SENDER",
            "PINJAMAN cepat cair"
        )
        assertTrue(result.isSpam)
    }

    @Test
    fun `allow rule wins over block rule regardless of priority`() {
        val rules = listOf(
            rule("bank", priority = 0),
            rule("mybank", field = MatchField.SENDER, action = RuleAction.ALLOW, priority = 999)
        )
        val result = RuleEngine.evaluate(rules, "MYBANK", "your bank otp is 123456")
        assertFalse(result.isSpam)
        assertEquals(RuleAction.ALLOW, result.matchedRule?.action)
    }

    @Test
    fun `sender field matches alpha sender id`() {
        val result = RuleEngine.evaluate(
            listOf(rule("AX-", type = MatchType.PREFIX, field = MatchField.SENDER)),
            "AX-1234",
            "hello"
        )
        assertTrue(result.isSpam)
    }

    @Test
    fun `tag rule assigns category without blocking`() {
        val result = RuleEngine.evaluate(
            listOf(
                rule(
                    "delivered to your",
                    action = RuleAction.TAG,
                    categoryId = "DELIVERY"
                )
            ),
            "SENDER",
            "Order delivered to your address"
        )
        assertFalse(result.isSpam)
        assertEquals("DELIVERY", result.tagCategoryId)
    }

    @Test
    fun `mute rule flags notification suppression`() {
        val result = RuleEngine.evaluate(
            listOf(rule("promo", action = RuleAction.MUTE)),
            "SENDER",
            "PROMO hari ini"
        )
        assertFalse(result.isSpam)
        assertTrue(result.muted)
    }

    @Test
    fun `mute and allow do not use tag category`() {
        val result = RuleEngine.evaluate(
            listOf(rule("promo", action = RuleAction.MUTE)),
            "SENDER",
            "PROMO hari ini"
        )
        assertNull(result.tagCategoryId)
    }

    @Test
    fun `zero width characters are stripped before matching`() {
        val result = RuleEngine.evaluate(
            listOf(rule("loan")),
            "SENDER",
            "l\u200Bo\u200Ba\u200Bn offer"
        )
        assertTrue(result.isSpam)
    }

    @Test
    fun `invalid regex is ignored without crashing`() {
        val result = RuleEngine.evaluate(
            listOf(rule("(unclosed", type = MatchType.REGEX), rule("loan")),
            "SENDER",
            "loan offer"
        )
        assertTrue(result.isSpam)
        assertEquals("loan", result.matchedRule?.pattern)
    }

    @Test
    fun `non stopping tag rule still allows a later block`() {
        val rules = listOf(
            rule("promo", action = RuleAction.TAG, categoryId = "CHAT", priority = 0, stopProcessing = false),
            rule("promo", action = RuleAction.BLOCK, priority = 1)
        )
        val result = RuleEngine.evaluate(rules, "SENDER", "promo murah")
        assertTrue(result.isSpam)
        assertEquals("CHAT", result.tagCategoryId)
    }

    @Test
    fun `prefix suffix and exact matching`() {
        assertTrue(
            RuleEngine.evaluate(listOf(rule("ID-", type = MatchType.PREFIX)), "S", "ID-7788 promo").isSpam
        )
        assertTrue(
            RuleEngine.evaluate(listOf(rule("promo", type = MatchType.SUFFIX)), "S", "big promo").isSpam
        )
        assertFalse(
            RuleEngine.evaluate(listOf(rule("promo", type = MatchType.SUFFIX)), "S", "promo big").isSpam
        )
        assertTrue(
            RuleEngine.evaluate(listOf(rule("stop", type = MatchType.EXACT)), "S", "  stop  ").isSpam
        )
    }

    @Test
    fun `validate rejects empty and malformed patterns`() {
        assertTrue(CompiledRule.validate(rule("")) != null)
        assertTrue(CompiledRule.validate(rule("(unclosed", type = MatchType.REGEX)) != null)
        assertNull(CompiledRule.validate(rule("loan")))
        assertNull(CompiledRule.validate(rule("\\b(loan|kredit)\\b", type = MatchType.REGEX)))
    }
}
