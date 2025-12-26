# Building APKs with Product Flavors

This document explains how to build separate ODK Collect and AIIMS ODK Collect APKs using the product flavor system.

## Overview

The Collect app now uses Android Product Flavors to create two distinct applications:

- **ODK Collect**: Standard ODK Collect application (`org.odk.collect.android`)
- **AIIMS ODK Collect**: AIIMS-customized version (`org.aiims.odk.collect`)

Both apps can be installed simultaneously on the same device with completely separate data storage.

## Build Variants

The product flavor system creates the following build variants:

### Debug Builds
- `odkDebug` - Standard ODK Collect (debug)
- `aiimsDebug` - AIIMS ODK Collect (debug)

### Release Builds
- `odkRelease` - Standard ODK Collect (release)
- `aiimsRelease` - AIIMS ODK Collect (release)
- `odkCollectRelease` - Official ODK release variant
- `selfSignedRelease` - Self-signed release variant

## Building APKs

### Build All Flavors

```bash
# Build all debug variants
./gradlew assembleDebug

# Build all release variants
./gradlew assembleRelease

# Build all variants (debug + release)
./gradlew assemble
```

### Build Specific Flavor

#### ODK Collect
```bash
# Debug APK
./gradlew assembleOdkDebug

# Release APK
./gradlew assembleOdkRelease

# All ODK variants
./gradlew assembleOdk
```

#### AIIMS ODK Collect
```bash
# Debug APK
./gradlew assembleAiimsDebug

# Release APK
./gradlew assembleAiimsRelease

# All AIIMS variants
./gradlew assembleAiims
```

## Generated APK Locations

After building, APKs are located in:

```
collect_app/build/outputs/apk/
├── odk/
│   ├── debug/
│   │   └── ODK-Collect-debug.apk
│   └── release/
│       └── ODK-Collect-release.apk
├── aiims/
│   ├── debug/
│   │   └── AIIMS-ODK-Collect-debug.apk
│   └── release/
│       └── AIIMS-ODK-Collect-release.apk
├── debug/
│   └── [legacy build variants]
└── release/
    └── [legacy build variants]
```

## APK Naming Convention

- **ODK Debug**: `ODK-Collect-debug.apk`
- **ODK Release**: `ODK-Collect-release.apk`
- **AIIMS Debug**: `AIIMS-ODK-Collect-debug.apk`
- **AIIMS Release**: `AIIMS-ODK-Collect-release.apk`

## Application IDs

Each flavor has a unique application ID (package name), enabling both apps to be installed simultaneously:

| Flavor | Application ID | App Name |
|--------|---|---|
| ODK | `org.odk.collect.android` | ODK Collect |
| AIIMS | `org.aiims.odk.collect` | AIIMS ODK Collect |

## Data Storage

Each application maintains independent data storage:

- **ODK Collect**: `/data/data/org.odk.collect.android/`
- **AIIMS ODK Collect**: `/data/data/org.aiims.odk.collect/`

This means:
- Separate SQLite databases
- Independent SharedPreferences
- Isolated file storage
- Different cache directories

## Feature Flags

Each flavor has its own feature flag configuration:

### ODK Flavor
```gradle
resValue("bool", "aiims_auth_enabled", "false")
resValue("bool", "odk_launcher_enabled", "true")
```

### AIIMS Flavor
```gradle
resValue("bool", "aiims_auth_enabled", "true")
resValue("bool", "odk_launcher_enabled", "false")
resValue("string", "aiims_default_api_url", "http://localhost:5174/api")
resValue("integer", "aiims_default_offline_period", "7")
resValue("integer", "aiims_default_auto_logout", "30")
```

## Versioning Strategy

### Version Numbers

Both flavors share the same `versionCode` but have distinct `versionName` values to differentiate builds:

| Flavor | Version Name Example |
|--------|---|
| ODK Debug | `v2025.1.0-RC1-DEBUG` |
| ODK Release | `v2025.1.0-RC1` |
| AIIMS Debug | `v2025.1.0-RC1-AIIMS-DEBUG` |
| AIIMS Release | `v2025.1.0-RC1-AIIMS` |

### How It Works

- **Base Version**: Defined in `defaultConfig` block (shared by both flavors)
- **AIIMS Suffix**: Added via `versionNameSuffix "-AIIMS"` in AIIMS flavor
- **Debug Suffix**: Added via `versionNameSuffix "-DEBUG"` in debug build type

Suffixes are concatenated automatically: `baseVersion + flavorSuffix + buildTypeSuffix`

### Updating Versions

To update the app version for both flavors, modify only the `defaultConfig` in `collect_app/build.gradle`:

```gradle
defaultConfig {
    versionCode 5114  // Increment for each release
    versionName "v2025.1.1"  // Update version string
    // ... other config
}
```

The AIIMS and debug suffixes are automatically applied to all variants.

### Benefits

- **Single Source of Truth**: Update version once, all flavors get new version
- **Easy Identification**: AIIMS suffix clearly identifies non-standard builds
- **Crash Reporting**: Firebase/Crashlytics shows distinct versions per flavor
- **Release Management**: Separate version strings for release notes and changelogs

## Build Configuration Files

### Main Configuration
- **File**: `collect_app/build.gradle`
- **Location**: Lines 87-118 (Product Flavors block)

### Google Services Configuration
- **Debug**: `collect_app/src/debug/google-services.json`
- **Release**: `collect_app/src/release/google-services.json`

Both files include client configurations for both `org.odk.collect.android` and `org.aiims.odk.collect`.

### Flavor-Specific Resources
- **ODK**: `collect_app/src/odk/res/values/strings.xml`
- **AIIMS**: `collect_app/src/aiims/res/values/strings.xml`

## Building for Installation

### Install Debug APK on Device

```bash
# ODK Debug
./gradlew :collect_app:installOdkDebug

# AIIMS Debug
./gradlew :collect_app:installAiimsDebug
```

### Install Release APK on Device

```bash
# ODK Release (requires signing configuration)
./gradlew :collect_app:installOdkRelease

# AIIMS Release (requires signing configuration)
./gradlew :collect_app:installAiimsRelease
```

## Testing Both Flavors

To test both applications on the same device:

```bash
# Build and install both debug variants
./gradlew :collect_app:installOdkDebug :collect_app:installAiimsDebug
```

Both apps will appear in the launcher as separate applications:
- "ODK Collect"
- "AIIMS ODK Collect"

Each maintains independent data, settings, and user accounts.

## Verifying Installation

After installation, verify both apps are present:

```bash
# List installed apps for both package names
adb shell pm list packages | grep odk.collect
```

You should see:
- `package:org.odk.collect.android`
- `package:org.aiims.odk.collect`

## Troubleshooting

### Build Fails with "No matching client found"
If you get an error about missing client configuration in `google-services.json`:

**Solution**: Ensure both package names are configured in `google-services.json`:
- `org.odk.collect.android` (for ODK flavor)
- `org.aiims.odk.collect` (for AIIMS flavor)

### APK Installation Fails with "INSTALL_FAILED_DUPLICATE_PACKAGE"
This typically means the app with that package name is already installed. This is expected if you're installing multiple versions of the same flavor.

**Solution**: Uninstall the previous version first:
```bash
adb uninstall org.odk.collect.android
adb uninstall org.aiims.odk.collect
```

### Gradle Sync Issues
If Android Studio doesn't recognize the new flavors:

**Solution**:
1. Click "Sync Now" when prompted
2. Or manually run: `./gradlew clean :collect_app:clean`
3. Rebuild the project

## Gradle Tasks Reference

### Available Tasks
```bash
# Show all available tasks for collect_app
./gradlew :collect_app:tasks

# List only assemble tasks (build APKs)
./gradlew :collect_app:tasks | grep -i assemble
```

### Common Build Commands

| Command | Purpose |
|---------|---------|
| `./gradlew assembleOdkDebug` | Build ODK debug APK |
| `./gradlew assembleAiimsDebug` | Build AIIMS debug APK |
| `./gradlew assembleOdk` | Build all ODK variants |
| `./gradlew assembleAiims` | Build all AIIMS variants |
| `./gradlew clean` | Clean all build artifacts |
| `./gradlew :collect_app:clean` | Clean only collect_app module |

## Architecture Overview

The product flavor system architecture:

```
collect_app/
├── src/
│   ├── main/
│   │   ├── AndroidManifest.xml
│   │   ├── java/
│   │   ├── res/
│   │   └── ... (shared code)
│   ├── odk/
│   │   └── res/
│   │       └── values/
│   │           └── strings.xml (ODK-specific)
│   ├── aiims/
│   │   └── res/
│   │       └── values/
│   │           └── strings.xml (AIIMS-specific)
│   ├── debug/
│   │   └── google-services.json
│   └── release/
│       └── google-services.json
├── build.gradle (flavor definitions)
└── ... (other files)
```

## Integration with AIIMS Module

The `aiims_auth_module` is included in both flavors:

- **ODK flavor**: Compiled in but disabled via `aiims_auth_enabled=false`
- **AIIMS flavor**: Enabled via `aiims_auth_enabled=true`

This modular approach keeps AIIMS-specific code isolated while allowing both apps to share the common codebase.

## References

- Android Product Flavors Documentation: https://developer.android.com/build/build-variants#product-flavors
- Build Variants Guide: https://developer.android.com/build/build-variants

## Support

For issues or questions about building with product flavors, refer to:
1. `collect_app/build.gradle` - Product flavor definitions
2. `collect_app/src/debug/google-services.json` - Firebase configuration
3. `collect_app/src/release/google-services.json` - Firebase configuration
