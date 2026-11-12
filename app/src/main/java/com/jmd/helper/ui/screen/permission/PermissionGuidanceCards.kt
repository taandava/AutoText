/**
 * Package: com.jmd.helper.ui.screen.permission
 * Module: Application Core Subsystem
 *
 * What it is:
 * Architecture definition and implementation for [PermissionGuidanceCards].
 *
 * What it does:
 * Provides core logic, component interfaces, and subsystem bindings for PermissionGuidanceCards.
 *
 * Linked With:
 * - UI Screens & Components: [androidx.compose.ui.Alignment], [androidx.compose.ui.Modifier], [androidx.compose.ui.draw.scale], [androidx.compose.ui.platform.LocalContext]
 * - Core Security & Utilities: [androidx.core.net.toUri], [com.jmd.helper.core.AccessibilityHealthManager], [com.jmd.helper.core.OemBrandHelper], [com.jmd.helper.core.OverlayPermissionHelper]
 * - Android Framework & Jetpack: [android.provider.Settings], [androidx.compose.animation.AnimatedVisibility], [androidx.compose.animation.expandVertically], [androidx.compose.animation.fadeIn]
 */

package com.jmd.helper.ui.screen.permission

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.jmd.helper.core.AccessibilityHealthManager
import com.jmd.helper.core.OemBrandHelper
import com.jmd.helper.core.OverlayPermissionHelper
import com.jmd.helper.core.showAppToast
import com.jmd.helper.ui.components.*
import com.jmd.helper.ui.theme.AppLayoutGrid
import com.jmd.helper.ui.theme.AppSemanticColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * What it is:
 * OEM background autostart & freeze prevention guidance card tailored to current device hardware.
 *
 * What it does:
 * Renders interactive Material 3 UI for [OemAutostartCard] adhering to application design invariants.
 *
 * Linked with:
 * - Architecture: [PermissionGuidanceCards], [OemAutostartCard]
 */
@Composable
fun OemAutostartCard(
    modifier: Modifier = Modifier,
    context: Context = LocalContext.current
) {
    val brandSummary = remember { OemBrandHelper.getDeviceSummary() }
    val brandGuidance = remember { OemBrandHelper.getOemGuidance() }
    var isExpanded by remember { mutableStateOf(false) }

    AppGroupCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { isExpanded = !isExpanded }
                .padding(horizontal = AppLayoutGrid.SpaceMedium, vertical = AppLayoutGrid.SpaceStandard)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = AppLayoutGrid.MinTouchTarget),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(AppLayoutGrid.SquircleCornerRadius)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Smartphone,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(AppLayoutGrid.SpaceStandard))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "OEM Autostart ($brandSummary)",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(AppLayoutGrid.SpaceMicro))
                    Text(
                        text = "Hardware settings to prevent background kills",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier
                        .size(20.dp)
                        .scale(1f, if (isExpanded) -1f else 1f)
                )
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = AppLayoutGrid.SpaceSmall, start = 52.dp),
                    verticalArrangement = Arrangement.spacedBy(AppLayoutGrid.SpaceXS)
                ) {
                    brandGuidance.forEach { step ->
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "• ",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = step,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 16.sp
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(AppLayoutGrid.SpaceSmall))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(AppLayoutGrid.SpaceSmall)
                    ) {
                        GlassButton(
                            text = "Open Auto-Start",
                            onClick = {
                                if (!OemBrandHelper.openOemBackgroundSettings(context)) {
                                    context.showAppToast("Unable to open OEM autostart settings directly")
                                }
                            },
                            icon = Icons.Default.Settings,
                            variant = GlassButtonVariant.PRIMARY,
                            modifier = Modifier.weight(1f)
                        )
                        GlassButton(
                            text = "Special Overlays",
                            onClick = {
                                if (!OverlayPermissionHelper.launchOverlaySettings(context)) {
                                    context.showAppToast("Unable to open overlay settings directly")
                                }
                            },
                            icon = Icons.Default.Layers,
                            variant = GlassButtonVariant.SECONDARY,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

/**
 * What it is:
 * Android 13+ restricted settings assistance card.
 *
 * What it does:
 * Renders interactive Material 3 UI for [SideloadedAppHelpCard] adhering to application design invariants.
 *
 * Linked with:
 * - Architecture: [PermissionGuidanceCards], [SideloadedAppHelpCard]
 */
@Composable
fun SideloadedAppHelpCard(
    modifier: Modifier = Modifier,
    context: Context = LocalContext.current
) {
    AppGroupCard(modifier = modifier.fillMaxWidth()) {
        AppGroupRow(
            title = "Sideloaded App Guidance (Android 13+)",
            subtitle = "If settings are greyed out: App Info -> 3 dots -> Allow restricted settings.",
            icon = Icons.Default.Security,
            iconTint = MaterialTheme.colorScheme.primary,
            iconContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
            trailingContent = {
                PermissionGrantActionButton(
                    text = "App Info",
                    onClick = {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = "package:${context.packageName}".toUri()
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        context.startActivity(intent)
                    }
                )
            },
            detailedPurpose = "On Android 13+, sideloaded APKs have accessibility and notification permissions restricted by default. Tapping the three dots in App Info unlocks full access.",
            onSettingsClick = {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = "package:${context.packageName}".toUri()
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            }
        )
    }
}

/**
 * What it is:
 * Watchdog & Self-Healing Service Trigger Card.
 *
 * What it does:
 * Renders interactive Material 3 UI for [AutoHealWatchdogCard] adhering to application design invariants.
 *
 * Linked with:
 * - Architecture: [PermissionGuidanceCards], [AutoHealWatchdogCard]
 */
@Composable
fun AutoHealWatchdogCard(
    modifier: Modifier = Modifier,
    context: Context = LocalContext.current,
    onHealDone: () -> Unit = {}
) {
    var isHealing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    AppGroupCard(modifier = modifier.fillMaxWidth()) {
        AppGroupRow(
            title = "Auto-Heal Services & Watchdog",
            subtitle = "Restores and binds any dropped background services or alarms.",
            icon = Icons.Default.Sync,
            iconTint = AppSemanticColors.success,
            iconContainerColor = AppSemanticColors.successContainer,
            trailingContent = {
                GlassButton(
                    text = if (isHealing) "Healing..." else "Auto-Heal",
                    onClick = {
                        if (isHealing) return@GlassButton
                        isHealing = true
                        scope.launch {
                            val healed = withContext(Dispatchers.IO) {
                                AccessibilityHealthManager.ensureAllServicesActive(context)
                                AccessibilityHealthManager.ensureServiceAlive(context)
                            }
                            isHealing = false
                            onHealDone()
                            if (healed) {
                                context.showAppToast("Services verified and operational")
                            } else {
                                context.showAppToast("Auto-heal executed. Review individual items in Permission Center.")
                            }
                        }
                    },
                    icon = if (isHealing) Icons.Default.HourglassTop else Icons.Default.Sync,
                    variant = GlassButtonVariant.SUCCESS
                )
            }
        )
    }
}
