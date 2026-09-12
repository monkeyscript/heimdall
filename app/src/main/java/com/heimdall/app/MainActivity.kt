package com.heimdall.app

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.format.DateUtils
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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
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
import com.heimdall.app.data.InspectedMessage
import com.heimdall.app.data.PreferencesManager
import com.heimdall.app.ui.theme.*
import com.heimdall.app.util.CategoryHelper
import com.heimdall.app.util.ClickableLinkifiedText
import com.heimdall.app.util.MessageCategory
import com.heimdall.app.util.NotificationHelper
import com.heimdall.app.util.OtpHelper
import com.heimdall.app.util.SmsRoleHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

enum class AppScreen {
    INBOX,
    SETTINGS
}

// Static, cached formatters to eliminate garbage collection churn on scroll
private val TIME_FORMAT = SimpleDateFormat("hh:mm a", Locale.getDefault())
private val DATE_FORMAT = SimpleDateFormat("dd MMM", Locale.getDefault())
private val FULL_DATE_FORMAT = SimpleDateFormat("MMM dd, yyyy • hh:mm a", Locale.getDefault())

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
            HeimdallTheme {
                HeimdallApp(
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
fun HeimdallApp(
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
    var keywordsList by remember { mutableStateOf(prefsManager.getKeywords().sorted()) }
    var isMasterActive by remember { mutableStateOf(prefsManager.isMasterActive()) }
    var isFilterEnabled by remember { mutableStateOf(prefsManager.isFilterEnabled()) }
    var isShowSpamInFeed by remember { mutableStateOf(prefsManager.isShowSpamInFeed()) }

    val defaultSmsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        isDefaultSms = SmsRoleHelper.isDefaultSmsApp(context)
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
                inspectedLogs = prefsManager.getInspectedMessages()
                selectedMessageForModal = targetMsg.copy(isRead = true)
                currentScreen = AppScreen.INBOX
            }
            onDirectOpenConsumed()
        }
    }

    // Permission state
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> }

    LaunchedEffect(Unit) {
        val needed = mutableListOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            needed.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        val notGranted = needed.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (notGranted.isNotEmpty()) {
            permissionLauncher.launch(notGranted.toTypedArray())
        }
    }

    LaunchedEffect(currentScreen) {
        isDefaultSms = SmsRoleHelper.isDefaultSmsApp(context)
        inspectedLogs = prefsManager.getInspectedMessages()
        keywordsList = prefsManager.getKeywords().sorted()
        isMasterActive = prefsManager.isMasterActive()
        isFilterEnabled = prefsManager.isFilterEnabled()
        isShowSpamInFeed = prefsManager.isShowSpamInFeed()
    }

    // One-time initial load of existing inbox messages once default SMS or READ_SMS is available
    LaunchedEffect(isDefaultSms) {
        val hasReadPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_SMS
        ) == PackageManager.PERMISSION_GRANTED
        if ((isDefaultSms || hasReadPermission) && !prefsManager.hasImportedInitialSms()) {
            withContext(Dispatchers.IO) {
                val keywords = prefsManager.getKeywords()
                val oldMessages = SmsRoleHelper.readExistingInboxMessages(context, keywords = keywords, limit = 100)
                if (oldMessages.isNotEmpty()) {
                    prefsManager.importInitialMessages(oldMessages)
                }
                prefsManager.setImportedInitialSms(true)
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

    BackHandler(enabled = currentScreen == AppScreen.SETTINGS) {
        currentScreen = AppScreen.INBOX
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
                        SmsRoleHelper.deleteFromTelephonyProvider(context, message.timestamp)
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
                                    SmsRoleHelper.deleteFromTelephonyProvider(context, msg.timestamp)
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
                    onOpenSettings = { currentScreen = AppScreen.SETTINGS },
                    onSelectMessage = { message ->
                        prefsManager.markMessageAsRead(message.timestamp)
                        inspectedLogs = prefsManager.getInspectedMessages()
                        selectedMessageForModal = message.copy(isRead = true)
                    },
                    onMarkAllAsRead = {
                        val count = prefsManager.markAllAsRead()
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
                    isDefaultSms = isDefaultSms,
                    keywordsList = keywordsList,
                    messages = inspectedLogs,
                    onMasterToggle = { enabled ->
                        isMasterActive = enabled
                        prefsManager.setMasterActive(enabled)
                    },
                    onFilterToggle = { enabled ->
                        isFilterEnabled = enabled
                        prefsManager.setFilterEnabled(enabled)
                    },
                    onShowSpamInFeedToggle = { enabled ->
                        isShowSpamInFeed = enabled
                        prefsManager.setShowSpamInFeed(enabled)
                    },
                    onRequestDefaultSms = {
                        defaultSmsLauncher.launch(SmsRoleHelper.createDefaultSmsIntent(context))
                    },
                    onKeywordsUpdated = {
                        keywordsList = prefsManager.getKeywords().sorted()
                    },
                    onDeleteAllSpam = {
                        val count = prefsManager.deleteAllSpam()
                        inspectedLogs = prefsManager.getInspectedMessages()
                        Toast.makeText(context, "Purged $count spam message(s)", Toast.LENGTH_SHORT).show()
                    },
                    onBack = { currentScreen = AppScreen.INBOX },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                )
            }
        }
    }
}

@Composable
fun InboxScreen(
    uiMessages: List<UiMessageItem>,
    showSpamInFeed: Boolean,
    onOpenSettings: () -> Unit,
    onSelectMessage: (InspectedMessage) -> Unit,
    onMarkAllAsRead: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showOnlyUnread by remember { mutableStateOf(false) }
    var rawSearchQuery by remember { mutableStateOf("") }
    var debouncedSearchQuery by remember { mutableStateOf("") }
    var visibleItemCount by remember { mutableIntStateOf(25) }

    val unreadCount = remember(uiMessages, showSpamInFeed) {
        uiMessages.count { !it.raw.isRead && (showSpamInFeed || !it.raw.isSpam) }
    }

    // 200ms Search Debounce
    LaunchedEffect(rawSearchQuery) {
        delay(200L)
        debouncedSearchQuery = rawSearchQuery
    }

    // Reset pagination when search, unread, or showSpamInFeed changes
    LaunchedEffect(debouncedSearchQuery, showOnlyUnread, showSpamInFeed) {
        visibleItemCount = 25
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

    val displayedMessages = remember(filteredMessages, visibleItemCount) {
        filteredMessages.take(visibleItemCount)
    }

    val remainingCount = filteredMessages.size - displayedMessages.size

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
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.RemoveRedEye,
                    contentDescription = null,
                    tint = YellowAccent,
                    modifier = Modifier.size(22.dp)
                )

                Text(
                    text = "HEIMDALL",
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
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                items(
                    items = displayedMessages,
                    key = { it.raw.timestamp },
                    contentType = { "message_row" }
                ) { item ->
                    InboxMessageRow(
                        item = item,
                        onClick = { onSelectMessage(item.raw) }
                    )
                    HorizontalDivider(color = DarkBorder.copy(alpha = 0.4f), thickness = 1.dp)
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

// 100% O(1) Fast Composable Row
@Composable
fun InboxMessageRow(
    item: UiMessageItem,
    onClick: () -> Unit
) {
    val message = item.raw
    val category = item.category
    val displayTimestamp = item.formattedTime

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Unified Dark Surface Background
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(DarkSurfaceVariant)
                .border(1.dp, DarkBorder, RectangleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = category.emoji,
                fontSize = 16.sp
            )
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
                        fontWeight = if (!message.isRead) FontWeight.Black else FontWeight.Bold,
                        color = if (!message.isRead) TextPrimary else TextSecondary
                    )
                    if (message.isSpam && !message.matchedKeyword.isNullOrEmpty()) {
                        Surface(
                            shape = RectangleShape,
                            color = YellowAccentSubtle,
                            border = androidx.compose.foundation.BorderStroke(1.dp, YellowAccent.copy(alpha = 0.4f))
                        ) {
                            Text(
                                text = message.matchedKeyword.uppercase(),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = YellowAccent,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                // Timestamp + Minimal Unread Dot
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = displayTimestamp,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = if (!message.isRead) YellowAccent else TextMuted
                    )

                    // Minimal Unread Indicator Dot
                    if (!message.isRead) {
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
                color = if (!message.isRead) TextPrimary else TextSecondary,
                fontWeight = if (!message.isRead) FontWeight.Medium else FontWeight.Normal,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 18.sp
            )
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
                // Header: // SENDER NAME in yellow monospace, Category + Timestamp below it
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "// ${message.sender.uppercase()}",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.sp,
                            color = YellowAccent
                        )

                        // Category & Timestamp below sender name
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "${category.emoji} ${category.label}${if (message.isSpam && !message.matchedKeyword.isNullOrEmpty()) " [${message.matchedKeyword.uppercase()}]" else ""}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = if (message.isSpam || category == MessageCategory.OTP) YellowAccent else TextSecondary
                            )
                            Text(
                                text = "•",
                                fontSize = 11.sp,
                                color = TextMuted
                            )
                            Text(
                                text = fullTimeString,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = TextMuted
                            )
                        }
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
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (message.isSpam) YellowAccent else Color(0xFFEF4444).copy(alpha = 0.2f),
                                contentColor = if (message.isSpam) DarkBackground else Color(0xFFEF4444)
                            ),
                            border = if (!message.isSpam) androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.4f)) else null
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("DELETE", fontWeight = FontWeight.Bold, fontSize = 13.sp, letterSpacing = 1.sp)
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
                                val clip = ClipData.newPlainText("Heimdall SMS", message.body)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier
                                .weight(1f)
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
                                text = "COPY",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                letterSpacing = 1.sp
                            )
                        }

                        Button(
                            onClick = onDelete,
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp),
                            shape = RectangleShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (message.isSpam) YellowAccent else Color(0xFFEF4444).copy(alpha = 0.2f),
                                contentColor = if (message.isSpam) DarkBackground else Color(0xFFEF4444)
                            ),
                            border = if (!message.isSpam) androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.4f)) else null
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("DELETE", fontWeight = FontWeight.Bold, fontSize = 13.sp, letterSpacing = 1.sp)
                        }
                    }
                }
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
    isDefaultSms: Boolean,
    keywordsList: List<String>,
    messages: List<InspectedMessage>,
    onMasterToggle: (Boolean) -> Unit,
    onFilterToggle: (Boolean) -> Unit,
    onShowSpamInFeedToggle: (Boolean) -> Unit,
    onRequestDefaultSms: () -> Unit,
    onKeywordsUpdated: () -> Unit,
    onDeleteAllSpam: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var newKeywordText by remember { mutableStateOf("") }
    var isAdvancedExpanded by remember { mutableStateOf(false) }
    val spamCount = remember(messages) { messages.count { it.isSpam } }

    val onAddKeyword = {
        val trimmed = newKeywordText.trim()
        if (trimmed.isEmpty()) {
            Toast.makeText(context, "Please enter a keyword", Toast.LENGTH_SHORT).show()
        } else if (keywordsList.any { it.equals(trimmed, ignoreCase = true) }) {
            Toast.makeText(context, "'$trimmed' already exists", Toast.LENGTH_SHORT).show()
        } else {
            prefsManager.addKeyword(trimmed)
            onKeywordsUpdated()
            newKeywordText = ""
            keyboardController?.hide()
            Toast.makeText(context, "Added '$trimmed'", Toast.LENGTH_SHORT).show()
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
                            Text(
                                text = "ACTIVE",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp,
                                color = TextPrimary
                            )

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
                            }
                        }
                    }
                }
            }

            // Dedicated Default SMS App Card
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
                            text = "// DEFAULT SMS APP",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.sp,
                            color = TextSecondary
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(
                                        if (isDefaultSms) Color(0xFF10B981) else YellowAccent,
                                        CircleShape
                                    )
                            )
                            Text(
                                text = if (isDefaultSms) "HEIMDALL IS DEFAULT SMS APP" else "NOT DEFAULT SMS APP",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 0.5.sp,
                                color = if (isDefaultSms) Color(0xFF10B981) else YellowAccent
                            )
                        }

                        Text(
                            text = if (isDefaultSms) {
                                "Spam is intercepted silently and kept out of system SMS storage. Clean messages sync automatically to Android's database."
                            } else {
                                "Set Heimdall as default SMS app to silently intercept spam, prevent spam from polluting system SMS, and enable system message deletion."
                            },
                            fontSize = 11.sp,
                            color = TextMuted,
                            lineHeight = 16.sp
                        )

                        if (isDefaultSms) {
                            OutlinedButton(
                                onClick = onRequestDefaultSms,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(42.dp),
                                shape = RectangleShape,
                                border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SwapHoriz,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "SWITCH / MANAGE DEFAULT APP",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    letterSpacing = 1.sp
                                )
                            }
                        } else {
                            Button(
                                onClick = onRequestDefaultSms,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp),
                                shape = RectangleShape,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = YellowAccent,
                                    contentColor = DarkBackground
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Shield,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = DarkBackground
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "SET AS DEFAULT SMS APP",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 12.sp,
                                    letterSpacing = 1.sp
                                )
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

            // Aligned Section: SPAM RULES
            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, DarkBorder, RectangleShape)
                        .alpha(if (isMasterActive && isFilterEnabled) 1f else 0.45f),
                    shape = RectangleShape,
                    color = DarkSurface
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "// SPAM RULES",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 1.sp,
                                color = TextSecondary
                            )
                            Text(
                                text = if (isFilterEnabled) "[ ${keywordsList.size} ACTIVE ]" else "[ PAUSED ]",
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = if (isFilterEnabled) YellowAccent else TextMuted
                            )
                        }

                        // Pixel-Perfect Input Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            BasicTextField(
                                value = newKeywordText,
                                onValueChange = { newKeywordText = it },
                                singleLine = true,
                                textStyle = TextStyle(
                                    color = TextPrimary,
                                    fontSize = 14.sp,
                                    fontFamily = FontFamily.Default
                                ),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = { onAddKeyword() }),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .background(DarkBackground)
                                    .border(
                                        1.dp,
                                        if (newKeywordText.isNotEmpty()) YellowAccent else DarkBorder,
                                        RectangleShape
                                    )
                                    .padding(horizontal = 12.dp),
                                decorationBox = { innerTextField ->
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.CenterStart
                                    ) {
                                        if (newKeywordText.isEmpty()) {
                                            Text(
                                                text = "e.g. loan, crypto, winner",
                                                color = TextMuted,
                                                fontSize = 13.sp
                                            )
                                        }
                                        innerTextField()
                                    }
                                }
                            )

                            Button(
                                onClick = onAddKeyword,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = YellowAccent,
                                    contentColor = DarkBackground
                                ),
                                shape = RectangleShape,
                                modifier = Modifier.height(48.dp)
                            ) {
                                Text("ADD", fontWeight = FontWeight.Black, fontSize = 13.sp, letterSpacing = 1.sp)
                            }
                        }

                        // Tag Chips
                        if (keywordsList.isEmpty()) {
                            Text(
                                text = "No active keyword rules.",
                                color = TextMuted,
                                fontSize = 13.sp
                            )
                        } else {
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                keywordsList.forEach { keyword ->
                                    TechKeywordChip(
                                        keyword = keyword,
                                        onDelete = {
                                            prefsManager.removeKeyword(keyword)
                                            onKeywordsUpdated()
                                            Toast.makeText(context, "Removed '$keyword'", Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Purge All Spam Button
            item {
                Button(
                    onClick = onDeleteAllSpam,
                    enabled = spamCount > 0,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = YellowAccentSubtle,
                        contentColor = YellowAccent,
                        disabledContainerColor = DarkSurfaceVariant,
                        disabledContentColor = TextMuted
                    ),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (spamCount > 0) YellowAccent.copy(alpha = 0.5f) else DarkBorder
                    ),
                    shape = RectangleShape,
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
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

// Sharp Futuristic Tech Tag Chip
@Composable
fun TechKeywordChip(
    keyword: String,
    onDelete: () -> Unit
) {
    Surface(
        shape = RectangleShape,
        color = DarkBackground,
        border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = keyword.uppercase(),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = FontFamily.Monospace,
                color = TextPrimary
            )
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .clickable { onDelete() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Delete $keyword",
                    tint = TextMuted,
                    modifier = Modifier.size(12.dp)
                )
            }
        }
    }
}
