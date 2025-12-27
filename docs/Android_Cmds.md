
emulator -list-avds
emulator -avd Medium_Phone_API_36.0 -netdelay none -netspeed full &

adb devices

## Build & Install (AIIMS Flavor)
# Builds and installs 'org.aiims.odk.collect' (Debug variant)
./gradlew :collect_app:installAiimsDebug

# Location of generated APK:
# collect_app/build/outputs/apk/aiims/debug/AIIMS-ODK-Collect-debug.apk

## Build & Install (ODK Flavor)
# Builds and installs 'org.odk.collect.android' (Debug variant)
./gradlew :collect_app:installOdkDebug

# Location of generated APK:
# collect_app/build/outputs/apk/odk/debug/ODK-Collect-debug.apk

## Release Build
# Builds all release variants (ODK, AIIMS, Self-Signed)
./gradlew clean assembleRelease

# Build specific release flavor
./gradlew assembleAiimsRelease
./gradlew assembleOdkRelease
