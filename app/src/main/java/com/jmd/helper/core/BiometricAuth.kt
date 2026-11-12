/**
 * Package: com.jmd.helper.core
 * Module: Core Security, Invariants & Privacy
 *
 * What it is:
 * Architecture definition and implementation for [BiometricAuth].
 *
 * What it does:
 * Provides core logic, component interfaces, and subsystem bindings for BiometricAuth.
 *
 * Linked With:
 * - Core Security & Utilities: [androidx.biometric.BiometricManager], [androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG], [androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL], [androidx.core.content.ContextCompat]
 * - Android Framework & Jetpack: [androidx.biometric.BiometricPrompt], [androidx.fragment.app.FragmentActivity]
 */

package com.jmd.helper.core

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * What it is:
 * Standardized AndroidX Biometric authentication manager.
 *
 * What it does:
 * Provides biometric authentication (Fingerprint, Face) with device credential fallback (PIN, Pattern, Password). Backward-compatible from Android 14/15/16+ down to Android 8.1 (API 27).
 *
 * Linked with:
 * - Architecture: [BiometricAuth], [BiometricAuthManager]
 */
class BiometricAuthManager(private val context: Context) {

    private val biometricManager = BiometricManager.from(context)

    /**
     * What it is:
     * Checks if biometric hardware is present, enrolled, and ready for authentication.
     *
     * What it does:
     * Executes [canAuthenticate] logic in accordance with system and security invariants.
     *
     * Linked with:
     * - Architecture: [BiometricAuth], [canAuthenticate]
     */
    fun canAuthenticate(): Boolean {
        val authenticators = BIOMETRIC_STRONG or DEVICE_CREDENTIAL
        return biometricManager.canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS
    }

    /**
     * What it is:
     * Prompts the user with the system BiometricPrompt dialog.
     *
     * What it does:
     * Executes [authenticate] logic in accordance with system and security invariants.
     *
     * Linked with:
     * - Architecture: [BiometricAuth], [authenticate]
     */
    fun authenticate(
        activity: FragmentActivity,
        title: String = "Unlock Helper",
        subtitle: String = "Authenticate to access secured communication automation",
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val executor = ContextCompat.getMainExecutor(activity)

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)
            .build()

        val biometricPrompt = BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() {
            /**
             * What it is:
             * Biometric authentication lifecycle callback [onAuthenticationSucceeded].
             *
             * What it does:
             * Receives authentication state transitions and updates security credentials for onAuthenticationSucceeded.
             *
             * Linked with:
             * - Component: [BiometricAuth]
             */
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                AppFileLogger.i("BiometricAuthManager", "User biometric authentication succeeded")
                onSuccess()
            }
            /**
             * What it is:
             * Biometric authentication lifecycle callback [onAuthenticationError].
             *
             * What it does:
             * Receives authentication state transitions and updates security credentials for onAuthenticationError.
             *
             * Linked with:
             * - Component: [BiometricAuth]
             */

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                AppFileLogger.w("BiometricAuthManager", "Biometric authentication error ($errorCode): $errString")
                onError(errString.toString())
            }
            /**
             * What it is:
             * Biometric authentication lifecycle callback [onAuthenticationFailed].
             *
             * What it does:
             * Receives authentication state transitions and updates security credentials for onAuthenticationFailed.
             *
             * Linked with:
             * - Component: [BiometricAuth]
             */

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                AppFileLogger.w("BiometricAuthManager", "Biometric authentication failed (unrecognized credential)")
            }
        })

        try {
            biometricPrompt.authenticate(promptInfo)
        } catch (e: Exception) {
            AppFileLogger.e("BiometricAuthManager", "Failed to launch BiometricPrompt: ${e.message}", e)
            onError(e.message ?: "Authentication failed to launch")
        }
    }
}

