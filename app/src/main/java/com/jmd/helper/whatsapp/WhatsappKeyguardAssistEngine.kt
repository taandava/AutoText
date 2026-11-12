/**
 * Package: com.jmd.helper.whatsapp
 * Module: WhatsApp Automation Subsystem
 *
 * What it is:
 * Architecture definition and implementation for [WhatsappKeyguardAssistEngine].
 *
 * What it does:
 * Provides autonomous lockscreen unlocking (swipe gestures, accessibility PIN entry, coordinate tapping) and security relocking post-task via [android.app.admin.DevicePolicyManager].
 *
 * Linked With:
 * - Services & Engines: [android.accessibilityservice.AccessibilityService], [android.accessibilityservice.GestureDescription]
 * - Core Security & Utilities: [android.app.KeyguardManager], [android.util.Log]
 * - Android Framework & Jetpack: [android.graphics.Path], [android.os.Build], [android.os.Handler], [android.view.accessibility.AccessibilityNodeInfo]
 */

package com.jmd.helper.whatsapp

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
 * What it is:
 * Autonomous keyguard screen entry assistance and security relock engine.
 *
 * What it does:
 * Handles detection of active lockscreens, executing upward swipe gestures to invoke the PIN bouncer, sequentially entering PIN digits via element actions, confirming entry, and halting upon error detection (e.g. cooldown timers or wrong PIN warnings).
 *
 * Linked with:
 * - Architecture: [WhatsappKeyguardAssistEngine], [KeyguardAssistEngine]
 * @property service The active [AccessibilityService] used for gesture dispatch and element interaction.
 * @property handler Handler on the main thread for coordinating timed sequential clicks.
 * @property loggerTag Logging identifier tag for lockscreen telemetry.
 */
class KeyguardAssistEngine(
    private val service: AccessibilityService,
    private val handler: Handler,
    private val loggerTag: String = "KeyguardAssistEngine"
) {

    // Universal policy-compliant properties
    @Volatile var isAssistPending = false
    @Volatile var isAssistingDigits = false
    @Volatile var assistPin = ""
    @Volatile var shouldRelockAfterTask = false
    @Volatile var isTestAssistMode = false

    var assistAttemptCount = 0
    var assistLoopAttempts = 0

    // Backward-compatible properties with bidirectional delegation
    var isUnlockPending: Boolean
        get() = isAssistPending
        set(value) { isAssistPending = value }

    var isEnteringPinDigits: Boolean
        get() = isAssistingDigits
        set(value) { isAssistingDigits = value }

    var unlockPin: String
        get() = assistPin
        set(value) { assistPin = value }

    var shouldRelockAfterSend: Boolean
        get() = shouldRelockAfterTask
        set(value) { shouldRelockAfterTask = value }

    var isTestUnlockMode: Boolean
        get() = isTestAssistMode
        set(value) { isTestAssistMode = value }

    var unlockAttemptCount: Int
        get() = assistAttemptCount
        set(value) { assistAttemptCount = value }

    var unlockLoopAttempts: Int
        get() = assistLoopAttempts
        set(value) { assistLoopAttempts = value }

    private var lastSwipeTime = 0L
    private var lastUnlockActionTime = 0L
    /**
     * What it is:
     * Execution routine for [resetInitialDelay].
     *
     * What it does:
     * Coordinates resetInitialDelay operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [WhatsappKeyguardAssistEngine]
     */

    fun resetInitialDelay() {
        assistAttemptCount = 0
        assistLoopAttempts = 0
        isAssistingDigits = false
        lastSwipeTime = System.currentTimeMillis() + 600L
    }

    /**
     * What it is:
     * Orchestrates the keyguard unlock evaluation loop.
     *
     * What it does:
     * Evaluates lock state via [KeyguardManager], checks for lockout errors or cooldowns, performs upward swipes to reveal the bouncer if necessary, and dispatches numeric PIN entry or text input.
     *
     * Linked with:
     * - Architecture: [WhatsappKeyguardAssistEngine], [handleKeyguardUnlock]
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
                            lastSwipeTime = now + 800L
                            Log.i(loggerTag, "Clicked explicit switch button to open PIN keypad. Waiting for bouncer...")
                            handler.postDelayed({ handleKeyguardUnlock(onUnlockSuccess, onUnlockAbort) }, 750L)
                            return
                        }
                    }
                }

                if (isBiometricPromptVisible(roots) && now - lastSwipeTime > 600L) {
                    for (root in roots) {
                        if (tryClickSwitchToNumberLock(root)) {
                            lastSwipeTime = now + 800L
                            Log.i(loggerTag, "Dismissed biometric prompt to open PIN keypad. Waiting for bouncer...")
                            handler.postDelayed({ handleKeyguardUnlock(onUnlockSuccess, onUnlockAbort) }, 750L)
                            return
                        }
                    }
                }

                if (now - lastSwipeTime > 900L && unlockAttemptCount < 5) {
                    lastSwipeTime = now
                    unlockAttemptCount++
                    Log.i(loggerTag, "Auto-swiping UP on lockscreen to reveal keypad (attempt $unlockAttemptCount of 5)...")
                    swipeUpToRevealBouncer {
                        handler.postDelayed({ handleKeyguardUnlock(onUnlockSuccess, onUnlockAbort) }, 500L)
                    }
                    return
                } else if (unlockAttemptCount >= 5) {
                    Log.w(loggerTag, "Reached max swipe attempts (5). Stopping auto-unlock.")
                    isUnlockPending = false
                    isEnteringPinDigits = false
                    unlockAttemptCount = 0
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

    /**
     * What it is:
     * Primary policy-compliant alias for [handleKeyguardUnlock].
     *
     * What it does:
     * Executes [handleKeyguardScreenAssist] logic in accordance with system and security invariants.
     *
     * Linked with:
     * - Architecture: [WhatsappKeyguardAssistEngine], [handleKeyguardScreenAssist]
     */
    fun handleKeyguardScreenAssist(
        onSuccess: () -> Unit,
        onAbort: (reason: String) -> Unit
    ) = handleKeyguardUnlock(onSuccess, onAbort)
    /**
     * What it is:
     * Execution routine for [enterKeypadDigitsAndSubmit].
     *
     * What it does:
     * Coordinates enterKeypadDigitsAndSubmit operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [WhatsappKeyguardAssistEngine]
     */

    private fun enterKeypadDigitsAndSubmit(
        pin: String,
        onUnlockSuccess: () -> Unit,
        onUnlockAbort: (reason: String) -> Unit
    ) {
        if (isEnteringPinDigits) return
        isEnteringPinDigits = true
        Log.i(loggerTag, "Starting element-based PIN keypad entry for ${pin.length} digits...")
        /**
         * What it is:
         * Operational routine [tapNextDigit].
         *
         * What it does:
         * Executes tapNextDigit actions according to system invariants.
         *
         * Linked with:
         * - Component: [WhatsappKeyguardAssistEngine]
         */

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
                unlockAttemptCount = 0
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
                                Log.d(loggerTag, "Dispatched physical tap on keyguard keypad entry [${index + 1}/${pin.length}] at ($tapX, $tapY)")
                                break
                            }
                        }
                    }
                }

                if (!digitTapped) {
                    val containerRect = findKeypadContainer(currentRoots)
                    val (tapX, tapY) = if (containerRect != null && containerRect.width() > 200 && containerRect.height() > 200) {
                        val colIdx = when (digitChar) {
                            '1', '4', '7' -> 0
                            '2', '5', '8', '0' -> 1
                            '3', '6', '9' -> 2
                            else -> 1
                        }
                        val rowIdx = when (digitChar) {
                            '1', '2', '3' -> 0
                            '4', '5', '6' -> 1
                            '7', '8', '9' -> 2
                            '0' -> 3
                            else -> 3
                        }
                        val x = containerRect.left + (containerRect.width() * (2 * colIdx + 1) / 6f)
                        val y = containerRect.top + (containerRect.height() * (2 * rowIdx + 1) / 8f)
                        Pair(x, y)
                    } else {
                        val dm = service.resources.displayMetrics
                        val width = dm.widthPixels.toFloat()
                        val height = dm.heightPixels.toFloat()
                        val isTallScreen = height > 2400f
                        val (colRatio, rowRatio) = when (digitChar) {
                            '1' -> Pair(0.241f, if (isTallScreen) 0.530f else 0.524f)
                            '2' -> Pair(0.500f, if (isTallScreen) 0.530f else 0.524f)
                            '3' -> Pair(0.759f, if (isTallScreen) 0.530f else 0.524f)
                            '4' -> Pair(0.241f, if (isTallScreen) 0.635f else 0.619f)
                            '5' -> Pair(0.500f, if (isTallScreen) 0.635f else 0.619f)
                            '6' -> Pair(0.759f, if (isTallScreen) 0.635f else 0.619f)
                            '7' -> Pair(0.241f, if (isTallScreen) 0.740f else 0.714f)
                            '8' -> Pair(0.500f, if (isTallScreen) 0.740f else 0.714f)
                            '9' -> Pair(0.759f, if (isTallScreen) 0.740f else 0.714f)
                            '0' -> Pair(0.500f, if (isTallScreen) 0.845f else 0.809f)
                            else -> Pair(0.500f, if (isTallScreen) 0.845f else 0.809f)
                        }
                        Pair(width * colRatio, height * rowRatio)
                    }
                    dispatchPhysicalTap(tapX, tapY)
                    Log.d(loggerTag, "Coordinate fallback physical tap for keyguard keypad entry [${index + 1}/${pin.length}] at ($tapX, $tapY) (container=${containerRect != null})")
                }

                handler.postDelayed({
                    tapNextDigit(index + 1)
                }, 220L)
            } else {
                handler.postDelayed({
                    val checkKm = service.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
                    if (checkKm?.isKeyguardLocked == false) {
                        Log.i(loggerTag, "Device confirmed unlocked immediately after digits (no Enter needed)")
                        isEnteringPinDigits = false
                        isUnlockPending = false
                        unlockAttemptCount = 0
                        onUnlockSuccess()
                        return@postDelayed
                    }

                    val currentRoots = AccessibilityNodeUtils.getTargetRoots(service)
                    for (r in currentRoots) {
                        val enterNode = findKeypadEnterNode(r)
                        if (enterNode != null && !isDeleteOrBackspaceNode(enterNode)) {
                            val rect = android.graphics.Rect()
                            enterNode.getBoundsInScreen(rect)
                            if (rect.width() > 0 && rect.height() > 0) {
                                val tapX = rect.centerX().toFloat()
                                val tapY = rect.centerY().toFloat()
                                if (dispatchPhysicalTap(tapX, tapY)) {
                                    Log.i(loggerTag, "Dispatched physical tap on designated Enter/OK element at ($tapX, $tapY)")
                                    break
                                }
                            }
                        }
                    }

                    // Never blindly tap bottom-right coordinate fallback when no Enter node is found,
                    // as row 4 column 3 on modern Android devices (Vivo, Samsung, Pixel) is the Delete/Backspace button!
                    /**
                     * What it is:
                     * Operational routine [pollUnlockVerification].
                     *
                     * What it does:
                     * Executes pollUnlockVerification actions according to system invariants.
                     *
                     * Linked with:
                     * - Component: [WhatsappKeyguardAssistEngine]
                     */
                    fun pollUnlockVerification(attempt: Int) {
                        val finalKm = service.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
                        if (finalKm?.isKeyguardLocked == false) {
                            Log.i(loggerTag, "Device confirmed unlocked following PIN entry (attempt $attempt)")
                            isEnteringPinDigits = false
                            isUnlockPending = false
                            unlockAttemptCount = 0
                            onUnlockSuccess()
                        } else if (attempt < 6) {
                            handler.postDelayed({ pollUnlockVerification(attempt + 1) }, 350L)
                        } else {
                            Log.w(loggerTag, "Device still locked after submission and 2.1s timeout. Stopping.")
                            isUnlockPending = false
                            isEnteringPinDigits = false
                            unlockAttemptCount = 0
                            onUnlockAbort("Device remained locked after PIN submission")
                        }
                    }

                    handler.postDelayed({ pollUnlockVerification(1) }, 400L)
                }, 500L)
            }
        }

        tapNextDigit(0)
    }
    /**
     * What it is:
     * Execution routine for [findKeypadContainer].
     *
     * What it does:
     * Coordinates findKeypadContainer operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [WhatsappKeyguardAssistEngine]
     */

    private fun findKeypadContainer(roots: List<AccessibilityNodeInfo>): android.graphics.Rect? {
        val containerIds = listOf(
            "pinContainer", "keyguard_pin_view", "pin_view", "numeric_keypad",
            "keyboard_container", "keypad_layout", "sec_pin_view", "vivo_pin_view"
        )
        for (root in roots) {
            for (id in containerIds) {
                val nodes = AccessibilityNodeUtils.findNodesByViewId(root, "com.android.systemui", id)
                for (node in nodes) {
                    val rect = android.graphics.Rect()
                    node.getBoundsInScreen(rect)
                    if (rect.width() > 200 && rect.height() > 200) {
                        return rect
                    }
                }
            }
        }
        return null
    }
    /**
     * What it is:
     * Execution routine for [findKeypadDigitNode].
     *
     * What it does:
     * Coordinates findKeypadDigitNode operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [WhatsappKeyguardAssistEngine]
     */

    private fun findKeypadDigitNode(root: AccessibilityNodeInfo, digit: Char): AccessibilityNodeInfo? {
        val dm = service.resources.displayMetrics
        val minKeypadY = dm.heightPixels * 0.40f

        val digitIds = listOf(
            // Vivo / iQOO
            "VivoPinkey$digit", "vivopinkey$digit", "vivo_keypad_num_$digit", "vivo_pin_key_$digit",
            // Samsung One UI
            "sec_key$digit", "sec_pin_key$digit", "sec_keyboard_key$digit", "sec_keypad_key_$digit", "sec_keypad_num_$digit",
            // Xiaomi / Redmi / POCO
            "numeric_key_$digit", "pin_number_$digit", "pin_num_$digit", "keyboard_num_$digit", "key_$digit",
            // Oppo / Realme / OnePlus
            "color_pin_$digit", "coui_keyboard_key$digit", "coui_pin_num_$digit",
            // Motorola / Lenovo
            "moto_pin_$digit", "moto_keypad_num_$digit",
            // Transsion / Huawei / Honor / Asus
            "keypad_key_$digit", "transsion_pin_$digit", "hw_pin_$digit", "huawei_keypad_num_$digit", "asus_pin_$digit",
            // Standard AOSP / Pixel / Nokia / Common
            "key$digit", "button$digit", "num$digit", "btn$digit", "btn_$digit",
            "digit$digit", "digit_$digit", "kl_button$digit"
        )
        for (id in digitIds) {
            val nodes = AccessibilityNodeUtils.findNodesByViewId(root, "com.android.systemui", id)
            val inKeypad = nodes.firstOrNull { node ->
                val rect = android.graphics.Rect()
                node.getBoundsInScreen(rect)
                rect.centerY() >= minKeypadY && rect.width() > 0 && rect.height() > 0
            }
            if (inKeypad != null) return inKeypad
        }

        // Subtext-aware digit node resolution strictly bounded to the keypad area (preventing clock/notification matches)
        val d = digit.toString()
        val directNode = with(AccessibilityNodeUtils) {
            root.findFirstDescendant { node ->
                val rect = android.graphics.Rect()
                node.getBoundsInScreen(rect)
                if (rect.centerY() < minKeypadY || rect.width() <= 0 || rect.height() <= 0) {
                    return@findFirstDescendant false
                }
                if (isDeleteOrBackspaceNode(node)) return@findFirstDescendant false

                val text = node.text?.toString()?.trim() ?: ""
                val desc = node.contentDescription?.toString()?.trim() ?: ""
                text == d || desc == d ||
                    (text.startsWith(d) && (text.length == 1 || text[1] == '\n' || text[1] == '\r')) ||
                    (desc.startsWith(d) && (desc.length == 1 || desc[1] == ',' || desc[1] == '\n'))
            }
        }
        if (directNode != null) return directNode

        return null
    }
    /**
     * What it is:
     * Execution routine for [isDeleteOrBackspaceNode].
     *
     * What it does:
     * Coordinates isDeleteOrBackspaceNode operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [WhatsappKeyguardAssistEngine]
     */

    private fun isDeleteOrBackspaceNode(node: AccessibilityNodeInfo): Boolean {
        val id = node.viewIdResourceName?.lowercase().orEmpty()
        val text = node.text?.toString()?.lowercase()?.trim().orEmpty()
        val desc = node.contentDescription?.toString()?.lowercase()?.trim().orEmpty()
        return id.contains("delete") || id.contains("backspace") || id.contains("clear") || id.contains("0right") ||
               desc.contains("delete") || desc.contains("backspace") || desc.contains("clear") ||
               text.contains("delete") || text.contains("clear") || text == "⌫" || text == "del"
    }
    /**
     * What it is:
     * Execution routine for [findKeypadEnterNode].
     *
     * What it does:
     * Coordinates findKeypadEnterNode operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [WhatsappKeyguardAssistEngine]
     */

    private fun findKeypadEnterNode(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val dm = service.resources.displayMetrics
        val minKeypadY = dm.heightPixels * 0.40f

        val enterIds = listOf(
            // Samsung
            "sec_key_enter", "sec_btn_ok", "sec_key_ok", "sec_pin_ok", "sec_pin_enter", "sec_key_done",
            // Vivo
            "vivo_keypad_enter", "vivo_pin_confirm", "key_enter",
            // Xiaomi
            "btn_confirm", "confirm_btn", "key_enter", "key_submit",
            // Oppo / OnePlus / Realme
            "coui_keyboard_confirm", "btn_confirm", "coui_btn_confirm", "color_pin_enter",
            // Transsion / Huawei / Honor / Motorola
            "keypad_key_enter", "btn_enter", "pin_enter_btn", "hw_pin_ok", "moto_pin_enter",
            // AOSP / Pixel / Nokia / Standard
            "key_enter_text", "button_next", "check", "enter", "btn_ok", "ok", "done"
        )
        for (id in enterIds) {
            val nodes = AccessibilityNodeUtils.findNodesByViewId(root, "com.android.systemui", id)
            val valid = nodes.firstOrNull { node ->
                if (isDeleteOrBackspaceNode(node)) return@firstOrNull false
                val rect = android.graphics.Rect()
                node.getBoundsInScreen(rect)
                rect.centerY() >= minKeypadY && rect.width() > 0 && rect.height() > 0
            }
            if (valid != null) return valid
        }

        return with(AccessibilityNodeUtils) {
            root.findFirstDescendant { node ->
                if (isDeleteOrBackspaceNode(node)) return@findFirstDescendant false
                val rect = android.graphics.Rect()
                node.getBoundsInScreen(rect)
                if (rect.centerY() < minKeypadY || rect.width() <= 0 || rect.height() <= 0) {
                    return@findFirstDescendant false
                }
                val text = node.text?.toString()?.lowercase()?.trim() ?: ""
                val desc = node.contentDescription?.toString()?.lowercase()?.trim() ?: ""
                val resId = node.viewIdResourceName?.lowercase().orEmpty()
                resId.contains("enter") || resId.contains("confirm") || resId.contains("submit") ||
                    resId.contains("done") || desc.contains("enter") || desc.contains("done") ||
                    desc.contains("confirm") || text == "ok" || text == "enter" || text == "✓" ||
                    text == "done" || text == "confirm"
            }
        }
    }

    /**
     * What it is:
     * Locates and clicks the "Enter", "OK", or checkmark button on the lockscreen PIN keypad.
     *
     * What it does:
     * Executes [clickKeypadEnter] logic in accordance with system and security invariants.
     *
     * Linked with:
     * - Architecture: [WhatsappKeyguardAssistEngine], [clickKeypadEnter]
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
    /**
     * What it is:
     * Execution routine for [dispatchPhysicalTap].
     *
     * What it does:
     * Coordinates dispatchPhysicalTap operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [WhatsappKeyguardAssistEngine]
     */

    private fun dispatchPhysicalTap(x: Float, y: Float, durationMs: Long = 50L): Boolean {
        return try {
            AccessibilityNodeUtils.lastGestureTimestamp = System.currentTimeMillis()
            val path = Path().apply { moveTo(x, y) }
            val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
            val gesture = GestureDescription.Builder().addStroke(stroke).build()
            service.dispatchGesture(gesture, null, null)
        } catch (t: Throwable) {
            Log.e(loggerTag, "dispatchPhysicalTap failed: ${t.message}")
            false
        }
    }
    /**
     * What it is:
     * Execution routine for [findPinInputField].
     *
     * What it does:
     * Coordinates findPinInputField operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [WhatsappKeyguardAssistEngine]
     */

    private fun findPinInputField(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val inputIds = listOf(
            "pinEntry", "passwordEntry", "pin_entry", "password_entry",
            "lockPassword", "password", "pin", "entry", "vivo_pin_input",
            "sec_pin_entry", "sec_password_entry", "coui_pin_input",
            "color_password_entry", "miui_pin_entry", "numeric_input"
        )
        for (id in inputIds) {
            val nodes = AccessibilityNodeUtils.findNodesByViewId(root, "com.android.systemui", id)
            if (nodes.isNotEmpty()) return nodes.first()
        }

        with(AccessibilityNodeUtils) {
            return root.findFirstDescendant { it.className?.toString()?.contains("EditText", ignoreCase = true) == true }
        }
    }
    /**
     * What it is:
     * Execution routine for [hasKeypadDigits].
     *
     * What it does:
     * Coordinates hasKeypadDigits operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [WhatsappKeyguardAssistEngine]
     */

    private fun hasKeypadDigits(root: AccessibilityNodeInfo): Boolean {
        var foundCount = 0
        for (digit in listOf('1', '2', '3', '4', '5')) {
            if (findKeypadDigitNode(root, digit) != null) foundCount++
        }
        return foundCount >= 2
    }
    /**
     * What it is:
     * Execution routine for [tryClickSwitchToNumberLock].
     *
     * What it does:
     * Coordinates tryClickSwitchToNumberLock operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [WhatsappKeyguardAssistEngine]
     */

    private fun tryClickSwitchToNumberLock(root: AccessibilityNodeInfo): Boolean {
        val switchKeywords = listOf("use pin", "enter pin", "switch to pin", "use password", "enter password", "switch to password")
        val switchIds = listOf("use_pin", "btn_use_pin", "switch_to_pin", "button_secondary", "switch_to_password", "pin_switch")
        for (id in switchIds) {
            val nodes = AccessibilityNodeUtils.findNodesByViewId(root, "com.android.systemui", id)
            if (nodes.isNotEmpty()) {
                if (AccessibilityNodeUtils.clickElement(nodes.first(), service)) return true
            }
        }
        val textNode = AccessibilityNodeUtils.findNodeByTextOrDesc(root, switchKeywords)
        return textNode != null && AccessibilityNodeUtils.clickElement(textNode, service)
    }
    /**
     * What it is:
     * Execution routine for [isBiometricPromptVisible].
     *
     * What it does:
     * Coordinates isBiometricPromptVisible operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [WhatsappKeyguardAssistEngine]
     */

    private fun isBiometricPromptVisible(roots: List<AccessibilityNodeInfo>): Boolean {
        val keywords = listOf(
            "touch the fingerprint sensor", "fingerprint", "face not recognized", "biometric",
            "position your face", "face to unlock", "face unlock"
        )
        val viewIds = listOf(
            "vivo_face_unlock_icon", "finger_indicator_layout", "face_unlock_icon",
            "biometric_prompt", "fingerprint_icon", "sec_biometric_face_icon",
            "sem_biometric_icon", "coui_fingerprint_icon", "moto_fingerprint_icon",
            "miui_keyguard_face_unlock_view"
        )
        for (r in roots) {
            if (AccessibilityNodeUtils.findNodeByTextOrDesc(r, keywords) != null) return true
            for (id in viewIds) {
                if (AccessibilityNodeUtils.findNodesByViewId(r, "com.vivo.systemuiplugin", id).isNotEmpty()) return true
                if (AccessibilityNodeUtils.findNodesByViewId(r, "com.android.systemui", id).isNotEmpty()) return true
            }
        }
        return false
    }
    /**
     * What it is:
     * Execution routine for [isLockscreenErrorOrCooldownPresent].
     *
     * What it does:
     * Coordinates isLockscreenErrorOrCooldownPresent operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [WhatsappKeyguardAssistEngine]
     */

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
    /**
     * What it is:
     * Execution routine for [swipeUpToRevealBouncer].
     *
     * What it does:
     * Coordinates swipeUpToRevealBouncer operations conforming to application constraints.
     *
     * Linked with:
     * - Component: [WhatsappKeyguardAssistEngine]
     */

    private fun swipeUpToRevealBouncer(onComplete: () -> Unit) {
        try {
            AccessibilityNodeUtils.lastGestureTimestamp = System.currentTimeMillis()
            val dm = service.resources.displayMetrics
            val width = dm.widthPixels.toFloat()
            val height = dm.heightPixels.toFloat()
            val startY = height * 0.88f
            val endY = height * 0.22f

            val builder = GestureDescription.Builder()
            // Alternate between single-finger and two-finger strokes based on attempt count
            if (unlockAttemptCount % 2 != 0) {
                // Single-finger center swipe up (fast flick from bottom chin, clearing fingerprint sensor zone)
                val singlePath = Path().apply {
                    moveTo(width * 0.50f, startY)
                    lineTo(width * 0.50f, endY)
                }
                builder.addStroke(GestureDescription.StrokeDescription(singlePath, 0, 180L))
                Log.d(loggerTag, "Dispatching single-finger swipe up bouncer gesture (attempt $unlockAttemptCount)")
            } else {
                // Two-finger parallel swipe up
                val path1 = Path().apply {
                    moveTo(width * 0.35f, startY)
                    lineTo(width * 0.35f, endY)
                }
                val path2 = Path().apply {
                    moveTo(width * 0.65f, startY)
                    lineTo(width * 0.65f, endY)
                }
                builder.addStroke(GestureDescription.StrokeDescription(path1, 0, 200L))
                builder.addStroke(GestureDescription.StrokeDescription(path2, 0, 200L))
                Log.d(loggerTag, "Dispatching two-finger swipe up bouncer gesture (attempt $unlockAttemptCount)")
            }

            val gesture = builder.build()
            service.dispatchGesture(gesture, object : AccessibilityService.GestureResultCallback() {
                /**
                 * What it is:
                 * Accessibility gesture callback [onCompleted].
                 *
                 * What it does:
                 * Handles gesture completion or cancellation dispatch for onCompleted.
                 *
                 * Linked with:
                 * - Component: [WhatsappKeyguardAssistEngine]
                 */
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    Log.d(loggerTag, "Swipe gesture completed")
                    handler.postDelayed({ onComplete() }, 300L)
                }
                /**
                 * What it is:
                 * Accessibility gesture callback [onCancelled].
                 *
                 * What it does:
                 * Handles gesture completion or cancellation dispatch for onCancelled.
                 *
                 * Linked with:
                 * - Component: [WhatsappKeyguardAssistEngine]
                 */

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    Log.w(loggerTag, "Swipe gesture cancelled")
                    handler.postDelayed({ onComplete() }, 300L)
                }
            }, null)
        } catch (t: Throwable) {
            Log.e(loggerTag, "Failed swipeUpToRevealBouncer", t)
            handler.postDelayed({ onComplete() }, 300L)
        }
    }
}

/**
 * Backward-compatible typealias for [KeyguardAssistEngine].
 */
typealias KeyguardUnlockEngine = KeyguardAssistEngine

