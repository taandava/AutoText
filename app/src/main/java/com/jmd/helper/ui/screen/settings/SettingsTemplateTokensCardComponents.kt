/**
 * Package: com.jmd.helper.ui.screen.settings
 * Module: Application Core Subsystem
 *
 * What it is:
 * Architecture definition and implementation for [SettingsTemplateTokensCardComponents].
 *
 * What it does:
 * Provides core logic, component interfaces, and subsystem bindings for SettingsTemplateTokensCardComponents.
 *
 * Linked With:
 * - UI Screens & Components: [androidx.compose.ui.Alignment], [androidx.compose.ui.Modifier], [androidx.compose.ui.draw.clip], [androidx.compose.ui.graphics.Color]
 * - Core Security & Utilities: [com.jmd.helper.core.MessageTemplateResolver], [com.jmd.helper.data.prefs.PreferenceManager]
 * - Android Framework & Jetpack: [androidx.compose.animation.AnimatedVisibility], [androidx.compose.foundation.background], [androidx.compose.foundation.border], [androidx.compose.foundation.clickable]
 */

package com.jmd.helper.ui.screen.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jmd.helper.core.MessageTemplateResolver
import com.jmd.helper.data.prefs.PreferenceManager
import com.jmd.helper.ui.components.*
import com.jmd.helper.ui.theme.AppDesignTokens

/**
 * What it is:
 * Settings configuration card for template dynamic variables, date/time formats, contextual greetings, and currency tokens.
 *
 * What it does:
 * Renders interactive Material 3 UI for [SettingsTemplateTokensCard] adhering to application design invariants.
 *
 * Linked with:
 * - Architecture: [SettingsTemplateTokensCardComponents], [SettingsTemplateTokensCard]
 */
@Composable
fun SettingsTemplateTokensCard(
    prefs: PreferenceManager,
    modifier: Modifier = Modifier
) {
    var dateFormat by remember { mutableStateOf(prefs.customDateFormat) }
    var customPattern by remember { mutableStateOf(prefs.customDateCustomPattern) }
    var dateSeparator by remember { mutableStateOf(prefs.customDateSeparator) }
    var shortDateFormat by remember { mutableStateOf(prefs.customShortDateFormat) }
    var timeFormat by remember { mutableStateOf(prefs.customTimeFormat) }
    var greetingStyle by remember { mutableStateOf(prefs.customGreetingStyle) }
    var customGreetingText by remember { mutableStateOf(prefs.customGreetingCustomText) }
    var namasteText by remember { mutableStateOf(prefs.customNamasteText) }
    var currencySymbol by remember { mutableStateOf(prefs.customCurrencySymbol) }
    var indianNumbering by remember { mutableStateOf(prefs.isIndianNumberFormattingEnabled) }

    // Live preview resolution triggering automatically on preference state changes
    val livePreviewSample = remember(dateFormat, customPattern, dateSeparator, shortDateFormat, timeFormat, greetingStyle, customGreetingText, namasteText, currencySymbol) {
        val sampleTemplate = "{namaste} Aarav! Your scheduled appointment is confirmed for {date} ({short_day}) at {time}. Total fee: {rupee}1,500. Have a {greeting}!"
        MessageTemplateResolver.resolve(
            template = sampleTemplate,
            name = "Aarav Sharma",
            phone = "+91 98765 43210",
            channel = "WhatsApp",
            prefs = prefs
        )
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // 1. Live Interactive Template Preview Card
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
                        text = "Live Token Resolution Preview",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 14.sp
                    )
                }
                AppBadge(text = "Dynamic Live", color = MaterialTheme.colorScheme.primary)
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Below preview updates in real time as you adjust date, time, greeting, and currency formats.",
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

        // 2. Date Format & Separator Customization
        AppCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Filled.CalendarMonth, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Text("{date} Format Customization", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                }
                val previewDate = MessageTemplateResolver.resolve("{date}", prefs = prefs)
                AppBadge(text = previewDate, color = MaterialTheme.colorScheme.tertiary)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text("Choose how dates are dynamically injected into scheduled drafts and templates.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)

            Spacer(modifier = Modifier.height(10.dp))

            val dateFormats = listOf(
                "dd-MM-yyyy" to "DD-MM-YYYY",
                "dd/MM/yyyy" to "DD/MM/YYYY",
                "dd.MM.yyyy" to "DD.MM.YYYY",
                "ddMMyyyy" to "DDMMYYYY",
                "dd-MM-yy" to "DD-MM-YY",
                "yyyy-MM-dd" to "YYYY-MM-DD",
                "dd MMM yyyy" to "12 Nov 2026",
                "dd MMMM yyyy" to "12 November 2026",
                "MMMM dd, yyyy" to "Nov 12, 2026",
                "CUSTOM" to "Custom Pattern"
            )

            // Grid of Date Formats
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                dateFormats.chunked(2).forEach { rowItems ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        rowItems.forEach { (fmt, label) ->
                            GlassChip(
                                selected = dateFormat == fmt,
                                onClick = {
                                    dateFormat = fmt
                                    prefs.customDateFormat = fmt
                                },
                                label = label,
                                activeColor = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(visible = dateFormat == "CUSTOM") {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    OutlinedTextField(
                        value = customPattern,
                        onValueChange = { newPattern ->
                            customPattern = newPattern
                            prefs.customDateCustomPattern = newPattern
                        },
                        label = { Text("Custom Date Pattern (e.g. EEE, d MMM yyyy)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text("Date Delimiter Separator:", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
            Spacer(modifier = Modifier.height(6.dp))

            val separators = listOf(
                "auto" to "Default",
                "-" to "Hyphen (-)",
                "/" to "Slash (/)",
                "." to "Dot (.)",
                " " to "Space ( )",
                "" to "None"
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                separators.forEach { (sep, label) ->
                    GlassChip(
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

        // 3. Short Date & Time Format Customization
        AppCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Filled.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Text("{short_date} & {time} Formats", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                }
                val previewTime = MessageTemplateResolver.resolve("{time}", prefs = prefs)
                AppBadge(text = previewTime, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text("Configure compact date tags and 12-hour/24-hour time representation.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)

            Spacer(modifier = Modifier.height(10.dp))
            Text("{short_date} Format:", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
            Spacer(modifier = Modifier.height(6.dp))

            val shortFormats = listOf(
                "dd/MM/yyyy" to "DD/MM/YYYY",
                "dd-MM-yy" to "DD-MM-YY",
                "dd/MM" to "DD/MM",
                "MM/dd" to "MM/DD",
                "dd-MM" to "DD-MM"
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                shortFormats.forEach { (fmt, label) ->
                    GlassChip(
                        selected = shortDateFormat == fmt,
                        onClick = {
                            shortDateFormat = fmt
                            prefs.customShortDateFormat = fmt
                        },
                        label = label,
                        activeColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text("{time} Format:", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
            Spacer(modifier = Modifier.height(6.dp))

            val timeFormats = listOf(
                "hh:mm a" to "12-Hour (AM/PM)",
                "hh:mm:ss a" to "12-Hour + Sec",
                "HH:mm" to "24-Hour Military",
                "HH:mm:ss" to "24-Hour + Sec",
                "hh:mm a 'IST'" to "With IST Zone"
            )
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                timeFormats.chunked(2).forEach { rowItems ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        rowItems.forEach { (fmt, label) ->
                            GlassChip(
                                selected = timeFormat == fmt,
                                onClick = {
                                    timeFormat = fmt
                                    prefs.customTimeFormat = fmt
                                },
                                label = label,
                                activeColor = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }

        // 4. Greetings, Salutations & Namaste Customization
        AppCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Filled.WavingHand, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Text("{greeting} & {namaste} Salutations", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                }
                val previewGreet = MessageTemplateResolver.resolve("{greeting}", prefs = prefs)
                AppBadge(text = previewGreet, color = MaterialTheme.colorScheme.tertiary)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text("Tailor contextual salutations and localized traditional greetings.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)

            Spacer(modifier = Modifier.height(10.dp))
            Text("{greeting} Style:", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
            Spacer(modifier = Modifier.height(6.dp))

            val greetingStyles = listOf(
                "DYNAMIC" to "Time-of-Day (Morning/Eve)",
                "NAMASTE" to "Namaste",
                "PRANAM" to "Pranam",
                "HELLO" to "Hello",
                "HI" to "Hi",
                "CUSTOM" to "Custom Text"
            )
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                greetingStyles.chunked(2).forEach { rowItems ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        rowItems.forEach { (style, label) ->
                            GlassChip(
                                selected = greetingStyle == style,
                                onClick = {
                                    greetingStyle = style
                                    prefs.customGreetingStyle = style
                                },
                                label = label,
                                activeColor = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(visible = greetingStyle == "CUSTOM") {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    OutlinedTextField(
                        value = customGreetingText,
                        onValueChange = { newText ->
                            customGreetingText = newText
                            prefs.customGreetingCustomText = newText
                        },
                        label = { Text("Custom Greeting (e.g. Jai Jinendra, Radhe Radhe, Sat Sri Akal)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text("{namaste} Token Form:", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
            Spacer(modifier = Modifier.height(6.dp))

            val namasteForms = listOf(
                "Namaste" to "Namaste",
                "नमस्ते" to "नमस्ते",
                "Pranam" to "Pranam",
                "Namaskar" to "Namaskar",
                "Hello" to "Hello",
                "Hi" to "Hi"
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                namasteForms.forEach { (form, label) ->
                    GlassChip(
                        selected = namasteText == form,
                        onClick = {
                            namasteText = form
                            prefs.customNamasteText = form
                        },
                        label = label,
                        activeColor = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // 5. Currency Symbol & Indian Number Grouping
        AppCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Filled.CurrencyRupee, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Text("{rupee} & Currency Customization", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                }
                AppBadge(text = currencySymbol, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text("Configure currency symbol and Indian Lakhs/Crores numbering formats.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)

            Spacer(modifier = Modifier.height(10.dp))
            Text("Currency Symbol:", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
            Spacer(modifier = Modifier.height(6.dp))

            val currencies = listOf(
                "₹" to "₹ (Rupee)",
                "Rs." to "Rs.",
                "INR" to "INR",
                "$" to "$ (Dollar)",
                "€" to "€ (Euro)",
                "£" to "£ (Pound)"
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                currencies.forEach { (sym, label) ->
                    GlassChip(
                        selected = currencySymbol == sym,
                        onClick = {
                            currencySymbol = sym
                            prefs.customCurrencySymbol = sym
                        },
                        label = label,
                        activeColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            GlassToggleRow(
                title = "Indian Number Grouping (1,00,000)",
                subtitle = "Format thousands and lakhs using Indian comma conventions rather than international millions (100,000)",
                checked = indianNumbering,
                onCheckedChange = { checked ->
                    indianNumbering = checked
                    prefs.isIndianNumberFormattingEnabled = checked
                },
                icon = Icons.Filled.Pin
            )
        }

        // 6. Available Template Variables Cheat Sheet
        AppCard(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Filled.Code, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Text("Dynamic Template Variable Reference", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text("Insert any of these tags into task messages, reminders, or campaign templates:", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)

            Spacer(modifier = Modifier.height(10.dp))

            val tokenList = listOf(
                "{name}" to "Full contact name",
                "{first_name}" to "First name only",
                "{phone}" to "Normalized phone",
                "{date}" to "Configured date",
                "{short_date}" to "Compact date",
                "{time}" to "Configured time",
                "{day}" to "Full weekday (e.g. Thursday)",
                "{short_day}" to "Short weekday (e.g. Thu)",
                "{day_num}" to "Day number (01-31)",
                "{today}" to "Today's date",
                "{ist_time}" to "Indian Standard Time",
                "{greeting}" to "Dynamic greeting",
                "{namaste}" to "Configured namaste",
                "{rupee}" to "Configured currency"
            )

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                tokenList.forEach { (token, desc) ->
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
                            text = token,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 12.sp
                        )
                        Text(
                            text = desc,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }
}
