/**
 * Package: com.jmd.helper.ui.screen.tasks
 * Module: Application Core Subsystem
 *
 * What it is:
 * Architecture definition and implementation for [TasksScreen].
 *
 * What it does:
 * Primary coordinator screen managing:
 *
 * Linked With:
 * - Repositories: [com.jmd.helper.data.repo.AppRepository]
 * - Models & Entities: [com.jmd.helper.data.model.ChannelType], [com.jmd.helper.data.model.ScheduledTask], [com.jmd.helper.data.model.TaskStatus]
 * - UI Screens & Components: [androidx.compose.ui.Alignment], [androidx.compose.ui.Modifier], [androidx.compose.ui.platform.LocalContext], [androidx.compose.ui.unit.dp]
 * - Android Framework & Jetpack: [androidx.compose.foundation.background], [androidx.compose.foundation.lazy.LazyColumn], [androidx.compose.foundation.lazy.items], [androidx.compose.material.icons.Icons]
 */

package com.jmd.helper.ui.screen.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.jmd.helper.data.model.ChannelType
import com.jmd.helper.data.model.ScheduledTask
import com.jmd.helper.data.model.TaskStatus
import com.jmd.helper.data.repo.AppRepository
import com.jmd.helper.ui.components.AppEmptyState
import com.jmd.helper.ui.components.GlassSearchField
import com.jmd.helper.ui.components.GlassSegmentedControl
import com.jmd.helper.ui.screen.TaskTypeSelectionDialog
import com.jmd.helper.ui.theme.AppDesignTokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * What it is:
 * Primary task dashboard presenting scheduled tasks with search, filtering, and creation controls.
 *
 * What it does:
 * Renders interactive Material 3 UI for [TasksListScreen] adhering to application design invariants.
 *
 * Linked with:
 * - Architecture: [TasksScreen], [TasksListScreen]
 * @param onNavigateToCompose Route callback to create a new task for a channel.
 * @param onEditTask Route callback to open existing task in the task composer.
 * @param onNavigateToDailyCampaign Route callback to navigate to daily campaigns.
 */
@Composable
fun TasksListScreen(
    onNavigateToCompose: (ChannelType) -> Unit,
    onEditTask: (ScheduledTask) -> Unit = {},
    onNavigateToDailyCampaign: () -> Unit = {},
) {
    val context = LocalContext.current
    val repository = remember { AppRepository(context) }
    var tasks by remember { mutableStateOf(emptyList<ScheduledTask>()) }
    var selectedTab by remember { mutableIntStateOf(0) } // 0=All, 1=Active, 2=Sent, 3=Failed
    var searchQuery by remember { mutableStateOf("") }
    var showTaskTypeDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val t = repository.getAllTasks()
            withContext(Dispatchers.Main) {
                tasks = t
            }
        }
    }

    val filteredTasks = remember(tasks, selectedTab, searchQuery) {
        tasks.filter { task ->
            val matchesTab = when (selectedTab) {
                1 -> (task.status.isPendingOrScheduled || task.status == TaskStatus.PENDING_CONFIRMATION || task.status.isRunning)
                2 -> task.status.isSuccessOrDone
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

    val pendingTasksCount = remember(tasks) {
        tasks.count { it.status.isPendingOrScheduled || it.status == TaskStatus.PENDING_CONFIRMATION || it.status.isRunning }
    }
    val doneTasksCount = remember(tasks) { tasks.count { it.status.isSuccessOrDone } }
    val failedTasksCount = remember(tasks) { tasks.count { it.status == TaskStatus.FAILED } }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Enterprise Modern Glass Search Bar
            GlassSearchField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = "Search tasks, contacts or message...",
                leadingIcon = Icons.Default.Search,
                onClear = { searchQuery = "" }
            )

            // Segmented Status Filter with Live Counts
            GlassSegmentedControl(
                items = listOf(
                    "All (${tasks.size})",
                    "Active ($pendingTasksCount)",
                    "Sent ($doneTasksCount)",
                    "Failed ($failedTasksCount)"
                ),
                selectedIndex = selectedTab,
                onItemSelected = { selectedTab = it },
                activeColor = MaterialTheme.colorScheme.primary
            )

            // Task List or Empty State
            if (filteredTasks.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    AppEmptyState(
                        icon = Icons.Default.Inbox,
                        title = "No Tasks Found",
                        description = if (searchQuery.isNotEmpty()) {
                            "No tasks match your search filter."
                        } else {
                            "No scheduled tasks in this section. Create your first task schedule!"
                        },
                        actionText = "Schedule New Task",
                        onActionClick = { showTaskTypeDialog = true }
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 4.dp, bottom = 88.dp)
                ) {
                    items(filteredTasks, key = { it.id }) { task ->
                        TaskCard(
                            task = task,
                            onRunNow = {
                                com.jmd.helper.api.TaskFeatureApi.runTaskImmediately(context, repository, task)
                                tasks = repository.getAllTasks()
                            },
                            onToggle = { isChecked: Boolean ->
                                com.jmd.helper.api.TaskFeatureApi.toggleTaskSchedule(context, repository, task, isChecked)
                                tasks = repository.getAllTasks()
                            },
                            onEdit = {
                                onEditTask(task)
                            },
                            onDelete = {
                                com.jmd.helper.api.TaskFeatureApi.deleteTask(context, repository, task.id)
                                tasks = repository.getAllTasks()
                            }
                        )
                    }
                }
            }
        }

        // Material 3 Floating Action Button with min 48dp target & navigationBarsPadding
        FloatingActionButton(
            onClick = { showTaskTypeDialog = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 16.dp, bottom = 16.dp)
                .sizeIn(minWidth = 56.dp, minHeight = 56.dp),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            shape = AppDesignTokens.ShapeLarge
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Create Task",
                modifier = Modifier.size(24.dp)
            )
        }

        if (showTaskTypeDialog) {
            TaskTypeSelectionDialog(
                onDismiss = { showTaskTypeDialog = false },
                onSelectChannel = { channel ->
                    showTaskTypeDialog = false
                    onNavigateToCompose(channel)
                },
                onSelectDailyCampaign = {
                    showTaskTypeDialog = false
                    onNavigateToDailyCampaign()
                }
            )
        }
    }
}
