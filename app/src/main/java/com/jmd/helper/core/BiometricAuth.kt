package com.jmd.helper.core

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * Standardized AndroidX Biometric authentication manager.
 * Provides biometric authentication (Fingerprint, Face) with device credential fallback (PIN, Pattern, Password).
 * Backward-compatible from Android 14/15/16+ down to Android 8.1 (API 27).
 */
class BiometricAuthManager(private val context: Context) {

    private val biometricManager = BiometricManager.from(context)

    /**
     * Checks if biometric hardware is present, enrolled, and ready for authentication.
     */
    fun canAuthenticate(): Boolean {
        val authenticators = BIOMETRIC_STRONG or DEVICE_CREDENTIAL
        return biometricManager.canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS
    }

    /**
     * Prompts the user with the system BiometricPrompt dialog.
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
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                AppFileLogger.i("BiometricAuthManager", "User biometric authentication succeeded")
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                AppFileLogger.w("BiometricAuthManager", "Biometric authentication error ($errorCode): $errString")
                onError(errString.toString())
            }

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

