# AIIMS ODK Collect - Quick Start Guide

> Get up and running with AIIMS ODK Collect development

---

## Prerequisites

- **JDK 17** or higher
- **Android Studio** Hedgehog (2023.1.1) or newer
- **Android SDK** API 36 (Android 14)
- **Gradle 8.x** (included with project)

---

## 1. Clone the Repository

```bash
git clone https://github.com/drguptavivek/collect.git
cd collect
git checkout vg-work
```

---

## 2. Initial Setup

### Add Upstream Remote (for future syncing)

```bash
git remote add upstream https://github.com/getodk/collect.git
git fetch upstream
```

### Verify Branch

```bash
git branch -v
# Should show: * vg-work
```

---

## 3. Build the App

### Build Debug APK

```bash
./gradlew assembleAiimsDebug
```

### Build and Install on Emulator/Device

```bash
./gradlew :collect_app:installAiimsDebug
```

### Output Location

```
collect_app/build/outputs/apk/aiims/debug/AIIMS-ODK-Collect-debug.apk
```

---

## 4. Local Development Setup

### Start Android Emulator

```bash
emulator -avd <your_avd_name> -netdelay none -netspeed full &
```

Or use Android Studio's Device Manager.

### Verify Device Connection

```bash
adb devices
```

### Backend Configuration (for local dev)

The app automatically maps `central.local` → `10.0.2.2` in debug builds.

**In the App:**
1. Launch AIIMS ODK Collect
2. Tap the gear icon (Settings) on the login screen
3. Configure:
   - **Base URL**: `https://central.local`
   - **Project ID**: `1` (or your target project)

### SSL/HTTPS

Debug builds trust all certificates automatically. No certificate installation needed.

---

## 5. Running the App

### From Android Studio

1. Open the project in Android Studio
2. Wait for Gradle sync to complete
3. Select `aiimsDebug` build variant
4. Click Run button or press `Shift+F10`

### From Command Line

```bash
./gradlew :collect_app:installAiimsDebug
adb shell am start -n org.aiims.odk.collect/.activities.FirstLaunchActivity
```

---

## 6. First Run - Authentication Flow

### Step 1: Initial Login

1. App launches to **AiimsLoginActivity**
2. Enter credentials (e.g., username/password)
3. Tap **Login**

### Step 2: PIN Setup

1. On successful login, you're redirected to **SetupPinActivity**
2. Enter a 4-digit PIN
3. Confirm the PIN
4. PIN is now required on app resume

### Step 3: Main Menu

1. After PIN setup, you reach the **Main Menu**
2. Download forms via **Fill Blank Form**
3. Fill forms and submit via **Finalize**

---

## 7. Viewing Auth Token (Debug)

To verify your authentication:

1. Go to **Main Menu**
2. Tap the **Auth Settings** icon (key/prefs icon in toolbar)
3. View your current **Bearer Token**, expiry time, and user details

---

## 8. Common Development Tasks

### Clean Build

```bash
./gradlew clean
```

### View Logs

```bash
adb logcat | grep -E "AiimsAuth|AiimsAppLock|OkHttp"
```

### Clear App Data

```bash
adb shell pm clear org.aiims.odk.collect
```

### Uninstall App

```bash
adb uninstall org.aiims.odk.collect
```

---

## 9. Project Structure Overview

```
collect/
├── aiims_auth_module/          # Custom authentication module
│   └── src/main/java/org/aiims/odk/auth/
│       ├── activities/         # Login, PIN, Settings screens
│       ├── managers/           # AuthManager, PinManager
│       ├── api/                # Retrofit API services
│       └── utils/              # Helpers (AppLock, telemetry)
│
├── collect_app/                # Main application module
│   └── src/main/
│       ├── java/org/odk/collect/android/
│       │   └── injections/     # Dependency injection setup
│       └── res/                # Shared resources
│
└── open-rosa/                  # Networking layer (modified)
    └── OkHttpOpenRosaServerClientProvider.java  # Token injection
```

---

## 10. Feature Flags

The AIIMS customization is controlled by these flags (in `AndroidManifest.xml`):

| Flag | Default | Description |
|------|---------|-------------|
| `aiims_auth_enabled` | `true` | Enables AIIMS auth module |
| `odk_launcher_enabled` | `false` | Disables standard ODK launcher |

---

## 11. Key Differences from Vanilla ODK

| Feature | Vanilla ODK | AIIMS Fork |
|---------|-------------|------------|
| Authentication | Basic Auth (username/password) | Bearer Token (short-lived JWT) |
| Session | Persistent until logout | 3-day expiry with 6h grace |
| Security | Optional admin password | Mandatory 4-digit PIN |
| QR Code | Bypasses login | Configures project, requires login |
| Data Isolation | None | Forms wiped on logout |
| Local Dev | Manual configuration | Automatic DNS mapping |

---

## 12. Troubleshooting

### Build Fails: "No matching client"

**Cause**: Missing Firebase configuration
**Solution**: Ensure `google-services.json` exists for AIIMS package

### Login Crash in Release Build

**Cause**: ProGuard removed API model classes
**Solution**: Check `collect_app/proguard-rules.pro` has keep rules for `org.aiims.odk.auth.api.**`

### Connection Refused to central.local

**Cause**: Emulator networking issue
**Solution**: Ensure debug build is installed (automatic DNS mapping only in debug)

### Forms Still Visible After Logout

**Cause**: `ProjectCleaner` not triggered or UUID mismatch
**Solution**: Check logs for `AiimsAuthManager` cleanup events

---

## 13. Next Steps

- **Architecture**: [01-ARCHITECTURE/overview.md](01-ARCHITECTURE/overview.md)
- **Building**: [02-DEVELOPMENT/building.md](02-DEVELOPMENT/building.md)
- **API Reference**: [03-API/reference.md](03-API/reference.md)
- **Maintenance**: [04-OPERATIONS/maintenance.md](04-OPERATIONS/maintenance.md)

---

## 14. Getting Help

| Issue Type | Reference |
|------------|-----------|
| Authentication flows | [01-ARCHITECTURE/authentication.md](01-ARCHITECTURE/authentication.md) |
| PIN security issues | [05-FEATURES/pin-security.md](05-FEATURES/pin-security.md) |
| QR configuration | [05-FEATURES/qr-workflow.md](05-FEATURES/qr-workflow.md) |
| Build/Gradle issues | [02-DEVELOPMENT/building.md](02-DEVELOPMENT/building.md) |

---

## 15. Version Information

Current versions (check `collect_app/build.gradle` for latest):

| Component | Version |
|-----------|---------|
| versionCode | `5113` |
| versionName | `v2025.1.0-RC1` |
| AIIMS Debug suffix | `-AIIMS-DEBUG` |
| AIIMS Release suffix | `-AIIMS` |

See [04-OPERATIONS/versioning.md](04-OPERATIONS/versioning.md) for versioning strategy.
