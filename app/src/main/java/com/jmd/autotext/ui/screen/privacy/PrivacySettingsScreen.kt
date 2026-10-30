/**
 * Package: com.jmd.autotext.ui.screen.privacy
 * Module: UI Privacy Layer
 *
 * Full-screen interface providing comprehensive privacy preferences, application-specific
 * exclusions, accessibility target scoping, content masking, and installed application selector.
 */
package com.jmd.autotext.ui.screen.privacy

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.core.graphics.createBitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jmd.autotext.core.AccessibilityHealthManager
import com.jmd.autotext.core.PrivacyManager
import com.jmd.autotext.core.showAppToast
import com.jmd.autotext.data.model.AppInfoItem
import com.jmd.autotext.data.model.AppPrivacyConfig
import com.jmd.autotext.data.prefs.PreferenceManager
import com.jmd.autotext.service.AutoTextAccessibilityService
import com.jmd.autotext.ui.components.*
import com.jmd.autotext.ui.theme.AppDesignTokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private enum class AppListFilter {
    ALL,
    SHIELDED,
    FINANCIAL
}

@Composable
fun PrivacySettingsScreen(
    context: Context,
    prefs: PreferenceManager,
    onBack: () -> Unit
) {
    val privacyMgr = remember { PrivacyManager.getInstance(context) }

    // Global settings state
    var privacyModeEnabled by remember { mutableStateOf(prefs.isPrivacyModeEnabled) }
    var autoExcludeBanking by remember { mutableStateOf(prefs.isAutoExcludeBankingEnabled) }
    var smartA11yScoping by remember { mutableStateOf(prefs.isSmartAccessibilityScopingEnabled) }
    var dynamicA11yOnDemand by remember { mutableStateOf(prefs.isDynamicA11yOnDemandEnabled) }
    var maskLockScreenNotifs by remember { mutableStateOf(prefs.isMaskNotificationsOnLockScreenEnabled) }
    var maskExecutionLogs by remember { mutableStateOf(prefs.isMaskExecutionLogsEnabled) }
    var autoClearClipboardSec by remember { mutableIntStateOf(prefs.autoClearClipboardSeconds) }
    var suppressNotifsOverExcluded by remember { mutableStateOf(prefs.isSuppressNotifsOverExcludedEnabled) }
    var blockOverlaysOverExcluded by remember { mutableStateOf(prefs.isBlockOverlaysOverExcludedEnabled) }

    // Installed apps and filter state
    var installedApps by remember { mutableStateOf<List<AppInfoItem>>(emptyList()) }
    var isLoadingApps by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }
    var currentFilter by remember { mutableStateOf(AppListFilter.ALL) }
    var selectedAppForDetail by remember { mutableStateOf<AppInfoItem?>(null) }
    var showResetDialog by remember { mutableStateOf(false) }

    val hasWriteSecure = remember { AccessibilityHealthManager.hasWriteSecureSettings(context) }

    // Load installed applications asynchronously on IO dispatcher
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val intent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val resolveInfos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.queryIntentActivities(intent, 0)
            }

            val appMap = mutableMapOf<String, AppInfoItem>()
            for (ri in resolveInfos) {
                val pkg = ri.activityInfo.packageName
                if (pkg == context.packageName) continue
                val name = ri.loadLabel(pm).toString()
                val isSystem = (ri.activityInfo.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                val isBanking = AutoTextAccessibilityService.isFinancialOrBankingPackage(pkg)
                val isExcluded = privacyMgr.isAppExcluded(pkg)
                val config = privacyMgr.getConfigForPackage(pkg)
                appMap[pkg] = AppInfoItem(
                    packageName = pkg,
                    appName = name,
                    isSystemApp = isSystem,
                    isBankingOrFinancial = isBanking,
                    isExcluded = isExcluded,
                    config = config
                )
            }

            installedApps = appMap.values.sortedWith(
                compareByDescending<AppInfoItem> { it.isExcluded }
                    .thenByDescending { it.isBankingOrFinancial }
                    .thenBy { it.appName.lowercase() }
            )
            isLoadingApps = false
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            appSubScreenHeader(
                title = "Privacy & App Visibility",
                subtitle = "App exclusions, content masking & stealth shielding",
                onBack = onBack
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        val filteredApps = remember(installedApps, searchQuery, currentFilter) {
            installedApps.filter { app ->
                val matchesQuery = searchQuery.isBlank() ||
                    app.appName.contains(searchQuery, ignoreCase = true) ||
                    app.packageName.contains(searchQuery, ignoreCase = true)

                val matchesFilter = when (currentFilter) {
                    AppListFilter.ALL -> true
                    AppListFilter.SHIELDED -> app.isExcluded
                    AppListFilter.FINANCIAL -> app.isBankingOrFinancial
                }
                matchesQuery && matchesFilter
            }
        }

        val totalExcludedCount = remember(installedApps) { installedApps.count { it.isExcluded } }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(
                top = 10.dp,
                bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 28.dp
            )
        ) {
            // ==================== SECTION 1: MASTER PRIVACY CONTROLS ====================
            item {
                appCard(
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = MaterialTheme.colorScheme.surface,
                    borderColor = if (privacyModeEnabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = "🛡️", fontSize = 18.sp, modifier = Modifier.padding(end = 8.dp))
                            Column {
                                Text(
                                    text = "Privacy & App Shielding",
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                                Text(
                                    text = if (privacyModeEnabled) "$totalExcludedCount Applications Shielded" else "Privacy Restrictions Paused",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp
                                )
                            }
                        }
                        appBadge(
                            text = if (privacyModeEnabled) "SHIELD ACTIVE" else "DISABLED",
                            color = if (privacyModeEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            showDot = true
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(text = "Master Privacy Mode", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                            Text(text = "Enforces application exclusions, stealth shielding, and accessibility isolation.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = privacyModeEnabled,
                            onCheckedChange = { enable ->
                                privacyModeEnabled = enable
                                prefs.isPrivacyModeEnabled = enable
                                privacyMgr.updateAccessibilityScoping()
                                context.showAppToast(if (enable) "Privacy Mode Enabled" else "Privacy Mode Disabled")
                            }
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    // Auto-Exclude Banking
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(text = "Auto-Shield Banking & UPI Apps", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                            Text(text = "Automatically isolates SBI, HDFC, ICICI, PhonePe, Google Pay, Paytm, Saraswat Bank & financial apps.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = autoExcludeBanking,
                            enabled = privacyModeEnabled,
                            onCheckedChange = { enable ->
                                autoExcludeBanking = enable
                                prefs.isAutoExcludeBankingEnabled = enable
                                privacyMgr.updateAccessibilityScoping()
                            }
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    // Smart Accessibility Scoping
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(text = "Smart Accessibility Scoping", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                            Text(text = "Restricts AccessibilityServiceInfo.packageNames to only target apps. Excluded & banking apps are never observed by Android accessibility.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = smartA11yScoping,
                            enabled = privacyModeEnabled,
                            onCheckedChange = { enable ->
                                smartA11yScoping = enable
                                prefs.isSmartAccessibilityScopingEnabled = enable
                                privacyMgr.updateAccessibilityScoping()
                                context.showAppToast(if (enable) "Accessibility Scoped to Target Apps" else "Accessibility Listening to All Packages")
                            }
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    // Dynamic On-Demand Accessibility Mode
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(text = "Dynamic On-Demand Accessibility", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                                Spacer(modifier = Modifier.width(6.dp))
                                if (hasWriteSecure) {
                                    appBadge(text = "PRIVILEGED", color = MaterialTheme.colorScheme.primary)
                                } else {
                                    appBadge(text = "NEEDS SECURE_SETTINGS", color = MaterialTheme.colorScheme.outline)
                                }
                            }
                            Text(text = "Keeps accessibility completely OFF when idle. Automatically enables service only during active dispatches for 3s-5s, then disables it. Banking apps will NEVER detect accessibility.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = dynamicA11yOnDemand,
                            enabled = privacyModeEnabled && hasWriteSecure,
                            onCheckedChange = { enable ->
                                dynamicA11yOnDemand = enable
                                prefs.isDynamicA11yOnDemandEnabled = enable
                                if (enable) {
                                    AccessibilityHealthManager.disableAccessibilityService(context)
                                    context.showAppToast("Dynamic On-Demand Mode Active: Accessibility Service Disabled when Idle")
                                } else {
                                    AccessibilityHealthManager.enableAccessibilityService(context)
                                    context.showAppToast("Continuous Accessibility Mode Active")
                                }
                            }
                        )
                    }
                }
            }

            // ==================== SECTION 2: NOTIFICATIONS & CONTENT PRIVACY ====================
            item {
                appCard(
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = MaterialTheme.colorScheme.surface
                ) {
                    Text(text = "Content Masking & System Protection", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(modifier = Modifier.height(8.dp))

                    // Mask on lockscreen
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(text = "Mask Content on Lockscreen", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                            Text(text = "Replaces message text with '[AutoText] Automation Active' on lockscreen.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = maskLockScreenNotifs,
                            onCheckedChange = {
                                maskLockScreenNotifs = it
                                prefs.isMaskNotificationsOnLockScreenEnabled = it
                            }
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    // Mask execution logs
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(text = "Mask Execution History Logs", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                            Text(text = "Replaces message bodies and phone numbers with '***' in execution history.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = maskExecutionLogs,
                            onCheckedChange = {
                                maskExecutionLogs = it
                                prefs.isMaskExecutionLogsEnabled = it
                            }
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    // Suppress notifs over excluded apps
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(text = "Suppress Alerts Over Excluded Apps", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                            Text(text = "Mutes non-critical notification popups when an excluded app is foregrounded.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = suppressNotifsOverExcluded,
                            onCheckedChange = {
                                suppressNotifsOverExcluded = it
                                prefs.isSuppressNotifsOverExcludedEnabled = it
                            }
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    // Block overlays over excluded apps
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(text = "Block Overlays Over Excluded Apps", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                            Text(text = "Prevents floating popups, wake-windows, or fake calls while an excluded app is open.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = blockOverlaysOverExcluded,
                            onCheckedChange = {
                                blockOverlaysOverExcluded = it
                                prefs.isBlockOverlaysOverExcludedEnabled = it
                            }
                        )
                    }
                }
            }

            // ==================== SECTION 3: APPLICATION SELECTOR ====================
            item {
                appCard(
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = MaterialTheme.colorScheme.surface
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(text = "Installed Applications", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                            Text(text = "Select apps to isolate, block overlays, or customize rules", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(text = "${filteredApps.size} apps", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Search Field
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Search by name or package...", fontSize = 12.sp) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", modifier = Modifier.size(18.dp)) },
                        trailingIcon = {
                            if (searchQuery.isNotBlank()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Filter Chips & Multi-Select Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            glassChip(
                                selected = currentFilter == AppListFilter.ALL,
                                onClick = { currentFilter = AppListFilter.ALL },
                                label = "All (${installedApps.size})"
                            )
                            glassChip(
                                selected = currentFilter == AppListFilter.SHIELDED,
                                onClick = { currentFilter = AppListFilter.SHIELDED },
                                label = "Shielded ($totalExcludedCount)"
                            )
                            glassChip(
                                selected = currentFilter == AppListFilter.FINANCIAL,
                                onClick = { currentFilter = AppListFilter.FINANCIAL },
                                label = "Financial"
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(
                                onClick = {
                                    val updated = installedApps.map { app ->
                                        val newConfig = (app.config ?: AppPrivacyConfig(
                                            packageName = app.packageName,
                                            appName = app.appName
                                        )).copy(isExcluded = true)
                                        privacyMgr.saveConfig(newConfig)
                                        app.copy(isExcluded = true, config = newConfig)
                                    }
                                    installedApps = updated
                                    context.showAppToast("All ${installedApps.size} applications shielded")
                                },
                                contentPadding = PaddingValues(horizontal = 6.dp)
                            ) {
                                Text("Select All", fontSize = 11.sp)
                            }
                            TextButton(
                                onClick = {
                                    val updated = installedApps.map { app ->
                                        val newConfig = (app.config ?: AppPrivacyConfig(
                                            packageName = app.packageName,
                                            appName = app.appName
                                        )).copy(isExcluded = false)
                                        privacyMgr.saveConfig(newConfig)
                                        app.copy(isExcluded = false, config = newConfig)
                                    }
                                    installedApps = updated
                                    context.showAppToast("All exclusions cleared")
                                },
                                contentPadding = PaddingValues(horizontal = 6.dp)
                            ) {
                                Text("Clear", fontSize = 11.sp)
                            }
                        }
                    }
                }
            }

            // Application List Rows
            if (isLoadingApps) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    }
                }
            } else if (filteredApps.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text(text = "No applications match the search filter", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                items(filteredApps, key = { it.packageName }) { appItem ->
                    AppRowItem(
                        context = context,
                        appItem = appItem,
                        onToggleExclusion = { excluded ->
                            val currentConfig = appItem.config ?: AppPrivacyConfig(
                                packageName = appItem.packageName,
                                appName = appItem.appName
                            )
                            val updatedConfig = currentConfig.copy(
                                isExcluded = excluded,
                                updatedAt = System.currentTimeMillis()
                            )
                            privacyMgr.saveConfig(updatedConfig)

                            installedApps = installedApps.map {
                                if (it.packageName == appItem.packageName) {
                                    it.copy(isExcluded = excluded, config = updatedConfig)
                                } else it
                            }
                        },
                        onOpenDetails = {
                            selectedAppForDetail = appItem
                        }
                    )
                }
            }

            // ==================== SECTION 4: RESET DEFAULTS ====================
            item {
                Spacer(modifier = Modifier.height(6.dp))
                glassButton(
                    text = "Reset All Privacy Settings to Factory Defaults",
                    variant = GlassButtonVariant.SECONDARY,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { showResetDialog = true }
                )
            }
        }
    }

    // Detail Dialog Sheet for Individual App
    selectedAppForDetail?.let { appInfo ->
        val existingConfig = appInfo.config ?: AppPrivacyConfig(
            packageName = appInfo.packageName,
            appName = appInfo.appName,
            isExcluded = appInfo.isExcluded
        )
        AppPrivacyDetailDialog(
            appInfo = appInfo,
            initialConfig = existingConfig,
            onSave = { updated ->
                privacyMgr.saveConfig(updated)
                installedApps = installedApps.map {
                    if (it.packageName == appInfo.packageName) {
                        it.copy(isExcluded = updated.isExcluded, config = updated)
                    } else it
                }
                selectedAppForDetail = null
                context.showAppToast("Updated settings for ${appInfo.appName}")
            },
            onDismiss = { selectedAppForDetail = null }
        )
    }

    // Reset Confirmation Dialog
    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Reset Privacy Settings?", fontWeight = FontWeight.Bold) },
            text = { Text("This will restore default banking shields and clear custom exclusions.", fontSize = 12.sp) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showResetDialog = false
                        privacyMgr.resetAllSettings()
                        privacyModeEnabled = prefs.isPrivacyModeEnabled
                        autoExcludeBanking = prefs.isAutoExcludeBankingEnabled
                        smartA11yScoping = prefs.isSmartAccessibilityScopingEnabled
                        dynamicA11yOnDemand = prefs.isDynamicA11yOnDemandEnabled
                        maskLockScreenNotifs = prefs.isMaskNotificationsOnLockScreenEnabled
                        maskExecutionLogs = prefs.isMaskExecutionLogsEnabled
                        context.showAppToast("Privacy configuration reset to secure defaults")
                    }
                ) {
                    Text("Reset", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun AppRowItem(
    context: Context,
    appItem: AppInfoItem,
    onToggleExclusion: (Boolean) -> Unit,
    onOpenDetails: () -> Unit
) {
    var iconBitmap by remember(appItem.packageName) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(appItem.packageName) {
        withContext(Dispatchers.IO) {
            try {
                val drawable = context.packageManager.getApplicationIcon(appItem.packageName)
                iconBitmap = drawableToBitmap(drawable)
            } catch (_: Throwable) {}
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenDetails),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(10.dp),
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // App Icon
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (iconBitmap != null) {
                    Image(
                        bitmap = iconBitmap!!.asImageBitmap(),
                        contentDescription = appItem.appName,
                        modifier = Modifier.size(32.dp)
                    )
                } else {
                    Text(
                        text = appItem.appName.take(1).uppercase(),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 16.sp
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            // App Label & Package
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = appItem.appName,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    if (appItem.isBankingOrFinancial) {
                        appBadge(text = "BANKING", color = MaterialTheme.colorScheme.error)
                    }
                }
                Text(
                    text = appItem.packageName,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            // Exclusion Toggle
            Switch(
                checked = appItem.isExcluded,
                onCheckedChange = onToggleExclusion,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.primary,
                    checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                )
            )
        }
    }
}

private fun drawableToBitmap(drawable: Drawable): Bitmap {
    if (drawable is BitmapDrawable && drawable.bitmap != null) {
        return drawable.bitmap
    }
    val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 48
    val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 48
    val bitmap = createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    drawable.setBounds(0, 0, canvas.width, canvas.height)
    drawable.draw(canvas)
    return bitmap
}
