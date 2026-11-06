/**
 * Package: com.jmd.helper.ui.screen.permission
 * Module: UI Permission Center - Hero System Health Card
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
import com.jmd.helper.ui.theme.AppLayoutGrid
import com.jmd.helper.ui.theme.AppSemanticColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Modern Material 3 Hero System Health Card with dynamic luminance contrast and animated progress gauge.
 */
@Composable
fun PermissionHeroCard(
    context: Context,
    grantedCount: Int,
    totalCount: Int,
    isA11yActive: Boolean,
    canExactAlarm: Boolean,
    hasNotifListener: Boolean,
    isIgnoringBattery: Boolean,
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
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(AppLayoutGrid.SpaceStandard))

                    Column {
                        Text(
                            text = if (isAllGranted) "System Protected & Ready" else "System Health Check",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(AppLayoutGrid.SpaceMicro))
                        Text(
                            text = if (isAllGranted) {
                                "All $totalCount core privileges authorized & healthy."
                            } else {
                                "$grantedCount of $totalCount privileges authorized."
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
                    modifier = Modifier.size(48.dp)
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

            // Progress Bar
            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = if (isAllGranted) successAccent else primaryAccent,
                trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
            )

            Spacer(modifier = Modifier.height(AppLayoutGrid.SpaceStandard))

            // Mini Indicators Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                HealthMiniChip(name = "Engine", isGranted = isA11yActive)
                HealthMiniChip(name = "Alarms", isGranted = canExactAlarm)
                HealthMiniChip(name = "Notifs", isGranted = hasNotifListener)
                HealthMiniChip(name = "Battery", isGranted = isIgnoringBattery)
            }

            Spacer(modifier = Modifier.height(AppLayoutGrid.SpaceMedium))

            // Quick Actions Row
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

@Composable
private fun HealthMiniChip(name: String, isGranted: Boolean) {
    val activeColor = AppSemanticColors.success
    val inactiveColor = MaterialTheme.colorScheme.outline
    val bg = if (isGranted) AppSemanticColors.successContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)

    Surface(
        shape = RoundedCornerShape(AppLayoutGrid.ChipCornerRadius),
        color = bg,
        modifier = Modifier.padding(horizontal = AppLayoutGrid.SpaceMicro)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = AppLayoutGrid.SpaceSmall, vertical = AppLayoutGrid.SpaceXS),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(
                        color = if (isGranted) activeColor else inactiveColor,
                        shape = CircleShape
                    )
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = name,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = if (isGranted) activeColor else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
