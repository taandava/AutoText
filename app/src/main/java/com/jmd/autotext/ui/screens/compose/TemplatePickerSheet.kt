package com.jmd.autotext.ui.screens.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jmd.autotext.data.AppRepository
import com.jmd.autotext.model.MessageTemplate
import com.jmd.autotext.ui.components.*
import com.jmd.autotext.ui.theme.BentoGlassTokens

@Composable
fun TemplatePickerSheet(
    repository: AppRepository? = null,
    initialTemplates: List<MessageTemplate> = emptyList(),
    onSelectTemplate: (MessageTemplate) -> Unit,
    onDismiss: () -> Unit
) {
    var templateList by remember {
        mutableStateOf(repository?.getAllTemplates() ?: initialTemplates)
    }

    var isEditing by remember { mutableStateOf(false) }
    var editingTemplate by remember { mutableStateOf<MessageTemplate?>(null) }
    var formTitle by remember { mutableStateOf("") }
    var formCategory by remember { mutableStateOf("General") }
    var formContent by remember { mutableStateOf("") }
    var deleteCandidate by remember { mutableStateOf<MessageTemplate?>(null) }

    fun refresh() {
        if (repository != null) {
            templateList = repository.getAllTemplates()
        }
    }

    // Delete Confirmation Dialog
    if (deleteCandidate != null) {
        val cand = deleteCandidate!!
        AlertDialog(
            onDismissRequest = { deleteCandidate = null },
            title = {
                Text("Delete Template?", fontWeight = FontWeight.Bold, color = BentoGlassTokens.TextPrimary)
            },
            text = {
                Text(
                    "Are you sure you want to delete \"${cand.title}\"?",
                    color = BentoGlassTokens.TextMuted,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (repository != null) {
                            repository.deleteTemplate(cand.id)
                            refresh()
                        } else {
                            templateList = templateList.filterNot { it.id == cand.id }
                        }
                        deleteCandidate = null
                    }
                ) {
                    Text("Delete", color = BentoGlassTokens.CyberRose, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteCandidate = null }) {
                    Text("Cancel", color = BentoGlassTokens.TextMuted)
                }
            },
            containerColor = BentoGlassTokens.GlassSurfaceElevated
        )
    }

    if (isEditing) {
        // Sub-view: Add / Edit Template
        Scaffold(
            topBar = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(BentoGlassTokens.GlassSurfaceElevated)
                        .statusBarsPadding()
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                isEditing = false
                                editingTemplate = null
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = BentoGlassTokens.TextPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            if (editingTemplate != null) "Edit Template" else "New Template",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = BentoGlassTokens.TextPrimary
                        )
                    }
                }
            },
            containerColor = BentoGlassTokens.GlassBackground
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 14.dp, vertical = 10.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                BentoCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        GlassInputField(
                            value = formTitle,
                            onValueChange = { formTitle = it },
                            label = "Template Title",
                            placeholder = "e.g. Order Confirmation, Meeting Notes",
                            leadingIcon = Icons.AutoMirrored.Filled.Label,
                            accentColor = BentoGlassTokens.NeonCyan
                        )
                        GlassInputField(
                            value = formCategory,
                            onValueChange = { formCategory = it },
                            label = "Category",
                            placeholder = "Business, Greetings, Personal...",
                            leadingIcon = Icons.Default.Category,
                            accentColor = BentoGlassTokens.NeonCyan
                        )
                        GlassInputField(
                            value = formContent,
                            onValueChange = { formContent = it },
                            label = "Template Content",
                            placeholder = "Write message text... Use {name} or {date} for dynamic insertion.",
                            leadingIcon = Icons.Default.EditNote,
                            singleLine = false,
                            minLines = 4,
                            maxLines = 8,
                            accentColor = BentoGlassTokens.NeonCyan
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    GlassButton(
                        text = "Cancel",
                        onClick = {
                            isEditing = false
                            editingTemplate = null
                        },
                        variant = GlassButtonVariant.SECONDARY,
                        modifier = Modifier.weight(1f)
                    )

                    GlassButton(
                        text = if (editingTemplate != null) "Update" else "Save Template",
                        onClick = {
                            if (formTitle.isNotBlank() && formContent.isNotBlank()) {
                                val t = MessageTemplate(
                                    id = editingTemplate?.id ?: 0L,
                                    title = formTitle.trim(),
                                    category = formCategory.trim().ifBlank { "General" },
                                    content = formContent.trim()
                                )
                                if (repository != null) {
                                    if (t.id > 0) repository.updateTemplate(t) else repository.insertTemplate(t)
                                    refresh()
                                } else {
                                    val newId = if (t.id > 0) t.id else (System.currentTimeMillis())
                                    val saved = t.copy(id = newId)
                                    templateList = if (t.id > 0) {
                                        templateList.map { if (it.id == t.id) saved else it }
                                    } else {
                                        listOf(saved) + templateList
                                    }
                                }
                                isEditing = false
                                editingTemplate = null
                            }
                        },
                        variant = GlassButtonVariant.PRIMARY,
                        modifier = Modifier.weight(1f),
                        enabled = formTitle.isNotBlank() && formContent.isNotBlank()
                    )
                }
            }
        }
    } else {
        // Main List View with Add, Edit, Delete
        Scaffold(
            topBar = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(BentoGlassTokens.GlassSurfaceElevated)
                        .statusBarsPadding()
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = BentoGlassTokens.TextPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            "Select Message Template",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = BentoGlassTokens.TextPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = {
                                editingTemplate = null
                                formTitle = ""
                                formCategory = "General"
                                formContent = ""
                                isEditing = true
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = "Add Template",
                                tint = BentoGlassTokens.NeonCyan,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            },
            containerColor = BentoGlassTokens.GlassBackground
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (templateList.isEmpty()) {
                    item {
                        BentoEmptyState(
                            icon = Icons.Default.FormatQuote,
                            title = "No Templates",
                            description = "You have not saved any templates yet. Tap '+' at the top right to create one."
                        )
                    }
                } else {
                    items(templateList, key = { it.id }) { t ->
                        BentoCard(
                            onClick = { onSelectTemplate(t) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        t.title,
                                        fontWeight = FontWeight.Bold,
                                        color = BentoGlassTokens.NeonCyan,
                                        fontSize = 14.sp
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        t.content,
                                        fontSize = 13.sp,
                                        color = BentoGlassTokens.TextPrimary
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    BentoBadge(
                                        text = t.category,
                                        color = BentoGlassTokens.ElectricIndigo,
                                        showDot = false
                                    )
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    IconButton(
                                        onClick = {
                                            editingTemplate = t
                                            formTitle = t.title
                                            formCategory = t.category
                                            formContent = t.content
                                            isEditing = true
                                        },
                                        modifier = Modifier.size(30.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Edit,
                                            contentDescription = "Edit",
                                            tint = BentoGlassTokens.TextMuted,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            deleteCandidate = t
                                        },
                                        modifier = Modifier.size(30.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "Delete",
                                            tint = BentoGlassTokens.CyberRose,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
