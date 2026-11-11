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
    private static final String LAST_UPDATED_AT = "last_updated_at";
    private static final String LAST_ROM_FAMILY = "last_rom_family";

    private Diagnostics() { }

    public static void record(Context context, String state, String error) {
        SharedPreferences.Editor editor = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(LAST_STATE, state)
                .putLong(LAST_UPDATED_AT, System.currentTimeMillis())
                .putString(LAST_ROM_FAMILY, RomCompatibility.detect().name());
        if (error != null && !error.isEmpty()) {
            editor.putString(LAST_ERROR, error);
        } else if ("active".equals(state)) {
            editor.remove(LAST_ERROR);
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
        SharedPreferences preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String lastRomFamily = preferences.getString(LAST_ROM_FAMILY, "unknown");
        long lastUpdatedAt = preferences.getLong(LAST_UPDATED_AT, 0L);
        String lastError = lastError(context);

        return "ROM family: " + RomCompatibility.detect()
                + "\nBuild identifiers: " + RomCompatibility.buildIdentifiers()
                + "\nRecorded ROM family: " + lastRomFamily
                + "\nOverlay permission: " + Settings.canDrawOverlays(context)
                + "\nBattery unrestricted: " + batteryUnrestricted
                + "\nNotification permission: " + notificationsGranted
                + "\nNotifications enabled: " + notificationsEnabled
                + "\nOverlay attached: " + PerformanceService.isOverlayAttached()
                + "\nForeground service active: " + PerformanceService.isOperational()
                + "\nLast state: " + lastState
                + "\nLast update: " + (lastUpdatedAt == 0L ? "never" : lastUpdatedAt)
                + "\nLast error: " + (lastError.isEmpty() ? "none" : lastError);
    }
}
