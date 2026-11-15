package com.antutu.ABenchMark;

import android.os.Build;

import java.util.Locale;

/**
 * Uses only public build identifiers; no OEM-private APIs or intents are used
 * to detect the ROM family.
 */
public final class RomCompatibility {

    public enum Family { ORIGIN_OS, OPLUS_FAMILY, HYPER_OS, STANDARD_ANDROID }

    private RomCompatibility() { }

    public static Family detect() {
        String manufacturer = normalized(Build.MANUFACTURER);
        String brand = normalized(Build.BRAND);
        if (contains(manufacturer, "vivo", "iqoo") || contains(brand, "vivo", "iqoo")) {
            return Family.ORIGIN_OS;
        }
        if (contains(manufacturer, "oppo", "oneplus", "realme", "oplus")
                || contains(brand, "oppo", "oneplus", "realme", "oplus")) {
            return Family.OPLUS_FAMILY;
        }
        if (contains(manufacturer, "xiaomi", "redmi", "poco")
                || contains(brand, "xiaomi", "redmi", "poco")) {
            return Family.HYPER_OS;
        }
        return Family.STANDARD_ANDROID;
    }

    public static String guidance(Family family) {
        switch (family) {
            case OPLUS_FAMILY:
                return "ColorOS setup required:\n\n"
                        + "1. Settings > Apps > this app > Allow display over other apps.\n"
                        + "2. Settings > Apps > Auto-launch > this app > ON.\n"
                        + "3. Settings > Apps > App management > this app > Battery usage:\n"
                        + "     Allow background activity ON, Allow foreground activity ON,\n"
                        + "     Optimize battery use OFF.\n"
                        + "4. Settings > Battery > Power saving settings > App quick freeze:\n"
                        + "     remove this app from the freeze list.\n"
                        + "5. Settings > Battery > Power saver options > Sleep standby\n"
                        + "     optimisation OFF.\n"
                        + "6. Pull the card down in Recents to lock it.\n\n"
                        + "ColorOS has no Monster Mode. The performance boost is the\n"
                        + "AOSP performance game mode, applied from the setup screen.";
            case HYPER_OS:
                return "HyperOS setup required:\n\n"
                        + "1. Allow Display over other apps.\n"
                        + "2. Settings > Apps > this app > Battery saver > No restrictions.\n"
                        + "3. Settings > Apps > this app > Autostart > ON.\n"
                        + "4. Settings > Apps > this app > Other permissions > Start in background > allow.\n"
                        + "5. Tap 'Apply ROM protections' below if root or Shizuku is available.\n\n"
                        + "HyperOS freezes background processes (Greezer and the memory\n"
                        + "freezer) and destroys the overlay surface of a frozen app.";
            case ORIGIN_OS:
                return "OriginOS setup required:\n\n"
                        + "Allow Display over other apps and Unrestricted battery usage, then retry.\n"
                        + "OriginOS grants the performance profile when it sees the running\n"
                        + "benchmark, so the spoof needs the process and the overlay alive.";
            default:
                return "Android setup required:\n\n"
                        + "Allow Display over other apps and remove battery optimization for this app, then retry.";
        }
    }

    private static String normalized(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private static boolean contains(String value, String... candidates) {
        for (String candidate : candidates) {
            if (value.contains(candidate)) {
                return true;
            }
        }
        return false;
    }
}
