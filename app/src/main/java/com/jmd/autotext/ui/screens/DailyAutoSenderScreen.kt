package com.jmd.autotext.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FactCheck
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.jmd.autotext.data.AppRepository
import com.jmd.autotext.model.*
import com.jmd.autotext.scanner.ContactIngestionEngine
import com.jmd.autotext.scheduler.DailyCampaignScheduler
import com.jmd.autotext.service.AccessibilityHealthManager
import com.jmd.autotext.ui.components.*
import com.jmd.autotext.ui.screens.daily.*
import com.jmd.autotext.ui.theme.BentoGlassTokens
import com.jmd.autotext.util.AppFileLogger
import kotlin.time.Duration.Companion.seconds
import com.jmd.autotext.util.SmsQuotaManager
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Suppress("FunctionName")
@Composable
fun DailyAutoSenderScreen(initialCampaignId: Long = 1L) {
    val context = LocalContext.current
    val repository = remember { AppRepository(context) }
    val scope = rememberCoroutineScope()

    var selectedCampaignId by remember(initialCampaignId) { mutableLongStateOf(initialCampaignId) } // 1L = SMS, 2L = WhatsApp
    var config by remember(selectedCampaignId) { mutableStateOf(DailyCampaignConfig(id = selectedCampaignId, channel = if (selectedCampaignId == 2L) "WHATSAPP" else "SMS", title = "Daily Campaign")) }
    var stats by remember { mutableStateOf(DynamicPoolStats()) }
    var contactsList by remember { mutableStateOf<List<DynamicContactItem>>(emptyList()) }
    var sentLedgerList by remember { mutableStateOf<List<DynamicContactItem>>(emptyList()) }
    var poolTab by remember { mutableIntStateOf(0) } // 0 = Pending Pool, 1 = Sent Ledger

    var isA11yActive by remember { mutableStateOf(false) }
    var isScanning by remember { mutableStateOf(false) }
    var lastScanSummary by remember { mutableStateOf<IngestionSummary?>(null) }
    var filterUnsentOnly by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var smsQuota by remember { mutableStateOf(SmsQuotaManager.QuotaStatus(0, 100, 100, false, "Calculating...")) }

    fun refreshData() {
        val wa = selectedCampaignId == 2L
        val currentTab = poolTab
        val query = searchQuery.trim()
        val unsentOnly = filterUnsentOnly
        isA11yActive = AccessibilityHealthManager.isServiceRunning()
        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val st = repository.getDynamicPoolStats(whatsApp = wa)
            val pool = if (currentTab == 0) {
                repository.getDynamicPool(
                    limit = 80,
                    filterUnsentOnly = unsentOnly,
                    searchQuery = query,
                    whatsApp = wa
                )
            } else contactsList

            val ledger = if (currentTab == 1) {
                repository.getSentContactsLedger(
                    whatsApp = wa,
                    limit = 80
                ).filter { item ->
                    if (query.isBlank()) true
                    else item.name.contains(query, ignoreCase = true) || item.phone.contains(query)
                }
            } else sentLedgerList

            val quota = if (!wa) SmsQuotaManager.getQuotaStatus(context) else smsQuota

            withContext(kotlinx.coroutines.Dispatchers.Main) {
                stats = st
                contactsList = pool
                sentLedgerList = ledger
                smsQuota = quota
            }
        }
    }

    LaunchedEffect(initialCampaignId) {
        selectedCampaignId = initialCampaignId
    }

    LaunchedEffect(filterUnsentOnly, searchQuery, selectedCampaignId, poolTab) {
        if (searchQuery.isNotBlank()) {
            kotlinx.coroutines.delay(250) // Debounce search keystrokes
        }
        withContext(kotlinx.coroutines.Dispatchers.IO) {
            val cfg = repository.getDailyCampaignConfig(selectedCampaignId)
            if (selectedCampaignId == 2L && !AccessibilityHealthManager.isServiceRunning() && AccessibilityHealthManager.hasWriteSecureSettings(context)) {
                AccessibilityHealthManager.ensureServiceAlive(context)
            }
            withContext(kotlinx.coroutines.Dispatchers.Main) {
                config = cfg
            }
        }
        refreshData()
    }

    val isWhatsApp = selectedCampaignId == 2L
    val campaignThemeColor = if (isWhatsApp) BentoGlassTokens.VividEmerald else BentoGlassTokens.NeonCyan

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(BentoGlassTokens.VoidBackground)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Campaign Selector
        item {
            GlassSegmentedControl(
                items = listOf("SMS Daily Automation", "WhatsApp Daily Automation"),
                selectedIndex = if (isWhatsApp) 1 else 0,
                onItemSelected = { selectedCampaignId = if (it == 1) 2L else 1L },
                activeColor = if (selectedCampaignId == 2L) BentoGlassTokens.VividEmerald else BentoGlassTokens.NeonCyan
            )
        }

        // Application-level daily SMS safeguard (not a verified regulatory limit)
        if (!isWhatsApp) {
            item {
                DailySmsQuotaCard(smsQuota = smsQuota)
            }
        }

        // Top Status Bento Banner
        item {
            DailyCampaignBannerCard(
                isWhatsApp = isWhatsApp,
                config = config,
                stats = stats,
                isA11yActive = isA11yActive,
                isScanning = isScanning,
                campaignThemeColor = campaignThemeColor,
                lastScanSummary = lastScanSummary,
                onToggleActive = { active ->
                    val updated = config.copy(isActive = active)
                    config = updated
                    repository.saveDailyCampaignConfig(updated)
                    DailyCampaignScheduler.scheduleOrUpdate(context, updated.id)
                    Toast.makeText(
                        context,
                        if (active) "${if (isWhatsApp) "Daily WhatsApp" else "Daily SMS"} Automation Activated" else "Automation Paused",
                        Toast.LENGTH_SHORT
                    ).show()
                },
                onAutoHeal = {
                    scope.launch {
                        val alive = withContext(kotlinx.coroutines.Dispatchers.IO) {
                            AccessibilityHealthManager.ensureServiceAlive(context)
                        }
                        isA11yActive = alive
                        Toast.makeText(context, if (alive) "Service rebound & active" else "Heal triggered. Awaiting binder...", Toast.LENGTH_SHORT).show()
                    }
                },
                onScan = {
                    if (!isScanning) {
                        isScanning = true
                        scope.launch {
                            val scanner = ContactIngestionEngine(context, repository)
                            val summary = scanner.scanAndIngestAll()
                            lastScanSummary = summary
                            refreshData()
                            isScanning = false
                            Toast.makeText(context, "Ingested ${summary.newAdded} new contacts into pool!", Toast.LENGTH_LONG).show()
                        }
                    }
                },
                onSendBatch = {
                    if (!isWhatsApp && smsQuota.isLimitReached) {
                        AppFileLogger.w(context, "DailyAutoSenderScreen", "Manual Send SMS blocked: app daily safeguard of 100 SMS reached")
                        Toast.makeText(context, "App safeguard of 100 SMS/day reached across device!", Toast.LENGTH_LONG).show()
                        return@DailyCampaignBannerCard
                    }
                    AppFileLogger.i(context, "DailyAutoSenderScreen", "Manual Send Batch triggered for campaign $selectedCampaignId (isWhatsApp=$isWhatsApp)")
                    DailyCampaignScheduler.triggerNow(context, selectedCampaignId)
                    val typeName = if (isWhatsApp) "WhatsApp messages" else "SMS"
                    Toast.makeText(context, "Dispatched batch of ${config.dailyLimit} $typeName in background!", Toast.LENGTH_SHORT).show()
                    scope.launch {
                        kotlinx.coroutines.delay(2.seconds)
                        smsQuota = SmsQuotaManager.getQuotaStatus(context)
                        refreshData()
                    }
                }
            )
        }

        // Campaign Configuration Bento Card
        item {
            DailyCampaignConfigCard(
                config = config,
                isWhatsApp = isWhatsApp,
                campaignThemeColor = campaignThemeColor,
                onConfigChange = { config = it },
                onSave = { updated ->
                    repository.saveDailyCampaignConfig(updated)
                    DailyCampaignScheduler.scheduleOrUpdate(context, updated.id)
                    Toast.makeText(context, "${if (isWhatsApp) "WhatsApp" else "SMS"} campaign settings saved successfully", Toast.LENGTH_SHORT).show()
                }
            )
        }

        // Contact Pool & Sent Ledger Tab Selector
        item {
            GlassSegmentedControl(
                items = listOf("Pending Pool (${stats.unsentCount})", "Sent Ledger (${stats.sentCount})"),
                selectedIndex = poolTab,
                onItemSelected = { poolTab = it },
                activeColor = if (poolTab == 0) BentoGlassTokens.ElectricIndigo else BentoGlassTokens.VividEmerald
            )
        }

        if (poolTab == 0) {
            item {
                DailyContactPoolHeaderCard(
                    stats = stats,
                    searchQuery = searchQuery,
                    onSearchQueryChange = { searchQuery = it },
                    filterUnsentOnly = filterUnsentOnly,
                    onFilterUnsentOnlyChange = { filterUnsentOnly = it },
                    onClearPool = {
                        repository.clearDynamicPool()
                        refreshData()
                        Toast.makeText(context, "Contact pool cleared", Toast.LENGTH_SHORT).show()
                    }
                )
            }

            if (contactsList.isEmpty()) {
                item {
                    BentoEmptyState(
                        icon = Icons.Default.Contacts,
                        title = "No Contacts in Pool",
                        description = "Tap 'Scan' above to automatically discover and ingest phone contacts, SMS messages and calls."
                    )
                }
            } else {
                items(contactsList, key = { it.id }) { contact ->
                    BentoContactItemCard(
                        contact = contact,
                        isWhatsApp = isWhatsApp,
                        onToggle = { enabled ->
                            repository.toggleDynamicContact(contact.id, enabled)
                            refreshData()
                        },
                        onDelete = {
                            repository.deleteDynamicContact(contact.id)
                            refreshData()
                        }
                    )
                }
            }
        } else {
            item {
                DailySentLedgerHeaderCard(
                    isWhatsApp = isWhatsApp,
                    sentCount = sentLedgerList.size,
                    searchQuery = searchQuery,
                    onSearchQueryChange = { searchQuery = it },
                    campaignThemeColor = campaignThemeColor,
                    onResetCycle = {
                        repository.resetDailyCampaignLedger(whatsApp = isWhatsApp)
                        refreshData()
                        Toast.makeText(context, "${if (isWhatsApp) "WhatsApp" else "SMS"} campaign cycle reset successfully!", Toast.LENGTH_SHORT).show()
                    }
                )
            }

            if (sentLedgerList.isEmpty()) {
                item {
                    BentoEmptyState(
                        icon = Icons.AutoMirrored.Filled.FactCheck,
                        title = "Sent Ledger is Empty",
                        description = "No ${if (isWhatsApp) "WhatsApp" else "SMS"} dispatches have been recorded yet in this cycle. Send your first batch above to build your ledger."
                    )
                }
            } else {
                items(sentLedgerList, key = { "ledger_${it.id}" }) { contact ->
                    BentoLedgerItemCard(
                        contact = contact,
                        isWhatsApp = isWhatsApp,
                        onRequeue = {
                            repository.resetContactLedger(contact.id, whatsApp = isWhatsApp)
                            refreshData()
                            Toast.makeText(
                                context,
                                "${contact.name.ifEmpty { contact.phone }} re-queued for next dispatch",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    )
                }
            }
        }
    }
}