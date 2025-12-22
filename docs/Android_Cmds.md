
emulator -list-avds
emulator -avd Medium_Phone_API_36.0 -netdelay none -netspeed full &

adb devices


## DEBUG Build
./gradlew installDebug
adb install collect_app/build/outputs/apk/debug/ODK-Collect-debug.apk 

## Release Build
./gradlew clean assembleSelfSignedRelease
