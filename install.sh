#!/usr/bin/env bash
# Build and install NovelChat on the first connected Android device.

set -euo pipefail

ANDROID_SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
APK_PATH="app/build/outputs/apk/debug/app-debug.apk"

if [ -z "$ANDROID_SDK" ] || [ ! -x "$ANDROID_SDK/platform-tools/adb" ]; then
    echo "Set ANDROID_HOME or ANDROID_SDK_ROOT to an Android SDK containing platform-tools/adb."
    exit 1
fi

echo "=== 编译 NovelChat ==="
cd "$(dirname "$0")"
./gradlew assembleDebug

echo ""
echo "=== 安装到设备 ==="
if [ -f "$APK_PATH" ]; then
    "$ANDROID_SDK/platform-tools/adb" install -r "$APK_PATH"
    echo "=== 安装成功! ==="
else
    echo "APK not found at $APK_PATH"
    exit 1
fi
