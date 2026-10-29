

/**
 * Package: com.jmd.autotext.ui.activity
 * Module: UI Application Entry Point
 *
 * Primary Android Activity hosting Jetpack Compose UI, handling runtime permission requests,
 * deep links, incoming SEND intent shares, dynamic app icon aliases, and theme state.
 *
 * Linked With:
 * - Host: `MainAppHost`
 * - Preferences: [com.jmd.autotext.data.prefs.PreferenceManager]
 * - Application: [com.jmd.autotext.AutoTextApplication]
 * - Theme: `ThemeKt`
 */
package com.jmd.autotext.ui.activity

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.jmd.autotext.core.AccessibilityHealthManager
import com.jmd.autotext.data.prefs.PreferenceManager
import com.jmd.autotext.data.repo.AppRepository
import com.jmd.autotext.engine.ContactIngestionEngine
import com.jmd.autotext.ui.screen.mainAppHost
import com.jmd.autotext.ui.theme.autoTextTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Root compose activity hosting the primary navigation hierarchy.
 */
class MainActivity : ComponentActivity() {

    private val sharedTextState = mutableStateOf<String?>(null)

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { _ ->
        triggerBackgroundContactIngestion()
        AccessibilityHealthManager.enforceAutoRotateInvariant(applicationContext)
    }

    /**
     * Initializes edge-to-edge window insets, parses incoming share intents, validates runtime permissions,
     * triggers background contact discovery, and mounts Compose hierarchy.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIncomingIntent(intent)
        checkAndRequestPermissions()
        triggerBackgroundContactIngestion()
        AccessibilityHealthManager.enforceAutoRotateInvariant(applicationContext)

        setContent {
            val prefs = remember { PreferenceManager(applicationContext) }
            var currentTheme by remember { mutableStateOf(prefs.appThemeMode) }
            var dynamicColor by remember { mutableStateOf(prefs.isDynamicColorEnabled) }
            val sharedText by remember { sharedTextState }

            autoTextTheme(themeMode = currentTheme, isDynamicColor = dynamicColor) {
                mainAppHost(
                    sharedText = sharedText,
                    onSharedTextConsumed = { sharedTextState.value = null },
                ) { newTheme ->
                    prefs.appThemeMode = newTheme
                    currentTheme = newTheme
                    dynamicColor = prefs.isDynamicColorEnabled
                }
            }
        }
    }

    /**
     * Captures incoming intents delivered while activity is running in single-task or top mode.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    /**
     * Parses plain text payload received via external ACTION_SEND intents.
     */
    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return
        if ((intent.action == Intent.ACTION_SEND) && (intent.type?.startsWith("text/") == true)) {
            val text = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (!text.isNullOrBlank()) {
                sharedTextState.value = text
            }
        }
    }

    /**
     * Re-verifies accessibility and notification listener service health upon returning to foreground.
     */
    override fun onResume() {
        super.onResume()
        // Ensure accessibility and notification services are silently auto-healed in background
        CoroutineScope(Dispatchers.IO).launch {
            AccessibilityHealthManager.ensureAllServicesActive(applicationContext)
        }
        triggerBackgroundContactIngestion()
        AccessibilityHealthManager.enforceAutoRotateInvariant(applicationContext)
    }

    /**
     * Scans device address book, call logs, and SMS threads into local SQLite contact pool if throttle elapsed.
     */
    private fun triggerBackgroundContactIngestion() {
        if (ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return
        }
        val prefs = PreferenceManager(applicationContext)
        val repo = AppRepository(applicationContext)
        val isPoolEmpty = repo.getDynamicPoolStats().totalCount == 0

        val lastScan = prefs.lastContactIngestionTime
        val now = System.currentTimeMillis()
        // If pool is empty, bypass throttle and ingest immediately! Otherwise throttle 6 hours
        if ((!isPoolEmpty) && ((now - lastScan) < (6 * 3600 * 1000L))) {
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val scanner = ContactIngestionEngine(applicationContext, repo)
                val (newAdded, totalPoolCount) = scanner.scanAndIngestAll()
                if ((newAdded > 0) || (totalPoolCount > 0)) {
                    prefs.lastContactIngestionTime = now
                    Log.i("MainActivity", "Background contact ingestion finished: added $newAdded, total $totalPoolCount")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                Log.e("MainActivity", "Background contact ingestion failed", t)
            }
        }
    }

    /**
     * Evaluates dangerous runtime permissions and requests any ungranted items.
     */
    private fun checkAndRequestPermissions() {
        val permissionsToRequest = mutableListOf(
            Manifest.permission.SEND_SMS,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_SMS,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.READ_CALL_LOG,
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val missing = permissionsToRequest.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isNotEmpty()) {
            requestPermissionLauncher.launch(missing.toTypedArray())
        }
    }
}


