package com.antutu.ABenchMark;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.SystemClock;
import android.util.Log;

/**
 * Alarm based watchdog.
 *
 * <p>HyperOS stops foreground services aggressively and, on Android 14+, the
 * system raises {@code ForegroundServiceDidNotStopInTimeException} inside the
 * app when it removes a foreground service without a clean shutdown. A plain
 * {@code START_STICKY} restart is not enough because the sticky restart is
 * itself subject to the background foreground-service rules.
 *
 * <p>A repeating alarm gives the app a second, independent path back to a
 * running service. The exact-alarm variant is used when the platform allows
 * it, otherwise the inexact idle-aware variant is used so the alarm still
 * fires while the device is dozing.
 */
public final class ServiceWatchdog {

    private static final String TAG = "PerfSpoof/Watchdog";
    private static final int REQUEST_CODE = 4201;
    private static final long WATCHDOG_INTERVAL_MS = 20_000L;
    private static final long RESTART_DELAY_MS = 1_500L;

    private ServiceWatchdog() { }

    /** Schedules the repeating watchdog alarm. */
    public static void schedule(Context context) {
        AlarmManager alarmManager =
                (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            return;
        }
        PendingIntent operation = pendingIntent(context, REQUEST_CODE, false);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                    && !alarmManager.canScheduleExactAlarms()) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME,
                        SystemClock.elapsedRealtime() + WATCHDOG_INTERVAL_MS, operation);
                return;
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME,
                        SystemClock.elapsedRealtime() + WATCHDOG_INTERVAL_MS, operation);
                return;
            }
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME,
                    SystemClock.elapsedRealtime() + WATCHDOG_INTERVAL_MS, operation);
        } catch (Throwable throwable) {
            Log.w(TAG, "exact alarm rejected, falling back: " + throwable);
            alarmManager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME,
                    SystemClock.elapsedRealtime() + WATCHDOG_INTERVAL_MS, operation);
        }
    }

    /** Schedules a one shot restart, used when the task is swiped away. */
    public static void scheduleRestart(Context context) {
        AlarmManager alarmManager =
                (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            return;
        }
        PendingIntent operation = pendingIntent(context, REQUEST_CODE + 1, false);
        alarmManager.set(AlarmManager.ELAPSED_REALTIME,
                SystemClock.elapsedRealtime() + RESTART_DELAY_MS, operation);
    }

    /** Cancels every watchdog alarm. */
    public static void cancel(Context context) {
        AlarmManager alarmManager =
                (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            return;
        }
        alarmManager.cancel(pendingIntent(context, REQUEST_CODE, false));
        alarmManager.cancel(pendingIntent(context, REQUEST_CODE + 1, false));
    }

    private static PendingIntent pendingIntent(Context context, int requestCode, boolean oneShot) {
        Intent intent = new Intent(context, WatchdogReceiver.class);
        intent.setAction("com.antutu.ABenchMark.WATCHDOG");
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        if (oneShot) {
            flags |= PendingIntent.FLAG_ONE_SHOT;
        }
        return PendingIntent.getBroadcast(context, requestCode, intent, flags);
    }
}
