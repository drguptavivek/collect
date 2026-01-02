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

## Build & Install (MEDRES ODK Collect)

> **Note:** This fork only builds MEDRES flavor. For vanilla ODK, use `main` branch.

```bash
# Build and install debug APK
./gradlew :collect_app:installMedresDebug

# Build debug APK only (no install)
./gradlew assembleMedresDebug

# Location of generated APK:
# collect_app/build/outputs/apk/medres/debug/MEDRES-ODK-Collect-debug.apk
```

## Release Build

```bash
# Build MEDRES release APK
./gradlew assembleMedresRelease

# Build all release variants
./gradlew clean assembleRelease

# Location of generated APK:
# collect_app/build/outputs/apk/medres/release/MEDRES-ODK-Collect-release.apk
```

## Useful ADB Commands

```bash
# Install APK manually
adb install -r collect_app/build/outputs/apk/medres/debug/MEDRES-ODK-Collect-debug.apk

# Uninstall app
adb uninstall org.medres.odk.collect

# Check if MEDRES app is installed
adb shell pm list packages | grep medres.odk.collect

# View logs (MEDRES app only)
adb logcat | grep org.medres.odk.collect

# Clear app data
adb shell pm clear org.medres.odk.collect
```

## Clean Build

```bash
# Clean all build artifacts
./gradlew clean

# Clean only collect_app module
./gradlew :collect_app:clean

# Full clean rebuild
./gradlew clean assembleMedresDebug
```

## Emulator Management

```bash

```
