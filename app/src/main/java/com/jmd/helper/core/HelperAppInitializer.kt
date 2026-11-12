/**
 * Package: com.jmd.helper.core
 * Module: Core Security, Invariants & Privacy
 *
 * What it is:
 * Architecture definition and implementation for [HelperAppInitializer].
 *
 * What it does:
 * Provides core logic, component interfaces, and subsystem bindings for HelperAppInitializer.
 *
 * Linked With:
 * - Schedulers & Receivers: [com.jmd.helper.scheduler.WorkManagerScheduler]
 * - Android Framework & Jetpack: [androidx.startup.Initializer]
 */

package com.jmd.helper.core

import android.content.Context
import androidx.startup.Initializer
import com.jmd.helper.data.prefs.EncryptedKeyguardStore
import com.jmd.helper.scheduler.WorkManagerScheduler

/**
 * What it is:
 * AndroidX Startup Initializer.
 *
 * What it does:
 * Flattens application initialization into a unified startup sequence, optimizing cold-start latency.
 *
 * Linked with:
 * - Architecture: [HelperAppInitializer], [HelperAppInitializer]
 */
class HelperAppInitializer : Initializer<Unit> {
    /**
     * What it is:
     * Execution routine for [create].
     *
     * What it does:
     * Coordinates create operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [HelperAppInitializer]
     */

    override fun create(context: Context) {
        val appContext = context.applicationContext
        AppFileLogger.i(appContext, "HelperAppInitializer", "AndroidX Startup Initializer executing early system bootstrap")

        // Pre-warm encrypted hardware keystore
        try {
            EncryptedKeyguardStore.getInstance(appContext)
        } catch (e: Exception) {
            AppFileLogger.w("HelperAppInitializer", "Keystore pre-warm note: ${e.message}")
        }

        // Enqueue periodic background maintenance worker
        try {
            WorkManagerScheduler.schedulePeriodicMaintenance(appContext)
        } catch (e: Exception) {
            AppFileLogger.w("HelperAppInitializer", "WorkManager bootstrap note: ${e.message}")
        }
    }
    /**
     * What it is:
     * Execution routine for [dependencies].
     *
     * What it does:
     * Coordinates dependencies operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [HelperAppInitializer]
     */

    override fun dependencies(): List<Class<out Initializer<*>>> = emptyList()
}
