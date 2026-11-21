/**
 * Package: com.jmd.superapp.ui.screen.settings
 * Module: Application Core Subsystem
 *
 * What it is:
 * Settings card for dynamic variable formats.
 *
 * What it does:
 * Exposes date, time, salutation and currency formatting for the tokens listed in [MessageVariables].
 * Adheres strictly to the 2-option / 3-step structured format architecture and Max 2 Words Rule.
 *
 * Linked With:
 * - Core: [MessageTemplateResolver], [MessageVariables], [PreferenceManager]
 */

package com.jmd.superapp.ui.screen.settings

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
import com.jmd.superapp.core.MessageTemplateResolver
import com.jmd.superapp.core.MessageVariables
import com.jmd.superapp.data.prefs.PreferenceManager
import com.jmd.superapp.ui.components.*
import com.jmd.superapp.ui.theme.AppDesignTokens

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
    // 3-step date state
    var dateMonthOption by remember { mutableStateOf(prefs.dateMonthOption) }
    var dateYearOption by remember { mutableStateOf(prefs.dateYearOption) }
    var dateSeparator by remember { mutableStateOf(prefs.customDateSeparator) }

    // 2-option time format state ("12HR" vs "24HR")
    var timeFormatOption by remember { mutableStateOf(prefs.timeFormatOption) }

    // 2-option greeting state ("TIME_OF_DAY" vs "CUSTOM")
    var salutationOption by remember { mutableStateOf(prefs.salutationOption) }
    var customGreetingText by remember { mutableStateOf(prefs.customGreetingCustomText) }

    // 2-option currency state ("RUPEE" vs "CUSTOM")
    var currencyOption by remember { mutableStateOf(prefs.currencyOption) }
    var customCurrencySymbol by remember { mutableStateOf(prefs.customCurrencySymbol) }

    // Dynamic live preview resolving in real-time
    val livePreviewSample = remember(
        dateMonthOption,
        dateYearOption,
        dateSeparator,
        timeFormatOption,
        salutationOption,
        customGreetingText,
        currencyOption,
        customCurrencySymbol
    ) {
        val sampleTemplate = "{greeting}! {name}, your task is scheduled on {date} ({day}) at {time}. Total: {rupee}500."
        MessageTemplateResolver.resolve(
            template = sampleTemplate,
            phone = "+91 98765 43210",
            name = "Rahul",
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
                        contentDescription = "Live Preview",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Live Preview",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 14.sp
                    )
                }
                AppBadge(text = "Real Time", color = MaterialTheme.colorScheme.primary)
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Updates live with your custom formatting settings.",
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

        // 2. Date Format (3-Step Selection Logic)
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
            Text("3-step builder for date formatting.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)

            Spacer(modifier = Modifier.height(10.dp))

            // Step 1: Day (Constant fixed 'dd')
            Text("Day (Fixed)", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, fontSize = 12.5.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                AppChip(
                    selected = true,
                    onClick = { /* Constant dd */ },
                    label = "dd (Day)",
                    activeColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Step 2: Month Choice (m, mm, mmm, mmmm)
            Text("Month Format", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, fontSize = 12.5.sp)
            Spacer(modifier = Modifier.height(4.dp))
            val months = listOf(
                "m" to "m (1-12)",
                "mm" to "mm (01-12)",
                "mmm" to "mmm (Nov)",
                "mmmm" to "mmmm (Full)"
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                months.forEach { (mCode, label) ->
                    AppChip(
                        selected = dateMonthOption.equals(mCode, ignoreCase = true),
                        onClick = {
                            dateMonthOption = mCode
                            prefs.dateMonthOption = mCode
                        },
                        label = label,
                        activeColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Step 3: Year Choice (yy, yyyy)
            Text("Year Format", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, fontSize = 12.5.sp)
            Spacer(modifier = Modifier.height(4.dp))
            val years = listOf(
                "yy" to "yy (26)",
                "yyyy" to "yyyy (2026)"
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                years.forEach { (yCode, label) ->
                    AppChip(
                        selected = dateYearOption.equals(yCode, ignoreCase = true),
                        onClick = {
                            dateYearOption = yCode
                            prefs.dateYearOption = yCode
                        },
                        label = label,
                        activeColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Step 4: Separator Choice (/, -, ., space)
            Text("Date Delimiter", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, fontSize = 12.5.sp)
            Spacer(modifier = Modifier.height(4.dp))
            val separators = listOf(
                "/" to "/ Slash",
                "-" to "- Dash",
                "." to ". Dot",
                " " to "  Space"
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                separators.forEach { (sep, label) ->
                    AppChip(
                        selected = dateSeparator == sep,
                        onClick = {
                            dateSeparator = sep
                            prefs.customDateSeparator = sep
                        },
                        label = label,
                        activeColor = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // 3. Time Format (Exactly 2 Options: 12 hr vs 24 hr)
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
            Text("Choose 12-hour or 24-hour time representation.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)

            Spacer(modifier = Modifier.height(10.dp))
            val timeOptions = listOf(
                "12HR" to "12 hr (AM/PM)",
                "24HR" to "24 hr (24H)"
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                timeOptions.forEach { (opt, label) ->
                    AppChip(
                        selected = timeFormatOption == opt,
                        onClick = {
                            timeFormatOption = opt
                            prefs.timeFormatOption = opt
                        },
                        label = label,
                        activeColor = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // 4. Salutation / Greeting (Exactly 2 Options: Time of Day vs Custom)
        AppCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Filled.WavingHand, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Text("Greeting Format", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                }
                val previewGreet = MessageTemplateResolver.resolve("{greeting}", prefs = prefs)
                AppBadge(text = previewGreet, color = MaterialTheme.colorScheme.tertiary)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text("Tailor greeting produced by {greeting}.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)

            Spacer(modifier = Modifier.height(10.dp))
            val greetingOptions = listOf(
                "TIME_OF_DAY" to "Time Day",
                "CUSTOM" to "Custom Text"
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                greetingOptions.forEach { (opt, label) ->
                    AppChip(
                        selected = salutationOption == opt,
                        onClick = {
                            salutationOption = opt
                            prefs.salutationOption = opt
                        },
                        label = label,
                        activeColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            AnimatedVisibility(visible = salutationOption == "CUSTOM") {
                Column(modifier = Modifier.padding(top = 10.dp)) {
                    OutlinedTextField(
                        value = customGreetingText,
                        onValueChange = { newText ->
                            customGreetingText = newText
                            prefs.customGreetingCustomText = newText
                        },
                        label = { Text("Custom Greeting") },
                        placeholder = { Text("e.g. Hello, Namaste, Pranam") },
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

        // 5. Currency Format (Exactly 2 Options: Standard Rupee vs Custom)
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
                val previewCurrency = MessageTemplateResolver.resolve("{rupee}", prefs = prefs)
                AppBadge(text = previewCurrency, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text("Configure currency symbol for {rupee}.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)

            Spacer(modifier = Modifier.height(10.dp))
            val currencyOptions = listOf(
                "RUPEE" to "₹ Rupee",
                "CUSTOM" to "Custom Symbol"
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                currencyOptions.forEach { (opt, label) ->
                    AppChip(
                        selected = currencyOption == opt,
                        onClick = {
                            currencyOption = opt
                            prefs.currencyOption = opt
                        },
                        label = label,
                        activeColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            AnimatedVisibility(visible = currencyOption == "CUSTOM") {
                Column(modifier = Modifier.padding(top = 10.dp)) {
                    OutlinedTextField(
                        value = customCurrencySymbol,
                        onValueChange = { newSymbol ->
                            customCurrencySymbol = newSymbol
                            prefs.customCurrencySymbol = newSymbol
                        },
                        label = { Text("Custom Symbol") },
                        placeholder = { Text("e.g. $, €, INR") },
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

        // 6. Variable Reference Card
        AppCard(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Filled.Code, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Text("Variable Reference", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text("Insert any of these tags into task messages:", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)

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
