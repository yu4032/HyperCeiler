#!/bin/bash
# Build and install DockCustomizer
set -e
cd "$(dirname "$0")"

export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
export ANDROID_HOME="$HOME/Android"

echo "Building..."
~/gradle/gradle-9.6.1/bin/gradle :betterdock:assembleRelease --no-daemon -q

echo "Signing..."
APK=betterdock/build/outputs/apk/release/betterdock-release-unsigned.apk
SIGNED=/tmp/betterdock-signed.apk

if [ ! -f /tmp/betterdock-debug.keystore ]; then
    keytool -genkey -v -keystore /tmp/betterdock-debug.keystore \
      -alias betterdock -keyalg RSA -keysize 2048 -validity 10000 \
      -storepass android -keypass android \
      -dname "CN=DockCustomizer" > /dev/null 2>&1
fi

$ANDROID_HOME/build-tools/37.0.0/apksigner sign \
  --ks /tmp/betterdock-debug.keystore \
  --ks-pass pass:android --ks-key-alias betterdock --key-pass pass:android \
  --out $SIGNED $APK 2>/dev/null

echo "Installing..."
$ANDROID_HOME/platform-tools/adb install -r $SIGNED
$ANDROID_HOME/platform-tools/adb shell am force-stop com.miui.home
sleep 2
$ANDROID_HOME/platform-tools/adb shell am start -n com.miui.home/.launcher.Launcher
echo "Done!"
