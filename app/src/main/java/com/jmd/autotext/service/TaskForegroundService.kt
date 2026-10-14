package com.jmd.autotext.service

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log

/**
 * Legacy service stub retained purely to stop any previous foreground instances
 * and immediately dismiss notification ID 2001. No foreground notifications or
 * background services are launched.
 */
class TaskForegroundService : Service() {

    companion object {
        private const val TAG = "TaskFGS"
        const val NOTIFICATION_ID = 2001

        fun start(context: Context) {
            // No-op: Persistent foreground notifications disabled per user specification.
            // Automation reliability is maintained silently via AlarmManager exact heartbeats.
        }

        fun stop(context: Context) {
            try {
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
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
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.cancel(NOTIFICATION_ID)
        } catch (ignored: Exception) {}
        stopSelf()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.cancel(NOTIFICATION_ID)
        } catch (ignored: Exception) {}
        stopSelf()
        return START_NOT_STICKY
    }
}
