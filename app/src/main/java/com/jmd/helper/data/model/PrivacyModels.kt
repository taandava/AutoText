/**
 * Package: com.jmd.helper.data.model
 * Module: Application Core Subsystem
 *
 * What it is:
 * Architecture definition and implementation for [PrivacyModels].
 *
 * What it does:
 * Defines data models and immutable configuration contracts for per-application privacy, system visibility controls, content masking, and accessibility scoping.
 *
 * Linked With:
 * - Architecture: Standard application component linkages
 */

package com.jmd.helper.data.model

/**
 * What it is:
 * Granular privacy and interaction configuration assigned to an individual application package.
 *
 * What it does:
 * Encapsulates state, business contracts, and operations for [AppPrivacyConfig].
 *
 * Linked with:
 * - Architecture: [PrivacyModels], [AppPrivacyConfig]
 * @property packageName Unique Android package name (e.g., "com.saraswatbank.gomobile", "com.phonepe.app").
 * @property appName Human-readable application label.
 * @property isExcluded Master flag indicating whether Helper isolates from this application.
 * @property disableTaskAction When true, Helper never dispatches actions inside or targeted at this app.
 * @property blockOverlays When true, Helper suppresses floating overlays, popups, and wake-windows when this app is active.
 * @property suppressNotifications When true, non-critical Helper notifications are suppressed while this app is in the foreground.
 * @property disableClipboard When true, Helper never reads from or writes to the system clipboard while interacting with this app.
 * @property maskSensitiveContent When true, message contents, contact names, and phone numbers related to this app are masked in logs.
 * @property disableRules When true, incoming notification auto-replies or trigger rules originating from this app are ignored.
 * @property customNotes Optional user notes or security audit labels.
 * @property updatedAt Epoch timestamp of the last configuration change.
 */
data class AppPrivacyConfig(
    val packageName: String,
    val appName: String,
    val isExcluded: Boolean = true,
    val disableTaskAction: Boolean = true,
    val blockOverlays: Boolean = true,
    val suppressNotifications: Boolean = true,
    val disableClipboard: Boolean = true,
    val maskSensitiveContent: Boolean = true,
    val disableRules: Boolean = true,
    val customNotes: String = "",
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * What it is:
 * UI representation of an installed application on the device for selection and privacy configuration.
 *
 * What it does:
 * Encapsulates state, business contracts, and operations for [AppInfoItem].
 *
 * Linked with:
 * - Architecture: [PrivacyModels], [AppInfoItem]
 * @property packageName Unique application package identifier.
 * @property appName Localized application name.
 * @property isSystemApp True if the package is installed in the system partition.
 * @property isBankingOrFinancial True if the package is identified as a banking, payment, or financial application.
 * @property isExcluded True if the application is currently marked as excluded.
 * @property config Associated [AppPrivacyConfig] if configured, or default configuration.
 */
data class AppInfoItem(
    val packageName: String,
    val appName: String,
    val isSystemApp: Boolean = false,
    val isBankingOrFinancial: Boolean = false,
    val isExcluded: Boolean = false,
    val config: AppPrivacyConfig? = null
)

/**
 * What it is:
 * Global operational preferences controlling application-wide privacy, visibility, and system integration.
 *
 * What it does:
 * Encapsulates state, business contracts, and operations for [GlobalPrivacySettings].
 *
 * Linked with:
 * - Architecture: [PrivacyModels], [GlobalPrivacySettings]
 * @property isPrivacyModeEnabled Master safety toggle for all privacy restrictions.
 * @property autoExcludeBankingApps Directly includes known banking and UPI applications in privacy shielding.
 * @property maskNotificationsOnLockScreen Replaces sensitive message text on lockscreen notifications with generic status text.
 * @property maskExecutionLogs Masks message snippets in execution audit logs (e.g., replacement with asterisks).
 * @property autoClearClipboardSeconds Timeout in seconds to directly wipe system clipboard after task (0 = disabled).
 * @property smartAccessibilityScoping Restricts AccessibilityServiceInfo.packageNames to only authorized target apps.
 * @property dynamicA11yOnDemand Dynamically enables accessibility service only during active dispatches if WRITE_SECURE_SETTINGS is granted.
 * @property suppressNotifsOverExcluded Suppresses heads-up notifications when any excluded application is active in the foreground.
 * @property blockOverlaysOverExcluded Prevents floating windows or simulated call overlays while an excluded app is foregrounded.
 */
data class GlobalPrivacySettings(
    val isPrivacyModeEnabled: Boolean = true,
    val autoExcludeBankingApps: Boolean = true,
    val maskNotificationsOnLockScreen: Boolean = true,
    val maskExecutionLogs: Boolean = false,
    val autoClearClipboardSeconds: Int = 15,
    val smartAccessibilityScoping: Boolean = true,
    val dynamicA11yOnDemand: Boolean = true,
    val suppressNotifsOverExcluded: Boolean = true,
    val blockOverlaysOverExcluded: Boolean = true
)
