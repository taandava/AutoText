/**
 * Package: com.jmd.autotext.ui.screen.logs
 * Module: UI Log Detail & Telemetry View
 *
 * Detailed view presenting technical execution telemetry for an individual [ExecutionLog] item.
 * Displays timestamp, recipient identifiers, channel details, dispatch payloads, raw error stack
 * traces (if failed), and an instant one-tap retry dispatch trigger.
 */
package com.jmd.autotext.ui.screen.logs

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.withLock
import java.text.SimpleDateFormat
import java.util.Date
import kotlin.time.Duration.Companion.milliseconds

import com.jmd.autotext.core.AppFileLogger
import com.jmd.autotext.core.PhoneNumberNormalizer
import com.jmd.autotext.core.SmsQuotaManager
import com.jmd.autotext.core.showAppToast
import com.jmd.autotext.data.model.ChannelType
import com.jmd.autotext.data.model.ExecutionLog
import com.jmd.autotext.data.repo.AppRepository
import com.jmd.autotext.engine.SequentialTaskEngine
import com.jmd.autotext.engine.SmsSender
import com.jmd.autotext.engine.WhatsAppAutomationSender
import com.jmd.autotext.service.AutoTextAccessibilityService
import com.jmd.autotext.ui.components.*
import com.jmd.autotext.ui.theme.AppDesignTokens

/**
 * Detailed view presenting technical execution telemetry for an individual [ExecutionLog] item.
 *
 * @param log Execution log model item to inspect.
 * @param repository Data layer repository for task retry and status queries.
 * @param onBack Callback invoked to exit detail view.
 */
@Composable
fun logDetailView(
    log: ExecutionLog,
    repository: AppRepository,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isRetrying by remember { mutableStateOf(false) }
    var retryResult by remember { mutableStateOf<String?>(null) }
    val isSuccess = log.status.equals("SUCCESS", ignoreCase = true)
    val isQueued = log.status.equals("QUEUED", ignoreCase = true)
    val statusColor = when {
        isSuccess -> MaterialTheme.colorScheme.tertiary
        isQueued -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.error
    }
    val currentLocale = LocalLocale.current.platformLocale
    val timeStr = SimpleDateFormat("dd MMMM yyyy, hh:mm:ss a", currentLocale).format(Date(log.timestampMillis))

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            appSubScreenHeader(
                title = "Execution Details",
                subtitle = "Log #${log.id} • ${log.channel}",
                onBack = onBack
            )
        },
        containerColor = AppDesignTokens.VoidBackground
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .navigationBarsPadding()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Status Header Banner Enterprise Card
            appCard(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = statusColor.copy(alpha = 0.12f),
                borderColor = statusColor.copy(alpha = 0.35f)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isSuccess) "✅" else if (isQueued) "⏳" else "❌",
                        fontSize = 30.sp
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(
                            text = if (isSuccess) "Delivery Successful" else if (isQueued) "Queued (TRAI / App Quota Reached)" else "Delivery Failed",
                            fontWeight = FontWeight.Bold,
                            color = statusColor,
                            fontSize = 15.sp
                        )
                        Text(timeStr, fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // Target Details Enterprise Card
            appCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Target Information", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Recipient:", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                        Text(log.recipient, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Channel:", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                        val channelEmoji = if (log.channel.equals("SMS", ignoreCase = true)) "📩" else "💬"
                        Text("$channelEmoji ${log.channel}", color = if (log.channel.equals("SMS", ignoreCase = true)) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Task ID:", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                        Text("#${log.taskId}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Status Code:", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                        Text(log.status, color = statusColor, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }

            // Message Content Enterprise Card
            appCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Message Snippet", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp)
                    IconButton(onClick = {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        com.jmd.autotext.compat.ClipboardCompat.setPrimaryClip(cm, ClipData.newPlainText("Dispatched Message", log.messageSnippet))
                        context.showAppToast("Message copied")
                    }) {
                        Text("📋", fontSize = 18.sp)
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = log.messageSnippet,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(AppDesignTokens.GlassSurfaceSubtle, AppDesignTokens.ShapeMedium)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, AppDesignTokens.ShapeMedium)
                        .padding(12.dp)
                )
            }

            // Technical Details & Error Stack Traces
            if (log.details.isNotEmpty()) {
                appCard(modifier = Modifier.fillMaxWidth()) {
                    Text("Technical Trace & Execution Log", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = log.details,
                        color = if (isSuccess) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                        fontSize = 11.5.sp,
                        lineHeight = 16.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(AppDesignTokens.VoidBackground, AppDesignTokens.ShapeMedium)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, AppDesignTokens.ShapeMedium)
                            .padding(10.dp)
                    )
                }
            }

            // Dynamic Manual Retry Trigger
            if (!isSuccess) {
                appCardElevated(
                    modifier = Modifier.fillMaxWidth(),
                    accentGlow = MaterialTheme.colorScheme.primary
                ) {
                    Text("Manual Re-dispatch Action", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Immediately dispatch this message directly via carrier SMS or WhatsApp accessibility.",
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 16.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    retryResult?.let { result ->
                        Text(
                            text = result,
                            color = if (result.contains("success", ignoreCase = true) || result.contains("sent", ignoreCase = true)) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }

                    glassButton(
                        text = if (isRetrying) "Dispatching..." else "Retry Dispatch Now",
                        onClick = {
                            isRetrying = true
                            retryResult = null
                            val cleanTargetPhone = PhoneNumberNormalizer.extractRawPhoneNumber(log.recipient)
                            if (log.channel.equals("SMS", ignoreCase = true)) {
                                coroutineScope.launch {
                                    val sendResult = withContext(Dispatchers.IO) {
                                        val sender = SmsSender(context)
                                        sender.sendSmsWithResult(cleanTargetPhone, log.messageSnippet, enforceQuota = true)
                                    }
                                    val isQuota = sendResult.reason.contains("limit", ignoreCase = true) || sendResult.reason.contains("quota", ignoreCase = true)
                                    val newStatus = when (sendResult.deliveryStatus) {
                                        SmsSender.DeliveryStatus.CONFIRMED -> "SUCCESS"
                                        SmsSender.DeliveryStatus.PENDING_TIMEOUT -> "DISPATCHED"
                                        SmsSender.DeliveryStatus.PARTIAL_DISPATCH -> "PARTIAL"
                                        SmsSender.DeliveryStatus.FAILED -> if (isQuota) "QUEUED" else "FAILED"
                                    }
                                    val details = if (sendResult.success) "Manual retry, parts: ${sendResult.segments}" else sendResult.reason
                                    repository.insertLog(ExecutionLog(taskId = log.taskId, recipient = log.recipient, channel = "SMS", status = newStatus, messageSnippet = log.messageSnippet, details = details, segments = sendResult.segments, timestampMillis = System.currentTimeMillis()))
                                    if (sendResult.success) {
                                        SmsQuotaManager.commitSmsQuotaReservation(sendResult.segments)
                                    }
                                    AppFileLogger.i(context, "SmsSender", "Manual retry for ${log.recipient}: status=$newStatus, reason=${sendResult.reason}")
                                    retryResult = when (sendResult.deliveryStatus) {
                                        SmsSender.DeliveryStatus.CONFIRMED -> "SMS sent and confirmed! (${sendResult.segments} parts)"
                                        SmsSender.DeliveryStatus.PENDING_TIMEOUT -> "Dispatched to modem (${sendResult.segments} parts). Awaiting carrier confirmation."
                                        SmsSender.DeliveryStatus.PARTIAL_DISPATCH -> "Partial send: ${sendResult.successfulParts}/${sendResult.segments} parts confirmed. Retry blocked to avoid duplicates."
                                        SmsSender.DeliveryStatus.FAILED -> "Cannot send: ${sendResult.reason}"
                                    }
                                    isRetrying = false
                                }
                            } else {
                                coroutineScope.launch {
                                    val mutex = SequentialTaskEngine.getUiAutomationMutex(context)
                                    val isSuccess = mutex.withLock {
                                        withContext(Dispatchers.IO) {
                                            val waSender = WhatsAppAutomationSender(context)
                                            val launched = waSender.send(log.taskId, ChannelType.WHATSAPP, cleanTargetPhone, log.messageSnippet)
                                            if (!launched) {
                                                false
                                            } else {
                                                // Await accessibility service terminal result up to 20 seconds
                                                var waited = 0L
                                                while (AutoTextAccessibilityService.lastSendResult == AutoTextAccessibilityService.RESULT_PENDING && waited < 20000L) {
                                                    delay(500.milliseconds)
                                                    waited += 500L
                                                }
                                                AutoTextAccessibilityService.lastSendResult == AutoTextAccessibilityService.RESULT_SUCCESS
                                            }
                                        }
                                    }
                                    val newStatus = if (isSuccess) "SUCCESS" else "FAILED"
                                    repository.insertLog(ExecutionLog(taskId = log.taskId, recipient = log.recipient, channel = "WHATSAPP", status = newStatus, messageSnippet = log.messageSnippet, timestampMillis = System.currentTimeMillis()))
                                    AppFileLogger.i(context, "WhatsAppSender", "Manual retry for ${log.recipient}: completed=$isSuccess")
                                    retryResult = if (isSuccess) "WhatsApp message sent successfully!" else "WhatsApp delivery failed or timed out."
                                    isRetrying = false
                                }
                            }
                        },
                        emoji = "🔄",
                        variant = GlassButtonVariant.PRIMARY,
                        enabled = !isRetrying,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}
