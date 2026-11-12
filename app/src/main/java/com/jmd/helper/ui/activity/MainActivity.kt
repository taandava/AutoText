/**
 * Package: com.jmd.helper.ui.activity
 * Module: Application Core Subsystem
 *
 * What it is:
 * Architecture definition and implementation for [MainActivity].
 *
 * What it does:
 * Primary Android Activity hosting Jetpack Compose UI, handling runtime permission requests, deep links, incoming SEND intent shares, dynamic app icon aliases, and theme state.
 *
 * Linked With:
 * - Repositories: [com.jmd.helper.data.repo.AppRepository]
 * - Services & Engines: [com.jmd.helper.engine.ContactIngestionEngine]
 * - UI Screens & Components: [androidx.activity.ComponentActivity], [androidx.activity.compose.setContent], [androidx.activity.enableEdgeToEdge], [androidx.activity.result.contract.ActivityResultContracts]
 * - Core Security & Utilities: [android.content.pm.PackageManager], [android.util.Log], [androidx.core.content.ContextCompat], [com.jmd.helper.core.AccessibilityHealthManager]
 * - Android Framework & Jetpack: [android.Manifest], [android.os.Build], [android.os.Bundle], [androidx.lifecycle.lifecycleScope]
 */

package com.jmd.helper.ui.activity

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
import androidx.lifecycle.lifecycleScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.jmd.helper.core.AccessibilityHealthManager
import com.jmd.helper.core.AppLifecycleObserver
import com.jmd.helper.core.AppTrace
import com.jmd.helper.data.prefs.PreferenceManager
import com.jmd.helper.data.repo.AppRepository
import com.jmd.helper.engine.ContactIngestionEngine
import com.jmd.helper.ui.screen.MainAppHost
import com.jmd.helper.ui.theme.HelperTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * What it is:
 * Root compose activity hosting the primary navigation hierarchy.
 *
 * What it does:
 * Encapsulates state, business contracts, and operations for [MainActivity].
 *
 * Linked with:
 * - Architecture: [MainActivity], [MainActivity]
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
     * What it is:
     * Initializes edge-to-edge window insets, parses incoming share intents, validates runtime permissions, triggers background contact discovery, and mounts Compose hierarchy.
     *
     * What it does:
     * Executes [onCreate] logic in accordance with system and security invariants.
     *
     * Linked with:
     * - Architecture: [MainActivity], [onCreate]
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        AppLifecycleObserver.init()

        AppTrace.section("MainActivity.onCreate") {
            enableEdgeToEdge()
            handleIncomingIntent(intent)
            checkAndRequestPermissions()
            triggerBackgroundContactIngestion()
            AccessibilityHealthManager.enforceAutoRotateInvariant(applicationContext)
        }

        setContent {
            val prefs = remember { PreferenceManager(applicationContext) }
            var currentTheme by remember { mutableStateOf(prefs.appThemeMode) }
            var dynamicColor by remember { mutableStateOf(prefs.isDynamicColorEnabled) }
            val sharedText by remember { sharedTextState }

            HelperTheme(themeMode = currentTheme, isDynamicColor = dynamicColor) {
                MainAppHost(
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
     * What it is:
     * Captures incoming intents delivered while activity is running in single-task or top mode.
     *
     * What it does:
     * Executes [onNewIntent] logic in accordance with system and security invariants.
     *
     * Linked with:
     * - Architecture: [MainActivity], [onNewIntent]
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    /**
     * What it is:
     * Parses plain text payload received via external ACTION_SEND intents.
     *
     * What it does:
     * Executes [handleIncomingIntent] logic in accordance with system and security invariants.
     *
     * Linked with:
     * - Architecture: [MainActivity], [handleIncomingIntent]
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
     * What it is:
     * Re-verifies accessibility and notification listener service health upon returning to foreground.
     *
     * What it does:
     * Executes [onResume] logic in accordance with system and security invariants.
     *
     * Linked with:
     * - Architecture: [MainActivity], [onResume]
     */
    override fun onResume() {
        super.onResume()
        AccessibilityHealthManager.ensureServiceComponentEnabled(applicationContext)
        // Ensure accessibility and notification services are silently auto-healed in background
        lifecycleScope.launch(Dispatchers.IO) {
            val prefs = PreferenceManager(applicationContext)
            if (!prefs.isA11yUserPaused) {
                AccessibilityHealthManager.ensureAllServicesActive(applicationContext)
            }
        }
        triggerBackgroundContactIngestion()
        AccessibilityHealthManager.enforceAutoRotateInvariant(applicationContext)
    }

    /**
     * What it is:
     * Scans device address book, call logs, and SMS threads into local SQLite contact pool if throttle elapsed.
     *
     * What it does:
     * Executes [triggerBackgroundContactIngestion] logic in accordance with system and security invariants.
     *
     * Linked with:
     * - Architecture: [MainActivity], [triggerBackgroundContactIngestion]
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
        // If pool is empty, skip throttle and ingest immediately! Otherwise throttle 6 hours
        if ((!isPoolEmpty) && ((now - lastScan) < (6 * 3600 * 1000L))) {
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
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
     * What it is:
     * Evaluates dangerous runtime permissions and requests any ungranted items.
     *
     * What it does:
     * Executes [checkAndRequestPermissions] logic in accordance with system and security invariants.
     *
     * Linked with:
     * - Architecture: [MainActivity], [checkAndRequestPermissions]
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
            Manifest.permission.ANSWER_PHONE_CALLS,
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
