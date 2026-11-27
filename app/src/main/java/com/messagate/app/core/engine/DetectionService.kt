package com.messagate.app.core.engine

import android.content.Context
import com.messagate.app.util.MessageCategory
import com.messagate.app.util.CategoryHelper

/**
 * Facade used by the receiver, inbox import and re-evaluation so every path
 * classifies a message identically.
 */
class DetectionService(context: Context) {

    private val repository = RulesRepository(context)

    init {
        RuleMigration.ensureMigrated(context)
    }

    fun rules(): List<Rule> = repository.getRules()

    fun detect(sender: String, body: String): DetectionResult =
        RuleEngine.evaluateCompiled(repository.compiledRules(), sender, body)

    fun detectCategory(
        sender: String,
        body: String,
        isSpam: Boolean,
        tagCategoryId: String?
    ): MessageCategory =
        CategoryHelper.detectCategory(sender, body, isSpam, tagCategoryId)
}
