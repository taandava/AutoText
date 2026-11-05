/**
 * Package: com.jmd.helper.core
 * Module: OEM Brand Architecture & Deep System Integration
 *
 * Provides comprehensive, unified device manufacturer detection, autostart intents,
 * OEM-specific battery optimization settings, floating window/overlay routing,
 * safe system overlay filtering, and brand-tailored user guidance across:
 * - Oppo & Realme (ColorOS / Realme UI / OPlus)
 * - Vivo & iQOO (Funtouch OS / OriginOS)
 * - Samsung (One UI)
 * - Xiaomi, Redmi & POCO (MIUI / HyperOS)
 * - Motorola & Lenovo (MyUX / Hello UI)
 * - Nokia & HMD Global (Pure Android / Evenwell)
 * - OnePlus (OxygenOS / ColorOS)
 * - Huawei & Honor (EMUI / MagicOS)
 * - Transsion: Tecno, Infinix, itel (HiOS / XOS / itelOS)
 * - Asus & ROG (ZenUI / ROG UI)
 * - Google Pixel, Sony Xperia, Nothing Phone, and Generic AOSP
 */
package com.jmd.helper.core

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.core.net.toUri

/**
 * Enumeration representing all major Android device manufacturers.
 */
enum class OemBrand(val displayName: String) {
    SAMSUNG("Samsung (One UI)"),
    XIAOMI("Xiaomi / Redmi / POCO (MIUI / HyperOS)"),
    OPPO("Oppo (ColorOS)"),
    VIVO("Vivo / iQOO (Funtouch OS / OriginOS)"),
    ONEPLUS("OnePlus (OxygenOS)"),
    REALME("Realme (Realme UI)"),
    MOTOROLA("Motorola (MyUX / Hello UI)"),
    NOKIA("Nokia (Pure Android)"),
    HUAWEI("Huawei (EMUI / HarmonyOS)"),
    HONOR("Honor (MagicOS)"),
    TRANSSION("Tecno / Infinix / itel (HiOS / XOS)"),
    ASUS("Asus / ROG (ZenUI)"),
    GOOGLE("Google Pixel (Pure Android)"),
    SONY("Sony (Xperia)"),
    NOTHING("Nothing Phone (Nothing OS)"),
    GENERIC("Android Device");
}

/**
 * Master OEM helper for brand-aware intent routing, overlays, keyguards, and user guidance.
 */
object OemBrandHelper {

    private val manufacturerLower: String
        get() = (Build.MANUFACTURER ?: "").lowercase()
    private val brandLower: String
        get() = (Build.BRAND ?: "").lowercase()
    private val modelLower: String
        get() = (Build.MODEL ?: "").lowercase()

    /**
     * The actively detected OEM brand for this device.
     */
    val currentBrand: OemBrand by lazy { detectBrand() }

    /**
     * Resolves the [OemBrand] enum matching the active hardware and software properties.
     */
    private fun detectBrand(): OemBrand {
        return when {
            manufacturerLower.contains("samsung") || brandLower.contains("samsung") -> OemBrand.SAMSUNG
            manufacturerLower.contains("xiaomi") || brandLower.contains("xiaomi") ||
                brandLower.contains("redmi") || brandLower.contains("poco") -> OemBrand.XIAOMI
            brandLower.contains("oneplus") || manufacturerLower.contains("oneplus") -> OemBrand.ONEPLUS
            brandLower.contains("realme") || manufacturerLower.contains("realme") -> OemBrand.REALME
            manufacturerLower.contains("oppo") || brandLower.contains("oppo") -> OemBrand.OPPO
            manufacturerLower.contains("vivo") || brandLower.contains("vivo") ||
                brandLower.contains("iqoo") || manufacturerLower.contains("iqoo") -> OemBrand.VIVO
            manufacturerLower.contains("motorola") || brandLower.contains("motorola") ||
                brandLower.contains("moto") || manufacturerLower.contains("lenovo") -> OemBrand.MOTOROLA
            manufacturerLower.contains("hmd") || brandLower.contains("nokia") ||
                manufacturerLower.contains("nokia") -> OemBrand.NOKIA
            brandLower.contains("honor") || manufacturerLower.contains("honor") -> OemBrand.HONOR
            manufacturerLower.contains("huawei") || brandLower.contains("huawei") -> OemBrand.HUAWEI
            manufacturerLower.contains("transsion") || brandLower.contains("tecno") ||
                brandLower.contains("infinix") || brandLower.contains("itel") -> OemBrand.TRANSSION
            manufacturerLower.contains("asus") || brandLower.contains("asus") -> OemBrand.ASUS
            manufacturerLower.contains("google") || brandLower.contains("google") -> OemBrand.GOOGLE
            manufacturerLower.contains("sony") || brandLower.contains("sony") -> OemBrand.SONY
            manufacturerLower.contains("nothing") || brandLower.contains("nothing") -> OemBrand.NOTHING
            else -> OemBrand.GENERIC
        }
    }

    fun isSamsung(): Boolean = currentBrand == OemBrand.SAMSUNG
    fun isXiaomi(): Boolean = currentBrand == OemBrand.XIAOMI
    fun isOppo(): Boolean = currentBrand == OemBrand.OPPO
    fun isVivo(): Boolean = currentBrand == OemBrand.VIVO
    fun isOnePlus(): Boolean = currentBrand == OemBrand.ONEPLUS
    fun isRealme(): Boolean = currentBrand == OemBrand.REALME
    fun isMotorola(): Boolean = currentBrand == OemBrand.MOTOROLA
    fun isNokia(): Boolean = currentBrand == OemBrand.NOKIA
    fun isHuawei(): Boolean = currentBrand == OemBrand.HUAWEI
    fun isHonor(): Boolean = currentBrand == OemBrand.HONOR
    fun isTranssion(): Boolean = currentBrand == OemBrand.TRANSSION
    fun isAsus(): Boolean = currentBrand == OemBrand.ASUS

    /**
     * Human-readable device identity for UI diagnostic cards (e.g. "Vivo V2401 — Funtouch OS / OriginOS").
     */
    fun getDeviceSummary(): String {
        val brandName = when (currentBrand) {
            OemBrand.SAMSUNG -> "Samsung"
            OemBrand.XIAOMI -> "Xiaomi / POCO"
            OemBrand.OPPO -> "Oppo"
            OemBrand.VIVO -> "Vivo / iQOO"
            OemBrand.ONEPLUS -> "OnePlus"
            OemBrand.REALME -> "Realme"
            OemBrand.MOTOROLA -> "Motorola"
            OemBrand.NOKIA -> "Nokia"
            OemBrand.HUAWEI -> "Huawei"
            OemBrand.HONOR -> "Honor"
            OemBrand.TRANSSION -> "Tecno / Infinix"
            OemBrand.ASUS -> "Asus"
            OemBrand.GOOGLE -> "Google Pixel"
            OemBrand.SONY -> "Sony Xperia"
            OemBrand.NOTHING -> "Nothing"
            OemBrand.GENERIC -> (Build.MANUFACTURER?.takeIf { it.isNotBlank() } ?: "Android").replaceFirstChar { it.uppercase() }
        }
        val model = (Build.MODEL ?: "").trim()
        return if (model.isNotBlank() && !model.equals("unknown", ignoreCase = true)) "$brandName ($model)" else brandName
    }

    /**
     * Returns prioritized OEM-specific autostart and background management intents.
     * Tries brand-tailored manager activities first, followed by AOSP fallbacks.
     */
    fun getAutostartIntents(context: Context): List<Intent> {
        val intents = mutableListOf<Intent>()
        val pkg = context.packageName

        when (currentBrand) {
            OemBrand.VIVO -> {
                intents.add(Intent().apply {
                    component = ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.PurviewTabActivity")
                })
                intents.add(Intent().apply {
                    component = ComponentName("com.vivo.abe", "com.vivo.abe.ui.ExcessivePowerManagerActivity")
                })
                intents.add(Intent().apply {
                    component = ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity")
                })
                intents.add(Intent().apply {
                    component = ComponentName("com.iqoo.secure", "com.iqoo.secure.MainGuideActivity")
                })
                intents.add(Intent().apply {
                    component = ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.SoftPermissionDetailActivity")
                    putExtra("packagename", pkg)
                })
            }
            OemBrand.OPPO, OemBrand.REALME -> {
                intents.add(Intent().apply {
                    component = ComponentName("com.oplus.safecenter", "com.oplus.safecenter.permission.startup.StartupAppListActivity")
                })
                intents.add(Intent().apply {
                    component = ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity")
                })
                intents.add(Intent().apply {
                    component = ComponentName("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity")
                })
                intents.add(Intent().apply {
                    component = ComponentName("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity")
                })
                intents.add(Intent().apply {
                    component = ComponentName("com.oplus.safecenter", "com.oplus.safecenter.permission.startupapp.StartupAppListActivity")
                })
            }
            OemBrand.ONEPLUS -> {
                intents.add(Intent().apply {
                    component = ComponentName("com.oneplus.security", "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity")
                })
                intents.add(Intent().apply {
                    component = ComponentName("com.oplus.safecenter", "com.oplus.safecenter.permission.startup.StartupAppListActivity")
                })
                intents.add(Intent().apply {
                    component = ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity")
                })
            }
            OemBrand.SAMSUNG -> {
                intents.add(Intent().apply {
                    component = ComponentName("com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity")
                })
                intents.add(Intent().apply {
                    component = ComponentName("com.samsung.android.sm", "com.samsung.android.sm.ui.battery.BatteryActivity")
                })
                intents.add(Intent().apply {
                    component = ComponentName("com.samsung.android.sm_cn", "com.samsung.android.sm.ui.battery.BatteryActivity")
                })
                intents.add(Intent().apply {
                    component = ComponentName("com.samsung.android.sm", "com.samsung.android.sm.app.dashboard.SmartManagerDashBoardActivity")
                })
            }
            OemBrand.XIAOMI -> {
                intents.add(Intent().apply {
                    component = ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")
                })
                intents.add(Intent().apply {
                    component = ComponentName("com.miui.powerkeeper", "com.miui.powerkeeper.ui.HiddenAppsConfigActivity")
                    putExtra("package_name", pkg)
                    putExtra("package_label", "AutoText")
                })
                intents.add(Intent("miui.intent.action.APP_PERM_EDITOR").apply {
                    setClassName("com.miui.securitycenter", "com.miui.permcenter.permissions.PermissionsEditorActivity")
                    putExtra("extra_pkgname", pkg)
                })
            }
            OemBrand.MOTOROLA -> {
                intents.add(Intent().apply {
                    component = ComponentName("com.motorola.ccc.notification", "com.motorola.ccc.notification.NotificationActivity")
                })
                intents.add(Intent().apply {
                    component = ComponentName("com.motorola.powersave", "com.motorola.powersave.PowerSaveActivity")
                })
                intents.add(Intent().apply {
                    component = ComponentName("com.motorola.bodyguard", "com.motorola.bodyguard.MainActivity")
                })
            }
            OemBrand.NOKIA -> {
                intents.add(Intent().apply {
                    component = ComponentName("com.evenwell.powersaving.g3", "com.evenwell.powersaving.g3.exception.PowerSaverExceptionActivity")
                })
                intents.add(Intent().apply {
                    component = ComponentName("com.evenwell.emm", "com.evenwell.emm.PowerManagerActivity")
                })
            }
            OemBrand.HUAWEI -> {
                intents.add(Intent().apply {
                    component = ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity")
                })
                intents.add(Intent().apply {
                    component = ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity")
                })
            }
            OemBrand.HONOR -> {
                intents.add(Intent().apply {
                    component = ComponentName("com.hihonor.systemmanager", "com.hihonor.systemmanager.optimize.process.ProtectActivity")
                })
                intents.add(Intent().apply {
                    component = ComponentName("com.hihonor.systemmanager", "com.hihonor.systemmanager.startupmgr.ui.StartupNormalAppListActivity")
                })
                intents.add(Intent().apply {
                    component = ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity")
                })
            }
            OemBrand.TRANSSION -> {
                intents.add(Intent().apply {
                    component = ComponentName("com.transsion.phonemanager", "com.transsion.phonemanager.settings.AutoStartSettingsActivity")
                })
                intents.add(Intent().apply {
                    component = ComponentName("com.transsion.phonemanager", "com.transsion.phonemanager.battery.PowerManagementActivity")
                })
            }
            OemBrand.ASUS -> {
                intents.add(Intent().apply {
                    component = ComponentName("com.asus.mobilemanager", "com.asus.mobilemanager.autostart.AutoStartActivity")
                })
                intents.add(Intent().apply {
                    component = ComponentName("com.asus.mobilemanager", "com.asus.mobilemanager.entry.FunctionActivity")
                })
            }
            OemBrand.SONY -> {
                intents.add(Intent().apply {
                    component = ComponentName("com.sonymobile.cta", "com.sonymobile.cta.SomcCTAMainActivity")
                })
            }
            else -> {
                // Generic / Pixel / Nothing
            }
        }

        // Standard Android AOSP Fallbacks
        intents.add(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        intents.add(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = "package:$pkg".toUri()
        })

        return intents
    }

    /**
     * Launches the best matching OEM autostart or battery setting screen.
     */
    fun openOemBackgroundSettings(context: Context): Boolean {
        for (intent in getAutostartIntents(context)) {
            try {
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)
                return true
            } catch (_: Exception) {
                // Continue to next fallback
            }
        }
        return false
    }

    /**
     * Resolves OEM-specific floating window or special overlay permission screens.
     * Several OEMs (e.g. Xiaomi, Vivo, Oppo) require an explicit secondary toggle
     * beyond standard [Settings.canDrawOverlays] for background popup display.
     */
    fun getSpecialOverlayIntents(context: Context): List<Intent> {
        val intents = mutableListOf<Intent>()
        val pkg = context.packageName

        when (currentBrand) {
            OemBrand.XIAOMI -> {
                intents.add(Intent("miui.intent.action.APP_PERM_EDITOR").apply {
                    setClassName("com.miui.securitycenter", "com.miui.permcenter.permissions.PermissionsEditorActivity")
                    putExtra("extra_pkgname", pkg)
                })
            }
            OemBrand.VIVO -> {
                intents.add(Intent().apply {
                    component = ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.SoftPermissionDetailActivity")
                    putExtra("packagename", pkg)
                })
                intents.add(Intent().apply {
                    component = ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.PurviewTabActivity")
                })
            }
            OemBrand.OPPO, OemBrand.REALME -> {
                intents.add(Intent().apply {
                    component = ComponentName("com.coloros.safecenter", "com.coloros.safecenter.sysfloatwindow.FloatWindowListActivity")
                })
                intents.add(Intent().apply {
                    component = ComponentName("com.oplus.safecenter", "com.oplus.safecenter.sysfloatwindow.FloatWindowListActivity")
                })
            }
            OemBrand.HUAWEI, OemBrand.HONOR -> {
                intents.add(Intent().apply {
                    component = ComponentName("com.huawei.systemmanager", "com.huawei.permissionmanager.ui.MainActivity")
                })
                intents.add(Intent().apply {
                    component = ComponentName("com.hihonor.systemmanager", "com.hihonor.permissionmanager.ui.MainActivity")
                })
            }
            else -> {}
        }

        // Standard Overlay intent
        intents.add(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, "package:$pkg".toUri()))
        intents.add(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
        return intents
    }

    /**
     * Checks whether a visible window belongs to a known OEM system navigation element,
     * sidebar, or floating assistant that must NOT be treated as a blocking third-party popup.
     */
    fun isSafeSystemOverlay(packageName: String): Boolean {
        if (packageName.isBlank()) return false
        val pkg = packageName.lowercase()

        // Standard Android System UI and Launchers
        if (pkg.contains("systemui") || pkg.contains("launcher3") || pkg == "android") {
            return true
        }

        // Vivo / iQOO
        if (pkg.contains("com.vivo.upslide") || pkg.contains("com.vivo.systemuiplugin") ||
            pkg.contains("com.vivo.globalsearch") || pkg.contains("com.vivo.smartshot")) {
            return true
        }

        // Samsung One UI
        if (pkg.contains("com.samsung.android.app.cocktailbarservice") ||
            pkg.contains("com.samsung.android.sidebarservice") ||
            pkg.contains("com.samsung.android.honeyboard") ||
            pkg.contains("com.sec.android.app.launcher") ||
            pkg.contains("com.samsung.android.app.taskedge")) {
            return true
        }

        // Xiaomi / Redmi / POCO
        if (pkg.contains("com.miui.freeform") || pkg.contains("com.miui.voiceassist") ||
            pkg.contains("com.miui.screenrecorder") || pkg.contains("com.miui.touchassistant") ||
            pkg.contains("com.miui.home")) {
            return true
        }

        // Oppo / OnePlus / Realme
        if (pkg.contains("com.coloros.floatassistant") || pkg.contains("com.oplus.sublauncher") ||
            pkg.contains("com.coloros.gesture") || pkg.contains("com.oplus.gesture") ||
            pkg.contains("com.coloros.smartsidebar") || pkg.contains("com.oppo.launcher") ||
            pkg.contains("com.oneplus.launcher")) {
            return true
        }

        // Motorola / Lenovo
        if (pkg.contains("com.motorola.gestures") || pkg.contains("com.motorola.launcher3") ||
            pkg.contains("com.motorola.visualgesture")) {
            return true
        }

        // Transsion
        if (pkg.contains("com.transsion.smartpanel") || pkg.contains("com.transsion.xlauncher")) {
            return true
        }

        // Huawei / Honor
        if (pkg.contains("com.huawei.android.launcher") || pkg.contains("com.huawei.dock") ||
            pkg.contains("com.hihonor.android.launcher")) {
            return true
        }

        // Google Pixel
        if (pkg.contains("com.google.android.apps.nexuslauncher") || pkg.contains("com.google.android.inputmethod.latin")) {
            return true
        }

        return false
    }

    /**
     * Step-by-step user instructions customized to the detected device brand for keeping
     * AutoText alive in the background and granting autostart privileges.
     */
    fun getOemGuidance(): List<String> {
        return when (currentBrand) {
            OemBrand.VIVO -> listOf(
                "1. Open Settings > Battery > 'Background power consumption' > Find AutoText and select 'High background power consumption'.",
                "2. Open Settings > More settings > Permission management > 'Autostart' > Enable AutoText.",
                "3. In Recent Apps overview, tap the three dots (⋮) or pull down on AutoText > Tap 'Lock' so the padlock appears.",
                "4. Enable 'Floating window' / 'Display over other apps' in Settings > More Settings > Permission management."
            )
            OemBrand.SAMSUNG -> listOf(
                "1. Open Settings > Apps > AutoText > Battery > Select 'Unrestricted'.",
                "2. Open Settings > Battery > 'Background usage limits' > 'Never sleeping apps' > Tap '+' and add AutoText.",
                "3. In Recent Apps (||| button), tap the AutoText app icon at the top of the card > Tap 'Keep open' (padlock icon).",
                "4. Settings > Battery > More battery settings > Turn OFF 'Put unused apps to sleep'."
            )
            OemBrand.XIAOMI -> listOf(
                "1. Open Security app > Manage apps > AutoText > Turn ON 'Autostart'.",
                "2. In the same AutoText screen > Battery saver > Choose 'No restrictions'.",
                "3. In Other permissions > Enable 'Display pop-up windows while running in the background'.",
                "4. In Recent Apps screen > Long-press the AutoText card > Tap the Padlock icon to lock it in RAM."
            )
            OemBrand.OPPO, OemBrand.REALME -> listOf(
                "1. Open Settings > Battery > More battery settings > App battery management > AutoText > Enable 'Allow background activity' and 'Allow auto-launch'.",
                "2. In Phone Manager / Safecenter > Privacy permissions > Startup manager > Ensure AutoText is ON.",
                "3. In Recent Apps overview > Tap the three dots (⋮) on AutoText > Select 'Lock'.",
                "4. Settings > Special app access > Display over other apps > Allow AutoText."
            )
            OemBrand.ONEPLUS -> listOf(
                "1. Open Settings > Battery > Battery usage > AutoText > Set to 'Don't optimize' (Unrestricted).",
                "2. Open Settings > Apps > Auto-launch > Enable AutoText.",
                "3. In Recent Apps > Tap three dots (⋮) on AutoText > Select 'Lock'.",
                "4. Settings > Apps > Special app access > Display over other apps > Ensure enabled."
            )
            OemBrand.MOTOROLA -> listOf(
                "1. Open Settings > Apps > AutoText > Battery > Select 'Unrestricted'.",
                "2. Open Settings > Battery > Adaptive Battery > Tap 'Exempt apps' and add AutoText.",
                "3. In Settings > Apps > Special app access > Display over other apps > Verify granted.",
                "4. In Recent Apps screen, keep AutoText active without swiping it away."
            )
            OemBrand.NOKIA -> listOf(
                "1. Open Settings > Apps & notifications > AutoText > Battery > Battery optimization > Select 'Not optimized'.",
                "2. If 'Evenwell PowerSaver' or 'Duraspeed' is present in your build, disable battery saver for AutoText.",
                "3. Settings > Apps > Special app access > Display over other apps > Verify granted."
            )
            OemBrand.HUAWEI, OemBrand.HONOR -> listOf(
                "1. Open Settings > Apps > App launch > Find AutoText > Turn OFF 'Auto manage' > Enable 'Auto launch', 'Secondary launch', and 'Run in background'.",
                "2. Open Settings > Battery > More battery settings > Stay connected when device sleeps > ON.",
                "3. In Recent Apps screen, pull down on the AutoText card to lock it with a padlock."
            )
            OemBrand.TRANSSION -> listOf(
                "1. Open Phone Master / Phone Manager > Auto-start management > Enable AutoText.",
                "2. Open Power Marathon / Battery Lab > High power consumption apps > Exclude AutoText.",
                "3. In Recent Apps screen, lock AutoText to prevent memory purging."
            )
            OemBrand.ASUS -> listOf(
                "1. Open Mobile Manager > PowerMaster > Auto-start Manager > Allow AutoText.",
                "2. Settings > Battery > Battery optimization > Select 'Don't optimize' for AutoText."
            )
            else -> listOf(
                "1. Open Settings > Apps > AutoText > Battery > Select 'Unrestricted'.",
                "2. Ensure 'Allow background activity' and 'Schedule exact alarms' are enabled in App Info.",
                "3. Keep AutoText locked or active in Recent Apps for guaranteed background tasks."
            )
        }
    }

    /**
     * Populates brand-specific and standard telephony extras into [bundle] for multi-SIM calling.
     *
     * @param bundle Target Intent or Telecom extras bundle.
     * @param targetPhysicalSlot Physical SIM slot index (0 for SIM 1, 1 for SIM 2).
     * @param subId Cellular subscription identifier (if available from SubscriptionManager).
     */
    fun populateTelecomSimExtras(bundle: android.os.Bundle, targetPhysicalSlot: Int, subId: Int? = null) {
        // Standard Android Telecom / Telephony extras
        bundle.putInt("android.telecom.extra.PHONE_ACCOUNT_HANDLE_INDEX", targetPhysicalSlot)
        bundle.putInt("com.android.phone.extra.slot", targetPhysicalSlot)
        bundle.putInt("simSlot", targetPhysicalSlot)
        bundle.putInt("sim_slot", targetPhysicalSlot)
        bundle.putInt("slot", targetPhysicalSlot)

        // Samsung One UI
        bundle.putInt("phone_id", targetPhysicalSlot)

        // Xiaomi / Redmi / POCO (MIUI / HyperOS)
        bundle.putInt("simId", targetPhysicalSlot)
        bundle.putInt("slotId", targetPhysicalSlot)
        bundle.putInt("slot_id", targetPhysicalSlot)

        // Vivo / iQOO (Funtouch OS / OriginOS)
        bundle.putInt("simnum", targetPhysicalSlot)
        bundle.putInt("sim_index", targetPhysicalSlot)

        // Transsion / Motorola / Asus
        bundle.putInt("slot_idx", targetPhysicalSlot)

        // Cellular Subscription ID (Huawei, Honor, Android AOSP)
        if (subId != null && subId >= 0) {
            bundle.putInt("android.telephony.extra.SUBSCRIPTION_INDEX", subId)
            bundle.putInt("subscription", subId)
            bundle.putInt("sub_id", subId)
        }
    }
}
