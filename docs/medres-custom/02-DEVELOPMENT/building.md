# Building MEDRES ODK Collect APK
> Last Updated: 2025-12-28
> Reviewed At: 2025-12-28

This document explains how to build the MEDRES ODK Collect APK.

## Overview

This fork (`vg-work` branch) is specifically for **MEDRES ODK Collect**. Core ODK files have been modified for MEDRES functionality, so this codebase only produces MEDRES-flavored builds.

> [!IMPORTANT]
> **For vanilla ODK Collect**, checkout the `main` branch and build using standard ODK instructions.
> This fork's modified codebase cannot produce a true vanilla ODK build.

## Branch Strategy

| Branch | Purpose | Build Command |
|--------|---------|---------------|
| `main` | Vanilla ODK Collect (upstream sync) | `./gradlew assembleDebug` |
| `vg-work` | MEDRES ODK Collect | `./gradlew assembleMedresDebug` |

## Build Variants

### Debug Builds
- `medresDebug` - MEDRES ODK Collect (debug)

### Release Builds
- `medresRelease` - MEDRES ODK Collect (release)
- `medresOdkCollectRelease` - Official release variant
- `medresSelfSignedRelease` - Self-signed release variant

## Building APKs

### Build MEDRES Debug APK
```bash
./gradlew assembleMedresDebug
```

### Build MEDRES Release APK
```bash
./gradlew assembleMedresRelease
```

### Build All Variants
```bash
# All debug variants
./gradlew assembleDebug

# All release variants
./gradlew assembleRelease

# All variants
./gradlew assemble
```

## Generated APK Locations

After building, APKs are located in:

```
collect_app/build/outputs/apk/
└── medres/
    ├── debug/
    │   └── MEDRES-ODK-Collect-debug.apk
    └── release/
        └── MEDRES-ODK-Collect-release.apk
```

## APK Naming Convention

- **MEDRES Debug**: `MEDRES-ODK-Collect-debug.apk`
- **MEDRES Release**: `MEDRES-ODK-Collect-release.apk`

## Application ID

| App | Application ID |
|-----|----------------|
| MEDRES ODK Collect | `org.medres.odk.collect` |

## Data Storage

MEDRES ODK Collect maintains independent data storage:

- **Data Directory**: `/data/data/org.medres.odk.collect/`
- Separate SQLite databases
- Independent SharedPreferences
- Isolated file storage
- Different cache directories

## Feature Flags

The MEDRES flavor has the following configuration:

```gradle
resValue("bool", "medres_auth_enabled", "true")
resValue("bool", "odk_launcher_enabled", "false")
resValue("string", "medres_default_api_url", "http://localhost:5174/api")
resValue("integer", "medres_default_offline_period", "7")
resValue("integer", "medres_default_auto_logout", "30")
```

## Versioning Strategy

### Version Numbers

| Build Type | Version Name Example |
|------------|---------------------|
| MEDRES Debug | `v2025.1.0-RC1-MEDRES-DEBUG` |
| MEDRES Release | `v2025.1.0-RC1-MEDRES` |

### How It Works

- **Base Version**: Defined in `defaultConfig` block
- **MEDRES Suffix**: Added via `versionNameSuffix "-MEDRES"` in MEDRES flavor
- **Debug Suffix**: Added via `versionNameSuffix "-DEBUG"` in debug build type

### Updating Versions

To update the app version, modify the `defaultConfig` in `collect_app/build.gradle`:

```gradle
defaultConfig {
    versionCode 5114  // Increment for each release
    versionName "v2025.1.1"  // Update version string
    // ... other config
}
```

The MEDRES and debug suffixes are automatically applied.

## Build Configuration Files

### Main Configuration
- **File**: `collect_app/build.gradle`
- **Location**: Product Flavors block

### Google Services Configuration
- **Debug**: `collect_app/src/debug/google-services.json`
- **Release**: `collect_app/src/release/google-services.json`

Both files include client configuration for `org.medres.odk.collect`.

### Flavor-Specific Resources
- **MEDRES**: `collect_app/src/medres/res/values/strings.xml`

## Installing on Device

### Install Debug APK
```bash
./gradlew :collect_app:installMedresDebug
```

### Install Release APK
```bash
# Requires signing configuration
./gradlew :collect_app:installMedresRelease
```

## Verifying Installation

After installation, verify the app is present:

```bash
adb shell pm list packages | grep medres.odk.collect
```

You should see:
- `package:org.medres.odk.collect`

## Troubleshooting

### Build Fails with "No matching client found"
If you get an error about missing client configuration in `google-services.json`:

**Solution**: Ensure `org.medres.odk.collect` is configured in `google-services.json`.

### Gradle Sync Issues
If Android Studio doesn't recognize the flavor:

**Solution**:
1. Click "Sync Now" when prompted
2. Or manually run: `./gradlew clean :collect_app:clean`
3. Rebuild the project

## Gradle Tasks Reference

### Available Tasks
```bash
# Show all available tasks for collect_app
./gradlew :collect_app:tasks

# List only assemble tasks
./gradlew :collect_app:tasks | grep -i assemble
```

### Common Build Commands

| Command | Purpose |
|---------|---------|
| `./gradlew assembleMedresDebug` | Build MEDRES debug APK |
| `./gradlew assembleMedresRelease` | Build MEDRES release APK |
| `./gradlew clean` | Clean all build artifacts |
| `./gradlew :collect_app:clean` | Clean only collect_app module |

## Architecture Overview

The source structure for MEDRES flavor:

```
collect_app/
├── src/
│   ├── main/
│   │   ├── AndroidManifest.xml
│   │   ├── java/
│   │   ├── res/
│   │   └── ... (shared code, modified for MEDRES)
│   ├── medres/
│   │   └── res/
│   │       └── values/
│   │           └── strings.xml (MEDRES-specific)
│   ├── debug/
│   │   └── google-services.json
│   └── release/
│       └── google-services.json
├── build.gradle (flavor definitions)
└── ... (other files)
```

## Integration with MEDRES Module

The `medres_auth_module` is the core authentication module:

- Enabled via `medres_auth_enabled=true`
- Provides MEDRES-specific authentication flow
- Manages user sessions, PIN, and data isolation

## References

- Android Product Flavors Documentation: https://developer.android.com/build/build-variants#product-flavors
- Build Variants Guide: https://developer.android.com/build/build-variants

## Support

For issues or questions about building, refer to:
1. `collect_app/build.gradle` - Product flavor definitions
2. `collect_app/src/debug/google-services.json` - Firebase configuration
3. `collect_app/src/release/google-services.json` - Firebase configuration

Please refer to the **[Maintenance Guide](../04-OPERATIONS/maintenance.md)** for:
- Syncing with upstream ODK changes
- Database migrations
- Release management

## Related Documentation

- [Quick Start](../01-QUICKSTART.md) - Getting started
- [Local Setup](setup.md) - Development environment
- [Debugging](debugging.md) - Debugging tools
- [Versioning](../04-OPERATIONS/versioning.md) - Version strategy
