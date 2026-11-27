package com.messagate.app.data

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.mutableLongStateOf
import com.messagate.app.core.engine.DetectionResult
import com.messagate.app.core.engine.Rule
import com.messagate.app.core.engine.RuleEngine
import com.messagate.app.core.engine.RuleMigration
import com.messagate.app.core.engine.RulesRepository
import com.messagate.app.util.CategoryHelper
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
    val category: String = "CHAT",
    val matchedRuleName: String? = null
)

class PreferencesManager(context: Context) {
    private val appContext = context.applicationContext
    private val prefs: SharedPreferences
    private val rulesRepository: RulesRepository

    init {
        PrefsMigration.ensure(appContext)
        prefs = appContext.getSharedPreferences("messagate_prefs", Context.MODE_PRIVATE)
        rulesRepository = RulesRepository(appContext)
        RuleMigration.ensureMigrated(appContext)
    }

    companion object {
        val messagesVersion = mutableLongStateOf(0L)

        // Process-wide in-memory cache shared across all PreferencesManager instances
        // (SmsReceiver, MainActivity, CopyOtpReceiver) so writes are immediately visible.
        @Volatile
        private var memoryMessagesCache: MutableList<InspectedMessage>? = null
        private val cacheLock = Any()

        private const val KEY_MASTER_ACTIVE = "master_active"
        private const val KEY_FILTER_ENABLED = "filter_enabled"
        private const val KEY_KEYWORDS = "filtered_keywords"
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

    // Sub-toggle for Spam Filters
    fun isFilterEnabled(): Boolean {
        return prefs.getBoolean(KEY_FILTER_ENABLED, true)
    }

    fun setFilterEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_FILTER_ENABLED, enabled).apply()
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

    // ------------------------------------------------------------------
    // Detection rules (ordered engine)
    // ------------------------------------------------------------------

    fun getRules(): List<Rule> {
        RuleMigration.ensureMigrated(appContext)
        return rulesRepository.getRules().sortedBy { it.priority }
    }

    fun saveRules(rules: List<Rule>) {
        rulesRepository.saveRules(rules)
        triggerReevaluation(150L)
    }

    // ------------------------------------------------------------------
    // Backup / restore (rules + prefs, never messages)
    // ------------------------------------------------------------------

    fun exportBackupJson(): String {
        val settings = JSONObject().apply {
            put("masterActive", isMasterActive())
            put("filterEnabled", isFilterEnabled())
            put("showSpamInFeed", isShowSpamInFeed())
        }
        val rulesArray = JSONArray()
        getRules().sortedBy { it.priority }.forEach { rulesArray.put(it.toJson()) }

        return JSONObject().apply {
            put("app", "messagate")
            put("schema", 1)
            put("exportedAt", System.currentTimeMillis())
            put("settings", settings)
            put("rules", rulesArray)
        }.toString(2)
    }

    data class BackupImportResult(
        val success: Boolean,
        val message: String,
        val ruleCount: Int = 0
    )

    fun importBackupJson(raw: String): BackupImportResult {
        return try {
            val root = JSONObject(raw)
            val app = root.optString("app", "")
            if (app.isNotBlank() && !app.equals("messagate", ignoreCase = true)) {
                return BackupImportResult(false, "Not a Messagate backup file")
            }
            val rulesArray = root.optJSONArray("rules")
                ?: return BackupImportResult(false, "Backup has no rules section")

            root.optJSONObject("settings")?.let { settings ->
                if (settings.has("masterActive")) {
                    setMasterActive(settings.optBoolean("masterActive", isMasterActive()))
                }
                if (settings.has("filterEnabled")) {
                    setFilterEnabled(settings.optBoolean("filterEnabled", isFilterEnabled()))
                }
                if (settings.has("showSpamInFeed")) {
                    setShowSpamInFeed(settings.optBoolean("showSpamInFeed", isShowSpamInFeed()))
                }
            }

            val imported = (0 until rulesArray.length()).mapNotNull { index ->
                rulesArray.optJSONObject(index)?.let { obj ->
                    runCatching { Rule.fromJson(obj) }.getOrNull()
                }
            }
            saveRules(
                imported.sortedBy { it.priority }
                    .mapIndexed { index, rule -> rule.copy(priority = index * 10) }
            )
            BackupImportResult(
                success = true,
                message = "Imported ${imported.size} rule(s)",
                ruleCount = imported.size
            )
        } catch (e: Exception) {
            BackupImportResult(false, "Invalid backup: ${e.message ?: "parse error"}")
        }
    }

    /**
     * The detection engine is the single rule surface. Legacy keyword entries
     * are still readable as rules (origin = "keyword") for migration, but new
     * rules are always written by the editor as origin = "user".
     */
    private val reevaluateScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var reevaluateJob: Job? = null

    fun triggerReevaluation(debounceMs: Long = 150L) {
        reevaluateJob?.cancel()
        reevaluateJob = reevaluateScope.launch {
            if (debounceMs > 0) {
                delay(debounceMs)
            }
            reevaluateMessages()
        }
    }

    fun reevaluateMessages() {
        synchronized(cacheLock) {
            val list = getInspectedMessagesInternal().toMutableList()
            var changed = false
            val isFilterOn = isFilterEnabled() && isMasterActive()
            val compiled = if (isFilterOn) rulesRepository.compiledRules() else emptyList()

            for (i in list.indices) {
                val msg = list[i]
                val detection: DetectionResult = if (isFilterOn) {
                    RuleEngine.evaluateCompiled(compiled, msg.sender, msg.body)
                } else {
                    DetectionResult.NONE
                }
                val isSpam = detection.isSpam
                val newCategory = CategoryHelper.detectCategory(
                    msg.sender,
                    msg.body,
                    isSpam,
                    detection.tagCategoryId
                ).name

                if (msg.isSpam != isSpam ||
                    msg.matchedKeyword != detection.matchedText ||
                    msg.matchedRuleName != detection.matchedRuleName ||
                    msg.category != newCategory
                ) {
                    list[i] = msg.copy(
                        isSpam = isSpam,
                        matchedKeyword = detection.matchedText,
                        matchedRuleName = detection.matchedRuleName,
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
                    val matchedRule = obj.optString("matchedRule", "")

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
                            category = category,
                            matchedRuleName = if (matchedRule.isNullOrEmpty()) null else matchedRule
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
                        put("matchedRule", item.matchedRuleName ?: "")
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
