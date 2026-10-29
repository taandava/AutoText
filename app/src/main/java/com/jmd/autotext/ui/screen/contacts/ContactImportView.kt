/**
 * Package: com.jmd.autotext.ui.screen.contacts
 * Module: UI Bulk Contact Import View
 *
 * Bulk contact ingestion screen supporting multi-line pasting and CSV file selection.
 * Automatically parses delimited tokens (names, mobile numbers, international formats)
 * using [com.jmd.autotext.core.PhoneNumberNormalizer] and batch-inserts valid contacts into database.
 */
package com.jmd.autotext.ui.screen.contacts
import androidx.compose.material3.MaterialTheme

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jmd.autotext.core.showAppToast
import com.jmd.autotext.data.repo.AppRepository
import com.jmd.autotext.ui.components.*
import com.jmd.autotext.ui.theme.AppDesignTokens

/**
 * Bulk contact ingestion screen supporting multi-line pasting and CSV file selection.
 *
 * @param repository Data layer repository for batch persistence.
 * @param onPickCsv Callback launching Android SAF document picker for CSV files.
 * @param onBack Callback invoked to cancel import.
 * @param onImportSuccess Callback invoked following successful batch insertion.
 */
@Composable
fun bulkContactImportView(
    repository: AppRepository,
    onPickCsv: () -> Unit,
    onBack: () -> Unit,
    onImportSuccess: () -> Unit
) {
    val context = LocalContext.current
    var pasteText by remember { mutableStateOf("") }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            appSubScreenHeader(
                title = "Import Contacts List",
                subtitle = "Batch paste and normalization",
                onBack = onBack
            )
        },
        containerColor = AppDesignTokens.VoidBackground
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 14.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            appCard(modifier = Modifier.fillMaxWidth()) {
                Text("Import Contacts from CSV or Text", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Supported: CSV files (.csv, .txt) with Name & Phone Number.\nDelimiters supported: comma (,), semicolon (;), or tab. Header row auto-detected.",
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                glassButton(
                    text = "Pick CSV File",
                    icon = Icons.Default.FolderOpen,
                    onClick = onPickCsv,
                    variant = GlassButtonVariant.PRIMARY,
                    modifier = Modifier.weight(1f)
                )
                glassButton(
                    text = "Paste Clipboard",
                    icon = Icons.Default.ContentPaste,
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val text = clipboard.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
                        if (text.isNotBlank()) pasteText = text
                    },
                    variant = GlassButtonVariant.SECONDARY,
                    modifier = Modifier.weight(1f)
                )
            }

            glassInputField(
                value = pasteText,
                onValueChange = { pasteText = it },
                label = "Paste Contacts / CSV Content",
                placeholder = "Rahul Sharma, +919876543210\nPriya, +919123456789\n...",
                leadingIcon = Icons.Default.Group,
                singleLine = false,
                minLines = 6,
                maxLines = 10,
                accentColor = MaterialTheme.colorScheme.tertiary
            )

            glassButton(
                text = "Import Pasted Text Now",
                onClick = {
                    val count = repository.importContactsFromCsv(pasteText)
                    if (count > 0) {
                        context.showAppToast("Added $count contacts")
                        pasteText = ""
                        onImportSuccess()
                    } else {
                        context.showAppToast("No valid contacts found")
                    }
                },
                icon = Icons.Default.UploadFile,
                variant = GlassButtonVariant.SUCCESS,
                enabled = pasteText.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
