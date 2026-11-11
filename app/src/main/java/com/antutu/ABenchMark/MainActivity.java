package com.antutu.ABenchMark;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
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
import android.view.Window;
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
    private boolean permissionFlowActive;
    private boolean overlayRequestAttempted;
    private boolean batteryRequestAttempted;
    private boolean notificationRequestAttempted;

    public MainActivity() {
        super();

        // Registration order recovered from MainActivity.<init>() in DEX.
        notificationPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
            granted -> {
                permissionFlowActive = false;
                startServiceIfReady();
            }
        );

        overlayPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
            result -> {
                permissionFlowActive = false;
                if (Settings.canDrawOverlays(this)) {
                checkAndStartService();
                } else {
                showOverlayPermissionMessage();
                }
            }
        );

        batteryPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
            result -> {
                permissionFlowActive = false;
                checkAndStartService();
            }
        );
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState != null) {
            permissionFlowActive = savedInstanceState.getBoolean("permissionFlowActive");
            overlayRequestAttempted = savedInstanceState.getBoolean("overlayRequestAttempted");
            batteryRequestAttempted = savedInstanceState.getBoolean("batteryRequestAttempted");
            notificationRequestAttempted = savedInstanceState.getBoolean("notificationRequestAttempted");
        }

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
    protected void onSaveInstanceState(Bundle outState) {
        outState.putBoolean("permissionFlowActive", permissionFlowActive);
        outState.putBoolean("overlayRequestAttempted", overlayRequestAttempted);
        outState.putBoolean("batteryRequestAttempted", batteryRequestAttempted);
        outState.putBoolean("notificationRequestAttempted", notificationRequestAttempted);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onResume() {
        super.onResume();

        overridePendingTransition(0, 0);
        enableImmersiveStickyMode();

        if (!permissionFlowActive
                && Settings.canDrawOverlays(this)
                && !PerformanceService.Companion.isRunning()) {
            checkAndStartService();
            return;
        }

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
        if (permissionFlowActive || PerformanceService.Companion.isRunning()) {
            return;
        }

        if (!Settings.canDrawOverlays(this)) {
            if (overlayRequestAttempted) {
                showOverlayPermissionMessage();
                return;
            }

            Uri packageUri = Uri.parse("package:" + getPackageName());
            Intent overlayIntent = new Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    packageUri
            );
            overlayRequestAttempted = true;
            permissionFlowActive = true;
            try {
                overlayPermissionLauncher.launch(overlayIntent);
            } catch (RuntimeException exception) {
                permissionFlowActive = false;
                showOverlayPermissionMessage();
            }
            return;
        }

        PowerManager powerManager =
                (PowerManager) getSystemService(POWER_SERVICE);

        if (powerManager != null
                && !batteryRequestAttempted
                && !isIgnoringBatteryOptimizations(powerManager)) {
            batteryRequestAttempted = true;
            Uri packageUri = Uri.parse("package:" + getPackageName());
            Intent batteryIntent = new Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    packageUri
            );
            permissionFlowActive = true;
            try {
                batteryPermissionLauncher.launch(batteryIntent);
                return;
            } catch (RuntimeException exception) {
                permissionFlowActive = false;
            }
        }

        if (Build.VERSION.SDK_INT >= 33
                && !notificationRequestAttempted
                && ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED) {
            notificationRequestAttempted = true;
            permissionFlowActive = true;
            try {
                notificationPermissionLauncher.launch(
                        Manifest.permission.POST_NOTIFICATIONS
                );
                return;
            } catch (RuntimeException exception) {
                permissionFlowActive = false;
            }
        }

        startServiceIfReady();
    }

    private boolean isIgnoringBatteryOptimizations(PowerManager powerManager) {
        try {
            return powerManager.isIgnoringBatteryOptimizations(getPackageName());
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private void startServiceIfReady() {
        if (permissionFlowActive || PerformanceService.Companion.isRunning()) {
            return;
        }

        if (!Settings.canDrawOverlays(this)) {
            showOverlayPermissionMessage();
            return;
        }

        try {
            PerformanceService.Companion.startService(this);
        } catch (RuntimeException exception) {
            Toast.makeText(
                    this,
                    "Monster Mode could not start. Open the app and try again.",
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

    private void showOverlayPermissionMessage() {
        Toast.makeText(
                this,
                "Please grant 'Display over other apps' permission for Monster Mode.",
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
