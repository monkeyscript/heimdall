package com.messagate.app.core.engine

import android.content.Context

/**
 * One-time conversion of the legacy `filtered_keywords` list into ordered BLOCK
 * rules. Runs from both the receiver and the UI so an upgrading user never has
 * a window where their keyword list is ignored.
 *
 * The rules payload is written even when the legacy list is empty, which marks
 * the engine as initialized.
 */
object RuleMigration {

    @Volatile
    private var checked = false

    fun ensureMigrated(context: Context) {
        if (checked) return
        synchronized(this) {
            if (checked) return
            val repository = RulesRepository(context)
            if (!repository.isUninitialized()) {
                checked = true
                return
            }
            val legacyKeywords = context
                .getSharedPreferences("messagate_prefs", Context.MODE_PRIVATE)
                .getStringSet("filtered_keywords", null)
                .orEmpty()

            val migrated = legacyKeywords
                .filter { it.isNotBlank() }
                .sorted()
                .mapIndexed { index, keyword ->
                    Rule(
                        name = keyword,
                        pattern = keyword,
                        priority = index * 10,
                        action = RuleAction.BLOCK,
                        origin = "keyword"
                    )
                }
            repository.saveRules(migrated)
            checked = true
        }
    }
}
