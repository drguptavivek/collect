# Local Development Setup - AIIMS ODK Collect

> Last Updated: 2025-12-28

This guide covers setting up a local development environment for AIIMS ODK Collect.

---

## Prerequisites

| Requirement | Version | Purpose |
|-------------|---------|---------|
| JDK | 17+ | Java development |
| Android Studio | Hedgehog (2023.1.1) or newer | IDE and emulator |
| Android SDK | API 36 (Android 14) | Target platform |
| Gradle | 8.x (included) | Build system |

---

## 1. Clone and Configure

### Clone Repository

```bash
git clone https://github.com/drguptavivek/collect.git
cd collect
git checkout vg-work
```

### Add Upstream Remote

```bash
git remote add upstream https://github.com/getodk/collect.git
git fetch upstream
```

---

## 2. Backend Connectivity

### The Problem

Your local Central backend runs on `localhost`, but the Android Emulator sees `localhost` as itself (the emulator), not your development machine.

### The Solution

In **debug builds only**, the app automatically maps `central.local` → `10.0.2.2` (the special host that points to your development machine from within the emulator).

#### Implementation

Located in `open-rosa/.../OkHttpOpenRosaServerClientProvider.java`:

```java
if (isDebug) {
    Dns dns = hostname -> {
        if ("central.local".equals(hostname)) {
            return Arrays.asList(InetAddress.getByName("10.0.2.2"));
        }
        return Dns.SYSTEM.lookup(hostname);
    };
    // Use custom DNS for OkHttp client
}
```

---

## 3. SSL/HTTPS Configuration

### Debug Builds

Debug builds use an `UnsafeTrustManager` that trusts all certificates:

```java
if (isDebug) {
    TrustManager[] trustAllCerts = new TrustManager[]{
        new X509TrustManager() {
            public void checkClientTrusted(X509Certificate[] chain, String authType) {}
            public void checkServerTrusted(X509Certificate[] chain, String authType) {}
            public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[]{}; }
        }
    };
}
```

**No certificate installation needed** for local development.

### Release Builds

Release builds enforce strict SSL validation. Ensure your production server has valid certificates.

---

## 4. Configure Local Backend

### Start Central Backend

Ensure your local ODK Central backend is running and accessible at `https://central.local`.

### In-App Configuration

1. Launch AIIMS ODK Collect on the emulator
2. Tap the **gear icon** (⚙️) on the login screen
3. Configure:
   - **Base URL**: `https://central.local`
   - **Project ID**: `1` (or your target project)

### QR Code Alternative

You can also configure via QR code:

```json
{
  "general": {
    "server_url": "https://central.local/v1/projects/1",
    "form_update_mode": "match_exactly"
  },
  "project": {
    "name": "Local Dev Project",
    "project_id": "1"
  }
}
```

---

## 5. Emulator Setup

### Start Emulator

```bash
# List available AVDs
emulator -list-avds

# Start emulator in background
emulator -avd <your_avd_name> -netdelay none -netspeed full &
```

Or use Android Studio's **Device Manager**.

### Verify Connection

```bash
adb devices
# Should list your emulator

# Test ping from emulator
adb shell ping 10.0.2.2
# Should reach your development machine
```

---

## 6. Install and Launch

### Build and Install

```bash
./gradlew :collect_app:installAiimsDebug
```

### Launch from Command Line

```bash
adb shell am start -n org.aiims.odk.collect/.activities.FirstLaunchActivity
```

### Launch from Android Studio

1. Select **aiimsDebug** build variant
2. Click **Run** button or press `Shift+F10`

---

## 7. Feature Flags

The AIIMS customization is controlled by these flags in `AndroidManifest.xml`:

| Flag | Default | Description |
|------|---------|-------------|
| `aiims_auth_enabled` | `true` | Enables AIIMS auth module |
| `odk_launcher_enabled` | `false` | Disables standard ODK launcher |

To temporarily disable AIIMS auth (for testing vanilla ODK behavior):

```xml
<meta-data
    android:name="aiims_auth_enabled"
    android:value="false" />
```

---

## 8. Default Configuration

### Debug Build Defaults

Located in `collect_app/build.gradle` under the `aiims` flavor:

```gradle
resValue("bool", "aiims_auth_enabled", "true")
resValue("bool", "odk_launcher_enabled", "false")
resValue("string", "aiims_default_api_url", "http://localhost:5174/api")
resValue("integer", "aiims_default_offline_period", "7")
resValue("integer", "aiims_default_auto_logout", "30")
```

### Environment Variables

You can override defaults via `gradle.properties`:

```properties
AIIMS_DEFAULT_API_URL=http://localhost:5174/api
AIIMS_DEFAULT_OFFLINE_PERIOD=7
AIIMS_DEFAULT_AUTO_LOGOUT=30
```

---

## 9. Firebase Configuration

### Debug Configuration

File: `collect_app/src/debug/google-services.json`

Must contain configuration for `org.aiims.odk.collect`.

### Release Configuration

File: `collect_app/src/release/google-services.json`

Same package, different Firebase project configuration typically.

---

## 10. Debugging Tools

### Viewing Auth Token

1. Go to **Main Menu**
2. Tap **Auth Settings** (key icon in toolbar)
3. View current **Bearer Token** and expiry time

### Logcat Filtering

```bash
# AIIMS-specific logs
adb logcat | grep -E "AiimsAuth|AiimsAppLock|AiimsPin"

# Network requests
adb logcat | grep OkHttp

# All AIIMS tags
adb logcat *:S | grep "aiims"
```

### Inspecting Preferences

```bash
# List shared prefs
adb shell run-as org.aiims.odk.collect ls /data/data/org.aiims.odk.collect/shared_prefs/

# View AIIMS auth prefs
adb shell run-as org.aiims.odk.collect cat /data/data/org.aiims.odk.collect/shared_prefs/aiims_auth_prefs.xml
```

---

## 11. Troubleshooting

### Issue: Connection Refused to central.local

**Cause**: Debug build not installed or DNS mapping not active

**Solution**:
- Verify you're using `aiimsDebug` variant
- Check `OkHttpOpenRosaServerClientProvider` has DNS code

### Issue: SSL Handshake Failed

**Cause**: Using release build with self-signed cert

**Solution**:
- Switch to debug build (trusts all certs)
- Or add cert to device for release build

### Issue: 401 Unauthorized on Form List

**Cause**: Token not being injected

**Solution**:
- Check `TokenProvider` implementation in `AppDependencyModule`
- Verify `auth_token_*` key exists in `aiims_auth_prefs`

### Issue: Forms Persist After Logout

**Cause**: `ProjectCleaner` not triggered or UUID mismatch

**Solution**:
- Check logs for `AiimsAuthManager` cleanup events
- Verify ODK UUID resolution in `ProjectCleaner`

---

## 12. Development Workflow

### Typical Development Cycle

```bash
# 1. Make code changes
# Edit files in aiims_auth_module/ or collect_app/

# 2. Clean build (if needed)
./gradlew :collect_app:clean

# 3. Build and install
./gradlew :collect_app:installAiimsDebug

# 4. View logs
adb logcat | grep -E "AiimsAuth|OkHttp"

# 5. Test feature
# Launch app and test

# 6. Clear data (if needed)
adb shell pm clear org.aiims.odk.collect
```

### Hot Reload (Limited)

Android Studio supports some hot reload for Kotlin/Java:

- **Apply Code Changes**: For small code changes
- **Apply Changes and Restart Activity**: For larger changes

For authentication flows, a full app restart is usually required.

---

## 13. IDE Configuration

### Android Studio Settings

**Recommended**:
- Enable **Auto-Import**
- Enable **Show line numbers**
- Set **Code style** to Kotlin (for `aiims_auth_module`)
- Install **Kotlin** plugin

### Lint

```bash
./gradlew lintAiimsDebug
```

---

## 14. Related Documentation

- [Building the App](building.md) - Complete build instructions
- [Debugging Tips](debugging.md) - Debugging tools and techniques
- [Architecture Overview](../01-ARCHITECTURE/overview.md) - System architecture
- [Quick Start](../01-QUICKSTART.md) - Getting started guide
