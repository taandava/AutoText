/**
 * Package: com.jmd.autotext.ui.screen.logs
 * Module: UI Automation Logs & Audit Screen Layer
 *
 * Primary coordinator screen managing:
 * - Execution history audit ledger.
 * - Filtering by channel (All, SMS, WhatsApp, Calls, Campaigns, Failed).
 * - CSV export via Storage Access Framework (SAF).
 * - Purge control.
 * - Detail inspection via [logDetailView].
 */
package com.jmd.autotext.ui.screen.logs

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

import com.jmd.autotext.core.showAppToast
import com.jmd.autotext.data.model.ExecutionLog
import com.jmd.autotext.data.repo.AppRepository
import com.jmd.autotext.ui.components.*
import com.jmd.autotext.ui.theme.AppDesignTokens

/**
 * Comprehensive execution history and audit ledger screen.
 *
 * @param repository Data layer repository providing historical log records.
 * @param onNavigateToSystemLogs Callback routing to raw system file logs viewer.
 * @param onBack Callback invoked to return to the parent menu.
 */
@Composable
fun logsView(
    repository: AppRepository,
    onNavigateToSystemLogs: () -> Unit = {},
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var logs by remember { mutableStateOf(repository.getAllLogs()) }
    var selectedFilter by remember { mutableStateOf("ALL") }
    var selectedLogDetail by remember { mutableStateOf<ExecutionLog?>(null) }

    val csvExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    val csvData = repository.exportLogsToCsv()
                    context.contentResolver.openOutputStream(uri)?.use { os ->
                        os.write(csvData.toByteArray(Charsets.UTF_8))
                        os.flush()
                    }
                    withContext(Dispatchers.Main) {
                        context.showAppToast("Execution logs exported successfully!", isLong = true)
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    withContext(Dispatchers.Main) {
                        context.showAppToast("Export failed: ${e.message}")
                    }
                }
            }
        }
    }

    if (selectedLogDetail != null) {
        BackHandler {
            selectedLogDetail = null
            logs = repository.getAllLogs()
        }
        logDetailView(
            log = selectedLogDetail!!,
            repository = repository,
            onBack = {
                selectedLogDetail = null
                logs = repository.getAllLogs()
            }
        )
        return
    }

    val filteredLogs = remember(logs, selectedFilter) {
        when (selectedFilter) {
            "SMS" -> logs.filter { it.channel.equals("SMS", ignoreCase = true) }
            "WHATSAPP" -> logs.filter { it.channel.contains("WHATSAPP", ignoreCase = true) }
            "CALLS" -> logs.filter { it.channel.contains("CALL", ignoreCase = true) }
            "CAMPAIGNS" -> logs.filter {
                it.details.contains("campaign", ignoreCase = true) ||
                it.details.contains("pool", ignoreCase = true) ||
                it.messageSnippet.contains("Daily", ignoreCase = true)
            }
            "FAILED" -> logs.filter { it.status.equals("FAILED", ignoreCase = true) || it.status.equals("QUEUED", ignoreCase = true) }
            else -> logs
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            appSubScreenHeader(
                title = "Execution History",
                subtitle = "${filteredLogs.size} records",
                onBack = onBack,
                actions = {
                    IconButton(onClick = {
                        val dateStr = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                        csvExportLauncher.launch("autotext_execution_logs_$dateStr.csv")
                    }) {
                        Text("📥", fontSize = 18.sp)
                    }
                    IconButton(onClick = onNavigateToSystemLogs) {
                        Text("📜", fontSize = 18.sp)
                    }
                    IconButton(onClick = {
                        repository.clearLogs()
                        logs = emptyList()
                    }) {
                        Text("🗑️", fontSize = 18.sp)
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
            // Responsive Filter Chips Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 6.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(
                    Triple("ALL", "All (${logs.size})", "📊"),
                    Triple("SMS", "SMS (${logs.count { it.channel.equals("SMS", ignoreCase = true) }})", "📩"),
                    Triple("WHATSAPP", "WhatsApp (${logs.count { it.channel.contains("WHATSAPP", ignoreCase = true) }})", "💬"),
                    Triple("CALLS", "Calls (${logs.count { it.channel.contains("CALL", ignoreCase = true) }})", "📞"),
                    Triple("CAMPAIGNS", "Campaigns (${logs.count { it.details.contains("campaign", ignoreCase = true) || it.details.contains("pool", ignoreCase = true) || it.messageSnippet.contains("Daily", ignoreCase = true) }})", "🚀"),
                    Triple("FAILED", "Failed (${logs.count { it.status.equals("FAILED", ignoreCase = true) || it.status.equals("QUEUED", ignoreCase = true) }})", "❌")
                ).forEach { (key, label, chipEmoji) ->
                    glassChip(
                        selected = selectedFilter == key,
                        onClick = { selectedFilter = key },
                        label = label,
                        emoji = chipEmoji
                    )
                }
            }

            if (filteredLogs.isEmpty()) {
                appEmptyState(
                    title = "No Execution Records",
                    description = "Sent, auto-replied, and scheduled messages will appear here.",
                    emoji = "📜",
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                val currentLocale = LocalLocale.current.platformLocale
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 14.dp),
                    contentPadding = PaddingValues(
                        top = 4.dp,
                        bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 24.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredLogs, key = { it.id }) { log ->
                        val timeStr = SimpleDateFormat("dd MMM, hh:mm:ss a", currentLocale).format(Date(log.timestampMillis))
                        val isSuccess = log.status.equals("SUCCESS", ignoreCase = true)
                        val isQueued = log.status.equals("QUEUED", ignoreCase = true)
                        val statusColor = when {
                            isSuccess -> MaterialTheme.colorScheme.tertiary
                            isQueued -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.error
                        }
                        val statusEmoji = when {
                            isSuccess -> "✅"
                            isQueued -> "⏳"
                            else -> "❌"
                        }

                        appCard(
                            onClick = { selectedLogDetail = log },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Status Emoji
                                Text(statusEmoji, fontSize = 20.sp)

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            log.recipient,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            fontSize = 13.5.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        val (displayChannel, channelEmoji, channelColor) = when {
                                            log.channel.contains("Call", ignoreCase = true) && log.channel.contains("Phone", ignoreCase = true) ->
                                                Triple("Phone Call", "📞", MaterialTheme.colorScheme.primary)
                                            log.channel.contains("Call", ignoreCase = true) ->
                                                Triple("WA Call", "📞", MaterialTheme.colorScheme.tertiary)
                                            log.channel.contains("Status", ignoreCase = true) ->
                                                Triple("WA Status", "🌟", MaterialTheme.colorScheme.primary)
                                            log.channel.contains("SMS", ignoreCase = true) ->
                                                Triple("SMS", "📩", MaterialTheme.colorScheme.primary)
                                            log.channel.contains("Business", ignoreCase = true) || log.channel.contains("w4b", ignoreCase = true) ->
                                                Triple("W4B", "💼", MaterialTheme.colorScheme.tertiary)
                                            else ->
                                                Triple("WhatsApp", "💬", MaterialTheme.colorScheme.tertiary)
                                        }
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Surface(
                                                shape = AppDesignTokens.ShapePill,
                                                color = channelColor.copy(alpha = 0.15f),
                                                border = BorderStroke(1.dp, channelColor.copy(alpha = 0.35f))
                                            ) {
                                                Text(
                                                    text = "$channelEmoji $displayChannel",
                                                    color = channelColor,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                            Surface(
                                                shape = AppDesignTokens.ShapePill,
                                                color = statusColor.copy(alpha = 0.15f),
                                                border = BorderStroke(1.dp, statusColor.copy(alpha = 0.35f))
                                            ) {
                                                Text(
                                                    text = log.status,
                                                    color = statusColor,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = log.messageSnippet,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 12.sp,
                                        maxLines = 2,
                                        lineHeight = 16.sp
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(timeStr, color = Color(0xFF64748B), fontSize = 10.sp)
                                }

                                Spacer(modifier = Modifier.width(6.dp))
                                Text("➡️", fontSize = 14.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
