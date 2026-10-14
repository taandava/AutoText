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

// ----------------- SUB SCREEN: WHATSAPP SETUP & AUTO UNLOCK -----------------
@android.annotation.SuppressLint("BatteryLife")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhatsAppSetupAndAutoUnlockView(
    context: Context,
    prefs: PreferenceManager,
    onBack: () -> Unit
) {
    var isAutoUnlock by remember { mutableStateOf(prefs.isAutoUnlockEnabled) }
    var unlockType by remember { mutableStateOf(prefs.autoUnlockType) }
    var pinCode by remember { mutableStateOf(prefs.deviceUnlockPin) }
    var isAutoLockAfterSend by remember { mutableStateOf(prefs.isAutoLockAfterSendEnabled) }
    var countryCode by remember { mutableStateOf(prefs.defaultCountryCode) }
    var showPin by remember { mutableStateOf(false) }

    var testPhone by remember { mutableStateOf("") }
    var testMessage by remember { mutableStateOf("Hello! This is a test message from AutoText.") }
    var isTesting by remember { mutableStateOf(false) }

    val isA11yActive = AutoTextAccessibilityService.isServiceRunning

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("WhatsApp & Screen Auto-Unlock", fontWeight = FontWeight.Bold, color = BentoGlassTokens.TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = BentoGlassTokens.TextPrimary) }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BentoGlassTokens.VoidBackground,
                    titleContentColor = BentoGlassTokens.TextPrimary,
                    navigationIconContentColor = BentoGlassTokens.TextPrimary
                )
            )
        },
        containerColor = BentoGlassTokens.VoidBackground
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 1. Accessibility Service Live Status Bento Card
            item {
                WhatsAppA11yStatusCard(context = context, isA11yActive = isA11yActive)
            }

            // 2. Android 13/14 "Restricted Settings" Guide Bento Card
            item {
                WhatsAppRestrictedSettingsCard(context = context)
            }

            // 2B. Self-Healing & Never-Stop Watchdog Card
            item {
                SuperAutoHealAndPermissionFixerCard(context)
            }

            // 2C. Keep Open in Recent Apps Card
            item {
                KeepOpenRecentAppsCard()
            }

            // 3. Screen Auto-Unlock & Auto-Lock Settings Bento Card
            item {
                BentoCard(modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.LockClock, contentDescription = null, tint = BentoGlassTokens.ElectricIndigo, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Screen Auto-Unlock & Auto-Lock",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = BentoGlassTokens.TextPrimary
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Allows AutoText to wake your screen, enter your device PIN when locked, send scheduled messages, and automatically lock the screen back.",
                        fontSize = 11.5.sp,
                        color = BentoGlassTokens.TextMuted
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Toggle Auto-Unlock
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Auto-Unlock When Locked", fontWeight = FontWeight.SemiBold, color = BentoGlassTokens.TextPrimary, fontSize = 13.5.sp)
                            Text("Wakes screen and unlocks device automatically", color = BentoGlassTokens.TextMuted, fontSize = 11.sp)
                        }
                        Switch(
                            checked = isAutoUnlock,
                            onCheckedChange = {
                                isAutoUnlock = it
                                prefs.isAutoUnlockEnabled = it
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = BentoGlassTokens.NeonCyan,
                                checkedTrackColor = BentoGlassTokens.NeonCyan.copy(alpha = 0.35f)
                            )
                        )
                    }

                    if (isAutoUnlock) {
                        Spacer(modifier = Modifier.height(12.dp))

                        Text("Screen Lock Type:", color = BentoGlassTokens.TextMuted, fontSize = 11.5.sp)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            listOf(
                                "PIN" to "PIN",
                                "PASSWORD" to "Password",
                                "SWIPE" to "Swipe"
                            ).forEach { (type, label) ->
                                GlassChip(
                                    selected = unlockType == type,
                                    onClick = {
                                        unlockType = type
                                        prefs.autoUnlockType = type
                                    },
                                    label = label,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        if (unlockType == "PIN" || unlockType == "PASSWORD") {
                            Spacer(modifier = Modifier.height(12.dp))

                            val isPinMode = unlockType == "PIN"
                            GlassInputField(
                                value = pinCode,
                                onValueChange = {
                                    pinCode = if (isPinMode) it.filter { ch -> ch.isDigit() } else it
                                    prefs.deviceUnlockPin = pinCode
                                },
                                label = if (isPinMode) "Device Number Lock PIN" else "Device Lock Password",
                                placeholder = if (isPinMode) "e.g. 1234" else "Enter lock password",
                                isPassword = true,
                                leadingIcon = Icons.Default.Lock,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = if (isPinMode) KeyboardType.NumberPassword else KeyboardType.Password
                                ),
                                accentColor = BentoGlassTokens.NeonCyan
                            )

                            Text(
                                "Fingerprint / Face Auto-Switch: If your phone uses fingerprint, AutoText automatically swipes up to dismiss fingerprint and switch directly to number lock to enter your password.",
                                fontSize = 11.sp,
                                color = BentoGlassTokens.TextMuted,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Auto Lock After Send Toggle
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Auto-Lock Phone After Send", fontWeight = FontWeight.SemiBold, color = BentoGlassTokens.TextPrimary, fontSize = 13.5.sp)
                                Text("Re-locks the screen 2s after message is sent", color = BentoGlassTokens.TextMuted, fontSize = 11.sp)
                            }
                            Switch(
                                checked = isAutoLockAfterSend,
                                onCheckedChange = {
                                    isAutoLockAfterSend = it
                                    prefs.isAutoLockAfterSendEnabled = it
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = BentoGlassTokens.NeonCyan,
                                    checkedTrackColor = BentoGlassTokens.NeonCyan.copy(alpha = 0.35f)
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Test Screen Lock & Auto-Unlock Testing Hub
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        GlassButton(
                            text = "Test Screen Lock",
                            onClick = {
                                AccessibilityHealthManager.ensureServiceAlive(context)
                                Toast.makeText(context, "Locking screen now...", Toast.LENGTH_SHORT).show()
                                AutoTextAccessibilityService.lockScreenNow()
                            },
                            icon = Icons.Default.Lock,
                            variant = GlassButtonVariant.SECONDARY,
                            modifier = Modifier.weight(1f)
                        )
                        GlassButton(
                            text = "Test Screen Unlock",
                            onClick = {
                                AccessibilityHealthManager.ensureServiceAlive(context)
                                val currentPin = pinCode.ifBlank { prefs.deviceUnlockPin }
                                AutoTextAccessibilityService.testLockAndAutoUnlock(context, currentPin)
                            },
                            icon = Icons.Default.LockOpen,
                            variant = GlassButtonVariant.PRIMARY,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    GlassButton(
                        text = "Emergency Stop All Automation",
                        onClick = {
                            AutoTextAccessibilityService.clearPending()
                            Toast.makeText(context, "🛑 All background automation & auto-unlock stopped cleanly", Toast.LENGTH_SHORT).show()
                        },
                        icon = Icons.Default.StopCircle,
                        variant = GlassButtonVariant.DANGER,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // 4. Default Country Code Bento Card
            item {
                BentoCard(modifier = Modifier.fillMaxWidth()) {
                    Text("Default Country Code", fontWeight = FontWeight.Bold, color = BentoGlassTokens.TextPrimary, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "WhatsApp requires international dial codes. If phone numbers are entered without country code (e.g. 10 digits), AutoText automatically prefixes this country code.",
                        fontSize = 11.5.sp,
                        color = BentoGlassTokens.TextMuted
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    GlassInputField(
                        value = countryCode,
                        onValueChange = {
                            countryCode = it.filter { ch -> ch.isDigit() }
                            prefs.defaultCountryCode = countryCode
                        },
                        label = "Country Code (e.g. 91 for India, 1 for US)",
                        placeholder = "91",
                        leadingIcon = Icons.Default.Public,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        accentColor = BentoGlassTokens.NeonCyan
                    )
                }
            }

            // 5. Battery Optimization & Background Guide Bento Card
            item {
                WhatsAppBatteryOptimizationCard(context = context)
            }

            // 6. Live Test WhatsApp Automation Bento Card
            item {
                WhatsAppLiveTestCard(
                    context = context,
                    testPhone = testPhone,
                    onTestPhoneChange = { testPhone = it },
                    testMessage = testMessage,
                    onTestMessageChange = { testMessage = it }
                )
            }

            item {
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}
