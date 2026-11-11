package com.antutu.ABenchMark;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.activity.ComponentActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

/**
 * Reconstructed from com.antutu.ABenchMark.MainActivity in classes.dex.
 *
 * The original Activity is intentionally transparent and contains no visible
 * controls. Its only purpose is permission gating and starting the foreground
 * PerformanceService, then moving its task behind the app the user opens.
 */
public final class MainActivity extends ComponentActivity {

    public static final int $stable = 8;
    private static final long ACTIVATION_POLL_INTERVAL_MS = 100L;
    private static final long ACTIVATION_TIMEOUT_MS = 2000L;

    private final ActivityResultLauncher<Intent> overlayPermissionLauncher;
    private boolean activationPending;
    private boolean activationComplete;
    private long activationRequestedAt;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public MainActivity() {
        super();

        overlayPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (Settings.canDrawOverlays(this)) {
                        checkAndStartService();
                    } else {
                        showRecovery("Display over other apps permission was not granted.");
                    }
                }
        );

    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        overridePendingTransition(0, 0);

        Window window = getWindow();
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);

        // Exact flag value recovered from the original Activity bytecode.
        window.addFlags(0x01000218);

        WindowManager.LayoutParams attributes = window.getAttributes();
        attributes.alpha = 0.0f;
        window.setAttributes(attributes);

        enableImmersiveStickyMode();

        View transparentView = new View(this);
        transparentView.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        transparentView.setBackgroundColor(Color.TRANSPARENT);
        setContentView(transparentView);

        checkAndStartService();
    }

    @Override
    protected void onResume() {
        super.onResume();

        overridePendingTransition(0, 0);
        enableImmersiveStickyMode();

        if (!activationPending && Settings.canDrawOverlays(this)
                && !PerformanceService.Companion.isRunning()) {
            checkAndStartService();
            return;
        }

        if (PerformanceService.Companion.isRunning() && !activationPending) {
            if (isInMultiWindowMode()) {
                finish();
                return;
            }

            new Handler(Looper.getMainLooper()).postDelayed(
                    () -> moveTaskToBack(true),
                    200L
            );
        }
    }

    private void checkAndStartService() {
        if (activationPending || activationComplete) {
            return;
        }
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(
                    this,
                    "Please grant 'Display over other apps' permission for Monster Mode.",
                    Toast.LENGTH_LONG
            ).show();

            Uri packageUri = Uri.parse("package:" + getPackageName());
            Intent overlayIntent = new Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    packageUri
            );
            overlayPermissionLauncher.launch(overlayIntent);
            return;
        }

        activationPending = true;
        activationRequestedAt = android.os.SystemClock.elapsedRealtime();
        Diagnostics.record(this, "starting", null);
        try {
            PerformanceService.Companion.startService(this);
            mainHandler.postDelayed(
                    this::confirmActivation,
                    ACTIVATION_POLL_INTERVAL_MS
            );
        } catch (RuntimeException exception) {
            showRecovery("Foreground service request failed: "
                    + exception.getClass().getSimpleName());
        }
    }

    private void confirmActivation() {
        if (PerformanceService.isOperational()) {
            completeActivation();
            return;
        }

        long elapsed = android.os.SystemClock.elapsedRealtime() - activationRequestedAt;
        if (elapsed < ACTIVATION_TIMEOUT_MS) {
            mainHandler.postDelayed(this::confirmActivation, ACTIVATION_POLL_INTERVAL_MS);
            return;
        }

        String failure = PerformanceService.getLastFailure();
        if (failure.isEmpty()) {
            failure = Diagnostics.lastError(this);
        }
        showRecovery(failure.isEmpty()
                ? "The performance overlay did not become ready within 2 seconds."
                : failure);
    }

    private void completeActivation() {
        activationPending = false;
        activationComplete = true;
        Toast.makeText(
                this,
                "🔥 100% Automated Monster Mode Active! Open BGMI now.",
                Toast.LENGTH_LONG
        ).show();

        if (isInMultiWindowMode()) {
            finish();
            return;
        }

        long remainingDelay = Math.max(0L, 500L
                - (android.os.SystemClock.elapsedRealtime() - activationRequestedAt));
        mainHandler.postDelayed(
                () -> moveTaskToBack(true),
                remainingDelay
        );
    }

    private void showRecovery(String reason) {
        activationPending = false;
        mainHandler.removeCallbacksAndMessages(null);
        Diagnostics.record(this, "recovery shown", reason);
        RecoveryActivity.show(this, reason);
        finish();
    }

    private void enableImmersiveStickyMode() {
        if (Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                int systemBars = WindowInsets.Type.statusBars()
                        | WindowInsets.Type.navigationBars();
                controller.hide(systemBars);
                controller.setSystemBarsBehavior(
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                );
            }
            return;
        }

        getWindow().getDecorView().setSystemUiVisibility(5382);
    }
}
