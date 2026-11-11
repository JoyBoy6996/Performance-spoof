package com.antutu.ABenchMark;

import android.os.Build;

import java.util.Locale;

/** Uses only public build identifiers; no OEM-private APIs or intents are used. */
public final class RomCompatibility {

    public enum Family { ORIGIN_OS, COLOR_OS, HYPER_OS, STANDARD_ANDROID }

    private RomCompatibility() { }

    public static Family detect() {
        String manufacturer = normalized(Build.MANUFACTURER);
        String brand = normalized(Build.BRAND);
        if (contains(manufacturer, "vivo", "iqoo") || contains(brand, "vivo", "iqoo")) {
            return Family.ORIGIN_OS;
        }
        if (contains(manufacturer, "oppo", "oneplus", "realme", "oplus")
                || contains(brand, "oppo", "oneplus", "realme", "oplus")) {
            return Family.COLOR_OS;
        }
        if (contains(manufacturer, "xiaomi", "redmi", "poco")
                || contains(brand, "xiaomi", "redmi", "poco")) {
            return Family.HYPER_OS;
        }
        return Family.STANDARD_ANDROID;
    }

    public static String guidance(Family family) {
        switch (family) {
            case COLOR_OS:
                return "ColorOS setup required:\n\n"
                        + "1. Allow Display over other apps.\n"
                        + "2. Set battery use to Unrestricted / Don't optimize.\n"
                        + "3. If your device offers Background activity, allow it for this app.\n\n"
                        + "Names and locations vary by ColorOS version.";
            case HYPER_OS:
                return "HyperOS setup required:\n\n"
                        + "1. Allow Display over other apps.\n"
                        + "2. Set Battery saver to No restrictions.\n"
                        + "3. Optionally enable Auto-start for better persistence.\n\n"
                        + "Auto-start remains a setting you control.";
            case ORIGIN_OS:
                return "OriginOS setup required:\n\n"
                        + "Allow Display over other apps and Unrestricted battery usage, then retry.";
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
