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

// ----------------- HELPER: READ BUNDLED DOCUMENTATION ASSET -----------------
fun readDocumentationAsset(context: Context): String {
    return try {
        context.assets.open("AUTOTEXT_FEATURES_AND_LOGIC.md").bufferedReader().use { it.readText() }
    } catch (e: Exception) {
        "AutoText Features and Logic Documentation\n\nError reading bundled asset: ${e.message}"
    }
}

// ----------------- SUB SCREEN: SPECIFICATIONS & LOGIC MANUAL -----------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentationView(
    context: Context,
    onBack: () -> Unit
) {
    val fullDocText = remember { readDocumentationAsset(context) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("ALL") }

    val categories = listOf(
        "ALL" to "All Sections",
        "1." to "1. Executive Summary",
        "2." to "2. Architecture & Data",
        "3." to "3. Scheduled Tasks",
        "4." to "4. Daily Sender & TRAI",
        "5." to "5. WhatsApp & Auto-Unlock",
        "6." to "6. Auto-Reply Engine",
        "7." to "7. Silent Watchdog",
        "8." to "8. Security & Privacy",
        "9." to "9. Troubleshooting"
    )

    val rawSections = remember(fullDocText) {
        fullDocText.split("\n## ").mapIndexed { index, s ->
            if (index == 0) s else "## $s"
        }
    }

    val filteredSections = remember(rawSections, searchQuery, selectedCategory) {
        rawSections.filter { sec ->
            val matchesCategory = when (selectedCategory) {
                "ALL" -> true
                else -> sec.contains(selectedCategory)
            }
            val matchesSearch = if (searchQuery.isBlank()) {
                true
            } else {
                sec.contains(searchQuery, ignoreCase = true)
            }
            matchesCategory && matchesSearch
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Feature & Logic Manual", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = BentoGlassTokens.TextPrimary)
                        Text("Bundled in APK (AUTOTEXT_FEATURES_AND_LOGIC.md)", fontSize = 11.sp, color = BentoGlassTokens.TextMuted)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = BentoGlassTokens.TextPrimary)
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, "AutoText - Complete Specifications & Logic Guide")
                            putExtra(Intent.EXTRA_TEXT, fullDocText)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Share AutoText Documentation"))
                    }) {
                        Icon(Icons.Default.Share, contentDescription = "Share", tint = BentoGlassTokens.NeonCyan)
                    }
                    IconButton(onClick = {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("AutoText Guide", fullDocText))
                        Toast.makeText(context, "Full guide copied to clipboard", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = BentoGlassTokens.RadiantAmber)
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
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            GlassSearchField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = "Search specifications, 100 SMS, PIN, A11y...",
                leadingIcon = Icons.Default.Search,
                onClear = { searchQuery = "" },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                categories.forEach { (prefix, label) ->
                    GlassChip(
                        selected = selectedCategory == prefix,
                        onClick = { selectedCategory = prefix },
                        label = label,
                        activeColor = BentoGlassTokens.NeonCyan
                    )
                }
            }

            if (filteredSections.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    BentoEmptyState(
                        icon = Icons.Default.Info,
                        title = "No Matching Sections",
                        description = "No specifications match '$searchQuery'"
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredSections) { sec ->
                        val lines = sec.lines()
                        val title = lines.firstOrNull { it.startsWith("#") }?.replace("#", "")?.trim() ?: "Overview"
                        val body = lines.filterNot { it.startsWith("#") }.joinToString("\n").trim()

                        BentoCard(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    title,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = BentoGlassTokens.NeonCyan,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = {
                                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        cm.setPrimaryClip(ClipData.newPlainText(title, sec))
                                        Toast.makeText(context, "Section copied", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy Section", tint = BentoGlassTokens.TextMuted, modifier = Modifier.size(15.dp))
                                }
                            }

                            if (body.isNotBlank()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    body,
                                    fontSize = 12.sp,
                                    color = BentoGlassTokens.TextPrimary,
                                    lineHeight = 18.sp,
                                    fontFamily = if (body.contains("adb shell") || body.contains("fun ") || body.contains("val ")) FontFamily.Monospace else FontFamily.Default
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
