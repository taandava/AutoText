/**
 * Package: com.jmd.helper.ui.activity
 * Module: UI Calling Simulator Layer
 *
 * Fullscreen incoming call simulator activity playing real ringtones and vibration patterns,
 * displaying custom caller name and number, and supporting Accept/Decline interactions.
 *
 * Linked With:
 * - Engine: [com.jmd.helper.engine.SequentialTaskEngine]
 * - Screen: `CallSimulationScreen`
 */
package com.jmd.helper.ui.activity

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

import com.jmd.helper.ui.theme.HelperTheme
import com.jmd.helper.ui.screen.CallSimulationScreen

/**
 * Activity rendering incoming call simulation screen with audio and haptic feedback.
 */
open class CallSimulationActivity : ComponentActivity() {

    companion object {
        fun start(
            context: Context,
            callerName: String = "Boss / Office",
            callerNumber: String = "+91 98765 43210",
            callerStyle: String = "PIXEL"
        ) {
            val intent = Intent(context, CallSimulationActivity::class.java).apply {
                putExtra("caller_name", callerName)
                putExtra("caller_number", callerNumber)
                putExtra("caller_style", callerStyle)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    private var mediaPlayer: MediaPlayer? = null
    private var fallbackRingtone: android.media.Ringtone? = null
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Acquire wake lock to ensure CPU remains active during incoming call simulation
        try {
            val pm = getSystemService(PowerManager::class.java)
            wakeLock = pm?.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "AutoText:CallSimulationWakeLock",
            )
            wakeLock?.acquire(30000L)
        } catch (e: Exception) {
            Log.w("CallSimulationActivity", "WakeLock: ${e.message}")
        }

        // Show over lockscreen and turn screen on (minSdk is 26, O_MR1 is 27)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val notifId = intent.getIntExtra("notif_id", 0)
        if (notifId != 0) {
            runCatching {
                val nm = getSystemService(android.app.NotificationManager::class.java)
                nm?.cancel(notifId)
            }
        }

        val keyguardManager = getSystemService(KeyguardManager::class.java)
        keyguardManager?.requestDismissKeyguard(this, null)

        val callerName = intent.getStringExtra("caller_name")
            ?: intent.getStringExtra("simulatedCallerName")
            ?: intent.getStringExtra("simulated_caller_name")
            ?: "Incoming Call"
        val callerNumber = intent.getStringExtra("caller_number")
            ?: intent.getStringExtra("simulatedCallerNumber")
            ?: intent.getStringExtra("simulated_caller_number")
            ?: "+91 98765 43210"
        val callerStyle = intent.getStringExtra("caller_style")
            ?: intent.getStringExtra("simulatedCallStyle")
            ?: intent.getStringExtra("simulated_call_style")
            ?: "PIXEL"

        startRinging()

        setContent {
            HelperTheme {
                CallSimulationScreen(
                    name = callerName,
                    number = callerNumber,
                    style = callerStyle,
                    onAnswer = { stopRinging() },
                ) {
                    stopRinging()
                    finish()
                }
            }
        }
    }

    /**
     * Initializes media player with default system ringtone and activates repetitive haptic vibration motor.
     */
    private fun startRinging() {
        try {
            val ringtoneUri = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_RINGTONE)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                ?: android.provider.Settings.System.DEFAULT_RINGTONE_URI

            var playerStarted = false
            try {
                mediaPlayer = MediaPlayer.create(this, ringtoneUri)?.apply {
                    isLooping = true
                    start()
                }
                playerStarted = mediaPlayer != null
            } catch (e: Exception) {
                Log.w("CallSimulationActivity", "MediaPlayer.create failed for $ringtoneUri: ${e.message}")
            }

            if (!playerStarted) {
                fallbackRingtone = RingtoneManager.getRingtone(this, ringtoneUri)?.apply {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        isLooping = true
                    }
                    play()
                }
            }
        } catch (e: Exception) {
            Log.e("CallSimulationActivity", "Error starting simulated call ringtone audio", e)
        }

        try {
            vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = getSystemService(VibratorManager::class.java)
                vm?.defaultVibrator
            } else {
                getSystemService(Vibrator::class.java)
            }

            val pattern = longArrayOf(0, 1000, 1000)
            vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
        } catch (e: Exception) {
            Log.e("CallSimulationActivity", "Error starting simulated call vibration", e)
        }
    }

    /**
     * Silences ringtone playback and halts vibration pattern when call is answered, dismissed, or activity destroyed.
     */
    private fun stopRinging() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
        } catch (e: Exception) {
            Log.w("CallSimulationActivity", "Error stopping mediaPlayer: ${e.message}")
        }
        try {
            fallbackRingtone?.stop()
            fallbackRingtone = null
        } catch (e: Exception) {
            Log.w("CallSimulationActivity", "Error stopping fallbackRingtone: ${e.message}")
        }
        try {
            vibrator?.cancel()
            vibrator = null
        } catch (e: Exception) {
            Log.w("CallSimulationActivity", "Error stopping vibrator: ${e.message}")
        }
    }

    override fun onPause() {
        super.onPause()
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (_: Exception) {
        }
        wakeLock = null
    }

    override fun onDestroy() {
        stopRinging()
        super.onDestroy()
    }
}

/**
 * Backward-compatible activity class aliased to [CallSimulationActivity].
 */
typealias SimulatedCallActivity = CallSimulationActivity


