package com.jmd.superapp.ui.screen.settings

import android.content.Context
import android.content.Intent
import com.jmd.superapp.core.AccessibilityHealthManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jmd.superapp.core.ProtectedApplicationManager
import com.jmd.superapp.core.showAppToast
import com.jmd.superapp.data.prefs.PreferenceManager
import com.jmd.superapp.ui.components.AppButton
import com.jmd.superapp.ui.components.AppButtonVariant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A launchable app the owner can add to the protected list. */
private data class InstalledApp(val packageName: String, val label: String)

/** Heuristic used only to pre-tick obvious banking/UPI apps; the owner can change every row. */
private val BANKING_HINTS = listOf(
    "bank", "upi", "payment", "wallet", "finance", "fintech", "paytm", "phonepe",
    "gpay", "google pay", "sbi", "hdfc", "icici", "axis", "kotak", "bhim", "cred",
    "navi", "jupiter", "fi money", "amazon pay", "mobikwik", "freecharge", "slice"
)

/**
 * What it is:
 * Searchable picker of every launchable app, persisted as the protected banking list.
 *
 * What it does:
 * Loads installed launcher apps, lets the owner tick/untick them (with a one-tap suggested
 * selection) and pushes the result into [PreferenceManager] and [ProtectedApplicationManager]
 * so automation and overlays skip those packages immediately.
 */
@Composable
fun ProtectedAppsSection(
    context: Context,
    prefs: PreferenceManager,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(true) }
    var query by remember { mutableStateOf("") }
    var apps by remember { mutableStateOf<List<InstalledApp>>(emptyList()) }
    var selected by remember { mutableStateOf(prefs.protectedPackages) }

    fun persist(updated: Set<String>) {
        selected = updated
        prefs.protectedPackages = updated
        ProtectedApplicationManager.setUserProtectedPackages(updated)
        prefs.isBankingShieldEnabled = true
        prefs.isSmartAccessibilityScopingEnabled = true
        prefs.isDynamicA11yOnDemandEnabled = false
    }

    LaunchedEffect(Unit) {
        if (apps.isEmpty()) {
            val loadedApps = withContext(Dispatchers.IO) {
                val pm = context.packageManager
                val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                runCatching {
                    pm.queryIntentActivities(launcher, 0)
                        .mapNotNull { info ->
                            val pkg = info.activityInfo?.packageName ?: return@mapNotNull null
                            if (pkg == context.packageName) return@mapNotNull null
                            InstalledApp(pkg, info.loadLabel(pm).toString().ifBlank { pkg })
                        }
                        .distinctBy { it.packageName }
                        .sortedBy { it.label.lowercase() }
                }.getOrDefault(emptyList())
            }
            apps = loadedApps

            // Intelligent mechanism: automatically detect and pre-protect banking & UPI apps
            if (selected.isEmpty() && loadedApps.isNotEmpty()) {
                val autoSuggested = loadedApps.filter { app ->
                    val haystack = "${app.label} ${app.packageName}".lowercase()
                    BANKING_HINTS.any { haystack.contains(it) } || ProtectedApplicationManager.KNOWN_PROTECTED_PACKAGES.contains(app.packageName)
                }.map { it.packageName }.toSet()
                if (autoSuggested.isNotEmpty()) {
                    persist(autoSuggested)
                }
            }
        }
    }

    val visibleApps = remember(apps, query) {
        val needle = query.trim().lowercase()
        if (needle.isBlank()) apps else apps.filter {
            it.label.lowercase().contains(needle) || it.packageName.lowercase().contains(needle)
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Shield,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Protected (${selected.size})",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "Select banking, UPI, and financial apps. Super safely isolates them completely.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                )
            }
            Icon(
                imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = if (expanded) "Collapse app list" else "Expand app list",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }

        if (expanded) {
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Search Apps") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))
            AppButton(
                text = "Select Suggested",
                onClick = {
                    val suggested = apps.filter { app ->
                        val haystack = "${app.label} ${app.packageName}".lowercase()
                        BANKING_HINTS.any { haystack.contains(it) } || ProtectedApplicationManager.KNOWN_PROTECTED_PACKAGES.contains(app.packageName)
                    }.map { it.packageName }.toSet()
                    if (suggested.isEmpty()) {
                        context.showAppToast("No banking apps detected")
                    } else {
                        persist(selected + suggested)
                        context.showAppToast("Added ${suggested.size} apps")
                    }
                },
                variant = AppButtonVariant.SECONDARY,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(6.dp))
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (visibleApps.isEmpty()) {
                    Text(
                        text = if (apps.isEmpty()) "Loading installed apps..." else "No app matches \"$query\".",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.5.sp,
                        modifier = Modifier.padding(12.dp)
                    )
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 260.dp)) {
                        items(visibleApps, key = { it.packageName }) { app ->
                            val isChecked = selected.contains(app.packageName)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        persist(
                                            if (isChecked) selected - app.packageName
                                            else selected + app.packageName
                                        )
                                    }
                                    .padding(horizontal = 8.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = {
                                        persist(
                                            if (isChecked) selected - app.packageName
                                            else selected + app.packageName
                                        )
                                    },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = MaterialTheme.colorScheme.primary
                                    )
                                )
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = app.label,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = 12.5.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = app.packageName,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 10.5.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
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
