package com.messagate.app.data

import android.content.Context

/**
 * Renames the SharedPreferences files from the old "heimdall_*" names to the
 * new "messagate_*" names in place, so an existing install keeps its message
 * log and rule list after the rebrand.
 */
object PrefsMigration {

    @Volatile
    private var done = false

    private const val OLD_PREFS = "heimdall_prefs"
    private const val NEW_PREFS = "messagate_prefs"
    private const val OLD_RULES = "heimdall_rules"
    private const val NEW_RULES = "messagate_rules"

    fun ensure(context: Context) {
        if (done) return
        synchronized(this) {
            if (done) return
            val appContext = context.applicationContext
            migrate(appContext, NEW_PREFS, OLD_PREFS)
            migrate(appContext, NEW_RULES, OLD_RULES)
            done = true
        }
    }

    private fun migrate(context: Context, newName: String, oldName: String) {
        val newPrefs = context.getSharedPreferences(newName, Context.MODE_PRIVATE)
        if (newPrefs.all.isNotEmpty()) return

        val oldPrefs = context.getSharedPreferences(oldName, Context.MODE_PRIVATE)
        val entries = oldPrefs.all
        if (entries.isEmpty()) return

        val editor = newPrefs.edit()
        for ((key, value) in entries) {
            when (value) {
                is Boolean -> editor.putBoolean(key, value)
                is String -> editor.putString(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Float -> editor.putFloat(key, value)
                is Set<*> -> editor.putStringSet(
                    key,
                    value.filterIsInstance<String>().toSet()
                )
            }
        }
        editor.apply()
        oldPrefs.edit().clear().apply()
    }
}
