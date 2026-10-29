

/**
 * Package: com.jmd.autotext.ui.screen
 * Module: UI In-App Documentation Screen Layer
 *
 * In-app interactive documentation, feature guides, troubleshooting references,
 * and automation architecture explanations for users.
 *
 * Linked With:
 * - Host: `MainAppHost`
 * - Components: `GlassComponentsKt`
 */
package com.jmd.autotext.ui.screen

import android.content.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import com.jmd.autotext.core.*
import com.jmd.autotext.ui.components.*
import com.jmd.autotext.ui.theme.*

/**
 * Reads bundled markdown documentation asset (`AUTOTEXT_FEATURES_AND_LOGIC.md`) from application assets.
 *
 * @param context Active Android [Context].
 * @return Entire markdown document string, or fallback message if asset is unreadable.
 */
fun readDocumentationAsset(context: Context): String {
    return try {
        context.assets.open("AUTOTEXT_FEATURES_AND_LOGIC.md").bufferedReader().use { it.readText() }
    } catch (e: Exception) {
        "AutoText Features and Logic Documentation\n\nError reading bundled asset: ${e.message}"
    }
}

// ----------------- SUB SCREEN: SPECIFICATIONS & LOGIC MANUAL -----------------

/**
 * In-app interactive documentation screen featuring full-text search, topic chapter filters,
 * collapsible section cards, and code block formatting.
 *
 * @param context Active Android [Context].
 * @param onBack Callback invoked when user exits documentation.
 */
@Composable
fun documentationView(
    context: Context,
    onBack: () -> Unit,
) {
    val fullDocText = remember { readDocumentationAsset(context) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("ALL") }

    val categories = listOf(
        "ALL" to "All Sections",
        "## 1. " to "1. System Vision",
        "## 2. " to "2. Codebase Layout",
        "## 3. " to "3. Database Schema",
        "## 4. " to "4. Multi-Channel Dispatch",
        "## 5. " to "5. Screen Unlock & Auto-Lock",
        "## 6. " to "6. Personalization Engine",
        "## 7. " to "7. Contact Sequence Studio",
        "## 8. " to "8. Smart Auto-Reply",
        "## 9. " to "9. Silent Watchdog",
        "## 10. " to "10. Design System",
        "## 11. " to "11. Permissions & Admin",
        "## 12. " to "12. Build System",
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
            val matchesSearch = searchQuery.isBlank() || sec.contains(searchQuery, ignoreCase = true)
            matchesCategory && matchesSearch
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            appSubScreenHeader(
                title = "Feature & Logic Manual",
                subtitle = "Bundled specifications guide",
                onBack = onBack,
                actions = {
                    IconButton(
                        onClick = {
                            val shareIntent = Intent(Intent.ACTION_SEND)
                            shareIntent.type = "text/plain"
                            shareIntent.putExtra(Intent.EXTRA_SUBJECT, "AutoText - Complete Specifications & Logic Guide")
                            shareIntent.putExtra(Intent.EXTRA_TEXT, fullDocText)
                            context.startActivity(Intent.createChooser(shareIntent, "Share AutoText Documentation"))
                        },
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Share", tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(
                        onClick = {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            com.jmd.autotext.compat.ClipboardCompat.setPrimaryClip(cm, ClipData.newPlainText("AutoText Guide", fullDocText))
                            context.showAppToast("Guide copied")
                        },
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            )
        },
        containerColor = AppDesignTokens.VoidBackground
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            glassSearchField(
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
                    glassChip(
                        selected = selectedCategory == prefix,
                        onClick = { selectedCategory = prefix },
                        label = label,
                        activeColor = MaterialTheme.colorScheme.primary
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
                    appEmptyState(
                        icon = Icons.Default.Info,
                        title = "No Matching Sections",
                        description = "No specifications match '$searchQuery'"
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredSections) { sec ->
                        val lines = sec.lines()
                        val title = lines.firstOrNull { it.startsWith("#") }?.replace("#", "")?.trim() ?: "Overview"
                        val body = lines.asSequence().filterNot { it.startsWith("#") }.joinToString("\n").trim()

                        appCard(
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
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = {
                                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        com.jmd.autotext.compat.ClipboardCompat.setPrimaryClip(cm, ClipData.newPlainText(title, sec))
                                        context.showAppToast("Section copied")
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy Section", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(15.dp))
                                }
                            }

                            if (body.isNotBlank()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    body,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
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


