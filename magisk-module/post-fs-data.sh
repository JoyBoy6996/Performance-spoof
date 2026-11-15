#!/system/bin/sh
# Runs before the framework is up. Turns the HyperOS freeze switches off so the
# performance overlay surface is never destroyed by the memory freezer.
MODDIR=${0%/*}

settings put global cached_apps_freezer disabled
settings put system cloud_greezer_enable false
settings put system cloud_memFreeze_control 0
