# AIIMS ODK Collect Versioning Guide

## Overview

This document explains the versioning strategy for AIIMS ODK Collect.

## Version Location

**File:** `collect_app/build.gradle`  
**Lines:** 81-82 in `defaultConfig` block

```gradle
defaultConfig {
    versionCode 5113           // Integer for Play Store
    versionName "v2025.1.0-RC1" // Human-readable version
    // ...
}
```

## Version Components

### versionCode (Integer)
- **Purpose:** Unique identifier used by Android/Play Store for updates
- **Rule:** Must be **strictly increasing** for each release
- **Current:** `5113`

### versionName (String)
- **Purpose:** Human-readable version displayed to users
- **Format:** `v{YEAR}.{MAJOR}.{PATCH}[-SUFFIX]`
- **Current:** `v2025.1.0-RC1`

### Automatic Suffixes

The build system automatically appends suffixes:

| Build Type | Suffix Added | Final Example |
|------------|--------------|---------------|
| AIIMS Release | `-AIIMS` | `v2025.1.0-RC1-AIIMS` |
| AIIMS Debug | `-AIIMS-DEBUG` | `v2025.1.0-RC1-AIIMS-DEBUG` |

## Versioning Scheme

### Format: `v{YEAR}.{MAJOR}.{PATCH}`

| Component | Description | When to Increment |
|-----------|-------------|-------------------|
| `YEAR` | Calendar year | New year or major rebase |
| `MAJOR` | Major feature release | New features, breaking changes |
| `PATCH` | Bug fixes, minor updates | Bug fixes, small improvements |

### Optional Suffixes

| Suffix | Purpose | Example |
|--------|---------|---------|
| `-RC{N}` | Release Candidate | `v2025.1.0-RC2` |
| `-beta` | Beta release | `v2025.2.0-beta` |
| `-alpha` | Alpha/experimental | `v2025.3.0-alpha` |

## Syncing with Upstream ODK Version

AIIMS ODK Collect is a fork of ODK Collect. Before releasing, check upstream version and align accordingly.

### Step 1: Add Upstream Remote (One-time Setup)

```bash
# Add ODK upstream if not already added
git remote add upstream https://github.com/getodk/collect.git

# Verify remotes
git remote -v
```

### Step 2: Fetch Upstream Tags and Branches

```bash
# Fetch all upstream data
git fetch upstream --tags

# List upstream tags (versions)
git tag -l | grep "^v" | sort -V | tail -10
```

### Step 3: Check Latest Upstream Version

```bash
# View latest upstream release tag
git describe --tags $(git rev-list --tags --max-count=1 upstream/master 2>/dev/null || echo upstream/master)

# Or check upstream build.gradle directly
git show upstream/master:collect_app/build.gradle | grep -E "versionCode|versionName"
```

### Step 4: Compare and Update AIIMS Version

```bash
# Check current AIIMS version
grep -E "versionCode|versionName" collect_app/build.gradle
```

**Decision Matrix:**

| Scenario | Action |
|----------|--------|
| Upstream `versionCode` > AIIMS | Update AIIMS to match or exceed upstream |
| AIIMS has independent changes | Increment AIIMS versionCode beyond upstream |
| Major upstream merge | Adopt upstream version, add AIIMS patch number |

### Step 5: Merge Upstream Changes (Optional)

If you want to incorporate upstream changes:

```bash
# Ensure you're on vg-work branch
git checkout vg-work

# Merge upstream master (or specific tag)
git merge upstream/master

# Or merge a specific release tag
git merge v2025.2.0

# Resolve conflicts, then update version
```

### Example: Syncing with Upstream

```bash
# Current state
# - Upstream: versionCode 5200, versionName "v2025.2.0"
# - AIIMS:    versionCode 5113, versionName "v2025.1.0-RC1"

# After merging upstream, update AIIMS:
versionCode 5201              # Upstream + 1
versionName "v2025.2.0"       # Match upstream base version
# Result: v2025.2.0-AIIMS
```

### Quick Reference Commands

```bash
# Check upstream version
git show upstream/master:collect_app/build.gradle | grep -E "versionCode|versionName"

# Check local AIIMS version
grep -E "versionCode|versionName" collect_app/build.gradle

# Compare side by side
echo "=== Upstream ===" && git show upstream/master:collect_app/build.gradle | grep -E "versionCode|versionName" && echo "=== AIIMS ===" && grep -E "versionCode|versionName" collect_app/build.gradle
```

## How to Update Version

### Step 1: Edit build.gradle

Open `collect_app/build.gradle` and update:

```gradle
defaultConfig {
    versionCode 5114              // Increment by 1
    versionName "v2025.1.1"       // Update version string
    // ...
}
```

### Step 2: Build and Verify

```bash
./gradlew assembleAiimsDebug
```

Check the APK name includes the new version.

### Step 3: Commit

```bash
git add collect_app/build.gradle
git commit -m "Bump version to v2025.1.1 (5114)"
```

## Version History Template

Track releases in a changelog:

| versionCode | versionName | Date | Changes |
|-------------|-------------|------|---------|
| 5113 | v2025.1.0-RC1 | 2025-01 | Initial AIIMS release |
| 5114 | v2025.1.1 | TBD | Bug fixes |

## Examples

### Minor Bug Fix Release
```gradle
// Before
versionCode 5113
versionName "v2025.1.0-RC1"

// After
versionCode 5114
versionName "v2025.1.1"
```
Result: `v2025.1.1-AIIMS`

### New Feature Release
```gradle
// Before
versionCode 5114
versionName "v2025.1.1"

// After
versionCode 5115
versionName "v2025.2.0"
```
Result: `v2025.2.0-AIIMS`

### Release Candidate
```gradle
versionCode 5116
versionName "v2025.2.0-RC1"
```
Result: `v2025.2.0-RC1-AIIMS`

## Checking Current Version

### In Code
```kotlin
BuildConfig.VERSION_NAME  // "v2025.1.0-RC1-AIIMS"
BuildConfig.VERSION_CODE  // 5113
```

### In APK
```bash
# Check installed app version
adb shell dumpsys package org.aiims.odk.collect | grep version
```

### In App
Settings → About → Version

## Best Practices

1. **Always increment versionCode** - Even for test builds distributed to users
2. **Use semantic versioning** - Clear meaning for each number
3. **Tag releases in git** - `git tag v2025.1.1`
4. **Document changes** - Update CHANGELOG.md with each release
5. **Test before release** - Verify version displays correctly in app

## Related Files

- [AIIMS_flavours_building_apk.md](AIIMS_flavours_building_apk.md) - Build instructions
- [Android_Cmds.md](Android_Cmds.md) - Quick command reference
- [CHANGELOG.md](CHANGELOG.md) - Version history
