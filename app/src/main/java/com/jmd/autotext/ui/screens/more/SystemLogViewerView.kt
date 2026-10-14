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

// ----------------- SUB SCREEN: CAMPAIGN LEDGER & SYSTEM FILE LOG VIEWER -----------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SystemLogViewerView(
    context: Context,
    onBack: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Ledger (CSV), 1 = Actions & System Log, 2 = Errors
    var searchQuery by remember { mutableStateOf("") }
    var reloadTrigger by remember { mutableIntStateOf(0) }
    val currentLocale = androidx.compose.ui.platform.LocalLocale.current.platformLocale

    val ledgerText = remember(reloadTrigger) { AppFileLogger.readLedgerFile(context) }
    val execText = remember(reloadTrigger) { AppFileLogger.readLogFile(context) }
    val errorText = remember(reloadTrigger) { AppFileLogger.readErrorLog(context) }

    val (activeTitle, activeFile, activeContent) = when (selectedTab) {
        0 -> Triple("Campaign Audit Ledger", AppFileLogger.getLedgerFile(context), ledgerText)
        1 -> Triple("Device Actions & System Log", AppFileLogger.getLogFile(context), execText)
        else -> Triple("Errors & Crashes Log", AppFileLogger.getErrorFile(context), errorText)
    }

    val fileSizeKb = ((activeFile?.length() ?: 0L) / 1024.0)

    val displayedLines = remember(activeContent, searchQuery) {
        val lines = if (activeContent.isBlank()) emptyList() else activeContent.lines()
        if (searchQuery.isBlank()) {
            lines
        } else {
            lines.filter { it.contains(searchQuery, ignoreCase = true) }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(activeTitle, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = BentoGlassTokens.TextPrimary)
                        Text(
                            "${displayedLines.size} entries • ${String.format(currentLocale, "%.1f KB", fileSizeKb)}",
                            fontSize = 11.sp,
                            color = BentoGlassTokens.TextMuted
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = BentoGlassTokens.TextPrimary)
                    }
                },
                actions = {
                    IconButton(onClick = {
                        reloadTrigger++
                        Toast.makeText(context, "Logs refreshed", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = BentoGlassTokens.TextPrimary)
                    }
                    IconButton(onClick = {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, "AutoText Diagnostic Report - $activeTitle")
                            putExtra(Intent.EXTRA_TEXT, activeContent)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Share $activeTitle"))
                    }) {
                        Icon(Icons.Default.Share, contentDescription = "Share Active Tab", tint = BentoGlassTokens.NeonCyan)
                    }
                    IconButton(onClick = {
                        val allIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, "AutoText Complete Ledger & Diagnostic Logs Bundle")
                            putExtra(Intent.EXTRA_TEXT, AppFileLogger.exportAllLogsSummary(context))
                        }
                        context.startActivity(Intent.createChooser(allIntent, "Export Complete Audit Bundle"))
                    }) {
                        Icon(Icons.Default.FolderZip, contentDescription = "Export All Logs Bundle", tint = BentoGlassTokens.RadiantAmber)
                    }
                    IconButton(onClick = {
                        AppFileLogger.clearAllLogs(context)
                        reloadTrigger++
                        Toast.makeText(context, "All log files and campaign ledger reset", Toast.LENGTH_SHORT).show()
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
                .padding(horizontal = 14.dp, vertical = 6.dp)
        ) {
            // Segmented Log Tabs
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val tabs = listOf(
                    Triple(0, "📊 Campaign Ledger", BentoGlassTokens.VividEmerald),
                    Triple(1, "⚡ Actions & Exec", BentoGlassTokens.NeonCyan),
                    Triple(2, "🚨 Errors & Traces", BentoGlassTokens.CyberRose)
                )
                tabs.forEach { (index, title, accent) ->
                    val isSelected = selectedTab == index
                    Surface(
                        onClick = { selectedTab = index },
                        shape = BentoGlassTokens.ShapePill,
                        color = if (isSelected) accent.copy(alpha = 0.2f) else BentoGlassTokens.GlassSurfaceElevated,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isSelected) accent else BentoGlassTokens.GlassBorderSubtle
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = title,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) accent else BentoGlassTokens.TextMuted,
                            modifier = Modifier.padding(vertical = 7.dp, horizontal = 4.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }

            // Search Bar & Info Strip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Filter phone, task, status...", fontSize = 11.5.sp, color = BentoGlassTokens.TextMuted) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = BentoGlassTokens.TextMuted, modifier = Modifier.size(16.dp)) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear", tint = BentoGlassTokens.TextMuted, modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = BentoGlassTokens.GlassSurfaceElevated,
                        unfocusedContainerColor = BentoGlassTokens.GlassSurfaceElevated,
                        focusedBorderColor = BentoGlassTokens.NeonCyan,
                        unfocusedBorderColor = BentoGlassTokens.GlassBorderSubtle,
                        focusedTextColor = BentoGlassTokens.TextPrimary,
                        unfocusedTextColor = BentoGlassTokens.TextPrimary
                    ),
                    shape = BentoGlassTokens.ShapePill,
                    modifier = Modifier.weight(1f).height(46.dp)
                )

                Surface(
                    shape = BentoGlassTokens.ShapePill,
                    color = BentoGlassTokens.GlassSurfaceElevated,
                    border = androidx.compose.foundation.BorderStroke(1.dp, BentoGlassTokens.GlassBorderSubtle)
                ) {
                    Text(
                        text = if (selectedTab == 0) "CSV Log" else if (selectedTab == 1) "3MB Rotate" else "Crash Log",
                        fontSize = 10.sp,
                        color = BentoGlassTokens.TextMuted,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }

            // Main Console Box
            Surface(
                shape = BentoGlassTokens.ShapeMedium,
                color = BentoGlassTokens.GlassSurfaceSubtle,
                border = androidx.compose.foundation.BorderStroke(1.dp, BentoGlassTokens.GlassBorderSubtle),
                modifier = Modifier.fillMaxSize()
            ) {
                if (displayedLines.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(20.dp)) {
                            Icon(
                                imageVector = if (selectedTab == 2) Icons.Default.CheckCircle else Icons.Default.Inbox,
                                contentDescription = null,
                                tint = if (selectedTab == 2) BentoGlassTokens.VividEmerald else BentoGlassTokens.TextMuted,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = if (searchQuery.isNotEmpty()) {
                                    "No matching records for \"$searchQuery\""
                                } else if (selectedTab == 2) {
                                    "No errors or crashes recorded. System is healthy!"
                                } else if (selectedTab == 0) {
                                    "No campaign ledger records yet.\nCampaign dispatches will appear here in real-time."
                                } else {
                                    "No system logs recorded yet."
                                },
                                color = BentoGlassTokens.TextMuted,
                                fontSize = 12.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(10.dp)
                            .verticalScroll(rememberScrollState())
                            .horizontalScroll(rememberScrollState())
                    ) {
                        Column {
                            displayedLines.forEach { line ->
                                val lineColor = when {
                                    line.contains("ERROR", ignoreCase = true) || line.contains("FAILED", ignoreCase = true) || line.contains("Crash", ignoreCase = true) -> BentoGlassTokens.CyberRose
                                    line.contains("[ACTION]", ignoreCase = true) || line.contains("ACTION", ignoreCase = true) -> BentoGlassTokens.NeonCyan
                                    line.contains("SUCCESS", ignoreCase = true) -> BentoGlassTokens.VividEmerald
                                    line.contains("WARN", ignoreCase = true) || line.contains("QUEUED", ignoreCase = true) || line.contains("TIMEOUT", ignoreCase = true) -> BentoGlassTokens.RadiantAmber
                                    else -> Color(0xFF94A3B8)
                                }
                                Text(
                                    text = line,
                                    color = lineColor,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
