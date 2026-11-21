/**
 * Package: com.jmd.superapp.ui.activity
 * Module: Application Core Subsystem
 *
 * What it is:
 * Architecture definition and implementation for [MainActivity].
 *
 * What it does:
 * Primary Android Activity hosting Jetpack Compose UI, handling runtime permission requests, deep links, incoming SEND intent shares, dynamic app icon aliases, and theme state.
 *
 * Linked With:
 * - Repositories: [com.jmd.superapp.data.repo.AppRepository]
 * - Services & Engines: [com.jmd.superapp.engine.ContactIngestionEngine]
 * - UI Screens & Components: [androidx.activity.ComponentActivity], [androidx.activity.compose.setContent], [androidx.activity.enableEdgeToEdge], [androidx.activity.result.contract.ActivityResultContracts]
 * - Core Security & Utilities: [android.content.pm.PackageManager], [android.util.Log], [androidx.core.content.ContextCompat], [com.jmd.superapp.core.AccessibilityHealthManager]
 * - Android Framework & Jetpack: [android.Manifest], [android.os.Build], [android.os.Bundle], [androidx.lifecycle.lifecycleScope]
 */

package com.jmd.superapp.ui.activity

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.jmd.superapp.core.AccessibilityHealthManager
import com.jmd.superapp.core.AppLifecycleObserver
import com.jmd.superapp.core.AppTrace
import com.jmd.superapp.data.prefs.PreferenceManager
import com.jmd.superapp.data.repo.AppRepository
import com.jmd.superapp.engine.ContactIngestionEngine
import com.jmd.superapp.ui.screen.MainAppHost
import com.jmd.superapp.ui.theme.SuperTheme
import kotlinx.coroutines.CancellationException
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
        }

        setContent {
            val prefs = remember { PreferenceManager(applicationContext) }
            var currentTheme by remember { mutableStateOf(prefs.appThemeMode) }
            var dynamicColor by remember { mutableStateOf(prefs.isDynamicColorEnabled) }
            val sharedText by remember { sharedTextState }

            SuperTheme(themeMode = currentTheme, isDynamicColor = dynamicColor) {
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
     * Parses plain text content received via external ACTION_SEND intents.
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
        // All service/PM/settings reconciliation runs off the UI thread: none of it is needed to draw a frame.
        lifecycleScope.launch(Dispatchers.IO) {
            AccessibilityHealthManager.ensureServiceComponentEnabled(applicationContext)
            val prefs = PreferenceManager(applicationContext)
            if (!prefs.isA11yUserPaused) {
                AccessibilityHealthManager.ensureAllServicesActive(applicationContext)
            }
        }
        triggerBackgroundContactIngestion()
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
        // Everything below hits storage (pool stats + contact scan). It must never run on the
        // frame-producing thread, so the throttle check moves inside the IO coroutine as well.
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val prefs = PreferenceManager(applicationContext)
                val repo = AppRepository(applicationContext)
                val isPoolEmpty = repo.getDynamicPoolStats().totalCount == 0
                val now = System.currentTimeMillis()
                val lastScan = prefs.lastContactIngestionTime
                // If pool is empty, skip throttle and ingest immediately! Otherwise throttle 6 hours
                if (!isPoolEmpty && (now - lastScan) < (6 * 3600 * 1000L)) {
                    return@launch
                }

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
     * Evaluates sensitive runtime permissions and requests any ungranted items.
     *
     * What it does:
     * Executes [checkAndRequestPermissions] logic in accordance with system and security invariants.
     *
     * Linked with:
     * - Architecture: [MainActivity], [checkAndRequestPermissions]
     */
    private fun checkAndRequestPermissions() {
        // Only the notification permission is requested up-front: everything else is requested
        // contextually from the Permissions centre when the user enables a feature that needs it.
        // This avoids a permission wall on first launch and keeps the app asking for less than it can.
        val permissionsToRequest = mutableListOf<String>()

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
