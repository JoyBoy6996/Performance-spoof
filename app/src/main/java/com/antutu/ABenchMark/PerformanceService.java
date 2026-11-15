package com.antutu.ABenchMark;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;

import androidx.core.app.NotificationCompat;

import java.lang.ref.WeakReference;
import java.util.Objects;

/**
 * Reconstructed from com.antutu.ABenchMark.PerformanceService in classes.dex.
 *
 * Preserves the original service class name, action strings, notification
 * channel, notification ID, transparent overlay geometry, heartbeat cadence,
 * start/stop lifecycle and foreground-service type.
 *
 * <p>HyperOS port notes: the service is promoted to the foreground inside
 * {@link #onCreate()} instead of {@link #onStartCommand()}, because HyperOS
 * stops special-use foreground services that take too long to call
 * {@code startForeground()} and the platform then raises
 * {@code ForegroundServiceDidNotStopInTimeException} in this process. The
 * overlay is attached only after the promotion succeeded, so the
 * hardware-accelerated surface is never created for a service that is not
 * actually in the foreground.
 */
public final class PerformanceService extends Service {

    public static final int $stable = 8;

    public static final String ACTION_START_SERVICE =
            "com.antutu.ABenchMark.ACTION_START";
    public static final String ACTION_STOP_SERVICE =
            "com.antutu.ABenchMark.ACTION_STOP";
    public static final String CHANNEL_ID =
            "iqoo_performance_service_channel";
    public static final int NOTIFICATION_ID = 1001;

    public static final Companion Companion = new Companion();

    private static final String PREFS = "service_state";
    private static final String KEY_USER_STOPPED = "user_stopped";
    private static final String KEY_STOP_TOKEN = "stop_token";
    private static final String EXTRA_STOP_TOKEN = "com.antutu.ABenchMark.extra.STOP_TOKEN";

    private static WeakReference<PerformanceService> instance;
    private static boolean isRunning;
    private static boolean overlayAttached;
    private static boolean foregroundStarted;
    private static String lastFailure = "";

    private final Handler heartbeatHandler =
            new Handler(Looper.getMainLooper());
    private final heartbeatRunnable$1 heartbeatRunnable =
            new heartbeatRunnable$1();

    private WindowManager.LayoutParams overlayParams;
    private View overlayView;
    private WindowManager windowManager;

    public PerformanceService() {
        super();
    }

    @Override
    public void onCreate() {
        super.onCreate();

        instance = new WeakReference<>(this);
        lastFailure = "";
        foregroundStarted = false;
        overlayAttached = false;

        // Promote first. On HyperOS a slow promotion is fatal, and the overlay
        // is only meaningful while the service is in the foreground.
        boolean promoted = promoteToForeground();
        if (!promoted) {
            recordFailure("foreground_failed",
                    "Foreground service could not start: " + lastFailure);
            stopSelf();
            return;
        }

        try {
            overlayAttached = attachHardwareAcceleratedOverlay();
            isRunning = overlayAttached;
            if (overlayAttached) {
                startHeartbeatPulse();
                Diagnostics.record(this, "overlay attached", null);
            } else {
                Diagnostics.record(this, "overlay_failed", lastFailure);
            }
        } catch (RuntimeException exception) {
            isRunning = false;
            recordFailure("overlay_failed", "Service initialization failed: "
                    + exception.getClass().getSimpleName());
            stopSelf();
            return;
        }

        // Re-apply the HyperOS exemptions every time the service comes up.
        HyperOsGuard.applyAsync(this, null);
        ServiceWatchdog.schedule(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : null;

        if (ACTION_STOP_SERVICE.equals(action)) {
            // The service is exported so that the shell identity can restart
            // it. The stop action is guarded by a per install secret that no
            // other app can read, so only this app can stop the spoof.
            if (isAuthorisedStop(intent)) {
                stopForegroundService();
            } else {
                Log.w("PerfSpoof/Service", "ignored an unauthorised stop request");
            }
            return START_NOT_STICKY;
        }

        setUserStopped(this, false);
        clearStopMarker();

        if (!foregroundStarted && !promoteToForeground()) {
            Diagnostics.record(this, "foreground_failed", lastFailure);
            stopSelf();
            return START_NOT_STICKY;
        }

        if (!overlayAttached) {
            overlayAttached = attachHardwareAcceleratedOverlay();
            isRunning = overlayAttached;
            if (overlayAttached) {
                startHeartbeatPulse();
            }
        }

        if (!overlayAttached) {
            Diagnostics.record(this, "overlay_failed", lastFailure);
            stopSelf();
            return START_NOT_STICKY;
        }

        try {
            Notification notification = buildNotification();
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                );
            } else {
                startForeground(NOTIFICATION_ID, notification);
            }
            foregroundStarted = true;
            Diagnostics.record(this, "active", null);
        } catch (Throwable throwable) {
            recordFailure("foreground_failed", "Foreground service could not start: "
                    + throwable.getClass().getSimpleName());
            stopSelf();
            return START_NOT_STICKY;
        }

        ServiceWatchdog.schedule(this);
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
        // HyperOS clears the whole task and the service with it. Re-arm the
        // watchdog so the overlay comes back unless the user asked to stop.
        if (!isUserStopped(this)) {
            ServiceWatchdog.scheduleRestart(this);
        }
    }

    @Override
    public void onDestroy() {
        detachOverlay();
        instance = null;
        isRunning = false;
        foregroundStarted = false;
        if (lastFailure.isEmpty()) {
            Diagnostics.record(this, "stopped", null);
        }
        super.onDestroy();
    }

    /**
     * Promotes the service to the foreground. Returns false and records the
     * reason in {@link #lastFailure} when the platform refused.
     */
    private boolean promoteToForeground() {
        if (foregroundStarted) {
            return true;
        }
        try {
            createNotificationChannel();
            Notification notification = buildNotification();
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                );
            } else {
                startForeground(NOTIFICATION_ID, notification);
            }
            foregroundStarted = true;
            return true;
        } catch (Throwable throwable) {
            lastFailure = throwable.getClass().getSimpleName()
                    + (throwable.getMessage() == null ? "" : ": " + throwable.getMessage());
            Log.w("PerfSpoof/Service", "promote failed: " + lastFailure);
            return false;
        }
    }

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
        );
        channel.setDescription(
                "Notification channel for Performance Service"
        );

        NotificationManager manager =
                (NotificationManager) getSystemService("notification");
        manager.createNotificationChannel(channel);
    }

    private Notification buildNotification() {
        // The stop request goes through the non-exported StopReceiver so the
        // exported service can be started by the shell or by a Shizuku session
        // without exposing the stop action to other apps.
        Intent stopIntent = new Intent(this, StopReceiver.class);
        stopIntent.setAction(ACTION_STOP_SERVICE);

        int pendingIntentFlags =
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;

        PendingIntent stopPendingIntent = PendingIntent.getBroadcast(
                this,
                0,
                stopIntent,
                pendingIntentFlags
        );

        Intent contentIntent = new Intent(this, MainActivity.class);
        PendingIntent contentPendingIntent = PendingIntent.getActivity(
                this,
                0,
                contentIntent,
                pendingIntentFlags
        );

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setWhen(System.currentTimeMillis())
                .setSmallIcon(R.drawable.ic_stat_spoof)
                .setContentTitle(getString(R.string.notification_title))
                .setContentText(getString(R.string.notification_text))
                .setContentIntent(contentPendingIntent)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .addAction(
                        R.drawable.ic_stat_spoof,
                        getString(R.string.stop_action),
                        stopPendingIntent
                )
                .build();
    }

    private boolean attachHardwareAcceleratedOverlay() {
        try {
            if (overlayView != null) {
                return true;
            }

            windowManager = (WindowManager) getSystemService("window");

            // Original LayoutParams constructor:
            // width=2, height=2, type=2038, flags=16777496, format=-3.
            WindowManager.LayoutParams params =
                    new WindowManager.LayoutParams(
                            2,
                            2,
                            2038,
                            16777496,
                            PixelFormat.TRANSLUCENT
                    );

            params.gravity = 8388659;
            params.x = 0;
            params.y = 0;
            params.alpha = 0.02f;
            overlayParams = params;

            View view = new View(this);
            view.setAlpha(0.02f);
            view.setLayerType(View.LAYER_TYPE_HARDWARE, null);
            overlayView = view;

            if (windowManager != null) {
                windowManager.addView(view, params);
                return true;
            }
            recordFailure("overlay_failed", "Overlay could not attach: WindowManager unavailable");
        } catch (Throwable throwable) {
            recordFailure("overlay_failed", "Overlay could not attach: "
                    + throwable.getClass().getSimpleName());
        }
        return false;
    }

    private void detachOverlay() {
        heartbeatHandler.removeCallbacks(heartbeatRunnable);

        try {
            if (overlayView != null && windowManager != null) {
                windowManager.removeView(overlayView);
            }
        } catch (Throwable throwable) {
            recordFailure("teardown_failed", "Overlay could not detach: "
                    + throwable.getClass().getSimpleName());
        }

        overlayView = null;
        overlayAttached = false;
    }

    private void startHeartbeatPulse() {
        heartbeatHandler.removeCallbacks(heartbeatRunnable);
        heartbeatHandler.postDelayed(heartbeatRunnable, 3000L);
    }

    private void stopForegroundService() {
        setUserStopped(this, true);
        writeStopMarker();
        detachOverlay();
        ServiceWatchdog.cancel(this);
        HyperOsGuard.restore(this);
        try {
            stopForeground(STOP_FOREGROUND_REMOVE);
        } catch (Throwable ignored) {
            // The service may already have been removed by the system.
        }
        stopSelf();

        instance = null;
        isRunning = false;
        foregroundStarted = false;
        Diagnostics.record(this, "stopped", null);
    }

    /**
     * Binary name preserved as PerformanceService$heartbeatRunnable$1.
     * This mirrors the original Kotlin-generated Runnable inner class.
     */
    private final class heartbeatRunnable$1 implements Runnable {
        @Override
        public void run() {
            if (isRunning) {
                if (overlayView != null) {
                    overlayView.invalidate();
                }

                heartbeatHandler.postDelayed(this, 3000L);
            }
        }
    }

    /**
     * Binary name preserved as PerformanceService$Companion.
     */
    public static final class Companion {

        private Companion() {
        }

        public final boolean isRunning() {
            return PerformanceService.isRunning;
        }

        public final void startService(Context context) {
            Objects.requireNonNull(context, "context");

            Intent intent = new Intent(context, PerformanceService.class);
            intent.setAction(ACTION_START_SERVICE);
            context.startForegroundService(intent);
        }

        public final void stopService(Context context) {
            Objects.requireNonNull(context, "context");

            Intent intent = new Intent(context, PerformanceService.class);
            intent.setAction(ACTION_STOP_SERVICE);
            intent.putExtra(EXTRA_STOP_TOKEN, getStopToken(context));
            context.startService(intent);
        }
    }

    public static boolean isOverlayAttached() {
        return overlayAttached;
    }

    public static boolean isOperational() {
        return isRunning && overlayAttached && foregroundStarted;
    }

    public static String getLastFailure() {
        return lastFailure;
    }

    /** True when the user explicitly stopped the service from the notification. */
    public static boolean isUserStopped(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(KEY_USER_STOPPED, false);
    }

    private static void setUserStopped(Context context, boolean stopped) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_USER_STOPPED, stopped)
                .apply();
    }

    /**
     * Marker file used by the optional root watchdog so that an explicit user
     * stop is not undone by the background service. The file lives inside the
     * app data directory, which only root can read.
     */
    void writeStopMarker() {
        try {
            java.io.File marker = new java.io.File(getFilesDir(), "stopped");
            if (!marker.exists()) {
                //noinspection ResultOfMethodCallIgnored
                marker.createNewFile();
            }
        } catch (Throwable ignored) {
            // The marker is a convenience for the root watchdog only.
        }
    }

    void clearStopMarker() {
        try {
            java.io.File marker = new java.io.File(getFilesDir(), "stopped");
            if (marker.exists()) {
                //noinspection ResultOfMethodCallIgnored
                marker.delete();
            }
        } catch (Throwable ignored) {
            // The marker is a convenience for the root watchdog only.
        }
    }

    /**
     * True when the stop request carries this install's stop secret.
     *
     * <p>The service has to be exported so the shell identity (adb shell or a
     * Shizuku session) can restart it from the background. The stop secret
     * keeps the stop action out of reach of other apps, which is stronger
     * than a caller uid check and uses only public API.
     */
    private boolean isAuthorisedStop(Intent intent) {
        if (intent == null) {
            return false;
        }
        String provided = intent.getStringExtra(EXTRA_STOP_TOKEN);
        String expected = getStopToken(this);
        return provided != null && provided.equals(expected);
    }

    /** Per install secret used to authorise the stop action. */
    static String getStopToken(Context context) {
        SharedPreferences preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String token = preferences.getString(KEY_STOP_TOKEN, null);
        if (token == null) {
            token = java.util.UUID.randomUUID().toString();
            preferences.edit().putString(KEY_STOP_TOKEN, token).apply();
        }
        return token;
    }

    private void recordFailure(String state, String message) {
        lastFailure = message;
        Diagnostics.record(this, state, message);
    }
}
