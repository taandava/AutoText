/**
 * Package: com.jmd.helper.ui.screen.permission
 * Module: UI Permission Center - Executive Hero System Health Card
 */
package com.jmd.helper.ui.screen.permission

import android.content.Context
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.SecurityUpdateWarning
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jmd.helper.core.AccessibilityHealthManager
import com.jmd.helper.core.showAppToast
import com.jmd.helper.ui.components.GlassButton
import com.jmd.helper.ui.components.GlassButtonVariant
import com.jmd.helper.ui.theme.AppDesignTokens
import com.jmd.helper.ui.theme.AppLayoutGrid
import com.jmd.helper.ui.theme.AppSemanticColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Executive Material 3 Hero Card presenting real-time system privilege readiness,
 * animated circular health gauge, subsystem status pill indicators, and self-healing controls.
 */
@Composable
fun PermissionHeroCard(
    context: Context,
    grantedCount: Int,
    totalCount: Int = 8,
    privilegeStates: Map<AppPrivilege, Boolean>,
    onOpenAdbDialog: () -> Unit,
    onHealDone: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var isHealing by remember { mutableStateOf(false) }
    val isAllGranted = grantedCount >= totalCount

    val healthPercentage = (grantedCount.toFloat() / totalCount.toFloat()).coerceIn(0f, 1f)
    val animatedProgress by animateFloatAsState(targetValue = healthPercentage, label = "HealthProgress")

    val successAccent = AppSemanticColors.success
    val primaryAccent = MaterialTheme.colorScheme.primary

    val corePrivileges = remember { AppPrivilege.entries.filter { it.category == PrivilegeCategory.CORE } }
    val systemPrivileges = remember { AppPrivilege.entries.filter { it.category == PrivilegeCategory.SYSTEM } }
    val commPrivileges = remember { AppPrivilege.entries.filter { it.category == PrivilegeCategory.COMMUNICATION } }

    val coreCount = corePrivileges.count { privilegeStates[it] == true }
    val systemCount = systemPrivileges.count { privilegeStates[it] == true }
    val commCount = commPrivileges.count { privilegeStates[it] == true }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(AppLayoutGrid.CardCornerRadius),
        colors = CardDefaults.cardColors(
            containerColor = AppSemanticColors.heroContainer,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        border = BorderStroke(
            AppLayoutGrid.BorderWidthStandard,
            if (isAllGranted) AppSemanticColors.successBorder else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppLayoutGrid.SpaceMedium)
        ) {
            // Header Row: Status icon + title + circular gauge
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .background(
                                color = if (isAllGranted) AppSemanticColors.successContainer else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(AppLayoutGrid.SquircleCornerRadius)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isAllGranted) Icons.Default.Shield else Icons.Default.SecurityUpdateWarning,
                            contentDescription = null,
                            tint = if (isAllGranted) successAccent else primaryAccent,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(AppLayoutGrid.SpaceStandard))

                    Column {
                        Text(
                            text = if (isAllGranted) "All Systems Operational" else "Privilege Setup Required",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(AppLayoutGrid.SpaceMicro))
                        Text(
                            text = if (isAllGranted) {
                                "All $totalCount system privileges active & armed"
                            } else {
                                "${totalCount - grantedCount} privilege(s) require authorization"
                            },
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.width(AppLayoutGrid.SpaceSmall))

                // Elevated Circular Health Gauge
                Box(
                    modifier = Modifier.size(56.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier.fillMaxSize(),
                        color = if (isAllGranted) successAccent else primaryAccent,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        strokeWidth = 4.5.dp
                    )
                    Text(
                        text = "${(healthPercentage * 100).toInt()}%",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (isAllGranted) successAccent else primaryAccent
                    )
                }
            }

            Spacer(modifier = Modifier.height(AppLayoutGrid.SpaceMedium))

            // Subsystem Health Status Chips Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AppLayoutGrid.SpaceSmall)
            ) {
                SubsystemStatusChip(
                    title = "Automation",
                    count = coreCount,
                    total = corePrivileges.size,
                    modifier = Modifier.weight(1f)
                )
                SubsystemStatusChip(
                    title = "System",
                    count = systemCount,
                    total = systemPrivileges.size,
                    modifier = Modifier.weight(1f)
                )
                SubsystemStatusChip(
                    title = "Comms",
                    count = commCount,
                    total = commPrivileges.size,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(AppLayoutGrid.SpaceMedium))

            // Action Buttons with guaranteed 48dp touch targets
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AppLayoutGrid.SpaceSmall)
            ) {
                GlassButton(
                    text = if (isHealing) "Healing..." else "Auto-Heal Services",
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
                                context.showAppToast("Auto-heal executed. Review individual items below.", isLong = true)
                            }
                        }
                    },
                    icon = if (isHealing) Icons.Default.HourglassTop else Icons.Default.Sync,
                    variant = GlassButtonVariant.SUCCESS,
                    modifier = Modifier.weight(1.15f)
                )

                GlassButton(
                    text = "ADB Setup",
                    onClick = onOpenAdbDialog,
                    icon = Icons.Default.Terminal,
                    variant = GlassButtonVariant.SECONDARY,
                    modifier = Modifier.weight(0.85f)
                )
            }
        }
    }
}

/**
 * At-a-glance status chip representing a privilege subsystem.
 */
@Composable
private fun SubsystemStatusChip(
    title: String,
    count: Int,
    total: Int,
    modifier: Modifier = Modifier
) {
    val isComplete = count >= total
    val accent = if (isComplete) AppSemanticColors.success else MaterialTheme.colorScheme.onSurfaceVariant
    val containerBg = if (isComplete) AppSemanticColors.successContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)

    Surface(
        shape = AppDesignTokens.ShapePill,
        color = containerBg,
        border = BorderStroke(
            AppLayoutGrid.BorderWidthStandard,
            if (isComplete) AppSemanticColors.successBorder else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
        ),
        modifier = modifier.defaultMinSize(minHeight = 36.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (isComplete) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
            Text(
                text = "$title $count/$total",
                fontSize = 11.5.sp,
                fontWeight = if (isComplete) FontWeight.Bold else FontWeight.Medium,
                color = accent,
                maxLines = 1
            )
        }
    }
}
