package com.antutu.ABenchMark;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * HyperOS / MIUI specific hardening.
 *
 * <p>OriginOS 6 keeps a foreground service alive once the overlay permission
 * and "unrestricted battery" are granted. HyperOS does not: it adds a cgroup
 * freezer (Greezer), a memory freezer, an app standby bucket, a doze whitelist
 * and a per-app AppOps layer, any one of which is enough to freeze or stop the
 * service and tear down the overlay surface that the OEM performance profile
 * detection relies on.
 *
 * <p>This class re-applies every one of those exemptions. It needs an ADB
 * shell identity (Shizuku) or root for the settings/AppOps writes; the plain
 * AppOps and doze-whitelist writes are the ones that matter most.
 */
public final class HyperOsGuard {

    private static final String TAG = "PerfSpoof/Guard";
    private static final String PREFS = "hyperos_guard";

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

    private HyperOsGuard() { }

    /** True once the guard has run successfully at least once. */
    public static boolean isGuarded(Context context) {
        return prefs(context).getBoolean(KEY_GUARDED, false);
    }

    /** Human readable summary of the guard state, for the diagnostics screen. */
    public static String summary(Context context) {
        StringBuilder builder = new StringBuilder();
        ShellBridge.Backend backend = ShellBridge.resolvedBackend();
        builder.append("Privileged backend: ").append(backend).append('\n');
        builder.append("Guards applied: ").append(isGuarded(context)).append('\n');
        if (backend == ShellBridge.Backend.NONE) {
            builder.append("\nNo root and no Shizuku session detected.\n")
                    .append("Run setup-hyperos.sh over adb, or install Shizuku and grant it\n")
                    .append("permission, then tap Apply in the recovery screen.\n");
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

    /**
     * Applies every HyperOS exemption in the background.
     *
     * @param onDone invoked on the calling thread once the work finished
     */
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

    /** Applies every HyperOS exemption. Blocks until finished. */
    public static void apply(Context context) {
        ShellBridge.Backend backend = ShellBridge.backend(context);
        if (backend == ShellBridge.Backend.NONE) {
            Log.w(TAG, "no privileged backend, skipping HyperOS hardening");
            return;
        }

        String pkg = packageName(context);

        // 1. AppOps: keep the process schedulable, allow background work and
        //    foreground starts, and make sure the overlay stays permitted.
        String[] appOps = {
                "RUN_ANY_IN_BACKGROUND",
                "RUN_IN_BACKGROUND",
                "START_FOREGROUND",
                "WAKE_LOCK",
                "SYSTEM_ALERT_WINDOW",
        };
        List<String> commands = new ArrayList<>();
        for (String op : appOps) {
            commands.add("cmd appops set " + pkg + " " + op + " allow");
        }

        // 2. Doze: the regular whitelist plus the stronger except-idle list.
        commands.add("dumpsys deviceidle whitelist +" + pkg);
        commands.add("cmd deviceidle except-idle-whitelist +" + pkg);

        // 3. Standby bucket: ACTIVE keeps the app out of the restricted bucket
        //    where HyperOS removes existing foreground services.
        commands.add("am set-standby-bucket " + pkg + " active");

        // 4. Xiaomi cloud whitelists: the memory freezer destroys the overlay
        //    surface of a backgrounded app, which is exactly the signal the
        //    OEM performance detection needs.
        for (String whitelist : CLOUD_WHITELISTS) {
            String current = ShellBridge.settingsGet(context, "system", whitelist);
            String merged = appendCsv(current, pkg);
            if (merged != null) {
                commands.add("settings put system " + whitelist + " " + merged);
            }
        }

        // 5. Global freeze switches. The previous values are stored so they can
        //    be restored when the service stops.
        storeOriginal(context, "global", "cached_apps_freezer", KEY_ORIG_CACHED_FREEZER);
        storeOriginal(context, "system", "cloud_greezer_enable", KEY_ORIG_GREEZER);
        storeOriginal(context, "system", "cloud_memFreeze_control", KEY_ORIG_MEMFREEZE);
        commands.add("settings put global cached_apps_freezer disabled");
        commands.add("settings put system cloud_greezer_enable false");
        commands.add("settings put system cloud_memFreeze_control 0");

        ShellBridge.execAll(context, commands);

        prefs(context).edit().putBoolean(KEY_GUARDED, true).apply();
        Log.i(TAG, "hyperos guards applied via " + backend);
    }

    /**
     * Restores the global freeze switches to the values captured before the
     * guard ran. Called when the user stops the service from the notification.
     */
    public static void restore(Context context) {
        if (ShellBridge.backend(context) == ShellBridge.Backend.NONE) {
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

    /** Removes a package from a comma separated list. */
    static String removeCsv(String current, String pkg) {
        if (current == null || current.isEmpty()) {
            return "";
        }
        List<String> entries = new ArrayList<>();
        for (String part : current.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty() && !trimmed.equals(pkg)) {
                entries.add(trimmed);
            }
        }
        return String.join(",", entries);
    }

    /** Opens the MIUI/HyperOS auto-start manager, falling back to Security. */
    public static boolean openAutostartSettings(Context context) {
        String[] candidates = {
                "com.miui.securitycenter/com.miui.permcenter.autostart.AutoStartManagementActivity",
                "com.miui.securitycenter/com.miui.permcenter.permissions.PermissionsManagementActivity",
                "com.miui.securitycenter/com.miui.main.SafeCenter",
        };
        for (String candidate : candidates) {
            try {
                android.content.ComponentName component =
                        android.content.ComponentName.unflattenFromString(candidate);
                if (component == null) {
                    continue;
                }
                android.content.Intent intent = new android.content.Intent();
                intent.setComponent(component);
                intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
                return true;
            } catch (Throwable throwable) {
                Log.i(TAG, "autostart candidate failed: " + candidate);
            }
        }
        return false;
    }

    /** Opens the per-app HyperOS battery page. */
    public static boolean openBatterySettings(Context context) {
        try {
            android.content.Intent intent = new android.content.Intent(
                    android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    android.net.Uri.parse("package:" + context.getPackageName()));
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            return true;
        } catch (Throwable throwable) {
            return false;
        }
    }

    static List<String> commandPreview(Context context) {
        String pkg = packageName(context);
        return new ArrayList<>(Arrays.asList(
                "cmd appops set " + pkg + " RUN_ANY_IN_BACKGROUND allow",
                "cmd appops set " + pkg + " RUN_IN_BACKGROUND allow",
                "cmd appops set " + pkg + " START_FOREGROUND allow",
                "cmd appops set " + pkg + " WAKE_LOCK allow",
                "cmd appops set " + pkg + " SYSTEM_ALERT_WINDOW allow",
                "dumpsys deviceidle whitelist +" + pkg,
                "cmd deviceidle except-idle-whitelist +" + pkg,
                "am set-standby-bucket " + pkg + " active",
                "settings put global cached_apps_freezer disabled",
                "settings put system cloud_greezer_enable false",
                "settings put system cloud_memFreeze_control 0"));
    }

    private static String packageName(Context context) {
        return context.getPackageName();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
