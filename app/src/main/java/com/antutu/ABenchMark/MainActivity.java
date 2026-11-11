package com.antutu.ABenchMark;

import android.Manifest;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.activity.ComponentActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;

/**
 * Reconstructed from com.antutu.ABenchMark.MainActivity in classes.dex.
 *
 * The original Activity is intentionally transparent and contains no visible
 * controls. Its only purpose is permission gating and starting the foreground
 * PerformanceService, then moving its task behind the app the user opens.
 */
public final class MainActivity extends ComponentActivity {

    public static final int $stable = 8;

    private final ActivityResultLauncher<String> notificationPermissionLauncher;
    private final ActivityResultLauncher<Intent> overlayPermissionLauncher;
    private final ActivityResultLauncher<Intent> batteryPermissionLauncher;
    private boolean notificationPermissionHandled;
    private boolean serviceStartRequested;

    public MainActivity() {
        super();

        // Registration order recovered from MainActivity.<init>() in DEX.
        notificationPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                granted -> {
                    notificationPermissionHandled = true;
                    checkAndStartService();
                }
        );

        overlayPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (Settings.canDrawOverlays(this)) {
                        checkAndStartService();
                    } else {
                        showPermissionDenied("Display over other apps");
                    }
                }
        );

        batteryPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    PowerManager powerManager =
                            (PowerManager) getSystemService(POWER_SERVICE);
                    if (powerManager != null
                            && powerManager.isIgnoringBatteryOptimizations(getPackageName())) {
                        checkAndStartService();
                    } else {
                        showPermissionDenied("Unrestricted Battery usage");
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

        if (PerformanceService.Companion.isRunning()) {
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

        PowerManager powerManager =
                (PowerManager) getSystemService(POWER_SERVICE);

        if (powerManager != null
            && !powerManager.isIgnoringBatteryOptimizations(getPackageName())) {
            Toast.makeText(
                    this,
                    "Please allow Unrestricted Battery usage for continuous Monster Mode.",
                    Toast.LENGTH_LONG
            ).show();

            Uri packageUri = Uri.parse("package:" + getPackageName());
            Intent batteryIntent = new Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    packageUri
            );
            batteryPermissionLauncher.launch(batteryIntent);
            return;
        }

        if (Build.VERSION.SDK_INT >= 33
                && ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.POST_NOTIFICATIONS
                ) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            if (!notificationPermissionHandled) {
                notificationPermissionLauncher.launch(
                        Manifest.permission.POST_NOTIFICATIONS
                );
                return;
            }
        }

        if (serviceStartRequested || PerformanceService.Companion.isRunning()) {
            return;
        }

        serviceStartRequested = true;
        try {
            PerformanceService.Companion.startService(this);
        } catch (RuntimeException exception) {
            serviceStartRequested = false;
            Toast.makeText(
                    this,
                    "Unable to start Monster Mode. Please try again.",
                    Toast.LENGTH_LONG
            ).show();
            return;
        }

        Toast.makeText(
                this,
                "🔥 100% Automated Monster Mode Active! Open BGMI now.",
                Toast.LENGTH_LONG
        ).show();

        if (isInMultiWindowMode()) {
            finish();
            return;
        }

        new Handler(Looper.getMainLooper()).postDelayed(
                () -> moveTaskToBack(true),
                500L
        );
    }

    private void showPermissionDenied(String permissionName) {
        Toast.makeText(
                this,
                permissionName + " permission is required for Monster Mode.",
                Toast.LENGTH_LONG
        ).show();
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
