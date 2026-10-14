package com.jmd.autotext.ui.screens

import android.content.Intent
import com.jmd.autotext.ui.screens.tasks.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jmd.autotext.data.AppRepository
import com.jmd.autotext.data.PreferenceManager
import com.jmd.autotext.model.*
import com.jmd.autotext.scheduler.AlarmScheduler
import com.jmd.autotext.scheduler.TaskAlarmReceiver
import com.jmd.autotext.ui.components.*
import com.jmd.autotext.ui.theme.BentoGlassTokens
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@Suppress("FunctionName")
@Composable
fun TasksListScreen(
    initialCategoryTab: Int = 0,
    onNavigateToCompose: () -> Unit,
    onEditTask: (ScheduledTask) -> Unit = {}
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

    LaunchedEffect(Unit) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val t = repository.getAllTasks()
            val s = repository.getAllContactSequences()
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                tasks = t
                sequences = s
            }
        }
    }

    // Studio Full Page State
    var studioSequenceId by remember { mutableStateOf<Long?>(null) }
    var isStudioOpen by remember { mutableStateOf(false) }

    if (isStudioOpen) {
        ContactSequenceStudio(
            sequenceId = studioSequenceId ?: 0L,
            onDismiss = {
                isStudioOpen = false
                studioSequenceId = null
            },
            onSaved = {
                isStudioOpen = false
                studioSequenceId = null
                tasks = repository.getAllTasks()
                sequences = repository.getAllContactSequences()
            }
        )
        return
    }

    val filteredTasks = remember(tasks, selectedTab, searchQuery) {
        tasks.filter { task ->
            val matchesTab = when (selectedTab) {
                1 -> task.status == TaskStatus.PENDING
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
            .background(BentoGlassTokens.GlassBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 2.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Main Mode Tab: Single Tasks vs Contact Sequences
            GlassSegmentedControl(
                items = listOf("Single Tasks (${tasks.size})", "Contact Sequences (${sequences.size})"),
                selectedIndex = mainCategoryTab,
                onItemSelected = { mainCategoryTab = it },
                activeColor = if (mainCategoryTab == 0) BentoGlassTokens.ElectricIndigo else BentoGlassTokens.NeonCyan
            )

            // Bento Glass Search Bar
            GlassSearchField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = if (mainCategoryTab == 0) "Search tasks, contacts or message..." else "Search sequence contact, phone or title...",
                leadingIcon = Icons.Default.Search,
                onClear = { searchQuery = "" }
            )

            if (mainCategoryTab == 0) {
                // Top Master Task Scheduler Engine Banner
                BentoCardElevated(
                    modifier = Modifier.fillMaxWidth(),
                    shape = BentoGlassTokens.ShapeMedium,
                    accentGlow = BentoGlassTokens.ElectricIndigo
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
                                tint = BentoGlassTokens.ElectricIndigo,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    "Task Scheduler Engine",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = BentoGlassTokens.TextPrimary
                                )
                                Text(
                                    if (isTasksMasterEnabled) "Active • Automatic execution enabled"
                                    else "Task Scheduler Engine Paused",
                                    fontSize = 12.5.sp,
                                    color = if (isTasksMasterEnabled) BentoGlassTokens.ElectricIndigo else BentoGlassTokens.TextMuted
                                )
                            }
                        }

                        Switch(
                            checked = isTasksMasterEnabled,
                            onCheckedChange = { active ->
                                isTasksMasterEnabled = active
                                prefs.isTasksMasterEnabled = active
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = BentoGlassTokens.ElectricIndigo,
                                uncheckedThumbColor = BentoGlassTokens.TextMuted,
                                uncheckedTrackColor = BentoGlassTokens.GlassSurfaceElevated
                            )
                        )
                    }
                }

                // Bento Glass Segmented Status Filter
                GlassSegmentedControl(
                    items = listOf("All (${tasks.size})", "Pending", "Sent", "Failed"),
                    selectedIndex = selectedTab,
                    onItemSelected = { selectedTab = it },
                    activeColor = BentoGlassTokens.ElectricIndigo
                )

                // Task List or Empty State
                if (filteredTasks.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = 20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        BentoEmptyState(
                            icon = Icons.Default.Inbox,
                            title = "No Tasks Found",
                            description = if (searchQuery.isNotEmpty()) "No tasks match your search filter." else "No scheduled tasks in this section. Create your first automated schedule!",
                            actionText = "Schedule New Task",
                            onActionClick = onNavigateToCompose
                        )
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 60.dp)
                    ) {
                        items(filteredTasks, key = { it.id }) { task ->
                            BentoTaskCard(
                                task = task,
                                onRunNow = {
                                    val intent = Intent(context, TaskAlarmReceiver::class.java).apply {
                                        action = AlarmScheduler.ACTION_FIRE_TASK
                                        putExtra(AlarmScheduler.EXTRA_TASK_ID, task.id)
                                    }
                                    context.sendBroadcast(intent)
                                    tasks = repository.getAllTasks()
                                },
                                onToggle = { isChecked ->
                                    val newStatus = if (isChecked) TaskStatus.PENDING else TaskStatus.CANCELLED
                                    val updated = task.copy(status = newStatus)
                                    repository.updateTask(updated)
                                    if (isChecked) {
                                        AlarmScheduler(context).scheduleTask(updated)
                                    } else {
                                        AlarmScheduler(context).cancelTask(task.id)
                                    }
                                    tasks = repository.getAllTasks()
                                },
                                onEdit = {
                                    onEditTask(task)
                                },
                                onDelete = {
                                    repository.deleteTask(task.id)
                                    AlarmScheduler(context).cancelTask(task.id)
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
                        BentoEmptyState(
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
                        contentPadding = PaddingValues(bottom = 60.dp)
                    ) {
                        items(filteredSequences, key = { it.id }) { seq ->
                            BentoContactSequenceCard(
                                sequence = seq,
                                onEdit = {
                                    studioSequenceId = seq.id
                                    isStudioOpen = true
                                },
                                onToggle = { enable ->
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

        // Floating Action Button with Bento Glass Glow
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 12.dp, bottom = 12.dp)
                .size(46.dp)
                .clip(CircleShape)
                .background(if (mainCategoryTab == 0) BentoGlassTokens.IndigoGradient else BentoGlassTokens.VioletGradient)
                .border(1.dp, Color(0x66FFFFFF), CircleShape)
                .clickable {
                    if (mainCategoryTab == 0) {
                        onNavigateToCompose()
                    } else {
                        studioSequenceId = 0L
                        isStudioOpen = true
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = if (mainCategoryTab == 0) "Create Task" else "Create Contact Sequence",
                tint = Color.White,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

