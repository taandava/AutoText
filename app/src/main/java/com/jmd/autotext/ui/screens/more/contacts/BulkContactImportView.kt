package com.jmd.autotext.ui.screens.more.contacts

import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jmd.autotext.data.AppRepository
import com.jmd.autotext.ui.components.*
import com.jmd.autotext.ui.theme.BentoGlassTokens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BulkContactImportView(
    repository: AppRepository,
    onPickCsv: () -> Unit,
    onBack: () -> Unit,
    onImportSuccess: () -> Unit
) {
    val context = LocalContext.current
    var pasteText by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Import Contacts List", fontWeight = FontWeight.Bold, color = BentoGlassTokens.TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
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
                Text("Import Contacts from CSV or Text", fontWeight = FontWeight.Bold, color = BentoGlassTokens.TextPrimary, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Supported: CSV files (.csv, .txt) with Name & Phone Number.\nDelimiters supported: comma (,), semicolon (;), or tab. Header row auto-detected.",
                    fontSize = 11.5.sp,
                    color = BentoGlassTokens.TextMuted,
                    lineHeight = 16.sp
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                GlassButton(
                    text = "Pick CSV File",
                    icon = Icons.Default.FolderOpen,
                    onClick = onPickCsv,
                    variant = GlassButtonVariant.PRIMARY,
                    modifier = Modifier.weight(1f)
                )
                GlassButton(
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

            GlassInputField(
                value = pasteText,
                onValueChange = { pasteText = it },
                label = "Paste Contacts / CSV Content",
                placeholder = "Rahul Sharma, +919876543210\nPriya, +919123456789\n...",
                leadingIcon = Icons.Default.Group,
                singleLine = false,
                minLines = 6,
                maxLines = 10,
                accentColor = BentoGlassTokens.VividEmerald
            )

            GlassButton(
                text = "Import Pasted Text Now",
                onClick = {
                    val count = repository.importContactsFromCsv(pasteText)
                    if (count > 0) {
                        Toast.makeText(context, "Added $count contacts successfully!", Toast.LENGTH_SHORT).show()
                        pasteText = ""
                        onImportSuccess()
                    } else {
                        Toast.makeText(context, "No valid contacts found in pasted text", Toast.LENGTH_SHORT).show()
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
