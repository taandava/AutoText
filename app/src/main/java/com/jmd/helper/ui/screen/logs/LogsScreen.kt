/**
 * Package: com.jmd.helper.ui.screen.logs
 * Module: Application Core Subsystem
 *
 * What it is:
 * Architecture definition and implementation for [LogsScreen].
 *
 * What it does:
 * Primary coordinator screen managing:
 *
 * Linked With:
 * - Repositories: [com.jmd.helper.data.repo.AppRepository]
 * - Models & Entities: [com.jmd.helper.data.model.ExecutionLog]
 * - UI Screens & Components: [androidx.activity.compose.BackHandler], [androidx.activity.compose.rememberLauncherForActivityResult], [androidx.activity.result.contract.ActivityResultContracts], [androidx.compose.ui.Alignment]
 * - Core Security & Utilities: [java.util.*], [com.jmd.helper.core.showAppToast]
 * - Android Framework & Jetpack: [android.net.Uri], [androidx.compose.foundation.BorderStroke], [androidx.compose.foundation.horizontalScroll], [androidx.compose.foundation.lazy.LazyColumn]
 */

package com.jmd.helper.ui.screen.logs

import androidx.compose.material.icons.automirrored.filled.*

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Delete
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

import com.jmd.helper.core.showAppToast
import com.jmd.helper.data.model.ExecutionLog
import com.jmd.helper.data.repo.AppRepository
import com.jmd.helper.ui.components.*
import com.jmd.helper.ui.theme.AppDesignTokens

/**
 * What it is:
 * Comprehensive execution history and audit ledger screen.
 *
 * What it does:
 * Renders interactive Material 3 UI for [LogsView] adhering to application design invariants.
 *
 * Linked with:
 * - Architecture: [LogsScreen], [LogsView]
 * @param repository Data layer repository providing historical log records.
 * @param onNavigateToSystemLogs Callback routing to raw system file logs viewer.
 * @param onBack Callback invoked to return to the parent menu.
 */
@Composable
fun LogsView(
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

    val currentDetail = selectedLogDetail
    if (currentDetail != null) {
        BackHandler {
            selectedLogDetail = null
            logs = repository.getAllLogs()
        }
        LogDetailView(
            log = currentDetail,
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
            AppSubScreenHeader(
                title = "Execution History",
                subtitle = "${filteredLogs.size} records",
                onBack = onBack,
                actions = {
                    IconButton(onClick = {
                        val dateStr = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                        csvExportLauncher.launch("helper_execution_logs_$dateStr.csv")
                    }) {
                        Icon(
                            imageVector = Icons.Filled.Download,
                            contentDescription = "Export CSV",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(onClick = onNavigateToSystemLogs) {
                        Icon(
                            imageVector = Icons.Filled.Description,
                            contentDescription = "System Logs",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(onClick = {
                        repository.clearLogs()
                        logs = emptyList()
                    }) {
                        Icon(
                            imageVector = Icons.Outlined.Delete,
                            contentDescription = "Clear Logs",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
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
                data class LogFilterChip(val key: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)
                listOf(
                    LogFilterChip("ALL", "All (${logs.size})", Icons.Filled.BarChart),
                    LogFilterChip("SMS", "SMS (${logs.count { it.channel.equals("SMS", ignoreCase = true) }})", Icons.AutoMirrored.Filled.Message),
                    LogFilterChip("WHATSAPP", "WhatsApp (${logs.count { it.channel.contains("WHATSAPP", ignoreCase = true) }})", Icons.AutoMirrored.Filled.Chat),
                    LogFilterChip("CALLS", "Calls (${logs.count { it.channel.contains("CALL", ignoreCase = true) }})", Icons.Filled.Phone),
                    LogFilterChip("CAMPAIGNS", "Campaigns (${logs.count { it.details.contains("campaign", ignoreCase = true) || it.details.contains("pool", ignoreCase = true) || it.messageSnippet.contains("Daily", ignoreCase = true) }})", Icons.Filled.RocketLaunch),
                    LogFilterChip("FAILED", "Failed (${logs.count { it.status.equals("FAILED", ignoreCase = true) || it.status.equals("QUEUED", ignoreCase = true) }})", Icons.Filled.Cancel)
                ).forEach { item ->
                    GlassChip(
                        selected = selectedFilter == item.key,
                        onClick = { selectedFilter = item.key },
                        label = item.label,
                        icon = item.icon
                    )
                }
            }

            if (filteredLogs.isEmpty()) {
                AppEmptyState(
                    title = "No Execution Records",
                    description = "Sent, auto-replied, and scheduled messages will appear here.",
                    icon = Icons.Filled.History,
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
                        val statusIcon = when {
                            isSuccess -> Icons.Filled.CheckCircle
                            isQueued -> Icons.Filled.HourglassTop
                            else -> Icons.Filled.Cancel
                        }

                        AppCard(
                            onClick = { selectedLogDetail = log },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Status Icon
                                Icon(
                                    imageVector = statusIcon,
                                    contentDescription = log.status,
                                    tint = statusColor,
                                    modifier = Modifier.size(20.dp)
                                )

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
                                        val (displayChannel, channelIcon, channelColor) = when {
                                            log.channel.contains("Call", ignoreCase = true) && log.channel.contains("Phone", ignoreCase = true) ->
                                                Triple("Phone Call", Icons.Filled.Phone, MaterialTheme.colorScheme.primary)
                                            log.channel.contains("Call", ignoreCase = true) ->
                                                Triple("WA Call", Icons.Filled.PhoneInTalk, MaterialTheme.colorScheme.tertiary)
                                            log.channel.contains("Status", ignoreCase = true) ->
                                                Triple("WA Status", Icons.Filled.AutoAwesome, MaterialTheme.colorScheme.primary)
                                            log.channel.contains("SMS", ignoreCase = true) ->
                                                Triple("SMS", Icons.AutoMirrored.Filled.Message, MaterialTheme.colorScheme.primary)
                                            log.channel.contains("Business", ignoreCase = true) || log.channel.contains("w4b", ignoreCase = true) ->
                                                Triple("W4B", Icons.Filled.BusinessCenter, MaterialTheme.colorScheme.tertiary)
                                            else ->
                                                Triple("WhatsApp", Icons.AutoMirrored.Filled.Chat, MaterialTheme.colorScheme.tertiary)
                                        }
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Surface(
                                                shape = AppDesignTokens.ShapePill,
                                                color = channelColor.copy(alpha = 0.15f),
                                                border = BorderStroke(1.dp, channelColor.copy(alpha = 0.35f))
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(
                                                        imageVector = channelIcon,
                                                        contentDescription = null,
                                                        tint = channelColor,
                                                        modifier = Modifier.size(11.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(3.dp))
                                                    Text(
                                                        text = displayChannel,
                                                        color = channelColor,
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
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
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
