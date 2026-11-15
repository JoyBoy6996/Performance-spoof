package com.antutu.ABenchMark;

import android.os.Build;

import java.util.Locale;

/** Uses only public build identifiers; no OEM-private APIs or intents are used. */
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
                return "OPlus-family setup required:\n\n"
                        + "1. Allow Display over other apps.\n"
                        + "2. Set battery use to Unrestricted / Don't optimize.\n"
                        + "3. If your device offers Background activity, allow it for this app.\n\n"
                        + "Names and locations vary by ColorOS, OxygenOS, and realme UI version.";
            case HYPER_OS:
                return "HyperOS setup required:\n\n"
                        + "1. Allow Display over other apps.\n"
                        + "2. Settings > Apps > " + appLabelHint() + " > Battery saver > No restrictions.\n"
                        + "3. Settings > Apps > " + appLabelHint() + " > Autostart > ON.\n"
                        + "4. Settings > Apps > " + appLabelHint() + " > Other permissions > Start in background > allow.\n"
                        + "5. Tap 'Apply HyperOS protections' below if root or Shizuku is available.\n\n"
                        + "HyperOS freezes background processes (Greezer and the memory\n"
                        + "freezer) and destroys the overlay surface of a frozen app.\n"
                        + "Auto-start matters only if a future version adds boot-time startup.";
            case ORIGIN_OS:
                return "OriginOS setup required:\n\n"
                        + "Allow Display over other apps and Unrestricted battery usage, then retry.";
            default:
                return "Android setup required:\n\n"
                        + "Allow Display over other apps and remove battery optimization for this app, then retry.";
        }
    }

    /** Short hint used in the setup instructions. */
    private static String appLabelHint() {
        return "this app";
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
