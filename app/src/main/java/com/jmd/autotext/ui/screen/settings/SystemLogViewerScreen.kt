package com.jmd.autotext.ui.screen.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLocale
import com.jmd.autotext.compat.ClipboardCompat
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jmd.autotext.core.AppFileLogger
import com.jmd.autotext.core.showAppToast
import com.jmd.autotext.ui.components.appCard
import com.jmd.autotext.ui.components.appSubScreenHeader
import com.jmd.autotext.ui.components.glassChip
import com.jmd.autotext.ui.theme.AppDesignTokens

/**
 * Structured log models for rich execution-history style display.
 */
sealed class ParsedSystemLogItem {
    abstract val rawText: String
    abstract val timestamp: String

    data class ActionLog(
        override val timestamp: String,
        val level: String,
        val tag: String,
        val message: String,
        val details: String = "",
        override val rawText: String
    ) : ParsedSystemLogItem()

    data class LedgerLog(
        override val timestamp: String,
        val campaignId: String,
        val channel: String,
        val recipientName: String,
        val recipientPhone: String,
        val status: String,
        val snippet: String,
        val attempt: String,
        val details: String,
        override val rawText: String
    ) : ParsedSystemLogItem()

    data class ErrorLog(
        override val timestamp: String,
        val tag: String,
        val message: String,
        val stackTrace: String = "",
        override val rawText: String
    ) : ParsedSystemLogItem()

    data class RawLine(
        override val timestamp: String = "",
        override val rawText: String
    ) : ParsedSystemLogItem()
}

/**
 * Modernized Diagnostic Log Reader rendering Actions, Campaign Ledger, and Errors
 * formatted cleanly with Material 3 Expressive cards, chips, and level badges like Execution History.
 */
@Composable
fun systemLogViewerView(
    context: Context,
    onBack: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(1) } // 1: Actions & Exec, 0: Campaign Ledger, 2: Errors & Traces
    var searchQuery by remember { mutableStateOf("") }
    var levelFilter by remember { mutableStateOf("ALL") }
    var reloadTrigger by remember { mutableIntStateOf(0) }
    val currentLocale = LocalLocale.current.platformLocale

    val ledgerText = remember(reloadTrigger) { AppFileLogger.readLedgerFile(context, 1500) }
    val execText = remember(reloadTrigger) { AppFileLogger.readLogFile(context, 1500) }
    val errorText = remember(reloadTrigger) { AppFileLogger.readErrorLog(context, 500) }

    val (activeTitle, activeFile, activeContent) = when (selectedTab) {
        0 -> Triple("Campaign Audit Ledger", AppFileLogger.getLedgerFile(context), ledgerText)
        1 -> Triple("Device Actions & System Log", AppFileLogger.getLogFile(context), execText)
        else -> Triple("Errors & Crashes Log", AppFileLogger.getErrorFile(context), errorText)
    }

    val fileSizeKb = ((activeFile?.length() ?: 0L) / 1024.0)

    // Parse the active log content into structured entries
    val parsedItems = remember(activeContent, selectedTab) {
        if (activeContent.isBlank()) return@remember emptyList<ParsedSystemLogItem>()
        val lines = activeContent.lines().filter { it.isNotBlank() }

        when (selectedTab) {
            1 -> {
                // Actions & Exec Log: Format is "[$timestamp] [$level] [$tag] $message"
                val execRegex = Regex("""^\[(.*?)\]\s+\[(.*?)\]\s+\[(.*?)\]\s+(.*)$""")
                lines.map { line ->
                    val match = execRegex.find(line.trim())
                    if (match != null) {
                        val (ts, lvl, tag, msg) = match.destructured
                        ParsedSystemLogItem.ActionLog(
                            timestamp = ts.trim(),
                            level = lvl.trim().uppercase(),
                            tag = tag.trim(),
                            message = msg.trim(),
                            rawText = line
                        )
                    } else {
                        ParsedSystemLogItem.RawLine(rawText = line)
                    }
                }.reversed() // Show newest on top
            }
            0 -> {
                // Campaign Ledger: CSV format
                // "Timestamp,CampaignId,Channel,RecipientName,RecipientPhone,Status,MessageSnippet,Attempt,Details"
                lines.mapNotNull { line ->
                    if (line.startsWith("Timestamp,", ignoreCase = true)) return@mapNotNull null
                    try {
                        val parts = parseCsvLine(line)
                        if (parts.size >= 6) {
                            ParsedSystemLogItem.LedgerLog(
                                timestamp = parts.getOrElse(0) { "" },
                                campaignId = parts.getOrElse(1) { "" },
                                channel = parts.getOrElse(2) { "" },
                                recipientName = parts.getOrElse(3) { "" },
                                recipientPhone = parts.getOrElse(4) { "" },
                                status = parts.getOrElse(5) { "UNKNOWN" },
                                snippet = parts.getOrElse(6) { "" },
                                attempt = parts.getOrElse(7) { "1" },
                                details = parts.getOrElse(8) { "" },
                                rawText = line
                            )
                        } else {
                            ParsedSystemLogItem.RawLine(rawText = line)
                        }
                    } catch (_: Exception) {
                        ParsedSystemLogItem.RawLine(rawText = line)
                    }
                }.reversed() // Show newest on top
            }
            else -> {
                // Errors & Crashes: Format is "[$timestamp] [$tag] $message" or stack traces
                val errRegex = Regex("""^\[(.*?)\]\s+\[(.*?)\]\s+(.*)$""")
                val result = mutableListOf<ParsedSystemLogItem>()
                var currentError: ParsedSystemLogItem.ErrorLog? = null

                lines.forEach { line ->
                    val match = errRegex.find(line.trim())
                    if (match != null) {
                        currentError?.let { result.add(it) }
                        val (ts, tag, msg) = match.destructured
                        currentError = ParsedSystemLogItem.ErrorLog(
                            timestamp = ts.trim(),
                            tag = tag.trim(),
                            message = msg.trim(),
                            stackTrace = "",
                            rawText = line
                        )
                    } else {
                        val err = currentError
                        if (err != null) {
                            val st = if (err.stackTrace.isEmpty()) line else "${err.stackTrace}\n$line"
                            currentError = err.copy(stackTrace = st)
                        } else {
                            result.add(ParsedSystemLogItem.RawLine(rawText = line))
                        }
                    }
                }
                currentError?.let { result.add(it) }
                result.reversed() // Show newest on top
            }
        }
    }

    // Filter items based on searchQuery and levelFilter
    val filteredItems = remember(parsedItems, searchQuery, levelFilter, selectedTab) {
        var items = parsedItems
        if (selectedTab == 1 && levelFilter != "ALL") {
            items = items.filter { item ->
                when (item) {
                    is ParsedSystemLogItem.ActionLog -> item.level.equals(levelFilter, ignoreCase = true)
                    else -> true
                }
            }
        } else if (selectedTab == 0 && levelFilter != "ALL") {
            items = items.filter { item ->
                when (item) {
                    is ParsedSystemLogItem.LedgerLog -> {
                        when (levelFilter) {
                            "SUCCESS" -> item.status.equals("SUCCESS", ignoreCase = true)
                            "QUEUED" -> item.status.equals("QUEUED", ignoreCase = true)
                            "FAILED" -> item.status.contains("FAIL", ignoreCase = true) || item.status.contains("INVALID", ignoreCase = true)
                            else -> true
                        }
                    }
                    else -> true
                }
            }
        }

        if (searchQuery.isNotBlank()) {
            val q = searchQuery.trim().lowercase()
            items = items.filter { item ->
                when (item) {
                    is ParsedSystemLogItem.ActionLog ->
                        item.message.lowercase().contains(q) || item.tag.lowercase().contains(q) || item.timestamp.contains(q)
                    is ParsedSystemLogItem.LedgerLog ->
                        item.recipientPhone.contains(q) || item.recipientName.lowercase().contains(q) ||
                                item.channel.lowercase().contains(q) || item.status.lowercase().contains(q) ||
                                item.details.lowercase().contains(q) || item.snippet.lowercase().contains(q)
                    is ParsedSystemLogItem.ErrorLog ->
                        item.message.lowercase().contains(q) || item.tag.lowercase().contains(q) || item.stackTrace.lowercase().contains(q)
                    is ParsedSystemLogItem.RawLine ->
                        item.rawText.lowercase().contains(q)
                }
            }
        }
        items
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            appSubScreenHeader(
                title = activeTitle,
                subtitle = "${filteredItems.size} records • ${String.format(currentLocale, "%.1f KB", fileSizeKb)}",
                onBack = onBack,
                actions = {
                    IconButton(onClick = {
                        reloadTrigger++
                        context.showAppToast("Logs refreshed")
                    }) {
                        Text("🔄", fontSize = 16.sp)
                    }
                    IconButton(onClick = {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, "AutoText Diagnostic Report - $activeTitle")
                            putExtra(Intent.EXTRA_TEXT, activeContent)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Share $activeTitle"))
                    }) {
                        Text("📤", fontSize = 16.sp)
                    }
                    IconButton(onClick = {
                        val allIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, "AutoText Complete Ledger & Diagnostic Logs Bundle")
                            putExtra(Intent.EXTRA_TEXT, AppFileLogger.exportAllLogsSummary(context))
                        }
                        context.startActivity(Intent.createChooser(allIntent, "Export Complete Audit Bundle"))
                    }) {
                        Text("📦", fontSize = 16.sp)
                    }
                    IconButton(onClick = {
                        AppFileLogger.clearAllLogs(context)
                        reloadTrigger++
                        context.showAppToast("Logs cleared")
                    }) {
                        Text("🗑️", fontSize = 16.sp)
                    }
                }
            )
        },
        containerColor = AppDesignTokens.VoidBackground
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Segmented Log Tabs
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val tabs = listOf(
                    Triple(1, "⚡ Actions & Exec", MaterialTheme.colorScheme.primary),
                    Triple(0, "📊 Campaign Ledger", MaterialTheme.colorScheme.tertiary),
                    Triple(2, "🚨 Errors & Traces", MaterialTheme.colorScheme.error)
                )
                tabs.forEach { (index, title, accent) ->
                    val isSelected = selectedTab == index
                    Surface(
                        onClick = {
                            selectedTab = index
                            levelFilter = "ALL"
                        },
                        shape = AppDesignTokens.ShapePill,
                        color = if (isSelected) accent.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceContainerHigh,
                        border = BorderStroke(
                            1.dp,
                            if (isSelected) accent else MaterialTheme.colorScheme.outlineVariant
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = title,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 7.dp, horizontal = 2.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            // Quick Filter Pills (for Actions or Ledger)
            if (selectedTab == 1) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 2.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        Pair("ALL", "All"),
                        Pair("ACTION", "⚡ Action"),
                        Pair("INFO", "ℹ️ Info"),
                        Pair("WARN", "⚠️ Warn"),
                        Pair("ERROR", "🚨 Error")
                    ).forEach { (key, label) ->
                        glassChip(
                            selected = levelFilter == key,
                            onClick = { levelFilter = key },
                            label = label
                        )
                    }
                }
            } else if (selectedTab == 0) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 2.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        Pair("ALL", "All"),
                        Pair("SUCCESS", "✅ Success"),
                        Pair("QUEUED", "⏳ Queued"),
                        Pair("FAILED", "❌ Failed")
                    ).forEach { (key, label) ->
                        glassChip(
                            selected = levelFilter == key,
                            onClick = { levelFilter = key },
                            label = label
                        )
                    }
                }
            }

            // Search Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text(
                            text = if (selectedTab == 0) "Filter phone, campaign, status..." else "Filter tag, text, error...",
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    leadingIcon = { Text("🔍", fontSize = 13.sp, modifier = Modifier.padding(start = 6.dp)) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Text("❌", fontSize = 11.sp)
                            }
                        }
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                    ),
                    shape = AppDesignTokens.ShapePill,
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                )

                Surface(
                    shape = AppDesignTokens.ShapePill,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Text(
                        text = "${filteredItems.size}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }

            // Main Content: LazyColumn with Card Layout
            if (filteredItems.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (selectedTab == 2) "🛡️" else "📜",
                            fontSize = 42.sp
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = if (searchQuery.isNotBlank() || levelFilter != "ALL") {
                                "No logs match current filter criteria"
                            } else if (selectedTab == 2) {
                                "No errors or crashes logged. System is operating normally!"
                            } else if (selectedTab == 0) {
                                "No campaign ledger entries recorded yet."
                            } else {
                                "No execution records logged yet."
                            },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.5.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp),
                    contentPadding = PaddingValues(
                        top = 4.dp,
                        bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 24.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    itemsIndexed(filteredItems, key = { index, item -> "$selectedTab-$index-${item.timestamp}" }) { _, item ->
                        when (item) {
                            is ParsedSystemLogItem.ActionLog -> actionLogCard(
                                log = item,
                                onCopy = {
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                    ClipboardCompat.setPrimaryClip(cm, ClipData.newPlainText("System Log", item.rawText))
                                    context.showAppToast("Log copied to clipboard")
                                }
                            )
                            is ParsedSystemLogItem.LedgerLog -> ledgerLogCard(
                                log = item,
                                onCopy = {
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                    ClipboardCompat.setPrimaryClip(cm, ClipData.newPlainText("Ledger Log", item.rawText))
                                    context.showAppToast("Ledger entry copied")
                                }
                            )
                            is ParsedSystemLogItem.ErrorLog -> errorLogCard(
                                log = item,
                                onCopy = {
                                    val full = "${item.rawText}\n${item.stackTrace}"
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                    ClipboardCompat.setPrimaryClip(cm, ClipData.newPlainText("Error Log", full))
                                    context.showAppToast("Error details copied")
                                }
                            )
                            is ParsedSystemLogItem.RawLine -> rawLogCard(
                                line = item.rawText,
                                onCopy = {
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                    ClipboardCompat.setPrimaryClip(cm, ClipData.newPlainText("Log Line", item.rawText))
                                    context.showAppToast("Line copied")
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Card rendering an Action / System log entry with badge, tag, timestamp, and message.
 */
@Composable
private fun actionLogCard(
    log: ParsedSystemLogItem.ActionLog,
    onCopy: () -> Unit
) {
    val (badgeColor, badgeEmoji) = when (log.level) {
        "ACTION" -> Pair(MaterialTheme.colorScheme.primary, "⚡")
        "INFO" -> Pair(MaterialTheme.colorScheme.secondary, "ℹ️")
        "WARN" -> Pair(Color(0xFFEAB308), "⚠️")
        "ERROR" -> Pair(MaterialTheme.colorScheme.error, "🚨")
        "SUCCESS" -> Pair(MaterialTheme.colorScheme.tertiary, "✅")
        else -> Pair(MaterialTheme.colorScheme.onSurfaceVariant, "📄")
    }

    appCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onCopy
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Level badge
            Surface(
                shape = AppDesignTokens.ShapePill,
                color = badgeColor.copy(alpha = 0.16f),
                border = BorderStroke(0.75.dp, badgeColor.copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(badgeEmoji, fontSize = 10.sp)
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = log.level,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = badgeColor
                    )
                }
            }

            Spacer(modifier = Modifier.width(6.dp))

            // Tag chip
            Surface(
                shape = AppDesignTokens.ShapePill,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Text(
                    text = log.tag,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // Timestamp chip
            Text(
                text = log.timestamp,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Message text
        Text(
            text = log.message,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface,
            lineHeight = 16.5.sp,
            fontFamily = FontFamily.SansSerif
        )
    }
}

/**
 * Card rendering a Campaign Ledger entry with recipient, channel, status pill, and details.
 */
@Composable
private fun ledgerLogCard(
    log: ParsedSystemLogItem.LedgerLog,
    onCopy: () -> Unit
) {
    val isSuccess = log.status.equals("SUCCESS", ignoreCase = true) || log.status.equals("DISPATCHED", ignoreCase = true)
    val isQueued = log.status.equals("QUEUED", ignoreCase = true)
    val (statusColor, statusEmoji) = when {
        isSuccess -> Pair(MaterialTheme.colorScheme.tertiary, "✅")
        isQueued -> Pair(Color(0xFFEAB308), "⏳")
        else -> Pair(MaterialTheme.colorScheme.error, "❌")
    }

    val (channelEmoji, channelColor) = when {
        log.channel.contains("SMS", ignoreCase = true) -> Pair("📩", MaterialTheme.colorScheme.secondary)
        log.channel.contains("BUSINESS", ignoreCase = true) -> Pair("💼", MaterialTheme.colorScheme.primary)
        else -> Pair("💬", MaterialTheme.colorScheme.tertiary)
    }

    appCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onCopy
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(statusEmoji, fontSize = 16.sp)
            Spacer(modifier = Modifier.width(8.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (log.recipientName.isNotBlank() && log.recipientName != log.recipientPhone) {
                            "${log.recipientName} (${log.recipientPhone})"
                        } else {
                            log.recipientPhone
                        },
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.5.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    // Channel Pill
                    Surface(
                        shape = AppDesignTokens.ShapePill,
                        color = channelColor.copy(alpha = 0.16f),
                        border = BorderStroke(0.75.dp, channelColor.copy(alpha = 0.35f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(channelEmoji, fontSize = 9.sp)
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = log.channel,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = channelColor
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Status: ${log.status} • Attempt ${log.attempt}",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = statusColor
                    )

                    Text(
                        text = log.timestamp,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (log.snippet.isNotBlank()) {
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = "“${log.snippet}”",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (log.details.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = log.details,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/**
 * Card rendering an Error entry with collapsible stack trace and copy capability.
 */
@Composable
private fun errorLogCard(
    log: ParsedSystemLogItem.ErrorLog,
    onCopy: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    appCard(
        modifier = Modifier.fillMaxWidth(),
        borderColor = MaterialTheme.colorScheme.error.copy(alpha = 0.4f),
        onClick = { expanded = !expanded }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = AppDesignTokens.ShapePill,
                color = MaterialTheme.colorScheme.error.copy(alpha = 0.16f),
                border = BorderStroke(0.75.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("🚨", fontSize = 10.sp)
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = "ERROR",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            Spacer(modifier = Modifier.width(6.dp))

            Surface(
                shape = AppDesignTokens.ShapePill,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Text(
                    text = log.tag,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = log.timestamp,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = log.message,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.error,
            lineHeight = 16.sp
        )

        if (log.stackTrace.isNotBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (expanded) "▼ Collapse stack trace" else "▶ Expand stack trace",
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Tap to toggle",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Surface(
                    shape = AppDesignTokens.ShapeSmall,
                    color = Color(0xFF0F172A),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp)
                ) {
                    Text(
                        text = log.stackTrace,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFFF87171),
                        modifier = Modifier.padding(8.dp),
                        lineHeight = 14.sp
                    )
                }
            }
        }
    }
}

/**
 * Fallback card for raw unstructured log text.
 */
@Composable
private fun rawLogCard(
    line: String,
    onCopy: () -> Unit
) {
    appCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onCopy
    ) {
        Text(
            text = line,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface,
            lineHeight = 15.sp
        )
    }
}

/**
 * Standard CSV line parser supporting double-quoted fields.
 */
private fun parseCsvLine(line: String): List<String> {
    val result = mutableListOf<String>()
    var cur = StringBuilder()
    var inQuotes = false
    var i = 0
    while (i < line.length) {
        val ch = line[i]
        if (ch == '\"') {
            if (inQuotes && i + 1 < line.length && line[i + 1] == '\"') {
                cur.append('\"')
                i++
            } else {
                inQuotes = !inQuotes
            }
        } else if (ch == ',' && !inQuotes) {
            result.add(cur.toString().trim())
            cur = StringBuilder()
        } else {
            cur.append(ch)
        }
        i++
    }
    result.add(cur.toString().trim())
    return result
}
