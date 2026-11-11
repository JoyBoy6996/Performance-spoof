package com.antutu.ABenchMark;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
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

        try {
            createNotificationChannel();
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
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : null;

        if (ACTION_STOP_SERVICE.equals(action)) {
            stopForegroundService();
            return START_NOT_STICKY;
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
        } catch (RuntimeException exception) {
            recordFailure("foreground_failed", "Foreground service could not start: "
                    + exception.getClass().getSimpleName());
            stopSelf();
            return START_NOT_STICKY;
        }

        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
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
        Intent stopIntent = new Intent(this, PerformanceService.class);
        stopIntent.setAction(ACTION_STOP_SERVICE);

        int pendingIntentFlags =
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;

        PendingIntent stopPendingIntent = PendingIntent.getService(
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
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(getString(R.string.notification_title))
                .setContentText(getString(R.string.notification_text))
                .setContentIntent(contentPendingIntent)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .addAction(
                        R.mipmap.ic_launcher,
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
        detachOverlay();
        stopForeground(STOP_FOREGROUND_REMOVE);
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

    private void recordFailure(String state, String message) {
        lastFailure = message;
        Diagnostics.record(this, state, message);
    }
}
