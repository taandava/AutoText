/**
 * Package: com.jmd.helper.ui.screen
 * Module: UI Permission & Health Screen Layer
 *
 * Material 3 Expressive System Health Hub presenting real-time privilege states:
 * - Accessibility Service (Task Engine)
 * - Exact Alarm privileges
 * - Notification Listener Service
 * - Battery optimization exemption
 * - Overlay permission
 * - Device Admin privileges
 * - Runtime permissions (SMS, Phone, Contacts, Media, Notifications)
 * - Sound & DND Policy Access
 *
 * Linked With:
 * - Health Manager: [com.jmd.helper.core.AccessibilityHealthManager]
 * - Overlay Helper: [com.jmd.helper.core.OverlayPermissionHelper]
 * - Components: [com.jmd.helper.ui.components.AppGroupCard], [com.jmd.helper.ui.components.AppGroupRow], [com.jmd.helper.ui.components.AppGroupDivider]
 * - Design System: [com.jmd.helper.ui.theme.AppSemanticColors], [com.jmd.helper.ui.theme.AppLayoutGrid]
 * - Modular Subsystem: [com.jmd.helper.ui.screen.permission.*]
 */
package com.jmd.helper.ui.screen

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
import com.jmd.helper.core.AccessibilityHealthManager
import com.jmd.helper.core.PermissionManager
import com.jmd.helper.ui.components.AppGroupCard
import com.jmd.helper.ui.components.AppGroupDivider
import com.jmd.helper.ui.components.AppGroupRow
import com.jmd.helper.ui.components.AppSubScreenHeader
import com.jmd.helper.ui.screen.permission.*
import com.jmd.helper.ui.theme.AppLayoutGrid
import com.jmd.helper.ui.theme.AppSemanticColors

/**
 * Material 3 Expressive System Health & Permission Center.
 *
 * @param context Android [Context] for system managers and settings intent dispatch.
 * @param onBack Callback invoked when user navigates back.
 */
@Composable
fun PermissionCenterView(context: Context, onBack: () -> Unit) {
    var showA11yDisclosureDialog by remember { mutableStateOf(false) }
    var showAdbSetupDialog by remember { mutableStateOf(false) }
    var permissionsRefreshKey by remember { mutableIntStateOf(0) }

    val coreRuntimePermissions = remember {
        buildList {
            add(Manifest.permission.READ_CONTACTS)
            add(Manifest.permission.SEND_SMS)
            add(Manifest.permission.RECEIVE_SMS)
            add(Manifest.permission.CALL_PHONE)
            add(Manifest.permission.READ_PHONE_STATE)
            add(Manifest.permission.READ_CALL_LOG)
            addAll(PermissionManager.getRequiredMediaPermissions())
            add(Manifest.permission.ANSWER_PHONE_CALLS)
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

    // Live permission states mapped by privilege
    val privilegeStates = remember(permissionsRefreshKey) {
        AppPrivilege.entries.associateWith { it.isGranted(context) }
    }

    val grantedCount = privilegeStates.values.count { it }
    val totalPermissions = AppPrivilege.entries.size

    val corePrivileges = remember { AppPrivilege.entries.filter { it.category == PrivilegeCategory.CORE } }
    val systemPrivileges = remember { AppPrivilege.entries.filter { it.category == PrivilegeCategory.SYSTEM } }
    val commPrivileges = remember { AppPrivilege.entries.filter { it.category == PrivilegeCategory.COMMUNICATION } }

    val coreCount = corePrivileges.count { privilegeStates[it] == true }
    val systemCount = systemPrivileges.count { privilegeStates[it] == true }
    val commCount = commPrivileges.count { privilegeStates[it] == true }

    // Dialogs
    if (showA11yDisclosureDialog) {
        A11yDisclosureDialog(
            context = context,
            onDismiss = { showA11yDisclosureDialog = false }
        )
    }

    if (showAdbSetupDialog) {
        AdbSetupModalDialog(
            context = context,
            onDismiss = { showAdbSetupDialog = false }
        )
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            AppSubScreenHeader(
                title = "Permission Center",
                subtitle = "System access & automated health",
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
                bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + AppLayoutGrid.SpaceSection
            ),
            verticalArrangement = Arrangement.spacedBy(AppLayoutGrid.SpaceMedium),
        ) {
            // 1. Hero Health Dashboard Card
            item {
                PermissionHeroCard(
                    context = context,
                    grantedCount = grantedCount,
                    totalCount = totalPermissions,
                    privilegeStates = privilegeStates,
                    onOpenAdbDialog = { showAdbSetupDialog = true },
                    onHealDone = { permissionsRefreshKey++ }
                )
            }

            // 2. Group 1: Core Automation Privileges
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
                        val a11yState = if (privilege == AppPrivilege.ACCESSIBILITY) {
                            AccessibilityHealthManager.getServiceState(context)
                        } else null

                        val rowTitle = if (a11yState != null) {
                            when (a11yState) {
                                AccessibilityHealthManager.ServiceState.OPERATIONAL -> "Task Service: Active"
                                AccessibilityHealthManager.ServiceState.STANDBY -> "Task Service: Bank-Safe Standby"
                                AccessibilityHealthManager.ServiceState.ENABLED_BUT_NOT_BOUND -> "Task Service: Connecting"
                                AccessibilityHealthManager.ServiceState.DISABLED -> privilege.title
                            }
                        } else {
                            if (isGranted) "${privilege.title}: Active" else privilege.title
                        }

                        val rowSubtitle = if (a11yState != null) {
                            when (a11yState) {
                                AccessibilityHealthManager.ServiceState.OPERATIONAL -> "Active in background to dispatch scheduled messaging tasks."
                                AccessibilityHealthManager.ServiceState.STANDBY -> "Bank-Safe Standby: Operates on-demand for tasks only."
                                AccessibilityHealthManager.ServiceState.ENABLED_BUT_NOT_BOUND -> "Enabled in settings, connecting background binder."
                                AccessibilityHealthManager.ServiceState.DISABLED -> privilege.subtitle
                            }
                        } else privilege.subtitle

                        AppGroupRow(
                            title = rowTitle,
                            subtitle = rowSubtitle,
                            icon = privilege.icon,
                            iconTint = if (isGranted) AppSemanticColors.success else MaterialTheme.colorScheme.primary,
                            iconContainerColor = if (isGranted) AppSemanticColors.successContainer else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                            trailingContent = {
                                if (isGranted) {
                                    PermissionStatusActiveChip()
                                } else {
                                    val buttonText = if (a11yState == AccessibilityHealthManager.ServiceState.ENABLED_BUT_NOT_BOUND) "Toggle" else privilege.buttonLabel
                                    PermissionGrantActionButton(
                                        text = buttonText,
                                        onClick = {
                                            if (privilege == AppPrivilege.ACCESSIBILITY) {
                                                showA11yDisclosureDialog = true
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

                        if (index < corePrivileges.lastIndex) {
                            AppGroupDivider()
                        }
                    }
                }
            }

            // 3. Group 2: System & Power Privileges
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

            // 4. Group 3: Device & Communication Privileges
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

            // 5. System Optimization & Hardware Guidance
            item {
                PermissionSectionHeader(
                    title = "Hardware & System Reliability",
                    subtitle = "Brand-specific autostart & restricted settings guidance",
                    grantedCount = 2,
                    totalCount = 2
                )
            }

            item {
                OemAutostartCard(context = context)
            }

            item {
                SideloadedAppHelpCard(context = context)
            }
        }
    }
}
