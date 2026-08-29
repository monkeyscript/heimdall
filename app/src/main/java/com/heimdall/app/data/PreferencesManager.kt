package com.heimdall.app.data

import android.content.Context
import android.content.SharedPreferences
import com.heimdall.app.util.CategoryHelper
import org.json.JSONArray
import org.json.JSONObject

data class InspectedMessage(
    val timestamp: Long,
    val sender: String,
    val body: String,
    val isSpam: Boolean,
    val matchedKeyword: String?,
    val isRead: Boolean = false,
    val category: String = "CHAT"
)

class PreferencesManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("heimdall_prefs", Context.MODE_PRIVATE)

    // Thread-safe in-memory cache to eliminate UI thread JSON parsing and disk hits
    @Volatile
    private var memoryMessagesCache: MutableList<InspectedMessage>? = null

    companion object {
        private const val KEY_MASTER_ACTIVE = "master_active"
        private const val KEY_FILTER_ENABLED = "filter_enabled"
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

    // Master Switch
    fun isMasterActive(): Boolean {
        return prefs.getBoolean(KEY_MASTER_ACTIVE, true)
    }

    fun setMasterActive(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_MASTER_ACTIVE, enabled).apply()
    }

    // Sub-toggle for Spam Filters
    fun isFilterEnabled(): Boolean {
        return prefs.getBoolean(KEY_FILTER_ENABLED, true)
    }

    fun setFilterEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_FILTER_ENABLED, enabled).apply()
    }

    fun isShieldEnabled(): Boolean = isMasterActive()
    fun setShieldEnabled(enabled: Boolean) = setMasterActive(enabled)

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

    // High-performance in-memory add with async background persistence
    @Synchronized
    fun addInspectedMessage(message: InspectedMessage) {
        val list = getInspectedMessagesInternal().toMutableList()
        list.add(0, message)
        val trimmedList = if (list.size > 100) list.take(100).toMutableList() else list
        memoryMessagesCache = trimmedList
        saveMessagesAsync(trimmedList)
    }

    @Synchronized
    fun markMessageAsRead(timestamp: Long): Boolean {
        val list = getInspectedMessagesInternal().toMutableList()
        val idx = list.indexOfFirst { it.timestamp == timestamp }
        if (idx != -1 && !list[idx].isRead) {
            list[idx] = list[idx].copy(isRead = true)
            memoryMessagesCache = list
            saveMessagesAsync(list)
            return true
        }
        return false
    }

    @Synchronized
    fun markAllAsRead(): Int {
        val list = getInspectedMessagesInternal().toMutableList()
        var count = 0
        for (i in list.indices) {
            if (!list[i].isRead) {
                list[i] = list[i].copy(isRead = true)
                count++
            }
        }
        if (count > 0) {
            memoryMessagesCache = list
            saveMessagesAsync(list)
        }
        return count
    }

    fun getMessageByTimestamp(timestamp: Long): InspectedMessage? {
        return getInspectedMessages().firstOrNull { it.timestamp == timestamp }
    }

    @Synchronized
    fun deleteMessage(timestamp: Long): Boolean {
        val list = getInspectedMessagesInternal().toMutableList()
        val removed = list.removeAll { it.timestamp == timestamp }
        if (removed) {
            memoryMessagesCache = list
            saveMessagesAsync(list)
        }
        return removed
    }

    @Synchronized
    fun deleteAllSpam(): Int {
        val list = getInspectedMessagesInternal()
        val spamCount = list.count { it.isSpam }
        val cleanList = list.filter { !it.isSpam }.toMutableList()
        memoryMessagesCache = cleanList
        saveMessagesAsync(cleanList)
        return spamCount
    }

    // Instant O(1) in-memory retrieval
    fun getInspectedMessages(): List<InspectedMessage> {
        return getInspectedMessagesInternal()
    }

    @Synchronized
    private fun getInspectedMessagesInternal(): MutableList<InspectedMessage> {
        memoryMessagesCache?.let { return it }

        val raw = prefs.getString(KEY_INSPECTED_LOGS, null)
        val result = mutableListOf<InspectedMessage>()
        if (!raw.isNullOrEmpty()) {
            try {
                val jsonArray = JSONArray(raw)
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val keyword = obj.optString("matchedKeyword")
                    val body = obj.optString("body", "")
                    val sender = obj.optString("sender", "Unknown")
                    val isSpam = obj.optBoolean("isSpam", false)

                    // Backward-compatible category resolution: if missing in old logs, compute once and save
                    val rawCategory = obj.optString("category", "")
                    val category = if (rawCategory.isNotEmpty()) {
                        rawCategory
                    } else {
                        CategoryHelper.detectCategory(sender, body, isSpam).name
                    }

                    result.add(
                        InspectedMessage(
                            timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                            sender = sender,
                            body = body,
                            isSpam = isSpam,
                            matchedKeyword = if (keyword.isNullOrEmpty()) null else keyword,
                            isRead = obj.optBoolean("isRead", false),
                            category = category
                        )
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        memoryMessagesCache = result
        return result
    }

    private fun saveMessagesAsync(list: List<InspectedMessage>) {
        val jsonArray = JSONArray()
        for (item in list) {
            val obj = JSONObject().apply {
                put("timestamp", item.timestamp)
                put("sender", item.sender)
                put("body", item.body)
                put("isSpam", item.isSpam)
                put("matchedKeyword", item.matchedKeyword ?: "")
                put("isRead", item.isRead)
                put("category", item.category)
            }
            jsonArray.put(obj)
        }
        prefs.edit().putString(KEY_INSPECTED_LOGS, jsonArray.toString()).apply()
    }

    @Synchronized
    fun clearInspectedMessages() {
        memoryMessagesCache = mutableListOf()
        prefs.edit().remove(KEY_INSPECTED_LOGS).apply()
    }
}
