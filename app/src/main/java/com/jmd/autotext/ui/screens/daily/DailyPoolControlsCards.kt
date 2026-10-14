package com.jmd.autotext.ui.screens.daily

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jmd.autotext.model.DynamicPoolStats
import com.jmd.autotext.ui.components.*
import com.jmd.autotext.ui.theme.BentoGlassTokens

@Composable
fun DailyContactPoolHeaderCard(
    stats: DynamicPoolStats,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    filterUnsentOnly: Boolean,
    onFilterUnsentOnlyChange: (Boolean) -> Unit,
    onClearPool: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isConfirmClearVisible by remember { mutableStateOf(false) }

    BentoCard(
        modifier = modifier.fillMaxWidth(),
        shape = BentoGlassTokens.ShapeLarge
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    "Dynamic Contact Pool",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = BentoGlassTokens.TextPrimary
                )
                Text(
                    "${stats.totalCount} Contacts (${stats.unsentCount} Unsent)",
                    fontSize = 11.sp,
                    color = BentoGlassTokens.TextMuted
                )
            }

            GlassButton(
                text = if (isConfirmClearVisible) "Cancel" else "Clear Pool",
                onClick = { isConfirmClearVisible = !isConfirmClearVisible },
                variant = GlassButtonVariant.DANGER,
                contentPadding = PaddingValues(horizontal = 9.dp, vertical = 4.dp),
                shape = BentoGlassTokens.ShapePill
            )
        }

        if (isConfirmClearVisible) {
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                shape = BentoGlassTokens.ShapeMedium,
                color = BentoGlassTokens.CyberRose.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, BentoGlassTokens.CyberRose.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text(
                        "Clear all ${stats.totalCount} contacts from pool? You can re-scan anytime.",
                        color = BentoGlassTokens.TextPrimary,
                        fontSize = 11.5.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        GlassButton(
                            text = "Cancel",
                            onClick = { isConfirmClearVisible = false },
                            variant = GlassButtonVariant.SECONDARY,
                            modifier = Modifier.weight(1f)
                        )
                        GlassButton(
                            text = "Confirm Clear",
                            onClick = {
                                onClearPool()
                                isConfirmClearVisible = false
                            },
                            variant = GlassButtonVariant.DANGER,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Search Field
        GlassSearchField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            placeholder = "Search by name or number...",
            leadingIcon = Icons.Default.Search,
            onClear = { onSearchQueryChange("") }
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Filter Chips
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            GlassChip(
                selected = !filterUnsentOnly,
                onClick = { onFilterUnsentOnlyChange(false) },
                label = "All (${stats.totalCount})",
                activeColor = BentoGlassTokens.ElectricIndigo,
                modifier = Modifier.weight(1f)
            )
            GlassChip(
                selected = filterUnsentOnly,
                onClick = { onFilterUnsentOnlyChange(true) },
                label = "Unsent (${stats.unsentCount})",
                activeColor = BentoGlassTokens.RadiantAmber,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun DailySentLedgerHeaderCard(
    isWhatsApp: Boolean,
    sentCount: Int,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    campaignThemeColor: Color,
    onResetCycle: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isConfirmResetCycleVisible by remember { mutableStateOf(false) }

    BentoCard(
        modifier = modifier.fillMaxWidth(),
        shape = BentoGlassTokens.ShapeLarge,
        borderColor = campaignThemeColor.copy(alpha = 0.35f)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${if (isWhatsApp) "WhatsApp" else "SMS"} Sent Ledger",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = BentoGlassTokens.TextPrimary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    BentoBadge(
                        text = "$sentCount Contacted",
                        color = campaignThemeColor,
                        showDot = false
                    )
                }
                Text(
                    "Strict deduplication ledger. Each unique recipient is messaged once.",
                    fontSize = 10.5.sp,
                    color = BentoGlassTokens.TextMuted,
                    lineHeight = 13.5.sp
                )
            }

            if (sentCount > 0) {
                Spacer(modifier = Modifier.width(6.dp))
                GlassButton(
                    text = if (isConfirmResetCycleVisible) "Cancel" else "Reset Cycle",
                    onClick = { isConfirmResetCycleVisible = !isConfirmResetCycleVisible },
                    variant = GlassButtonVariant.SECONDARY,
                    contentPadding = PaddingValues(horizontal = 9.dp, vertical = 4.dp),
                    shape = BentoGlassTokens.ShapePill
                )
            }
        }

        if (isConfirmResetCycleVisible) {
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                shape = BentoGlassTokens.ShapeMedium,
                color = BentoGlassTokens.RadiantAmber.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, BentoGlassTokens.RadiantAmber.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text(
                        "Reset campaign cycle for ${if (isWhatsApp) "WhatsApp" else "SMS"}? This will re-queue all $sentCount contacted numbers for new dispatches.",
                        color = BentoGlassTokens.TextPrimary,
                        fontSize = 11.5.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        GlassButton(
                            text = "Cancel",
                            onClick = { isConfirmResetCycleVisible = false },
                            variant = GlassButtonVariant.SECONDARY,
                            modifier = Modifier.weight(1f)
                        )
                        GlassButton(
                            text = "Confirm Reset",
                            onClick = {
                                onResetCycle()
                                isConfirmResetCycleVisible = false
                            },
                            variant = GlassButtonVariant.PRIMARY,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        GlassSearchField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            placeholder = "Search sent ledger by name or number...",
            leadingIcon = Icons.Default.Search,
            onClear = { onSearchQueryChange("") }
        )
    }
}
