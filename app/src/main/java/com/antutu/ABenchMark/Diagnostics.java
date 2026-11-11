package com.antutu.ABenchMark;

import android.Manifest;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;

/** Local-only activation state used for support and recovery UI. */
public final class Diagnostics {

    private static final String PREFS = "activation_diagnostics";
    private static final String LAST_ERROR = "last_error";
    private static final String LAST_STATE = "last_state";

    private Diagnostics() { }

    public static void record(Context context, String state, String error) {
        SharedPreferences.Editor editor = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(LAST_STATE, state);
        if (error == null || error.isEmpty()) {
            editor.remove(LAST_ERROR);
        } else {
            editor.putString(LAST_ERROR, error);
        }
        editor.apply();
    }

    public static String lastError(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(LAST_ERROR, "");
    }

    public static String summary(Context context) {
        PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        boolean batteryUnrestricted = powerManager != null
                && powerManager.isIgnoringBatteryOptimizations(context.getPackageName());
        boolean notificationsGranted = Build.VERSION.SDK_INT < 33
                || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
        NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
        boolean notificationsEnabled = notificationManager == null || notificationManager.areNotificationsEnabled();
        String lastState = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(LAST_STATE, "not started");

        return "ROM family: " + RomCompatibility.detect()
                + "\nOverlay permission: " + Settings.canDrawOverlays(context)
                + "\nBattery unrestricted: " + batteryUnrestricted
                + "\nNotification permission: " + notificationsGranted
                + "\nNotifications enabled: " + notificationsEnabled
                + "\nOverlay attached: " + PerformanceService.isOverlayAttached()
                + "\nForeground service active: " + PerformanceService.isOperational()
                + "\nLast state: " + lastState;
    }
}
