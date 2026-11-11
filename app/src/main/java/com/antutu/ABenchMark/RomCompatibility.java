package com.antutu.ABenchMark;

import android.os.Build;

import java.util.Locale;

/** Uses only public Build fields; no OEM-private APIs or intents are used. */
public final class RomCompatibility {

    public enum Family {
        HYPER_OS,
        MIUI,
        XIAOMI_OS_UNIDENTIFIED,
        COLOR_OS,
        OXYGEN_OS,
        REALME_UI,
        OPLUS_OS_UNIDENTIFIED,
        ORIGIN_OS,
        FUNTOUCH_OS,
        VIVO_OS_UNIDENTIFIED,
        STANDARD_ANDROID
    }

    private RomCompatibility() { }

    public static Family detect() {
        String manufacturer = normalized(Build.MANUFACTURER);
        String brand = normalized(Build.BRAND);
        String build = normalized(Build.DISPLAY) + " "
                + normalized(Build.FINGERPRINT) + " "
                + normalized(Build.PRODUCT) + " "
                + normalized(Build.DEVICE) + " "
                + normalized(Build.VERSION.INCREMENTAL);

        if (contains(build, "hyperos", "hyper os")) return Family.HYPER_OS;
        if (contains(build, "miui")) return Family.MIUI;
        if (contains(build, "coloros", "color os")) return Family.COLOR_OS;
        if (contains(build, "oxygenos", "oxygen os")) return Family.OXYGEN_OS;
        if (contains(build, "realmeui", "realme ui")) return Family.REALME_UI;
        if (contains(build, "originos", "origin os")) return Family.ORIGIN_OS;
        if (contains(build, "funtouchos", "funtouch os")) return Family.FUNTOUCH_OS;

        String vendor = manufacturer + " " + brand;
        if (contains(vendor, "xiaomi", "redmi", "poco")) {
            return Family.XIAOMI_OS_UNIDENTIFIED;
        }
        if (contains(vendor, "oppo", "oneplus", "realme", "oplus")) {
            return Family.OPLUS_OS_UNIDENTIFIED;
        }
        if (contains(vendor, "vivo", "iqoo")) {
            return Family.VIVO_OS_UNIDENTIFIED;
        }
        return Family.STANDARD_ANDROID;
    }

    public static String guidance(Family family) {
        switch (family) {
            case HYPER_OS:
                return "HyperOS: allow Display over other apps. If the service is later stopped by the system, review this app's battery settings. Battery restrictions are not an activation requirement.";
            case MIUI:
                return "MIUI: allow Display over other apps. If the service is later stopped by the system, review this app's battery settings. Battery restrictions are not an activation requirement.";
            case COLOR_OS:
                return "ColorOS: allow Display over other apps. If background operation is interrupted, review this app's battery or background-activity settings; labels vary by version.";
            case OXYGEN_OS:
                return "OxygenOS: allow Display over other apps. If background operation is interrupted, review this app's battery or background-activity settings; labels vary by version.";
            case REALME_UI:
                return "realme UI: allow Display over other apps. If background operation is interrupted, review this app's battery or background-activity settings; labels vary by version.";
            case ORIGIN_OS:
                return "OriginOS: allow Display over other apps. If background operation is interrupted, review this app's battery settings; available controls vary by device and version.";
            case FUNTOUCH_OS:
                return "Funtouch OS: allow Display over other apps. If background operation is interrupted, review this app's battery settings; available controls vary by device and version.";
            case XIAOMI_OS_UNIDENTIFIED:
                return "Xiaomi device detected, but its ROM variant could not be identified from public build fields. Allow Display over other apps and use the standard Android app settings if needed.";
            case OPLUS_OS_UNIDENTIFIED:
                return "OPPO/OnePlus/realme device detected, but its ROM variant could not be identified from public build fields. Allow Display over other apps and use the standard Android app settings if needed.";
            case VIVO_OS_UNIDENTIFIED:
                return "vivo/iQOO device detected, but its ROM variant could not be identified from public build fields. Allow Display over other apps and use the standard Android app settings if needed.";
            default:
                return "Android: allow Display over other apps. Battery optimization and notification permission are optional and are not required to activate the service.";
        }
    }

    public static String buildIdentifiers() {
        return "manufacturer=" + safe(Build.MANUFACTURER)
                + ", brand=" + safe(Build.BRAND)
                + ", model=" + safe(Build.MODEL)
                + ", display=" + safe(Build.DISPLAY)
                + ", product=" + safe(Build.PRODUCT)
                + ", sdk=" + Build.VERSION.SDK_INT;
    }

    private static String normalized(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private static String safe(String value) {
        return value == null || value.isEmpty() ? "unknown" : value;
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
