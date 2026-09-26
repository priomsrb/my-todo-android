#!/usr/bin/env bash
# Build the debug APK from the current working tree and install it onto the
# phone plugged in over USB, keeping the app's data. Then launch it.
#
# Usage: scripts/install-phone.sh [--no-launch]
#
# Targets the USB device only (adb -d), so a running emulator is left alone.
# `./gradlew installDebug` is not used because it installs onto every
# connected device at once.
set -euo pipefail

cd "$(dirname "$0")/.."

PACKAGE="dev.shafqat.mytodo"
APK="app/build/outputs/apk/debug/app-debug.apk"
LAUNCH=1
[[ "${1:-}" == "--no-launch" ]] && LAUNCH=0

if ! command -v adb >/dev/null; then
    ADB_SDK="$HOME/Library/Android/sdk/platform-tools/adb"
    [[ -x "$ADB_SDK" ]] || { echo "adb not found" >&2; exit 1; }
    adb() { "$ADB_SDK" "$@"; }
fi

usb_devices=$(adb devices -l | awk 'NR > 1 && / usb:/ { print $1, $2 }')
if [[ -z "$usb_devices" ]]; then
    echo "No phone found over USB. Plug it in and enable USB debugging." >&2
    exit 1
fi
if [[ $(wc -l <<<"$usb_devices") -gt 1 ]]; then
    echo "More than one USB device connected:" >&2
    echo "$usb_devices" >&2
    exit 1
fi
read -r serial state <<<"$usb_devices"
if [[ "$state" != "device" ]]; then
    echo "Phone $serial is '$state' — accept the USB debugging prompt on the phone." >&2
    exit 1
fi

model=$(adb -s "$serial" shell getprop ro.product.model | tr -d '\r')
echo "==> Building debug APK ($(git rev-parse --short HEAD)$(git diff --quiet HEAD || echo '+dirty'))"
./gradlew :app:assembleDebug

echo "==> Installing onto $model ($serial)"
# -r keeps the app's data; the debug keystore is per-machine, so an APK
# signed elsewhere will fail with INSTALL_FAILED_UPDATE_INCOMPATIBLE.
adb -s "$serial" install -r "$APK"

if [[ $LAUNCH -eq 1 ]]; then
    echo "==> Launching"
    adb -s "$serial" shell am force-stop "$PACKAGE"
    adb -s "$serial" shell am start -n "$PACKAGE/.MainActivity" >/dev/null
fi

echo "==> Done"
