#!/usr/bin/env bash
#
# setup-hyperos.sh
#
# Applies every HyperOS / MIUI exemption that Performance Spoof needs in order
# to stay alive, and re-applies the ADB shell identity that a root or Shizuku
# session would otherwise provide.
#
# Usage:
#   adb push setup-hyperos.sh /data/local/tmp/
#   adb shell sh /data/local/tmp/setup-hyperos.sh
#
# Run it again whenever HyperOS resets the settings (a reboot, a factory
# reset, or a system update can restore the defaults).

set -u

PKG="com.antutu.ABenchMark"
SERVICE="com.antutu.ABenchMark/.PerformanceService"
ACTION="com.antutu.ABenchMark.ACTION_START"

say()  { echo "[setup] $*"; }
run()  { "$@" 2>&1 | sed 's/^/[setup]   /'; }

# Prefer root so the settings database writes succeed. Fall back to the plain
# shell identity, which is still enough for the AppOps and doze whitelist.
if command -v su >/dev/null 2>&1 && su -c id >/dev/null 2>&1; then
    say "root available, running privileged"
    as_root() { su -c "$*"; }
else
    say "no root, running as shell (AppOps and doze whitelist only)"
    as_root() { "$@"; }
fi

say "appops: background execution and foreground start"
as_root cmd appops set "$PKG" RUN_ANY_IN_BACKGROUND allow
as_root cmd appops set "$PKG" RUN_IN_BACKGROUND allow
as_root cmd appops set "$PKG" START_FOREGROUND allow
as_root cmd appops set "$PKG" WAKE_LOCK allow
as_root cmd appops set "$PKG" SYSTEM_ALERT_WINDOW allow

say "doze whitelist"
as_root dumpsys deviceidle whitelist "+$PKG"
as_root cmd deviceidle except-idle-whitelist "+$PKG"

say "standby bucket: active"
as_root am set-standby-bucket "$PKG" active

say "uninstall / disable state"
as_root cmd package unstop --user 0 "$PKG" 2>/dev/null || true

if command -v su >/dev/null 2>&1 && su -c id >/dev/null 2>&1; then
    say "hyperos freeze switches"
    as_root settings put global cached_apps_freezer disabled
    as_root settings put system cloud_greezer_enable false
    as_root settings put system cloud_memFreeze_control 0

    say "xiaomi cloud whitelists"
    for wl in cloud_memory_freeze_whitelist cloud_lowlatency_whitelist cloud_network_priority_whitelist; do
        current=$(as_root settings get system "$wl" 2>/dev/null | tr -d '\r' | head -n1)
        [ "$current" = "null" ] && current=""
        case ",$current," in
            *",$PKG,"*) ;;
            *)
                if [ -z "$current" ]; then
                    as_root settings put system "$wl" "$PKG"
                else
                    as_root settings put system "$wl" "$current,$PKG"
                fi
                ;;
        esac
    done
fi

say "starting the performance service"
as_root am start-foreground-service --user 0 -n "$SERVICE" -a "$ACTION" 2>/dev/null \
    || as_root am startservice --user 0 -n "$SERVICE" -a "$ACTION" 2>/dev/null \
    || true

say "done. verify with: adb shell dumpsys activity services $PKG"
