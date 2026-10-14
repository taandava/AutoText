package com.jmd.autotext.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jmd.autotext.data.AppRepository
import com.jmd.autotext.model.*
import com.jmd.autotext.scheduler.AlarmScheduler
import com.jmd.autotext.scheduler.DailyCampaignScheduler
import com.jmd.autotext.service.AccessibilityHealthManager
import com.jmd.autotext.ui.components.*
import com.jmd.autotext.ui.screens.dashboard.*
import com.jmd.autotext.ui.theme.BentoGlassTokens
import com.jmd.autotext.util.AppFileLogger
import com.jmd.autotext.util.SmsQuotaManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Suppress("FunctionName")
@Composable
fun DashboardScreen(
    onNavigateToCompose: (ChannelType) -> Unit,
    onNavigateToTasks: () -> Unit,
    onNavigateToDailyCampaign: (Long) -> Unit = {},
    onNavigateToSequences: () -> Unit = {},
    onEditTask: (ScheduledTask) -> Unit = {}
) {
    val context = LocalContext.current
    val repository = remember { AppRepository(context) }
    var allTasks by remember { mutableStateOf(emptyList<ScheduledTask>()) }
    var autoReplies by remember { mutableStateOf(emptyList<AutoReplyRule>()) }
    var smsDailyConfig by remember { mutableStateOf(DailyCampaignConfig(id = 1L, channel = "SMS", title = "Daily SMS Campaign")) }
    var waDailyConfig by remember { mutableStateOf(DailyCampaignConfig(id = 2L, channel = "WHATSAPP", title = "Daily WhatsApp Campaign")) }
    var waPoolStats by remember { mutableStateOf(DynamicPoolStats()) }
    var smsQuota by remember { mutableStateOf(SmsQuotaManager.QuotaStatus(0, 100, 100, false, "Calculating quota...")) }
    var isA11yActive by remember {
        mutableStateOf(AccessibilityHealthManager.isServiceRunning())
    }

    val pendingCount = remember(allTasks) { allTasks.count { it.status == TaskStatus.PENDING } }
    val doneCount = remember(allTasks) { allTasks.count { it.status == TaskStatus.DONE } }
    val failedCount = remember(allTasks) { allTasks.count { it.status == TaskStatus.FAILED } }
    val activeRepliesCount = remember(autoReplies) { autoReplies.count { it.isEnabled } }

    val upcomingTasks = remember(allTasks) {
        allTasks.filter { it.status == TaskStatus.PENDING }.take(5)
    }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val tasks = repository.getAllTasks()
            val replies = repository.getAllAutoReplyRules()
            val smsCfg = repository.getDailyCampaignConfig(1L)
            val waCfg = repository.getDailyCampaignConfig(2L)
            val poolStats = repository.getDynamicPoolStats(whatsApp = true)
            val quota = SmsQuotaManager.getQuotaStatus(context)
            if (!AccessibilityHealthManager.isServiceRunning() && AccessibilityHealthManager.hasWriteSecureSettings(context)) {
                AccessibilityHealthManager.ensureServiceAlive(context)
            }
            val a11yRunning = AccessibilityHealthManager.isServiceRunning()

            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                allTasks = tasks
                autoReplies = replies
                smsDailyConfig = smsCfg
                waDailyConfig = waCfg
                waPoolStats = poolStats
                smsQuota = quota
                isA11yActive = a11yRunning
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(BentoGlassTokens.VoidBackground)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // 1. Bento Glass Card: Daily SMS Campaign
        item {
            DashboardSmsCampaignCard(
                smsDailyConfig = smsDailyConfig,
                smsQuota = smsQuota,
                onNavigateToDailyCampaign = { onNavigateToDailyCampaign(1L) },
                onToggleActive = { active ->
                    val updated = smsDailyConfig.copy(isActive = active)
                    smsDailyConfig = updated
                    repository.saveDailyCampaignConfig(updated)
                    DailyCampaignScheduler.scheduleOrUpdate(context, 1L)
                    Toast.makeText(
                        context,
                        if (active) "Daily SMS Activated" else "Daily SMS Paused",
                        Toast.LENGTH_SHORT
                    ).show()
                },
                onSendBatch = {
                    if (smsQuota.isLimitReached) {
                        AppFileLogger.w(context, "DashboardScreen", "Manual Send SMS blocked: app daily safeguard of 100 SMS reached")
                        Toast.makeText(context, "India 100 SMS/day limit reached!", Toast.LENGTH_LONG).show()
                    } else {
                        AppFileLogger.i(context, "DashboardScreen", "Manual Send SMS triggered")
                        DailyCampaignScheduler.triggerNow(context, 1L)
                        Toast.makeText(context, "Dispatched SMS batch in background!", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }

        // 2. Bento Glass Card: Daily WhatsApp Campaign
        item {
            DashboardWhatsAppCampaignCard(
                waDailyConfig = waDailyConfig,
                waPoolStats = waPoolStats,
                isA11yActive = isA11yActive,
                onNavigateToDailyCampaign = { onNavigateToDailyCampaign(2L) },
                onToggleActive = { active ->
                    val updated = waDailyConfig.copy(isActive = active)
                    waDailyConfig = updated
                    repository.saveDailyCampaignConfig(updated)
                    DailyCampaignScheduler.scheduleOrUpdate(context, 2L)
                    Toast.makeText(
                        context,
                        if (active) "Daily WhatsApp Activated" else "Daily WhatsApp Paused",
                        Toast.LENGTH_SHORT
                    ).show()
                },
                onSendBatch = {
                    AppFileLogger.i(context, "DashboardScreen", "Manual Send WhatsApp batch triggered")
                    DailyCampaignScheduler.triggerNow(context, 2L)
                    Toast.makeText(context, "Dispatched WhatsApp batch in background!", Toast.LENGTH_SHORT).show()
                }
            )
        }

        // 3. Bento Telemetry Bar
        item {
            DashboardTelemetryBar(
                pendingCount = pendingCount,
                doneCount = doneCount,
                failedCount = failedCount,
                activeRepliesCount = activeRepliesCount
            )
        }

        // 4. Bento Quick Actions Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Quick Actions",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 15.sp,
                    color = BentoGlassTokens.TextPrimary
                )
                Text(
                    "8 Tools",
                    fontSize = 11.5.sp,
                    color = BentoGlassTokens.TextMuted
                )
            }
        }

        // 5. Bento Action Grid
        item {
            DashboardQuickActionsGrid(
                onNavigateToCompose = onNavigateToCompose,
                onNavigateToSequences = onNavigateToSequences,
                onNavigateToDailyCampaign = onNavigateToDailyCampaign
            )
        }

        // 6. Upcoming Tasks Header
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Upcoming Schedule",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 15.sp,
                    color = BentoGlassTokens.TextPrimary
                )
                TextButton(onClick = onNavigateToTasks) {
                    Text("View All (${allTasks.size})", color = BentoGlassTokens.NeonCyan, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // 7. Upcoming Tasks List or Empty State
        if (upcomingTasks.isEmpty()) {
            item {
                BentoEmptyState(
                    icon = Icons.Default.HourglassEmpty,
                    title = "No Pending Tasks",
                    description = "Your automated queue is currently clear.",
                    actionText = "Schedule New Task",
                    onActionClick = { onNavigateToCompose(ChannelType.SMS) }
                )
            }
        } else {
            items(upcomingTasks, key = { it.id }) { task ->
                DashboardTaskSummaryCard(
                    task = task,
                    onEdit = {
                        onEditTask(task)
                    },
                    onDelete = {
                        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                            repository.deleteTask(task.id)
                            AlarmScheduler(context).cancelTask(task.id)
                            val updated = repository.getAllTasks()
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                allTasks = updated
                            }
                        }
                    }
                )
            }
        }
    }
}
