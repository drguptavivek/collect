# MEDRES ODK Collect Versioning Guide
> Last Updated: 2025-12-28
> Reviewed At: 2025-12-28

## Overview

This document explains the versioning strategy for MEDRES ODK Collect.

## Version Location

**File:** `collect_app/build.gradle`  
**Lines:** 81-82 in `defaultConfig` block

```gradle
defaultConfig {
    versionCode 5115               // Integer for Play Store
    versionName "v2025.3.3-MEDRES-RC2" // Human-readable version
    // ...
}
```

## Version Components

### versionCode (Integer)
- **Purpose:** Unique identifier used by Android/Play Store for updates
- **Rule:** Must be **strictly increasing** for each release
- **Current:** `5115`

### versionName (String)
- **Purpose:** Human-readable version displayed to users
- **Format:** `v{YEAR}.{MAJOR}.{PATCH}[-SUFFIX]`
- **Current:** `v2025.3.3-MEDRES-RC2`

### Automatic Suffixes

The build system automatically appends suffixes:

| Build Type | Suffix Added | Final Example |
|------------|--------------|---------------|
| MEDRES Release | `-MEDRES` | `v2025.1.0-RC1-MEDRES` |
| MEDRES Debug | `-MEDRES-DEBUG` | `v2025.1.0-RC1-MEDRES-DEBUG` |

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
| `-RC{N}` | Release Candidate | `v2025.3.3-MEDRES-RC1` |
| `-RC{N}-DEV` | Development between RCs | `v2025.3.3-MEDRES-RC2-DEV` |
| `-beta` | Beta release | `v2025.2.0-beta` |
| `-alpha` | Alpha/experimental | `v2025.3.0-alpha` |

### Iterative RC Workflow

1. **RC Build**: Cut a release candidate (e.g., `RC1`) and distribute APKs.
2. **Development**: Immediately bump to the next `RC-DEV` version (e.g., `RC2-DEV`).
3. **Bug Fixing**: Apply fixes on the `vg-work` branch.
4. **Promotion**: When ready for the next RC, drop the `-DEV` suffix, tag, and release.

## Syncing with Upstream ODK Version

MEDRES ODK Collect is a fork of ODK Collect. Before releasing, check upstream version and align accordingly.

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

### Step 4: Compare and Update MEDRES Version

```bash
# Check current MEDRES version
grep -E "versionCode|versionName" collect_app/build.gradle
```

**Decision Matrix:**

| Scenario | Action |
|----------|--------|
| Upstream `versionCode` > MEDRES | Update MEDRES to match or exceed upstream |
| MEDRES has independent changes | Increment MEDRES versionCode beyond upstream |
| Major upstream merge | Adopt upstream version, add MEDRES patch number |

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
# - MEDRES:    versionCode 5113, versionName "v2025.1.0-RC1"

# After merging upstream, update MEDRES:
versionCode 5201              # Upstream + 1
versionName "v2025.2.0"       # Match upstream base version
# Result: v2025.2.0-MEDRES
```

### Quick Reference Commands

```bash
# Check upstream version
git show upstream/master:collect_app/build.gradle | grep -E "versionCode|versionName"

# Check local MEDRES version
grep -E "versionCode|versionName" collect_app/build.gradle

# Compare side by side
echo "=== Upstream ===" && git show upstream/master:collect_app/build.gradle | grep -E "versionCode|versionName" && echo "=== MEDRES ===" && grep -E "versionCode|versionName" collect_app/build.gradle
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
./gradlew assembleMedresDebug
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
| 5114 | v2025.3.3-MEDRES-RC1 | 2026-01-01 | Initial MEDRES release |
| 5115 | v2025.3.3-MEDRES-RC2 | 2026-01-02 | Fix Auth 404 URL issue (collect-uyw) |

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
Result: `v2025.1.1-MEDRES`

### New Feature Release
```gradle
// Before
versionCode 5114
versionName "v2025.1.1"

// After
versionCode 5115
versionName "v2025.2.0"
```
Result: `v2025.2.0-MEDRES`

### Release Candidate
```gradle
versionCode 5116
versionName "v2025.2.0-RC1"
```
Result: `v2025.2.0-RC1-MEDRES`

## Checking Current Version

### In Code
```kotlin
BuildConfig.VERSION_NAME  // "v2025.1.0-RC1-MEDRES"
BuildConfig.VERSION_CODE  // 5113
```

### In APK
```bash
# Check installed app version
adb shell dumpsys package org.medres.odk.collect | grep version
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

- [MEDRES_flavours_building_apk.md](MEDRES_flavours_building_apk.md) - Build instructions
- [Android_Cmds.md](Android_Cmds.md) - Quick command reference
- [CHANGELOG.md](CHANGELOG.md) - Version history
