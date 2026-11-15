package com.antutu.ABenchMark;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * The setup console.
 *
 * <p>Reachable from the launcher icon long press, so the normal launch flow
 * stays exactly as it was: a transparent activity that starts the service and
 * moves itself to the back.
 *
 * <p>It exposes the three things a user needs on a Chinese OEM skin:
 * re-apply the survival exemptions, open the vendor screens that cannot be
 * scripted, and turn on the performance game mode for the game they want to
 * boost.
 */
public final class SetupActivity extends Activity {

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final LinearLayout content = new LinearLayout(this);
    private TextView statusView;
    private EditText packageField;
    private TextView reportView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        int padding = (int) (24 * getResources().getDisplayMetrics().density);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(padding, padding, padding, padding);

        TextView title = new TextView(this);
        title.setText("Performance Spoof — setup");
        title.setTextSize(22);
        content.addView(title);

        statusView = new TextView(this);
        statusView.setTextSize(14);
        content.addView(statusView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // ---- survival ------------------------------------------------------
        content.addView(section("Keep the service alive"));

        Button apply = button("Apply ROM protections", v -> {
            Toast.makeText(this, "Applying protections...", Toast.LENGTH_SHORT).show();
            RomGuard.applyAsync(this, () -> mainHandler.post(() -> {
                Toast.makeText(this, "Protections applied.", Toast.LENGTH_LONG).show();
                refreshStatus();
            }));
        });
        content.addView(apply);

        Button shizuku = button("Authorize Shizuku (no root)", v -> {
            ShellBridge.invalidate();
            ShellBridge.ShizukuBridge.requestPermission(this);
            Toast.makeText(this,
                    "If Shizuku is running, accept the request, then tap Apply.",
                    Toast.LENGTH_LONG).show();
        });
        content.addView(shizuku);

        Button autostart = button("Open auto-launch settings", v -> {
            if (!RomGuard.openAutostartSettings(this)) {
                toast("Auto-launch screen not available on this ROM.");
            }
        });
        content.addView(autostart);

        Button background = button("Open background activity settings", v -> {
            if (!RomGuard.openBackgroundActivitySettings(this)) {
                toast("Battery screen not available on this ROM.");
            }
        });
        content.addView(background);

        Button overlay = button("Open overlay permission", v -> {
            if (!RomGuard.openOverlaySettings(this)) {
                toast("Overlay settings not available.");
            }
        });
        content.addView(overlay);

        Button lockHint = button("How to lock the app in Recents", v -> toast(
                "Open Recents, pull down the Performance Spoof card to lock it.\n"
                        + "A locked card is never cleared by \"Clear all\"."));
        content.addView(lockHint);

        // ---- boost ---------------------------------------------------------
        content.addView(section("Boost a game"));

        TextView hint = new TextView(this);
        hint.setTextSize(13);
        hint.setText("Package name of the game, for example com.tencent.tmgp.pubgmhd\n"
                + "Or use the button below to pick the app that is on screen.");
        content.addView(hint);

        packageField = new EditText(this);
        packageField.setHint("game package name");
        packageField.setSingleLine(true);
        content.addView(packageField);

        Button foreground = button("Use the app on screen", v -> {
            String pkg = GameBoostController.foregroundPackage(this);
            if (pkg.isEmpty()) {
                toast("Could not resolve the foreground app.");
            } else {
                packageField.setText(pkg);
                toast("Selected " + pkg);
            }
        });
        content.addView(foreground);

        Button boost = button("Turn on performance mode", v -> {
            String pkg = packageField.getText().toString().trim();
            if (TextUtils.isEmpty(pkg)) {
                toast("Enter a package name first.");
                return;
            }
            runPrivileged(report -> GameBoostController.applyPerformanceMode(this, pkg));
        });
        content.addView(boost);

        Button boostSelf = button("Performance mode for the spoof itself", v ->
                runPrivileged(report -> GameBoostController.applyPerformanceModeToSelf(this)));
        content.addView(boostSelf);

        Button reset = button("Reset performance mode", v -> {
            String pkg = packageField.getText().toString().trim();
            if (TextUtils.isEmpty(pkg)) {
                toast("Enter a package name first.");
                return;
            }
            runPrivileged(report -> GameBoostController.resetPerformanceMode(this, pkg));
        });
        content.addView(reset);

        Button gameCenter = button("Open Game Center (Pro Gamer mode)", v -> {
            if (!RomGuard.openGameCenter(this)) {
                toast("Game Center not found on this ROM.");
            }
        });
        content.addView(gameCenter);

        Button batteryMode = button("Open battery mode / High performance mode", v -> {
            if (!RomGuard.openBatteryModeSettings(this)) {
                toast("Battery settings not available.");
            }
        });
        content.addView(batteryMode);

        // ---- diagnostics ---------------------------------------------------
        content.addView(section("Diagnostics"));

        reportView = new TextView(this);
        reportView.setTextSize(13);
        content.addView(reportView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        Button status = button("Refresh status", v -> refreshStatus());
        content.addView(status);

        Button stop = button("Stop the service", v -> {
            PerformanceService.Companion.stopService(this);
            toast("Stopped.");
            refreshStatus();
        });
        content.addView(stop);

        ScrollView scrollView = new ScrollView(this);
        scrollView.addView(content);
        setContentView(scrollView);

        refreshStatus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatus();
    }

    private TextView section(String label) {
        TextView view = new TextView(this);
        view.setText(label);
        view.setTextSize(17);
        view.setPadding(0, 32, 0, 8);
        content.addView(view);
        return view;
    }

    private Button button(String label, View.OnClickListener listener) {
        Button view = new Button(this);
        view.setText(label);
        view.setOnClickListener(listener);
        return view;
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    /** Runs a privileged action off the UI thread and shows its report. */
    private void runPrivileged(java.util.function.Function<Void, String> action) {
        reportView.setText("Working...");
        new Thread(() -> {
            String report;
            try {
                report = action.apply(null);
            } catch (Throwable throwable) {
                report = "Failed: " + throwable;
            }
            String finalReport = report;
            mainHandler.post(() -> reportView.setText(finalReport));
        }, "perf-spoof-action").start();
    }

    private void refreshStatus() {
        if (statusView == null) {
            return;
        }
        StringBuilder builder = new StringBuilder();
        builder.append(RomCompatibility.detect()).append('\n');
        builder.append("Service active: ").append(PerformanceService.isOperational()).append('\n');
        builder.append("Overlay attached: ").append(PerformanceService.isOverlayAttached()).append('\n');
        builder.append("Guards applied: ").append(RomGuard.isGuarded(this)).append('\n');
        builder.append("Privileged backend: ").append(ShellBridge.resolvedBackend()).append('\n');
        statusView.setText(builder.toString());
    }
}
