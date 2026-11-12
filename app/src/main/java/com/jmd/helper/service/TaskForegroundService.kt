/**
 * Package: com.jmd.helper.service
 * Module: Background Services & Quick Tiles
 *
 * What it is:
 * Architecture definition and implementation for [TaskForegroundService].
 *
 * What it does:
 * Dedicated foreground service lifecycle manager ensuring background task visibility and clean notification teardown.
 *
 * Linked With:
 * - Core Security & Utilities: [android.app.NotificationManager], [android.util.Log]
 * - Android Framework & Jetpack: [android.app.Service], [android.os.IBinder]
 */

package com.jmd.helper.service

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log

/**
 * What it is:
 * Service managing task foreground state and ongoing notification channels.
 *
 * What it does:
 * Encapsulates state, business contracts, and operations for [TaskForegroundService].
 *
 * Linked with:
 * - Architecture: [TaskForegroundService], [TaskForegroundService]
 */
class TaskForegroundService : Service() {
    /**
     * What it is:
     * Companion object constant and factory provider for [TaskForegroundService].
     *
     * What it does:
     * Manages static declarations, tag references, and builder utilities.
     *
     * Linked with:
     * - Component: [TaskForegroundService]
     */

    companion object {
        private const val TAG = "TaskFGS"

        /** Fixed notification identifier used for legacy foreground service notifications. */
        const val NOTIFICATION_ID = 2001

        /**
         * Reconciles foreground notifications by delegating to [stop] to guarantee no sticky notifications remain.
         *
         * @param context Active Android [Context].
         */
        fun start(context: Context) {
            // Service is dedicated to tearing down and canceling stale foreground execution notifications
            stop(context)
        }

        /**
         * Cancels any lingering notification with [NOTIFICATION_ID] from the system [NotificationManager].
         *
         * @param context Active Android [Context].
         */
        fun stop(context: Context) {
            try {
                val nm = context.getSystemService(NOTIFICATION_SERVICE) as? NotificationManager
                nm?.cancel(NOTIFICATION_ID)
            } catch (e: Exception) {
                Log.w(TAG, "Cannot cancel legacy notification: ${e.message}")
            }
        }
    }
    /**
     * What it is:
     * Execution routine for [onBind].
     *
     * What it does:
     * Coordinates onBind operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [TaskForegroundService]
     */

    override fun onBind(intent: Intent?): IBinder? = null
    /**
     * What it is:
     * Execution routine for [onCreate].
     *
     * What it does:
     * Coordinates onCreate operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [TaskForegroundService]
     */

    override fun onCreate() {
        super.onCreate()
        try {
            val nm = getSystemService(NOTIFICATION_SERVICE) as? NotificationManager
            nm?.cancel(NOTIFICATION_ID)
        } catch (e: Exception) {
            Log.d(TAG, "Notification cancel error on create: ${e.message}")
        }
        stopSelf()
    }
    /**
     * What it is:
     * Execution routine for [onStartCommand].
     *
     * What it does:
     * Coordinates onStartCommand operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [TaskForegroundService]
     */

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            val nm = getSystemService(NOTIFICATION_SERVICE) as? NotificationManager
            nm?.cancel(NOTIFICATION_ID)
        } catch (e: Exception) {
            Log.d(TAG, "Notification cancel error on start command: ${e.message}")
        }
        stopSelf()
        return START_NOT_STICKY
    }
}
