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

/** Shown only when standard activation cannot complete. */
public final class RecoveryActivity extends Activity {

    public static final String EXTRA_REASON = "reason";

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
