package com.jmd.autotext.ui.screen.settings

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jmd.autotext.data.prefs.PreferenceManager
import com.jmd.autotext.ui.components.*
import com.jmd.autotext.ui.theme.AppDesignTokens
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

/**
 * Enterprise settings status guide presenting all header indicator icons,
 * their detailed operational descriptions, and real-time health statuses.
 */
@Composable
fun settingsStatusIndicatorsGuideCard(
    context: Context,
    prefs: PreferenceManager,
    modifier: Modifier = Modifier
) {
    var indicators by remember {
        mutableStateOf(SystemIndicatorResolver.resolveAll(context, prefs))
    }

    LaunchedEffect(Unit) {
        while (true) {
            indicators = SystemIndicatorResolver.resolveAll(context, prefs)
            delay(1500L.milliseconds)
        }
    }

    val activeCount = remember(indicators) { indicators.count { it.isActive } }
    val totalCount = indicators.size

    appCard(
        modifier = modifier.fillMaxWidth(),
        shape = AppDesignTokens.ShapeLarge
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Status Indicators & System Health",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Header icons show real-time health: Green = Working, Red = Attention needed",
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(
                    shape = AppDesignTokens.ShapePill,
                    color = if (activeCount == totalCount) IndicatorActiveGreen.copy(alpha = 0.16f) else IndicatorInactiveRed.copy(alpha = 0.16f),
                    border = BorderStroke(1.dp, if (activeCount == totalCount) IndicatorActiveGreen.copy(alpha = 0.5f) else IndicatorInactiveRed.copy(alpha = 0.5f))
                ) {
                    Text(
                        text = "$activeCount / $totalCount HEALTHY",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (activeCount == totalCount) IndicatorActiveGreen else IndicatorInactiveRed,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                thickness = 0.5.dp
            )

            // Indicators List
            indicators.forEach { indicator ->
                val statusColor = if (indicator.isActive) IndicatorActiveGreen else IndicatorInactiveRed

                Surface(
                    shape = AppDesignTokens.ShapeMedium,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.4f),
                    border = BorderStroke(0.75.dp, statusColor.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(statusColor.copy(alpha = 0.18f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = indicator.icon,
                                        contentDescription = indicator.name,
                                        tint = statusColor,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(9.dp))
                                Text(
                                    text = indicator.name,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            Surface(
                                shape = AppDesignTokens.ShapePill,
                                color = statusColor.copy(alpha = 0.15f),
                                border = BorderStroke(0.75.dp, statusColor.copy(alpha = 0.45f))
                            ) {
                                Text(
                                    text = if (indicator.isActive) "ACTIVE" else "NOT WORKING",
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = statusColor,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Text(
                            text = indicator.description,
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 15.sp,
                            modifier = Modifier.padding(start = 2.dp)
                        )

                        if (!indicator.isActive && indicator.actionLabel != null && indicator.onAction != null) {
                            Button(
                                onClick = { indicator.onAction.invoke(context) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = statusColor.copy(alpha = 0.18f),
                                    contentColor = statusColor
                                ),
                                border = BorderStroke(1.dp, statusColor.copy(alpha = 0.6f)),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                modifier = Modifier
                                    .align(Alignment.End)
                                    .height(28.dp),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = indicator.actionLabel,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
