/**
 * Package: com.jmd.supersms.ui.activity
 * Module: Application Core Subsystem
 *
 * What it is:
 * Transparent trampoline Activity that opens the system Device Administration prompt.
 *
 * What it does:
 * OEM skins (notably Samsung's SecDeviceAdminAdd) reject `ACTION_ADD_DEVICE_ADMIN` intents that
 * carry FLAG_ACTIVITY_NEW_TASK and close their own screen immediately, so the confirmation never
 * appears. Non-Activity callers (services, receivers, workers) cannot start an Activity without that
 * flag, so they route through this Activity instead, which launches the prompt without the flag and
 * finishes itself immediately.
 *
 * Linked With:
 * - Receivers: [com.jmd.supersms.receiver.SuperDeviceAdminReceiver]
 * - Android Framework: [android.app.Activity], [android.content.Intent]
 */

package com.jmd.supersms.ui.activity

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import com.jmd.supersms.receiver.SuperDeviceAdminReceiver

/**
 * What it is:
 * Zero-UI bridge that presents the Device Admin activation prompt from any context.
 *
 * What it does:
 * Starts the Device Admin intent from an Activity context (no NEW_TASK flag), then closes itself so
 * the user lands directly on the system confirmation dialog.
 */
class DeviceAdminPromptActivity : Activity() {

    companion object {
        private const val TAG = "DeviceAdminPrompt"

        /**
         * What it is:
         * Entry point used by non-Activity callers.
         *
         * What it does:
         * Starts this trampoline with NEW_TASK (required outside an Activity) so it can in turn
         * launch the admin prompt the way OEM settings apps accept it.
         *
         * @param context Any context (application, service, receiver).
         */
        fun start(context: Context) {
            context.startActivity(
                Intent(context, DeviceAdminPromptActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            startActivity(SuperDeviceAdminReceiver.getAddDeviceAdminIntent(this))
        } catch (t: Throwable) {
            Log.e(TAG, "Unable to open the Device Admin prompt: ${t.message}", t)
        } finally {
            finish()
        }
    }
}
