package com.messagate.app

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.text.format.DateUtils
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.messagate.app.data.InspectedMessage
import com.messagate.app.data.PreferencesManager
import com.messagate.app.R
import com.messagate.app.core.engine.CompiledRule
import com.messagate.app.core.engine.MatchField
import com.messagate.app.core.engine.MatchType
import com.messagate.app.core.engine.Rule
import com.messagate.app.core.engine.RuleAction
import com.messagate.app.ui.settings.RulesScreen
import com.messagate.app.ui.settings.rememberBackupLaunchers
import com.messagate.app.ui.theme.*
import com.messagate.app.util.CategoryHelper
import com.messagate.app.util.ClickableLinkifiedText
import com.messagate.app.util.MessageCategory
import com.messagate.app.util.NotificationHelper
import com.messagate.app.util.OtpHelper
import com.messagate.app.util.SmsRoleHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

enum class AppScreen {
    INBOX,
    SETTINGS,
    RULES
}

// Static, cached formatters to eliminate garbage collection churn on scroll
private val TIME_FORMAT = SimpleDateFormat("hh:mm a", Locale.getDefault())
private val DATE_FORMAT = SimpleDateFormat("dd MMM", Locale.getDefault())
private val FULL_DATE_FORMAT = SimpleDateFormat("MMM dd, yyyy • hh:mm a", Locale.getDefault())

// Cached graphics objects to prevent GC allocations during LazyColumn scrolling
private val SPAM_TAG_BORDER = androidx.compose.foundation.BorderStroke(1.dp, YellowAccent.copy(alpha = 0.4f))
private val ROW_DIVIDER_COLOR = DarkBorder.copy(alpha = 0.4f)
private val AVATAR_BOX_BORDER = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder)

// Shared destructive palette so every delete action looks identical.
private val DESTRUCTIVE_TEXT = Color(0xFFEF4444)
private val DESTRUCTIVE_FILL = Color(0xFFEF4444).copy(alpha = 0.15f)
private val DESTRUCTIVE_BORDER = androidx.compose.foundation.BorderStroke(
    1.dp,
    Color(0xFFEF4444).copy(alpha = 0.4f)
)

private const val EASTER_EGG_FALLBACK =
    "Fork of Heimdall by Karjok Pangesty"

/**
 * Easter egg: two-line custom toast so the author credit is never truncated by
 * the platform's two-line text-toast limit, plus a short vibration pattern.
 */
private fun showEasterEgg(context: Context) {
    vibrateEasterEgg(context)
    try {
        val density = context.resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()

        val card = GradientDrawable().apply {
            setColor(0xFF1E1E22.toInt())
            cornerRadius = dp(4).toFloat()
            setStroke(dp(1), 0xFFF5B718.toInt())
        }
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(12), dp(18), dp(12))
            background = card
        }
        container.addView(TextView(context).apply {
            text = "Messagate is a forked version of Heimdall"
            setTextColor(0xFFF4F4F5.toInt())
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
        })
        container.addView(TextView(context).apply {
            text = "Forked by Karjok Pangesty"
            setTextColor(0xFFF5B718.toInt())
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(3), 0, 0)
        })

        @Suppress("DEPRECATION")
        Toast(context).apply {
            duration = Toast.LENGTH_LONG
            view = container
        }.show()
    } catch (_: Exception) {
        Toast.makeText(context, EASTER_EGG_FALLBACK, Toast.LENGTH_LONG).show()
    }
}

/** Small double-pulse used by the logo long-press easter egg. */
private fun vibrateEasterEgg(context: Context) {
    try {
        val effect = VibrationEffect.createWaveform(longArrayOf(0, 60, 70, 130), -1)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            manager.defaultVibrator.vibrate(effect)
        } else {
            @Suppress("DEPRECATION")
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            vibrator.vibrate(effect)
        }
    } catch (_: Exception) {
        // Device without a vibrator, or vibration disabled by policy.
    }
}

// Fast O(1) timestamp formatting using Android's native DateUtils without Calendar allocations
fun formatMessageTimestamp(timestamp: Long): String {
    return if (DateUtils.isToday(timestamp)) {
        synchronized(TIME_FORMAT) { TIME_FORMAT.format(Date(timestamp)) }
    } else {
        synchronized(DATE_FORMAT) { DATE_FORMAT.format(Date(timestamp)) }
    }
}

// Stable UI Model with pre-stored category and formatted time
@Immutable
data class UiMessageItem(
    val raw: InspectedMessage,
    val category: MessageCategory,
    val formattedTime: String
)

@Immutable
data class UiMessageGroup(
    val head: UiMessageItem,
    val subMessages: List<UiMessageItem> = emptyList()
) {
    val isStacked: Boolean get() = subMessages.isNotEmpty()
    val totalCount: Int get() = 1 + subMessages.size
    val allTimestamps: List<Long> = listOf(head.raw.timestamp) + subMessages.map { it.raw.timestamp }
    val hasUnread: Boolean get() = !head.raw.isRead || subMessages.any { !it.raw.isRead }
}

fun groupContiguousMessages(items: List<UiMessageItem>): List<UiMessageGroup> {
    if (items.isEmpty()) return emptyList()
    val result = ArrayList<UiMessageGroup>()
    var currentHead: UiMessageItem? = null
    var currentSubs = ArrayList<UiMessageItem>()

    for (item in items) {
        if (currentHead == null) {
            currentHead = item
        } else if (currentHead.raw.sender.trim().equals(item.raw.sender.trim(), ignoreCase = true)) {
            currentSubs.add(item)
        } else {
            result.add(UiMessageGroup(currentHead, currentSubs))
            currentHead = item
            currentSubs = ArrayList()
        }
    }
    if (currentHead != null) {
        result.add(UiMessageGroup(currentHead, currentSubs))
    }
    return result
}

class MainActivity : ComponentActivity() {
    private lateinit var prefsManager: PreferencesManager
    private val notificationTimestamp = mutableStateOf<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefsManager = PreferencesManager(this)
        NotificationHelper.createNotificationChannels(this)
        handleIntent(intent)
        enableEdgeToEdge()
        setContent {
            MessagateTheme {
                MessagateApp(
                    prefsManager = prefsManager,
                    directOpenTimestamp = notificationTimestamp.value,
                    onDirectOpenConsumed = { notificationTimestamp.value = null }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        PreferencesManager.messagesVersion.longValue++
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent != null && intent.hasExtra(NotificationHelper.EXTRA_MESSAGE_TIMESTAMP)) {
            val timestamp = intent.getLongExtra(NotificationHelper.EXTRA_MESSAGE_TIMESTAMP, -1L)
            if (timestamp > 0) {
                notificationTimestamp.value = timestamp
            }
        }
    }
}

@Composable
fun MessagateApp(
    prefsManager: PreferencesManager,
    directOpenTimestamp: Long?,
    onDirectOpenConsumed: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val messagesVersion by remember { PreferencesManager.messagesVersion }
    var isDefaultSms by remember { mutableStateOf(SmsRoleHelper.isDefaultSmsApp(context)) }
    var currentScreen by remember { mutableStateOf(AppScreen.INBOX) }
    var inspectedLogs by remember { mutableStateOf(prefsManager.getInspectedMessages()) }
    var isMasterActive by remember { mutableStateOf(prefsManager.isMasterActive()) }
    var isFilterEnabled by remember { mutableStateOf(prefsManager.isFilterEnabled()) }
    var isShowSpamInFeed by remember { mutableStateOf(prefsManager.isShowSpamInFeed()) }
    var rulesList by remember { mutableStateOf(prefsManager.getRules()) }

    // Single-permission launcher for notifications (Android 13+)
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

    val defaultSmsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        isDefaultSms = SmsRoleHelper.isDefaultSmsApp(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    LaunchedEffect(messagesVersion) {
        inspectedLogs = prefsManager.getInspectedMessages()
        isDefaultSms = SmsRoleHelper.isDefaultSmsApp(context)
    }

    // Instant O(1) Mapping directly from pre-computed fields
    val uiMessages = remember(inspectedLogs) {
        inspectedLogs.map { msg ->
            UiMessageItem(
                raw = msg,
                category = MessageCategory.fromString(msg.category),
                formattedTime = formatMessageTimestamp(msg.timestamp)
            )
        }
    }

    // Modal state for viewing full message
    var selectedMessageForModal by remember { mutableStateOf<InspectedMessage?>(null) }
    var cleanMessageToDelete by remember { mutableStateOf<InspectedMessage?>(null) }

    // Auto-open modal when launched from notification
    LaunchedEffect(directOpenTimestamp) {
        if (directOpenTimestamp != null && directOpenTimestamp > 0) {
            val targetMsg = prefsManager.getMessageByTimestamp(directOpenTimestamp)
            if (targetMsg != null) {
                prefsManager.markMessageAsRead(targetMsg.timestamp)
                NotificationHelper.cancelNotification(context, targetMsg.timestamp)
                inspectedLogs = prefsManager.getInspectedMessages()
                selectedMessageForModal = targetMsg.copy(isRead = true)
                currentScreen = AppScreen.INBOX
            }
            onDirectOpenConsumed()
        }
    }

    // Direct startup trigger: immediately request default SMS role on open if not set
    LaunchedEffect(Unit) {
        prefsManager.reevaluateMessages()
        inspectedLogs = prefsManager.getInspectedMessages()
        if (!SmsRoleHelper.isDefaultSmsApp(context)) {
            try {
                defaultSmsLauncher.launch(SmsRoleHelper.createDefaultSmsIntent(context))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    LaunchedEffect(currentScreen) {
        isDefaultSms = SmsRoleHelper.isDefaultSmsApp(context)
        inspectedLogs = prefsManager.getInspectedMessages()
        rulesList = prefsManager.getRules()
        isMasterActive = prefsManager.isMasterActive()
        isFilterEnabled = prefsManager.isFilterEnabled()
        isShowSpamInFeed = prefsManager.isShowSpamInFeed()
    }

    // Initial / full sync of existing inbox messages once default SMS or READ_SMS is available
    LaunchedEffect(isDefaultSms) {
        val hasReadPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_SMS
        ) == PackageManager.PERMISSION_GRANTED
        if ((isDefaultSms || hasReadPermission) && !prefsManager.hasImportedFullSms()) {
            withContext(Dispatchers.IO) {
                val oldMessages = SmsRoleHelper.readExistingInboxMessages(
                    context,
                    limit = PreferencesManager.MAX_STORED_MESSAGES
                )
                if (oldMessages.isNotEmpty()) {
                    prefsManager.importInitialMessages(oldMessages)
                }
                prefsManager.setImportedInitialSms(true)
                prefsManager.setImportedFullSms(true)
            }
            inspectedLogs = prefsManager.getInspectedMessages()
        }
    }

    // Run automatic spam cleanup (older than 30 days) when app is open and idle
    LaunchedEffect(Unit) {
        delay(2000L) // Wait 2s for UI to settle idle
        withContext(Dispatchers.IO) {
            prefsManager.cleanupOldSpam()
        }
    }

    BackHandler(enabled = currentScreen != AppScreen.INBOX) {
        currentScreen = if (currentScreen == AppScreen.RULES) AppScreen.SETTINGS else AppScreen.INBOX
    }

    // Standardized Message Preview Modal
    selectedMessageForModal?.let { message ->
        MessageDetailModal(
            message = message,
            onDismiss = { selectedMessageForModal = null },
            onDelete = {
                if (message.isSpam) {
                    prefsManager.deleteMessage(message.timestamp)
                    coroutineScope.launch(Dispatchers.IO) {
                        SmsRoleHelper.deleteFromTelephonyProvider(context, message.timestamp, message.sender)
                    }
                    inspectedLogs = prefsManager.getInspectedMessages()
                    selectedMessageForModal = null
                    Toast.makeText(context, "Spam message deleted", Toast.LENGTH_SHORT).show()
                } else {
                    selectedMessageForModal = null
                    cleanMessageToDelete = message
                }
            }
        )
    }

    // Standardized Clean Message Deletion Confirmation Dialog
    cleanMessageToDelete?.let { msg ->
        Dialog(
            onDismissRequest = { cleanMessageToDelete = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp)
                    .border(1.dp, DarkBorder, RectangleShape),
                shape = RectangleShape,
                color = DarkSurface
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "// CONFIRM DELETION",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.sp,
                            color = YellowAccent
                        )

                        IconButton(
                            onClick = { cleanMessageToDelete = null },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = TextMuted,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Text(
                        text = "This message is classified as Clean (Not Spam). Are you sure you want to delete it?",
                        fontSize = 14.sp,
                        color = TextSecondary,
                        lineHeight = 20.sp
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { cleanMessageToDelete = null },
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp),
                            shape = RectangleShape,
                            border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary)
                        ) {
                            Text("CANCEL", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, letterSpacing = 1.sp)
                        }

                        Button(
                            onClick = {
                                prefsManager.deleteMessage(msg.timestamp)
                                coroutineScope.launch(Dispatchers.IO) {
                                    SmsRoleHelper.deleteFromTelephonyProvider(context, msg.timestamp, msg.sender)
                                }
                                inspectedLogs = prefsManager.getInspectedMessages()
                                cleanMessageToDelete = null
                                Toast.makeText(context, "Message deleted", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp),
                            shape = RectangleShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFEF4444).copy(alpha = 0.2f),
                                contentColor = Color(0xFFEF4444)
                            ),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.4f))
                        ) {
                            Icon(imageVector = Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("DELETE", fontWeight = FontWeight.Bold, fontSize = 13.sp, letterSpacing = 1.sp)
                        }
                    }
                }
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = DarkBackground
    ) { innerPadding ->
        when (currentScreen) {
            AppScreen.INBOX -> {
                InboxScreen(
                    uiMessages = uiMessages,
                    showSpamInFeed = isShowSpamInFeed,
                    isDefaultSms = isDefaultSms,
                    onRequestDefaultSms = {
                        defaultSmsLauncher.launch(SmsRoleHelper.createDefaultSmsIntent(context))
                    },
                    onOpenSettings = { currentScreen = AppScreen.SETTINGS },
                    onSelectMessage = { message ->
                        prefsManager.markMessageAsRead(message.timestamp)
                        NotificationHelper.cancelNotification(context, message.timestamp)
                        inspectedLogs = prefsManager.getInspectedMessages()
                        selectedMessageForModal = message.copy(isRead = true)
                    },
                    onDeleteMessages = { timestamps ->
                        val count = prefsManager.deleteMessages(timestamps)
                        val msgsToDelete = inspectedLogs.filter { it.timestamp in timestamps }
                        coroutineScope.launch(Dispatchers.IO) {
                            for (m in msgsToDelete) {
                                SmsRoleHelper.deleteFromTelephonyProvider(context, m.timestamp, m.sender)
                            }
                        }
                        for (ts in timestamps) {
                            NotificationHelper.cancelNotification(context, ts)
                        }
                        inspectedLogs = prefsManager.getInspectedMessages()
                        Toast.makeText(context, "Deleted $count message(s)", Toast.LENGTH_SHORT).show()
                    },
                    onMarkAllAsRead = {
                        val count = prefsManager.markAllAsRead()
                        NotificationHelper.cancelAllNotifications(context)
                        inspectedLogs = prefsManager.getInspectedMessages()
                        Toast.makeText(context, "Marked $count message(s) as read", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                )
            }
            AppScreen.SETTINGS -> {
                SettingsScreen(
                    prefsManager = prefsManager,
                    isMasterActive = isMasterActive,
                    isFilterEnabled = isFilterEnabled,
                    isShowSpamInFeed = isShowSpamInFeed,
                    rules = rulesList,
                    messages = inspectedLogs,
                    onMasterToggle = { enabled ->
                        isMasterActive = enabled
                        prefsManager.setMasterActive(enabled)
                        prefsManager.triggerReevaluation(0L)
                        inspectedLogs = prefsManager.getInspectedMessages()
                    },
                    onFilterToggle = { enabled ->
                        isFilterEnabled = enabled
                        prefsManager.setFilterEnabled(enabled)
                        prefsManager.triggerReevaluation(0L)
                        inspectedLogs = prefsManager.getInspectedMessages()
                    },
                    onShowSpamInFeedToggle = { enabled ->
                        isShowSpamInFeed = enabled
                        prefsManager.setShowSpamInFeed(enabled)
                    },
                    onOpenRules = { currentScreen = AppScreen.RULES },
                    onQuickBlock = { text, regex ->
                        val trimmed = text.trim()
                        if (trimmed.isNotEmpty()) {
                            val rule = Rule(
                                name = trimmed,
                                pattern = trimmed,
                                field = MatchField.BOTH,
                                type = if (regex) MatchType.REGEX else MatchType.CONTAINS,
                                action = RuleAction.BLOCK,
                                priority = (rulesList.minOfOrNull { it.priority } ?: 10) - 10
                            )
                            prefsManager.saveRules(
                                (rulesList + rule)
                                    .sortedBy { it.priority }
                                    .mapIndexed { i, r -> r.copy(priority = i * 10) }
                            )
                            rulesList = prefsManager.getRules()
                        }
                    },
                    onDeleteRule = { id ->
                        prefsManager.saveRules(
                            rulesList
                                .filterNot { it.id == id }
                                .sortedBy { it.priority }
                                .mapIndexed { i, r -> r.copy(priority = i * 10) }
                        )
                        rulesList = prefsManager.getRules()
                    },
                    onDeleteAllSpam = {
                        val count = prefsManager.deleteAllSpam()
                        inspectedLogs = prefsManager.getInspectedMessages()
                        Toast.makeText(context, "Purged $count spam message(s)", Toast.LENGTH_SHORT).show()
                    },
                    onDataImported = {
                        rulesList = prefsManager.getRules()
                        inspectedLogs = prefsManager.getInspectedMessages()
                        isMasterActive = prefsManager.isMasterActive()
                        isFilterEnabled = prefsManager.isFilterEnabled()
                        isShowSpamInFeed = prefsManager.isShowSpamInFeed()
                    },
                    onBack = { currentScreen = AppScreen.INBOX },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                )
            }
            AppScreen.RULES -> {
                RulesScreen(
                    rules = rulesList,
                    prefsManager = prefsManager,
                    onSaveRule = { rule ->
                        val list = rulesList.toMutableList()
                        val index = list.indexOfFirst { it.id == rule.id }
                        if (index >= 0) list[index] = rule else list.add(rule)
                        val normalized = list
                            .sortedBy { it.priority }
                            .mapIndexed { i, r -> r.copy(priority = i * 10) }
                        prefsManager.saveRules(normalized)
                        rulesList = prefsManager.getRules()
                    },
                    onDeleteRule = { id ->
                        val normalized = rulesList
                            .filterNot { it.id == id }
                            .sortedBy { it.priority }
                            .mapIndexed { i, r -> r.copy(priority = i * 10) }
                        prefsManager.saveRules(normalized)
                        rulesList = prefsManager.getRules()
                    },
                    onDeleteRules = { ids ->
                        val normalized = rulesList
                            .filterNot { it.id in ids }
                            .sortedBy { it.priority }
                            .mapIndexed { i, r -> r.copy(priority = i * 10) }
                        prefsManager.saveRules(normalized)
                        rulesList = prefsManager.getRules()
                        Toast.makeText(context, "Deleted ${ids.size} rule(s)", Toast.LENGTH_SHORT).show()
                    },
                    onToggleRule = { rule ->
                        prefsManager.saveRules(
                            rulesList.map { if (it.id == rule.id) it.copy(enabled = !it.enabled) else it }
                        )
                        rulesList = prefsManager.getRules()
                    },
                    onMoveRule = { rule, delta ->
                        val sorted = rulesList.sortedBy { it.priority }.toMutableList()
                        val from = sorted.indexOfFirst { it.id == rule.id }
                        val to = from + delta
                        if (from >= 0 && to in sorted.indices) {
                            val moving = sorted[from]
                            sorted[from] = sorted[to]
                            sorted[to] = moving
                            prefsManager.saveRules(
                                sorted.mapIndexed { i, r -> r.copy(priority = i * 10) }
                            )
                            rulesList = prefsManager.getRules()
                        }
                    },
                    onBack = { currentScreen = AppScreen.SETTINGS },
                    onDataImported = {
                        rulesList = prefsManager.getRules()
                        inspectedLogs = prefsManager.getInspectedMessages()
                        isMasterActive = prefsManager.isMasterActive()
                        isFilterEnabled = prefsManager.isFilterEnabled()
                        isShowSpamInFeed = prefsManager.isShowSpamInFeed()
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun InboxScreen(
    uiMessages: List<UiMessageItem>,
    showSpamInFeed: Boolean,
    isDefaultSms: Boolean,
    onRequestDefaultSms: () -> Unit,
    onOpenSettings: () -> Unit,
    onSelectMessage: (InspectedMessage) -> Unit,
    onDeleteMessages: (Set<Long>) -> Unit,
    onMarkAllAsRead: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    var showOnlyUnread by remember { mutableStateOf(false) }
    var rawSearchQuery by remember { mutableStateOf("") }
    var debouncedSearchQuery by remember { mutableStateOf("") }
    var visibleItemCount by remember { mutableIntStateOf(25) }

    var selectedTimestamps by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    val isSelectionMode = selectedTimestamps.isNotEmpty()

    val listState = rememberLazyListState()

    BackHandler(enabled = isSelectionMode) {
        selectedTimestamps = emptySet()
    }

    val unreadCount = remember(uiMessages, showSpamInFeed) {
        uiMessages.count { !it.raw.isRead && (showSpamInFeed || !it.raw.isSpam) }
    }

    var expandedGroupTimestamps by remember { mutableStateOf<Set<Long>>(emptySet()) }

    // 200ms Search Debounce
    LaunchedEffect(rawSearchQuery) {
        delay(200L)
        debouncedSearchQuery = rawSearchQuery
    }

    // Reset pagination and scroll to top when search, unread, or showSpamInFeed changes
    LaunchedEffect(debouncedSearchQuery, showOnlyUnread, showSpamInFeed) {
        visibleItemCount = 25
        expandedGroupTimestamps = emptySet()
        listState.scrollToItem(0)
    }

    val filteredMessages = remember(uiMessages, showSpamInFeed, showOnlyUnread, debouncedSearchQuery) {
        uiMessages.filter { item ->
            val matchesSpam = showSpamInFeed || !item.raw.isSpam
            val matchesUnread = !showOnlyUnread || !item.raw.isRead
            val query = debouncedSearchQuery.trim()
            val matchesSearch = query.isEmpty() ||
                    item.raw.sender.contains(query, ignoreCase = true) ||
                    item.raw.body.contains(query, ignoreCase = true)
            matchesSpam && matchesUnread && matchesSearch
        }
    }

    val groupedMessages = remember(filteredMessages) {
        groupContiguousMessages(filteredMessages)
    }

    val displayedGroups = remember(groupedMessages, visibleItemCount) {
        groupedMessages.take(visibleItemCount)
    }

    val topMessageTimestamp = displayedGroups.firstOrNull()?.head?.raw?.timestamp
    var lastTopTimestamp by remember { mutableStateOf<Long?>(null) }

    // Auto-scroll to top when a new message is added to the feed
    LaunchedEffect(topMessageTimestamp) {
        if (lastTopTimestamp != null && topMessageTimestamp != null && topMessageTimestamp > lastTopTimestamp!!) {
            listState.animateScrollToItem(0)
        }
        lastTopTimestamp = topMessageTimestamp
    }

    val remainingCount = groupedMessages.size - displayedGroups.size

    if (showDeleteConfirmDialog) {
        Dialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.7f)),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth(0.88f)
                        .border(1.dp, Color(0xFFEF4444).copy(alpha = 0.5f), RectangleShape),
                    color = DarkSurface,
                    shape = RectangleShape
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "// DELETE MESSAGES",
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            letterSpacing = 1.sp,
                            color = Color(0xFFEF4444)
                        )

                        Text(
                            text = "Delete ${selectedTimestamps.size} selected message(s)? Clean messages will also be removed from your phone's SMS storage.",
                            fontSize = 13.sp,
                            color = TextSecondary,
                            lineHeight = 18.sp
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = { showDeleteConfirmDialog = false },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(40.dp),
                                shape = RectangleShape,
                                border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary)
                            ) {
                                Text("CANCEL", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, letterSpacing = 1.sp)
                            }

                            Button(
                                onClick = {
                                    val toDelete = selectedTimestamps
                                    selectedTimestamps = emptySet()
                                    showDeleteConfirmDialog = false
                                    onDeleteMessages(toDelete)
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(40.dp),
                                shape = RectangleShape,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFEF4444).copy(alpha = 0.2f),
                                    contentColor = Color(0xFFEF4444)
                                ),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.6f))
                            ) {
                                Text(
                                    text = "DELETE (${selectedTimestamps.size})",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    letterSpacing = 1.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Minimal Top Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.combinedClickable(
                    onClick = { },
                    onLongClick = { showEasterEgg(context) }
                ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_messagate_logo),
                    contentDescription = null,
                    tint = YellowAccent,
                    modifier = Modifier.size(22.dp)
                )

                Text(
                    text = "MESSAGATE",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp,
                    color = TextPrimary
                )
            }

            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = "Settings",
                    tint = YellowAccent,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        HorizontalDivider(color = DarkBorder, thickness = 1.dp)

        if (!isDefaultSms) {
            // Dedicated Centered Activation Screen when not Default SMS App
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .background(YellowAccent.copy(alpha = 0.1f), CircleShape)
                            .border(1.dp, YellowAccent.copy(alpha = 0.6f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = YellowAccent,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    Text(
                        text = "// FIREWALL OFFLINE",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.5.sp,
                        color = YellowAccent
                    )

                    Text(
                        text = "On Android, only the default SMS app can silently block spam before it alerts you or saves to your phone.",
                        fontSize = 13.sp,
                        color = TextSecondary,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        lineHeight = 19.sp,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = onRequestDefaultSms,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RectangleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = YellowAccent,
                            contentColor = DarkBackground
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = DarkBackground
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "SET AS DEFAULT SMS APP",
                            fontWeight = FontWeight.Black,
                            fontSize = 13.sp,
                            letterSpacing = 1.sp
                        )
                    }
                }
            }
        } else {
            if (isSelectionMode) {
                // Contextual Multi-Select Bar: Red DELETE on Left + Close on Right
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { showDeleteConfirmDialog = true },
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp),
                        shape = RectangleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFEF4444).copy(alpha = 0.2f),
                            contentColor = Color(0xFFEF4444)
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.6f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = Color(0xFFEF4444)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "DELETE (${selectedTimestamps.size})",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            letterSpacing = 1.sp
                        )
                    }

                    IconButton(
                        onClick = { selectedTimestamps = emptySet() },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cancel selection",
                            tint = TextPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            } else {
                // Minimal Options Bar (Search on Left + UNREAD Badge on Right)
                Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Minimalist Search Bar with Real-time Debounce on Left
            BasicTextField(
                value = rawSearchQuery,
                onValueChange = { rawSearchQuery = it },
                singleLine = true,
                cursorBrush = SolidColor(YellowAccent),
                textStyle = TextStyle(
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Default
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp)
                    .background(DarkSurface)
                    .border(
                        1.dp,
                        if (rawSearchQuery.isNotEmpty()) YellowAccent else DarkBorder,
                        RectangleShape
                    )
                    .padding(horizontal = 10.dp),
                decorationBox = { innerTextField ->
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = TextMuted,
                            modifier = Modifier.size(16.dp)
                        )
                        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                            if (rawSearchQuery.isEmpty()) {
                                Text(
                                    text = "Search...",
                                    color = TextMuted,
                                    fontSize = 12.sp
                                )
                            }
                            innerTextField()
                        }
                        if (rawSearchQuery.isNotEmpty()) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear search",
                                tint = TextMuted,
                                modifier = Modifier
                                    .size(16.dp)
                                    .clickable {
                                        rawSearchQuery = ""
                                        debouncedSearchQuery = ""
                                    }
                            )
                        }
                    }
                }
            )

            // UNREAD Filter Toggle Badge on Right
            Surface(
                shape = RectangleShape,
                color = if (showOnlyUnread) YellowAccent else DarkSurface,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (showOnlyUnread) YellowAccent else DarkBorder
                ),
                modifier = Modifier
                    .height(36.dp)
                    .clickable { showOnlyUnread = !showOnlyUnread }
            ) {
                Box(
                    modifier = Modifier.padding(horizontal = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (unreadCount > 0) "UNREAD ($unreadCount)" else "UNREAD",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = if (showOnlyUnread) DarkBackground else if (unreadCount > 0) YellowAccent else TextMuted
                    )
                }
            }
        }
    }

    HorizontalDivider(color = DarkBorder.copy(alpha = 0.5f), thickness = 1.dp)

        // Messages Feed with Recycled Composable Items
        if (filteredMessages.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .background(DarkSurfaceVariant)
                            .border(1.dp, DarkBorder, RectangleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChatBubbleOutline,
                            contentDescription = null,
                            tint = TextMuted,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Text(
                        text = if (showOnlyUnread) "// NO UNREAD MESSAGES" else if (debouncedSearchQuery.isNotEmpty()) "// NO MATCHES FOUND" else "// NO MESSAGES DETECTED",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp,
                        color = TextSecondary
                    )
                    Text(
                        text = if (showOnlyUnread) "All messages have been reviewed." else if (debouncedSearchQuery.isNotEmpty()) "Try a different search query." else "Incoming SMS messages will be intercepted and logged here.",
                        fontSize = 13.sp,
                        color = TextMuted,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        lineHeight = 18.sp
                    )
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                items(
                    items = displayedGroups,
                    key = { it.head.raw.timestamp },
                    contentType = { "message_group" }
                ) { group ->
                    val isExpanded = group.head.raw.timestamp in expandedGroupTimestamps
                    val isAnySelected = group.allTimestamps.any { it in selectedTimestamps }
                    val isAllSelected = group.allTimestamps.all { it in selectedTimestamps }

                    InboxMessageGroupRow(
                        group = group,
                        isExpanded = isExpanded,
                        isAnySelected = isAnySelected,
                        isAllSelected = isAllSelected,
                        selectedTimestamps = selectedTimestamps,
                        isSelectionMode = isSelectionMode,
                        onSelectMessage = onSelectMessage,
                        onToggleExpand = {
                            expandedGroupTimestamps = if (isExpanded) {
                                expandedGroupTimestamps - group.head.raw.timestamp
                            } else {
                                expandedGroupTimestamps + group.head.raw.timestamp
                            }
                        },
                        onToggleSelectGroup = {
                            selectedTimestamps = if (isAnySelected) {
                                selectedTimestamps - group.allTimestamps.toSet()
                            } else {
                                selectedTimestamps + group.allTimestamps.toSet()
                            }
                        },
                        onToggleSelectTimestamp = { timestamp ->
                            selectedTimestamps = if (timestamp in selectedTimestamps) {
                                selectedTimestamps - timestamp
                            } else {
                                selectedTimestamps + timestamp
                            }
                        },
                        onLongClickGroup = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            selectedTimestamps = if (isAnySelected) {
                                selectedTimestamps - group.allTimestamps.toSet()
                            } else {
                                selectedTimestamps + group.allTimestamps.toSet()
                            }
                        }
                    )
                    HorizontalDivider(color = ROW_DIVIDER_COLOR, thickness = 1.dp)
                }

                // Show More Pagination Button
                if (remainingCount > 0) {
                    item(contentType = "pagination_button") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            OutlinedButton(
                                onClick = { visibleItemCount += 25 },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp),
                                shape = RectangleShape,
                                border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = YellowAccent)
                            ) {
                                Text(
                                    text = "SHOW MORE (${if (remainingCount > 25) "+25" else remainingCount} of $remainingCount REMAINING)",
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    letterSpacing = 1.sp
                                )
                            }
                        }
                    }
                }

                // Mark All As Read Option
                if (showOnlyUnread && unreadCount > 0) {
                    item(contentType = "mark_read_button") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Button(
                                onClick = onMarkAllAsRead,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp),
                                shape = RectangleShape,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = YellowAccent.copy(alpha = 0.15f),
                                    contentColor = YellowAccent
                                ),
                                border = androidx.compose.foundation.BorderStroke(1.dp, YellowAccent.copy(alpha = 0.4f))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DoneAll,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "MARK ALL AS READ ($unreadCount)",
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    letterSpacing = 1.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
}

// 100% O(1) Fast Composable Group Row with 3D Stacked Deck & Tree Accordion
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun InboxMessageGroupRow(
    group: UiMessageGroup,
    isExpanded: Boolean,
    isAnySelected: Boolean,
    isAllSelected: Boolean,
    selectedTimestamps: Set<Long>,
    isSelectionMode: Boolean,
    onSelectMessage: (InspectedMessage) -> Unit,
    onToggleExpand: () -> Unit,
    onToggleSelectGroup: () -> Unit,
    onToggleSelectTimestamp: (Long) -> Unit,
    onLongClickGroup: () -> Unit,
    modifier: Modifier = Modifier
) {
    val message = group.head.raw
    val category = group.head.category
    val displayTimestamp = group.head.formattedTime
    val isStacked = group.isStacked
    val isHeadSelected = group.head.raw.timestamp in selectedTimestamps

    val rowBackground = if (isHeadSelected || (!isExpanded && isAnySelected)) {
        Color(0xFFEF4444).copy(alpha = 0.08f)
    } else {
        Color.Transparent
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(rowBackground)
                .combinedClickable(
                    onClick = {
                        if (isSelectionMode) {
                            onToggleSelectGroup()
                        } else {
                            onSelectMessage(message)
                        }
                    },
                    onLongClick = onLongClickGroup
                )
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 3D Avatar Box: Multi-layered physical deck when stacked
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .combinedClickable(
                        onClick = {
                            if (isSelectionMode) {
                                onToggleSelectGroup()
                            } else if (isStacked) {
                                onToggleExpand()
                            } else {
                                onSelectMessage(message)
                            }
                        },
                        onLongClick = onLongClickGroup
                    )
            ) {
                if (isStacked) {
                    // Backmost card (Layer 2)
                    Box(
                        modifier = Modifier
                            .offset(x = 4.dp, y = 4.dp)
                            .size(36.dp)
                            .background(DarkBackground)
                            .border(
                                1.dp,
                                if (isAnySelected) Color(0xFFEF4444).copy(alpha = 0.4f) else DarkBorder.copy(alpha = 0.6f),
                                RectangleShape
                            )
                    )
                    // Middle card (Layer 1)
                    Box(
                        modifier = Modifier
                            .offset(x = 2.dp, y = 2.dp)
                            .size(36.dp)
                            .background(DarkSurfaceVariant)
                            .border(
                                1.dp,
                                if (isAnySelected) Color(0xFFEF4444).copy(alpha = 0.7f) else DarkBorder.copy(alpha = 0.85f),
                                RectangleShape
                            )
                    )
                }

                // Front card (Layer 0)
                Box(
                    modifier = Modifier
                        .offset(x = 0.dp, y = 0.dp)
                        .size(36.dp)
                        .background(if (isAnySelected) Color(0xFFEF4444).copy(alpha = 0.25f) else DarkSurfaceVariant)
                        .border(
                            width = 1.dp,
                            color = if (isAnySelected) Color(0xFFEF4444) else DarkBorder,
                            shape = RectangleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isAnySelected) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Selected",
                            tint = Color(0xFFEF4444),
                            modifier = Modifier.size(20.dp)
                        )
                    } else if (category.iconRes != 0) {
                        Icon(
                            painter = painterResource(category.iconRes),
                            contentDescription = category.label,
                            tint = Color.Unspecified,
                            modifier = Modifier.size(20.dp)
                        )
                    } else {
                        Text(
                            text = category.emoji,
                            fontSize = 16.sp
                        )
                    }
                }
            }

            // Message Preview Content
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = message.sender,
                            fontSize = 15.sp,
                            fontWeight = if (group.hasUnread) FontWeight.Black else FontWeight.Bold,
                            color = if (group.hasUnread) TextPrimary else TextSecondary
                        )
                        val spamLabel = message.matchedRuleName ?: message.matchedKeyword
                        if (message.isSpam && !spamLabel.isNullOrEmpty()) {
                            Surface(
                                shape = RectangleShape,
                                color = YellowAccentSubtle,
                                border = SPAM_TAG_BORDER
                            ) {
                                Text(
                                    text = spamLabel.uppercase(),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    color = YellowAccent,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    // Stack Pill Badge + Timestamp + Minimal Unread Dot
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (isStacked) {
                            Surface(
                                shape = RectangleShape,
                                color = YellowAccentSubtle,
                                border = androidx.compose.foundation.BorderStroke(1.dp, YellowAccent.copy(alpha = 0.7f)),
                                modifier = Modifier.clickable { onToggleExpand() }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "+${group.subMessages.size} ${if (group.subMessages.size == 1) "MSG" else "MSGS"}",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        color = YellowAccent
                                    )
                                    Text(
                                        text = if (isExpanded) "▲" else "▼",
                                        fontSize = 8.sp,
                                        color = YellowAccent
                                    )
                                }
                            }
                        }

                        Text(
                            text = displayTimestamp,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = if (group.hasUnread) YellowAccent else TextMuted
                        )

                        // Minimal Unread Indicator Dot
                        if (group.hasUnread) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(YellowAccent, CircleShape)
                            )
                        }
                    }
                }

                Text(
                    text = message.body,
                    fontSize = 13.sp,
                    color = if (group.hasUnread) TextPrimary else TextSecondary,
                    fontWeight = if (group.hasUnread) FontWeight.Medium else FontWeight.Normal,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 18.sp
                )
            }
        }

        // Inline Terminal Tree Accordion for Stacked Sub-messages
        AnimatedVisibility(
            visible = isExpanded && isStacked,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF141416))
                    .padding(bottom = 4.dp)
            ) {
                group.subMessages.forEachIndexed { index, subItem ->
                    val isLast = index == group.subMessages.lastIndex
                    val isSubSelected = subItem.raw.timestamp in selectedTimestamps
                    val subRowBackground = if (isSubSelected) {
                        Color(0xFFEF4444).copy(alpha = 0.08f)
                    } else {
                        Color.Transparent
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(subRowBackground)
                            .clickable {
                                if (isSelectionMode) {
                                    onToggleSelectTimestamp(subItem.raw.timestamp)
                                } else {
                                    onSelectMessage(subItem.raw)
                                }
                            }
                            .padding(start = 36.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = if (isLast) "└──" else "├──",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSubSelected) Color(0xFFEF4444).copy(alpha = 0.85f) else YellowAccent.copy(alpha = 0.85f),
                            modifier = Modifier.padding(top = 1.dp)
                        )

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = subItem.formattedTime,
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = if (isSubSelected) Color(0xFFEF4444) else if (!subItem.raw.isRead) YellowAccent else TextMuted
                                    )
                                    if (!subItem.raw.isRead) {
                                        Box(
                                            modifier = Modifier
                                                .size(5.dp)
                                                .background(if (isSubSelected) Color(0xFFEF4444) else YellowAccent, CircleShape)
                                        )
                                    }
                                }

                                if (isSelectionMode) {
                                    Box(
                                        modifier = Modifier
                                            .size(16.dp)
                                            .background(if (isSubSelected) Color(0xFFEF4444) else Color.Transparent)
                                            .border(
                                                1.dp,
                                                if (isSubSelected) Color(0xFFEF4444) else DarkBorder,
                                                RectangleShape
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isSubSelected) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(12.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            Text(
                                text = subItem.raw.body,
                                fontSize = 12.sp,
                                color = if (!subItem.raw.isRead) TextPrimary else TextSecondary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

// Standardized Wide Message Details Modal
@Composable
fun MessageDetailModal(
    message: InspectedMessage,
    onDismiss: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val maxTextBoxHeight = (configuration.screenHeightDp * 0.50f).dp
    val fullTimeString = remember(message.timestamp) {
        synchronized(FULL_DATE_FORMAT) { FULL_DATE_FORMAT.format(Date(message.timestamp)) }
    }
    val extractedOtp = remember(message.body) { OtpHelper.extractOtp(message.body) }
    val category = remember(message.category) { MessageCategory.fromString(message.category) }
    val spamLabel = message.matchedRuleName ?: message.matchedKeyword
    // One destructive style for every message, spam or clean, so the button does not
    // change colour/size when a message is tagged as spam.
    val deleteContainer = DESTRUCTIVE_FILL
    val deleteContent = DESTRUCTIVE_TEXT
    val deleteBorder = DESTRUCTIVE_BORDER

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp)
                .border(1.dp, DarkBorder, RectangleShape),
            shape = RectangleShape,
            color = DarkSurface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header: sender + compact status chips, timestamp on its own full-width line
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "// ${message.sender.uppercase()}",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.sp,
                            color = YellowAccent,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        // Status: spam collapses to one chip — warning icon + matched pattern
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (message.isSpam) {
                                DetailStatusChip(
                                    text = (spamLabel ?: category.label).uppercase(),
                                    color = YellowAccent,
                                    iconRes = R.drawable.ic_warning
                                )
                            } else {
                                DetailStatusChip(
                                    text = if (category.iconRes != 0) category.label else "${category.emoji} ${category.label}",
                                    color = if (category == MessageCategory.OTP) YellowAccent else TextSecondary,
                                    iconRes = category.iconRes
                                )
                            }
                        }

                        // Own line: full width available, smaller, never wraps
                        Text(
                            text = fullTimeString,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            color = TextMuted,
                            maxLines = 1,
                            softWrap = false
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = TextMuted,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                // Message Text in Dark Container with Clickable Links and Phone Numbers
                Surface(
                    shape = RectangleShape,
                    color = DarkBackground,
                    border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = maxTextBoxHeight)
                ) {
                    ClickableLinkifiedText(
                        text = message.body,
                        fontSize = 14.sp,
                        lineHeight = 22.sp,
                        modifier = Modifier
                            .padding(16.dp)
                            .verticalScroll(rememberScrollState())
                    )
                }

                // Action Buttons Layout
                if (extractedOtp != null) {
                    // OTP Layout: 2 Full-Width Lines
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Line 1: Full-Width Copy OTP Button
                        OutlinedButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("OTP Code", extractedOtp)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "Copied OTP: $extractedOtp", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp),
                            shape = RectangleShape,
                            border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "COPY $extractedOtp",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                letterSpacing = 1.sp
                            )
                        }

                        // Line 2: Full-Width Delete Button
                        Button(
                            onClick = onDelete,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp),
                            shape = RectangleShape,
                            contentPadding = PaddingValues(horizontal = 10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = deleteContainer,
                                contentColor = deleteContent
                            ),
                            border = deleteBorder
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "DELETE",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                letterSpacing = 1.sp,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                } else {
                    // Normal Message Layout: Side-by-Side Symmetrical Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("Messagate SMS", message.body)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp),
                            shape = RectangleShape,
                            contentPadding = PaddingValues(horizontal = 6.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "COPY",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                letterSpacing = 0.5.sp,
                                maxLines = 1,
                                softWrap = false
                            )
                        }

                        Button(
                            onClick = onDelete,
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp),
                            shape = RectangleShape,
                            contentPadding = PaddingValues(horizontal = 6.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = deleteContainer,
                                contentColor = deleteContent
                            ),
                            border = deleteBorder
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                "DELETE",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                letterSpacing = 0.5.sp,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailStatusChip(text: String, color: Color, iconRes: Int = 0) {
    Surface(
        shape = RectangleShape,
        color = DarkBackground,
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            if (iconRes != 0) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    tint = Color.Unspecified,
                    modifier = Modifier.size(11.dp)
                )
            }
            Text(
                text = text,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 0.5.sp,
                color = color,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ModeChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RectangleShape,
        color = if (selected) YellowAccentSubtle else DarkBackground,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (selected) YellowAccent else DarkBorder
        ),
        modifier = Modifier.clickable { onClick() }
    ) {
        Text(
            text = text,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) YellowAccent else TextSecondary,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun SettingsRuleChip(rule: Rule, onDelete: () -> Unit) {
    // Neutral chip styling (same as the old keyword chips); the action is conveyed
    // by the rules screen, not by a coloured chip here.
    val contentColor = if (rule.enabled) TextPrimary else TextMuted
    val strokeColor = if (rule.enabled) DarkBorder else DarkBorder.copy(alpha = 0.6f)
    val raw = if (rule.type == MatchType.REGEX) "/${rule.pattern}/" else rule.pattern
    val label = if (raw.length > 30) raw.take(30) + "…" else raw

    Surface(
        shape = RectangleShape,
        color = DarkBackground,
        border = androidx.compose.foundation.BorderStroke(1.dp, strokeColor)
    ) {
        Row(
            modifier = Modifier.padding(start = 6.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (!rule.enabled) {
                Text(
                    text = "OFF",
                    fontSize = 8.sp,
                    fontFamily = FontFamily.Monospace,
                    color = TextMuted
                )
            }
            Text(
                text = label.uppercase(),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = FontFamily.Monospace,
                color = contentColor,
                maxLines = 1,
                softWrap = false
            )
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clickable { onDelete() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Delete ${rule.pattern}",
                    tint = TextMuted,
                    modifier = Modifier.size(11.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    prefsManager: PreferencesManager,
    isMasterActive: Boolean,
    isFilterEnabled: Boolean,
    isShowSpamInFeed: Boolean,
    rules: List<Rule>,
    messages: List<InspectedMessage>,
    onMasterToggle: (Boolean) -> Unit,
    onFilterToggle: (Boolean) -> Unit,
    onShowSpamInFeedToggle: (Boolean) -> Unit,
    onOpenRules: () -> Unit,
    onQuickBlock: (String, Boolean) -> Unit,
    onDeleteRule: (String) -> Unit,
    onDeleteAllSpam: () -> Unit,
    onDataImported: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isAdvancedExpanded by remember { mutableStateOf(false) }
    var quickText by remember { mutableStateOf("") }
    var quickRegex by remember { mutableStateOf(false) }
    val spamCount = remember(messages) { messages.count { it.isSpam } }

    // Backup: export/import rules + switches via the system file picker
    val backup = rememberBackupLaunchers(prefsManager, onDataImported)

    val submitQuickBlock: () -> Unit = {
        val trimmed = quickText.trim()
        val draft = Rule(
            name = trimmed,
            pattern = trimmed,
            field = MatchField.BOTH,
            type = if (quickRegex) MatchType.REGEX else MatchType.CONTAINS,
            action = RuleAction.BLOCK
        )
        val validation = CompiledRule.validate(draft)
        val duplicate = rules.any {
            it.pattern.equals(trimmed, ignoreCase = true) &&
                it.field == draft.field &&
                it.type == draft.type &&
                it.action == RuleAction.BLOCK
        }
        when {
            trimmed.isEmpty() -> Unit
            validation != null -> Toast.makeText(context, validation, Toast.LENGTH_SHORT).show()
            duplicate -> Toast.makeText(context, "'$trimmed' already blocked", Toast.LENGTH_SHORT).show()
            else -> {
                onQuickBlock(trimmed, quickRegex)
                quickText = ""
                Toast.makeText(
                    context,
                    if (quickRegex) "Regex block added" else "Blocking '$trimmed'",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Settings Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = TextPrimary,
                    modifier = Modifier.size(22.dp)
                )
            }
            Text(
                text = "SETTINGS",
                fontSize = 18.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.5.sp,
                color = TextPrimary
            )
        }

        HorizontalDivider(color = DarkBorder, thickness = 1.dp)

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            // Master Active Switch & Expandable Advanced Card
            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, DarkBorder, RectangleShape),
                    shape = RectangleShape,
                    color = DarkSurface
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // Clean Master Active Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = "ACTIVE",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "Master firewall switch for incoming SMS",
                                    fontSize = 11.sp,
                                    color = TextMuted,
                                    lineHeight = 15.sp
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Switch(
                                checked = isMasterActive,
                                onCheckedChange = onMasterToggle,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = DarkBackground,
                                    checkedTrackColor = YellowAccent,
                                    uncheckedThumbColor = TextMuted,
                                    uncheckedTrackColor = DarkSurfaceVariant
                                )
                            )
                        }

                        HorizontalDivider(color = DarkBorder.copy(alpha = 0.5f), thickness = 1.dp)

                        // Simplified Expandable Advanced Header
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { isAdvancedExpanded = !isAdvancedExpanded }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "// ADVANCED",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = if (isMasterActive) YellowAccent else TextMuted
                            )

                            Icon(
                                imageVector = if (isAdvancedExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = "Toggle Advanced",
                                tint = if (isMasterActive) YellowAccent else TextMuted,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // Advanced Sub-Settings Dropdown
                        AnimatedVisibility(
                            visible = isAdvancedExpanded,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(DarkBackground.copy(alpha = 0.5f))
                                    .padding(horizontal = 16.dp, vertical = 12.dp)
                                    .alpha(if (isMasterActive) 1f else 0.4f),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Sub-toggle: SPAM FILTER
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        Text(
                                            text = "SPAM FILTER",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 0.5.sp,
                                            color = TextPrimary
                                        )
                                        Text(
                                            text = if (isFilterEnabled) "Scan incoming SMS for spam keywords" else "Filter disabled (SMS & alerts still active)",
                                            fontSize = 11.sp,
                                            color = TextMuted,
                                            lineHeight = 15.sp
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Switch(
                                        checked = isFilterEnabled && isMasterActive,
                                        onCheckedChange = { if (isMasterActive) onFilterToggle(it) },
                                        enabled = isMasterActive,
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = DarkBackground,
                                            checkedTrackColor = YellowAccent,
                                            uncheckedThumbColor = TextMuted,
                                            uncheckedTrackColor = DarkSurfaceVariant,
                                            disabledCheckedThumbColor = DarkBackground,
                                            disabledCheckedTrackColor = YellowAccent.copy(alpha = 0.4f)
                                        )
                                    )
                                }

                                HorizontalDivider(color = DarkBorder.copy(alpha = 0.5f), thickness = 1.dp)

                                // Full detection engine (the only rule editor)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(enabled = isMasterActive) { onOpenRules() }
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = "DETECTION RULES",
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                letterSpacing = 0.5.sp,
                                                color = TextPrimary
                                            )
                                            Text(
                                                text = "[ ${rules.size} ]",
                                                fontSize = 10.sp,
                                                fontFamily = FontFamily.Monospace,
                                                color = YellowAccent
                                            )
                                        }
                                        Text(
                                            text = "Regex, sender, allow-list, mute. Ordered; ALLOW always wins.",
                                            fontSize = 11.sp,
                                            color = TextMuted,
                                            lineHeight = 15.sp
                                        )
                                    }

                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                        contentDescription = "Open rules",
                                        tint = if (isMasterActive) YellowAccent else TextMuted,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Quick block + all rules as chips
            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, DarkBorder, RectangleShape)
                        .alpha(if (isMasterActive) 1f else 0.45f),
                    shape = RectangleShape,
                    color = DarkSurface
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "// QUICK BLOCK",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 1.sp,
                                color = TextSecondary
                            )
                            Text(
                                text = if (isMasterActive) "[ ${rules.size} RULES ]" else "[ PAUSED ]",
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = if (isMasterActive) YellowAccent else TextMuted
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            BasicTextField(
                                value = quickText,
                                onValueChange = { quickText = it },
                                enabled = isMasterActive,
                                singleLine = true,
                                cursorBrush = SolidColor(YellowAccent),
                                textStyle = TextStyle(
                                    color = TextPrimary,
                                    fontSize = 13.sp,
                                    fontFamily = FontFamily.Default
                                ),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = { submitQuickBlock() }),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                                    .background(DarkBackground)
                                    .border(
                                        1.dp,
                                        if (quickText.isNotEmpty()) YellowAccent else DarkBorder,
                                        RectangleShape
                                    )
                                    .padding(horizontal = 10.dp),
                                decorationBox = { innerTextField ->
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.CenterStart
                                    ) {
                                        if (quickText.isEmpty()) {
                                            Text(
                                                text = "Text in sender (title) or body",
                                                color = TextMuted,
                                                fontSize = 12.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        innerTextField()
                                    }
                                }
                            )
                            Button(
                                onClick = submitQuickBlock,
                                enabled = isMasterActive && quickText.isNotBlank(),
                                modifier = Modifier.height(44.dp),
                                shape = RectangleShape,
                                contentPadding = PaddingValues(horizontal = 12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = YellowAccent,
                                    contentColor = DarkBackground,
                                    disabledContainerColor = DarkSurfaceVariant,
                                    disabledContentColor = TextMuted
                                )
                            ) {
                                Text(
                                    "BLOCK",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 12.sp,
                                    letterSpacing = 1.sp,
                                    maxLines = 1
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "MATCH",
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                color = TextMuted
                            )
                            ModeChip("TEXT", selected = !quickRegex) { quickRegex = false }
                            ModeChip("REGEX", selected = quickRegex) { quickRegex = true }
                            Spacer(modifier = Modifier.weight(1f))
                            Text(
                                text = if (quickRegex) "sender + body · regex" else "sender + body",
                                fontSize = 9.sp,
                                color = TextMuted,
                                maxLines = 1
                            )
                        }

                        HorizontalDivider(color = DarkBorder.copy(alpha = 0.5f), thickness = 1.dp)

                        if (rules.isEmpty()) {
                            Text(
                                text = "No rules yet.",
                                color = TextMuted,
                                fontSize = 12.sp
                            )
                        } else {
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                rules.sortedBy { it.priority }.forEach { rule ->
                                    SettingsRuleChip(
                                        rule = rule,
                                        onDelete = { onDeleteRule(rule.id) }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Dedicated Feed Preferences Section
            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, DarkBorder, RectangleShape),
                    shape = RectangleShape,
                    color = DarkSurface
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "// FEED PREFERENCES",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.sp,
                            color = TextSecondary
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = "SHOW SPAM IN FEED",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp,
                                    color = TextPrimary
                                )
                                Text(
                                    text = if (isShowSpamInFeed) "Spam SMS visible in main inbox" else "Spam SMS hidden from main inbox (default)",
                                    fontSize = 11.sp,
                                    color = TextMuted,
                                    lineHeight = 15.sp
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Switch(
                                checked = isShowSpamInFeed,
                                onCheckedChange = onShowSpamInFeedToggle,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = DarkBackground,
                                    checkedTrackColor = YellowAccent,
                                    uncheckedThumbColor = TextMuted,
                                    uncheckedTrackColor = DarkSurfaceVariant
                                )
                            )
                        }
                    }
                }
            }

            // Backup / restore (rules + settings only)
            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, DarkBorder, RectangleShape),
                    shape = RectangleShape,
                    color = DarkSurface
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "// BACKUP",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.sp,
                            color = TextSecondary
                        )
                        Text(
                            text = "Export or restore rules and switches as a JSON file. Messages are never included.",
                            fontSize = 11.sp,
                            color = TextMuted,
                            lineHeight = 15.sp
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { backup.export() },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp),
                                shape = RectangleShape,
                                contentPadding = PaddingValues(horizontal = 8.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FileUpload,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    "EXPORT",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    letterSpacing = 1.sp,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                            OutlinedButton(
                                onClick = { backup.import() },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp),
                                shape = RectangleShape,
                                contentPadding = PaddingValues(horizontal = 8.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FileDownload,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    "IMPORT",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    letterSpacing = 1.sp,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }
                }
            }

            // Purge All Spam Button (same destructive styling as message DELETE)
            item {
                Button(
                    onClick = onDeleteAllSpam,
                    enabled = spamCount > 0,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DESTRUCTIVE_FILL,
                        contentColor = DESTRUCTIVE_TEXT,
                        disabledContainerColor = DarkSurfaceVariant,
                        disabledContentColor = TextMuted
                    ),
                    border = if (spamCount > 0) {
                        DESTRUCTIVE_BORDER
                    } else {
                        androidx.compose.foundation.BorderStroke(1.dp, DarkBorder)
                    },
                    shape = RectangleShape,
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteSweep,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (spamCount > 0) "PURGE ALL SPAM ($spamCount)" else "NO SPAM TO PURGE",
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp,
                        fontSize = 13.sp,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }
    }
}
