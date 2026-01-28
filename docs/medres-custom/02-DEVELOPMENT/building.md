# Building MEDRES ODK Collect APK
> Last Updated: 2025-01-28
> Reviewed At: 2025-01-28

This document explains how to build the MEDRES ODK Collect APK across three different workflows.

## Overview

This fork (`vg-work` branch) is specifically for **MEDRES ODK Collect**. Core ODK files have been modified for MEDRES functionality, so this codebase only produces MEDRES-flavored builds.

> [!IMPORTANT]
> **For vanilla ODK Collect**, checkout the `main` branch and build using standard ODK instructions.
> This fork's modified codebase cannot produce a true vanilla ODK build.

## Build Workflows Summary

| Workflow | Use Case | LeakCanary | Signing | APK Size | Performance |
|----------|----------|------------|---------|----------|-------------|
| **Debug** | Development & debugging | ✅ Included | Debug keystore | Largest | Slowest |
| **Self-Signed Release** | Testing release builds | ✅ Included | Debug keystore | Medium | Optimized |
| **Production Release** | App store distribution | ❌ Excluded | Release keystore | Smallest | Fastest |

---

## Workflow 1: Debug Build

### Purpose
- **Development and debugging**
- Fast iteration during coding
- Full debugging tools and LeakCanary memory leak detection
- Not suitable for distribution

### Characteristics
- ✅ **LeakCanary included** - Memory leak detection and notifications
- ✅ **Debuggable** - Full debugger access
- ✅ **Logging enabled** - Verbose log output
- ❌ **Not minified** - Larger APK size
- ❌ **Not optimized** - Slower performance
- ✅ **Debug keystore** - Uses Android debug certificate

### Build Command

```bash
./gradlew assembleMedresDebug
```

### Install Command

```bash
./gradlew :collect_app:installMedresDebug
# OR
./gradlew installMedresDebug
```

### Output Location
```
collect_app/build/outputs/apk/medres/debug/
└── MEDRES-ODK-Collect-debug.apk
```

### Application ID
```
edu.aiims.medresodk
```

### Version Name Example
```
v2025.3.3-MEDRES-RC2-DEBUG
```

### When to Use
- ✅ Daily development
- ✅ Debugging issues
- ✅ Testing memory leaks
- ✅ Quick iterations
- ❌ NOT for user testing
- ❌ NOT for production

---

## Workflow 2: Self-Signed Release

### Purpose
- **Testing release builds** before production
- Validating ProGuard/R8 minification
- Testing with near-production performance
- Internal testing without certificate setup

### Characteristics
- ✅ **LeakCanary included** - Memory leak detection still available
- ❌ **Not debuggable** - Debugger attachment limited
- ⚠️ **Logging limited** - Reduced log output
- ✅ **Minified & Optimized** - Smaller APK, better performance
- ✅ **Debug keystore** - Uses Android debug certificate (same as debug build)
- ⚠️ **R8/ProGuard enabled** - Code obfuscation active

### Build Command

```bash
./gradlew assembleMedresSelfSignedRelease
```

### Install Command

```bash
./gradlew :collect_app:installMedresSelfSignedRelease
# OR install pre-built APK
adb install collect_app/build/outputs/apk/medres/selfSignedRelease/MEDRES-ODK-Collect-v*.apk
```

### Output Location
```
collect_app/build/outputs/apk/medres/selfSignedRelease/
└── MEDRES-ODK-Collect-v2025.3.3-MEDRES-RC2.apk
```

### Application ID
```
edu.aiims.medresodk
```

### Version Name Example
```
v2025.3.3-MEDRES-RC2
```

### Why LeakCanary is Included

From `collect_app/build.gradle`:
```gradle
// Real LeakCanary for debug and selfSigned builds only: notifications, analysis, etc
debugImplementation libs.leakcanary
selfSignedReleaseImplementation libs.leakcanary
```

This allows testing release builds (with minification) while still having memory leak detection.

### When to Use
- ✅ Pre-production testing
- ✅ Validating minification/shrinking
- ✅ Internal dogfooding
- ✅ Performance testing
- ✅ Memory leak testing in release-like environment
- ❌ NOT for production distribution
- ❌ NOT for app store submission

---

## Workflow 3: Production Release

### Purpose
- **App store distribution** (Google Play, internal app stores)
- Production deployment
- Public release

### Characteristics
- ❌ **No LeakCanary** - Smaller size, no memory leak overhead
- ❌ **Not debuggable** - No debugger access
- ❌ **Logging minimal** - Production-safe logging only
- ✅ **Fully minified** - Smallest APK size
- ✅ **Fully optimized** - Best performance
- ✅ **Release keystore** - Requires proper signing certificate
- ✅ **R8/ProGuard full mode** - Maximum code obfuscation

### Prerequisites

Before building, you must:

1. **Generate a keystore** (one-time setup):
   ```bash
   keytool -genkey -v -keystore my-release-key.jks \
       -keyalg RSA -keysize 2048 -validity 10000 \
       -alias my-alias
   ```

2. **Create `secrets.properties`** in project root:
   ```properties
   RELEASE_STORE_FILE=my-release-key.jks
   RELEASE_STORE_PASSWORD=your_keystore_password
   RELEASE_KEY_ALIAS=your_key_alias
   RELEASE_KEY_PASSWORD=your_key_password
   ```

3. **Place the keystore file** in the project root (or update path in properties)

> [!WARNING]
> Keep your keystore file and passwords safe. If you lose them, you cannot update your app on the Play Store.

### Build Command

```bash
./gradlew assembleMedresRelease
```

### Install Command

```bash
./gradlew :collect_app:installMedresRelease
# OR install pre-built APK
adb install collect_app/build/outputs/apk/medres/release/MEDRES-ODK-Collect-v*.apk
```

### Output Location
```
collect_app/build/outputs/apk/medres/release/
└── MEDRES-ODK-Collect-release.apk
```

### Application ID
```
edu.aiims.medresodk
```

### Version Name Example
```
v2025.3.3-MEDRES-RC2
```

### When to Use
- ✅ Production deployment
- ✅ App store submission
- ✅ Public distribution
- ✅ Final QA before release
- ❌ NOT for development (use debug build instead)

### Detailed Instructions

See **[Release Signing Guide](../04-OPERATIONS/release-signing.md)** for complete setup instructions.

---

## Comparison Table

| Feature | Debug | Self-Signed Release | Production Release |
|---------|-------|-------------------|-------------------|
| **LeakCanary** | ✅ | ✅ | ❌ |
| **Debugger** | ✅ Full | ⚠️ Limited | ❌ None |
| **Logging** | ✅ Verbose | ⚠️ Limited | ⚠️ Minimal |
| **Minification** | ❌ | ✅ R8 | ✅ R8 Full |
| **Optimization** | ❌ | ✅ | ✅ Maximum |
| **Code Obfuscation** | ❌ | ✅ | ✅ Full |
| **APK Size** | Largest | Medium | Smallest |
| **Performance** | Slowest | Fast | Fastest |
| **Signing** | Debug keystore | Debug keystore | Release keystore |
| **Build Time** | Fastest | Medium | Medium |
| **Use Case** | Development | Pre-release testing | Production |

---

## Build All Variants

```bash
# All debug variants
./gradlew assembleDebug

# All release variants (requires signing config)
./gradlew assembleRelease

# All variants
./gradlew assemble
```

---

## Common Commands Reference

| Command | Purpose | Workflow |
|---------|---------|----------|
| `./gradlew assembleMedresDebug` | Build debug APK | Debug |
| `./gradlew assembleMedresSelfSignedRelease` | Build self-signed release | Self-Signed |
| `./gradlew assembleMedresRelease` | Build production release | Production |
| `./gradlew installMedresDebug` | Install debug to device | Debug |
| `./gradlew installMedresSelfSignedRelease` | Install self-signed to device | Self-Signed |
| `./gradlew installMedresRelease` | Install production to device | Production |
| `./gradlew clean` | Clean all build artifacts | All |
| `./gradlew :collect_app:tasks` | List available tasks | All |

---

## Troubleshooting

### "INSTALL_PARSE_FAILED_NO_CERTIFICATES"

**Problem:** Production release build fails to install
```
adb: failed to install ...: Failure [INSTALL_PARSE_FAILED_NO_CERTIFICATES]
```

**Cause:** APK not signed properly

**Solution:** Use `selfSignedRelease` for testing, or set up proper release signing (see [Release Signing Guide](../04-OPERATIONS/release-signing.md))

### Release build shows "Release_STORE_FILE not found"

**Problem:** Missing signing configuration

**Solution:** Create `secrets.properties` file with signing credentials (see Workflow 3 above)

### LeakCanary appears when I don't want it

**Problem:** LeakCanary showing in production

**Cause:** Using `debug` or `selfSignedRelease` build instead of production release

**Solution:** Use `assembleMedresRelease` with proper signing configured

### Build fails with "No matching client found"

**Solution:** Ensure `edu.aiims.medresodk` is configured in `google-services.json`

### Want to test minification without certificates

**Solution:** Use `assembleMedresSelfSignedRelease` - it includes minification and LeakCanary

---

## Gradle Tasks Reference

### Show All Available Tasks
```bash
./gradlew :collect_app:tasks
```

### List Only Assemble Tasks
```bash
./gradlew :collect_app:tasks | grep -i assemble
```

---

## Related Documentation

- [Release Signing Guide](../04-OPERATIONS/release-signing.md) - Setting up production certificates
- [Versioning](../04-OPERATIONS/versioning.md) - Version strategy
- [Quick Start](../01-QUICKSTART.md) - Getting started
- [Local Setup](setup.md) - Development environment
- [Debugging](debugging.md) - Debugging tools

---

## Branch Strategy

| Branch | Purpose | Build Command |
|--------|---------|---------------|
| `main` | Vanilla ODK Collect (upstream sync) | `./gradlew assembleDebug` |
| `vg-work` | MEDRES ODK Collect | `./gradlew assembleMedresDebug` |

> [!NOTE]
> This documentation is for the `vg-work` branch (MEDRES builds only).
