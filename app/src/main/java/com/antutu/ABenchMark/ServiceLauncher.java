package com.antutu.ABenchMark;

import android.content.Context;
import android.util.Log;

/**
 * Starts the performance service.
 *
 * <p>Two routes exist, because Android 12+ forbids a background app from
 * calling {@code startForegroundService()}:
 *
 * <ul>
 *   <li><b>Privileged route</b> - the shell runs
 *       {@code am start-foreground-service}. The shell identity holds
 *       {@code START_FOREGROUND_SERVICES_FROM_BACKGROUND}, so the start always
 *       succeeds no matter what the app is doing. This needs either root or a
 *       Shizuku session.</li>
 *   <li><b>In-app route</b> - {@code context.startForegroundService()}. Works
 *       while the app has a visible activity or an activity in the back stack
 *       of the foreground task. The transparent activity calls
 *       {@code moveTaskToBack()}, so its task stays in recents and this
 *       exemption usually holds.</li>
 * </ul>
 *
 * <p>The privileged route is preferred because it is the only one that
 * survives the app sitting in the background for hours.
 */
public final class ServiceLauncher {

    private static final String TAG = "PerfSpoof/Launcher";

    private ServiceLauncher() { }

    /**
     * Starts the service through the shell identity when one is available.
     *
     * @return true when the service was started through the shell
     */
    public static boolean startPrivileged(Context context) {
        // backend() probes root the first time it is called. This method only
        // ever runs on a background thread, so the probe is safe here.
        ShellBridge.Backend backend = ShellBridge.backend(context);
        if (backend == ShellBridge.Backend.NONE) {
            return false;
        }
        String command = "am start-foreground-service --user 0 -n "
                + context.getPackageName() + "/.PerformanceService -a "
                + PerformanceService.ACTION_START_SERVICE;
        String output = ShellBridge.exec(context, command);
        boolean started = !output.toLowerCase(java.util.Locale.ROOT)
                .contains("exception")
                && !output.toLowerCase(java.util.Locale.ROOT).contains("permission denial")
                && !output.contains("Error:");
        Log.i(TAG, "privileged start via " + backend + " -> " + output.trim());
        return started;
    }

    /**
     * Starts the service, preferring the privileged route.
     *
     * @return true when the start was accepted
     */
    public static boolean start(Context context) {
        if (startPrivileged(context)) {
            return true;
        }
        try {
            PerformanceService.Companion.startService(context);
            return true;
        } catch (Throwable throwable) {
            Log.w(TAG, "in-app start rejected: " + throwable);
            return false;
        }
    }
}
