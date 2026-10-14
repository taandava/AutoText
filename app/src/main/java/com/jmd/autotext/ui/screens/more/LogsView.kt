package com.jmd.autotext.ui.screens.more

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jmd.autotext.data.AppRepository
import com.jmd.autotext.model.*
import com.jmd.autotext.sender.SmsSender
import com.jmd.autotext.sender.WhatsAppAutomationSender
import com.jmd.autotext.ui.components.*
import com.jmd.autotext.ui.theme.*
import com.jmd.autotext.util.AppFileLogger
import java.text.SimpleDateFormat
import java.util.*

// ----------------- SUB SCREEN: LOGS (EXECUTION HISTORY) -----------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogsView(
    repository: AppRepository,
    onNavigateToSystemLogs: () -> Unit = {},
    onBack: () -> Unit
) {
    var logs by remember { mutableStateOf(repository.getAllLogs()) }
    var selectedFilter by remember { mutableStateOf("ALL") }
    var selectedLogDetail by remember { mutableStateOf<ExecutionLog?>(null) }

    if (selectedLogDetail != null) {
        androidx.activity.compose.BackHandler {
            selectedLogDetail = null
            logs = repository.getAllLogs()
        }
        LogDetailView(
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
            "FAILED" -> logs.filter { it.status.equals("FAILED", ignoreCase = true) || it.status.equals("QUEUED", ignoreCase = true) }
            else -> logs
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Execution History", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = BentoGlassTokens.TextPrimary)
                        Text("${filteredLogs.size} records", fontSize = 11.sp, color = BentoGlassTokens.TextMuted)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = BentoGlassTokens.TextPrimary) }
                },
                actions = {
                    IconButton(onClick = onNavigateToSystemLogs) {
                        Icon(Icons.Default.Terminal, contentDescription = "System File Log", tint = BentoGlassTokens.RadiantAmber)
                    }
                    IconButton(onClick = {
                        repository.clearLogs()
                        logs = emptyList()
                    }) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = "Clear Logs", tint = BentoGlassTokens.CyberRose)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BentoGlassTokens.VoidBackground)
            )
        },
        containerColor = BentoGlassTokens.VoidBackground
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
                    "ALL" to "All (${logs.size})",
                    "SMS" to "SMS (${logs.count { it.channel.equals("SMS", ignoreCase = true) }})",
                    "WHATSAPP" to "WhatsApp (${logs.count { it.channel.contains("WHATSAPP", ignoreCase = true) }})",
                    "FAILED" to "Failed / Queued (${logs.count { it.status.equals("FAILED", ignoreCase = true) || it.status.equals("QUEUED", ignoreCase = true) }})"
                ).forEach { (key, label) ->
                    GlassChip(
                        selected = selectedFilter == key,
                        onClick = { selectedFilter = key },
                        label = label
                    )
                }
            }

            if (filteredLogs.isEmpty()) {
                BentoEmptyState(
                    title = "No Execution Records",
                    description = "Sent, auto-replied, and scheduled messages will appear here.",
                    icon = Icons.Default.History,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                val currentLocale = androidx.compose.ui.platform.LocalLocale.current.platformLocale
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredLogs, key = { it.id }) { log ->
                        val timeStr = SimpleDateFormat("dd MMM, hh:mm:ss a", currentLocale).format(Date(log.timestampMillis))
                        val isSuccess = log.status.equals("SUCCESS", ignoreCase = true)
                        val isQueued = log.status.equals("QUEUED", ignoreCase = true)
                        val statusColor = when {
                            isSuccess -> BentoGlassTokens.VividEmerald
                            isQueued -> BentoGlassTokens.RadiantAmber
                            else -> BentoGlassTokens.CyberRose
                        }
                        val statusIcon = when {
                            isSuccess -> Icons.Default.CheckCircle
                            isQueued -> Icons.Default.HourglassTop
                            else -> Icons.Default.Cancel
                        }

                        BentoCard(
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
                                    contentDescription = null,
                                    tint = statusColor,
                                    modifier = Modifier.size(22.dp)
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
                                            color = BentoGlassTokens.TextPrimary,
                                            fontSize = 13.5.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        val displayChannel = if (log.channel.contains("SMS", ignoreCase = true)) "SMS" else "WhatsApp"
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Surface(
                                                shape = BentoGlassTokens.ShapePill,
                                                color = if (displayChannel == "SMS") BentoGlassTokens.NeonCyan.copy(alpha = 0.15f) else BentoGlassTokens.VividEmerald.copy(alpha = 0.15f),
                                                border = androidx.compose.foundation.BorderStroke(1.dp, if (displayChannel == "SMS") BentoGlassTokens.NeonCyan.copy(alpha = 0.35f) else BentoGlassTokens.VividEmerald.copy(alpha = 0.35f))
                                            ) {
                                                Text(
                                                    text = displayChannel,
                                                    color = if (displayChannel == "SMS") BentoGlassTokens.NeonCyan else BentoGlassTokens.VividEmerald,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                            Surface(
                                                shape = BentoGlassTokens.ShapePill,
                                                color = statusColor.copy(alpha = 0.15f),
                                                border = androidx.compose.foundation.BorderStroke(1.dp, statusColor.copy(alpha = 0.35f))
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
                                        color = BentoGlassTokens.TextMuted,
                                        fontSize = 12.sp,
                                        maxLines = 2,
                                        lineHeight = 16.sp
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(timeStr, color = Color(0xFF64748B), fontSize = 10.sp)
                                }

                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = BentoGlassTokens.TextMuted, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                    item { Spacer(modifier = Modifier.height(16.dp)) }
                }
            }
        }
    }
}


