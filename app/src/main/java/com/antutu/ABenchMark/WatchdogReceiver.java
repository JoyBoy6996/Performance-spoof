package com.antutu.ABenchMark;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * Receives the watchdog alarm and restarts the performance service when the
 * system stopped it. Also used for the boot broadcast.
 *
 * <p>The work runs on a background thread under {@code goAsync()}, because
 * the privileged restart shells out to {@code am start-foreground-service}
 * and must not block the main thread.
 */
public final class WatchdogReceiver extends BroadcastReceiver {

    private static final String TAG = "PerfSpoof/Watchdog";

    @Override
    public void onReceive(Context context, Intent intent) {
        final PendingResult pendingResult = goAsync();
        final Context appContext = context.getApplicationContext();
        final String action = intent != null ? intent.getAction() : null;

        new Thread(() -> {
            try {
                handle(appContext, action);
            } catch (Throwable throwable) {
                Log.w(TAG, "watchdog failed: " + throwable);
            } finally {
                pendingResult.finish();
            }
        }, "perf-spoof-watchdog").start();
    }

    private void handle(Context context, String action) {
        Log.i(TAG, "watchdog fired: " + action);

        if (Intent.ACTION_BOOT_COMPLETED.equals(action)
                || "android.intent.action.QUICKBOOT_POWERON".equals(action)
                || "com.htc.intent.action.QUICKBOOT_POWERON".equals(action)) {
            RomGuard.applyAsync(context, null);
        }

        if (PerformanceService.isUserStopped(context)) {
            ServiceWatchdog.cancel(context);
            return;
        }

        if (!PerformanceService.isOperational()) {
            // The shell identity can start a foreground service from the
            // background, which the app itself cannot always do on Android
            // 12+. The in-app route is the fallback.
            if (!ServiceLauncher.start(context)) {
                Log.w(TAG, "background start rejected, will retry on the next tick");
            }
        }

        ServiceWatchdog.schedule(context);
    }
}
