package com.heimdall.app

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
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
import com.heimdall.app.util.NotificationHelper
import com.heimdall.app.util.OtpHelper
import java.text.SimpleDateFormat
import java.util.*

enum class AppScreen {
    INBOX,
    SETTINGS
}

class MainActivity : ComponentActivity() {
    private lateinit var prefsManager: PreferencesManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefsManager = PreferencesManager(this)
        enableEdgeToEdge()
        setContent {
            HeimdallTheme {
                HeimdallApp(prefsManager = prefsManager)
            }
        }
    }
}

// Smart timestamp formatter: time for today, date for older days
fun formatMessageTimestamp(timestamp: Long): String {
    val now = Calendar.getInstance()
    val msgCal = Calendar.getInstance().apply { timeInMillis = timestamp }

    val isSameDay = now.get(Calendar.YEAR) == msgCal.get(Calendar.YEAR) &&
            now.get(Calendar.DAY_OF_YEAR) == msgCal.get(Calendar.DAY_OF_YEAR)

    return if (isSameDay) {
        SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(timestamp))
    } else {
        SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date(timestamp))
    }
}

@Composable
fun HeimdallApp(prefsManager: PreferencesManager) {
    val context = LocalContext.current
    var currentScreen by remember { mutableStateOf(AppScreen.INBOX) }
    var inspectedLogs by remember { mutableStateOf(prefsManager.getInspectedMessages()) }
    var keywordsList by remember { mutableStateOf(prefsManager.getKeywords().sorted()) }
    var isShieldOn by remember { mutableStateOf(prefsManager.isShieldEnabled()) }

    // Modal state for viewing full message
    var selectedMessageForModal by remember { mutableStateOf<InspectedMessage?>(null) }
    var cleanMessageToDelete by remember { mutableStateOf<InspectedMessage?>(null) }

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
        inspectedLogs = prefsManager.getInspectedMessages()
        keywordsList = prefsManager.getKeywords().sorted()
        isShieldOn = prefsManager.isShieldEnabled()
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
                    .padding(horizontal = 32.dp) // Standard 32px margin
                    .border(1.dp, DarkBorder, RectangleShape),
                shape = RectangleShape,
                color = DarkSurface
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp), // 8px system: 24dp
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
                        horizontalArrangement = Arrangement.spacedBy(8.dp) // 8px system: 8dp
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
                    messages = inspectedLogs,
                    onOpenSettings = { currentScreen = AppScreen.SETTINGS },
                    onSelectMessage = { message -> selectedMessageForModal = message },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                )
            }
            AppScreen.SETTINGS -> {
                SettingsScreen(
                    prefsManager = prefsManager,
                    isShieldOn = isShieldOn,
                    keywordsList = keywordsList,
                    messages = inspectedLogs,
                    onShieldToggle = { enabled ->
                        isShieldOn = enabled
                        prefsManager.setShieldEnabled(enabled)
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
                    onSimulateTest = { _ ->
                        inspectedLogs = prefsManager.getInspectedMessages()
                    },
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
    messages: List<InspectedMessage>,
    onOpenSettings: () -> Unit,
    onSelectMessage: (InspectedMessage) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        // Clean Minimal Header: Raw Icon Glyph + HEIMDALL + Settings Ghost Button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp), // 8px system: 16dp
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp) // 8px system: 10dp
            ) {
                // Pure Naked Icon Glyph
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

            // Borderless Ghost Settings Button
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

        // Messages Feed
        if (messages.isEmpty()) {
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
                        text = "// NO MESSAGES DETECTED",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp,
                        color = TextSecondary
                    )
                    Text(
                        text = "Incoming SMS messages will be intercepted and logged here.\nTap Settings to test or manage keywords.",
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
                items(messages, key = { it.timestamp }) { message ->
                    InboxMessageRow(
                        message = message,
                        onClick = { onSelectMessage(message) }
                    )
                    HorizontalDivider(color = DarkBorder.copy(alpha = 0.4f), thickness = 1.dp)
                }
            }
        }
    }
}

@Composable
fun InboxMessageRow(
    message: InspectedMessage,
    onClick: () -> Unit
) {
    val displayTimestamp = remember(message.timestamp) { formatMessageTimestamp(message.timestamp) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp), // 8px system
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Geometric Status Indicator Box with 1px Border
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(if (message.isSpam) YellowAccentSubtle else DarkSurfaceVariant)
                .border(
                    1.dp,
                    if (message.isSpam) YellowAccent.copy(alpha = 0.5f) else DarkBorder,
                    RectangleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (message.isSpam) "⚠️" else "🛡️",
                fontSize = 15.sp
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
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
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

                // Smart Timestamp (Time if today, Date if older)
                Text(
                    text = displayTimestamp,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = TextMuted
                )
            }

            Text(
                text = message.body,
                fontSize = 13.sp,
                color = TextSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 18.sp
            )
        }

        // Minimalist Chevron
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = "View Details",
            tint = TextMuted,
            modifier = Modifier
                .size(18.dp)
                .align(Alignment.CenterVertically)
        )
    }
}

// Standardized Wide Message Details Modal (Strictly 2 buttons: [COPY / COPY <OTP>] and [DELETE])
@Composable
fun MessageDetailModal(
    message: InspectedMessage,
    onDismiss: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val fullTimeFormat = remember { SimpleDateFormat("MMM dd, yyyy • hh:mm a", Locale.getDefault()) }
    val fullTimeString = remember(message.timestamp) { fullTimeFormat.format(Date(message.timestamp)) }
    val extractedOtp = remember(message.body) { OtpHelper.extractOtp(message.body) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp) // Standard 32px margin
                .border(1.dp, DarkBorder, RectangleShape),
            shape = RectangleShape,
            color = DarkSurface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp), // 8px system: 24dp
                verticalArrangement = Arrangement.spacedBy(16.dp) // 8px system: 16dp
            ) {
                // Header: // SENDER NAME in yellow monospace, Emoji + Timestamp below it
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

                        // Emoji & Timestamp below sender name
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = if (message.isSpam) "⚠️ SPAM [${message.matchedKeyword?.uppercase() ?: ""}]" else "🛡️ CLEAN",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = if (message.isSpam) YellowAccent else TextSecondary
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

                // Message Text in Dark Container
                Surface(
                    shape = RectangleShape,
                    color = DarkBackground,
                    border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 240.dp)
                ) {
                    Text(
                        text = message.body,
                        fontSize = 14.sp,
                        color = TextPrimary,
                        lineHeight = 22.sp,
                        modifier = Modifier
                            .padding(16.dp)
                            .verticalScroll(rememberScrollState())
                    )
                }

                // Strictly 2 Action Buttons: [COPY / COPY <OTP>] and [DELETE]
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Smart Copy Button: copies OTP if present, otherwise copies full message
                    OutlinedButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            if (extractedOtp != null) {
                                val clip = ClipData.newPlainText("OTP Code", extractedOtp)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "Copied OTP: $extractedOtp", Toast.LENGTH_SHORT).show()
                            } else {
                                val clip = ClipData.newPlainText("Heimdall SMS", message.body)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                            }
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
                            text = if (extractedOtp != null) "COPY $extractedOtp" else "COPY",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            letterSpacing = 1.sp
                        )
                    }

                    // Delete Button
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    prefsManager: PreferencesManager,
    isShieldOn: Boolean,
    keywordsList: List<String>,
    messages: List<InspectedMessage>,
    onShieldToggle: (Boolean) -> Unit,
    onKeywordsUpdated: () -> Unit,
    onDeleteAllSpam: () -> Unit,
    onSimulateTest: (Boolean) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var newKeywordText by remember { mutableStateOf("") }
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

    val onRunSimulation = { isSpam: Boolean, isOtp: Boolean ->
        val sender = when {
            isOtp -> "HDFC-BANK"
            isSpam -> "987521376514"
            else -> "+919876543210"
        }
        val body = when {
            isOtp -> "Your secret OTP for transaction of Rs. 4,500 is 21321. Valid for 10 mins. Do not share."
            isSpam -> "Congratulations! Your pre-approved personal loan of Rs. 5,00,000 is ready. Apply now."
            else -> "Hey Rahul, are you free for a quick call today afternoon?"
        }
        val matched = if (isSpam) "loan" else null

        if (isSpam) prefsManager.incrementBlockedCount()

        val msg = InspectedMessage(
            timestamp = System.currentTimeMillis(),
            sender = sender,
            body = body,
            isSpam = isSpam,
            matchedKeyword = matched
        )
        prefsManager.addInspectedMessage(msg)
        onSimulateTest(isSpam)

        NotificationHelper.showInspectionNotification(
            context = context,
            sender = sender,
            body = body,
            isSpam = isSpam,
            matchedKeyword = matched
        )
        Toast.makeText(context, "Pushed ${if (isOtp) "OTP 🔑" else if (isSpam) "SPAM ⚠️" else "CLEAN 🛡️"} alert", Toast.LENGTH_SHORT).show()
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Settings Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp), // 8px system: 16dp
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
                .padding(horizontal = 16.dp), // 8px system: 16dp
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            // Master Shield Row
            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, DarkBorder, RectangleShape),
                    shape = RectangleShape,
                    color = DarkSurface
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp), // 8px system
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = "ACTIVE",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp,
                                color = TextPrimary
                            )
                            Text(
                                text = if (isShieldOn) "// SHIELD RUNNING" else "// SHIELD PAUSED",
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                color = if (isShieldOn) YellowAccent else TextMuted
                            )
                        }

                        Switch(
                            checked = isShieldOn,
                            onCheckedChange = onShieldToggle,
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

            // Keyword Management Section
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
                            .padding(16.dp), // 8px system: 16dp
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "// FILTER RULES",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 1.sp,
                                color = TextSecondary
                            )
                            Text(
                                text = "[ ${keywordsList.size} ACTIVE ]",
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = YellowAccent
                            )
                        }

                        // Pixel-Perfect Vertically Centered Input Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp) // 8px system: 8dp
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

            // Test Notifications Section (with Test OTP, Test Spam, Test Clean)
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
                            .padding(16.dp), // 8px system: 16dp
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "// TEST NOTIFICATIONS",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.sp,
                            color = TextSecondary
                        )

                        // 3-way test buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp) // 8px system
                        ) {
                            Button(
                                onClick = { onRunSimulation(false, true) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = YellowAccent,
                                    contentColor = DarkBackground
                                ),
                                shape = RectangleShape,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                            ) {
                                Text("OTP 🔑", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }

                            Button(
                                onClick = { onRunSimulation(true, false) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = DarkSurfaceVariant,
                                    contentColor = YellowAccent
                                ),
                                border = androidx.compose.foundation.BorderStroke(1.dp, YellowAccent.copy(alpha = 0.4f)),
                                shape = RectangleShape,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                            ) {
                                Text("SPAM ⚠️", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }

                            OutlinedButton(
                                onClick = { onRunSimulation(false, false) },
                                border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                                shape = RectangleShape,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                            ) {
                                Text("CLEAN 🛡️", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
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
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp), // 8px system
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
