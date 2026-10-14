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

// ----------------- SUB SCREEN: LOG DETAIL VIEW -----------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogDetailView(
    log: ExecutionLog,
    repository: AppRepository,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var isRetrying by remember { mutableStateOf(false) }
    var retryResult by remember { mutableStateOf<String?>(null) }
    val isSuccess = log.status.equals("SUCCESS", ignoreCase = true)
    val isQueued = log.status.equals("QUEUED", ignoreCase = true)
    val statusColor = when {
        isSuccess -> BentoGlassTokens.VividEmerald
        isQueued -> BentoGlassTokens.RadiantAmber
        else -> BentoGlassTokens.CyberRose
    }
    val currentLocale = androidx.compose.ui.platform.LocalLocale.current.platformLocale
    val timeStr = SimpleDateFormat("dd MMMM yyyy, hh:mm:ss a", currentLocale).format(Date(log.timestampMillis))

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Execution Details", fontWeight = FontWeight.Bold, color = BentoGlassTokens.TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = BentoGlassTokens.TextPrimary) }
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
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Status Header Banner Bento Card
            BentoCard(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = statusColor.copy(alpha = 0.12f),
                borderColor = statusColor.copy(alpha = 0.35f)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isSuccess) Icons.Default.CheckCircle else if (isQueued) Icons.Default.HourglassTop else Icons.Default.Cancel,
                        contentDescription = null,
                        tint = statusColor,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(
                            text = if (isSuccess) "Delivery Successful" else if (isQueued) "Queued (TRAI / App Quota Reached)" else "Delivery Failed",
                            fontWeight = FontWeight.Bold,
                            color = statusColor,
                            fontSize = 15.sp
                        )
                        Text(timeStr, fontSize = 11.5.sp, color = BentoGlassTokens.TextMuted)
                    }
                }
            }

            // Target Details Bento Card
            BentoCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Target Information", fontWeight = FontWeight.Bold, color = BentoGlassTokens.TextPrimary, fontSize = 14.sp)
                    HorizontalDivider(color = BentoGlassTokens.GlassBorderSubtle)

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Recipient:", color = BentoGlassTokens.TextMuted, fontSize = 13.sp)
                        Text(log.recipient, color = BentoGlassTokens.TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Channel:", color = BentoGlassTokens.TextMuted, fontSize = 13.sp)
                        Text(log.channel, color = if (log.channel.equals("SMS", ignoreCase = true)) BentoGlassTokens.NeonCyan else BentoGlassTokens.VividEmerald, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Task ID:", color = BentoGlassTokens.TextMuted, fontSize = 13.sp)
                        Text("#${log.taskId}", color = BentoGlassTokens.TextMuted, fontSize = 13.sp)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Status Code:", color = BentoGlassTokens.TextMuted, fontSize = 13.sp)
                        Text(log.status, color = statusColor, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }

            // Message Content Bento Card
            BentoCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Message Snippet", fontWeight = FontWeight.Bold, color = BentoGlassTokens.TextPrimary, fontSize = 14.sp)
                    IconButton(onClick = {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("Dispatched Message", log.messageSnippet))
                        Toast.makeText(context, "Copied message to clipboard", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy Message", tint = BentoGlassTokens.NeonCyan, modifier = Modifier.size(18.dp))
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = log.messageSnippet,
                    color = BentoGlassTokens.TextPrimary,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(BentoGlassTokens.GlassSurfaceSubtle, BentoGlassTokens.ShapeMedium)
                        .border(1.dp, BentoGlassTokens.GlassBorderSubtle, BentoGlassTokens.ShapeMedium)
                        .padding(12.dp)
                )
            }

            // Action / Retry Bento Card
            if (!isSuccess) {
                BentoCard(modifier = Modifier.fillMaxWidth()) {
                    Text("Action & Retry", fontWeight = FontWeight.Bold, color = BentoGlassTokens.RadiantAmber, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (isQueued)
                            "This contact was queued due to quota limits. It is prioritized to send first on the next cycle, or you can retry immediate delivery right now."
                        else
                            "Delivery encountered an error. Tapping Retry Now will attempt immediate delivery and log the result.",
                        fontSize = 11.5.sp,
                        color = BentoGlassTokens.TextMuted,
                        lineHeight = 17.sp
                    )

                    if (retryResult != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(retryResult!!, fontSize = 12.sp, color = BentoGlassTokens.NeonCyan)
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    GlassButton(
                        text = if (isRetrying) "Sending..." else "Retry Delivery Now",
                        onClick = {
                            isRetrying = true
                            if (log.channel.equals("SMS", ignoreCase = true)) {
                                val sender = SmsSender(context)
                                val sendResult = sender.sendSmsWithResult(log.recipient, log.messageSnippet, enforceQuota = true)
                                val isQuota = sendResult.reason.contains("limit", ignoreCase = true) || sendResult.reason.contains("quota", ignoreCase = true)
                                val newStatus = if (sendResult.success) "SUCCESS" else if (isQuota) "QUEUED" else "FAILED"
                                val details = if (sendResult.success) "Manual retry, parts: ${sendResult.segments}" else sendResult.reason
                                repository.insertLog(ExecutionLog(taskId = log.taskId, recipient = log.recipient, channel = "SMS", status = newStatus, messageSnippet = log.messageSnippet, details = details, timestampMillis = System.currentTimeMillis()))
                                AppFileLogger.i(context, "SmsSender", "Manual retry for ${log.recipient}: status=$newStatus, reason=${sendResult.reason}")
                                retryResult = if (sendResult.success) "SMS sent successfully! (${sendResult.segments} parts)" else "Cannot send: ${sendResult.reason}"
                            } else {
                                val waSender = WhatsAppAutomationSender(context)
                                val launched = waSender.send(log.taskId, ChannelType.WHATSAPP, log.recipient, log.messageSnippet)
                                val newStatus = if (launched) "SUCCESS" else "FAILED"
                                repository.insertLog(ExecutionLog(taskId = log.taskId, recipient = log.recipient, channel = "WHATSAPP", status = newStatus, messageSnippet = log.messageSnippet, timestampMillis = System.currentTimeMillis()))
                                AppFileLogger.i(context, "WhatsAppSender", "Manual retry for ${log.recipient}: launched=$launched")
                                retryResult = if (launched) "WhatsApp automation launched!" else "WhatsApp delivery failed."
                            }
                            isRetrying = false
                        },
                        icon = Icons.Default.Refresh,
                        variant = GlassButtonVariant.PRIMARY,
                        enabled = !isRetrying,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}


