package com.heimdall.app.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

data class InspectedMessage(
    val timestamp: Long,
    val sender: String,
    val body: String,
    val isSpam: Boolean,
    val matchedKeyword: String?
)

class PreferencesManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("heimdall_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_SHIELD_ENABLED = "shield_enabled"
        private const val KEY_KEYWORDS = "filtered_keywords"
        private const val KEY_BLOCKED_COUNT = "blocked_count"
        private const val KEY_INSPECTED_LOGS = "inspected_logs"

        val DEFAULT_KEYWORDS = setOf(
            "loan",
            "winner",
            "offer",
            "crypto",
            "kyc",
            "lottery",
            "rummy",
            "bonus"
        )
    }

    fun isShieldEnabled(): Boolean {
        return prefs.getBoolean(KEY_SHIELD_ENABLED, true)
    }

    fun setShieldEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SHIELD_ENABLED, enabled).apply()
    }

    fun getKeywords(): Set<String> {
        return prefs.getStringSet(KEY_KEYWORDS, DEFAULT_KEYWORDS) ?: DEFAULT_KEYWORDS
    }

    fun addKeyword(keyword: String): Boolean {
        val trimmed = keyword.trim().lowercase()
        if (trimmed.isEmpty()) return false
        val current = getKeywords().toMutableSet()
        val added = current.add(trimmed)
        if (added) {
            prefs.edit().putStringSet(KEY_KEYWORDS, current).apply()
        }
        return added
    }

    fun removeKeyword(keyword: String): Boolean {
        val current = getKeywords().toMutableSet()
        val removed = current.remove(keyword.trim().lowercase())
        if (removed) {
            prefs.edit().putStringSet(KEY_KEYWORDS, current).apply()
        }
        return removed
    }

    fun getBlockedCount(): Int {
        return prefs.getInt(KEY_BLOCKED_COUNT, 0)
    }

    fun incrementBlockedCount() {
        val current = getBlockedCount()
        prefs.edit().putInt(KEY_BLOCKED_COUNT, current + 1).apply()
    }

    fun addInspectedMessage(message: InspectedMessage) {
        val currentList = getInspectedMessages().toMutableList()
        currentList.add(0, message) // Newest first
        val trimmedList = if (currentList.size > 100) currentList.take(100) else currentList
        saveMessagesList(trimmedList)
    }

    fun deleteMessage(timestamp: Long): Boolean {
        val currentList = getInspectedMessages().toMutableList()
        val removed = currentList.removeAll { it.timestamp == timestamp }
        if (removed) {
            saveMessagesList(currentList)
        }
        return removed
    }

    fun deleteAllSpam(): Int {
        val currentList = getInspectedMessages()
        val spamCount = currentList.count { it.isSpam }
        val cleanList = currentList.filter { !it.isSpam }
        saveMessagesList(cleanList)
        return spamCount
    }

    private fun saveMessagesList(list: List<InspectedMessage>) {
        val jsonArray = JSONArray()
        for (item in list) {
            val obj = JSONObject().apply {
                put("timestamp", item.timestamp)
                put("sender", item.sender)
                put("body", item.body)
                put("isSpam", item.isSpam)
                put("matchedKeyword", item.matchedKeyword ?: "")
            }
            jsonArray.put(obj)
        }
        prefs.edit().putString(KEY_INSPECTED_LOGS, jsonArray.toString()).apply()
    }

    fun getInspectedMessages(): List<InspectedMessage> {
        val raw = prefs.getString(KEY_INSPECTED_LOGS, null) ?: return emptyList()
        val result = mutableListOf<InspectedMessage>()
        try {
            val jsonArray = JSONArray(raw)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val keyword = obj.optString("matchedKeyword")
                result.add(
                    InspectedMessage(
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                        sender = obj.optString("sender", "Unknown"),
                        body = obj.optString("body", ""),
                        isSpam = obj.optBoolean("isSpam", false),
                        matchedKeyword = if (keyword.isNullOrEmpty()) null else keyword
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return result
    }

    fun clearInspectedMessages() {
        prefs.edit().remove(KEY_INSPECTED_LOGS).apply()
    }
}
