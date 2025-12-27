# AIIMS Collect Test Documentation

This document catalogs the AIIMS-specific tests and their purpose.

---

## Test Files

### AiimsAuthManagerTest.kt
**Location**: `aiims-auth-module/src/test/java/org/aiims/odk/auth/managers/`

Tests for the authentication manager focusing on login/logout flows and session handling.

| Test | Description |
|------|-------------|
| `#login stores token and updates state when success` | Verifies successful login stores token |
| `#login returns error and does not change state when failure` | Verifies failed login handling |
| `#login clears PIN when user changed` | Security: New user clears old PIN |
| `#login keeps PIN when user is the same` | Convenience: Same user keeps PIN |
| `#logout revokes token but preserves project data - Option B` | **Option B**: Data persists on logout |
| `#logout preserves project data for next user on shared device - Option B` | Multi-user scenario validation |
| `#refreshState detects token expiry and allows grace if server unreachable` | Offline grace period |
| `#refreshState enforces hard deadline after 6 hours` | Security timeout |
| `#refreshState sets soft expiry if reachable within grace` | Re-auth prompt |
| `#submitTelemetry sends correct data` | Telemetry payload validation |

---

### ProjectConfigurationTest.kt
**Location**: `aiims-auth-module/src/test/java/org/aiims/odk/auth/activities/`

Tests for the Find-or-Create project pattern used when configuring projects.

| Test | Scenario | Expected |
|------|----------|----------|
| `same URL reuses existing project - no change` | Same URL configured twice | Reuse existing project |
| `different project ID creates new project` | Same base URL, different `/projects/N` | New project created |
| `different server URL creates new project` | Different server, same project ID | New project created |
| `both URL and project ID change creates new project` | Both change | New project created |
| `switching back to previous project reuses it` | Configure A → B → A | Original A is reused |
| `project data persists across configuration changes - Option B` | Settings/flags survive switch | Data persists |
| `multiple projects can coexist for multi-user shared device` | 3 different projects | All 3 coexist |
| `dev server IP is saved to preferences` | Save dev IP | Persists in prefs |
| `dev server IP can be cleared` | Clear dev IP | Returns null |
| `dev server IP URL construction adds https if missing` | IP without http | Adds https:// |
| `dev server IP URL preserves existing http prefix` | IP with http:// | Preserves prefix |
| `dev server IP is only used in DEBUG builds` | DEBUG-only feature | Inaccessible in release |

---

## Running Tests

```bash
# All AIIMS auth module tests
./gradlew :aiims-auth-module:testDebugUnitTest

# Specific test class
./gradlew :aiims-auth-module:testDebugUnitTest --tests "*AiimsAuthManagerTest*"
./gradlew :aiims-auth-module:testDebugUnitTest --tests "*ProjectConfigurationTest*"

# All tests with coverage
./gradlew :aiims-auth-module:testDebugUnitTest jacocoTestReport
```

---

### AiimsAppLockTest.kt
**Location**: `aiims-auth-module/src/test/java/org/aiims/odk/auth/utils/`

Tests for the app lock mechanism that manages PIN requirement on app resume.

| Test | Scenario | Expected |
|------|----------|----------|
| `does not require PIN when not returning from background` | First start | No PIN |
| `sets shouldRequirePin when app goes to background` | Home button | Sets flag |
| `launches PinEntryActivity when returning from background with PIN set` | Resume after background | PIN required |
| `does not launch PIN when user is not logged in` | Logged out | No PIN |
| `does not launch PIN when PIN is not set` | No PIN configured | No PIN |
| `launches LoginActivity for reauth when soft expiry` | Token expired | Login screen |
| `skips PIN for auth flow activities` | Already on PinEntry | No redirect |
| `skips PIN for login activity` | Already on Login | No redirect |
| `multiple activities do not trigger background state` | Navigation in app | No PIN |

---

## Key Behaviors Tested

### Option B: Clear Nothing on Logout
- Verified in `AiimsAuthManagerTest`
- `projectCleaner.clearProjectData()` is **never** called
- Forms, instances, cache persist across user sessions

### Find-or-Create Pattern
- Verified in `ProjectConfigurationTest`
- Existing projects are reused when URL matches
- New projects created only when URL differs
- Supports multi-project coexistence on shared devices

---

## Related Documentation
- [User Behavior](vg-user-behaviour.md) - End-user behavior expectations
- [AIIMS Architecture](AIIMS_ARCHITECTURE.md) - System design
