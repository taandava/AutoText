/**
 * Package: com.jmd.autotext.ui.screen.tasks
 * Module: UI Tasks Management Screen Layer
 *
 * Primary coordinator screen managing:
 * - Single Tasks list and Contact Sequences list.
 * - Live real-time search and status filtering (All, Active, Sent, Failed).
 * - Master task scheduler engine toggle.
 * - Banking app shield status indicator.
 * - Task actions: Immediate Run Now, pause/resume, edit, delete.
 */
package com.jmd.autotext.ui.screen.tasks

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.AltRoute
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

import com.jmd.autotext.core.AccessibilityHealthManager
import com.jmd.autotext.core.showAppToast
import com.jmd.autotext.data.model.*
import com.jmd.autotext.data.prefs.PreferenceManager
import com.jmd.autotext.data.repo.AppRepository
import com.jmd.autotext.receiver.TaskAlarmReceiver
import com.jmd.autotext.scheduler.AlarmScheduler
import com.jmd.autotext.ui.components.*
import com.jmd.autotext.ui.screen.sequence.contactSequenceCard
import com.jmd.autotext.ui.screen.sequence.contactSequenceStudio
import com.jmd.autotext.ui.screen.taskTypeSelectionDialog
import com.jmd.autotext.ui.theme.AppDesignTokens

/**
 * Primary task dashboard presenting scheduled single tasks and multi-step contact sequences.
 *
 * @param initialCategoryTab Selected tab index on launch (0 = Single Tasks, 1 = Sequences).
 * @param onNavigateToCompose Route callback to create a new single task for a channel.
 * @param onNavigateToSequenceStudio Route callback to create or edit a multi-step sequence.
 * @param onEditTask Route callback to open existing task in the task composer.
 * @param onNavigateToDailyCampaign Route callback to navigate to daily campaigns.
 * @param onStudioStateChanged Callback notifying when sequence studio is opened or closed.
 */
@Composable
fun tasksListScreen(
    initialCategoryTab: Int = 0,
    onNavigateToCompose: (ChannelType) -> Unit,
    onNavigateToSequenceStudio: (Long) -> Unit = {},
    onEditTask: (ScheduledTask) -> Unit = {},
    onNavigateToDailyCampaign: () -> Unit = {},
    onStudioStateChanged: (Boolean) -> Unit = {},
) {
    val context = LocalContext.current
    val repository = remember { AppRepository(context) }
    val prefs = remember { PreferenceManager(context) }
    var isTasksMasterEnabled by remember { mutableStateOf(prefs.isTasksMasterEnabled) }
    var tasks by remember { mutableStateOf(emptyList<ScheduledTask>()) }
    var sequences by remember { mutableStateOf(emptyList<ContactSequence>()) }
    var mainCategoryTab by remember(initialCategoryTab) { mutableIntStateOf(initialCategoryTab) } // 0 = Single Tasks, 1 = Contact Sequences
    var selectedTab by remember { mutableIntStateOf(0) } // 0=All, 1=Pending, 2=Done, 3=Failed
    var searchQuery by remember { mutableStateOf("") }
    var showTaskTypeDialog by remember { mutableStateOf(value = false) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val t = repository.getAllTasks()
            val s = repository.getAllContactSequences()
            withContext(Dispatchers.Main) {
                tasks = t
                sequences = s
            }
        }
    }

    // Studio Full Page State
    var studioSequenceId by remember { mutableStateOf<Long?>(null) }
    var studioSequenceChannel by remember { mutableStateOf<ChannelType?>(null) }
    var isStudioOpen by remember { mutableStateOf(value = false) }

    LaunchedEffect(isStudioOpen) {
        onStudioStateChanged(isStudioOpen)
    }

    if (isStudioOpen) {
        contactSequenceStudio(
            sequenceId = studioSequenceId ?: 0L,
            initialChannel = studioSequenceChannel,
            onDismiss = {
                isStudioOpen = false
                studioSequenceId = null
                studioSequenceChannel = null
            },
            onSaved = {
                isStudioOpen = false
                studioSequenceId = null
                studioSequenceChannel = null
                tasks = repository.getAllTasks()
                sequences = repository.getAllContactSequences()
            },
        )
        return
    }

    val filteredTasks = remember(tasks, selectedTab, searchQuery) {
        tasks.filter { task ->
            val matchesTab = when (selectedTab) {
                1 -> (task.status == TaskStatus.PENDING || task.status == TaskStatus.PENDING_CONFIRMATION)
                2 -> task.status == TaskStatus.DONE
                3 -> task.status == TaskStatus.FAILED
                else -> true
            }
            val matchesSearch = searchQuery.isEmpty() ||
                    task.title.contains(searchQuery, ignoreCase = true) ||
                    task.messageText.contains(searchQuery, ignoreCase = true) ||
                    task.recipients.any { it.contains(searchQuery, ignoreCase = true) }

            matchesTab && matchesSearch
        }
    }

    val filteredSequences = remember(sequences, searchQuery) {
        sequences.filter { seq ->
            searchQuery.isEmpty() ||
                    seq.contactName.contains(searchQuery, ignoreCase = true) ||
                    seq.contactPhone.contains(searchQuery) ||
                    seq.title.contains(searchQuery, ignoreCase = true) ||
                    seq.steps.any { it.messageText.contains(searchQuery, ignoreCase = true) }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Main Mode Tab: Single Tasks vs Contact Sequences
            glassSegmentedControl(
                items = listOf("Single Tasks (${tasks.size})", "Contact Sequences (${sequences.size})"),
                selectedIndex = mainCategoryTab,
                onItemSelected = { mainCategoryTab = it },
                activeColor = if (mainCategoryTab == 0) AppDesignTokens.ElectricIndigo else AppDesignTokens.NeonCyan
            )

            // Enterprise Modern Glass Search Bar
            glassSearchField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = if (mainCategoryTab == 0) "Search tasks, contacts or message..." else "Search sequence contact, phone or title...",
                leadingIcon = Icons.Default.Search,
                onClear = { searchQuery = "" }
            )

            if (mainCategoryTab == 0) {
                // Top Master Task Scheduler Engine Banner
                appCardElevated(
                    modifier = Modifier.fillMaxWidth(),
                    shape = AppDesignTokens.ShapeMedium
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Alarm,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    "Task Scheduler Engine",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    if (isTasksMasterEnabled) "Active • Automatic execution enabled"
                                    else "Task Scheduler Engine Paused",
                                    fontSize = 12.5.sp,
                                    color = if (isTasksMasterEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Switch(
                            checked = isTasksMasterEnabled,
                            onCheckedChange = { active ->
                                isTasksMasterEnabled = active
                                prefs.isTasksMasterEnabled = active
                                if (active) {
                                    com.jmd.autotext.core.AutomationSafetyManager.resetEmergencyStop(context)
                                    AlarmScheduler(context).rescheduleAllPending()
                                    com.jmd.autotext.scheduler.DailyCampaignScheduler.scheduleAll(context)
                                    context.showAppToast("Task Scheduler active: Rescheduled pending tasks and campaigns")
                                } else {
                                    AlarmScheduler(context).cancelAll()
                                    com.jmd.autotext.engine.SequentialTaskEngine.getInstance(context).cancelAll()
                                    com.jmd.autotext.scheduler.DailyCampaignScheduler.cancelAllCampaigns(context)
                                    com.jmd.autotext.service.AutoTextAccessibilityService.clearPending()
                                    context.showAppToast("Task Scheduler paused: All tasks and campaigns halted")
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                checkedTrackColor = MaterialTheme.colorScheme.primary,
                                uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                            )
                        )
                    }
                }


                val pendingTasksCount = remember(tasks) { tasks.count { it.status == TaskStatus.PENDING || it.status == TaskStatus.PENDING_CONFIRMATION } }
                val doneTasksCount = remember(tasks) { tasks.count { it.status == TaskStatus.DONE } }
                val failedTasksCount = remember(tasks) { tasks.count { it.status == TaskStatus.FAILED } }

                // Enterprise Modern Segmented Status Filter with Live Counts
                glassSegmentedControl(
                    items = listOf("All (${tasks.size})", "Active ($pendingTasksCount)", "Sent ($doneTasksCount)", "Failed ($failedTasksCount)"),
                    selectedIndex = selectedTab,
                    onItemSelected = { selectedTab = it },
                    activeColor = MaterialTheme.colorScheme.primary
                )

                // Task List or Empty State
                if (filteredTasks.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = 20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        appEmptyState(
                            icon = Icons.Default.Inbox,
                            title = "No Tasks Found",
                            description = if (searchQuery.isNotEmpty()) "No tasks match your search filter." else "No scheduled tasks in this section. Create your first automated schedule!",
                            actionText = "Schedule New Task",
                            onActionClick = { showTaskTypeDialog = true }
                        )
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 80.dp)
                    ) {
                        items(filteredTasks, key = { it.id }) { task ->
                            taskCard(
                                task = task,
                                onRunNow = {
                                    repository.updateTaskStatus(task.id, TaskStatus.PENDING)
                                    val intent = Intent(context, TaskAlarmReceiver::class.java).apply {
                                        action = AlarmScheduler.ACTION_FIRE_TASK
                                        putExtra(AlarmScheduler.EXTRA_TASK_ID, task.id)
                                        putExtra(AlarmScheduler.EXTRA_MANUAL_RUN, true)
                                    }
                                    context.sendBroadcast(intent)
                                    tasks = repository.getAllTasks()
                                },
                                onToggle = { isChecked: Boolean ->
                                    val newStatus = if (isChecked) TaskStatus.PENDING else TaskStatus.CANCELLED
                                    val updated = task.copy(status = newStatus)
                                    repository.updateTask(updated)
                                    if (isChecked) {
                                        AlarmScheduler(context).scheduleTask(updated)
                                    } else {
                                        AlarmScheduler(context).cancelTask(task.id)
                                        com.jmd.autotext.engine.SequentialTaskEngine.getInstance(context).cancelTask(task.id)
                                    }
                                    tasks = repository.getAllTasks()
                                },
                                onEdit = {
                                    onEditTask(task)
                                },
                                onDelete = {
                                    com.jmd.autotext.engine.SequentialTaskEngine.getInstance(context).cancelTask(task.id)
                                    AlarmScheduler(context).cancelTask(task.id)
                                    repository.deleteTask(task.id)
                                    tasks = repository.getAllTasks()
                                }
                            )
                        }
                    }
                }
            } else {
                // Contact Sequences Tab
                if (filteredSequences.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = 20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        appEmptyState(
                            icon = Icons.AutoMirrored.Filled.AltRoute,
                            title = "No Contact Sequences",
                            description = if (searchQuery.isNotEmpty()) "No sequences match your search." else "Create multiple scheduled tasks inside a single contact with custom intervals across SMS and WhatsApp!",
                            actionText = "Create Contact Sequence",
                            onActionClick = {
                                studioSequenceId = 0L
                                isStudioOpen = true
                            }
                        )
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 80.dp)
                    ) {
                        items(filteredSequences, key = { it.id }) { seq ->
                            contactSequenceCard(
                                sequence = seq,
                                onEdit = {
                                    onNavigateToSequenceStudio(seq.id)
                                    studioSequenceId = seq.id
                                    isStudioOpen = true
                                },
                                onToggle = { enable: Boolean ->
                                    repository.toggleContactSequence(seq.id, enable)
                                    sequences = repository.getAllContactSequences()
                                    tasks = repository.getAllTasks()
                                },
                                onDelete = {
                                    repository.deleteContactSequence(seq.id)
                                    sequences = repository.getAllContactSequences()
                                    tasks = repository.getAllTasks()
                                }
                            )
                        }
                    }
                }
            }
        }

        // Material 3 Expressive Orange Floating Action Button
        FloatingActionButton(
            onClick = {
                if (mainCategoryTab == 0) {
                    showTaskTypeDialog = true
                } else {
                    studioSequenceId = 0L
                    isStudioOpen = true
                }
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 16.dp),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            shape = AppDesignTokens.ShapeLarge
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = if (mainCategoryTab == 0) "Create Task" else "Create Contact Sequence",
                modifier = Modifier.size(24.dp)
            )
        }

        if (showTaskTypeDialog) {
            taskTypeSelectionDialog(
                onDismiss = { showTaskTypeDialog = false },
                onSelectChannel = { channel ->
                    showTaskTypeDialog = false
                    onNavigateToCompose(channel)
                },
                onSelectSequence = { chosenChannel ->
                    showTaskTypeDialog = false
                    mainCategoryTab = 1
                    studioSequenceChannel = chosenChannel
                    studioSequenceId = 0L
                    isStudioOpen = true
                },
                onSelectDailyCampaign = {
                    showTaskTypeDialog = false
                    onNavigateToDailyCampaign()
                }
            )
        }
    }
}
