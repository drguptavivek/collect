# Android Development Commands

## Emulator

```bash
# List available emulators
emulator -list-avds

# Start emulator in background
emulator -avd Medium_Phone_API_36.0 -netdelay none -netspeed full &

# Clean start (wipes data/factory reset)
emulator -avd Medium_Phone_API_36.0 -netdelay none -netspeed full -wipe-data &

# Check connected devices
adb devices

# Stop specific emulator
adb -s emulator-5554 emu kill

# Kill all emulators
adb devices | grep emulator | cut -f1 | xargs -I {} adb -s {} emu kill

```

## Build & Install (AIIMS ODK Collect)

> **Note:** This fork only builds AIIMS flavor. For vanilla ODK, use `main` branch.

```bash
# Build and install debug APK
./gradlew :collect_app:installAiimsDebug

# Build debug APK only (no install)
./gradlew assembleAiimsDebug

# Location of generated APK:
# collect_app/build/outputs/apk/aiims/debug/AIIMS-ODK-Collect-debug.apk
```

## Release Build

```bash
# Build AIIMS release APK
./gradlew assembleAiimsRelease

# Build all release variants
./gradlew clean assembleRelease

# Location of generated APK:
# collect_app/build/outputs/apk/aiims/release/AIIMS-ODK-Collect-release.apk
```

## Useful ADB Commands

```bash
# Install APK manually
adb install -r collect_app/build/outputs/apk/aiims/debug/AIIMS-ODK-Collect-debug.apk

# Uninstall app
adb uninstall org.aiims.odk.collect

# Check if AIIMS app is installed
adb shell pm list packages | grep aiims.odk.collect

# View logs (AIIMS app only)
adb logcat | grep org.aiims.odk.collect

# Clear app data
adb shell pm clear org.aiims.odk.collect
```

## Clean Build

```bash
# Clean all build artifacts
./gradlew clean

# Clean only collect_app module
./gradlew :collect_app:clean

# Full clean rebuild
./gradlew clean assembleAiimsDebug
```

## Emulator Management

```bash

```
