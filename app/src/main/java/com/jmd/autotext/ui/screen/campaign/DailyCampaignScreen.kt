package com.jmd.autotext.ui.screen.campaign

import android.content.*
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

import com.jmd.autotext.core.*
import com.jmd.autotext.data.model.*
import com.jmd.autotext.data.repo.*
import com.jmd.autotext.engine.*
import com.jmd.autotext.scheduler.*
import com.jmd.autotext.ui.components.*
import com.jmd.autotext.ui.theme.*

/**
 * Dedicated recurrent daily campaign manager orchestrating SMS and WhatsApp automated multi-contact dispatches.
 *
 * @param initialCampaignId Target campaign identifier to open on initial composition.
 * @param onCampaignIdChange Callback invoked when active campaign selection changes.
 */
@Composable
fun dailyAutoSenderScreen(
    initialCampaignId: Long = 0L,
    onCampaignIdChange: ((Long) -> Unit)? = null,
    onBack: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val repository = remember { AppRepository(context) }
    val scope = rememberCoroutineScope()

    var selectedCampaignId by remember(initialCampaignId) { mutableLongStateOf(initialCampaignId) }
    var allCampaigns by remember { mutableStateOf(emptyList<DailyCampaignConfig>()) }
    var config by remember(selectedCampaignId) {
        mutableStateOf(
            if (selectedCampaignId > 0L) {
                DailyCampaignConfig(id = selectedCampaignId)
            } else {
                DailyCampaignConfig()
            }
        )
    }
    var stats by remember { mutableStateOf(DynamicPoolStats()) }
    var poolStats by remember { mutableStateOf(DynamicPoolStats()) }
    var contactsList by remember { mutableStateOf<List<DynamicContactItem>>(emptyList()) }
    var sentLedgerList by remember { mutableStateOf<List<DynamicContactItem>>(emptyList()) }
    var poolTab by remember { mutableIntStateOf(0) } // 0 = Pending Pool, 1 = Sent Ledger

    var isA11yActive by remember { mutableStateOf(false) }
    var isScanning by remember { mutableStateOf(false) }
    var lastScanSummary by remember { mutableStateOf<IngestionSummary?>(null) }
    var filterUnsentOnly by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var smsQuota by remember { mutableStateOf(SmsQuotaManager.QuotaStatus(0, 100, 100, false, "Calculating...")) }

    var showSelectChannelDialog by remember { mutableStateOf(false) }
    var showCreateCampaignDialog by remember { mutableStateOf(false) }
    var createChannelType by remember { mutableStateOf("SMS") }

    fun loadAllCampaigns() {
        scope.launch(Dispatchers.IO) {
            val list = repository.getAllDailyCampaignConfigs()
            val pStats = repository.getDynamicPoolStats()
            withContext(Dispatchers.Main) {
                allCampaigns = list
                poolStats = pStats
                if (selectedCampaignId > 0L) {
                    val matching = list.firstOrNull { it.id == selectedCampaignId }
                    if (matching != null) {
                        config = matching
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        loadAllCampaigns()
    }

    LaunchedEffect(initialCampaignId) {
        selectedCampaignId = initialCampaignId
    }

    fun setCampaign(id: Long) {
        selectedCampaignId = id
        onCampaignIdChange?.invoke(id)
    }

    fun refreshData() {
        val currentTab = poolTab
        val query = searchQuery.trim()
        val campId = selectedCampaignId
        isA11yActive = AccessibilityHealthManager.isServiceOperational()
        scope.launch(Dispatchers.IO) {
            val quota = SmsQuotaManager.getQuotaStatus(context)
            val pStats = repository.getDynamicPoolStats()
            if (campId > 0L) {
                val cfg = repository.getDailyCampaignConfig(campId)
                val isWa = cfg.channel.contains("WHATSAPP", ignoreCase = true)
                val st = repository.getCampaignStats(campId)
                val pending = if (currentTab == 0) {
                    repository.getPendingPoolContactsForCampaign(
                        campaignId = campId,
                        isWhatsApp = isWa,
                        limit = 100
                    ).filter { item ->
                        query.isBlank() || item.name.contains(query, ignoreCase = true) || item.phone.contains(query)
                    }
                } else contactsList

                val ledger = if (currentTab == 1) {
                    repository.getSentContactsForCampaign(
                        campaignId = campId,
                        limit = 100
                    ).filter { item ->
                        query.isBlank() || item.name.contains(query, ignoreCase = true) || item.phone.contains(query)
                    }
                } else sentLedgerList

                withContext(Dispatchers.Main) {
                    config = cfg
                    stats = st
                    poolStats = pStats
                    contactsList = pending
                    sentLedgerList = ledger
                    smsQuota = quota
                }
            } else {
                val pool = repository.getDynamicPool(limit = 100, searchQuery = query)
                withContext(Dispatchers.Main) {
                    poolStats = pStats
                    smsQuota = quota
                    contactsList = pool
                }
            }
        }
    }

    LaunchedEffect(selectedCampaignId, poolTab, filterUnsentOnly, searchQuery) {
        if (searchQuery.isNotBlank()) {
            delay(250.milliseconds)
        }
        refreshData()
    }

    if (selectedCampaignId != 0L || onBack != null) {
        BackHandler {
            if (onBack != null) {
                onBack()
            } else {
                setCampaign(0L)
            }
        }
    }

    val activeCampaigns = allCampaigns.filter { it.isActive }
    val pausedCampaigns = allCampaigns.filter { !it.isActive }

    if (selectedCampaignId == 0L) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 14.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // ==========================================
            // OVERVIEW MODE: ALL ACTIVE DAILY CAMPAIGNS
            // ==========================================
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "Campaigns",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Surface(
                            shape = AppDesignTokens.ShapePill,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "${activeCampaigns.size} Active",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        glassButton(
                            text = if (isScanning) "Syncing..." else "Sync Pool",
                            emoji = "🔄",
                            variant = GlassButtonVariant.SECONDARY,
                            isLoading = isScanning,
                            onClick = {
                                if (!isScanning) {
                                    isScanning = true
                                    scope.launch {
                                        val scanner = ContactIngestionEngine(context, repository)
                                        val summary = scanner.scanAndIngestAll()
                                        lastScanSummary = summary
                                        refreshData()
                                        loadAllCampaigns()
                                        isScanning = false
                                        context.showAppToast(
                                            "Ingested ${summary.newAdded} new contacts! (Total pool: ${summary.totalPoolCount})",
                                            isLong = true
                                        )
                                    }
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            shape = AppDesignTokens.ShapePill
                        )
                        glassButton(
                            text = "New Campaign",
                            emoji = "➕",
                            variant = GlassButtonVariant.PRIMARY,
                            onClick = {
                                showSelectChannelDialog = true
                            },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            shape = AppDesignTokens.ShapePill
                        )
                    }
                }
            }

            if (activeCampaigns.isNotEmpty()) {
                // Pool Stats & Device Health Banner (only shown when campaigns are active)
                item {
                    appCard(modifier = Modifier.fillMaxWidth(), shape = AppDesignTokens.ShapeMedium) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            appStatCard(
                                modifier = Modifier.weight(1f),
                                title = "Unique Pool",
                                value = "${poolStats.totalCount}",
                                icon = Icons.Default.People,
                                iconTint = MaterialTheme.colorScheme.primary
                            )
                            appStatCard(
                                modifier = Modifier.weight(1f),
                                title = "Active Campaigns",
                                value = "${activeCampaigns.size}",
                                icon = Icons.AutoMirrored.Filled.ScheduleSend,
                                iconTint = MaterialTheme.colorScheme.tertiary
                            )
                            appStatCard(
                                modifier = Modifier.weight(1f),
                                title = "SMS Quota",
                                value = "${smsQuota.remaining}",
                                icon = Icons.Default.Sms,
                                iconTint = MaterialTheme.colorScheme.primary
                            )
                        }
                        lastScanSummary?.let { summary ->
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                "Last Scan: ${summary.contactsScanned} Contacts, ${summary.smsScanned} SMS, ${summary.callLogsScanned} Calls, ${summary.waChatsScanned} WhatsApp, ${summary.waBusinessScanned} WA Business • +${summary.newAdded} added",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
                item {
                    Text(
                        "ACTIVE DAILY CAMPAIGNS (${activeCampaigns.size})",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 0.5.sp
                    )
                }

                items(activeCampaigns, key = { "active_${it.id}" }) { cmp ->
                    activeCampaignOverviewCard(
                        campaign = cmp,
                        sentToday = repository.getSentCountForCampaignToday(cmp.id),
                        stats = repository.getCampaignStats(cmp.id),
                        onToggleActive = { active ->
                            val updated = cmp.copy(isActive = active)
                            repository.saveDailyCampaignConfig(updated)
                            if (active) {
                                DailyCampaignScheduler.scheduleOrUpdate(context, updated.id)
                            } else {
                                DailyCampaignScheduler.cancel(context, updated.id)
                                com.jmd.autotext.service.AutoTextAccessibilityService.clearPending()
                            }
                            loadAllCampaigns()
                            context.showAppToast(if (active) "Campaign Activated" else "Campaign Paused: Execution stopped")
                        },
                        onRunNow = {
                            val isWa = cmp.channel.contains("WHATSAPP", ignoreCase = true)
                            if (!isWa && smsQuota.isLimitReached) {
                                context.showAppToast("Daily SMS limit reached", isLong = true)
                                return@activeCampaignOverviewCard
                            }
                            DailyCampaignScheduler.triggerNow(context, cmp.id)
                            context.showAppToast("Batch dispatched for ${cmp.title}")
                            scope.launch {
                                delay(2.seconds)
                                refreshData()
                            }
                        },
                        onConfigure = {
                            setCampaign(cmp.id)
                        },
                        onViewContacts = {
                            setCampaign(cmp.id)
                            poolTab = 0
                        }
                    )
                }

                if (pausedCampaigns.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "PAUSED CAMPAIGNS (${pausedCampaigns.size})",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            letterSpacing = 0.5.sp
                        )
                    }

                    items(pausedCampaigns, key = { "paused_${it.id}" }) { cmp ->
                        pausedCampaignOverviewCard(
                            campaign = cmp,
                            onResume = {
                                val updated = cmp.copy(isActive = true)
                                repository.saveDailyCampaignConfig(updated)
                                DailyCampaignScheduler.scheduleOrUpdate(context, updated.id)
                                loadAllCampaigns()
                                context.showAppToast("Campaign Resumed")
                            },
                            onConfigure = {
                                setCampaign(cmp.id)
                            },
                            onDelete = {
                                if (cmp.id > 2L) {
                                    repository.deleteDailyCampaignConfig(cmp.id)
                                    loadAllCampaigns()
                                    context.showAppToast("Campaign deleted")
                                }
                            }
                        )
                    }
                }
            } else {
                // ==========================================
                // BLANK STATE: NO ACTIVE CAMPAIGNS
                // ==========================================
                item {
                    dailyCampaignEmptyStateCard(
                        totalPoolCount = poolStats.totalCount,
                        onSelectChannel = { ch ->
                            val newCfg = DailyCampaignConfig(
                                id = (allCampaigns.maxOfOrNull { it.id } ?: 0L) + 1L,
                                title = when (ch) {
                                    "WHATSAPP_BUSINESS" -> "Daily WhatsApp Business Outreach"
                                    "WHATSAPP" -> "Daily WhatsApp Outreach"
                                    else -> "Daily SMS Outreach"
                                },
                                channel = ch,
                                isActive = true
                            )
                            repository.saveDailyCampaignConfig(newCfg)
                            DailyCampaignScheduler.scheduleOrUpdate(context, newCfg.id)
                            setCampaign(newCfg.id)
                            loadAllCampaigns()
                        },
                        onSyncPool = {
                            if (!isScanning) {
                                isScanning = true
                                scope.launch {
                                    val scanner = ContactIngestionEngine(context, repository)
                                    val summary = scanner.scanAndIngestAll()
                                    lastScanSummary = summary
                                    refreshData()
                                    loadAllCampaigns()
                                    isScanning = false
                                    context.showAppToast(
                                        "Ingested ${summary.newAdded} new contacts! (Total pool: ${summary.totalPoolCount})",
                                        isLong = true
                                    )
                                }
                            }
                        },
                        isScanning = isScanning,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                }
            }
        }
    } else {
        // ==========================================
        // TYPE-SPECIFIC CAMPAIGN FORM & LEDGER MODE
        // ==========================================
        val isWhatsApp = config.channel.contains("WHATSAPP", ignoreCase = true)
        val isWhatsAppBusiness = config.channel.contains("BUSINESS", ignoreCase = true)
        val campaignThemeColor = when {
            isWhatsAppBusiness -> MaterialTheme.colorScheme.primary
            isWhatsApp -> MaterialTheme.colorScheme.tertiary
            else -> MaterialTheme.colorScheme.primary
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            appSubScreenHeader(
                title = config.title.ifBlank { "Daily Campaign #${config.id}" },
                subtitle = "${config.channel} • ${if (config.isActive) "Active" else "Paused"}",
                onBack = {
                    if (onBack != null) onBack() else setCampaign(0L)
                },
                actions = {
                    appBadge(
                        text = if (config.isActive) "ACTIVE" else "PAUSED",
                        color = if (config.isActive) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                        showDot = true
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Switch(
                        checked = config.isActive,
                        onCheckedChange = { active ->
                            val updated = config.copy(isActive = active)
                            config = updated
                            repository.saveDailyCampaignConfig(updated)
                            if (active) {
                                DailyCampaignScheduler.scheduleOrUpdate(context, updated.id)
                            } else {
                                DailyCampaignScheduler.cancel(context, updated.id)
                                com.jmd.autotext.service.AutoTextAccessibilityService.clearPending()
                            }
                            loadAllCampaigns()
                            context.showAppToast(if (active) "Campaign Activated" else "Campaign Paused: Execution stopped")
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = campaignThemeColor,
                            uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        )
                    )
                }
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp, vertical = 4.dp),
                contentPadding = PaddingValues(
                    bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 24.dp
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {

            // Channel-Specific Safety / Quota / Readiness Cards
            if (!isWhatsApp) {
                item {
                    dailySmsQuotaCard(smsQuota = smsQuota)
                }
            } else if (isWhatsAppBusiness) {
                val isW4bInstalled = WhatsAppPackageHelper.isWhatsAppBusinessInstalled(context)
                if (!isW4bInstalled || !isA11yActive) {
                    item {
                        whatsAppBusinessReadinessCard(
                            isInstalled = isW4bInstalled,
                            isA11yActive = isA11yActive,
                            onAutoHeal = {
                                scope.launch {
                                    val alive = withContext(Dispatchers.IO) {
                                        AccessibilityHealthManager.ensureServiceAlive(context)
                                    }
                                    isA11yActive = alive
                                    context.showAppToast(if (alive) "Service rebound & active" else "Heal triggered. Awaiting binder...")
                                }
                            }
                        )
                    }
                }
            } else {
                val isWaInstalled = WhatsAppPackageHelper.isWhatsAppInstalled(context)
                if (!isWaInstalled || !isA11yActive) {
                    item {
                        whatsAppA11yHealthBar(
                            isA11yActive = isA11yActive,
                            onAutoHeal = {
                                scope.launch {
                                    val alive = withContext(Dispatchers.IO) {
                                        AccessibilityHealthManager.ensureServiceAlive(context)
                                    }
                                    isA11yActive = alive
                                    context.showAppToast(if (alive) "Service rebound & active" else "Heal triggered. Awaiting binder...")
                                }
                            }
                        )
                    }
                }
            }

            // Top Status Enterprise Banner for this campaign
            item {
                dailyCampaignBannerCard(
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
                        if (active) {
                            DailyCampaignScheduler.scheduleOrUpdate(context, updated.id)
                        } else {
                            DailyCampaignScheduler.cancel(context, updated.id)
                            com.jmd.autotext.service.AutoTextAccessibilityService.clearPending()
                        }
                        loadAllCampaigns()
                        context.showAppToast(if (active) "Campaign Activated" else "Campaign Paused: Execution stopped")
                    },
                    onAutoHeal = {
                        scope.launch {
                            val alive = withContext(Dispatchers.IO) {
                                AccessibilityHealthManager.ensureServiceAlive(context)
                            }
                            isA11yActive = alive
                            context.showAppToast(if (alive) "Service rebound & active" else "Heal triggered. Awaiting binder...")
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
                                loadAllCampaigns()
                                isScanning = false
                                context.showAppToast("Ingested ${summary.newAdded} contacts", isLong = true)
                            }
                        }
                    },
                    onSendBatch = {
                        if (!isWhatsApp && smsQuota.isLimitReached) {
                            context.showAppToast("Daily SMS limit reached", isLong = true)
                            return@dailyCampaignBannerCard
                        }
                        DailyCampaignScheduler.triggerNow(context, selectedCampaignId)
                        context.showAppToast("Batch dispatched (${config.dailyLimit})")
                        scope.launch {
                            delay(2.seconds)
                            smsQuota = SmsQuotaManager.getQuotaStatus(context)
                            refreshData()
                        }
                    }
                )
            }

            // Type-Specific Campaign Configuration Enterprise Card
            item {
                dailyCampaignConfigCard(
                    config = config,
                    isWhatsApp = isWhatsApp,
                    campaignThemeColor = campaignThemeColor,
                    onConfigChange = { config = it },
                    onSave = { updated ->
                        repository.saveDailyCampaignConfig(updated)
                        DailyCampaignScheduler.scheduleOrUpdate(context, updated.id)
                        loadAllCampaigns()
                        context.showAppToast("${if (isWhatsApp) "WhatsApp" else "SMS"} campaign settings saved successfully")
                    }
                )
            }

            // Contact Pool & Sent Ledger Tab Selector
            item {
                glassSegmentedControl(
                    items = listOf("Pending Unique Pool (${stats.unsentCount})", "Sent Ledger (${stats.sentCount})"),
                    selectedIndex = poolTab,
                    onItemSelected = { poolTab = it },
                    activeColor = if (poolTab == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
                )
            }

            if (poolTab == 0) {
                item {
                    dailyContactPoolHeaderCard(
                        stats = stats,
                        searchQuery = searchQuery,
                        onSearchQueryChange = { searchQuery = it },
                        filterUnsentOnly = filterUnsentOnly,
                        onFilterUnsentOnlyChange = { filterUnsentOnly = it },
                        onClearPool = {
                            repository.clearDynamicPool()
                            refreshData()
                            context.showAppToast("Contact pool cleared")
                        }
                    )
                }

                if (contactsList.isEmpty()) {
                    item {
                        appEmptyState(
                            icon = Icons.Default.Contacts,
                            title = "No Pending Contacts",
                            description = "All contacts in the unique pool have already received this campaign cycle, or the pool is empty. Tap 'Scan' above to discover new contacts or reset the cycle in Sent Ledger."
                        )
                    }
                } else {
                    items(contactsList, key = { it.id }) { contact ->
                        contactItemCard(
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
                    dailySentLedgerHeaderCard(
                        isWhatsApp = isWhatsApp,
                        sentCount = sentLedgerList.size,
                        searchQuery = searchQuery,
                        onSearchQueryChange = { searchQuery = it },
                        campaignThemeColor = campaignThemeColor,
                        onResetCycle = {
                            repository.resetCampaignLedger(selectedCampaignId)
                            refreshData()
                            context.showAppToast("${config.title} cycle reset successfully!")
                        }
                    )
                }

                if (sentLedgerList.isEmpty()) {
                    item {
                        appEmptyState(
                            icon = Icons.AutoMirrored.Filled.FactCheck,
                            title = "Sent Ledger is Empty",
                            description = "No messages have been sent yet for this campaign in this cycle. Send your first batch above to build your ledger."
                        )
                    }
                } else {
                    items(sentLedgerList, key = { "ledger_${it.id}" }) { contact ->
                        ledgerItemCard(
                            contact = contact,
                            isWhatsApp = isWhatsApp,
                            onRequeue = {
                                repository.requeueContactForCampaign(selectedCampaignId, contact.id)
                                refreshData()
                                context.showAppToast("${contact.name.ifEmpty { contact.phone }} re-queued for next dispatch")
                            }
                        )
                    }
                }
            }
        }
    }
}

    if (showSelectChannelDialog) {
        dailyCampaignChannelSelectionDialog(
            onDismiss = { showSelectChannelDialog = false },
            onSelectChannel = { channelType ->
                showSelectChannelDialog = false
                createChannelType = channelType
                showCreateCampaignDialog = true
            }
        )
    }

    if (showCreateCampaignDialog) {
        createDailyCampaignSheet(
            initialChannel = createChannelType,
            onDismiss = { showCreateCampaignDialog = false },
            onSave = { newCampaign ->
                val insertedId = repository.insertDailyCampaignConfig(newCampaign)
                if (insertedId > 0) {
                    selectedCampaignId = insertedId
                    loadAllCampaigns()
                    if (newCampaign.isActive) {
                        DailyCampaignScheduler.scheduleOrUpdate(context, insertedId)
                    }
                    context.showAppToast("Daily Campaign Created!")
                }
                showCreateCampaignDialog = false
            }
        )
    }
}


/**
 * Primary overview card for an active recurring campaign displaying current execution state,
 * today's dispatch progress, dynamic contact pool statistics, and instant trigger actions.
 *
 * @param campaign Target campaign configuration data class.
 * @param sentToday Number of messages successfully dispatched during the current calendar day.
 * @param stats Real-time contact pool inventory metrics (unmessaged, messaged, exhausted).
 * @param onToggleActive Callback triggered when the campaign enable/disable switch is flipped.
 * @param onRunNow Callback triggered when user requests immediate manual campaign execution.
 * @param onConfigure Callback to navigate to campaign parameter configuration.
 * @param onViewContacts Callback to navigate to contact pool management view.
 * @param modifier Optional layout modifier.
 */
