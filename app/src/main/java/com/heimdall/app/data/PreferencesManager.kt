package com.heimdall.app.data

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.mutableLongStateOf
import com.heimdall.app.util.CategoryHelper
import com.heimdall.app.util.SpamRuleHelper
import kotlinx.coroutines.*
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

data class SpamRule(
    val pattern: String,
    val isRegex: Boolean = false
)

class PreferencesManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("heimdall_prefs", Context.MODE_PRIVATE)

    companion object {
        val messagesVersion = mutableLongStateOf(0L)

        // Process-wide in-memory cache shared across all PreferencesManager instances
        // (SmsReceiver, MainActivity, CopyOtpReceiver) so writes are immediately visible.
        @Volatile
        private var memoryMessagesCache: MutableList<InspectedMessage>? = null
        private val cacheLock = Any()

        private const val KEY_MASTER_ACTIVE = "master_active"
        private const val KEY_KEYWORDS = "filtered_keywords"
        private const val KEY_REGEX_KEYWORDS = "filtered_regex_keywords"
        private const val KEY_BLOCKED_COUNT = "blocked_count"
        private const val KEY_INSPECTED_LOGS = "inspected_logs"
        private const val KEY_LAST_SPAM_CLEANUP = "last_spam_cleanup"
        private const val KEY_SHOW_SPAM_IN_FEED = "show_spam_in_feed"

        const val MAX_STORED_MESSAGES = 10000

        val DEFAULT_KEYWORDS: Set<String> = emptySet()
    }

    // Master Switch
    fun isMasterActive(): Boolean {
        return prefs.getBoolean(KEY_MASTER_ACTIVE, true)
    }

    fun setMasterActive(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_MASTER_ACTIVE, enabled).apply()
    }

    // Show/Hide Spam in Main Feed
    fun isShowSpamInFeed(): Boolean {
        return prefs.getBoolean(KEY_SHOW_SPAM_IN_FEED, false)
    }

    fun setShowSpamInFeed(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_SPAM_IN_FEED, enabled).apply()
    }

    fun isShieldEnabled(): Boolean = isMasterActive()
    fun setShieldEnabled(enabled: Boolean) = setMasterActive(enabled)

    fun getKeywords(): Set<String> {
        return prefs.getStringSet(KEY_KEYWORDS, null) ?: emptySet()
    }

    fun getRegexKeywords(): Set<String> {
        return prefs.getStringSet(KEY_REGEX_KEYWORDS, null) ?: emptySet()
    }

    fun getSpamRules(): List<SpamRule> {
        val normal = getKeywords().map { SpamRule(pattern = it, isRegex = false) }
        val regex = getRegexKeywords().map { SpamRule(pattern = it, isRegex = true) }
        return (normal + regex).sortedBy { it.pattern.lowercase() }
    }

    private val reevaluateScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var reevaluateJob: Job? = null

    fun addKeyword(keyword: String): Boolean = addSpamRule(pattern = keyword, isRegex = false)

    fun removeKeyword(keyword: String): Boolean = removeSpamRule(SpamRule(pattern = keyword, isRegex = false))

    fun addSpamRule(pattern: String, isRegex: Boolean): Boolean {
        val normalized = if (isRegex) pattern.trim() else pattern.trim().lowercase()
        if (normalized.isEmpty()) return false
        if (isRegex && SpamRuleHelper.compileRegex(normalized) == null) return false

        val key = if (isRegex) KEY_REGEX_KEYWORDS else KEY_KEYWORDS
        val current = HashSet(prefs.getStringSet(key, null) ?: emptySet())
        val added = current.add(normalized)
        if (added) {
            prefs.edit().putStringSet(key, current).apply()
            triggerReevaluation(150L)
        }
        return added
    }

    fun removeSpamRule(rule: SpamRule): Boolean {
        val target = if (rule.isRegex) rule.pattern.trim() else rule.pattern.trim().lowercase()
        val key = if (rule.isRegex) KEY_REGEX_KEYWORDS else KEY_KEYWORDS
        val current = HashSet(prefs.getStringSet(key, null) ?: emptySet())
        val removed = current.remove(target)
        if (removed) {
            prefs.edit().putStringSet(key, current).apply()
            triggerReevaluation(150L)
        }
        return removed
    }

    fun triggerReevaluation(debounceMs: Long = 150L) {
        reevaluateJob?.cancel()
        reevaluateJob = reevaluateScope.launch {
            if (debounceMs > 0) {
                delay(debounceMs)
            }
            reevaluateMessagesWithKeywords()
        }
    }

    fun reevaluateMessagesWithKeywords(activeRules: List<SpamRule> = getSpamRules()) {
        synchronized(cacheLock) {
            val list = getInspectedMessagesInternal().toMutableList()
            var changed = false
            val compiledRules = SpamRuleHelper.compileRules(activeRules)
            val isFilterOn = isMasterActive()

            for (i in list.indices) {
                val msg = list[i]
                val strippedBody = SpamRuleHelper.stripInvisibleChars(msg.body)
                val matched = if (isFilterOn) {
                    SpamRuleHelper.findFirstMatch(strippedBody, strippedBody.lowercase(), compiledRules)?.pattern
                } else {
                    null
                }
                val isSpam = matched != null
                val newCategory = CategoryHelper.detectCategory(msg.sender, msg.body, isSpam).name

                if (msg.isSpam != isSpam || msg.matchedKeyword != matched || msg.category != newCategory) {
                    list[i] = msg.copy(
                        isSpam = isSpam,
                        matchedKeyword = matched,
                        category = newCategory
                    )
                    changed = true
                }
            }

            if (changed) {
                memoryMessagesCache = list
                saveMessagesAsync(list)
                notifyMessagesChanged()
            }
        }
    }

    fun getBlockedCount(): Int {
        return prefs.getInt(KEY_BLOCKED_COUNT, 0)
    }

    fun incrementBlockedCount() {
        val current = getBlockedCount()
        prefs.edit().putInt(KEY_BLOCKED_COUNT, current + 1).apply()
    }

    private fun notifyMessagesChanged() {
        Handler(Looper.getMainLooper()).post {
            messagesVersion.longValue++
        }
    }

    // One-time initial SMS import state
    fun hasImportedInitialSms(): Boolean {
        return prefs.getBoolean("has_imported_initial_sms", false)
    }

    fun setImportedInitialSms(imported: Boolean) {
        prefs.edit().putBoolean("has_imported_initial_sms", imported).apply()
    }

    fun hasImportedFullSms(): Boolean {
        return prefs.getBoolean("has_imported_full_sms_v2", false)
    }

    fun setImportedFullSms(imported: Boolean) {
        prefs.edit().putBoolean("has_imported_full_sms_v2", imported).apply()
    }

    fun importInitialMessages(imported: List<InspectedMessage>) {
        synchronized(cacheLock) {
            val current = getInspectedMessagesInternal()
            val currentTimestamps = current.map { it.timestamp }.toSet()
            val newToAdd = imported.filter { it.timestamp !in currentTimestamps }.map { it.copy(isRead = true) }
            val combined = (current + newToAdd).sortedByDescending { it.timestamp }
            val trimmed = if (combined.size > MAX_STORED_MESSAGES) combined.take(MAX_STORED_MESSAGES).toMutableList() else combined.toMutableList()
            memoryMessagesCache = trimmed
            saveMessagesAsync(trimmed)
            notifyMessagesChanged()
        }
    }

    // High-performance in-memory add with async background persistence
    fun addInspectedMessage(message: InspectedMessage) {
        synchronized(cacheLock) {
            val list = getInspectedMessagesInternal().toMutableList()
            list.add(0, message)
            val trimmedList = if (list.size > MAX_STORED_MESSAGES) list.take(MAX_STORED_MESSAGES).toMutableList() else list
            memoryMessagesCache = trimmedList
            saveMessagesAsync(trimmedList)
            notifyMessagesChanged()
        }
    }

    fun markMessageAsRead(timestamp: Long): Boolean {
        synchronized(cacheLock) {
            val list = getInspectedMessagesInternal().toMutableList()
            val idx = list.indexOfFirst { it.timestamp == timestamp }
            if (idx != -1 && !list[idx].isRead) {
                list[idx] = list[idx].copy(isRead = true)
                memoryMessagesCache = list
                saveMessagesAsync(list)
                notifyMessagesChanged()
                return true
            }
            return false
        }
    }

    fun markAllAsRead(): Int {
        synchronized(cacheLock) {
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
                notifyMessagesChanged()
            }
            return count
        }
    }

    fun getMessageByTimestamp(timestamp: Long): InspectedMessage? {
        return getInspectedMessages().firstOrNull { it.timestamp == timestamp }
    }

    fun deleteMessage(timestamp: Long): Boolean {
        synchronized(cacheLock) {
            val list = getInspectedMessagesInternal().toMutableList()
            val removed = list.removeAll { it.timestamp == timestamp }
            if (removed) {
                memoryMessagesCache = list
                saveMessagesAsync(list)
                notifyMessagesChanged()
            }
            return removed
        }
    }

    fun deleteMessages(timestamps: Set<Long>): Int {
        if (timestamps.isEmpty()) return 0
        synchronized(cacheLock) {
            val list = getInspectedMessagesInternal().toMutableList()
            val initialSize = list.size
            list.removeAll { it.timestamp in timestamps }
            val removedCount = initialSize - list.size
            if (removedCount > 0) {
                memoryMessagesCache = list
                saveMessagesAsync(list)
                notifyMessagesChanged()
            }
            return removedCount
        }
    }

    fun deleteAllSpam(): Int {
        synchronized(cacheLock) {
            val list = getInspectedMessagesInternal()
            val spamCount = list.count { it.isSpam }
            val cleanList = list.filter { !it.isSpam }.toMutableList()
            memoryMessagesCache = cleanList
            saveMessagesAsync(cleanList)
            notifyMessagesChanged()
            return spamCount
        }
    }

    fun cleanupOldSpam(
        maxAgeMillis: Long = 30L * 24 * 60 * 60 * 1000L,
        force: Boolean = false
    ): Int {
        synchronized(cacheLock) {
            val now = System.currentTimeMillis()
            val lastCleanup = prefs.getLong(KEY_LAST_SPAM_CLEANUP, 0L)
            if (!force && (now - lastCleanup < 24L * 60 * 60 * 1000L)) {
                return 0
            }

            prefs.edit().putLong(KEY_LAST_SPAM_CLEANUP, now).apply()

            val cutoff = now - maxAgeMillis
            val list = getInspectedMessagesInternal()
            val initialSize = list.size
            val filtered = list.filterNot { it.isSpam && it.timestamp < cutoff }.toMutableList()
            val deleted = initialSize - filtered.size
            if (deleted > 0) {
                memoryMessagesCache = filtered
                saveMessagesAsync(filtered)
                notifyMessagesChanged()
            }
            return deleted
        }
    }

    // Instant O(1) in-memory retrieval
    fun getInspectedMessages(): List<InspectedMessage> {
        return synchronized(cacheLock) {
            getInspectedMessagesInternal().toList()
        }
    }

    private fun getInspectedMessagesInternal(): MutableList<InspectedMessage> {
        // Caller must hold cacheLock
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

    private val saveScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private fun saveMessagesAsync(list: List<InspectedMessage>) {
        val snapshot = list.toList()
        saveScope.launch {
            try {
                val jsonArray = JSONArray()
                for (item in snapshot) {
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
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun clearInspectedMessages() {
        synchronized(cacheLock) {
            memoryMessagesCache = mutableListOf()
            prefs.edit().remove(KEY_INSPECTED_LOGS).apply()
        }
    }
}
