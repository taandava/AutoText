

/**
 * Package: com.jmd.autotext.ui.activity
 * Module: UI Reminder & TTS Layer
 *
 * Fullscreen alarm activity displaying reminder title, notes, and speaking reminder message
 * aloud using Android [TextToSpeech] with snooze and dismiss actions.
 *
 * Linked With:
 * - Engine: [com.jmd.autotext.engine.SequentialTaskEngine]
 * - Screen: `ReminderScreen`
 * - Scheduler: [com.jmd.autotext.scheduler.AlarmScheduler]
 */
package com.jmd.autotext.ui.activity

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import java.util.Locale

import com.jmd.autotext.ui.screen.reminderScreen

/**
 * Activity presenting audible on-device reminder alarm with Text-to-Speech playback.
 */
class ReminderAlarmActivity : ComponentActivity(), TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var reminderText: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Show over lockscreen and turn screen on (minSdk is 26, O_MR1 is 27)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val title = intent.getStringExtra("reminder_title")?.takeIf { it.isNotBlank() }
            ?: intent.getStringExtra("task_title")?.takeIf { it.isNotBlank() }
            ?: "Reminder"
        reminderText = intent.getStringExtra("reminder_text")?.takeIf { it.isNotBlank() }
            ?: intent.getStringExtra("task_message")?.takeIf { it.isNotBlank() }
            ?: title.takeIf { it != "Reminder" }
            ?: "Reminder Alert"

        tts = TextToSpeech(this, this)

        // Safety auto-dismiss after 45 seconds so activity does not indefinitely block accessibility operations
        window.decorView.postDelayed(
            {
                if (!isFinishing && !isDestroyed) {
                    finish()
                }
            },
            45_000L,
        )

        setContent {
            reminderScreen(
                title = title,
                text = reminderText,
                onDismiss = {
                    finish()
                },
            ) { minutes ->
                snooze(minutes, title, reminderText)
                finish()
            }
        }
    }

    /**
     * TextToSpeech engine initialization callback; immediately speaks the reminder prompt if available.
     *
     * @param status Status code returned by TTS initialization (e.g. [TextToSpeech.SUCCESS]).
     */
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.getDefault()
            tts?.speak(reminderText, TextToSpeech.QUEUE_FLUSH, null, "reminder_speech")
        }
    }

    /**
     * Reschedules this reminder alarm activity to re-trigger after specified minutes interval.
     *
     * @param minutes Delay in minutes before firing alarm again.
     * @param title Header title of the reminder.
     * @param text Detailed reminder body text.
     */
    private fun snooze(minutes: Int, title: String, text: String) {
        val am = getSystemService(ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, ReminderAlarmActivity::class.java).apply {
            putExtra("reminder_title", title)
            putExtra("reminder_text", text)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            this,
            System.currentTimeMillis().toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val trigger = System.currentTimeMillis() + (minutes * 60 * 1000L)
        val canExact = (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) || am.canScheduleExactAlarms()
        try {
            if (canExact) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi)
            }
        } catch (e: SecurityException) {
            Log.e("ReminderAlarmActivity", "Exact alarm permission missing on snooze, falling back", e)
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi)
        }
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}


