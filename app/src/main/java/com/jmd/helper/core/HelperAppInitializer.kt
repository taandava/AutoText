package com.jmd.helper.core

import android.content.Context
import androidx.startup.Initializer
import com.jmd.helper.data.prefs.EncryptedKeyguardStore
import com.jmd.helper.scheduler.WorkManagerScheduler

/**
 * AndroidX Startup Initializer.
 * Flattens application initialization into a unified startup sequence, optimizing cold-start latency.
 */
class HelperAppInitializer : Initializer<Unit> {

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

    override fun dependencies(): List<Class<out Initializer<*>>> = emptyList()
}
