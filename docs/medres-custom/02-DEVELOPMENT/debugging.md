# Debugging Guide - MEDRES ODK Collect

> Last Updated: 2025-12-28

This guide covers debugging tools and techniques for MEDRES ODK Collect development.

---

## Table of Contents

1. [Logcat Filtering](#logcat-filtering)
2. [Inspecting State](#inspecting-state)
3. [Common Issues](#common-issues)
4. [Breakpoints & Debugging](#breakpoints--debugging)
5. [Network Inspection](#network-inspection)
6. [Data Inspection](#data-inspection)
7. [Performance Profiling](#performance-profiling)

---

## Logcat Filtering

### MEDRES-Specific Logs

```bash
# All MEDRES authentication logs
adb logcat | grep -E "MedresAuth|MedresAppLock|MedresPin|MedresLogin"

# Auth state transitions
adb logcat | grep "AuthState"

# PIN-related logs
adb logcat | grep -E "PIN|PinManager"

# Telemetry logs
adb logcat | grep Telemetry
```

### Network Logs

```bash
# OkHttp requests (including token injection)
adb logcat | grep OkHttp

# OpenRosa API calls
adb logcat | grep -E "formList|submission"

# Connection errors
adb logcat | grep -E "IOException|ConnectException"
```

### ODK Core Logs

```bash
# Forms database
adb logcat | grep -E "FormsRepository|FormsDataService"

# Projects
adb logcat | grep -E "ProjectsRepository|Project"

# General ODK
adb logcat | grep "org.odk.collect"
```

### Clean Output (Errors Only)

```bash
# Show only errors
adb logcat *:E

# Errors for MEDRES package
adb logcat *:E | grep "medres"
```

---

## Inspecting State

### View Auth Token

**Via UI**:
1. Go to **Main Menu**
2. Tap **Auth Settings** (key/prefs icon in toolbar)
3. View token at top of screen

**Via ADB**:
```bash
adb shell run-as org.medres.odk.collect cat /data/data/org.medres.odk.collect/shared_prefs/medres_auth_prefs.xml
```

### Check Current Project

```bash
# View meta prefs for current project ID
adb shell run-as org.medres.odk.collect cat /data/data/org.medres.odk.collect/shared_prefs/meta_prefs.xml | grep current_project_id
```

### Inspect Project Settings

```bash
# List all project pref files
adb shell run-as org.medres.odk.collect ls /data/data/org.medres.odk.collect/shared_prefs/

# View specific project settings
adb shell run-as org.medres.odk.collect cat /data/data/org.medres.odk.collect/shared_prefs/general_prefs*.xml
```

### Check PIN Status

```bash
# View PIN hash (can't reverse to actual PIN)
adb shell run-as org.medres.odk.collect cat /data/data/org.medres.odk.collect/shared_prefs/medres_auth_prefs.xml | grep pin
```

### View Installed App Version

```bash
adb shell dumpsys package org.medres.odk.collect | grep version
```

---

## Common Issues

### Issue: Login Crash in Release Build

**Symptom**: App crashes with "Network Error" or `ClassCastException` on login

**Cause**: ProGuard/R8 removed required API model classes

**Solution**:
Check `collect_app/proguard-rules.pro`:
```proguard
-keep class edu.aiims.medresodk.auth.api.** { *; }
-keep class kotlin.coroutines.Continuation
```

Verify:
```bash
./gradlew assembleMedresRelease
# Check build output for obfuscation warnings
```

### Issue: Forms Persist After Logout

**Symptom**: Blank forms still visible after logout

**Debug Steps**:
1. Check if cleanup was triggered:
   ```bash
   adb logcat | grep -E "ProjectCleaner|cleanup"
   ```
2. Verify UUID resolution:
   ```bash
   adb logcat | grep -E "Project.*UUID|requireCurrentProject"
   ```
3. Check if threading issue:
   ```bash
   adb logcat | grep -E "StrictMode|Main.*Thread"
   ```

**Solution**: Ensure `ProjectCleaner` uses `withContext(Dispatchers.IO)`

### Issue: PIN Not Required on Resume

**Symptom**: App doesn't show PIN screen when returning from background

**Debug Steps**:
1. Check if `MedresAppLock` is registered:
   ```bash
   adb logcat | grep -E "AppLock|registerActivityLifecycle"
   ```
2. Verify activity count:
   ```bash
   adb logcat | grep "started.*activities"
   ```
3. Check auth state:
   ```bash
   adb logcat | grep "getCurrentAuthState"
   ```

**Solution**: Ensure `MedresAppLock` is registered in `Collect.onCreate()`

### Issue: Token Not Being Injected

**Symptom**: 401 Unauthorized on form list download

**Debug Steps**:
1. Check token exists:
   ```bash
   adb shell run-as org.medres.odk.collect cat /data/data/org.medres.odk.collect/shared_prefs/medres_auth_prefs.xml | grep auth_token
   ```
2. Check interceptor logs:
   ```bash
   adb logcat | grep -E "Interceptor|Bearer|Authorization"
   ```
3. Verify token provider:
   ```bash
   adb logcat | grep TokenProvider
   ```

**Solution**: Verify `TokenProvider` implementation in `AppDependencyModule`

### Issue: Local Dev Connection Fails

**Symptom**: Connection refused to `central.local`

**Debug Steps**:
1. Verify build type:
   ```bash
   adb shell dumpsys package org.medres.odk.collect | grep versionName
   # Should end with "-MEDRES-DEBUG"
   ```
2. Check DNS mapping:
   ```bash
   adb logcat | grep -E "central\.local|10\.0\.2\.2|OkHttp"
   ```
3. Test network:
   ```bash
   adb shell ping 10.0.2.2
   ```

**Solution**: Ensure debug build is installed, not release

---

## Breakpoints & Debugging

### Key Breakpoints

| File | Line/Function | Purpose |
|------|---------------|---------|
| `MedresAuthManager.kt` | `login()` | Entry point for auth |
| `MedresAuthManager.kt` | `refreshState()` | State transition logic |
| `MedresAppLock.kt` | `onActivityStarted()` | PIN trigger logic |
| `PinManager.kt` | `validatePin()` | PIN verification |
| `OkHttpOpenRosaServerClientProvider.java` | `intercept()` | Token injection |
| `ProjectCleaner.kt` | `cleanup()` | Data cleanup on logout |

### Debugging Authentication Flow

1. Set breakpoint in `MedresAuthManager.login()`
2. Debug app when clicking Login button
3. Step through:
   - API call to backend
   - Response parsing
   - Token storage
   - PIN check redirection
   - State update

### Debugging PIN Flow

1. Set breakpoint in `MedresAppLock.onActivityStarted()`
2. Minimize and restore app
3. Step through:
   - Auth state check
   - Soft expiry check
   - PIN set check
   - Activity launch

### Debugging Token Injection

1. Set breakpoint in `OkHttpOpenRosaServerClientProvider.intercept()`
2. Trigger any OpenRosa request (e.g., download forms)
3. Inspect:
   - Request headers (should have `Authorization: Bearer ...`)
   - Token value from `TokenProvider`
   - DNS mapping (if `central.local`)

---

## Network Inspection

### Using OkHttp Logging

The app uses OkHttp with logging interceptor:

```bash
# View full HTTP requests/responses
adb logcat | grep -E "OkHttp|-->|<--"
```

### Using Proxy

For detailed inspection, set up a proxy:

1. Configure emulator proxy:
   ```bash
   emulator -avd <name> -http-proxy http://localhost:8080
   ```

2. Use proxy tool (Charles, Fiddler, mitmproxy)

3. Install CA cert on emulator for HTTPS inspection

### Example Output

```
--> POST https://central.local/v1/projects/1/app-users/login
Content-Type: application/json
Content-Length: 56

{"username":"test","password":"password"}
--> END POST

--> END POST

<-- 200 OK https://central.local/v1/projects/1/app-users/login (123ms)
Content-Type: application/json

{"token":"eyJ...","expiresAt":"2025-12-31T23:59:59.000Z","projectId":1}
<-- END HTTP
```

---

## Data Inspection

### Database Queries

```bash
# Enter SQLite shell
adb shell run-as org.medres.odk.collect sqlite3 /data/data/org.medres.odk.collect/databases/odk.sqlite

# List forms
SELECT _form_id, display_name, jr_form_id FROM forms;

# List instances
SELECT _id, display_name FROM instances;

# Exit
.quit
```

### File System Inspection

```bash
# Browse form files
adb shell run-as org.medres.odk.collect ls -la /data/data/org.medres.odk.collect/files/

# View instance directories
adb shell run-as org.medres.odk.collect ls -la /data/data/org.medres.odk.collect/instances/

# Copy file from device
adb pull /data/data/org.medres.odk.collect/files/forms/form.xml .
```

---

## Performance Profiling

### Using Android Studio Profiler

1. Open **View** → **Tool Windows** → **Profiler**
2. Select process: `org.medres.odk.collect`
3. Profile:
   - **CPU**: Method tracing
   - **Memory**: Heap dumps, allocations
   - **Network**: HTTP traffic
   - **Energy**: Power usage

### Common Performance Checks

| Issue | Check Command |
|----------------------|
| Main thread blocking | `adb logcat | grep StrictMode` |
| Memory leaks | Heap dump comparison |
| Slow DB queries | `adb logcat | grep -E "SQL|database"` |
| Network latency | OkHttp timing logs |

---

## Tips & Tricks

### Force Logout

```bash
# Clear app data (equivalent to fresh install)
adb shell pm clear org.medres.odk.collect

# Or clear just auth prefs
adb shell run-as org.medres.odk.collect rm /data/data/org.medres.odk.collect/shared_prefs/medres_auth_prefs.xml
```

### Simulate Token Expiry

```bash
# Set expiry to past
adb shell run-as org.medres.odk.collect
sqlite3 /data/data/org.medres.odk.collect/shared_prefs/medres_auth_prefs.xml
# Edit expires_at to past timestamp
```

Or via code (debug only):
```kotlin
MedresAuthManager.setDebugExpiry(Instant.now().minusSeconds(3600))
```

### Disable PIN (Debug Only)

```bash
# Clear PIN from prefs
adb shell run-as org.medres.odk.collect
rm /data/data/org.medres.odk.collect/shared_prefs/medres_auth_prefs.xml
# Then edit to remove pin_hash and pin_salt entries
```

---

## Related Documentation

- [Local Development Setup](setup.md) - Environment setup
- [Building the App](building.md) - Build instructions
- [Architecture Overview](../01-ARCHITECTURE/overview.md) - System architecture
- [Authentication System](../01-ARCHITECTURE/authentication.md) - Auth flow details
