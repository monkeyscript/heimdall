package com.messagate.app.core.engine

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.messagate.app.data.PrefsMigration
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persistence for the rule engine. Rules live in their own SharedPreferences
 * file (not inside messagate_prefs) so that a corrupted rule payload can never
 * take the message log with it.
 *
 * Process-wide compiled cache: the SMS receiver creates a fresh repository on
 * every broadcast, so compilation results are cached by JSON string.
 */
class RulesRepository(context: Context) {

    private val prefs: SharedPreferences

    init {
        PrefsMigration.ensure(context.applicationContext)
        prefs = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getRules(): List<Rule> {
        val raw = prefs.getString(KEY_RULES, null)
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val array = JSONObject(raw).optJSONArray("rules") ?: JSONArray()
            (0 until array.length()).mapNotNull { i ->
                array.optJSONObject(i)?.let { obj ->
                    runCatching { Rule.fromJson(obj) }.getOrNull()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read rules, falling back to empty list", e)
            emptyList()
        }
    }

    fun saveRules(rules: List<Rule>) {
        val array = JSONArray()
        rules.forEach { array.put(it.toJson()) }
        val payload = JSONObject().apply {
            put("schema", SCHEMA_VERSION)
            put("rules", array)
        }.toString()
        prefs.edit().putString(KEY_RULES, payload).apply()
        synchronized(CACHE_LOCK) {
            cachedJson = payload
            cachedCompiled = RuleEngine.compile(rules)
        }
    }

    fun compiledRules(): List<CompiledRule> {
        val raw = prefs.getString(KEY_RULES, null) ?: return emptyList()
        synchronized(CACHE_LOCK) {
            if (raw == cachedJson && cachedCompiled != null) return cachedCompiled!!
        }
        val compiled = RuleEngine.compile(getRules())
        synchronized(CACHE_LOCK) {
            cachedJson = raw
            cachedCompiled = compiled
        }
        return compiled
    }

    /** True when the rule payload has never been written. */
    fun isUninitialized(): Boolean = !prefs.contains(KEY_RULES)

    companion object {
        private const val TAG = "MessagateRules"
        private const val PREFS_NAME = "messagate_rules"
        private const val KEY_RULES = "rules_v1"
        private const val SCHEMA_VERSION = 1

        private val CACHE_LOCK = Any()

        @Volatile
        private var cachedJson: String? = null

        @Volatile
        private var cachedCompiled: List<CompiledRule>? = null
    }
}
