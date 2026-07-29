#!/bin/bash
cd /home/zhaoyu/HyperCeiler
./gradlew :smartdock:assembleRelease 2>&1 | tail -3
APK=$(find smartdock/build -name "*.apk" | head -1)
if [ -f "$APK" ]; then
  adb install -r "$APK" 2>&1
  adb shell am force-stop com.miui.personalassistant
  adb shell am start -n com.miui.personalassistant/.Launcher
  echo "Done!"
fi
