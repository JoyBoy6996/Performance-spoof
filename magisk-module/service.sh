#!/system/bin/sh
# ROM watchdog for com.antutu.ABenchMark.
#
# The ROMs stop special-use foreground services and freeze background
# processes. This loop re-applies the exemptions and restarts the service.
# It runs as root, so the background foreground-service restriction of
# Android 12+ does not apply.

MODDIR=${0%/*}
PKG=com.antutu.ABenchMark
SERVICE=com.antutu.ABenchMark/.PerformanceService
ACTION=com.antutu.ABenchMark.ACTION_START

# Wait for the framework.
until [ "$(getprop sys.boot_completed)" = "1" ]; do sleep 2; done
sleep 25

detect_family() {
    if [ -n "$(getprop ro.build.version.oplusrom)" ] || [ -n "$(getprop ro.build.version.opporom)" ]; then
        echo "oplus"
    elif [ -n "$(getprop ro.miui.ui.version.name)" ] || [ -n "$(getprop ro.miui.ui.version.code)" ]; then
        echo "xiaomi"
    else
        mfr=$(getprop ro.product.manufacturer | tr 'A-Z' 'a-z')
        brand=$(getprop ro.product.brand | tr 'A-Z' 'a-z')
        case "$mfr $brand" in
            *oppo*|*oneplus*|*realme*|*oplus*) echo "oplus" ;;
            *xiaomi*|*redmi*|*poco*) echo "xiaomi" ;;
            *) echo "generic" ;;
        esac
    fi
}

FAMILY=$(detect_family)

apply_guards() {
    cmd appops set $PKG RUN_ANY_IN_BACKGROUND allow >/dev/null 2>&1
    cmd appops set $PKG RUN_IN_BACKGROUND allow >/dev/null 2>&1
    cmd appops set $PKG START_FOREGROUND allow >/dev/null 2>&1
    cmd appops set $PKG WAKE_LOCK allow >/dev/null 2>&1
    cmd appops set $PKG SYSTEM_ALERT_WINDOW allow >/dev/null 2>&1
    dumpsys deviceidle whitelist +$PKG >/dev/null 2>&1
    cmd deviceidle except-idle-whitelist +$PKG >/dev/null 2>&1
    am set-standby-bucket $PKG active >/dev/null 2>&1
    am set-inactive $PKG false >/dev/null 2>&1
    cmd package unstop --user 0 $PKG >/dev/null 2>&1

    # Performance game mode for the spoof itself: the platform then sees a
    # running benchmark that is already in performance mode.
    device_config put game_overlay $PKG mode=2 >/dev/null 2>&1
    cmd game mode performance $PKG >/dev/null 2>&1

    if [ "$FAMILY" = "xiaomi" ]; then
        settings put global cached_apps_freezer disabled
        settings put system cloud_greezer_enable false
        settings put system cloud_memFreeze_control 0
        for wl in cloud_memory_freeze_whitelist cloud_lowlatency_whitelist cloud_network_priority_whitelist; do
            current=$(settings get system $wl 2>/dev/null | head -n1)
            [ "$current" = "null" ] && current=""
            case ",$current," in
                *",$PKG,"*) ;;
                *)
                    if [ -z "$current" ]; then
                        settings put system $wl $PKG
                    else
                        settings put system $wl "$current,$PKG"
                    fi
                    ;;
            esac
        done
    fi
}

user_stopped() {
    [ -f /data/user/0/$PKG/files/stopped ] || [ -f /data/data/$PKG/files/stopped ]
}

while true; do
    apply_guards

    if ! user_stopped; then
        if ! pidof $PKG >/dev/null 2>&1; then
            am start-foreground-service --user 0 -n $SERVICE -a $ACTION >/dev/null 2>&1 \
                || am startservice --user 0 -n $SERVICE -a $ACTION >/dev/null 2>&1
        fi
    fi

    sleep 20
done
