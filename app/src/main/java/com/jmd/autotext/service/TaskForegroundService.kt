/**
 * Package: com.jmd.autotext.service
 * Module: Foreground Service Layer
 *
 * Dedicated foreground service lifecycle manager ensuring background task visibility and clean notification teardown.
 *
 * Linked With:
 * - Engine: [com.jmd.autotext.engine.SequentialTaskEngine]
 * - Application: [com.jmd.autotext.AutoTextApplication]
 */
package com.jmd.autotext.service

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log

/**
 * Service managing task foreground state and ongoing notification channels.
 */
class TaskForegroundService : Service() {

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

    override fun onBind(intent: Intent?): IBinder? = null

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
