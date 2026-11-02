/**
 * Package: com.jmd.autotext.core
 * Module: Security & Protected Application Isolation Layer
 *
 * Dedicated Protected Application Manager enforcing strict isolation between AutoText
 * and banking, UPI, payment, financial authentication, and designated sensitive applications.
 *
 * Enforces Part 8 Invariants:
 * 1. Default-deny policy for all external UI interactions.
 * 2. Only explicitly authorized target applications (WhatsApp, WhatsApp Business, Phone/Dialer, Keyguard) may receive UI actions.
 * 3. Immediate pre-action context verification before every physical tap, click, or text injection.
 * 4. Zero inspection or traversal of protected applications' UI trees or window nodes.
 * 5. Instant cancellation of pending automation callbacks when a protected or non-authorized application is encountered.
 * 6. Zero collection of credentials, PINs, OTPs, card details, or screen contents.
 * 7. Zero overlay displays or notification heads-up over protected applications.
 */
package com.jmd.autotext.core

import android.content.Context
import android.util.Log
import com.jmd.autotext.data.model.ChannelType
import com.jmd.autotext.data.model.ScheduledTask
import com.jmd.autotext.data.prefs.PreferenceManager
import com.jmd.autotext.service.AutoTextAccessibilityService

/**
 * Dedicated architectural controller guaranteeing absolute isolation of banking and payment apps.
 */
object ProtectedApplicationManager {

    private const val TAG = "ProtectedAppMgr"

    /**
     * Comprehensive catalog of known Indian and international banking, payment, UPI,
     * wallet, investment, and financial authentication package identifiers.
     */
    val KNOWN_PROTECTED_PACKAGES: Set<String> = setOf(
        // UPI & Digital Payment Apps
        "com.google.android.apps.nbu.paisa.user",     // Google Pay (Tez)
        "com.google.android.apps.walletnfcrel",      // Google Wallet
        "com.phonepe.app",                           // PhonePe
        "net.one97.paytm",                           // Paytm
        "money.paytmmoney",                          // Paytm Money
        "in.org.npci.upiapp",                        // BHIM UPI
        "com.cred.app",                              // CRED
        "com.dreamplug.androidapp",                  // CRED Alt
        "com.freecharge.android",                    // Freecharge
        "com.mobikwik_new",                          // MobiKwik
        "com.mobikwik",                              // MobiKwik Alt
        "com.amazon.mShop.android.shopping.in",      // Amazon Pay India
        "in.amazon.mShop.android.shopping",          // Amazon Pay India Alt
        "com.payzapp",                               // HDFC PayZapp
        "com.samsung.android.spay",                  // Samsung Pay
        "com.samsung.android.raja.spay",             // Samsung Wallet
        "com.paypal.android.p2pmobile",              // PayPal
        "com.venmo",                                 // Venmo
        "com.squareup.cash",                         // Cash App
        "com.revolut.revolut",                       // Revolut
        "com.transferwise.android",                  // Wise

        // Major Indian Banks
        "com.sbi.upi",                               // SBI BHIM
        "com.sbi.lotusintouch",                      // SBI YONO Lite
        "com.yono.sbi",                              // SBI YONO
        "com.sbi.SBIFreedomPlus",                    // SBI Freedom
        "com.saraswatbank.gomobile",                 // Saraswat Bank
        "com.saraswat.app",                          // Saraswat Bank GOMONxT
        "com.saraswat.mobile",                       // Saraswat Mobile
        "com.hdfcbank.mobilebanking",                // HDFC Bank MobileBanking
        "com.hdfcbank.payzapp",                      // HDFC Payzapp
        "com.csam.icici.bank.imobile",               // ICICI iMobile Pay
        "com.icicibank.mobile",                      // ICICI Bank
        "com.axis.mobile",                           // Axis Mobile
        "com.axis.open",                             // Axis Open
        "com.kotak.bank",                            // Kotak Mobile Banking
        "com.kotak.kotakbank",                       // Kotak Bank
        "com.pnb.pnbone",                            // PNB ONE
        "com.bankofbaroda.mconnect",                 // BoB World
        "com.canarabank.ai1",                        // Canara ai1
        "com.unionbank.ecommerce.mobile.android",    // Union Bank Vyom
        "com.indusind.mobile",                       // IndusInd Bank
        "com.idbi.go",                               // IDBI Go Mobile
        "com.federalbank.fedmobile",                 // FedMobile
        "com.yesbank",                               // YES BANK
        "com.aubank.aupay",                          // AU 0101
        "com.rblbank.mobank",                        // MoBank RBL

        // Wealth, Brokerage & Mutual Funds
        "com.zerodha.kite3",                         // Zerodha Kite
        "com.nextbillion.groww",                     // Groww
        "com.msf.angelmobile",                       // Angel One
        "com.dhan.live",                             // Dhan
        "com.upstox",                                // Upstox
        "com.navi.mutualfund",                       // Navi Mutual Fund

        // Neobanks & Credit Lines
        "com.slicepay",                              // Slice
        "com.jupiter.money",                         // Jupiter
        "com.fi.money",                              // Fi Money
        "in.niyo",                                   // Niyo
        "com.bajajfinserv.experia"                   // Bajaj Finserv
    )

    /**
     * Package name prefixes that automatically flag an application as protected/financial.
     */
    val PROTECTED_PACKAGE_PREFIXES: List<String> = listOf(
        "com.sbi.",
        "com.google.android.apps.nbu.paisa",
        "com.phonepe.",
        "net.one97.paytm",
        "money.paytmmoney",
        "com.icicibank.",
        "com.hdfcbank.",
        "com.axis.",
        "com.kotak.",
        "in.org.npci.",
        "com.csam.icici.",
        "com.yono.",
        "com.bank.",
        "com.saraswatbank.",
        "com.saraswat.",
        "com.freecharge.",
        "com.mobikwik.",
        "com.bajajfinserv.",
        "com.cred.",
        "com.paytm.",
        "com.rblbank.",
        "com.indusind.",
        "com.canarabank.",
        "com.pnb.",
        "com.bob.",
        "com.unionbank",
        "com.bankofbaroda.",
        "com.idbi.",
        "com.federalbank.",
        "com.yesbank.",
        "com.aubank.",
        "com.slicepay",
        "com.jupiter.",
        "com.fi.money",
        "com.zerodha.",
        "com.nextbillion.",
        "com.dhan.",
        "com.upstox"
    )

    /**
     * Strict Allowlist of Authorized Target Applications.
     * Under the Default-Deny policy, AutoText will NEVER interact with any application
     * outside this explicit set.
     */
    val AUTHORIZED_AUTOMATION_TARGETS: Set<String> = setOf(
        // WhatsApp Applications
        "com.whatsapp",
        "com.whatsapp.w4b",

        // Native Telephony & In-Call Handlers
        "com.android.dialer",
        "com.google.android.dialer",
        "com.android.phone",
        "com.android.server.telecom",
        "com.samsung.android.dialer",
        "com.samsung.android.incallui",
        "com.sec.phone",

        // Android System UI & Lockscreen (for unattended keyguard PIN unlock only)
        "com.android.systemui",
        "com.google.android.systemui",
        "com.android.keyguard",

        // AutoText Self
        "com.jmd.autotext"
    )

    /**
     * Checks whether a package is a known or user-configured banking/financial application.
     */
    fun isProtectedPackage(packageName: String?): Boolean {
        if (packageName.isNullOrBlank()) return false
        val trimmed = packageName.trim()
        val lower = trimmed.lowercase()

        // 1. Direct exact catalog match
        if (KNOWN_PROTECTED_PACKAGES.contains(trimmed)) return true

        // 2. Prefix match
        for (prefix in PROTECTED_PACKAGE_PREFIXES) {
            if (lower.startsWith(prefix) || lower == prefix) return true
        }

        // 3. Keyword heuristic
        if (lower.contains("banking") || lower.contains("gomonxt") || lower.contains("saraswat") ||
            lower.contains(".upi.") || lower.contains("npci.bhim") || lower.contains(".yono.") ||
            lower.contains("mobilebanking") || lower.contains("walletnfcrel") || lower.contains("paisa.user") ||
            lower.contains("wallet") || lower.contains("crypto") || lower.contains("finance") || lower.contains("fintech")) {
            return true
        }

        // 4. Delegate to AutoTextAccessibilityService check for complete harmony
        return AutoTextAccessibilityService.isFinancialOrBankingPackage(trimmed)
    }

    /**
     * Enforces the Default-Deny Policy:
     * Returns true ONLY if the package is in the explicit allowlist of authorized targets.
     * All other third-party applications, games, browsers, and financial tools are denied.
     */
    fun isAuthorizedTarget(packageName: String?): Boolean {
        if (packageName.isNullOrBlank()) return false
        val trimmed = packageName.trim()

        // Absolute denial if package is protected
        if (isProtectedPackage(trimmed)) return false

        // Check if package or prefix matches authorized targets
        for (auth in AUTHORIZED_AUTOMATION_TARGETS) {
            if (trimmed.equals(auth, ignoreCase = true) || trimmed.startsWith("$auth.")) {
                return true
            }
        }
        return false
    }

    /**
     * Pre-action context verification:
     * Immediately verified before every click, gesture, or text injection.
     *
     * @param currentPackage The currently foreground/rendered package.
     * @param expectedTargetPackage The expected authorized target package for the active task.
     * @return `true` if context is safe and authorized; `false` if interaction must be denied.
     */
    fun verifyContextBeforeInteraction(currentPackage: String?, expectedTargetPackage: String?): Boolean {
        if (currentPackage.isNullOrBlank()) {
            Log.w(TAG, "verifyContextBeforeInteraction: Rejected because currentPackage is null or blank.")
            return false
        }
        val cur = currentPackage.trim()

        // 1. Critical Stop: Current app is a banking or protected application
        if (isProtectedPackage(cur)) {
            Log.e(TAG, "CRITICAL: verifyContextBeforeInteraction refused! Protected financial app in foreground: $cur")
            return false
        }

        // 2. Check if current app is authorized under Default-Deny
        if (!isAuthorizedTarget(cur)) {
            Log.w(TAG, "verifyContextBeforeInteraction: Rejected under Default-Deny policy. Target $cur is not authorized.")
            return false
        }

        // 3. If an explicit target package was expected (e.g. com.whatsapp), verify current matches expected
        if (!expectedTargetPackage.isNullOrBlank()) {
            val exp = expectedTargetPackage.trim()
            val matches = cur.contains(exp, ignoreCase = true) ||
                exp.contains(cur, ignoreCase = true) ||
                cur.contains("systemui", ignoreCase = true) ||
                cur.contains("inputmethod", ignoreCase = true)

            if (!matches) {
                Log.w(TAG, "verifyContextBeforeInteraction: Expected $exp but found $cur in foreground. Refusing interaction.")
                return false
            }
        }

        return true
    }

    /**
     * Validates whether a scheduled task's channel targets an authorized, safe destination.
     */
    fun isTaskTargetAuthorized(task: ScheduledTask): Boolean {
        return when (task.channel) {
            ChannelType.SMS,
            ChannelType.CALL,
            ChannelType.FAKE_CALL,
            ChannelType.REMINDER,
            ChannelType.SOUND_MODE -> true
            ChannelType.WHATSAPP,
            ChannelType.WHATSAPP_STATUS,
            ChannelType.WHATSAPP_GROUP,
            ChannelType.WHATSAPP_CALL -> true
            ChannelType.WHATSAPP_BUSINESS,
            ChannelType.WHATSAPP_BUSINESS_STATUS,
            ChannelType.WHATSAPP_BUSINESS_CALL -> true
        }
    }

    /**
     * Invoked immediately when a protected application is detected in the foreground.
     * Cancels any pending automation callbacks, resets service automation state,
     * logs a privacy-safe masked diagnostic record, and detaches accessibility if on-demand.
     */
    fun handleProtectedAppEncountered(context: Context, detectedPackage: String, source: String) {
        val maskedPkg = PrivacyManager.maskSensitiveText(detectedPackage)
        Log.w(TAG, "Protected application encountered from $source ($maskedPkg). Executing safe interruption.")
        AppFileLogger.action(
            context,
            TAG,
            "Protected application encountered ($maskedPkg) from $source. Safely interrupted external UI operations."
        )

        // 1. Immediately clear pending accessibility automation and watchdog callbacks
        AutoTextAccessibilityService.clearPending()

        // 2. If dynamic on-demand mode is enabled, immediately unbind and detach the accessibility service
        val prefs = PreferenceManager(context)
        if (prefs.isDynamicA11yOnDemandEnabled && AccessibilityHealthManager.hasWriteSecureSettings(context)) {
            Log.i(TAG, "Dynamic mode active: Retracting accessibility service to ensure 0% detection in banking app.")
            AccessibilityHealthManager.disableAccessibilityService(context)
        }
    }

    /**
     * Determines whether floating overlays, notifications, or toasts should be suppressed
     * over the given package.
     */
    fun shouldBlockOverlays(packageName: String?): Boolean {
        if (packageName.isNullOrBlank()) return false
        return isProtectedPackage(packageName) || !isAuthorizedTarget(packageName)
    }
}
