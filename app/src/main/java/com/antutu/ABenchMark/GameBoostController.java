package com.antutu.ABenchMark;

import android.content.Context;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The performance boost bridge.
 *
 * <p>OriginOS grants a performance profile when it sees a benchmark running.
 * ColorOS, OxygenOS and realme UI have no equivalent auto-trigger: their
 * performance boost is a <em>power mode</em> (Battery mode, High performance
 * mode) or a <em>per game mode</em> (Game Center, Pro Gamer mode).
 *
 * <p>Both of those are built on top of the AOSP game mode machinery, which
 * is scriptable:
 *
 * <pre>
 *   adb shell device_config put game_overlay &lt;pkg&gt; mode=2
 *   adb shell cmd game mode performance &lt;pkg&gt;
 * </pre>
 *
 * <p>The device_config entry makes performance mode available for a package
 * that does not declare itself a game, and the {@code cmd game mode} call
 * then activates it. This is the closest programmable equivalent of "Monster
 * Mode" on ColorOS and it works on the other ROMs as well, so the same code
 * path serves every family.
 */
public final class GameBoostController {

    private static final String TAG = "PerfSpoof/Boost";
    private static final String GAME_OVERLAY_NAMESPACE = "game_overlay";

    private static final Pattern COMPONENT_PATTERN =
            Pattern.compile("([A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z0-9_]+)+)/"
                    + "([A-Za-z0-9_.$]+)");

    private GameBoostController() { }

    /**
     * Puts a package into performance game mode.
     *
     * @return a human readable report of what was done
     */
    public static String applyPerformanceMode(Context context, String packageName) {
        if (packageName == null || packageName.trim().isEmpty()) {
            return "No package given.";
        }
        String pkg = packageName.trim();
        if (!pkg.contains(".")) {
            return "\"" + pkg + "\" is not a valid package name.";
        }

        ShellBridge.Backend backend = ShellBridge.backend(context);
        if (backend == ShellBridge.Backend.NONE) {
            return "No root and no Shizuku session: the performance game mode "
                    + "cannot be applied. Install Shizuku, grant it permission, "
                    + "or run setup-coloros.sh over adb.";
        }

        StringBuilder report = new StringBuilder();
        report.append("Boosting ").append(pkg).append('\n');

        String overlay = ShellBridge.exec(context,
                "device_config put " + GAME_OVERLAY_NAMESPACE + " " + pkg + " mode=2");
        report.append("device_config game_overlay mode=2 -> ")
                .append(overlay.isEmpty() ? "ok" : overlay.trim()).append('\n');

        String mode = ShellBridge.exec(context,
                "cmd game mode performance " + pkg);
        report.append("cmd game mode performance -> ")
                .append(mode.isEmpty() ? "ok" : mode.trim()).append('\n');

        if (mode.contains("not supported")) {
            report.append("The platform refused performance mode for this "
                    + "package. The game_overlay override is in place, so "
                    + "relaunch the game once.\n");
        } else {
            report.append("Relaunch the game for the change to take effect.\n");
        }
        return report.toString();
    }

    /** Returns a package to the standard game mode. */
    public static String resetPerformanceMode(Context context, String packageName) {
        if (packageName == null || packageName.trim().isEmpty()) {
            return "No package given.";
        }
        String pkg = packageName.trim();
        StringBuilder report = new StringBuilder();
        report.append("Resetting ").append(pkg).append('\n');
        report.append("cmd game mode standard -> ")
                .append(ShellBridge.exec(context, "cmd game mode standard " + pkg).trim())
                .append('\n');
        report.append("device_config delete -> ")
                .append(ShellBridge.exec(context,
                        "device_config delete " + GAME_OVERLAY_NAMESPACE + " " + pkg).trim())
                .append('\n');
        return report.toString();
    }

    /**
     * Finds the package that currently has focus.
     *
     * @return the package name, or an empty string when it cannot be resolved
     */
    public static String foregroundPackage(Context context) {
        String dump = ShellBridge.exec(context,
                "dumpsys activity activities | grep -E "
                        + "\"topResumedActivity|mResumedActivity|mFocusedActivity|ResumedActivity\"");
        if (dump.isEmpty()) {
            dump = ShellBridge.exec(context,
                    "dumpsys window | grep -E \"mCurrentFocus|mFocusedApp\"");
        }
        Matcher matcher = COMPONENT_PATTERN.matcher(dump);
        List<String> found = new ArrayList<>();
        while (matcher.find()) {
            String pkg = matcher.group(1);
            if (!found.contains(pkg)) {
                found.add(pkg);
            }
        }
        // Skip our own package, it is never the interesting target.
        for (String pkg : found) {
            if (!pkg.equals(context.getPackageName())) {
                return pkg;
            }
        }
        return found.isEmpty() ? "" : found.get(0);
    }

    /** Lists the interventions the platform knows about for a package. */
    public static String listInterventions(Context context, String packageName) {
        if (packageName == null || packageName.trim().isEmpty()) {
            return "No package given.";
        }
        String output = ShellBridge.exec(context,
                "cmd game list " + packageName.trim());
        return output.isEmpty() ? "No game mode data available." : output.trim();
    }

    /**
     * Applies the performance mode to the spoof package itself.
     *
     * <p>On ROMs whose performance stack inspects the running benchmark, a
     * benchmark that is itself in performance mode is the most convincing
     * thing the platform can see.
     */
    public static String applyPerformanceModeToSelf(Context context) {
        return applyPerformanceMode(context, context.getPackageName());
    }

    static void log(String message) {
        Log.i(TAG, message);
    }
}
