#!/system/bin/sh
# Runs before the framework is up.
#
# On HyperOS the memory freezer destroys the overlay surface of a background
# app, which silently breaks the spoof, so the freeze switches are turned off
# before anything else starts.

if [ -n "$(getprop ro.miui.ui.version.name)" ] || [ -n "$(getprop ro.miui.ui.version.code)" ]; then
    settings put global cached_apps_freezer disabled
    settings put system cloud_greezer_enable false
    settings put system cloud_memFreeze_control 0
fi
