package com.antutu.ABenchMark;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * Handles the "Stop" action of the foreground notification.
 *
 * <p>The stop request travels through a non-exported receiver rather than
 * through the service itself. That keeps the service free to be started by
 * the shell or by a Shizuku session - which is what makes the watchdog able
 * to restart it - without exposing the stop action to other apps.
 */
public final class StopReceiver extends BroadcastReceiver {

    private static final String TAG = "PerfSpoof/Stop";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent != null ? intent.getAction() : null;
        Log.i(TAG, "stop requested: " + action);
        if (!PerformanceService.ACTION_STOP_SERVICE.equals(action)) {
            return;
        }
        try {
            PerformanceService.Companion.stopService(context);
        } catch (Throwable throwable) {
            Log.w(TAG, "stop failed: " + throwable);
        }
    }
}
