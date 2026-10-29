

/**
 * Package: com.jmd.autotext.ui.screen
 * Module: UI Layer - Navigation & Hosting
 *
 * Primary UI application host managing bottom navigation tabs, deep-link text ingestion,
 * full-screen composer sheets, sequence studios, and task type selection dialogs.
 */
package com.jmd.autotext.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.*
import kotlin.time.Duration.Companion.milliseconds

import com.jmd.autotext.core.*
import com.jmd.autotext.data.model.*
import com.jmd.autotext.data.prefs.*
import com.jmd.autotext.ui.components.*
import com.jmd.autotext.ui.screen.autoreply.autoReplyScreen
import com.jmd.autotext.ui.screen.campaign.dailyAutoSenderScreen
import com.jmd.autotext.ui.screen.compose.composeTaskScreen
import com.jmd.autotext.ui.screen.tasks.tasksListScreen
import com.jmd.autotext.ui.theme.*

/**
 * Root Composable host managing top-level navigation, tab selection, and global overlay dialogs.
 *
 * @param sharedText Incoming text received from external ACTION_SEND intents.
 * @param onSharedTextConsumed Callback to reset consumed share intent data.
 * @param onThemeChanged Callback invoked when user modifies color theme settings.
 */
@Composable
fun mainAppHost(
    sharedText: String? = null,
    onSharedTextConsumed: () -> Unit = {},
    onThemeChanged: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val repository = remember { com.jmd.autotext.data.repo.AppRepository(context) }
    val prefs = remember { PreferenceManager(context) }

    var currentTab by remember { mutableIntStateOf(0) }
    var isComposing by remember { mutableStateOf(value = false) }
    var initialComposeMessage by remember { mutableStateOf("") }
    var editingTask by remember { mutableStateOf<ScheduledTask?>(null) }
    var composeChannel by remember { mutableStateOf(ChannelType.SMS) }
    var selectedDailyCampaignId by remember { mutableLongStateOf(0L) }
    var isToolsSubSectionOpen by remember { mutableStateOf(value = false) }
    var isAutoReplyEditing by remember { mutableStateOf(value = false) }
    var isTasksStudioOpen by remember { mutableStateOf(value = false) }
    var tasksInitialCategory by remember { mutableIntStateOf(0) }
    var showTaskTypeDialog by remember { mutableStateOf(value = false) }

    LaunchedEffect(sharedText) {
        if (!sharedText.isNullOrBlank()) {
            editingTask = null
            initialComposeMessage = sharedText
            composeChannel = ChannelType.SMS
            isComposing = true
            onSharedTextConsumed()
        }
    }

    if (showTaskTypeDialog) {
        taskTypeSelectionDialog(
            onDismiss = { showTaskTypeDialog = false },
            onSelectChannel = { channel ->
                showTaskTypeDialog = false
                editingTask = null
                composeChannel = channel
                isComposing = true
            },
            onSelectSequence = { _ ->
                showTaskTypeDialog = false
                tasksInitialCategory = 1
                currentTab = 0
            },
            onSelectDailyCampaign = {
                showTaskTypeDialog = false
                selectedDailyCampaignId = 0L
                currentTab = 1
            }
        )
    }

    if (isComposing) {
        composeTaskScreen(
            initialChannel = editingTask?.channel ?: composeChannel,
            initialMessage = initialComposeMessage,
            taskToEdit = editingTask,
            onTaskSaved = {
                editingTask = null
                initialComposeMessage = ""
                isComposing = false
            },
            onDeleteTask = { task ->
                repository.deleteTask(task.id)
                com.jmd.autotext.scheduler.AlarmScheduler(context).cancelTask(task.id)
                editingTask = null
                initialComposeMessage = ""
                isComposing = false
            },
            onCancel = {
                editingTask = null
                initialComposeMessage = ""
                isComposing = false
            }
        )
    } else {
        val isToolsSubSection = (currentTab == 3 && isToolsSubSectionOpen)
        val isCampaignDetail = (currentTab == 1 && selectedDailyCampaignId > 0L)
        val shouldShowBars = !isToolsSubSection && !isCampaignDetail && !isAutoReplyEditing && !isTasksStudioOpen

        BackHandler(enabled = currentTab != 0 && shouldShowBars) {
            currentTab = 0
        }

        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                if (shouldShowBars) {
                    var systemIndicators by remember {
                        mutableStateOf(SystemIndicatorResolver.resolveAll(context, prefs))
                    }
                    LaunchedEffect(Unit) {
                        while (true) {
                            systemIndicators = SystemIndicatorResolver.resolveAll(context, prefs)
                            delay(1500L.milliseconds)
                        }
                    }
                    val appTitle = prefs.customAppName.ifBlank { "AutoText" }
                    val iconVariant = prefs.appIconVariant
                    val appEmoji = when (iconVariant) {
                        "MESSENGER" -> "💬"
                        "BUSINESS" -> "💼"
                        "ROCKET" -> "🚀"
                        "FIRE" -> "🔥"
                        "DIAMOND" -> "💎"
                        "BOT" -> "🤖"
                        else -> "⚡"
                    }
                    val iconColor = when (iconVariant) {
                        "MESSENGER" -> MaterialTheme.colorScheme.primary
                        "BUSINESS" -> Color(0xFF2E7D32)
                        "ROCKET" -> Color(0xFFEF4444)
                        "FIRE" -> Color(0xFFF97316)
                        "DIAMOND" -> Color(0xFF06B6D4)
                        "BOT" -> Color(0xFF8B5CF6)
                        else -> MaterialTheme.colorScheme.primary
                    }

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 1.dp,
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .statusBarsPadding()
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            // Left: Brand identity & current section label (flex-shrinks safely with ellipsis)
                            Row(
                                modifier = Modifier
                                    .weight(1f, fill = false)
                                    .padding(end = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(30.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(iconColor.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = appEmoji,
                                        fontSize = 16.sp,
                                    )
                                }
                                Spacer(modifier = Modifier.width(7.dp))
                                Column(modifier = Modifier.weight(1f, fill = false)) {
                                    Text(
                                        text = appTitle,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = when (currentTab) {
                                            0 -> "Task Engine"
                                            1 -> "Campaigns"
                                            2 -> "Auto-Responder"
                                            else -> "Settings"
                                        },
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            // Right: Responsive Action Controls (Status Indicators + Icon-Only Stop + Icon-Only New Task)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                appHeaderStatusIndicators(
                                    indicators = systemIndicators,
                                    onClick = { currentTab = 3 }
                                )

                                // Icon-Only Emergency Stop Button
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier
                                        .size(26.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            com.jmd.autotext.core.AutomationSafetyManager.triggerEmergencyStop(context)
                                        }
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.Stop,
                                            contentDescription = "Emergency Stop",
                                            tint = MaterialTheme.colorScheme.onError,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }

                                // Icon-Only New Task Button (Only on Tasks tab)
                                if (currentTab == 0) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        border = BorderStroke(0.75.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
                                        modifier = Modifier
                                            .size(26.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable { showTaskTypeDialog = true }
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Default.Add,
                                                contentDescription = "New Task",
                                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            bottomBar = {
                if (shouldShowBars) {
                    NavigationBar(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(0.75.dp, MaterialTheme.colorScheme.outlineVariant),
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        tonalElevation = 2.dp
                    ) {
                    val navItems = listOf(
                        Triple(0, "Tasks", "📅"),
                        Triple(1, "Daily Auto", "🚀"),
                        Triple(2, "Auto Reply", "🤖"),
                        Triple(3, "Tools", "⚙️")
                    )

                    navItems.forEach { (tabIndex, title, emoji) ->
                        val isSelected = currentTab == tabIndex
                        NavigationBarItem(
                            selected = isSelected,
                            alwaysShowLabel = true,
                            onClick = {
                                if (tabIndex == 0) {
                                    tasksInitialCategory = 0
                                }
                                if (tabIndex == 1) {
                                    selectedDailyCampaignId = 0L
                                }
                                currentTab = tabIndex
                            },
                            icon = {
                                Text(
                                    text = emoji,
                                    fontSize = 19.sp
                                )
                            },
                            label = {
                                Text(
                                    text = title,
                                    fontSize = 10.5.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    maxLines = 1
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding())
            ) {
                var emergencyStopActive by remember {
                    mutableStateOf(com.jmd.autotext.core.AutomationSafetyManager.isEmergencyStopActive.get())
                }
                LaunchedEffect(Unit) {
                    while (true) {
                        emergencyStopActive = com.jmd.autotext.core.AutomationSafetyManager.isEmergencyStopActive.get()
                        delay(1000L.milliseconds)
                    }
                }
                if (emergencyStopActive) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "🛑 EMERGENCY STOP ACTIVE — Automation halted",
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Button(
                                onClick = {
                                    com.jmd.autotext.core.AutomationSafetyManager.resetEmergencyStop(context)
                                    emergencyStopActive = false
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(28.dp),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text("RESUME", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                ) {
                when (currentTab) {
                    0 -> tasksListScreen(
                        initialCategoryTab = tasksInitialCategory,
                        onNavigateToCompose = { channel ->
                            editingTask = null
                            composeChannel = channel
                            isComposing = true
                        },
                        onNavigateToSequenceStudio = { _ ->
                            tasksInitialCategory = 1
                        },
                        onEditTask = { task ->
                            editingTask = task
                            isComposing = true
                        },
                        onNavigateToDailyCampaign = {
                            currentTab = 1
                        },
                        onStudioStateChanged = { isTasksStudioOpen = it }
                    )
                    1 -> dailyAutoSenderScreen(
                        initialCampaignId = selectedDailyCampaignId,
                        onCampaignIdChange = { selectedDailyCampaignId = it }
                    )
                    2 -> autoReplyScreen(
                        onEditingStateChanged = { isAutoReplyEditing = it }
                    )
                    3 -> moreScreen(
                        onSubSectionStateChanged = { isToolsSubSectionOpen = it },
                        onThemeChanged = onThemeChanged
                    )
                }
            }
        }
    }
}
}
