package com.jmd.autotext.ui.screens

import android.net.Uri
import android.provider.ContactsContract
import android.widget.Toast
import com.jmd.autotext.ui.screens.sequence.StepEditorCard
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
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
import com.jmd.autotext.data.AppRepository
import com.jmd.autotext.model.ChannelType
import com.jmd.autotext.model.ContactSequence
import com.jmd.autotext.model.ContactSequenceStep
import com.jmd.autotext.ui.components.*
import com.jmd.autotext.ui.theme.BentoGlassTokens
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ContactSequenceStudio(
    sequenceId: Long = 0L,
    initialContactName: String = "",
    initialContactPhone: String = "",
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    val context = LocalContext.current
    val repository = remember { AppRepository(context) }

    // Sequence Header State
    var contactName by remember { mutableStateOf(initialContactName) }
    var contactPhone by remember { mutableStateOf(initialContactPhone) }
    var sequenceTitle by remember { mutableStateOf("") }
    var isEnabled by remember { mutableStateOf(true) }

    // Start Time State
    var isStartNow by remember { mutableStateOf(true) }
    var scheduledStartTimestamp by remember { mutableLongStateOf(System.currentTimeMillis() + 300_000L) }

    // Steps list state
    val steps = remember {
        mutableStateListOf(
            ContactSequenceStep(
                stepOrder = 1,
                channel = ChannelType.SMS,
                messageText = "",
                delayMinutesFromPrevious = 0,
                simSlot = 0
            ),
            ContactSequenceStep(
                stepOrder = 2,
                channel = ChannelType.WHATSAPP,
                messageText = "",
                delayMinutesFromPrevious = 120, // +2 hours
                simSlot = 0
            )
        )
    }

    // Load existing sequence if editing
    LaunchedEffect(sequenceId) {
        if (sequenceId > 0L) {
            val existing = repository.getContactSequenceById(sequenceId)
            if (existing != null) {
                contactName = existing.contactName
                contactPhone = existing.contactPhone
                sequenceTitle = existing.title
                isEnabled = existing.isEnabled
                if (existing.steps.isNotEmpty()) {
                    steps.clear()
                    steps.addAll(existing.steps)
                }
            }
        }
    }

    // Contact Picker
    val contactPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickContact()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val cursor = context.contentResolver.query(uri, null, null, null, null)
                cursor?.use {
                    if (it.moveToFirst()) {
                        val idIndex = it.getColumnIndex(ContactsContract.Contacts._ID)
                        val nameIndex = it.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)
                        val contactId = if (idIndex >= 0) it.getString(idIndex) else null
                        val name = if (nameIndex >= 0) it.getString(nameIndex) else ""
                        if (!name.isNullOrBlank()) contactName = name

                        if (contactId != null) {
                            val pCursor = context.contentResolver.query(
                                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                                null,
                                ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = ?",
                                arrayOf(contactId),
                                null
                            )
                            pCursor?.use { pc ->
                                if (pc.moveToFirst()) {
                                    val numberIdx = pc.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                                    if (numberIdx >= 0) {
                                        val number = pc.getString(numberIdx)
                                        if (!number.isNullOrBlank()) {
                                            contactPhone = number.replace("[^0-9+]".toRegex(), "")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Could not load contact: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        topBar = {
            Surface(
                color = BentoGlassTokens.GlassSurfaceElevated,
                border = androidx.compose.foundation.BorderStroke(1.dp, BentoGlassTokens.GlassBorderHairline),
                shadowElevation = 6.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .height(48.dp)
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = BentoGlassTokens.TextPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (sequenceId > 0) "Edit Contact Sequence" else "New Contact Task Sequence",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.5.sp,
                            color = BentoGlassTokens.TextPrimary
                        )
                        Text(
                            text = "Multi-step scheduled messaging per number",
                            fontSize = 10.sp,
                            color = BentoGlassTokens.NeonCyan
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (isEnabled) "Active" else "Paused",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isEnabled) BentoGlassTokens.VividEmerald else BentoGlassTokens.TextMuted
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Switch(
                            checked = isEnabled,
                            onCheckedChange = { isEnabled = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = BentoGlassTokens.VividEmerald,
                                uncheckedThumbColor = BentoGlassTokens.TextMuted,
                                uncheckedTrackColor = BentoGlassTokens.GlassSurfaceElevated
                            )
                        )
                    }
                }
            }
        },
        containerColor = BentoGlassTokens.VoidBackground
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 10.dp, vertical = 6.dp)
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 1. Target Contact Information Bento Card
            item {
                BentoCard(
                    modifier = Modifier.fillMaxWidth(),
                    borderColor = BentoGlassTokens.NeonCyan
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Person, contentDescription = null, tint = BentoGlassTokens.NeonCyan, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Target Contact & Number", fontWeight = FontWeight.Bold, color = BentoGlassTokens.TextPrimary, fontSize = 13.5.sp)
                        }
                        GlassButton(
                            text = "Pick Contact",
                            icon = Icons.Default.ContactPhone,
                            onClick = { contactPickerLauncher.launch(null) },
                            variant = GlassButtonVariant.SECONDARY,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 3.dp),
                            shape = BentoGlassTokens.ShapePill
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        GlassInputField(
                            value = contactName,
                            onValueChange = { contactName = it },
                            label = "Contact Name",
                            placeholder = "e.g. John Doe, Client, Lead",
                            leadingIcon = Icons.Default.Badge,
                            modifier = Modifier.weight(1f),
                            accentColor = BentoGlassTokens.NeonCyan
                        )
                        GlassInputField(
                            value = contactPhone,
                            onValueChange = { contactPhone = it.filter { ch -> ch.isDigit() || ch == '+' } },
                            label = "Phone Number",
                            placeholder = "e.g. +91 9876543210",
                            leadingIcon = Icons.Default.Phone,
                            modifier = Modifier.weight(1f),
                            accentColor = BentoGlassTokens.NeonCyan
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    GlassInputField(
                        value = sequenceTitle,
                        onValueChange = { sequenceTitle = it },
                        label = "Sequence Identifier (Optional)",
                        placeholder = if (contactName.isNotBlank()) "e.g. Follow-up for $contactName" else "e.g. 3-Step Lead Nurture",
                        leadingIcon = Icons.AutoMirrored.Filled.Label,
                        accentColor = BentoGlassTokens.ElectricIndigo
                    )
                }
            }

            // 2. Initial Start Timing Bento Card
            item {
                BentoCard(modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Schedule, contentDescription = null, tint = BentoGlassTokens.RadiantAmber, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Sequence Start Timing", fontWeight = FontWeight.Bold, color = BentoGlassTokens.TextPrimary, fontSize = 13.5.sp)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    GlassSegmentedControl(
                        items = listOf("Start Now (Immediate)", "Schedule Date & Time"),
                        selectedIndex = if (isStartNow) 0 else 1,
                        onItemSelected = { isStartNow = (it == 0) },
                        activeColor = BentoGlassTokens.RadiantAmber
                    )

                    if (!isStartNow) {
                        Spacer(modifier = Modifier.height(8.dp))
                        InlineBentoDateTimePicker(
                            timestampMillis = scheduledStartTimestamp,
                            onTimestampChanged = { scheduledStartTimestamp = it },
                            accentColor = BentoGlassTokens.RadiantAmber
                        )
                    }
                }
            }

            // 3. Visual Pipeline / Sequence Timeline
            item {
                BentoCard(
                    modifier = Modifier.fillMaxWidth(),
                    borderColor = BentoGlassTokens.ElectricIndigo
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.AutoMirrored.Filled.AltRoute, contentDescription = null, tint = BentoGlassTokens.ElectricIndigo, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Sequence Pipeline Flow (${steps.size} Steps)", fontWeight = FontWeight.Bold, color = BentoGlassTokens.TextPrimary, fontSize = 13.sp)
                    }
                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        steps.forEachIndexed { idx, step ->
                            val chipColor = when (step.channel) {
                                ChannelType.WHATSAPP -> BentoGlassTokens.VividEmerald
                                ChannelType.WHATSAPP_BUSINESS -> BentoGlassTokens.NeonCyan
                                else -> BentoGlassTokens.ElectricIndigo
                            }
                            val delayLabel = when {
                                idx == 0 && isStartNow -> "Now"
                                idx == 0 -> "At start"
                                step.delayMinutesFromPrevious < 60 -> "+${step.delayMinutesFromPrevious}m"
                                step.delayMinutesFromPrevious < 1440 -> "+${step.delayMinutesFromPrevious / 60}h"
                                else -> "+${step.delayMinutesFromPrevious / 1440}d"
                            }

                            Surface(
                                shape = BentoGlassTokens.ShapePill,
                                color = chipColor.copy(alpha = 0.15f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, chipColor.copy(alpha = 0.4f)),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(
                                    modifier = Modifier.padding(vertical = 4.dp, horizontal = 2.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text("#${idx + 1} ${step.channel.name.take(3)}", color = chipColor, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                                    Text(delayLabel, color = BentoGlassTokens.TextMuted, fontSize = 9.sp)
                                }
                            }
                        }
                    }
                }
            }

            // 4. Sequence Steps Editor
            itemsIndexed(steps) { index, step ->
                StepEditorCard(
                    stepNumber = index + 1,
                    isFirst = index == 0,
                    step = step,
                    onStepChanged = { updated -> steps[index] = updated },
                    onDelete = {
                        if (steps.size > 1) {
                            steps.removeAt(index)
                        } else {
                            Toast.makeText(context, "A sequence must contain at least 1 step", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onMoveUp = {
                        if (index > 0) {
                            val temp = steps[index]
                            steps[index] = steps[index - 1]
                            steps[index - 1] = temp
                        }
                    },
                    onMoveDown = {
                        if (index < steps.size - 1) {
                            val temp = steps[index]
                            steps[index] = steps[index + 1]
                            steps[index + 1] = temp
                        }
                    }
                )
            }

            // 5. Add Step Button
            item {
                GlassButton(
                    text = "Add Next Message / Task Step",
                    icon = Icons.Default.AddCircleOutline,
                    variant = GlassButtonVariant.SECONDARY,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        val nextDelay = if (steps.isEmpty()) 0 else 120 // +2 hours default
                        steps.add(
                            ContactSequenceStep(
                                stepOrder = steps.size + 1,
                                channel = if (steps.size % 2 == 1) ChannelType.WHATSAPP else ChannelType.SMS,
                                messageText = "",
                                delayMinutesFromPrevious = nextDelay,
                                simSlot = 0
                            )
                        )
                    }
                )
            }

            // 6. Save Sequence Action
            item {
                Spacer(modifier = Modifier.height(4.dp))
                GlassButton(
                    text = if (sequenceId > 0) "Update Sequence & Reschedule" else "Arm & Save Contact Sequence",
                    icon = Icons.Default.Save,
                    variant = GlassButtonVariant.PRIMARY,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        val cleanPhone = contactPhone.replace("[^0-9+]".toRegex(), "")
                        if (cleanPhone.length < 5) {
                            Toast.makeText(context, "Please enter a valid phone number for this contact", Toast.LENGTH_SHORT).show()
                            return@GlassButton
                        }
                        if (steps.any { it.messageText.isBlank() }) {
                            Toast.makeText(context, "Please fill in message text for all steps in the sequence", Toast.LENGTH_SHORT).show()
                            return@GlassButton
                        }

                        val startTs = if (isStartNow) {
                            System.currentTimeMillis() + 5000L
                        } else {
                            scheduledStartTimestamp
                        }

                        val sequenceToSave = ContactSequence(
                            id = sequenceId,
                            contactName = contactName.trim(),
                            contactPhone = cleanPhone,
                            title = sequenceTitle.ifBlank { "Sequence for ${contactName.ifBlank { cleanPhone }}" },
                            isEnabled = isEnabled,
                            createdAt = System.currentTimeMillis(),
                            steps = steps.toList()
                        )

                        repository.saveContactSequence(sequenceToSave, startTs)
                        Toast.makeText(context, "Sequence saved! ${steps.size} tasks armed for $cleanPhone", Toast.LENGTH_SHORT).show()
                        onSaved()
                    }
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}
