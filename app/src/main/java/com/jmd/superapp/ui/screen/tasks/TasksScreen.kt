/**
 * Package: com.jmd.superapp.ui.screen.tasks
 * Module: Application Core Subsystem
 *
 * What it is:
 * Architecture definition and implementation for [TasksScreen].
 *
 * What it does:
 * Primary coordinator screen managing scheduled task viewing, searching, filtering, and creation
 * in dynamic 2-column or 3-column / 3-row responsive adaptive grids.
 *
 * Linked With:
 * - Repositories: [com.jmd.superapp.data.repo.AppRepository]
 * - Models & Entities: [com.jmd.superapp.data.model.ChannelType], [com.jmd.superapp.data.model.ScheduledTask], [com.jmd.superapp.data.model.TaskStatus]
 * - UI Screens & Components: [androidx.compose.ui.Alignment], [androidx.compose.ui.Modifier], [androidx.compose.ui.platform.LocalContext], [androidx.compose.ui.unit.dp]
 * - Android Framework & Jetpack: [androidx.compose.foundation.background], [androidx.compose.foundation.lazy.grid.LazyVerticalGrid], [androidx.compose.material.icons.Icons]
 */

package com.jmd.superapp.ui.screen.tasks

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jmd.superapp.core.showAppToast
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.jmd.superapp.core.AppDataEvents
import com.jmd.superapp.data.model.ChannelType
import com.jmd.superapp.data.model.ScheduledTask
import com.jmd.superapp.data.model.TaskStatus
import com.jmd.superapp.data.repo.AppRepository
import com.jmd.superapp.ui.components.AppEmptyState
import com.jmd.superapp.ui.components.AppSearchField
import com.jmd.superapp.ui.components.AppSegmentedControl
import com.jmd.superapp.ui.screen.TaskTypeSelectionDialog
import com.jmd.superapp.ui.theme.AppDesignTokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * What it is:
 * Primary task dashboard presenting scheduled tasks with search, filtering, and creation controls.
 *
 * What it does:
 * Renders interactive Material 3 UI for [TasksListScreen] with single-column layout of compact grid cards.
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
    val coroutineScope = rememberCoroutineScope()

    fun refreshTasks() {
        coroutineScope.launch(Dispatchers.IO) {
            val t = repository.getAllTasks()
            withContext(Dispatchers.Main) {
                tasks = t
            }
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshTasks()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(Unit) {
        refreshTasks()
        AppDataEvents.tasksChanged.collect {
            refreshTasks()
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
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Search Bar (Single Column Full Width)
            AppSearchField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = "Search tasks...",
                leadingIcon = Icons.Default.Search,
                onClear = { searchQuery = "" },
                modifier = Modifier.fillMaxWidth()
            )

            // Segmented Status Filter with Live Counts
            AppSegmentedControl(
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

            // Single Column Task List or Empty State
            if (filteredTasks.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    AppEmptyState(
                        icon = Icons.Default.Inbox,
                        title = "No Tasks",
                        description = if (searchQuery.isNotEmpty()) {
                            "No tasks match your search filter."
                        } else {
                            "No scheduled tasks in this section. Create your first task schedule!"
                        },
                        actionText = "New Task",
                        onActionClick = { showTaskTypeDialog = true }
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 2.dp, bottom = 88.dp)
                ) {
                    items(filteredTasks, key = { it.id }) { task ->
                        TaskCard(
                            task = task,
                            onRunNow = {
                                coroutineScope.launch(Dispatchers.IO) {
                                    com.jmd.superapp.api.TaskFeatureApi.runTaskImmediately(context, repository, task)
                                    val t = repository.getAllTasks()
                                    withContext(Dispatchers.Main) { tasks = t }
                                }
                            },
                            onToggle = { isChecked: Boolean ->
                                coroutineScope.launch(Dispatchers.IO) {
                                    com.jmd.superapp.api.TaskFeatureApi.toggleTaskSchedule(context, repository, task, isChecked)
                                    val t = repository.getAllTasks()
                                    withContext(Dispatchers.Main) {
                                        tasks = t
                                        context.showAppToast(if (isChecked) "Task scheduled" else "Task paused")
                                    }
                                }
                            },
                            onEdit = {
                                onEditTask(task)
                            },
                            onDelete = {
                                coroutineScope.launch(Dispatchers.IO) {
                                    com.jmd.superapp.api.TaskFeatureApi.deleteTask(context, repository, task.id)
                                    val t = repository.getAllTasks()
                                    withContext(Dispatchers.Main) { tasks = t }
                                }
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
