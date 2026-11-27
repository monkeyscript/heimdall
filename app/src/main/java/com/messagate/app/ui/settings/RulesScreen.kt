package com.messagate.app.ui.settings

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
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
import com.messagate.app.core.engine.CompiledRule
import com.messagate.app.core.engine.MatchField
import com.messagate.app.core.engine.MatchType
import com.messagate.app.core.engine.Rule
import com.messagate.app.core.engine.RuleAction
import com.messagate.app.core.engine.RuleEngine
import com.messagate.app.data.PreferencesManager
import com.messagate.app.ui.theme.*

private val ERROR_RED = Color(0xFFEF4444)

private fun actionColor(action: RuleAction): Color = when (action) {
    RuleAction.BLOCK -> ERROR_RED
    RuleAction.ALLOW -> Color(0xFF34D399)
    RuleAction.TAG -> YellowAccent
    RuleAction.MUTE -> TextSecondary
}

@Composable
fun RulesScreen(
    rules: List<Rule>,
    prefsManager: PreferencesManager,
    onSaveRule: (Rule) -> Unit,
    onDeleteRule: (String) -> Unit,
    onDeleteRules: (Set<String>) -> Unit,
    onToggleRule: (Rule) -> Unit,
    onMoveRule: (Rule, Int) -> Unit,
    onDataImported: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val ordered = remember(rules) { rules.sortedBy { it.priority } }
    var editorRule by remember { mutableStateOf<Rule?>(null) }
    var quickKeyword by remember { mutableStateOf("") }
    var selectedRuleIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    val isSelectionMode = selectedRuleIds.isNotEmpty()
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val backup = rememberBackupLaunchers(prefsManager, onDataImported)

    // Drop selections that no longer exist (e.g. after an import)
    LaunchedEffect(rules) {
        val existing = rules.map { it.id }.toSet()
        selectedRuleIds = selectedRuleIds.intersect(existing)
    }

    BackHandler(enabled = isSelectionMode) {
        selectedRuleIds = emptySet()
    }

    editorRule?.let { editing ->
        RuleEditorDialog(
            initial = editing,
            isNew = ordered.none { it.id == editing.id },
            onDismiss = { editorRule = null },
            onSave = { saved ->
                onSaveRule(saved)
                editorRule = null
            }
        )
    }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = TextPrimary,
                    modifier = Modifier.size(22.dp)
                )
            }
            Text(
                text = "DETECTION RULES",
                fontSize = 16.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.2.sp,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { backup.import() }, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = Icons.Default.FileDownload,
                    contentDescription = "Import rules",
                    tint = TextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
            IconButton(onClick = { backup.export() }, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = Icons.Default.FileUpload,
                    contentDescription = "Export rules",
                    tint = TextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
            IconButton(
                onClick = {
                    editorRule = Rule(
                        priority = (ordered.maxOfOrNull { it.priority } ?: -10) + 10
                    )
                },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "New rule",
                    tint = YellowAccent,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        HorizontalDivider(color = DarkBorder, thickness = 1.dp)

        if (isSelectionMode) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkSurface)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        val ids = selectedRuleIds
                        selectedRuleIds = emptySet()
                        onDeleteRules(ids)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp),
                    shape = RectangleShape,
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ERROR_RED.copy(alpha = 0.2f),
                        contentColor = ERROR_RED
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, ERROR_RED.copy(alpha = 0.6f))
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "DELETE (${selectedRuleIds.size})",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        letterSpacing = 1.sp,
                        maxLines = 1,
                        softWrap = false
                    )
                }
                IconButton(onClick = { selectedRuleIds = emptySet() }, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Cancel selection",
                        tint = TextPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            HorizontalDivider(color = DarkBorder.copy(alpha = 0.5f), thickness = 1.dp)
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text(
                    text = "Rules run top to bottom. ALLOW rules are always checked first, so a trusted sender can never be blocked. A BLOCK match is dropped before it reaches Android's SMS database.",
                    fontSize = 11.sp,
                    color = TextMuted,
                    lineHeight = 16.sp
                )
            }

            // One-field shortcut: keyword -> CONTAINS/BODY/BLOCK at the top of the list
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    BasicTextField(
                        value = quickKeyword,
                        onValueChange = { quickKeyword = it },
                        singleLine = true,
                        cursorBrush = SolidColor(YellowAccent),
                        textStyle = TextStyle(
                            color = TextPrimary,
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Default
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .background(DarkBackground)
                            .border(
                                1.dp,
                                if (quickKeyword.isNotEmpty()) YellowAccent else DarkBorder,
                                RectangleShape
                            )
                            .padding(horizontal = 10.dp),
                        decorationBox = { innerTextField ->
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
                                if (quickKeyword.isEmpty()) {
                                    Text(
                                        text = "Quick block: keyword or phrase",
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
                        onClick = {
                            val trimmed = quickKeyword.trim()
                            if (trimmed.isEmpty()) return@Button
                            val duplicate = ordered.any {
                                it.pattern.equals(trimmed, ignoreCase = true) &&
                                    it.field == MatchField.BODY &&
                                    it.type == MatchType.CONTAINS &&
                                    it.action == RuleAction.BLOCK
                            }
                            if (duplicate) {
                                Toast.makeText(context, "'$trimmed' is already blocked", Toast.LENGTH_SHORT).show()
                            } else {
                                onSaveRule(
                                    Rule(
                                        name = trimmed,
                                        pattern = trimmed,
                                        // Negative priority puts a quick block first; the list is re-indexed on save.
                                        priority = (ordered.minOfOrNull { it.priority } ?: 10) - 10,
                                        action = RuleAction.BLOCK
                                    )
                                )
                                quickKeyword = ""
                                Toast.makeText(context, "Blocking '$trimmed'", Toast.LENGTH_SHORT).show()
                            }
                        },
                        enabled = quickKeyword.isNotBlank(),
                        modifier = Modifier.height(44.dp),
                        shape = RectangleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = YellowAccent,
                            contentColor = DarkBackground,
                            disabledContainerColor = DarkSurfaceVariant,
                            disabledContentColor = TextMuted
                        )
                    ) {
                        Text("BLOCK", fontWeight = FontWeight.Black, fontSize = 12.sp, letterSpacing = 1.sp, maxLines = 1)
                    }
                }
            }

            if (ordered.isEmpty()) {
                item {
                    Surface(
                        shape = RectangleShape,
                        color = DarkSurface,
                        border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "// NO RULES",
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = TextSecondary
                            )
                            Text(
                                text = "Nothing is blocked. Add a rule to start filtering.",
                                fontSize = 12.sp,
                                color = TextMuted
                            )
                        }
                    }
                }
            }

            items(items = ordered, key = { it.id }) { rule ->
                RuleCard(
                    rule = rule,
                    isSelectionMode = isSelectionMode,
                    isSelected = rule.id in selectedRuleIds,
                    onToggleSelect = {
                        selectedRuleIds = if (rule.id in selectedRuleIds) {
                            selectedRuleIds - rule.id
                        } else {
                            selectedRuleIds + rule.id
                        }
                    },
                    onEdit = { editorRule = rule },
                    onToggle = { onToggleRule(rule) },
                    onMoveUp = { onMoveRule(rule, -1) },
                    onMoveDown = { onMoveRule(rule, +1) },
                    onDelete = { onDeleteRule(rule.id) }
                )
            }

            item {
                Button(
                    onClick = {
                        editorRule = Rule(
                            priority = (ordered.maxOfOrNull { it.priority } ?: -10) + 10
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RectangleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = YellowAccent,
                        contentColor = DarkBackground
                    )
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("NEW RULE", fontWeight = FontWeight.Black, fontSize = 13.sp, letterSpacing = 1.sp)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RuleCard(
    rule: Rule,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onToggleSelect: () -> Unit,
    onEdit: () -> Unit,
    onToggle: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    Surface(
        shape = RectangleShape,
        color = if (isSelected) ERROR_RED.copy(alpha = 0.08f) else DarkSurface,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isSelected) ERROR_RED.copy(alpha = 0.8f) else DarkBorder
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = { if (isSelectionMode) onToggleSelect() else onEdit() },
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onToggleSelect()
                    }
                )
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = rule.name.ifBlank { rule.pattern },
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (rule.enabled) TextPrimary else TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "#${rule.priority}",
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = TextMuted
                )
                if (isSelectionMode) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .background(if (isSelected) ERROR_RED else Color.Transparent)
                            .border(
                                1.dp,
                                if (isSelected) ERROR_RED else DarkBorder,
                                RectangleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Selected",
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                } else {
                    Switch(
                        checked = rule.enabled,
                        onCheckedChange = { onToggle() },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = DarkBackground,
                            checkedTrackColor = YellowAccent,
                            uncheckedThumbColor = TextMuted,
                            uncheckedTrackColor = DarkSurfaceVariant
                        )
                    )
                }
            }

            Text(
                text = rule.pattern,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                color = if (rule.enabled) TextSecondary else TextMuted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                MiniTag(rule.action.label, actionColor(rule.action))
                MiniTag(rule.field.label, TextSecondary)
                MiniTag(rule.type.label, TextSecondary)
                if (rule.action == RuleAction.TAG && !rule.categoryId.isNullOrBlank()) {
                    MiniTag(rule.categoryId, YellowAccent)
                }
                Spacer(modifier = Modifier.weight(1f))
                if (!isSelectionMode) {
                    IconButton(onClick = onMoveUp, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.ArrowUpward, "Move up", tint = TextMuted, modifier = Modifier.size(16.dp))
                    }
                    IconButton(onClick = onMoveDown, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.ArrowDownward, "Move down", tint = TextMuted, modifier = Modifier.size(16.dp))
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.DeleteOutline, "Delete rule", tint = ERROR_RED, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniTag(text: String, color: Color) {
    Surface(
        shape = RectangleShape,
        color = DarkBackground,
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.5f))
    ) {
        Text(
            text = text,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            color = color,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RuleEditorDialog(
    initial: Rule,
    isNew: Boolean,
    onDismiss: () -> Unit,
    onSave: (Rule) -> Unit
) {
    var draft by remember(initial.id) { mutableStateOf(initial) }
    var sampleSender by remember { mutableStateOf("") }
    var sampleBody by remember { mutableStateOf("") }
    val keyboardController = LocalSoftwareKeyboardController.current
    val configuration = LocalConfiguration.current
    val maxHeight = (configuration.screenHeightDp * 0.88f).dp

    val validationError = remember(draft) { CompiledRule.validate(draft) }
    val testSender = sampleSender.ifBlank { "TEST-SENDER" }
    val testMatched = remember(draft, sampleSender, sampleBody) {
        sampleBody.isNotBlank() && RuleEngine.testRule(draft, testSender, sampleBody)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .heightIn(max = maxHeight)
                .border(1.dp, DarkBorder, RectangleShape),
            shape = RectangleShape,
            color = DarkSurface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isNew) "// NEW RULE" else "// EDIT RULE",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp,
                        color = YellowAccent
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Close, "Close", tint = TextMuted, modifier = Modifier.size(16.dp))
                    }
                }

                EditorLabel("NAME")
                RuleTextField(
                    value = draft.name,
                    onValueChange = { draft = draft.copy(name = it) },
                    placeholder = "e.g. Loan spam"
                )

                EditorLabel("FIELD")
                ChoiceRow(
                    options = MatchField.values().map { it.label },
                    selectedIndex = draft.field.ordinal,
                    onSelect = { draft = draft.copy(field = MatchField.values()[it]) }
                )

                EditorLabel("MATCH TYPE")
                ChoiceRow(
                    options = MatchType.values().map { it.label },
                    selectedIndex = draft.type.ordinal,
                    onSelect = { draft = draft.copy(type = MatchType.values()[it]) }
                )

                EditorLabel(if (draft.type == MatchType.REGEX) "REGEX PATTERN" else "PATTERN")
                RuleTextField(
                    value = draft.pattern,
                    onValueChange = { draft = draft.copy(pattern = it) },
                    placeholder = if (draft.type == MatchType.REGEX) "\\b(loan|kredit)\\b" else "loan",
                    monospace = true
                )
                if (validationError != null && draft.pattern.isNotEmpty()) {
                    Text(
                        text = validationError,
                        fontSize = 11.sp,
                        color = ERROR_RED,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Text(
                    text = when (draft.type) {
                        MatchType.REGEX -> "Java regular expression, evaluated case-insensitively unless enabled below."
                        MatchType.WORD -> "Whole word/phrase only, no partial matches inside other words."
                        MatchType.CONTAINS -> "Substring match anywhere in the selected field."
                        MatchType.PREFIX -> "Field must start with the pattern."
                        MatchType.SUFFIX -> "Field must end with the pattern."
                        MatchType.EXACT -> "Field must equal the pattern exactly."
                    },
                    fontSize = 10.sp,
                    color = TextMuted,
                    lineHeight = 14.sp
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("CASE SENSITIVE", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        Text("Match exact upper/lower case", fontSize = 10.sp, color = TextMuted)
                    }
                    Switch(
                        checked = draft.caseSensitive,
                        onCheckedChange = { draft = draft.copy(caseSensitive = it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = DarkBackground,
                            checkedTrackColor = YellowAccent,
                            uncheckedThumbColor = TextMuted,
                            uncheckedTrackColor = DarkSurfaceVariant
                        )
                    )
                }

                EditorLabel("ACTION")
                ChoiceRow(
                    options = RuleAction.values().map { it.label },
                    selectedIndex = draft.action.ordinal,
                    onSelect = { draft = draft.copy(action = RuleAction.values()[it]) }
                )
                Text(
                    text = when (draft.action) {
                        RuleAction.BLOCK -> "Dropped silently before storage. No notification."
                        RuleAction.ALLOW -> "Whitelisted. Checked before every other rule."
                        RuleAction.TAG -> "Delivered normally, tagged with the category below."
                        RuleAction.MUTE -> "Delivered and logged, notification suppressed."
                    },
                    fontSize = 10.sp,
                    color = TextMuted,
                    lineHeight = 14.sp
                )

                if (draft.action == RuleAction.TAG) {
                    EditorLabel("CATEGORY")
                    val categories = remember {
                        com.messagate.app.util.MessageCategory.values()
                            .filter { it != com.messagate.app.util.MessageCategory.SPAM }
                    }
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        categories.forEach { category ->
                            ChoiceChip(
                                text = category.label,
                                selected = draft.categoryId == category.name,
                                onClick = { draft = draft.copy(categoryId = category.name) },
                                iconRes = category.iconRes
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("STOP AFTER MATCH", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        Text("Skip lower priority rules when this one matches", fontSize = 10.sp, color = TextMuted)
                    }
                    Switch(
                        checked = draft.stopProcessing,
                        onCheckedChange = { draft = draft.copy(stopProcessing = it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = DarkBackground,
                            checkedTrackColor = YellowAccent,
                            uncheckedThumbColor = TextMuted,
                            uncheckedTrackColor = DarkSurfaceVariant
                        )
                    )
                }

                HorizontalDivider(color = DarkBorder, thickness = 1.dp)

                EditorLabel("TEST BENCH")
                RuleTextField(
                    value = sampleSender,
                    onValueChange = { sampleSender = it },
                    placeholder = "Sender (optional)",
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                )
                RuleTextArea(
                    value = sampleBody,
                    onValueChange = { sampleBody = it },
                    placeholder = "Paste a sample SMS body..."
                )
                if (sampleBody.isNotBlank()) {
                    val verdict = when {
                        validationError != null -> "INVALID PATTERN"
                        !testMatched -> "NO MATCH"
                        draft.action == RuleAction.BLOCK -> "MATCH → BLOCK (dropped)"
                        draft.action == RuleAction.ALLOW -> "MATCH → ALLOW (whitelisted)"
                        draft.action == RuleAction.MUTE -> "MATCH → MUTE (no notification)"
                        else -> "MATCH → TAG ${draft.categoryId ?: "?"}"
                    }
                    Text(
                        text = verdict,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            validationError != null -> ERROR_RED
                            !testMatched -> TextMuted
                            else -> actionColor(draft.action)
                        }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        shape = RectangleShape,
                        border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary)
                    ) {
                        Text("CANCEL", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, letterSpacing = 1.sp)
                    }
                    Button(
                        onClick = {
                            keyboardController?.hide()
                            onSave(draft.copy(pattern = draft.pattern.trim()))
                        },
                        enabled = validationError == null,
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        shape = RectangleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = YellowAccent,
                            contentColor = DarkBackground,
                            disabledContainerColor = DarkSurfaceVariant,
                            disabledContentColor = TextMuted
                        )
                    ) {
                        Text("SAVE", fontWeight = FontWeight.Black, fontSize = 12.sp, letterSpacing = 1.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun EditorLabel(text: String) {
    Text(
        text = text,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = FontFamily.Monospace,
        letterSpacing = 1.sp,
        color = TextSecondary
    )
}

@Composable
private fun RuleTextArea(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    height: androidx.compose.ui.unit.Dp = 120.dp
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = false,
        cursorBrush = SolidColor(YellowAccent),
        textStyle = TextStyle(
            color = TextPrimary,
            fontSize = 13.sp,
            fontFamily = FontFamily.Monospace,
            lineHeight = 18.sp
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .background(DarkBackground)
            .border(1.dp, if (value.isNotEmpty()) YellowAccent.copy(alpha = 0.6f) else DarkBorder, RectangleShape)
            .padding(10.dp),
        decorationBox = { innerTextField ->
            Box(modifier = Modifier.fillMaxSize()) {
                if (value.isEmpty()) {
                    Text(text = placeholder, color = TextMuted, fontSize = 12.sp)
                }
                innerTextField()
            }
        }
    )
}

@Composable
private fun RuleTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    monospace: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        cursorBrush = SolidColor(YellowAccent),
        textStyle = TextStyle(
            color = TextPrimary,
            fontSize = 13.sp,
            fontFamily = if (monospace) FontFamily.Monospace else FontFamily.Default
        ),
        keyboardOptions = keyboardOptions,
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .background(DarkBackground)
            .border(1.dp, if (value.isNotEmpty()) YellowAccent.copy(alpha = 0.6f) else DarkBorder, RectangleShape)
            .padding(horizontal = 10.dp),
        decorationBox = { innerTextField ->
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) {
                    Text(text = placeholder, color = TextMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                innerTextField()
            }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChoiceRow(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        options.forEachIndexed { index, option ->
            ChoiceChip(
                text = option,
                selected = index == selectedIndex,
                onClick = { onSelect(index) }
            )
        }
    }
}

@Composable
private fun ChoiceChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    iconRes: Int = 0
) {
    Surface(
        shape = RectangleShape,
        color = if (selected) YellowAccentSubtle else DarkBackground,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (selected) YellowAccent else DarkBorder
        ),
        modifier = Modifier.clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (iconRes != 0) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    tint = Color.Unspecified,
                    modifier = Modifier.size(12.dp)
                )
            }
            Text(
                text = text,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = if (selected) YellowAccent else TextSecondary
            )
        }
    }
}
