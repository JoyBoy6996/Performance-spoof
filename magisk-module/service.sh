#!/system/bin/sh
# HyperOS watchdog for com.antutu.ABenchMark.
#
# HyperOS stops special-use foreground services and freezes background
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

apply_guards() {
    cmd appops set $PKG RUN_ANY_IN_BACKGROUND allow >/dev/null 2>&1
    cmd appops set $PKG RUN_IN_BACKGROUND allow >/dev/null 2>&1
    cmd appops set $PKG START_FOREGROUND allow >/dev/null 2>&1
    cmd appops set $PKG WAKE_LOCK allow >/dev/null 2>&1
    cmd appops set $PKG SYSTEM_ALERT_WINDOW allow >/dev/null 2>&1
    dumpsys deviceidle whitelist +$PKG >/dev/null 2>&1
    cmd deviceidle except-idle-whitelist +$PKG >/dev/null 2>&1
    am set-standby-bucket $PKG active >/dev/null 2>&1
    cmd package unstop --user 0 $PKG >/dev/null 2>&1

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

    settings put global cached_apps_freezer disabled
    settings put system cloud_greezer_enable false
    settings put system cloud_memFreeze_control 0
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
