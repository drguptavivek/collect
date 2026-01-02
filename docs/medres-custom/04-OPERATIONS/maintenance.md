# Maintenance Guide
> Last Updated: 2025-12-28

This document outlines the procedures for maintaining the MEDRES Custom Fork of ODK Collect.

## Branch Structure
- **`master`**: Tracks `upstream/master` (Standard ODK Collect). SHOULD NOT contain custom code.
- **`vg-work`**: The active development branch containing MEDRES customizations on top of `master`.

## syncing with Upstream (Rebase Workflow)

To update the custom fork with the latest standard features/fixes:

1. **Update `master`**
   ```bash
   git checkout master
   git fetch upstream
   git merge upstream/master --ff-only
   git push origin master
   ```

2. **Rebase `vg-work`**
   ```bash
   git checkout vg-work
   git rebase master
   ```

3. **Resolve Conflicts**
   - If conflicts occur, resolve them in favor of keeping MEDRES functionality compliant with new upstream changes.
   - Use `git rebase --continue`.

4. **Verify**
   - Clean and Rebuild: `./gradlew clean assembleDebug`
   - Run Tests: `./gradlew testDebugUnitTest`

5. **Update Log**
   - Add an entry to `docs/SYNC_LOG.md` recording the sync.

6. **Push**
   ```bash
   git push -f origin vg-work
   ```
   *(Note: Force push is required after rebase)*

## Configuration & Feature Flags

The MEDRES module supports build-time and runtime configuration.

### Feature Flags
Located in `medres_auth_module/src/main/res/values/medres_config.xml` (or overridden in `collect_app`):

| Flag | Default | Description |
| :--- | :--- | :--- |
| `medres_auth_enabled` | `true` | Master switch. If false, app reverts to standard ODK behavior. |
| `odk_launcher_enabled` | `false` | Inverse of auth_enabled. Controls which Activity handles `MAIN` intent. |

### Build Config
You can also force flags in `gradle.properties` or `build.gradle`:
```gradle
buildConfigField "boolean", "MEDRES_AUTH_ENABLED", "true"
```

## Troubleshooting

### Common Sync/Build Issues

1.  **Hilt/Dagger Errors**:
    *   *Symptom*: "Missing binding for MedresAuthManager".
    *   *Fix*: Ensure `medres_auth_module` is included in `settings.gradle` and instantiated in `AppDependencyModule`.

2.  **Release Build Crashes**:
    *   *Symptom*: Login fails with "Network Error" or `ClassCastException` in Retrofit.
    *   *Fix*: Check `proguard-rules.pro`. Essential rules:
        ```proguard
        -keep class edu.aiims.medresodk.auth.api.** { *; }
        -keep class kotlin.coroutines.Continuation
        ```

3.  **Local Development (Emulator)**:
    *   *Symptom*: Connection Refused to `central.local`.
    *   *Fix*: Maintain the `OkHttpOpenRosaServerClientProvider` hacks that map `central.local` -> `10.0.2.2` for Debug builds.

