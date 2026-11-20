/**
 * Package: com.jmd.superwa.ui.screen.settings
 * Module: Application Core Subsystem
 *
 * What it is:
 * Settings card for dynamic variable formats.
 *
 * What it does:
 * Exposes date, time, salutation and currency formatting for the tokens listed in [MessageVariables].
 *
 * Linked With:
 * - Core: [MessageTemplateResolver], [MessageVariables], [PreferenceManager]
 */

package com.jmd.superwa.ui.screen.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jmd.superwa.core.MessageTemplateResolver
import com.jmd.superwa.core.MessageVariables
import com.jmd.superwa.data.prefs.PreferenceManager
import com.jmd.superwa.ui.components.*
import com.jmd.superwa.ui.theme.AppDesignTokens

/**
 * What it is:
 * Settings card for dynamic variable formats: dates, times, salutations and currency.
 *
 * What it does:
 * Renders interactive Material 3 UI for [SettingsFormattingCard] adhering to application design invariants.
 *
 * Linked with:
 * - Architecture: [SettingsFormattingCardComponents], [SettingsFormattingCard]
 */
@Composable
fun SettingsFormattingCard(
    prefs: PreferenceManager,
    modifier: Modifier = Modifier
) {
    var currencyOption by remember { mutableStateOf(prefs.currencyOption) }
    var customCurrencySymbol by remember {
        mutableStateOf(
            if (prefs.customCurrencySymbol == "\u20B9" || prefs.customCurrencySymbol == "â‚¹") "$" else prefs.customCurrencySymbol
        )
    }
    var salutationOption by remember { mutableStateOf(prefs.salutationOption) }
    var customGreetingText by remember { mutableStateOf(prefs.customGreetingCustomText) }
    var timeFormatOption by remember { mutableStateOf(prefs.timeFormatOption) }
    var dateMonthOption by remember { mutableStateOf(prefs.dateMonthOption) }
    var dateYearOption by remember { mutableStateOf(prefs.dateYearOption) }
    var dateSeparatorOption by remember { mutableStateOf(prefs.dateSeparatorOption) }

    // Live preview resolution triggering automatically on preference state changes
    val livePreviewSample = remember(
        currencyOption,
        customCurrencySymbol,
        salutationOption,
        customGreetingText,
        timeFormatOption,
        dateMonthOption,
        dateYearOption,
        dateSeparatorOption
    ) {
        val sampleTemplate = "{greeting}! Your bill is generated for {date} at {time}. Total fee: {rupee}1,500."
        MessageTemplateResolver.resolve(
            template = sampleTemplate,
            phone = "+91 98765 43210",
            channel = "WhatsApp",
            prefs = prefs
        )
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // 1. Live Variable Preview Card
        AppCardElevated(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(
                        imageVector = Icons.Filled.AutoAwesome,
                        contentDescription = "Preview",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Variable Preview",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 14.sp
                    )
                }
                AppBadge(text = "Dynamic Live", color = MaterialTheme.colorScheme.primary)
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Below preview updates in real time as you adjust date, time, salutation, and currency formats.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp
            )
            Spacer(modifier = Modifier.height(10.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(AppDesignTokens.ShapeSmall)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .border(0.75.dp, MaterialTheme.colorScheme.outlineVariant, AppDesignTokens.ShapeSmall)
                    .padding(12.dp)
            ) {
                Text(
                    text = livePreviewSample,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    fontFamily = FontFamily.Default
                )
            }
        }

        // 2. Currency Format Customization (Rupee ₹ vs Custom)
        AppCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Filled.CurrencyRupee, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Text("Currency Format", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                }
                val curBadge = if (currencyOption == "CUSTOM") customCurrencySymbol.ifBlank { "Custom" } else "₹"
                AppBadge(text = curBadge, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text("Select standard Rupee sign or custom currency symbol.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)

            Spacer(modifier = Modifier.height(10.dp))
            Text("Currency Option:", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
            Spacer(modifier = Modifier.height(6.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                AppChip(
                    selected = currencyOption == "RUPEE",
                    onClick = {
                        currencyOption = "RUPEE"
                        prefs.currencyOption = "RUPEE"
                    },
                    label = "Rupee (₹)",
                    activeColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                AppChip(
                    selected = currencyOption == "CUSTOM",
                    onClick = {
                        currencyOption = "CUSTOM"
                        prefs.currencyOption = "CUSTOM"
                    },
                    label = "Custom Symbol",
                    activeColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
            }

            AnimatedVisibility(visible = currencyOption == "CUSTOM") {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    OutlinedTextField(
                        value = customCurrencySymbol,
                        onValueChange = { newSym ->
                            customCurrencySymbol = newSym
                            prefs.customCurrencySymbol = newSym
                        },
                        label = { Text("Custom Symbol") },
                        placeholder = { Text("e.g. $, €, £, Rs.") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                        )
                    )
                }
            }
        }

        // 3. Salutation Format (Time of Day vs Custom)
        AppCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Filled.WavingHand, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Text("Salutation Format", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                }
                val previewGreet = MessageTemplateResolver.resolve("{greeting}", prefs = prefs)
                AppBadge(text = previewGreet, color = MaterialTheme.colorScheme.tertiary)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text("Select time-of-day greeting or custom text for {greeting}.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)

            Spacer(modifier = Modifier.height(10.dp))
            Text("Salutation Option:", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
            Spacer(modifier = Modifier.height(6.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                AppChip(
                    selected = salutationOption == "TIME_OF_DAY",
                    onClick = {
                        salutationOption = "TIME_OF_DAY"
                        prefs.salutationOption = "TIME_OF_DAY"
                    },
                    label = "Time Day",
                    activeColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                AppChip(
                    selected = salutationOption == "CUSTOM",
                    onClick = {
                        salutationOption = "CUSTOM"
                        prefs.salutationOption = "CUSTOM"
                    },
                    label = "Custom Text",
                    activeColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
            }

            AnimatedVisibility(visible = salutationOption == "CUSTOM") {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    OutlinedTextField(
                        value = customGreetingText,
                        onValueChange = { newText ->
                            customGreetingText = newText
                            prefs.customGreetingCustomText = newText
                        },
                        label = { Text("Custom Text") },
                        placeholder = { Text("e.g. Hello, Namaste, Welcome") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                        )
                    )
                }
            }
        }

        // 4. Time Format (12 hr vs 24 hr)
        AppCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Filled.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Text("Time Format", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                }
                val previewTime = MessageTemplateResolver.resolve("{time}", prefs = prefs)
                AppBadge(text = previewTime, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text("Select 12-hour or 24-hour clock format.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)

            Spacer(modifier = Modifier.height(10.dp))
            Text("Time Option:", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
            Spacer(modifier = Modifier.height(6.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                AppChip(
                    selected = timeFormatOption == "12HR",
                    onClick = {
                        timeFormatOption = "12HR"
                        prefs.timeFormatOption = "12HR"
                    },
                    label = "12 Hour",
                    activeColor = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.weight(1f)
                )
                AppChip(
                    selected = timeFormatOption == "24HR",
                    onClick = {
                        timeFormatOption = "24HR"
                        prefs.timeFormatOption = "24HR"
                    },
                    label = "24 Hour",
                    activeColor = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // 5. Date Format (dd constant, month m/mm/mmm/mmmm, year yy/yyyy, separator)
        AppCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Filled.CalendarMonth, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Text("Date Format", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                }
                val previewDate = MessageTemplateResolver.resolve("{date}", prefs = prefs)
                AppBadge(text = previewDate, color = MaterialTheme.colorScheme.tertiary)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text("Day dd is constant. Select month, year, and delimiter.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)

            Spacer(modifier = Modifier.height(10.dp))
            Text("Day Code:", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
            Spacer(modifier = Modifier.height(6.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                AppChip(
                    selected = true,
                    onClick = {},
                    label = "Day (dd)",
                    activeColor = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text("Month Format:", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
            Spacer(modifier = Modifier.height(6.dp))
            val months = listOf("m", "mm", "mmm", "mmmm")
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                months.forEach { m ->
                    AppChip(
                        selected = dateMonthOption.equals(m, ignoreCase = true),
                        onClick = {
                            dateMonthOption = m
                            prefs.dateMonthOption = m
                        },
                        label = m,
                        activeColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text("Year Format:", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
            Spacer(modifier = Modifier.height(6.dp))
            val years = listOf("yy", "yyyy")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                years.forEach { y ->
                    AppChip(
                        selected = dateYearOption.equals(y, ignoreCase = true),
                        onClick = {
                            dateYearOption = y
                            prefs.dateYearOption = y
                        },
                        label = y,
                        activeColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text("Date Separator:", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
            Spacer(modifier = Modifier.height(6.dp))
            val separators = listOf(
                "/" to "Slash (/)",
                "-" to "Hyphen (-)",
                "." to "Dot (.)",
                " " to "Space ( )"
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                separators.forEach { (sep, label) ->
                    AppChip(
                        selected = dateSeparatorOption == sep,
                        onClick = {
                            dateSeparatorOption = sep
                            prefs.dateSeparatorOption = sep
                        },
                        label = label,
                        activeColor = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // 6. Variable Reference
        AppCard(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Filled.Code, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Text("Variable Reference", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text("Available tokens for template messages:", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)

            Spacer(modifier = Modifier.height(10.dp))

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                MessageVariables.REFERENCE.forEach { variable ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(AppDesignTokens.ShapeSmall)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = variable.token,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 12.sp
                        )
                        Text(
                            text = variable.description,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }
}
