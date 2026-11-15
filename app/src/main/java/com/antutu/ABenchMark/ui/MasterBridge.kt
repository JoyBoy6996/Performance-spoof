package com.antutu.ABenchMark.ui

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.antutu.ABenchMark.Diagnostics
import com.antutu.ABenchMark.GameBoostController
import com.antutu.ABenchMark.PerformanceService
import com.antutu.ABenchMark.RomCompatibility
import com.antutu.ABenchMark.RomGuard
import com.antutu.ABenchMark.ShellBridge
import com.antutu.ABenchMark.ServiceLauncher

/** The ROM families the core supports. */
enum class RomFamily(val label: String, val shortLabel: String) {
    ORIGIN_OS("OriginOS / Funtouch OS", "OriginOS"),
    OPLUS_FAMILY("ColorOS / OxygenOS / realme UI", "ColorOS"),
    HYPER_OS("HyperOS / MIUI", "HyperOS"),
    STANDARD_ANDROID("Standard Android", "Android");

    companion object {
        fun from(family: RomCompatibility.Family): RomFamily = when (family) {
            RomCompatibility.Family.ORIGIN_OS -> ORIGIN_OS
            RomCompatibility.Family.OPLUS_FAMILY -> OPLUS_FAMILY
            RomCompatibility.Family.HYPER_OS -> HYPER_OS
            RomCompatibility.Family.STANDARD_ANDROID -> STANDARD_ANDROID
        }
    }
}

/** A live snapshot of everything the dashboard shows. */
data class MasterState(
    val rom: RomFamily,
    val serviceActive: Boolean = false,
    val overlayAttached: Boolean = false,
    val overlayPermission: Boolean = false,
    val batteryUnrestricted: Boolean = false,
    val notificationsGranted: Boolean = false,
    val notificationsEnabled: Boolean = false,
    val guardsApplied: Boolean = false,
    val backend: String = "none",
    val lastState: String = "not started",
    val lastError: String = "",
    val deviceModel: String = "",
    val androidVersion: String = "",
)

/** An installed app the user can pick as a boost target. */
data class InstalledApp(
    val packageName: String,
    val label: String,
)

/**
 * The bridge between the Compose dashboard and the Java core.
 *
 * The core - the service, the overlay, the heartbeat, the watchdog and the
 * ROM guards - is shared by all three ROM families and is untouched by the
 * UI. This class only reads state and starts work.
 */
object MasterBridge {

    private val log = ArrayDeque<String>()

    fun snapshot(context: Context): MasterState {
        val power = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager?
        val notificationsGranted = Build.VERSION.SDK_INT < 33 ||
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager?

        return MasterState(
            rom = RomFamily.from(RomCompatibility.detect()),
            serviceActive = PerformanceService.isOperational(),
            overlayAttached = PerformanceService.isOverlayAttached(),
            overlayPermission = Settings.canDrawOverlays(context),
            batteryUnrestricted = power?.isIgnoringBatteryOptimizations(context.packageName) == true,
            notificationsGranted = notificationsGranted,
            notificationsEnabled = notificationManager?.areNotificationsEnabled() != false,
            guardsApplied = RomGuard.isGuarded(context),
            backend = ShellBridge.resolvedBackend().name.lowercase(),
            lastState = readPref(context, "activation_diagnostics", "last_state", "not started"),
            lastError = readPref(context, "activation_diagnostics", "last_error", ""),
            deviceModel = "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}",
            androidVersion = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
        )
    }

    /** Starts the spoof: the service, the overlay surface and the watchdog. */
    fun activate(context: Context): Boolean {
        return try {
            ServiceLauncher.start(context)
            true
        } catch (throwable: Throwable) {
            appendLog("activate failed: $throwable")
            false
        }
    }

    fun stop(context: Context) {
        try {
            PerformanceService.stopFrom(context)
            appendLog("service stopped")
        } catch (throwable: Throwable) {
            appendLog("stop failed: $throwable")
        }
    }

    /** Re-applies every ROM exemption that can be applied from the shell. */
    fun applyGuards(context: Context, onDone: (String) -> Unit) {
        ShellBridge.invalidate()
        RomGuard.applyAsync(context) {
            val summary = RomGuard.summary(context)
            appendLog("guards applied")
            onDone(summary)
        }
    }

    /** Asks the Shizuku manager for the shell identity. */
    fun requestShizuku(context: Context) {
        ShellBridge.invalidate()
        try {
            ShellBridge.requestShizukuPermission(context)
            appendLog("shizuku permission requested")
        } catch (throwable: Throwable) {
            appendLog("shizuku request failed: $throwable")
        }
    }

    fun boost(context: Context, packageName: String, onDone: (String) -> Unit) {
        Thread {
            val report = GameBoostController.applyPerformanceMode(context, packageName)
            appendLog(report.trim().replace('\n', ' '))
            onDone(report)
        }.start()
    }

    fun resetBoost(context: Context, packageName: String, onDone: (String) -> Unit) {
        Thread {
            val report = GameBoostController.resetPerformanceMode(context, packageName)
            appendLog(report.trim().replace('\n', ' '))
            onDone(report)
        }.start()
    }

    fun foregroundPackage(context: Context): String =
        GameBoostController.foregroundPackage(context)

    fun diagnostics(context: Context): String = Diagnostics.summary(context)

    fun logs(): List<String> = log.toList()

    fun clearLogs() {
        log.clear()
    }

    // ---- vendor screens -------------------------------------------------

    fun openOverlaySettings(context: Context): Boolean =
        RomGuard.openOverlaySettings(context)

    fun openAutostartSettings(context: Context): Boolean =
        RomGuard.openAutostartSettings(context)

    fun openBackgroundSettings(context: Context): Boolean =
        RomGuard.openBackgroundActivitySettings(context)

    fun openBatteryMode(context: Context): Boolean =
        RomGuard.openBatteryModeSettings(context)

    fun openGameCenter(context: Context): Boolean =
        RomGuard.openGameCenter(context)

    fun openAppSettings(context: Context): Boolean =
        RomGuard.openAppSettings(context)

    fun openIgnoreBatteryOptimizations(context: Context): Boolean = try {
        context.startActivity(
            Intent(
                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Uri.parse("package:${context.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        true
    } catch (throwable: Throwable) {
        false
    }

    // ---- helpers --------------------------------------------------------

    /** Installed apps that have a launcher entry, used as boost targets. */
    fun installedApps(context: Context): List<InstalledApp> {
        val manager = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = mutableListOf<InstalledApp>()
        try {
            @Suppress("DEPRECATION")
            val resolved = manager.queryIntentActivities(intent, 0)
            for (info in resolved) {
                val appInfo = info.activityInfo?.applicationInfo ?: continue
                if (appInfo.packageName == context.packageName) continue
                val label = runCatching { manager.getApplicationLabel(appInfo).toString() }
                    .getOrDefault(appInfo.packageName)
                apps.add(InstalledApp(appInfo.packageName, label))
            }
        } catch (throwable: Throwable) {
            appendLog("app list failed: $throwable")
        }
        return apps.sortedBy { it.label.lowercase() }
    }

    private fun readPref(context: Context, file: String, key: String, fallback: String): String {
        return try {
            context.getSharedPreferences(file, Context.MODE_PRIVATE)
                .getString(key, fallback) ?: fallback
        } catch (throwable: Throwable) {
            fallback
        }
    }

    private fun appendLog(line: String) {
        synchronized(log) {
            log.addLast("${System.currentTimeMillis() % 100000}  $line")
            while (log.size > 80) log.removeFirst()
        }
    }
}
