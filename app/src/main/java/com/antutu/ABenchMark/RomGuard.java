package com.antutu.ABenchMark;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.provider.Settings;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Multi-ROM hardening.
 *
 * <p>The spoof needs three things from the system to keep working:
 * the process must stay alive, the overlay surface must stay alive, and the
 * platform must not tear the foreground service down. Every OEM stacks its own
 * background control on top of AOSP, so the guard applies the shared AOSP
 * layer everywhere and then adds the vendor specific layer for the detected
 * ROM family.
 *
 * <ul>
 *   <li><b>Shared AOSP layer</b> - AppOps, doze whitelist, standby bucket,
 *       un-stop. Works on every ROM.</li>
 *   <li><b>HyperOS / MIUI</b> - Greezer and the memory freezer, plus the
 *       three Xiaomi cloud whitelists. The memory freezer destroys the overlay
 *       surface of a frozen app, which silently breaks the spoof.</li>
 *   <li><b>ColorOS / OxygenOS / realme UI</b> - App Quick Freeze, Sleep
 *       standby optimisation and the per-app battery mode. Most of these live
 *       in the vendor's own database and are not reachable from adb, so the
 *       guard opens the right vendor screen instead.</li>
 * </ul>
 */
public final class RomGuard {

    private static final String TAG = "PerfSpoof/Guard";
    private static final String PREFS = "rom_guard";

    private static final String KEY_ORIG_CACHED_FREEZER = "orig_cached_apps_freezer";
    private static final String KEY_ORIG_GREEZER = "orig_cloud_greezer_enable";
    private static final String KEY_ORIG_MEMFREEZE = "orig_cloud_memFreeze_control";
    private static final String KEY_GUARDED = "guarded";

    /** Xiaomi cloud whitelist settings that hold a comma separated package list. */
    private static final String[] CLOUD_WHITELISTS = {
            "cloud_memory_freeze_whitelist",
            "cloud_lowlatency_whitelist",
            "cloud_network_priority_whitelist",
    };

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "perf-spoof-guard");
        thread.setDaemon(true);
        return thread;
    });

    private RomGuard() { }

    public static boolean isGuarded(Context context) {
        return prefs(context).getBoolean(KEY_GUARDED, false);
    }

    /** Human readable summary of the guard state, for the setup console. */
    public static String summary(Context context) {
        StringBuilder builder = new StringBuilder();
        ShellBridge.Backend backend = ShellBridge.resolvedBackend();
        builder.append("ROM family: ").append(RomCompatibility.detect()).append('\n');
        builder.append("Privileged backend: ").append(backend).append('\n');
        builder.append("Guards applied: ").append(isGuarded(context)).append('\n');
        if (backend == ShellBridge.Backend.NONE) {
            builder.append("\nNo root and no Shizuku session detected.\n")
                    .append("Run setup-coloros.sh over adb, or install Shizuku and grant it\n")
                    .append("permission, then tap Apply in the setup screen.\n");
            return builder.toString();
        }
        builder.append("\nAppOps / whitelist state:\n");
        builder.append(ShellBridge.execResolved(context,
                "cmd appops get " + packageName(context)));
        builder.append('\n');
        builder.append(ShellBridge.execResolved(context,
                "am get-standby-bucket " + packageName(context)));
        builder.append('\n');
        builder.append(ShellBridge.execResolved(context,
                "dumpsys deviceidle whitelist | grep -i " + packageName(context)));
        return builder.toString();
    }

    /** Applies the hardening in the background. */
    public static void applyAsync(Context context, Runnable onDone) {
        Context appContext = context.getApplicationContext();
        EXECUTOR.execute(() -> {
            try {
                apply(appContext);
            } catch (Throwable throwable) {
                Log.w(TAG, "guard failed", throwable);
            } finally {
                if (onDone != null) {
                    onDone.run();
                }
            }
        });
    }

    /** Applies the hardening. Blocks until finished. */
    public static void apply(Context context) {
        ShellBridge.Backend backend = ShellBridge.backend(context);
        if (backend == ShellBridge.Backend.NONE) {
            Log.w(TAG, "no privileged backend, skipping ROM hardening");
            return;
        }

        String pkg = packageName(context);

        // ---- Shared AOSP layer -------------------------------------------
        List<String> commands = new ArrayList<>();
        for (String op : new String[] {
                "RUN_ANY_IN_BACKGROUND", "RUN_IN_BACKGROUND", "START_FOREGROUND",
                "WAKE_LOCK", "SYSTEM_ALERT_WINDOW" }) {
            commands.add("cmd appops set " + pkg + " " + op + " allow");
        }
        commands.add("dumpsys deviceidle whitelist +" + pkg);
        commands.add("cmd deviceidle except-idle-whitelist +" + pkg);
        commands.add("am set-standby-bucket " + pkg + " active");
        commands.add("cmd package unstop --user 0 " + pkg);

        // ---- Vendor layer -------------------------------------------------
        RomCompatibility.Family family = RomCompatibility.detect();
        if (family == RomCompatibility.Family.HYPER_OS) {
            applyHyperOsLayer(context, pkg, commands);
        } else if (family == RomCompatibility.Family.OPLUS_FAMILY) {
            applyOplusLayer(context, pkg, commands);
        }

        ShellBridge.execAll(context, commands);
        prefs(context).edit().putBoolean(KEY_GUARDED, true).apply();
        Log.i(TAG, "ROM guards applied via " + backend + " for " + family);
    }

    /**
     * HyperOS / MIUI layer: turn the freeze switches off and keep the package
     * in the Xiaomi cloud whitelists.
     */
    private static void applyHyperOsLayer(Context context, String pkg, List<String> commands) {
        for (String whitelist : CLOUD_WHITELISTS) {
            String current = ShellBridge.settingsGet(context, "system", whitelist);
            String merged = appendCsv(current, pkg);
            if (merged != null) {
                commands.add("settings put system " + whitelist + " " + merged);
            }
        }

        storeOriginal(context, "global", "cached_apps_freezer", KEY_ORIG_CACHED_FREEZER);
        storeOriginal(context, "system", "cloud_greezer_enable", KEY_ORIG_GREEZER);
        storeOriginal(context, "system", "cloud_memFreeze_control", KEY_ORIG_MEMFREEZE);
        commands.add("settings put global cached_apps_freezer disabled");
        commands.add("settings put system cloud_greezer_enable false");
        commands.add("settings put system cloud_memFreeze_control 0");
    }

    /**
     * ColorOS / OxygenOS / realme UI layer.
     *
     * <p>Everything scriptable is already covered by the shared AOSP layer.
     * The remaining ColorOS switches (App Quick Freeze, Sleep standby
     * optimisation, per-app battery mode) live in the vendor database and are
     * not exposed through the settings provider, so they are surfaced in the
     * setup console instead of being written blindly.
     *
     * <p>The one extra that is scriptable is the AOSP game mode, which is what
     * ColorOS maps its Game Center "Pro Gamer mode" behaviour onto.
     */
    private static void applyOplusLayer(Context context, String pkg, List<String> commands) {
        // ColorOS keeps the per-app power mode in com.oplus.battery's own
        // database. Forcing the package out of the restricted state through
        // the standard channels is the only scripted option, so make sure the
        // package is not marked as restricted by the app standby service.
        commands.add("am set-inactive " + pkg + " false");
        commands.add("cmd appops set " + pkg + " RUN_ANY_IN_BACKGROUND allow");
    }

    /** Restores the global freeze switches captured before the guard ran. */
    public static void restore(Context context) {
        if (ShellBridge.resolvedBackend() == ShellBridge.Backend.NONE) {
            return;
        }
        SharedPreferences preferences = prefs(context);
        restoreSetting(context, preferences, "global", "cached_apps_freezer",
                KEY_ORIG_CACHED_FREEZER);
        restoreSetting(context, preferences, "system", "cloud_greezer_enable",
                KEY_ORIG_GREEZER);
        restoreSetting(context, preferences, "system", "cloud_memFreeze_control",
                KEY_ORIG_MEMFREEZE);
        preferences.edit().putBoolean(KEY_GUARDED, false).apply();
    }

    private static void restoreSetting(Context context, SharedPreferences preferences,
                                       String namespace, String key, String prefKey) {
        if (!preferences.contains(prefKey)) {
            return;
        }
        String original = preferences.getString(prefKey, null);
        if (original == null) {
            ShellBridge.exec(context, "settings delete " + namespace + " " + key);
        } else {
            ShellBridge.exec(context, "settings put " + namespace + " " + key + " " + original);
        }
        preferences.edit().remove(prefKey).apply();
    }

    private static void storeOriginal(Context context, String namespace, String key,
                                      String prefKey) {
        SharedPreferences preferences = prefs(context);
        if (preferences.contains(prefKey)) {
            return;
        }
        String current = ShellBridge.settingsGet(context, namespace, key);
        SharedPreferences.Editor editor = preferences.edit();
        if (current == null) {
            editor.putString(prefKey, null);
        } else {
            editor.putString(prefKey, current);
        }
        editor.apply();
    }

    /** Appends a package to a comma separated list, keeping the list stable. */
    static String appendCsv(String current, String pkg) {
        List<String> entries = new ArrayList<>();
        if (current != null && !current.isEmpty() && !"null".equals(current)) {
            for (String part : current.split(",")) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty()) {
                    entries.add(trimmed);
                }
            }
        }
        if (!entries.contains(pkg)) {
            entries.add(pkg);
        }
        return String.join(",", entries);
    }

    // ------------------------------------------------------------------
    // Vendor settings screens
    // ------------------------------------------------------------------

    /**
     * Opens the auto-launch / start-up manager for the detected ROM.
     *
     * @return true when a vendor screen was opened
     */
    public static boolean openAutostartSettings(Context context) {
        RomCompatibility.Family family = RomCompatibility.detect();
        if (family == RomCompatibility.Family.OPLUS_FAMILY) {
            if (openFirst(context, OPLUS_AUTOLAST_COMPONENTS)) {
                return true;
            }
        } else {
            if (openFirst(context, XIAOMI_AUTOLAST_COMPONENTS)) {
                return true;
            }
        }
        return openFirst(context, GENERIC_AUTOLAST_COMPONENTS);
    }

    /**
     * Opens the per-app battery usage page, which on ColorOS is where
     * "Allow background activity" and "Allow foreground activity" live.
     */
    public static boolean openBackgroundActivitySettings(Context context) {
        RomCompatibility.Family family = RomCompatibility.detect();
        if (family == RomCompatibility.Family.OPLUS_FAMILY) {
            if (openFirst(context, OPLUS_BATTERY_COMPONENTS)) {
                return true;
            }
        }
        return openFirst(context, GENERIC_BATTERY_COMPONENTS);
    }

    /**
     * Opens the battery settings page where ColorOS keeps
     * "High performance mode" (the closest analogue of OriginOS Monster Mode).
     */
    public static boolean openBatteryModeSettings(Context context) {
        try {
            Intent intent = new Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            return true;
        } catch (Throwable throwable) {
            Log.i(TAG, "battery saver settings unavailable: " + throwable);
        }
        try {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + context.getPackageName()));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            return true;
        } catch (Throwable throwable) {
            return false;
        }
    }

    /** Opens the app's own details page. */
    public static boolean openAppSettings(Context context) {
        try {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + context.getPackageName()));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            return true;
        } catch (Throwable throwable) {
            return false;
        }
    }

    /** Opens the overlay permission page for this package. */
    public static boolean openOverlaySettings(Context context) {
        try {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + context.getPackageName()));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            return true;
        } catch (Throwable throwable) {
            return false;
        }
    }

    /**
     * Opens the ColorOS Game Center so the user can pick "Pro Gamer mode",
     * the ColorOS equivalent of a per-game performance profile.
     */
    public static boolean openGameCenter(Context context) {
        String[] candidates = {
                "com.nearme.gamecenter/.MainActivity",
                "com.nearme.gamecenter/com.nearme.gamecenter.MainActivity",
                "com.heytap.gamecenter/.MainActivity",
                "com.oppo.gamecenter/.MainActivity",
                "com.oneplus.gamecenter/.MainActivity",
        };
        return openFirst(context, candidates);
    }

    private static boolean openFirst(Context context, String[] candidates) {
        for (String candidate : candidates) {
            try {
                ComponentName component = ComponentName.unflattenFromString(candidate);
                if (component == null) {
                    continue;
                }
                Intent intent = new Intent();
                intent.setComponent(component);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                intent.putExtra("package_name", context.getPackageName());
                intent.putExtra("packagename", context.getPackageName());
                context.startActivity(intent);
                return true;
            } catch (Throwable throwable) {
                Log.i(TAG, "component not available: " + candidate);
            }
        }
        return false;
    }

    // Vendor component names, best first.
    private static final String[] OPLUS_AUTOLAST_COMPONENTS = {
            "com.coloros.safecenter/com.coloros.safecenter.permission.startup.StartupAppListActivity",
            "com.coloros.safecenter/com.coloros.safecenter.startupapp.StartupAppListActivity",
            "com.oplus.safecenter/com.oplus.safecenter.startupapp.view.StartupAppListActivity",
            "com.oppo.safe/com.oppo.safe.permission.startup.StartupAppListActivity",
            "com.oneplus.security/com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity",
    };

    private static final String[] XIAOMI_AUTOLAST_COMPONENTS = {
            "com.miui.securitycenter/com.miui.permcenter.autostart.AutoStartManagementActivity",
            "com.miui.securitycenter/com.miui.appmanager.ApplicationsDetailsActivity",
    };

    private static final String[] GENERIC_AUTOLAST_COMPONENTS = {
            "com.android.settings/com.android.settings.ApplicationsDetailsActivity",
    };

    private static final String[] OPLUS_BATTERY_COMPONENTS = {
            "com.coloros.oppoguardelf/com.coloros.powermanager.fuelgaue.PowerUsageModelActivity",
            "com.coloros.oppoguardelf/com.coloros.powermanager.fuelgaue.PowerConsumptionActivity",
            "com.oplus.battery/com.oplus.powermanager.fuelgaue.PowerUsageModelActivity",
            "com.oplus.battery/com.oplus.powermanager.fuelgaue.PowerControlActivity",
            "com.android.settings/com.oplus.settings.OplusSubSettings",
    };

    private static final String[] GENERIC_BATTERY_COMPONENTS = {
            "com.android.settings/com.android.settings.SubSettings",
    };

    static List<String> commandPreview(Context context) {
        String pkg = packageName(context);
        List<String> commands = new ArrayList<>();
        commands.add("cmd appops set " + pkg + " RUN_ANY_IN_BACKGROUND allow");
        commands.add("cmd appops set " + pkg + " RUN_IN_BACKGROUND allow");
        commands.add("cmd appops set " + pkg + " START_FOREGROUND allow");
        commands.add("cmd appops set " + pkg + " WAKE_LOCK allow");
        commands.add("cmd appops set " + pkg + " SYSTEM_ALERT_WINDOW allow");
        commands.add("dumpsys deviceidle whitelist +" + pkg);
        commands.add("cmd deviceidle except-idle-whitelist +" + pkg);
        commands.add("am set-standby-bucket " + pkg + " active");
        commands.add("cmd package unstop --user 0 " + pkg);
        if (RomCompatibility.detect() == RomCompatibility.Family.HYPER_OS) {
            commands.add("settings put global cached_apps_freezer disabled");
            commands.add("settings put system cloud_greezer_enable false");
            commands.add("settings put system cloud_memFreeze_control 0");
        }
        return commands;
    }

    private static String packageName(Context context) {
        return context.getPackageName();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
