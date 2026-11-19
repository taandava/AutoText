/**
 * Package: com.jmd.supersms.ui.screen
 * Module: Application Core Subsystem
 *
 * What it is:
 * Material 3 Expressive System Health Hub presenting real-time privilege states for SuperSMS.
 */

package com.jmd.supersms.ui.screen

import android.Manifest
import android.content.Context
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jmd.supersms.core.PermissionManager
import com.jmd.supersms.ui.components.AppGroupCard
import com.jmd.supersms.ui.components.AppGroupDivider
import com.jmd.supersms.ui.components.AppGroupRow
import com.jmd.supersms.ui.components.AppSubScreenHeader
import com.jmd.supersms.ui.screen.permission.*
import com.jmd.supersms.ui.theme.AppLayoutGrid
import com.jmd.supersms.ui.theme.AppSemanticColors

@Composable
fun PermissionCenterView(context: Context, onBack: () -> Unit) {
    var permissionsRefreshKey by remember { mutableIntStateOf(0) }

    val coreRuntimePermissions = remember {
        buildList {
            add(Manifest.permission.READ_CONTACTS)
            add(Manifest.permission.SEND_SMS)
            add(Manifest.permission.CALL_PHONE)
            add(Manifest.permission.READ_PHONE_STATE)
            add(Manifest.permission.READ_CALL_LOG)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    val runtimePermissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        permissionsRefreshKey++
    }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                permissionsRefreshKey++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val privilegeStates = remember(permissionsRefreshKey) {
        AppPrivilege.supportedEntries.associateWith { it.isGranted(context) }
    }

    val grantedCount = privilegeStates.values.count { it }
    val totalPermissions = AppPrivilege.supportedEntries.size

    val corePrivileges = remember { AppPrivilege.supportedEntries.filter { it.category == PrivilegeCategory.CORE } }
    val systemPrivileges = remember { AppPrivilege.supportedEntries.filter { it.category == PrivilegeCategory.SYSTEM } }
    val commPrivileges = remember { AppPrivilege.supportedEntries.filter { it.category == PrivilegeCategory.COMMUNICATION } }

    val coreCount = corePrivileges.count { privilegeStates[it] == true }
    val systemCount = systemPrivileges.count { privilegeStates[it] == true }
    val commCount = commPrivileges.count { privilegeStates[it] == true }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            AppSubScreenHeader(
                title = "Permissions",
                subtitle = "System Health",
                onBack = onBack
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = AppLayoutGrid.SpaceMedium),
            contentPadding = PaddingValues(
                top = AppLayoutGrid.SpaceSmall,
                bottom = AppLayoutGrid.SpaceLarge
            ),
            verticalArrangement = Arrangement.spacedBy(AppLayoutGrid.SpaceMedium)
        ) {
            item {
                PermissionHeroCard(
                    context = context,
                    grantedCount = grantedCount,
                    totalCount = totalPermissions,
                    privilegeStates = privilegeStates,
                    onOpenAdbDialog = {},
                    onHealDone = { permissionsRefreshKey++ }
                )
            }

            if (corePrivileges.isNotEmpty()) {
                item {
                    PermissionSectionHeader(
                        title = PrivilegeCategory.CORE.title,
                        subtitle = PrivilegeCategory.CORE.subtitle,
                        grantedCount = coreCount,
                        totalCount = corePrivileges.size
                    )
                }
                item {
                    AppGroupCard {
                        corePrivileges.forEachIndexed { index, privilege ->
                            val isGranted = privilegeStates[privilege] == true
                            AppGroupRow(
                                title = if (isGranted) "${privilege.title}: Active" else privilege.title,
                                subtitle = privilege.subtitle,
                                icon = privilege.icon,
                                iconTint = if (isGranted) AppSemanticColors.success else MaterialTheme.colorScheme.primary,
                                iconContainerColor = if (isGranted) AppSemanticColors.successContainer else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                                trailingContent = {
                                    if (isGranted) {
                                        PermissionStatusActiveChip()
                                    } else {
                                        PermissionGrantActionButton(
                                            text = privilege.buttonLabel,
                                            onClick = { privilege.openSettings(context) }
                                        )
                                    }
                                },
                                detailedPurpose = privilege.detailedPurpose,
                                onSettingsClick = { privilege.openSettings(context) }
                            )
                            if (index < corePrivileges.lastIndex) {
                                AppGroupDivider()
                            }
                        }
                    }
                }
            }

            if (commPrivileges.isNotEmpty()) {
                item {
                    PermissionSectionHeader(
                        title = PrivilegeCategory.COMMUNICATION.title,
                        subtitle = PrivilegeCategory.COMMUNICATION.subtitle,
                        grantedCount = commCount,
                        totalCount = commPrivileges.size
                    )
                }
                item {
                    AppGroupCard {
                        commPrivileges.forEachIndexed { index, privilege ->
                            val isGranted = privilegeStates[privilege] == true
                            AppGroupRow(
                                title = if (isGranted) "${privilege.title}: Active" else privilege.title,
                                subtitle = privilege.subtitle,
                                icon = privilege.icon,
                                iconTint = if (isGranted) AppSemanticColors.success else MaterialTheme.colorScheme.primary,
                                iconContainerColor = if (isGranted) AppSemanticColors.successContainer else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                                trailingContent = {
                                    if (isGranted) {
                                        PermissionStatusActiveChip()
                                    } else {
                                        PermissionGrantActionButton(
                                            text = privilege.buttonLabel,
                                            onClick = {
                                                if (privilege == AppPrivilege.COMMUNICATIONS) {
                                                    runtimePermissionsLauncher.launch(coreRuntimePermissions.toTypedArray())
                                                } else {
                                                    privilege.openSettings(context)
                                                }
                                            }
                                        )
                                    }
                                },
                                detailedPurpose = privilege.detailedPurpose,
                                onSettingsClick = { privilege.openSettings(context) }
                            )
                            if (index < commPrivileges.lastIndex) {
                                AppGroupDivider()
                            }
                        }
                    }
                }
            }

            if (systemPrivileges.isNotEmpty()) {
                item {
                    PermissionSectionHeader(
                        title = PrivilegeCategory.SYSTEM.title,
                        subtitle = PrivilegeCategory.SYSTEM.subtitle,
                        grantedCount = systemCount,
                        totalCount = systemPrivileges.size
                    )
                }
                item {
                    AppGroupCard {
                        systemPrivileges.forEachIndexed { index, privilege ->
                            val isGranted = privilegeStates[privilege] == true
                            AppGroupRow(
                                title = if (isGranted) "${privilege.title}: Active" else privilege.title,
                                subtitle = privilege.subtitle,
                                icon = privilege.icon,
                                iconTint = if (isGranted) AppSemanticColors.success else MaterialTheme.colorScheme.primary,
                                iconContainerColor = if (isGranted) AppSemanticColors.successContainer else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                                trailingContent = {
                                    if (isGranted) {
                                        PermissionStatusActiveChip()
                                    } else {
                                        PermissionGrantActionButton(
                                            text = privilege.buttonLabel,
                                            onClick = { privilege.openSettings(context) }
                                        )
                                    }
                                },
                                detailedPurpose = privilege.detailedPurpose,
                                onSettingsClick = { privilege.openSettings(context) }
                            )
                            if (index < systemPrivileges.lastIndex) {
                                AppGroupDivider()
                            }
                        }
                    }
                }
            }
        }
    }
}
