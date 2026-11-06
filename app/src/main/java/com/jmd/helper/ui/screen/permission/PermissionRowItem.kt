/**
 * Package: com.jmd.helper.ui.screen.permission
 * Module: UI Permission Center - Reusable Row & Header Components
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
 * Categorized section title header with status counter badge.
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
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                fontSize = 12.sp,
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
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = badgeColor,
                modifier = Modifier.padding(horizontal = AppLayoutGrid.SpaceSmall, vertical = AppLayoutGrid.SpaceXS)
            )
        }
    }
}

/**
 * Active status pill badge indicating a granted permission.
 */
@Composable
fun PermissionStatusActiveChip() {
    Surface(
        shape = AppDesignTokens.ShapePill,
        color = AppSemanticColors.successContainer,
        border = BorderStroke(AppLayoutGrid.BorderWidthStandard, AppSemanticColors.successBorder)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = AppSemanticColors.success,
                modifier = Modifier.size(13.dp)
            )
            Spacer(modifier = Modifier.width(AppLayoutGrid.SpaceXS))
            Text(
                text = "Active",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = AppSemanticColors.success
            )
        }
    }
}

/**
 * Action button prompting the user to grant or configure an ungranted permission.
 */
@Composable
fun PermissionGrantActionButton(
    text: String,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        shape = AppDesignTokens.ShapePill,
        contentPadding = PaddingValues(horizontal = AppLayoutGrid.SpaceStandard, vertical = AppLayoutGrid.SpaceXS),
        modifier = Modifier.height(34.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary
        )
    ) {
        Text(
            text = text,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimary
        )
    }
}
