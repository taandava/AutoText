package com.jmd.autotext

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.border
import androidx.compose.ui.platform.LocalContext
import com.jmd.autotext.data.PreferenceManager
import com.jmd.autotext.model.ChannelType
import com.jmd.autotext.model.ScheduledTask
import com.jmd.autotext.ui.screens.*
import com.jmd.autotext.ui.theme.*
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        triggerBackgroundContactIngestion()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        checkAndRequestPermissions()
        triggerBackgroundContactIngestion()

        setContent {
            AutoTextTheme {
                MainAppHost()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Do not scan on every resume to prevent severe UI stutter and I/O lock
    }

    private fun triggerBackgroundContactIngestion() {
        val prefs = com.jmd.autotext.data.PreferenceManager(applicationContext)
        val lastScan = prefs.lastContactIngestionTime
        val now = System.currentTimeMillis()
        // Throttle: Only ingest at most once every 6 hours
        if (now - lastScan < 6 * 3600 * 1000L) {
            return
        }

        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            try {
                prefs.lastContactIngestionTime = now
                val repo = com.jmd.autotext.data.AppRepository(applicationContext)
                val scanner = com.jmd.autotext.scanner.ContactIngestionEngine(applicationContext, repo)
                scanner.scanAndIngestAll()
            } catch (_: Exception) {}
        }
    }

    private fun checkAndRequestPermissions() {
        val permissionsToRequest = mutableListOf(
            Manifest.permission.SEND_SMS,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_SMS,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.READ_CALL_LOG
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val missing = permissionsToRequest.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isNotEmpty()) {
            requestPermissionLauncher.launch(missing.toTypedArray())
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppHost() {
    val context = LocalContext.current
    val prefs = remember { PreferenceManager(context) }
    var showOnboardingDialog by remember {
        mutableStateOf(!prefs.isOnboardingCompleted && prefs.userName.isBlank())
    }

    var currentTab by remember { mutableIntStateOf(0) }
    var isComposing by remember { mutableStateOf(false) }
    var editingTask by remember { mutableStateOf<ScheduledTask?>(null) }
    var composeChannel by remember { mutableStateOf(ChannelType.SMS) }
    var selectedDailyCampaignId by remember { mutableLongStateOf(1L) }
    var isToolsSubSectionOpen by remember { mutableStateOf(false) }
    var isAutoReplyEditing by remember { mutableStateOf(false) }
    var tasksInitialCategory by remember { mutableIntStateOf(0) }

    if (showOnboardingDialog) {
        var tempUserName by remember { mutableStateOf(prefs.userName) }
        var tempDeviceName by remember { mutableStateOf(prefs.getEffectiveDeviceName()) }
        val hardwareModel = "${Build.MANUFACTURER.uppercase()} ${Build.MODEL} (Android ${Build.VERSION.RELEASE})"

        AlertDialog(
            onDismissRequest = { /* force setup on first launch */ },
            containerColor = BentoGlassTokens.GlassSurfaceElevated,
            shape = RoundedCornerShape(20.dp),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(BentoGlassTokens.NeonCyan.copy(alpha = 0.15f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = BentoGlassTokens.NeonCyan,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Welcome to AutoText",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = BentoGlassTokens.TextPrimary
                        )
                        Text(
                            text = "Set up your profile & runner identity",
                            fontSize = 12.sp,
                            color = BentoGlassTokens.TextMuted
                        )
                    }
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Enter your name and an identifiable device name to personalize automated messages and local activity logs.",
                        fontSize = 12.sp,
                        color = BentoGlassTokens.TextMuted,
                        lineHeight = 16.sp
                    )

                    OutlinedTextField(
                        value = tempUserName,
                        onValueChange = { tempUserName = it },
                        label = { Text("Your Name (Owner / User)") },
                        placeholder = { Text("e.g. Dilip Sharma") },
                        singleLine = true,
                        leadingIcon = {
                            Icon(Icons.Default.Badge, contentDescription = null, tint = BentoGlassTokens.NeonCyan)
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = BentoGlassTokens.NeonCyan,
                            unfocusedBorderColor = BentoGlassTokens.GlassBorderSubtle,
                            focusedTextColor = BentoGlassTokens.TextPrimary,
                            unfocusedTextColor = BentoGlassTokens.TextPrimary,
                            focusedLabelColor = BentoGlassTokens.NeonCyan,
                            unfocusedLabelColor = BentoGlassTokens.TextMuted
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = tempDeviceName,
                        onValueChange = { tempDeviceName = it },
                        label = { Text("Device Runner Name") },
                        placeholder = { Text("e.g. Office Runner / Primary") },
                        singleLine = true,
                        leadingIcon = {
                            Icon(Icons.Default.PhoneAndroid, contentDescription = null, tint = BentoGlassTokens.VividEmerald)
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = BentoGlassTokens.VividEmerald,
                            unfocusedBorderColor = BentoGlassTokens.GlassBorderSubtle,
                            focusedTextColor = BentoGlassTokens.TextPrimary,
                            unfocusedTextColor = BentoGlassTokens.TextPrimary,
                            focusedLabelColor = BentoGlassTokens.VividEmerald,
                            unfocusedLabelColor = BentoGlassTokens.TextMuted
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Read-only Hardware Model Info
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(BentoGlassTokens.GlassSurfaceDefault, RoundedCornerShape(10.dp))
                            .border(1.dp, BentoGlassTokens.GlassBorderSubtle, RoundedCornerShape(10.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Column {
                            Text(
                                text = "HARDWARE MODEL",
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = BentoGlassTokens.TextMuted,
                                letterSpacing = 0.5.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = hardwareModel,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = BentoGlassTokens.NeonCyan
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val finalUser = tempUserName.trim().ifBlank { "User" }
                        val finalDev = tempDeviceName.trim().ifBlank { prefs.getEffectiveDeviceName() }
                        prefs.userName = finalUser
                        prefs.customDeviceName = finalDev
                        prefs.isOnboardingCompleted = true
                        showOnboardingDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BentoGlassTokens.NeonCyan),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = "Save & Continue",
                        color = Color.Black,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        )
    }

    if (isComposing) {
        ComposeTaskScreen(
            initialChannel = editingTask?.channel ?: composeChannel,
            taskToEdit = editingTask,
            onTaskSaved = {
                editingTask = null
                isComposing = false
            },
            onCancel = {
                editingTask = null
                isComposing = false
            }
        )
    } else {
        BackHandler(enabled = currentTab != 0) {
            currentTab = 0
        }
        Scaffold(
            bottomBar = {
                NavigationBar(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .border(1.dp, BentoGlassTokens.GlassBorderSubtle),
                    containerColor = BentoGlassTokens.GlassSurfaceDefault,
                    contentColor = BentoGlassTokens.TextPrimary,
                    tonalElevation = 0.dp
                ) {
                    val navItems = listOf(
                        Triple(0, "Home", Icons.Default.Dashboard),
                        Triple(1, "Tasks", Icons.Default.CalendarToday),
                        Triple(2, "Daily Auto", Icons.AutoMirrored.Filled.ScheduleSend),
                        Triple(3, "Auto Reply", Icons.AutoMirrored.Filled.ReplyAll),
                        Triple(4, "Tools", Icons.Default.Widgets)
                    )

                    navItems.forEach { (tabIndex, title, icon) ->
                        val isSelected = currentTab == tabIndex
                        NavigationBarItem(
                            selected = isSelected,
                            onClick = {
                                if (tabIndex == 1) {
                                    tasksInitialCategory = 0
                                }
                                currentTab = tabIndex
                            },
                            icon = {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = title,
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            label = {
                                Text(
                                    text = title,
                                    fontSize = 10.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    maxLines = 1
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = BentoGlassTokens.NeonCyan,
                                selectedTextColor = BentoGlassTokens.NeonCyan,
                                indicatorColor = BentoGlassTokens.NeonCyanContainer,
                                unselectedIconColor = BentoGlassTokens.TextMuted,
                                unselectedTextColor = BentoGlassTokens.TextMuted
                            )
                        )
                    }
                }
            },
            containerColor = BentoGlassTokens.VoidBackground
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(bottom = padding.calculateBottomPadding())
            ) {
                when (currentTab) {
                    0 -> DashboardScreen(
                        onNavigateToCompose = { channel ->
                            editingTask = null
                            composeChannel = channel
                            isComposing = true
                        },
                        onNavigateToTasks = {
                            tasksInitialCategory = 0
                            currentTab = 1
                        },
                        onNavigateToDailyCampaign = { id ->
                            selectedDailyCampaignId = id
                            currentTab = 2
                        },
                        onNavigateToSequences = {
                            tasksInitialCategory = 1
                            currentTab = 1
                        },
                        onEditTask = { task ->
                            editingTask = task
                            isComposing = true
                        }
                    )
                    1 -> TasksListScreen(
                        initialCategoryTab = tasksInitialCategory,
                        onNavigateToCompose = {
                            editingTask = null
                            composeChannel = ChannelType.SMS
                            isComposing = true
                        },
                        onEditTask = { task ->
                            editingTask = task
                            isComposing = true
                        }
                    )
                    2 -> DailyAutoSenderScreen(initialCampaignId = selectedDailyCampaignId)
                    3 -> AutoReplyScreen(
                        onEditingStateChanged = { isAutoReplyEditing = it }
                    )
                    4 -> MoreScreen(
                        onNavigateToDailySms = {
                            selectedDailyCampaignId = 1L
                            currentTab = 2
                        },
                        onSubSectionStateChanged = { isToolsSubSectionOpen = it }
                    )
                }
            }
        }
    }
}
