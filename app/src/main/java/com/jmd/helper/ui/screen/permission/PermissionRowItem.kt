/**
 * Package: com.jmd.helper.ui.screen.permission
 * Module: Application Core Subsystem
 *
 * What it is:
 * Architecture definition and implementation for [PermissionRowItem].
 *
 * What it does:
 * Provides core logic, component interfaces, and subsystem bindings for PermissionRowItem.
 *
 * Linked With:
 * - UI Screens & Components: [androidx.compose.ui.Alignment], [androidx.compose.ui.Modifier], [androidx.compose.ui.graphics.vector.ImageVector], [androidx.compose.ui.text.font.FontWeight]
 * - Android Framework & Jetpack: [androidx.compose.foundation.BorderStroke], [androidx.compose.material.icons.Icons], [androidx.compose.material.icons.filled.Check], [androidx.compose.runtime.Composable]
 */

package com.jmd.helper.ui.screen.permission

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jmd.helper.ui.components.AppGroupRow
import com.jmd.helper.ui.theme.AppDesignTokens
import com.jmd.helper.ui.theme.AppLayoutGrid
import com.jmd.helper.ui.theme.AppSemanticColors

/**
 * What it is:
 * Categorized section title header with status counter badge.
 *
 * What it does:
 * Renders interactive Material 3 UI for [PermissionSectionHeader] adhering to application design invariants.
 *
 * Linked with:
 * - Architecture: [PermissionRowItem], [PermissionSectionHeader]
 */
@Composable
fun PermissionSectionHeader(
    title: String,
    subtitle: String,
    grantedCount: Int,
    totalCount: Int
) {
    val isComplete = grantedCount >= totalCount
    val badgeColor = if (isComplete) AppSemanticColors.success else MaterialTheme.colorScheme.primary
    val badgeBg = if (isComplete) AppSemanticColors.successContainer else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppLayoutGrid.SpaceMicro, vertical = AppLayoutGrid.SpaceXS),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                fontSize = 12.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Surface(
            shape = AppDesignTokens.ShapePill,
            color = badgeBg,
            border = BorderStroke(AppLayoutGrid.BorderWidthStandard, badgeColor.copy(alpha = 0.35f))
        ) {
            Text(
                text = "$grantedCount/$totalCount Ready",
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                color = badgeColor,
                modifier = Modifier.padding(horizontal = AppLayoutGrid.SpaceSmall, vertical = AppLayoutGrid.SpaceXS)
            )
        }
    }
}

/**
 * What it is:
 * Active status pill badge indicating a granted permission.
 *
 * What it does:
 * Renders interactive Material 3 UI for [PermissionStatusActiveChip] adhering to application design invariants.
 *
 * Linked with:
 * - Architecture: [PermissionRowItem], [PermissionStatusActiveChip]
 */
@Composable
fun PermissionStatusActiveChip() {
    Surface(
        shape = AppDesignTokens.ShapePill,
        color = AppSemanticColors.successContainer,
        border = BorderStroke(AppLayoutGrid.BorderWidthStandard, AppSemanticColors.successBorder)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = AppSemanticColors.success,
                modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "Active",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = AppSemanticColors.success
            )
        }
    }
}

/**
 * What it is:
 * Action button prompting the user to grant or configure an ungranted permission.
 *
 * What it does:
 * Strictly adheres to 48dp minimum accessible touch target.
 *
 * Linked with:
 * - Architecture: [PermissionRowItem], [PermissionGrantActionButton]
 */
@Composable
fun PermissionGrantActionButton(
    text: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = AppDesignTokens.ShapePill,
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        tonalElevation = 2.dp,
        modifier = Modifier.defaultMinSize(minHeight = 48.dp, minWidth = 80.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = text,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary,
                maxLines = 1
            )
        }
    }
}
