package com.jmd.autotext.ui.screens

import com.jmd.autotext.ui.screens.compose.*

import android.net.Uri
import android.provider.ContactsContract
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import com.jmd.autotext.data.AppRepository
import com.jmd.autotext.model.*
import com.jmd.autotext.scheduler.AlarmScheduler
import com.jmd.autotext.service.AutoTextAccessibilityService
import com.jmd.autotext.ui.components.*
import com.jmd.autotext.ui.theme.BentoGlassTokens
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

data class SelectedRecipient(
    val name: String,
    val phone: String
)

@Composable
fun ComposeTaskScreen(
    initialChannel: ChannelType = ChannelType.SMS,
    taskToEdit: ScheduledTask? = null,
    onTaskSaved: () -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val repository = remember { AppRepository(context) }
    val scheduler = remember { AlarmScheduler(context) }

    var selectedChannel by remember { mutableStateOf(taskToEdit?.channel ?: initialChannel) }
    var title by remember { mutableStateOf(taskToEdit?.title ?: "") }
    var recipientsInput by remember { mutableStateOf("") }
    var selectedRecipients by remember {
        mutableStateOf(
            taskToEdit?.recipients?.mapIndexed { idx, phone ->
                val name = taskToEdit.recipientNames.getOrNull(idx) ?: phone
                SelectedRecipient(
                    name = if (name == "Self" && phone == "Self") "" else name,
                    phone = if (phone == "Self") "" else phone
                )
            }?.filter { it.phone.isNotBlank() } ?: emptyList()
        )
    }
    var duplicateWarning by remember { mutableStateOf<String?>(null) }
    var messageText by remember { mutableStateOf(taskToEdit?.messageText ?: "") }

    fun tryAddRecipient(name: String, rawPhone: String): Boolean {
        val cleanPhone = rawPhone.trim()
        if (cleanPhone.isBlank()) return false
        val cleanName = name.trim()

        val norm = com.jmd.autotext.util.PhoneNumberNormalizer.normalize(cleanPhone)
        val digits = cleanPhone.filter { it.isDigit() }

        val isDuplicate = selectedRecipients.any { existing ->
            val existingNorm = com.jmd.autotext.util.PhoneNumberNormalizer.normalize(existing.phone)
            (norm.isNotBlank() && existingNorm.isNotBlank() && norm == existingNorm) ||
            (digits.length >= 10 && existing.phone.filter { it.isDigit() }.endsWith(digits.takeLast(10)))
        }

        if (isDuplicate) {
            val displayName = cleanName.ifBlank { cleanPhone }
            duplicateWarning = "Duplicate Rejected: '$displayName' ($cleanPhone) is already added!"
            android.widget.Toast.makeText(context, "Duplicate Rejected: '$displayName' ($cleanPhone) is already selected!", android.widget.Toast.LENGTH_LONG).show()
            return false
        }

        duplicateWarning = null
        selectedRecipients = selectedRecipients + SelectedRecipient(name = cleanName, phone = cleanPhone)
        return true
    }

    var mediaUri by remember { mutableStateOf(taskToEdit?.mediaUri ?: "") }

    val statusMediaPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val contentResolver = context.contentResolver
                val mimeType = contentResolver.getType(uri) ?: ""
                val ext = when {
                    mimeType.contains("mp4") || mimeType.contains("video") -> "mp4"
                    mimeType.contains("png") -> "png"
                    mimeType.contains("webp") -> "webp"
                    else -> "jpg"
                }
                val mediaDir = java.io.File(context.filesDir, "status_media").apply { mkdirs() }
                val destFile = java.io.File(mediaDir, "status_${System.currentTimeMillis()}.$ext")
                contentResolver.openInputStream(uri)?.use { input ->
                    java.io.FileOutputStream(destFile).use { output ->
                        input.copyTo(output)
                    }
                }
                val contentUri = androidx.core.content.FileProvider.getUriForFile(
                    context,
                    "com.jmd.autotext.fileprovider",
                    destFile
                )
                mediaUri = contentUri.toString()
                Toast.makeText(context, "Media attached: ${destFile.name}", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                mediaUri = uri.toString()
                Toast.makeText(context, "Media selected", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Contact Picker Launcher from System Contacts (Extracts BOTH Name and Number)
    val contactPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickContact()
    ) { contactUri: Uri? ->
        if (contactUri != null) {
            try {
                var contactId: String? = null
                var contactDisplayName = ""

                context.contentResolver.query(
                    contactUri,
                    arrayOf(ContactsContract.Contacts._ID, ContactsContract.Contacts.DISPLAY_NAME),
                    null,
                    null,
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idIdx = cursor.getColumnIndex(ContactsContract.Contacts._ID)
                        val nameIdx = cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)
                        if (idIdx != -1) contactId = cursor.getString(idIdx)
                        if (nameIdx != -1) contactDisplayName = cursor.getString(nameIdx).orEmpty()
                    }
                }

                if (contactId != null) {
                    context.contentResolver.query(
                        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                        arrayOf(
                            ContactsContract.CommonDataKinds.Phone.NUMBER,
                            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
                        ),
                        "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
                        arrayOf(contactId),
                        null
                    )?.use { pCursor ->
                        if (pCursor.moveToFirst()) {
                            val numCol = pCursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                            val nameCol = pCursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                            val number = if (numCol != -1) pCursor.getString(numCol).orEmpty() else ""
                            val pName = if (nameCol != -1) pCursor.getString(nameCol).orEmpty() else ""
                            val finalName = pName.ifBlank { contactDisplayName }
                            if (number.isNotBlank()) {
                                tryAddRecipient(finalName, number)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                android.widget.Toast.makeText(context, "Error reading contact: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Date & Time
    val calendar = remember {
        Calendar.getInstance().apply {
            if (taskToEdit != null) {
                timeInMillis = taskToEdit.triggerTimeMillis
            } else {
                add(Calendar.MINUTE, 5) // default 5 mins ahead
            }
        }
    }
    var triggerTimeMillis by remember { mutableLongStateOf(taskToEdit?.triggerTimeMillis ?: calendar.timeInMillis) }
    var selectedRecurrence by remember { mutableStateOf(taskToEdit?.recurrence ?: RecurrenceType.ONCE) }
    var customIntervalMinutes by remember { mutableIntStateOf(taskToEdit?.customIntervalMinutes ?: 60) }
    var simSlot by remember { mutableIntStateOf(taskToEdit?.simSlot ?: 0) }
    var askBeforeSend by remember { mutableStateOf(taskToEdit?.askBeforeSend ?: false) }

    // Fake call specific
    var fakeCallerName by remember { mutableStateOf(taskToEdit?.fakeCallerName ?: "Work / Office") }
    var fakeCallerNumber by remember { mutableStateOf(taskToEdit?.fakeCallerNumber ?: "+91 98765 43210") }
    var fakeCallStyle by remember { mutableStateOf(taskToEdit?.fakeCallStyle ?: "PIXEL") }

    // Sound mode specific
    var soundModeSetting by remember { mutableStateOf(taskToEdit?.soundModeSetting ?: "SILENT") }

    // WhatsApp Specific States
    var showWhatsAppContactsDialog by remember { mutableStateOf(false) }
    var showWhatsAppGroupsDialog by remember { mutableStateOf(false) }
    var refreshGroupsTrigger by remember { mutableIntStateOf(0) }
    var statusTargetPackage by remember {
        mutableStateOf(
            if (taskToEdit?.channel == ChannelType.WHATSAPP_STATUS && taskToEdit.recipients.firstOrNull()?.contains("w4b") == true) {
                "com.whatsapp.w4b"
            } else {
                "com.whatsapp"
            }
        )
    }
    var selectedGroupName by remember {
        mutableStateOf(
            if (taskToEdit != null && taskToEdit.channel == ChannelType.WHATSAPP_GROUP) {
                taskToEdit.recipients.firstOrNull()?.removePrefix("group:") ?: ""
            } else ""
        )
    }

    // Dialog states
    var showTemplateDialog by remember { mutableStateOf(false) }
    var showPoolPickerDialog by remember { mutableStateOf(false) }
    val templates = remember { repository.getAllTemplates() }

    BackHandler(enabled = true) {
        if (showTemplateDialog) {
            showTemplateDialog = false
        } else if (showPoolPickerDialog) {
            showPoolPickerDialog = false
        } else if (showWhatsAppContactsDialog) {
            showWhatsAppContactsDialog = false
        } else if (showWhatsAppGroupsDialog) {
            showWhatsAppGroupsDialog = false
        } else {
            onCancel()
        }
    }

    val isUnicode = remember(messageText) {
        messageText.any { it.code > 127 }
    }
    val smsPartCount = remember(messageText, isUnicode) {
        if (messageText.isEmpty()) 1
        else if (isUnicode) {
            if (messageText.length <= 70) 1 else ((messageText.length + 66) / 67)
        } else {
            if (messageText.length <= 160) 1 else ((messageText.length + 152) / 153)
        }
    }

    val currentLocale = androidx.compose.ui.platform.LocalLocale.current.platformLocale
    val formattedDate = remember(triggerTimeMillis, currentLocale) {
        SimpleDateFormat("EEE, dd MMM yyyy", currentLocale).format(Date(triggerTimeMillis))
    }
    val formattedTime = remember(triggerTimeMillis, currentLocale) {
        SimpleDateFormat("hh:mm a", currentLocale).format(Date(triggerTimeMillis))
    }

    val saveTaskAction: () -> Unit = saveTaskAction@ {
        // Ingest any text remaining in manual input if not status or group
        if (selectedChannel != ChannelType.WHATSAPP_STATUS && selectedChannel != ChannelType.WHATSAPP_GROUP) {
            if (recipientsInput.isNotBlank()) {
                recipientsInput.split(",").map { it.trim() }.filter { it.isNotEmpty() }.forEach {
                    tryAddRecipient("", it)
                }
                recipientsInput = ""
            }
        }

        val finalPhoneList = selectedRecipients.map { it.phone }
        val finalNameList = selectedRecipients.map { it.name.ifBlank { it.phone } }

        val finalRecipients: List<String>
        val finalRecipientNames: List<String>

        if (selectedChannel == ChannelType.WHATSAPP_STATUS) {
            if (mediaUri.isBlank() && messageText.isBlank()) {
                Toast.makeText(context, "⚠️ Please enter status text or choose a photo/video", Toast.LENGTH_LONG).show()
                return@saveTaskAction
            }
            val statusTarget = if (statusTargetPackage == "com.whatsapp.w4b") "My Status (w4b)" else "My Status"
            val statusDisplayName = if (statusTargetPackage == "com.whatsapp.w4b") "WA Business Status" else "WhatsApp Status"
            finalRecipients = listOf(statusTarget)
            finalRecipientNames = listOf(statusDisplayName)
        } else if (selectedChannel == ChannelType.WHATSAPP_GROUP) {
            val grp = if (selectedGroupName.isNotBlank()) selectedGroupName.trim()
                      else if (recipientsInput.isNotBlank()) recipientsInput.trim()
                      else selectedRecipients.firstOrNull()?.name ?: ""
            if (grp.isBlank()) {
                Toast.makeText(context, "⚠️ Please select or enter a WhatsApp Group name", Toast.LENGTH_LONG).show()
                return@saveTaskAction
            }
            repository.saveWhatsAppGroup(grp)
            finalRecipients = listOf("group:$grp")
            finalRecipientNames = listOf(grp)
        } else if (selectedChannel == ChannelType.REMINDER) {
            if (title.isBlank() && messageText.isBlank()) {
                Toast.makeText(context, "⚠️ Please enter a reminder title or note", Toast.LENGTH_LONG).show()
                return@saveTaskAction
            }
            finalRecipients = listOf("Self")
            finalRecipientNames = listOf(title.ifBlank { "Local Device Alarm" })
        } else if (finalPhoneList.isEmpty() && selectedChannel !in listOf(ChannelType.FAKE_CALL, ChannelType.REMINDER, ChannelType.SOUND_MODE)) {
            Toast.makeText(context, "⚠️ Please select at least one recipient", Toast.LENGTH_LONG).show()
            return@saveTaskAction
        } else if (messageText.isBlank() && selectedChannel in listOf(ChannelType.SMS, ChannelType.WHATSAPP, ChannelType.WHATSAPP_BUSINESS, ChannelType.EMAIL)) {
            Toast.makeText(context, "⚠️ Please enter a message to send", Toast.LENGTH_LONG).show()
            return@saveTaskAction
        } else {
            finalRecipients = finalPhoneList
            finalRecipientNames = finalNameList
        }

        if (taskToEdit != null) {
            val updatedTask = taskToEdit.copy(
                title = title.ifEmpty { "${selectedChannel.displayName} to ${finalRecipientNames.firstOrNull() ?: "Self"}" },
                channel = selectedChannel,
                recipients = finalRecipients,
                recipientNames = finalRecipientNames,
                messageText = messageText,
                triggerTimeMillis = triggerTimeMillis,
                recurrence = selectedRecurrence,
                customIntervalMinutes = customIntervalMinutes,
                simSlot = simSlot,
                askBeforeSend = askBeforeSend,
                fakeCallerName = fakeCallerName,
                fakeCallerNumber = fakeCallerNumber,
                fakeCallStyle = fakeCallStyle,
                soundModeSetting = soundModeSetting,
                mediaUri = mediaUri,
                status = TaskStatus.PENDING
            )
            repository.updateTask(updatedTask)
            scheduler.cancelTask(taskToEdit.id)
            scheduler.scheduleTask(updatedTask)
            android.widget.Toast.makeText(context, "Task updated and rescheduled!", android.widget.Toast.LENGTH_SHORT).show()
        } else {
            val newTask = ScheduledTask(
                title = title.ifEmpty { "${selectedChannel.displayName} to ${finalRecipientNames.firstOrNull() ?: "Self"}" },
                channel = selectedChannel,
                recipients = finalRecipients,
                recipientNames = finalRecipientNames,
                messageText = messageText,
                triggerTimeMillis = triggerTimeMillis,
                recurrence = selectedRecurrence,
                customIntervalMinutes = customIntervalMinutes,
                simSlot = simSlot,
                askBeforeSend = askBeforeSend,
                fakeCallerName = fakeCallerName,
                fakeCallerNumber = fakeCallerNumber,
                fakeCallStyle = fakeCallStyle,
                soundModeSetting = soundModeSetting,
                mediaUri = mediaUri
            )
            val insertedId = repository.insertTask(newTask)
            if (insertedId > 0) {
                scheduler.scheduleTask(newTask.copy(id = insertedId))
            }
            android.widget.Toast.makeText(context, "Task scheduled successfully!", android.widget.Toast.LENGTH_SHORT).show()
        }

        onTaskSaved()
    }

    val seqIdFromTask = remember(taskToEdit) {
        if (taskToEdit?.title?.startsWith("[Seq #") == true) {
            val regex = Regex("""\[Seq #(\d+)\]""")
            regex.find(taskToEdit.title)?.groupValues?.getOrNull(1)?.toLongOrNull() ?: 0L
        } else 0L
    }

    var composeModeTab by remember { mutableIntStateOf(if (seqIdFromTask > 0L) 1 else 0) }

    if (composeModeTab == 1) {
        ContactSequenceStudio(
            sequenceId = seqIdFromTask,
            initialContactName = selectedRecipients.firstOrNull()?.name ?: "",
            initialContactPhone = selectedRecipients.firstOrNull()?.phone ?: "",
            onDismiss = onCancel,
            onSaved = onTaskSaved
        )
        return
    }

    Scaffold(
        topBar = {
            ComposeTopHeader(
                selectedChannel = selectedChannel,
                taskToEdit = taskToEdit,
                composeModeTab = composeModeTab,
                onComposeModeTabChanged = { composeModeTab = it },
                onCancel = onCancel
            )
        },
        bottomBar = {
            ComposeBottomBar(
                isEditing = taskToEdit != null,
                onCancel = onCancel,
                onSave = { saveTaskAction() }
            )
        },
        containerColor = BentoGlassTokens.GlassBackground
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Contact Sequence Shortcut Banner
            SequenceShortcutCard(
                onBuildSequence = { composeModeTab = 1 }
            )

            // 1. Channel Selector Bento Card
            ComposeChannelSelector(
                selectedChannel = selectedChannel,
                onSelectChannel = { selectedChannel = it }
            )

            // 2. Task Label & Identifier (For channels other than REMINDER)
            if (selectedChannel != ChannelType.REMINDER) {
                BentoCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = BentoGlassTokens.ShapeLarge
                ) {
                    GlassInputField(
                        value = title,
                        onValueChange = { title = it },
                        label = "Task Label (Optional)",
                        placeholder = "e.g. Birthday Wish, Client Reminder, Weekly Standup",
                        leadingIcon = Icons.AutoMirrored.Filled.Label,
                        accentColor = BentoGlassTokens.ElectricIndigo
                    )
                }
            }

            // 3. Channel Specific Configuration Bento Card
            when (selectedChannel) {
                ChannelType.REMINDER -> {
                    ReminderComposerCard(
                        reminderTitle = title,
                        onReminderTitleChange = { title = it },
                        messageText = messageText,
                        onMessageTextChange = { messageText = it }
                    )
                }
                ChannelType.FAKE_CALL, ChannelType.SOUND_MODE -> {
                    ComposeFakeCallAndSoundCard(
                        selectedChannel = selectedChannel,
                        fakeCallerName = fakeCallerName,
                        onFakeCallerNameChange = { fakeCallerName = it },
                        fakeCallerNumber = fakeCallerNumber,
                        onFakeCallerNumberChange = { fakeCallerNumber = it },
                        fakeCallStyle = fakeCallStyle,
                        onFakeCallStyleChange = { fakeCallStyle = it },
                        soundModeSetting = soundModeSetting,
                        onSoundModeSettingChange = { soundModeSetting = it }
                    )
                }
                ChannelType.WHATSAPP_STATUS -> {
                    WhatsAppStatusComposerCard(
                        mediaUri = mediaUri,
                        onMediaUriChange = { mediaUri = it },
                        messageText = messageText,
                        onMessageTextChange = { messageText = it },
                        targetPackage = statusTargetPackage,
                        onTargetPackageChange = { statusTargetPackage = it },
                        onLaunchMediaPicker = { statusMediaPickerLauncher.launch("*/*") },
                        onOpenTemplates = { showTemplateDialog = true }
                    )
                }
                ChannelType.WHATSAPP_GROUP -> {
                    WhatsAppGroupComposerCard(
                        context = context,
                        repository = repository,
                        selectedGroupName = selectedGroupName,
                        onSelectGroupName = { selectedGroupName = it },
                        recipientsInput = recipientsInput,
                        onRecipientsInputChange = { recipientsInput = it },
                        messageText = messageText,
                        onMessageTextChange = { messageText = it },
                        onOpenGroupsDialog = { showWhatsAppGroupsDialog = true },
                        onOpenTemplates = { showTemplateDialog = true }
                    )
                }
                else -> {
                    ComposeRecipientsAndMessageCard(
                        selectedChannel = selectedChannel,
                        selectedRecipients = selectedRecipients,
                        duplicateWarning = duplicateWarning,
                        onDismissDuplicateWarning = { duplicateWarning = null },
                        onClearAllRecipients = {
                            selectedRecipients = emptyList()
                            duplicateWarning = null
                        },
                        onRemoveRecipient = { idx ->
                            selectedRecipients = selectedRecipients.filterIndexed { i, _ -> i != idx }
                        },
                        recipientsInput = recipientsInput,
                        onRecipientsInputChange = { recipientsInput = it },
                        onAddManualRecipient = {
                            if (recipientsInput.isNotBlank()) {
                                val parts = recipientsInput.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                                parts.forEach { part -> tryAddRecipient("", part) }
                                recipientsInput = ""
                            }
                        },
                        onOpenWhatsAppContacts = { showWhatsAppContactsDialog = true },
                        onOpenPoolPicker = { showPoolPickerDialog = true },
                        onOpenContactsPicker = { contactPickerLauncher.launch(null) },
                        messageText = messageText,
                        onMessageTextChange = { messageText = it },
                        onOpenTemplates = { showTemplateDialog = true },
                        smsPartCount = smsPartCount,
                        isUnicode = isUnicode
                    )
                }
            }

            // 4. Execution Schedule, Repetition, SIM & Safety Bento Cards
            ComposeScheduleOptionsCard(
                selectedChannel = selectedChannel,
                triggerTimeMillis = triggerTimeMillis,
                onTriggerTimeChanged = { triggerTimeMillis = it },
                selectedRecurrence = selectedRecurrence,
                onRecurrenceChanged = { selectedRecurrence = it },
                customIntervalMinutes = customIntervalMinutes,
                onCustomIntervalMinutesChanged = { customIntervalMinutes = it },
                simSlot = simSlot,
                onSimSlotChanged = { simSlot = it },
                askBeforeSend = askBeforeSend,
                onAskBeforeSendChanged = { askBeforeSend = it }
            )

            Spacer(modifier = Modifier.height(12.dp))
        }
    }

    if (showTemplateDialog) {
        TemplatePickerSheet(
            repository = repository,
            initialTemplates = templates,
            onSelectTemplate = { t ->
                messageText = t.content
                showTemplateDialog = false
            },
            onDismiss = { showTemplateDialog = false }
        )
    }

    if (showPoolPickerDialog) {
        ContactPoolPickerSheet(
            repository = repository,
            selectedRecipients = selectedRecipients,
            onAddRecipient = ::tryAddRecipient,
            onDismiss = { showPoolPickerDialog = false }
        )
    }

    if (showWhatsAppContactsDialog) {
        WhatsAppContactsPickerSheet(
            repository = repository,
            selectedRecipients = selectedRecipients,
            onAddRecipient = ::tryAddRecipient,
            onDismiss = { showWhatsAppContactsDialog = false }
        )
    }

    if (showWhatsAppGroupsDialog) {
        WhatsAppGroupsPickerSheet(
            repository = repository,
            selectedGroupName = selectedGroupName,
            onSelectGroup = { selectedGroupName = it },
            onDismiss = { showWhatsAppGroupsDialog = false }
        )
    }
}