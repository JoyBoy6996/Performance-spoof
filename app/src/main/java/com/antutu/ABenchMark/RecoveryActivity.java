package com.antutu.ABenchMark;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Shown only when standard activation cannot complete.
 *
 * <p>On HyperOS this screen is also the manual repair console: it exposes the
 * ADB-shell hardening commands (via root or Shizuku) that the automatic path
 * uses, plus the vendor settings screens that cannot be reached through a
 * standard Android intent.
 */
public final class RecoveryActivity extends Activity {

    public static final String EXTRA_REASON = "reason";

    private final android.os.Handler mainHandler =
            new android.os.Handler(android.os.Looper.getMainLooper());

    public static void show(Activity source, String reason) {
        Intent intent = new Intent(source, RecoveryActivity.class);
        intent.putExtra(EXTRA_REASON, reason);
        source.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        int padding = (int) (24 * getResources().getDisplayMetrics().density);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(padding, padding, padding, padding);

        TextView title = new TextView(this);
        title.setText("Activation needs attention");
        title.setTextSize(22);
        content.addView(title);

        TextView details = new TextView(this);
        String reason = getIntent().getStringExtra(EXTRA_REASON);
        details.setText((reason == null || reason.isEmpty() ? "Activation did not complete." : reason)
                + "\n\n" + RomCompatibility.guidance(RomCompatibility.detect())
                + "\n\nDiagnostics\n" + Diagnostics.summary(this));
        details.setTextSize(15);
        content.addView(details, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        Button overlaySettings = new Button(this);
        overlaySettings.setText("Open overlay permission");
        overlaySettings.setOnClickListener(v -> startActivity(new Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName())
        )));
        content.addView(overlaySettings);

        Button batterySettings = new Button(this);
        batterySettings.setText("Request unrestricted battery use");
        batterySettings.setOnClickListener(v -> startActivity(new Intent(
                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Uri.parse("package:" + getPackageName())
        )));
        content.addView(batterySettings);

        Button settings = new Button(this);
        settings.setText("Open app settings");
        settings.setOnClickListener(v -> {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        });
        content.addView(settings);

        Button autostart = new Button(this);
        autostart.setText("Open auto-start settings (HyperOS)");
        autostart.setOnClickListener(v -> {
            if (!HyperOsGuard.openAutostartSettings(this)) {
                Toast.makeText(this,
                        "Auto-start screen not available on this ROM.",
                        Toast.LENGTH_LONG).show();
            }
        });
        content.addView(autostart);

        Button shizuku = new Button(this);
        shizuku.setText("Authorize Shizuku (no root)");
        shizuku.setOnClickListener(v -> {
            // The backend may have been resolved as NONE before Shizuku was
            // running, so force a fresh probe on the next privileged call.
            ShellBridge.invalidate();
            ShellBridge.ShizukuBridge.requestPermission(this);
            Toast.makeText(this,
                    "If Shizuku is running, accept the request, then tap Apply.",
                    Toast.LENGTH_LONG).show();
        });
        content.addView(shizuku);

        Button apply = new Button(this);
        apply.setText("Apply HyperOS protections");
        apply.setOnClickListener(v -> {
            Toast.makeText(this, "Applying HyperOS protections...",
                    Toast.LENGTH_SHORT).show();
            ShellBridge.invalidate();
            HyperOsGuard.applyAsync(this, () -> mainHandler.post(() ->
                    Toast.makeText(this, "HyperOS protections applied.",
                            Toast.LENGTH_LONG).show()));
        });
        content.addView(apply);

        Button preview = new Button(this);
        preview.setText("Show protection status");
        preview.setOnClickListener(v -> {
            TextView report = new TextView(this);
            report.setTextSize(13);
            report.setPadding(padding, padding, padding, padding);
            content.addView(report);
            report.setText("Collecting...");
            // The privileged backend probe must not run on the UI thread.
            new Thread(() -> {
                String text = HyperOsGuard.summary(this);
                mainHandler.post(() -> report.setText(text));
            }, "perf-spoof-report").start();
        });
        content.addView(preview);

        Button retry = new Button(this);
        retry.setText("Retry activation");
        retry.setOnClickListener(v -> {
            startActivity(new Intent(this, MainActivity.class));
            finish();
        });
        content.addView(retry);

        ScrollView scrollView = new ScrollView(this);
        scrollView.addView(content);
        setContentView(scrollView);
    }
}
