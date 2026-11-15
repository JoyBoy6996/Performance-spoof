#!/usr/bin/env bash
#
# setup-coloros.sh
#
# Applies the survival exemptions that Performance Spoof needs on ColorOS /
# OxygenOS / realme UI, and optionally turns on the performance game mode for
# a game package.
#
# Usage:
#   adb push setup-coloros.sh /data/local/tmp/
#   adb shell sh /data/local/tmp/setup-coloros.sh [game.package.name]
#
# Examples:
#   adb shell sh /data/local/tmp/setup-coloros.sh
#   adb shell sh /data/local/tmp/setup-coloros.sh com.tencent.tmgp.pubgmhd
#
# Run it again after a reboot or a system update: ColorOS restores its
# defaults, and the game mode is cleared when the game is reinstalled.

set -u

PKG="com.antutu.ABenchMark"
SERVICE="com.antutu.ABenchMark/.PerformanceService"
ACTION="com.antutu.ABenchMark.ACTION_START"
GAME="${1:-}"

say() { echo "[setup] $*"; }
run() { "$@" 2>&1 | sed 's/^/[setup]   /'; }

# Root is needed for the settings writes; the plain shell identity is still
# enough for the AppOps and doze whitelist.
if command -v su >/dev/null 2>&1 && su -c id >/dev/null 2>&1; then
    say "root available"
    as_root() { su -c "$*"; }
    HAVE_ROOT=1
else
    say "no root, running as shell (AppOps, doze whitelist, game mode only)"
    as_root() { "$@"; }
    HAVE_ROOT=0
fi

# ---- ROM family ---------------------------------------------------------
detect_family() {
    mfr=$(getprop ro.product.manufacturer | tr 'A-Z' 'a-z')
    brand=$(getprop ro.product.brand | tr 'A-Z' 'a-z')
    if [ -n "$(getprop ro.build.version.oplusrom)" ] || [ -n "$(getprop ro.build.version.opporom)" ]; then
        echo "oplus"
    elif [ -n "$(getprop ro.miui.ui.version.name)" ] || [ -n "$(getprop ro.miui.ui.version.code)" ]; then
        echo "xiaomi"
    else
        case "$mfr $brand" in
            *oppo*|*oneplus*|*realme*|*oplus*) echo "oplus" ;;
            *xiaomi*|*redmi*|*poco*) echo "xiaomi" ;;
            *) echo "generic" ;;
        esac
    fi
}

FAMILY=$(detect_family)
say "ROM family: $FAMILY"

# ---- shared AOSP layer --------------------------------------------------
say "appops"
for op in RUN_ANY_IN_BACKGROUND RUN_IN_BACKGROUND START_FOREGROUND WAKE_LOCK SYSTEM_ALERT_WINDOW; do
    as_root cmd appops set "$PKG" "$op" allow
done

say "doze whitelist"
as_root dumpsys deviceidle whitelist "+$PKG"
as_root cmd deviceidle except-idle-whitelist "+$PKG"

say "standby bucket"
as_root am set-standby-bucket "$PKG" active
as_root am set-inactive "$PKG" false
as_root cmd package unstop --user 0 "$PKG"

# ---- vendor layer -------------------------------------------------------
if [ "$FAMILY" = "xiaomi" ] && [ "$HAVE_ROOT" = "1" ]; then
    say "HyperOS freeze switches"
    as_root settings put global cached_apps_freezer disabled
    as_root settings put system cloud_greezer_enable false
    as_root settings put system cloud_memFreeze_control 0
    for wl in cloud_memory_freeze_whitelist cloud_lowlatency_whitelist cloud_network_priority_whitelist; do
        current=$(as_root settings get system "$wl" 2>/dev/null | tr -d '\r' | head -n1)
        [ "$current" = "null" ] && current=""
        case ",$current," in
            *",$PKG,"*) ;;
            *) if [ -z "$current" ]; then
                   as_root settings put system "$wl" "$PKG"
               else
                   as_root settings put system "$wl" "$current,$PKG"
               fi ;;
        esac
    done
elif [ "$FAMILY" = "oplus" ]; then
    say "ColorOS: App Quick Freeze, Sleep standby optimisation and the per-app"
    say "battery mode are vendor toggles and cannot be scripted. Set them by hand:"
    say "  Settings > Apps > Auto-launch > $PKG > ON"
    say "  Settings > Apps > App management > $PKG > Battery usage >"
    say "      Allow background activity ON, Allow foreground activity ON,"
    say "      Optimize battery use OFF"
    say "  Settings > Battery > Power saving settings > App quick freeze > remove $PKG"
    say "  Settings > Battery > Power saver options > Sleep standby optimisation OFF"
    say "  Pull the card down in Recents to lock the app"
fi

# ---- performance game mode ---------------------------------------------
if [ -n "$GAME" ]; then
    say "performance game mode for $GAME"
    as_root device_config put game_overlay "$GAME" mode=2
    as_root cmd game mode performance "$GAME"
    say "relaunch the game for the change to take effect"
fi

# Also put the spoof package itself into performance mode, so the platform
# sees a running benchmark that is already in performance mode.
as_root device_config put game_overlay "$PKG" mode=2 >/dev/null 2>&1
as_root cmd game mode performance "$PKG" >/dev/null 2>&1

# ---- service ------------------------------------------------------------
say "starting the performance service"
as_root am start-foreground-service --user 0 -n "$SERVICE" -a "$ACTION" 2>/dev/null \
    || as_root am startservice --user 0 -n "$SERVICE" -a "$ACTION" 2>/dev/null \
    || true

say "done"
say "verify: adb shell dumpsys activity services $PKG | grep -i isForeground"
say "verify: adb shell dumpsys window windows | grep -i antutu"
