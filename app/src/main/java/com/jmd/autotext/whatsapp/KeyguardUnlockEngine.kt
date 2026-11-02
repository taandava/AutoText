

/**
 * Package: com.jmd.autotext.whatsapp
 * Module: WhatsApp Automation Subsystem
 *
 * Provides autonomous lockscreen unlocking (swipe gestures, accessibility PIN entry, coordinate tapping)
 * and security relocking post-automation via [android.app.admin.DevicePolicyManager].
 *
 * Linked With:
 * - Service: [com.jmd.autotext.service.AutoTextAccessibilityService]
 * - Receiver: [com.jmd.autotext.receiver.AutoTextDeviceAdminReceiver]
 * - Preferences: [com.jmd.autotext.data.prefs.PreferenceManager]
 * - Engines: [com.jmd.autotext.engine.SequentialTaskEngine], [com.jmd.autotext.engine.WhatsAppAutomationSender]
 */
package com.jmd.autotext.whatsapp

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.app.KeyguardManager
import android.content.Context
import android.graphics.Path
import android.os.Build
import android.os.Handler
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Autonomous keyguard unlock and security relock engine.
 *
 * Handles detection of active lockscreens, executing upward swipe gestures to invoke
 * the PIN bouncer, sequentially entering PIN digits via element actions, confirming entry,
 * and halting upon error detection (e.g. cooldown timers or wrong PIN warnings).
 *
 * @property service The active [AccessibilityService] used for gesture dispatch and element interaction.
 * @property handler Handler on the main thread for coordinating timed sequential clicks.
 * @property loggerTag Logging identifier tag for lockscreen telemetry.
 */
class KeyguardUnlockEngine(
    private val service: AccessibilityService,
    private val handler: Handler,
    private val loggerTag: String = "KeyguardUnlockEngine"
) {

    @Volatile var isUnlockPending = false
    @Volatile var isEnteringPinDigits = false
    @Volatile var unlockPin = ""
    @Volatile var shouldRelockAfterSend = false
    @Volatile var isTestUnlockMode = false

    var unlockAttemptCount = 0
    var unlockLoopAttempts = 0
    private var lastSwipeTime = 0L
    private var lastUnlockActionTime = 0L

    /**
     * Orchestrates the keyguard unlock evaluation loop.
     *
     * Evaluates lock state via [KeyguardManager], checks for lockout errors or cooldowns,
     * performs upward swipes to reveal the bouncer if necessary, and dispatches numeric
     * PIN entry or text injection.
     *
     * @param onUnlockSuccess Callback invoked once the keyguard is verified unlocked.
     * @param onUnlockAbort Callback invoked with an error reason if unlock fails or cooldown is detected.
     */
    fun handleKeyguardUnlock(
        onUnlockSuccess: () -> Unit,
        onUnlockAbort: (reason: String) -> Unit
    ) {
        try {
            if (!isUnlockPending || isEnteringPinDigits) return

            val km = service.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
            if (km != null && !km.isKeyguardLocked) {
                Log.i(loggerTag, "Lockscreen unlocked successfully (keyguard not locked)")
                isUnlockPending = false
                unlockAttemptCount = 0
                isEnteringPinDigits = false
                onUnlockSuccess()
                return
            }

            val now = System.currentTimeMillis()
            if (now - lastUnlockActionTime < 450L) return
            lastUnlockActionTime = now

            val roots = AccessibilityNodeUtils.getTargetRoots(service)
            Log.i(loggerTag, "handleKeyguardUnlock: attempt=$unlockAttemptCount, rootsCount=${roots.size}, targetPIN length=${unlockPin.length}")

            val lockscreenError = isLockscreenErrorOrCooldownPresent(roots)
            if (lockscreenError != null) {
                Log.e(loggerTag, "ZERO-LOCKOUT GUARD: Detected error/cooldown '$lockscreenError'! Aborting auto-unlock.")
                isUnlockPending = false
                isEnteringPinDigits = false
                unlockAttemptCount = 0
                onUnlockAbort(lockscreenError)
                return
            }

            if (unlockPin.isEmpty()) {
                if (now - lastSwipeTime > 800L) {
                    lastSwipeTime = now
                    Log.i(loggerTag, "Swipe mode: dispatching unlock swipe...")
                    swipeUpToRevealBouncer {
                        handler.postDelayed({
                            val checkKm = service.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
                            if (checkKm?.isKeyguardLocked == false) {
                                isUnlockPending = false
                                unlockAttemptCount = 0
                                onUnlockSuccess()
                            }
                        }, 300L)
                    }
                }
                return
            }

            val isNumericPin = unlockPin.all { it.isDigit() }

            if (isNumericPin) {
                for (root in roots) {
                    if (hasKeypadDigits(root)) {
                        Log.i(loggerTag, "Numeric keypad bouncer visible! Entering PIN digits sequentially via element actions...")
                        enterKeypadDigitsAndSubmit(unlockPin, onUnlockSuccess, onUnlockAbort)
                        return
                    }
                }

                for (root in roots) {
                    val pinField = findPinInputField(root)
                    if (pinField != null) {
                        Log.i(loggerTag, "Lockscreen PIN input field discovered! Entering text directly...")
                        if (AccessibilityNodeUtils.setTextOnInput(pinField, unlockPin, service)) {
                            handler.postDelayed({
                                clickKeypadEnter(root)
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                    pinField.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id)
                                }
                                handler.postDelayed({
                                    val checkKm = service.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
                                    if (checkKm?.isKeyguardLocked == false) {
                                        isUnlockPending = false
                                        unlockAttemptCount = 0
                                        onUnlockSuccess()
                                    }
                                }, 350L)
                            }, 250L)
                            return
                        }
                    }
                }

                if (now - lastSwipeTime > 600L) {
                    for (root in roots) {
                        if (tryClickSwitchToNumberLock(root)) {
                            lastSwipeTime = now
                            Log.i(loggerTag, "Clicked explicit switch button to open PIN keypad")
                            handler.postDelayed({ handleKeyguardUnlock(onUnlockSuccess, onUnlockAbort) }, 400L)
                            return
                        }
                    }
                }

                if (isBiometricPromptVisible(roots) && now - lastSwipeTime > 600L) {
                    for (root in roots) {
                        if (tryClickSwitchToNumberLock(root)) {
                            lastSwipeTime = now
                            handler.postDelayed({ handleKeyguardUnlock(onUnlockSuccess, onUnlockAbort) }, 400L)
                            return
                        }
                    }
                }

                if (now - lastSwipeTime > 800L && unlockAttemptCount < 3) {
                    lastSwipeTime = now
                    unlockAttemptCount++
                    Log.i(loggerTag, "Auto-swiping UP on lockscreen to reveal keypad (attempt $unlockAttemptCount of 3)...")
                    swipeUpToRevealBouncer {
                        handler.postDelayed({ handleKeyguardUnlock(onUnlockSuccess, onUnlockAbort) }, 400L)
                    }
                    return
                } else if (unlockAttemptCount >= 3) {
                    Log.w(loggerTag, "Reached max swipe attempts (3). Stopping auto-unlock.")
                    isUnlockPending = false
                    isEnteringPinDigits = false
                    onUnlockAbort("Max swipe attempts reached")
                    return
                }
            } else {
                for (root in roots) {
                    val pinField = findPinInputField(root)
                    if (pinField != null) {
                        Log.i(loggerTag, "Password input field discovered! Entering text...")
                        if (AccessibilityNodeUtils.setTextOnInput(pinField, unlockPin, service)) {
                            handler.postDelayed({
                                clickKeypadEnter(root)
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                    pinField.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id)
                                }
                                handler.postDelayed({
                                    val checkKm = service.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
                                    if (checkKm?.isKeyguardLocked == false) {
                                        isUnlockPending = false
                                        unlockAttemptCount = 0
                                        onUnlockSuccess()
                                    }
                                }, 350L)
                            }, 250L)
                            return
                        }
                    }
                }

                for (root in roots) {
                    if (tryClickSwitchToNumberLock(root)) {
                        handler.postDelayed({ handleKeyguardUnlock(onUnlockSuccess, onUnlockAbort) }, 300L)
                        return
                    }
                }
            }
        } catch (t: Throwable) {
            Log.e(loggerTag, "Error in handleKeyguardUnlock", t)
        }
    }

    private fun enterKeypadDigitsAndSubmit(
        pin: String,
        onUnlockSuccess: () -> Unit,
        onUnlockAbort: (reason: String) -> Unit
    ) {
        if (isEnteringPinDigits) return
        isEnteringPinDigits = true
        Log.i(loggerTag, "Starting element-based PIN keypad entry for ${pin.length} digits...")

        fun tapNextDigit(index: Int) {
            if (!isUnlockPending) {
                isEnteringPinDigits = false
                return
            }

            val km = service.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
            if (km?.isKeyguardLocked == false) {
                Log.d(loggerTag, "Device confirmed unlocked early (index $index)")
                isEnteringPinDigits = false
                isUnlockPending = false
                onUnlockSuccess()
                return
            }

            if (index < pin.length) {
                val digitChar = pin[index]
                val currentRoots = AccessibilityNodeUtils.getTargetRoots(service)
                var digitTapped = false

                for (r in currentRoots) {
                    val digitNode = findKeypadDigitNode(r, digitChar)
                    if (digitNode != null) {
                        val rect = android.graphics.Rect()
                        digitNode.getBoundsInScreen(rect)
                        if (rect.width() > 0 && rect.height() > 0) {
                            val tapX = rect.centerX().toFloat()
                            val tapY = rect.centerY().toFloat()
                            digitTapped = dispatchPhysicalTap(tapX, tapY)
                            if (digitTapped) {
                                Log.i(loggerTag, "Dispatched physical tap on digit '$digitChar' at ($tapX, $tapY) [${index + 1}/${pin.length}]")
                                break
                            }
                        }
                    }
                }

                if (!digitTapped) {
                    val dm = service.resources.displayMetrics
                    val width = dm.widthPixels.toFloat()
                    val height = dm.heightPixels.toFloat()
                    val (colRatio, rowRatio) = when (digitChar) {
                        '1' -> Pair(0.222f, 0.524f)
                        '2' -> Pair(0.500f, 0.524f)
                        '3' -> Pair(0.778f, 0.524f)
                        '4' -> Pair(0.222f, 0.619f)
                        '5' -> Pair(0.500f, 0.619f)
                        '6' -> Pair(0.778f, 0.619f)
                        '7' -> Pair(0.222f, 0.714f)
                        '8' -> Pair(0.500f, 0.714f)
                        '9' -> Pair(0.778f, 0.714f)
                        '0' -> Pair(0.500f, 0.809f)
                        else -> Pair(0.500f, 0.809f)
                    }
                    val tapX = width * colRatio
                    val tapY = height * rowRatio
                    dispatchPhysicalTap(tapX, tapY)
                    Log.i(loggerTag, "Coordinate fallback physical tap for digit '$digitChar' at ($tapX, $tapY) [${index + 1}/${pin.length}]")
                }

                handler.postDelayed({
                    tapNextDigit(index + 1)
                }, 220L)
            } else {
                handler.postDelayed({
                    val checkKm = service.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
                    if (checkKm?.isKeyguardLocked == false) {
                        Log.d(loggerTag, "Device confirmed unlocked immediately after digits (no Enter needed)")
                        isEnteringPinDigits = false
                        isUnlockPending = false
                        onUnlockSuccess()
                        return@postDelayed
                    }

                    var okTapped = false
                    val currentRoots = AccessibilityNodeUtils.getTargetRoots(service)
                    for (r in currentRoots) {
                        val enterNode = findKeypadEnterNode(r)
                        if (enterNode != null) {
                            val rect = android.graphics.Rect()
                            enterNode.getBoundsInScreen(rect)
                            if (rect.width() > 0 && rect.height() > 0) {
                                val tapX = rect.centerX().toFloat()
                                val tapY = rect.centerY().toFloat()
                                okTapped = dispatchPhysicalTap(tapX, tapY)
                                if (okTapped) {
                                    Log.i(loggerTag, "Dispatched physical tap on Enter/OK element at ($tapX, $tapY)")
                                    break
                                }
                            }
                        }
                    }

                    if (!okTapped) {
                        val dm = service.resources.displayMetrics
                        val tapX = dm.widthPixels * 0.746f
                        val tapY = dm.heightPixels * 0.809f
                        dispatchPhysicalTap(tapX, tapY)
                        Log.i(loggerTag, "Coordinate fallback physical tap on Enter/OK at ($tapX, $tapY)")
                    }

                    fun pollUnlockVerification(attempt: Int) {
                        val finalKm = service.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
                        if (finalKm?.isKeyguardLocked == false) {
                            Log.i(loggerTag, "Device confirmed unlocked following Enter submission (attempt $attempt)")
                            isEnteringPinDigits = false
                            isUnlockPending = false
                            onUnlockSuccess()
                        } else if (attempt < 5) {
                            handler.postDelayed({ pollUnlockVerification(attempt + 1) }, 350L)
                        } else {
                            Log.w(loggerTag, "Device still locked after submission and 1.8s timeout. Stopping.")
                            isUnlockPending = false
                            isEnteringPinDigits = false
                            onUnlockAbort("Device remained locked after PIN submission")
                        }
                    }

                    handler.postDelayed({ pollUnlockVerification(1) }, 400L)
                }, 300L)
            }
        }

        tapNextDigit(0)
    }

    private fun findKeypadDigitNode(root: AccessibilityNodeInfo, digit: Char): AccessibilityNodeInfo? {
        val digitIds = listOf(
            "VivoPinkey$digit", "vivopinkey$digit", "vivo_keypad_num_$digit",
            "key$digit", "button$digit", "num$digit", "btn$digit", "btn_$digit",
            "digit$digit", "digit_$digit", "sec_key$digit", "sec_pin_key$digit",
            "sec_keyboard_key$digit", "sec_keypad_key_$digit", "kl_button$digit",
            "pin_number_$digit", "pin_num_$digit", "keyboard_num_$digit",
            "color_pin_$digit", "numeric_key_$digit"
        )
        for (id in digitIds) {
            val nodes = AccessibilityNodeUtils.findNodesByViewId(root, "com.android.systemui", id)
            if (nodes.isNotEmpty()) return nodes.first()
        }

        return AccessibilityNodeUtils.findNodeByTextOrDesc(
            root,
            listOf(digit.toString()),
            exact = true
        )
    }

    private fun findKeypadEnterNode(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val enterIds = listOf(
            "key_enter_text", "key_enter", "sec_key_enter", "sec_btn_ok",
            "sec_key_ok", "sec_pin_ok", "sec_pin_enter", "btn_ok", "ok",
            "VivoPinkey0right", "vivopinkey0right", "button_next", "check", "enter",
            "coui_keyboard_confirm", "btn_confirm", "done", "key_submit"
        )
        for (id in enterIds) {
            val nodes = AccessibilityNodeUtils.findNodesByViewId(root, "com.android.systemui", id)
            if (nodes.isNotEmpty()) return nodes.first()
        }

        val textNode = AccessibilityNodeUtils.findNodeByTextOrDesc(
            root,
            listOf("ok", "enter", "done", "confirm", "submit", "✓", "check"),
            exact = true
        )
        return textNode
    }

    /**
     * Locates and clicks the "Enter", "OK", or checkmark button on the lockscreen PIN keypad.
     *
     * @param root The root [AccessibilityNodeInfo] representing the keyguard bouncer hierarchy.
     * @return `true` if an Enter/Confirm button was found and clicked successfully; `false` otherwise.
     */
    fun clickKeypadEnter(root: AccessibilityNodeInfo): Boolean {
        val enterNode = findKeypadEnterNode(root)
        if (enterNode != null) {
            val rect = android.graphics.Rect()
            enterNode.getBoundsInScreen(rect)
            if (rect.width() > 0 && rect.height() > 0) {
                return dispatchPhysicalTap(rect.centerX().toFloat(), rect.centerY().toFloat())
            }
            return AccessibilityNodeUtils.clickElement(enterNode, service)
        }
        return false
    }

    private fun dispatchPhysicalTap(x: Float, y: Float, durationMs: Long = 50L): Boolean {
        return try {
            val path = Path().apply { moveTo(x, y) }
            val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
            val gesture = GestureDescription.Builder().addStroke(stroke).build()
            service.dispatchGesture(gesture, null, null)
        } catch (t: Throwable) {
            Log.e(loggerTag, "dispatchPhysicalTap failed: ${t.message}")
            false
        }
    }

    private fun findPinInputField(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val inputIds = listOf(
            "pinEntry", "passwordEntry", "pin_entry", "password_entry",
            "lockPassword", "password", "pin", "entry", "vivo_pin_input"
        )
        for (id in inputIds) {
            val nodes = AccessibilityNodeUtils.findNodesByViewId(root, "com.android.systemui", id)
            if (nodes.isNotEmpty()) return nodes.first()
        }

        with(AccessibilityNodeUtils) {
            return root.findFirstDescendant { it.className?.toString()?.contains("EditText", ignoreCase = true) == true }
        }
    }

    private fun hasKeypadDigits(root: AccessibilityNodeInfo): Boolean {
        var foundCount = 0
        for (digit in listOf('1', '2', '3', '4', '5')) {
            if (findKeypadDigitNode(root, digit) != null) foundCount++
        }
        return foundCount >= 2
    }

    private fun tryClickSwitchToNumberLock(root: AccessibilityNodeInfo): Boolean {
        val switchKeywords = listOf("use pin", "pin", "use password", "password", "switch to pin", "cancel", "negative_button")
        val switchIds = listOf("use_pin", "btn_use_pin", "switch_to_pin", "button_secondary")
        for (id in switchIds) {
            val nodes = AccessibilityNodeUtils.findNodesByViewId(root, "com.android.systemui", id)
            if (nodes.isNotEmpty()) {
                if (AccessibilityNodeUtils.clickElement(nodes.first(), service)) return true
            }
        }
        val textNode = AccessibilityNodeUtils.findNodeByTextOrDesc(root, switchKeywords)
        return textNode != null && AccessibilityNodeUtils.clickElement(textNode, service)
    }

    private fun isBiometricPromptVisible(roots: List<AccessibilityNodeInfo>): Boolean {
        val keywords = listOf("touch the fingerprint sensor", "fingerprint", "face not recognized", "biometric")
        for (r in roots) {
            if (AccessibilityNodeUtils.findNodeByTextOrDesc(r, keywords) != null) return true
        }
        return false
    }

    private fun isLockscreenErrorOrCooldownPresent(roots: List<AccessibilityNodeInfo>): String? {
        val dangerKeywords = listOf(
            "try again in", "incorrect pin", "wrong pin", "wrong password",
            "too many attempts", "phone will be wiped", "device will be erased", "factory reset"
        )
        for (r in roots) {
            val node = AccessibilityNodeUtils.findNodeByTextOrDesc(r, dangerKeywords)
            if (node != null) {
                return node.text?.toString() ?: node.contentDescription?.toString() ?: "Cooldown / Error detected"
            }
        }
        return null
    }

    private fun swipeUpToRevealBouncer(onComplete: () -> Unit) {
        try {
            val dm = service.resources.displayMetrics
            val width = dm.widthPixels.toFloat()
            val height = dm.heightPixels.toFloat()
            val startY = height * 0.85f
            val endY = height * 0.20f

            // Dispatch two parallel strokes (two-finger swipe)
            // Satisfies OEM requirements like Vivo's "Swipe up with two fingers to unlock"
            // while functioning as a standard upward swipe on all Android devices
            val path1 = Path().apply {
                moveTo(width * 0.40f, startY)
                lineTo(width * 0.40f, endY)
            }
            val path2 = Path().apply {
                moveTo(width * 0.60f, startY)
                lineTo(width * 0.60f, endY)
            }
            val stroke1 = GestureDescription.StrokeDescription(path1, 0, 220L)
            val stroke2 = GestureDescription.StrokeDescription(path2, 0, 220L)
            val gesture = GestureDescription.Builder()
                .addStroke(stroke1)
                .addStroke(stroke2)
                .build()

            service.dispatchGesture(gesture, object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    Log.d(loggerTag, "Two-finger swipe completed")
                    handler.postDelayed({ onComplete() }, 250L)
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    Log.w(loggerTag, "Two-finger swipe cancelled")
                    handler.postDelayed({ onComplete() }, 250L)
                }
            }, null)
            Log.d(loggerTag, "Dispatched two-finger swipe up bouncer gesture")
        } catch (t: Throwable) {
            Log.e(loggerTag, "Failed swipeUpToRevealBouncer", t)
            handler.postDelayed({ onComplete() }, 250L)
        }
    }
}
