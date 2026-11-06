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
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.PermContactCalendar
import androidx.compose.material.icons.filled.SecurityUpdateWarning
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
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
 * Executive System Health Card presenting real-time system privilege readiness,
 * dynamic completion gauge, 3-pillar architectural summary, and self-healing action controls.
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
            // Header: Shield Icon, Headline Status & Circular Metric Badge
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
                            .size(46.dp)
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
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(AppLayoutGrid.SpaceStandard))

                    Column {
                        Text(
                            text = if (isAllGranted) "All Systems Operational" else "System Health Check",
                            fontSize = 15.sp,
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
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Progress Percentage Circular Badge
                Surface(
                    shape = CircleShape,
                    color = if (isAllGranted) AppSemanticColors.successContainer else primaryAccent.copy(alpha = 0.12f),
                    border = BorderStroke(1.5.dp, if (isAllGranted) successAccent else primaryAccent),
                    modifier = Modifier.size(46.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "${(healthPercentage * 100).toInt()}%",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (isAllGranted) successAccent else primaryAccent
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(AppLayoutGrid.SpaceStandard))

            // Smooth Animated Progress Bar
            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = if (isAllGranted) successAccent else primaryAccent,
                trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
            )

            Spacer(modifier = Modifier.height(AppLayoutGrid.SpaceMedium))

            // 3-Pillar Executive Architectural Status Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AppLayoutGrid.SpaceSmall)
            ) {
                CategoryPillarItem(
                    title = "Core Automation",
                    count = coreCount,
                    total = corePrivileges.size,
                    icon = Icons.Default.Bolt,
                    modifier = Modifier.weight(1f)
                )
                CategoryPillarItem(
                    title = "System & Power",
                    count = systemCount,
                    total = systemPrivileges.size,
                    icon = Icons.Default.Tune,
                    modifier = Modifier.weight(1f)
                )
                CategoryPillarItem(
                    title = "Device & Comms",
                    count = commCount,
                    total = commPrivileges.size,
                    icon = Icons.Default.PermContactCalendar,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(AppLayoutGrid.SpaceMedium))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AppLayoutGrid.SpaceSmall)
            ) {
                GlassButton(
                    text = if (isHealing) "Healing..." else "Auto-Heal Engine",
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
                    modifier = Modifier.weight(1.1f)
                )

                GlassButton(
                    text = "ADB Setup",
                    onClick = onOpenAdbDialog,
                    icon = Icons.Default.Terminal,
                    variant = GlassButtonVariant.PRIMARY,
                    modifier = Modifier.weight(0.9f)
                )
            }
        }
    }
}

/**
 * Pillar card showing health of an operational subsystem category.
 */
@Composable
private fun CategoryPillarItem(
    title: String,
    count: Int,
    total: Int,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    val isComplete = count >= total
    val accent = if (isComplete) AppSemanticColors.success else MaterialTheme.colorScheme.primary
    val containerBg = if (isComplete) AppSemanticColors.successContainer.copy(alpha = 0.6f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)

    Surface(
        shape = RoundedCornerShape(AppLayoutGrid.SquircleCornerRadius),
        color = containerBg,
        border = BorderStroke(
            AppLayoutGrid.BorderWidthStandard,
            if (isComplete) AppSemanticColors.successBorder else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
        ),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = AppLayoutGrid.SpaceSmall, horizontal = AppLayoutGrid.SpaceXS),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.height(AppLayoutGrid.SpaceMicro))
            Text(
                text = title,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                lineHeight = 12.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "$count/$total Ready",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = accent
            )
        }
    }
}
