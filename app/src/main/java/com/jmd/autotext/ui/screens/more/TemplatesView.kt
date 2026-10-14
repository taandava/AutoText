package com.jmd.autotext.ui.screens.more

import com.jmd.autotext.ui.screens.*

import android.app.AlarmManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jmd.autotext.data.AppRepository
import com.jmd.autotext.data.PreferenceManager
import com.jmd.autotext.model.*
import com.jmd.autotext.sender.SmsSender
import com.jmd.autotext.sender.WhatsAppAutomationSender
import com.jmd.autotext.service.AccessibilityHealthManager
import com.jmd.autotext.service.AutoTextAccessibilityService
import com.jmd.autotext.service.AutoTextDeviceAdminReceiver
import com.jmd.autotext.ui.components.*
import com.jmd.autotext.ui.theme.*
import com.jmd.autotext.util.AppFileLogger
import com.jmd.autotext.util.OverlayPermissionHelper
import java.text.SimpleDateFormat
import java.util.*

// ----------------- SUB SCREEN: TEMPLATES -----------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplatesView(repository: AppRepository, onBack: () -> Unit) {
    var templates by remember { mutableStateOf(repository.getAllTemplates()) }
    var isFormOpen by remember { mutableStateOf(false) }
    var editingTemplate by remember { mutableStateOf<MessageTemplate?>(null) }
    var formTitle by remember { mutableStateOf("") }
    var formCategory by remember { mutableStateOf("Business") }
    var formContent by remember { mutableStateOf("") }

    androidx.activity.compose.BackHandler(enabled = isFormOpen) {
        isFormOpen = false
        editingTemplate = null
    }

    if (isFormOpen) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            if (editingTemplate != null) "Edit Template" else "Create Template",
                            fontWeight = FontWeight.Bold,
                            color = BentoGlassTokens.TextPrimary
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            isFormOpen = false
                            editingTemplate = null
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = BentoGlassTokens.TextPrimary)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = BentoGlassTokens.VoidBackground)
                )
            },
            containerColor = BentoGlassTokens.VoidBackground
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 14.dp, vertical = 8.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                BentoCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        GlassInputField(
                            value = formTitle,
                            onValueChange = { formTitle = it },
                            label = "Template Title",
                            placeholder = "e.g. Order Confirmation, Meeting Link",
                            leadingIcon = Icons.AutoMirrored.Filled.Label,
                            accentColor = BentoGlassTokens.NeonCyan
                        )
                        GlassInputField(
                            value = formCategory,
                            onValueChange = { formCategory = it },
                            label = "Category",
                            placeholder = "Business, Greetings, Devotional...",
                            leadingIcon = Icons.Default.Category,
                            accentColor = BentoGlassTokens.NeonCyan
                        )
                        GlassInputField(
                            value = formContent,
                            onValueChange = { formContent = it },
                            label = "Template Content",
                            placeholder = "Write message here. Use {name} or {date} as placeholders.",
                            leadingIcon = Icons.Default.EditNote,
                            singleLine = false,
                            minLines = 4,
                            maxLines = 8,
                            accentColor = BentoGlassTokens.NeonCyan
                        )
                        Text(
                            "Supported dynamic tags: {name} will be replaced by the recipient's name, and {date} by today's date.",
                            fontSize = 11.5.sp,
                            color = BentoGlassTokens.TextMuted
                        )
                    }
                }

                GlassButton(
                    text = if (editingTemplate != null) "Update Template" else "Save Template",
                    onClick = {
                        if (formTitle.isNotBlank() && formContent.isNotBlank()) {
                            if (editingTemplate != null) {
                                repository.updateTemplate(
                                    editingTemplate!!.copy(
                                        title = formTitle.trim(),
                                        category = formCategory.trim(),
                                        content = formContent.trim()
                                    )
                                )
                            } else {
                                repository.insertTemplate(
                                    MessageTemplate(
                                        title = formTitle.trim(),
                                        category = formCategory.trim(),
                                        content = formContent.trim()
                                    )
                                )
                            }
                            templates = repository.getAllTemplates()
                            formTitle = ""
                            formContent = ""
                            editingTemplate = null
                            isFormOpen = false
                        }
                    },
                    icon = Icons.Default.Check,
                    variant = GlassButtonVariant.PRIMARY,
                    enabled = formTitle.isNotBlank() && formContent.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Message Templates", fontWeight = FontWeight.Bold, color = BentoGlassTokens.TextPrimary) },
                    navigationIcon = {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = BentoGlassTokens.TextPrimary) }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = BentoGlassTokens.VoidBackground)
                )
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = {
                        editingTemplate = null
                        formTitle = ""
                        formCategory = "Business"
                        formContent = ""
                        isFormOpen = true
                    },
                    containerColor = BentoGlassTokens.ElectricIndigo,
                    contentColor = Color.Black
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Template")
                }
            },
            containerColor = BentoGlassTokens.VoidBackground
        ) { padding ->
            if (templates.isEmpty()) {
                BentoEmptyState(
                    title = "No Templates Saved",
                    description = "Create reusable templates with dynamic tags like {name} and {date}.",
                    icon = Icons.Default.FormatQuote,
                    modifier = Modifier.fillMaxSize().padding(padding)
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(templates, key = { it.id }) { t ->
                        BentoCard(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                editingTemplate = t
                                formTitle = t.title
                                formCategory = t.category
                                formContent = t.content
                                isFormOpen = true
                            }
                        ) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(t.title, fontWeight = FontWeight.Bold, color = BentoGlassTokens.ElectricIndigo, fontSize = 14.sp)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = {
                                        editingTemplate = t
                                        formTitle = t.title
                                        formCategory = t.category
                                        formContent = t.content
                                        isFormOpen = true
                                    }, modifier = Modifier.size(28.dp)) {
                                        Icon(Icons.Default.Edit, contentDescription = "Edit Template", tint = BentoGlassTokens.NeonCyan, modifier = Modifier.size(18.dp))
                                    }
                                    Spacer(modifier = Modifier.width(4.dp))
                                    IconButton(onClick = {
                                        repository.deleteTemplate(t.id)
                                        templates = repository.getAllTemplates()
                                    }, modifier = Modifier.size(28.dp)) {
                                        Icon(Icons.Default.Delete, contentDescription = null, tint = BentoGlassTokens.CyberRose, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                            Text(t.content, color = BentoGlassTokens.TextPrimary, fontSize = 12.5.sp, modifier = Modifier.padding(vertical = 4.dp))
                            Text("Category: ${t.category}", color = BentoGlassTokens.TextMuted, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}
