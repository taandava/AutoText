

/**
 * Package: com.jmd.autotext.whatsapp
 * Module: WhatsApp Automation Subsystem
 *
 * Scans window hierarchies to detect and dismiss intrusive blocking overlays, system permission dialogs,
 * incoming call caller ID overlays (e.g. Truecaller), and notification shades before starting UI automation.
 *
 * Linked With:
 * - Service: [com.jmd.autotext.service.AutoTextAccessibilityService]
 * - Engines: [com.jmd.autotext.whatsapp.WhatsAppDirectSendEngine], [com.jmd.autotext.whatsapp.WhatsAppStatusEngine]
 */
package com.jmd.autotext.whatsapp

import android.accessibilityservice.AccessibilityService
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo

/**
 * Resilient overlay inspector detecting and dismissing UI obstacles.
 */
object PopupShield {
    private const val TAG = "PopupShield"
    private val handler = Handler(Looper.getMainLooper())

    private val dismissKeywords = listOf(
        "allow", "continue", "accept", "ok", "got it", "never", "remind later",
        "skip", "close", "deny", "not now", "maybe later", "dismiss", "i agree",
        "agree", "confirm", "yes", "no thanks", "no, thanks"
    )

    /**
     * Executes an asynchronous multi-pass loop to dismiss any blocking popups, dialogs,
     * or pulled-down notification shades before automation commences.
     *
     * @param service The active [AccessibilityService] used to query root windows and invoke dismiss actions.
     * @param onComplete Callback invoked on the main looper once all obstacles are cleared or max passes are exhausted.
     */
    fun clearAllBlockingPopups(service: AccessibilityService, onComplete: () -> Unit) {
        clearPass(service, pass = 0, maxPasses = 8, onComplete = onComplete)
    }

    /**
     * Executes a single pass of UI cleanup, re-scheduling subsequent passes until the window hierarchy is clear.
     *
     * @param service The active [AccessibilityService].
     * @param pass The current pass iteration index (0-indexed).
     * @param maxPasses The maximum allowed retry attempts before relinquishing control to `onComplete`.
     * @param onComplete Callback executed when the view hierarchy is clear of blocking overlays.
     */
    private fun clearPass(
        service: AccessibilityService,
        pass: Int,
        maxPasses: Int,
        onComplete: () -> Unit
    ) {
        try {
            val root = service.rootInActiveWindow
            if (root == null) {
                if (pass < maxPasses) {
                    handler.postDelayed({ clearPass(service, pass + 1, maxPasses, onComplete) }, 300L)
                } else {
                    onComplete()
                }
                return
            }

            val curPkg = root.packageName?.toString().orEmpty().lowercase()

            // 1. Clear any active notification shade or floating overlays (Truecaller, Messenger, system prompts)
            if (clearOverlaysAndNotificationShade(service)) {
                handler.postDelayed({ clearPass(service, pass + 1, maxPasses, onComplete) }, 350L)
                return
            }

            // 2. Click any dismiss/allow/ok style dialog button present.
            if (clickDismissButton(root, service)) {
                handler.postDelayed({ clearPass(service, pass + 1, maxPasses, onComplete) }, 300L)
                return
            }

            // 3. Nothing left to clear -> done.
            Log.i(TAG, "PopupShield: clear complete after pass $pass (pkg=$curPkg)")
            onComplete()
        } catch (t: Throwable) {
            Log.e(TAG, "PopupShield error", t)
            onComplete()
        }
    }

    /**
     * Inspects all visible windows on screen for floating overlays, heads-up banners,
     * or open notification shades that are not the target application.
     *
     * @param service The active [AccessibilityService] providing window introspection.
     * @param targetPkg The target package expected to hold user focus (defaults to "com.whatsapp").
     * @return `true` if an active blocking overlay or extended notification shade is detected; `false` otherwise.
     */
    fun hasActiveBlockingOverlay(service: AccessibilityService, targetPkg: String = "com.whatsapp"): Boolean {
        try {
            val windows = service.windows.orEmpty()
            val metrics = service.resources.displayMetrics
            val screenHeight = metrics.heightPixels

            for (window in windows) {
                val windowType = window.type
                val windowPkg = window.root?.packageName?.toString()?.lowercase().orEmpty()

                if (windowPkg.isBlank()) continue

                val rect = android.graphics.Rect()
                window.getBoundsInScreen(rect)
                val isSystemBar = (rect.top == 0 && rect.height() < screenHeight * 0.15f) ||
                    (rect.bottom >= screenHeight && rect.height() < screenHeight * 0.15f)

                if (windowPkg.contains("systemui")) {
                    if (rect.height() > screenHeight * 0.35f && !isSystemBar) {
                        return true
                    }
                    continue
                }

                if (windowPkg.contains("keyguard")) {
                    return true
                }

                val isOverlayType = windowType == AccessibilityWindowInfo.TYPE_SYSTEM ||
                    windowType == AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY

                if (isOverlayType && !isSystemBar &&
                    !windowPkg.contains(targetPkg, ignoreCase = true) &&
                    !windowPkg.contains("w4b", ignoreCase = true) &&
                    windowPkg != service.packageName.lowercase()
                ) {
                    return true
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Error checking active overlays: ${t.message}")
        }
        return false
    }

    /**
     * Clears all open notification shades, floating overlays, Truecaller popups,
     * and system dialogs to leave the screen clean and ready for automation clicks.
     *
     * @param service The active [AccessibilityService] used to perform dismiss actions and global back navigation.
     * @param targetPkg The target application package name.
     * @return `true` if any overlay, notification shade, or dialog button was closed; `false` otherwise.
     */
    fun clearOverlaysAndNotificationShade(service: AccessibilityService, targetPkg: String = "com.whatsapp"): Boolean {
        try {
            var clearedSomething = false

            // 1. Check for open notification shade / system UI (only if not on keyguard/lockscreen)
            val km = service.getSystemService(android.content.Context.KEYGUARD_SERVICE) as? android.app.KeyguardManager
            val isKeyguardLocked = km?.isKeyguardLocked == true

            val root = service.rootInActiveWindow
            val curPkg = root?.packageName?.toString().orEmpty().lowercase()
            val metrics = service.resources.displayMetrics
            val screenHeight = metrics.heightPixels
            val rect = android.graphics.Rect()
            root?.getBoundsInScreen(rect)

            val isNotificationShade = !isKeyguardLocked && (
                curPkg.contains("notification") ||
                (curPkg.contains("systemui") && rect.height() > screenHeight * 0.35f)
            )

            if (isNotificationShade) {
                Log.i(TAG, "Dismissing pulled-down notification shade window ($curPkg)...")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE)
                } else {
                    service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                }
                clearedSomething = true
            }

            // 2. Check for floating overlay windows (Truecaller, Messenger, Call overlays)
            val windows = service.windows.orEmpty()
            for (window in windows) {
                val windowType = window.type
                val windowRoot = window.root
                val windowPkg = windowRoot?.packageName?.toString()?.lowercase().orEmpty()

                val isOverlayType = windowType == AccessibilityWindowInfo.TYPE_SYSTEM ||
                    windowType == AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY

                val isSafeSystemPkg = windowPkg.contains("launcher") || windowPkg.contains("home") ||
                    windowPkg.contains("systemui") || windowPkg.contains("settings") ||
                    windowPkg.contains("inputmethod") || windowPkg.contains("gboard") ||
                    windowPkg.contains("permission") || windowPkg.contains("installer") ||
                    windowPkg == "android"

                if (isOverlayType && windowRoot != null &&
                    windowPkg.isNotBlank() &&
                    !isSafeSystemPkg &&
                    !windowPkg.contains(targetPkg, ignoreCase = true) &&
                    !windowPkg.contains("w4b", ignoreCase = true) &&
                    windowPkg != service.packageName.lowercase()
                ) {
                    Log.i(TAG, "Floating overlay detected from package: $windowPkg (type=$windowType)")

                    if (clickDismissButton(windowRoot, service)) {
                        Log.i(TAG, "Dismissed overlay button in $windowPkg")
                        clearedSomething = true
                    }
                }
            }

            // 3. Clear any visible dismiss/allow dialog buttons on active root (strictly non-system)
            val rootPkg = root?.packageName?.toString()?.lowercase().orEmpty()
            val isRootSafeSystem = rootPkg.contains("launcher") || rootPkg.contains("home") ||
                rootPkg.contains("systemui") || rootPkg.contains("settings") ||
                rootPkg.contains("inputmethod") || rootPkg.contains("gboard") ||
                rootPkg.contains("permission") || rootPkg.contains("installer") ||
                rootPkg == "android"

            if (root != null && !isRootSafeSystem && clickDismissButton(root, service)) {
                clearedSomething = true
            }

            return clearedSomething
        } catch (t: Throwable) {
            Log.e(TAG, "Error during clearOverlaysAndNotificationShade", t)
            return false
        }
    }

    /**
     * Executes a single synchronous pass over the view hierarchy to find and click any visible
     * dismiss, OK, accept, close, or permission dialog buttons.
     *
     * @param root The root [AccessibilityNodeInfo] representing the active window or popup hierarchy.
     * @param service The active [AccessibilityService] used to perform the element click action.
     * @return `true` if a matching dismissal button was clicked; `false` otherwise.
     */
    fun clickDismissButton(root: AccessibilityNodeInfo, service: AccessibilityService): Boolean {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var scanned = 0
        while (queue.isNotEmpty() && scanned < 80) {
            val curr = queue.removeFirst()
            scanned++
            val txt = curr.text?.toString()?.lowercase().orEmpty()
            val desc = curr.contentDescription?.toString()?.lowercase().orEmpty()
            val id = curr.viewIdResourceName?.lowercase().orEmpty()
            val pkg = curr.packageName?.toString()?.lowercase().orEmpty()

            // Strictly protect system auto-rotate, quick settings tiles, and system UI controls
            if (pkg.contains("systemui") || pkg.contains("settings") ||
                txt.contains("rotate") || txt.contains("rotation") ||
                desc.contains("rotate") || desc.contains("rotation") ||
                id.contains("rotate") || id.contains("rotation") || id.contains("tile")
            ) {
                continue
            }

            val isDialogButton = curr.isClickable &&
                (dismissKeywords.any { txt == it || desc == it } ||
                    id.contains("button1") || id.contains("button2") || id.contains("btn_close") ||
                    id.contains("close"))
            if (isDialogButton && AccessibilityNodeUtils.clickElement(curr, service)) {
                Log.d(TAG, "PopupShield dismissed button: $txt / $desc")
                return true
            }
            for (i in 0 until curr.childCount) {
                curr.getChild(i)?.let { queue.add(it) }
            }
        }
        return false
    }
}
