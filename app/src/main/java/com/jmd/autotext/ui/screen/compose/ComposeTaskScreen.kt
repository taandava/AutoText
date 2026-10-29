package com.jmd.autotext.ui.screen.compose

import android.content.*
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

import com.jmd.autotext.core.*
import com.jmd.autotext.data.model.*
import com.jmd.autotext.data.prefs.*
import com.jmd.autotext.data.repo.*
import com.jmd.autotext.engine.*
import com.jmd.autotext.scheduler.*
import com.jmd.autotext.service.*
import com.jmd.autotext.ui.components.*
import com.jmd.autotext.ui.model.*
import com.jmd.autotext.ui.screen.reminderComposerCard
import com.jmd.autotext.ui.screen.sequence.*
import com.jmd.autotext.ui.theme.*
import com.jmd.autotext.whatsapp.*

/**
 * Unified task composition screen providing authoring environments for SMS, WhatsApp direct messages,
 * WhatsApp Status updates, WhatsApp Groups, Phone Calls, Fake Calls, and Reminders.
 *
 * @param initialChannel Channel pre-selected upon launching the composer.
 * @param initialMessage Initial text pre-filled into the message body.
 * @param taskToEdit Existing scheduled task if editing; null if drafting a new task.
 * @param onTaskSaved Callback invoked when task is saved and scheduled.
 * @param onDeleteTask Optional callback invoked when user deletes the active task under edit.
 * @param onCancel Callback invoked to abort draft and return to previous view.
 */
@Composable
fun composeTaskScreen(
    initialChannel: ChannelType = ChannelType.SMS,
    initialMessage: String = "",
    taskToEdit: ScheduledTask? = null,
    onTaskSaved: () -> Unit,
    onDeleteTask: ((ScheduledTask) -> Unit)? = null,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val repository = remember { AppRepository(context) }
    val scheduler = remember { AlarmScheduler(context) }

    var selectedChannel by remember(initialChannel, taskToEdit) { mutableStateOf(taskToEdit?.channel ?: initialChannel) }
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
    var messageText by remember {
        mutableStateOf(
            taskToEdit?.messageText ?: initialMessage
        )
    }

    fun tryAddRecipient(name: String, rawPhone: String): Boolean {
        val cleanPhone = rawPhone.trim()
        if (cleanPhone.isBlank()) return false
        val cleanName = name.trim()

        val norm = PhoneNumberNormalizer.normalize(cleanPhone)
        val digits = cleanPhone.filter { it.isDigit() }

        val isDuplicate = selectedRecipients.any { existing ->
            val existingNorm = PhoneNumberNormalizer.normalize(existing.phone)
            (existing.phone.equals(cleanPhone, ignoreCase = true)) ||
            (norm.isNotBlank() && existingNorm.isNotBlank() && norm == existingNorm) ||
            (digits.length >= 10 && existing.phone.filter { it.isDigit() }.endsWith(digits.takeLast(10)))
        }

        if (isDuplicate) {
            val displayName = cleanName.ifBlank { cleanPhone }
            duplicateWarning = "Duplicate Rejected: '$displayName' ($cleanPhone) is already added!"
            context.showAppToast("Duplicate Rejected: '$displayName' ($cleanPhone) is already selected!", isLong = true)
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
                val mimeType = contentResolver.getType(uri).orEmpty().lowercase()
                val isSupported = mimeType.startsWith("image/") || mimeType.startsWith("video/") ||
                        uri.toString().endsWith(".jpg", ignoreCase = true) ||
                        uri.toString().endsWith(".jpeg", ignoreCase = true) ||
                        uri.toString().endsWith(".png", ignoreCase = true) ||
                        uri.toString().endsWith(".webp", ignoreCase = true) ||
                        uri.toString().endsWith(".mp4", ignoreCase = true)
                if (!isSupported && mimeType.isNotBlank()) {
                    context.showAppToast("Unsupported file type ($mimeType). Only images and videos are supported.")
                    return@rememberLauncherForActivityResult
                }
                val ext = when {
                    mimeType.contains("mp4") || mimeType.contains("video") -> "mp4"
                    mimeType.contains("png") -> "png"
                    mimeType.contains("webp") -> "webp"
                    else -> "jpg"
                }
                val mediaDir = File(context.filesDir, "media_attachments").apply { mkdirs() }
                val destFile = File(mediaDir, "media_${System.currentTimeMillis()}.$ext")
                contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output)
                    }
                }
                val contentUri = androidx.core.content.FileProvider.getUriForFile(
                    context,
                    "com.jmd.autotext.fileprovider",
                    destFile
                )
                mediaUri = contentUri.toString()
                context.showAppToast("Media attached: ${destFile.name}")
            } catch (e: Exception) {
                Log.e("ComposeScreen", "Failed to cache media attachment: ${e.message}", e)
                context.showAppToast("Failed to copy attachment: ${e.localizedMessage ?: "file inaccessible"}")
            }
        }
    }

    // Contact Picker Launcher from System Contacts
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
                context.showAppToast("Error reading contact: ${e.message}")
            }
        }
    }

    val callPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            context.showAppToast("Call permission granted")
        } else {
            context.showAppToast("Call permission needed", isLong = true)
        }
    }

    LaunchedEffect(selectedChannel) {
        if (selectedChannel == ChannelType.CALL) {
            if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
                callPermissionLauncher.launch(android.Manifest.permission.CALL_PHONE)
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
    var selectedDaysOfWeek by remember { mutableStateOf(taskToEdit?.selectedDaysOfWeek ?: emptyList()) }
    var customIntervalMinutes by remember { mutableIntStateOf(taskToEdit?.customIntervalMinutes ?: 60) }
    val prefs = remember { PreferenceManager(context) }
    var simSlot by remember { mutableIntStateOf(taskToEdit?.simSlot ?: prefs.defaultSimSlot) }

    // Fake call specific
    var fakeCallerName by remember { mutableStateOf(taskToEdit?.fakeCallerName ?: "") }
    var fakeCallerNumber by remember { mutableStateOf(taskToEdit?.fakeCallerNumber ?: "") }
    var fakeCallStyle by remember { mutableStateOf(taskToEdit?.fakeCallStyle ?: "PIXEL") }

    // Phone / WhatsApp call specific
    var callDurationSeconds by remember { mutableIntStateOf(taskToEdit?.callDurationSeconds ?: 0) }
    var includeDialTimeInCallDuration by remember { mutableStateOf(taskToEdit?.includeDialTimeInCallDuration ?: false) }
    var callSequenceMode by remember { mutableStateOf(taskToEdit?.callSequenceMode ?: "GIVEN") }
    var delayBetweenCallsSeconds by remember { mutableIntStateOf(taskToEdit?.delayBetweenCallsSeconds ?: 5) }
    var callFailurePolicy by remember { mutableStateOf(taskToEdit?.callFailurePolicy ?: "CONTINUE") }

    // Sound mode specific
    var soundModeSetting by remember { mutableStateOf(taskToEdit?.soundModeSetting ?: "SILENT") }

    // WhatsApp Specific States
    var showWhatsAppContactsDialog by remember { mutableStateOf(false) }
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

    val saveTaskAction: () -> Unit = saveTaskAction@ {
        fun validateContentOrShowToast(channel: ChannelType, text: String, media: String): Boolean {
            val validation = TaskContentValidator.validateContent(
                context = context,
                channel = channel,
                messageText = text,
                mediaUri = media
            )
            return when (validation) {
                is ContentValidationResult.Reject -> {
                    context.showAppToast(validation.reason, isLong = true)
                    false
                }
                is ContentValidationResult.AcceptMediaWithCaption -> {
                    Log.d("ComposeTaskScreen", "Accepted media with caption (len=${validation.caption.length}): ${validation.mimeType}, video=${validation.isVideo}")
                    true
                }
                is ContentValidationResult.AcceptMediaOnly -> {
                    Log.d("ComposeTaskScreen", "Accepted media only: ${validation.mimeType}, video=${validation.isVideo}")
                    true
                }
                is ContentValidationResult.AcceptTextOnly -> {
                    Log.d("ComposeTaskScreen", "Accepted text only payload")
                    true
                }
            }
        }

        // Ingest any text remaining in manual input if not status or group
        if (selectedChannel != ChannelType.WHATSAPP_STATUS && selectedChannel != ChannelType.WHATSAPP_BUSINESS_STATUS && selectedChannel != ChannelType.WHATSAPP_GROUP) {
            if (recipientsInput.isNotBlank()) {
                val parsed = PhoneNumberNormalizer.parseMultiInput(recipientsInput)
                if (parsed.isNotEmpty()) {
                    parsed.forEach { (raw, norm) -> tryAddRecipient("", norm ?: raw) }
                } else {
                    recipientsInput.split(",", "\n", ";").map { it.trim() }.filter { it.isNotEmpty() }.forEach {
                        tryAddRecipient("", it)
                    }
                }
                recipientsInput = ""
            }
        }

        val finalPhoneList = selectedRecipients.map { it.phone }
        val finalNameList = selectedRecipients.map { it.name.ifBlank { it.phone } }

        val finalRecipients: List<String>
        val finalRecipientNames: List<String>

        if (selectedChannel == ChannelType.WHATSAPP_STATUS) {
            if (!validateContentOrShowToast(selectedChannel, messageText, mediaUri)) return@saveTaskAction
            finalRecipients = listOf("My Status")
            finalRecipientNames = listOf("WhatsApp Status")
        } else if (selectedChannel == ChannelType.WHATSAPP_BUSINESS_STATUS) {
            if (!validateContentOrShowToast(selectedChannel, messageText, mediaUri)) return@saveTaskAction
            finalRecipients = listOf("My Status (w4b)")
            finalRecipientNames = listOf("WhatsApp Business Status")
        } else if (selectedChannel == ChannelType.WHATSAPP_GROUP) {
            val grp = if (selectedGroupName.isNotBlank()) selectedGroupName.trim()
                      else if (recipientsInput.isNotBlank()) recipientsInput.trim()
                      else selectedRecipients.firstOrNull()?.name ?: ""
            if (grp.isBlank()) {
                context.showAppToast("Select group name", isLong = true)
                return@saveTaskAction
            }
            if (!validateContentOrShowToast(selectedChannel, messageText, mediaUri)) return@saveTaskAction
            repository.saveWhatsAppGroup(grp)
            finalRecipients = listOf("group:$grp")
            finalRecipientNames = listOf(grp)
        } else if (selectedChannel == ChannelType.REMINDER) {
            if (title.isBlank() && messageText.isBlank()) {
                context.showAppToast("Enter reminder title", isLong = true)
                return@saveTaskAction
            }
            finalRecipients = listOf("Self")
            finalRecipientNames = listOf(title.ifBlank { "Local Device Alarm" })
        } else if (finalPhoneList.isEmpty() && selectedChannel !in listOf(ChannelType.FAKE_CALL, ChannelType.REMINDER, ChannelType.SOUND_MODE)) {
            context.showAppToast("Select a recipient", isLong = true)
            return@saveTaskAction
        } else {
            // Centralized content validation (text or media) for messaging channels
            if (selectedChannel in listOf(ChannelType.SMS, ChannelType.WHATSAPP, ChannelType.WHATSAPP_BUSINESS)) {
                if (!validateContentOrShowToast(selectedChannel, messageText, mediaUri)) return@saveTaskAction
            }
            finalRecipients = finalPhoneList
            finalRecipientNames = finalNameList
        }

        if (selectedChannel == ChannelType.CALL) {
            if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
                callPermissionLauncher.launch(android.Manifest.permission.CALL_PHONE)
            }
        }

        val effectiveRecurrence = selectedRecurrence
        val effectiveInterval = if (effectiveRecurrence != RecurrenceType.ONCE) {
            if (customIntervalMinutes > 0) customIntervalMinutes else 1
        } else {
            0
        }

        if (taskToEdit != null) {
            val updatedTask = taskToEdit.copy(
                title = title.ifEmpty { "${selectedChannel.displayName} to ${finalRecipientNames.firstOrNull() ?: "Self"}" },
                channel = selectedChannel,
                recipients = finalRecipients,
                recipientNames = finalRecipientNames,
                messageText = messageText,
                triggerTimeMillis = triggerTimeMillis,
                recurrence = effectiveRecurrence,
                customIntervalMinutes = effectiveInterval,
                selectedDaysOfWeek = selectedDaysOfWeek,
                simSlot = simSlot,
                askBeforeSend = false,
                fakeCallerName = fakeCallerName,
                fakeCallerNumber = fakeCallerNumber,
                fakeCallStyle = fakeCallStyle,
                soundModeSetting = soundModeSetting,
                mediaUri = mediaUri,
                callDurationSeconds = callDurationSeconds,
                includeDialTimeInCallDuration = includeDialTimeInCallDuration,
                callSequenceMode = callSequenceMode,
                delayBetweenCallsSeconds = delayBetweenCallsSeconds,
                callFailurePolicy = callFailurePolicy,
                status = TaskStatus.PENDING
            )
            val updated = repository.updateTask(updatedTask)
            if (updated) {
                // Invalidate any past delivery tracking records for this task so edits/changes take effect cleanly
                repository.clearDeliveriesForTask(taskToEdit.id)
                scheduler.cancelTask(taskToEdit.id)
                scheduler.scheduleTask(updatedTask)
                context.showAppToast("Task rescheduled")
            } else {
                context.showAppToast("Failed to update task in database", isLong = true)
                return@saveTaskAction
            }
        } else {
            val newTask = ScheduledTask(
                title = title.ifEmpty { "${selectedChannel.displayName} to ${finalRecipientNames.firstOrNull() ?: "Self"}" },
                channel = selectedChannel,
                recipients = finalRecipients,
                recipientNames = finalRecipientNames,
                messageText = messageText,
                triggerTimeMillis = triggerTimeMillis,
                recurrence = effectiveRecurrence,
                customIntervalMinutes = effectiveInterval,
                selectedDaysOfWeek = selectedDaysOfWeek,
                simSlot = simSlot,
                askBeforeSend = false,
                fakeCallerName = fakeCallerName,
                fakeCallerNumber = fakeCallerNumber,
                fakeCallStyle = fakeCallStyle,
                soundModeSetting = soundModeSetting,
                mediaUri = mediaUri,
                callDurationSeconds = callDurationSeconds,
                includeDialTimeInCallDuration = includeDialTimeInCallDuration,
                callSequenceMode = callSequenceMode,
                delayBetweenCallsSeconds = delayBetweenCallsSeconds,
                callFailurePolicy = callFailurePolicy
            )
            val insertedId = repository.insertTask(newTask)
            if (insertedId > 0) {
                scheduler.scheduleTask(newTask.copy(id = insertedId))
                context.showAppToast("Task scheduled")
            } else {
                context.showAppToast("Failed to save task to database", isLong = true)
                return@saveTaskAction
            }
        }

        onTaskSaved()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
            composeTopHeader(
                selectedChannel = selectedChannel,
                taskToEdit = taskToEdit,
                onCancel = onCancel
            )
        },
        bottomBar = {
            composeBottomBar(
                isEditing = taskToEdit != null,
                onCancel = onCancel,
                onSave = { saveTaskAction() },
                onDelete = if (taskToEdit != null && onDeleteTask != null) { { onDeleteTask(taskToEdit) } } else null
            )
        },
        containerColor = MaterialTheme.colorScheme.background
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
            // Channel Selector is omitted when channel is explicitly chosen/locked
            // The top bar badge already clearly indicates the active channel.

            // 2. Task Label & Identifier (For channels other than REMINDER)
            if (selectedChannel != ChannelType.REMINDER) {
                appCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = AppDesignTokens.ShapeLarge
                ) {
                    glassInputField(
                        value = title,
                        onValueChange = { title = it },
                        label = "Task Label (Optional)",
                        placeholder = "e.g. Birthday Wish, Client Reminder, Weekly Standup",
                        leadingIcon = Icons.AutoMirrored.Filled.Label,
                        accentColor = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // 3. Channel Specific Configuration Enterprise Card
            when (selectedChannel) {
                ChannelType.REMINDER -> {
                    reminderComposerCard(
                        reminderTitle = title,
                        onReminderTitleChange = { title = it },
                        messageText = messageText,
                        onMessageTextChange = { messageText = it }
                    )
                }
                ChannelType.FAKE_CALL, ChannelType.SOUND_MODE -> {
                    composeFakeCallAndSoundCard(
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
                ChannelType.CALL, ChannelType.WHATSAPP_CALL, ChannelType.WHATSAPP_BUSINESS_CALL -> {
                    composePhoneCallCard(
                        selectedChannel = selectedChannel,
                        onSelectChannel = { selectedChannel = it },
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
                                val parsed = PhoneNumberNormalizer.parseMultiInput(recipientsInput)
                                if (parsed.isNotEmpty()) {
                                    parsed.forEach { (raw, norm) -> tryAddRecipient("", norm ?: raw) }
                                } else {
                                    val parts = recipientsInput.split(",", "\n", ";").map { it.trim() }.filter { it.isNotEmpty() }
                                    parts.forEach { part -> tryAddRecipient("", part) }
                                }
                                recipientsInput = ""
                            }
                        },
                        onOpenPoolPicker = { showPoolPickerDialog = true },
                        onOpenContactsPicker = { contactPickerLauncher.launch(null) },
                        onOpenWhatsAppContacts = { showWhatsAppContactsDialog = true },
                        onOpenWhatsAppPicker = {
                            val targetPkg = if (selectedChannel == ChannelType.WHATSAPP_BUSINESS_CALL) WhatsAppPackageHelper.PACKAGE_WHATSAPP_BUSINESS else WhatsAppPackageHelper.PACKAGE_WHATSAPP
                            AutoTextAccessibilityService.startManualChatPicker(context, targetPackage = targetPkg, channel = selectedChannel) { chatInfo ->
                                val clean = chatInfo.title
                                val phone = chatInfo.phoneOrId ?: repository.findPhoneForContactName(clean) ?: clean
                                val digits = phone.filter { it.isDigit() }
                                if (digits.length >= 7) {
                                    tryAddRecipient(clean, phone)
                                } else {
                                    context.showAppToast("Calls require a contact with a valid phone number")
                                }
                            }
                        },
                        callDurationSeconds = callDurationSeconds,
                        onCallDurationChange = { callDurationSeconds = it },
                        includeDialTime = includeDialTimeInCallDuration,
                        onIncludeDialTimeChange = { includeDialTimeInCallDuration = it },
                        callSequenceMode = callSequenceMode,
                        onCallSequenceModeChange = { callSequenceMode = it },
                        delayBetweenCallsSeconds = delayBetweenCallsSeconds,
                        onDelayBetweenCallsChange = { delayBetweenCallsSeconds = it },
                        callFailurePolicy = callFailurePolicy,
                        onCallFailurePolicyChange = { callFailurePolicy = it }
                    )
                }
                ChannelType.WHATSAPP_STATUS -> {
                    whatsAppStatusComposerCard(
                        mediaUri = mediaUri,
                        onMediaUriChange = { mediaUri = it },
                        messageText = messageText,
                        onMessageTextChange = { messageText = it },
                        onLaunchMediaPicker = { statusMediaPickerLauncher.launch("*/*") },
                        onOpenTemplates = { showTemplateDialog = true }
                    )
                }
                ChannelType.WHATSAPP_BUSINESS_STATUS -> {
                    whatsAppBusinessStatusComposerCard(
                        mediaUri = mediaUri,
                        onMediaUriChange = { mediaUri = it },
                        messageText = messageText,
                        onMessageTextChange = { messageText = it },
                        onLaunchMediaPicker = { statusMediaPickerLauncher.launch("*/*") },
                        onOpenTemplates = { showTemplateDialog = true }
                    )
                }
                ChannelType.WHATSAPP_GROUP -> {
                    whatsAppGroupComposerCard(
                        context = context,
                        repository = repository,
                        selectedGroupName = selectedGroupName,
                        onSelectGroupName = { selectedGroupName = it },
                        messageText = messageText,
                        onMessageTextChange = { messageText = it },
                        onOpenTemplates = { showTemplateDialog = true },
                        mediaUri = mediaUri,
                        onMediaUriChange = { mediaUri = it },
                        onLaunchMediaPicker = { statusMediaPickerLauncher.launch("*/*") }
                    )
                }
                else -> {
                    composeRecipientsAndMessageCard(
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
                                val parsed = PhoneNumberNormalizer.parseMultiInput(recipientsInput)
                                if (parsed.isNotEmpty()) {
                                    parsed.forEach { (raw, norm) -> tryAddRecipient("", norm ?: raw) }
                                } else {
                                    val parts = recipientsInput.split(",", "\n", ";").map { it.trim() }.filter { it.isNotEmpty() }
                                    parts.forEach { part -> tryAddRecipient("", part) }
                                }
                                recipientsInput = ""
                            }
                        },
                        onOpenWhatsAppContacts = { showWhatsAppContactsDialog = true },
                        onAddDirectRecipient = { name, phone -> tryAddRecipient(name, phone) },
                        onOpenPoolPicker = { showPoolPickerDialog = true },
                        onOpenContactsPicker = { contactPickerLauncher.launch(null) },
                        messageText = messageText,
                        onMessageTextChange = { messageText = it },
                        onOpenTemplates = { showTemplateDialog = true },
                        smsPartCount = smsPartCount,
                        isUnicode = isUnicode,
                        mediaUri = mediaUri,
                        onMediaUriChange = { mediaUri = it },
                        onLaunchMediaPicker = { statusMediaPickerLauncher.launch("*/*") }
                    )
                }
            }

            // 4. Execution Schedule, Repetition, SIM & Safety Enterprise Cards
            composeScheduleOptionsCard(
                selectedChannel = selectedChannel,
                triggerTimeMillis = triggerTimeMillis,
                onTriggerTimeChanged = { triggerTimeMillis = it },
                selectedRecurrence = selectedRecurrence,
                onRecurrenceChanged = { selectedRecurrence = it },
                customIntervalMinutes = customIntervalMinutes,
                onCustomIntervalMinutesChanged = { customIntervalMinutes = it },
                simSlot = simSlot,
                onSimSlotChanged = { simSlot = it },
                selectedDaysOfWeek = selectedDaysOfWeek,
                onSelectedDaysOfWeekChanged = { selectedDaysOfWeek = it }
            )

            Spacer(modifier = Modifier.height(12.dp))
        }
    }

    if (showTemplateDialog) {
        templatePickerSheet(
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
        contactPoolPickerSheet(
            repository = repository,
            selectedRecipients = selectedRecipients,
            onAddRecipient = ::tryAddRecipient,
            onCommitRecipients = { selectedRecipients = it },
            onDismiss = { showPoolPickerDialog = false }
        )
    }

    if (showWhatsAppContactsDialog) {
        whatsAppContactsPickerSheet(
            repository = repository,
            selectedRecipients = selectedRecipients,
            selectedChannel = selectedChannel,
            onAddRecipient = ::tryAddRecipient,
            onCommitRecipients = { selectedRecipients = it },
            onDismiss = { showWhatsAppContactsDialog = false }
        )
    }
    }
}
