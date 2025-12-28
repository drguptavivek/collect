# AIIMS Reauthentication Workflow - Complete Scenario Matrix
> Last Updated: 2025-12-28
> Reviewed At: 2025-12-28

This document provides a comprehensive analysis of all possible scenarios in the AIIMS authentication module's reauthentication workflow with grace period.

---

## FIX INSTRUCTIONS
Fix collect-a55 address beads issues ..... 
When Fixing the issue, Refer to 
@docs/aiims-custom/audit/REAUTHENTICATION_SCENARIOS.md and 
@docs/aiims-custom/01-ARCHITECTURE/ 
Think carefully and propose a fix. 
Confirm first before applying fix. 
Verify. Doublecheck code.
Then Do an 
./gradlew clean
./gradlew compileAiimsDebug 
./gradlew installAiimsDebug

Then ask for User verofciation. 
Once user confirms, DoubleCheck code.
Update the @docs/aiims-custom/audit/REAUTHENTICATION_SCENARIOS.md  and @docs/aiims-custom/01-ARCHITECTURE/ . 
Finally  git commit. 
Close beads issue with full details of fix  and Git Commit ID.
Close GH issue  with full details of fix and Git Commit ID.
Udpate Knowldge base for any new broad based knowledge.

---

## Table of Contents

1. [System Overview](#system-overview)
2. [Key Variables](#key-variables)
3. [Scenario Matrix](#scenario-matrix)
4. [Detailed Scenario Analysis](#detailed-scenario-analysis)
5. [State Transitions](#state-transitions)
6. [Edge Cases](#edge-cases)

---

## System Overview

### Key Constants
- **GRACE_PERIOD_MS**: 6 hours (21,600,000 ms)
- **EXPIRATION_THRESHOLD_MS**: 24 hours (86,400,000 ms)
- **REACHABILITY_TIMEOUT_MS**: 5 seconds

### AppUser Constraint (Server-Side)

**CRITICAL: Users are permanently bound to ONE project and CANNOT switch projects**

- **One User ID → ONE Project (Permanent)**: Each user ID is associated with exactly ONE project and cannot switch to another project
- **Multiple Users Per Project**: A project can have multiple app users (shared device scenarios, team access)
- **Single Active Session**: At any given time, only ONE user-project ID can be logged in on the app
- **User Switching**: When User B logs in, User A is logged out (no simultaneous users)

### Authentication Model

```
┌─────────────────────────────────────────────────────────────┐
│                     Server-Side Binding                      │
├─────────────────────────────────────────────────────────────┤
│  alice@example.com  → PERMANENTLY bound to → Project A       │
│  bob@example.com    → PERMANENTLY bound to → Project A       │
│  charlie@example.com → PERMANENTLY bound to → Project B      │
│                                                               │
│  Users CANNOT switch projects - binding is permanent         │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│                   Device Storage (Single Session)            │
├─────────────────────────────────────────────────────────────┤
│  Active Session:                                              │
│  ├─ currentUserId: alice@example.com                         │
│  ├─ currentProjectId: project_a_id                           │
│  ├─ auth_token                                               │
│  ├─ expires_at                                               │
│  └─ grace_period_state                                       │
│                                                               │
│  When bob@example.com logs in:                                │
│  → Alice logged out (session replaced)                        │
│  → New session: bob + project_a_id                           │
│  → Alice's grace period state not preserved                  │
└─────────────────────────────────────────────────────────────┘

Shared Device Example:
┌─────────────────────────────────────────────────────────────┐
│  Project A can have multiple users:                          │
│  ├─ Alice can use Project A (when logged in)                │
│  ├─ Bob can use Project A (when logged in)                   │
│  └─ But NOT simultaneously (one session at a time)           │
│                                                               │
│  Charlie cannot use Project A:                                │
│  └─ Charlie is bound to Project B (server enforces)          │
└─────────────────────────────────────────────────────────────┘
```

### Key Time Points
```
|-------- VALID PERIOD --------|------- GRACE PERIOD -------|---- EXPIRED ----|
Token Issued                    Token Expires              +6 hours           Hard Deadline
T0                              T_expiry                   T_grace_end        T_hard
```

### States
1. **VALID**: Token not expired (`currentTime <= T_expiry`)
2. **GRACE**: Token expired but within grace period (`T_expiry < currentTime <= T_hard`)
3. **HARD_EXPIRED**: Past grace period (`currentTime > T_hard`)

### Network States
- **ONLINE_REACHABLE**: Server reachable via `/version.txt`
- **ONLINE_UNREACHABLE**: Device online but server unreachable
- **OFFLINE**: No network connectivity

---

## Key Variables

| Variable | Type | Range | Impact |
|----------|------|-------|--------|
| `currentTime` | Timestamp | Any time | Compared against token expiry |
| `tokenExpiryTime` | Timestamp | Fixed at login | Determines validity window |
| `hardDeadline` | Timestamp | `tokenExpiryTime + 6 hours` | End of grace period |
| `serverReachable` | Boolean | true/false | Determines reauth prompt behavior |
| `isInForeground` | Boolean | true/false | Triggers grace period checks |
| `userHasPIN` | Boolean | true/false | Determines secondary auth |
| `userInGracePeriod` | Boolean | true/false | `_isSoftExpiry` state |
| `currentUserId` | String | User email/ID | Permanently bound to one project |
| `currentProjectId` | String | Project identifier | Fixed per user (cannot switch) |

### AppUser Constraint Variables

| Variable | Constraint | Server Behavior |
|----------|------------|-----------------|
| `userId` | ONE permanent project | User bound to single project forever |
| `projectId` | MULTIPLE users | Project can have many app users |
| `sessionContext` | Single session | Only one user logged in at a time |

---

## Scenario Matrix

### Primary Scenario Matrix (Token × Network × Server)

| # | Token State | Grace Window | Network | Server Reachable | Result | UI Action |
|---|-------------|--------------|---------|------------------|--------|-----------|
| 1 | Valid | N/A | Online | Yes | Normal Access | None |
| 2 | Valid | N/A | Online | No | Normal Access | None |
| 3 | Valid | N/A | Offline | N/A | Normal Access | None |
| 4 | Expired | Within (0-6h) | Online | Yes | **Soft Expiry** | Show Reauth Prompt |
| 5 | Expired | Within (0-6h) | Online | No | **Silent Grace** | Allow offline access |
| 6 | Expired | Within (0-6h) | Offline | N/A | **Silent Grace** | Allow offline access |
| 7 | Expired | Outside (>6h) | Online | Yes | **Hard Logout** | Force logout |
| 8 | Expired | Outside (>6h) | Online | No | **Hard Logout** | Force logout |
| 9 | Expired | Outside (>6h) | Offline | N/A | **Hard Logout** | Force logout |

### Secondary Scenario Matrix (App State × User Action)

| # | App State | Current Auth State | User Action | Result |
|---|-----------|-------------------|-------------|--------|
| 10 | Background → Foreground | Grace Period + Server Reachable | Auto | Show Reauth Prompt |
| 11 | Background → Foreground | Grace Period + Server Unreachable | Auto | Silent continue |
| 12 | Foreground | Grace Period | User clicks "Work Offline" | Grace continues, prompt snoozed |
| 13 | Foreground | Grace Period | User reauthenticates | Token refreshed, grace reset |
| 14 | Foreground | Grace Period | User cancels prompt | Grace continues, prompt snoozed |
| ~~15~~ | ~~N/A~~ | **INVALID** | **User cannot switch projects** | See note below |
| 16 | Any | Hard Deadline | Any action | Force logout, no override |

**Note on Scenario 15:** Users are permanently bound to one project and CANNOT switch projects. See AppUser Constraint above.

---

## Detailed Scenario Analysis

### SCENARIO 1: Valid Token, Server Reachable (Happy Path)

**Conditions:**
- `currentTime <= tokenExpiryTime`
- Server responds to `/version.txt` within 5 seconds
- App in foreground

**System Behavior:**
```
_authState = LOGGED_IN
_isSoftExpiry = false
_isExpiringSoon = false (unless within 24h)
```

**User Experience:**
- Full app access
- No prompts
- Telemetry worker runs normally

**Code Flow:**
```kotlin
// AiimsAuthManager:checkTokenExpiry()
if (currentTime <= expiryTime) {
    _authState.value = AuthState.LOGGED_IN
    _isSoftExpiry.value = false
}
```

---

### SCENARIO 2: Valid Token, Server Unreachable

**Conditions:**
- `currentTime <= tokenExpiryTime`
- Server fails to respond to `/version.txt`
- App in foreground

**System Behavior:**
```
_authState = LOGGED_IN
_isSoftExpiry = false
```

**User Experience:**
- Full app access (token is still valid)
- No prompts needed
- App functions normally

**Note:** Server reachability only matters during grace period, not when token is valid.

---

### SCENARIO 3: Valid Token, Device Offline

**Conditions:**
- `currentTime <= tokenExpiryTime`
- No network connectivity
- App in foreground

**System Behavior:**
```
_authState = LOGGED_IN
_isSoftExpiry = false
```

**User Experience:**
- Full app access
- All functionality works (cached data, local submissions)

---

### SCENARIO 4: Token Expired, Within Grace Period, Server Reachable (CRITICAL)

**Conditions:**
- `tokenExpiryTime < currentTime <= hardDeadline` (0-6 hours past expiry)
- Server responds to `/version.txt`
- App comes to foreground

**System Behavior:**
```kotlin
// AiimsAuthManager:checkTokenExpiry()
if (currentTime > expiryTime && currentTime <= hardDeadline) {
    if (serverReachable) {
        _isSoftExpiry.value = true  // TRIGGERS PROMPT
    } else {
        _isSoftExpiry.value = false
    }
}
```

**User Experience:**
1. App detects grace period on foreground transition
2. `AiimsAppLock` triggers `AiimsLoginActivity` with `is_reauth=true`
3. User sees "Session Expired" screen with options:
   - **"Login Again"** → Full reauthentication flow
   - **"Work Offline (Grace Period)"** → Dismisses prompt, continues working
   - **"Cancel"** → Snoozes until next app restart

**Code Flow:**
```kotlin
// AiimsAppLock:onActivityResumed()
if (authManager.isSoftExpiry.value) {
    val intent = Intent(context, AiimsLoginActivity::class.java).apply {
        putExtra("is_reauth", true)
    }
    context.startActivity(intent)
}
```

**Sub-scenarios:**

#### 4a: User Reauthenticates
```
Token refresh → New expiry time set → Grace period reset
```

#### 4b: User Chooses "Work Offline"
```
_isSoftExpiry = false → Prompt dismissed → Access granted
Next foreground transition → Re-evaluates grace period
```

#### 4c: User Cancels
```
_isSoftExpiry = false → Prompt snoozed → Access granted
```

---

### SCENARIO 5: Token Expired, Within Grace Period, Server Unreachable

**Conditions:**
- `tokenExpiryTime < currentTime <= hardDeadline`
- Server does NOT respond to `/version.txt`
- App in foreground

**System Behavior:**
```kotlin
// Background reachability check
if (!serverReachable) {
    _isSoftExpiry.value = false  // SILENT GRACE
}
```

**User Experience:**
- **Silent grace period** - NO prompt shown
- Full app access continues
- User can work offline uninterrupted
- Telemetry may fail silently

**Rationale:** If the server is unreachable, the user cannot reauthenticate anyway, so forcing a prompt creates friction without benefit.

---

### SCENARIO 6: Token Expired, Within Grace Period, Device Offline

**Conditions:**
- `tokenExpiryTime < currentTime <= hardDeadline`
- No network connectivity
- App in foreground

**System Behavior:**
```
serverReachable = false (check fails immediately)
_isSoftExpiry = false
```

**User Experience:**
- Silent grace period
- No prompts shown
- Full offline access

**Note:** The reachability check requires network; failure defaults to silent grace.

---

### SCENARIO 7: Token Expired, Past Grace Period (Hard Deadline), Server Reachable

**Conditions:**
- `currentTime > hardDeadline` (>6 hours past token expiry)
- Server is reachable
- App in foreground

**System Behavior:**
```kotlin
// AiimsAuthManager:checkTokenExpiry()
if (currentTime > hardDeadline) {
    logout()  // FORCED LOGOUT
}
```

**User Experience:**
1. Immediate logout
2. All session data cleared
3. User redirected to login screen
4. Must enter full credentials again

**Code Flow:**
```kotlin
// AiimsAuthManager:logout()
telemetryWorker.cancel()
authStorage.clearToken()
authStorage.clearUserData()
authState.value = AuthState.LOGGED_OUT
```

**Irreversible:** Once hard deadline is reached, logout is enforced regardless of:
- Server reachability
- User action
- Network state
- App state

---

### SCENARIO 8: Token Expired, Past Grace Period, Server Unreachable

**Conditions:**
- `currentTime > hardDeadline`
- Server unreachable
- App in foreground

**System Behavior:**
```
HARD DEADLINE TRUMPS EVERYTHING
logout() enforced regardless of server state
```

**User Experience:**
- Forced logout
- Server state is irrelevant at this point
- User must login again (requires connectivity eventually)

**Note:** Even if server is unreachable, the 6-hour window has closed and security requires logout.

---

### SCENARIO 9: Token Expired, Past Grace Period, Device Offline

**Conditions:**
- `currentTime > hardDeadline`
- Device offline
- App in foreground

**System Behavior:**
```
logout() enforced
```

**User Experience:**
- Forced logout
- App shows login screen
- User cannot proceed until network is available

**Security Rationale:** The 6-hour grace period is a security boundary. Extending it indefinitely offline would defeat the purpose.

---

### SCENARIO 10: App Background → Foreground Transition (Grace Active)

**Conditions:**
- App was backgrounded with token in grace period
- User brings app to foreground
- Server was reachable at time of check

**System Behavior:**
```kotlin
// AiimsAppLock:onActivityResumed()
if (authManager.isSoftExpiry.value) {
    // Launch reauth screen
    startActivity(AiimsLoginActivity with is_reauth=true)
}
```

**User Experience:**
1. App becomes active
2. Immediate redirect to "Session Expired" screen
3. User must choose an action

**Note:** This check happens on EVERY foreground transition while in grace period.

---

### SCENARIO 11: App Background → Foreground (Grace Active + Server Unreachable)

**Conditions:**
- App was backgrounded with token in grace period
- User brings app to foreground
- Server unreachable at time of check

**System Behavior:**
```kotlin
// Reachability check runs in background
// If unreachable:
_isSoftExpiry.value = false
```

**User Experience:**
- App resumes normally
- No prompt shown
- Silent grace continues

---

### SCENARIO 12: User Clicks "Work Offline" During Grace

**Conditions:**
- Token in grace period
- Server reachable
- Reauth prompt shown
- User clicks "Work Offline (Grace Period)"

**System Behavior:**
```kotlin
// AiimsLoginActivity: onClick work offline button
finish() // Dismisses the reauth screen
authManager._isSoftExpiry.value = false // Resets soft expiry flag
```

**User Experience:**
1. Reauth screen dismissed
2. App returns to normal functionality
3. Next foreground transition may re-trigger the check

---

### SCENARIO 13: User Reauthenticates During Grace

**Conditions:**
- Token in grace period
- Reauth prompt shown
- User enters valid credentials

**System Behavior:**
```kotlin
// AiimsLoginActivity: Login success
val newToken = authenticate(credentials)
authStorage.saveToken(newToken)
authStorage.saveExpiryTime(newExpiry)
authManager._isSoftExpiry.value = false
```

**User Experience:**
1. New token obtained
2. Grace period reset
3. App continues normally
4. Next check will use new expiry time

---

### SCENARIO 14: User Cancels Reauth Prompt

**Conditions:**
- Token in grace period
- Reauth prompt shown
- User clicks "Cancel" or back button

**System Behavior:**
```kotlin
// AiimsLoginActivity: onCancel()
finish() // Simply closes the reauth screen
_isSoftExpiry.value = false // Resets flag
```

**User Experience:**
1. Prompt dismissed
2. Grace period continues silently
3. Next app restart may re-trigger

---

### ~~SCENARIO 15: User Switches Projects During Grace~~ **INVALID**

**Status:** This scenario is **NO LONGER VALID** due to the AppUser constraint.

**AppUser Constraint:**
- Users are **permanently bound to ONE project**
- Users **CANNOT switch projects**
- Each user ID is associated with exactly one project on the server

**What happens instead:**
- When a different user needs to use the app, the current user must **log out**
- The new user then logs in with their credentials
- The new user is bound to their (possibly different) project
- See **Scenario 49** for user switching behavior

**Previous (Incorrect) Assumption:**
The earlier version of this document assumed users could switch projects. This was incorrect. Users are permanently associated with a single project and cannot access other projects.

---

### SCENARIO 16: Hard Deadline - No Override Possible

**Conditions:**
- `currentTime > hardDeadline`
- Any user action
- Any network state

**System Behavior:**
```
LOGOUT IS FORCED
NO USER ACTION CAN PREVENT IT
```

**User Experience:**
- Immediate logout
- All session data cleared
- Must reauthenticate from scratch

---

## State Transitions

### Normal Lifecycle

```
INITIAL
    ↓ (login successful)
LOGGED_IN (Token Valid)
    ↓ (token expires)
GRACE_PERIOD (Soft Expiry)
    ↓ (6 hours pass OR user reauths)
    ├─→ HARD_LOGOUT (deadline reached)
    └─→ LOGGED_IN (reauth successful)
```

### State Transition Matrix

| From State | To State | Trigger | Server Required? |
|------------|----------|---------|------------------|
| INITIAL | LOGGED_IN | Successful login | Yes |
| LOGGED_IN | GRACE_PERIOD | Token expires | N/A |
| GRACE_PERIOD | LOGGED_IN | Reauth successful | Yes |
| GRACE_PERIOD | HARD_LOGOUT | 6 hours elapsed | No |
| Any | LOGGED_OUT | User logout / Hard deadline | Varies |
| LOGGED_OUT | LOGGED_IN | Successful login | Yes |

---

## Edge Cases

### Edge Case 1: Clock Change / Timezone Travel

**Scenario:** User changes device time or travels across timezones

**Impact:**
- `currentTime` comparisons may be affected
- Grace period could end prematurely or extend unexpectedly

**Current Handling:** No explicit handling for time changes

**Potential Issue:** If user sets clock backward, grace period could extend indefinitely

---

### Edge Case 2: Server Intermittent Connectivity

**Scenario:** Server alternates between reachable and unreachable during grace period

**Behavior:**
```
Check 1 (t=0): Server reachable → Show prompt → User clicks "Work Offline"
Check 2 (t=5min): Server unreachable → No prompt
Check 3 (t=10min): Server reachable → Show prompt again
```

**Result:** User may see multiple prompts depending on server stability

---

### Edge Case 3: App Killed During Grace

**Scenario:** App is force-killed by user or system during grace period

**Behavior:**
- `_isSoftExpiry` flag is NOT persisted
- On app restart, grace period is re-evaluated
- If still within grace window and server reachable → Prompt shown again

---

### Edge Case 4: Multiple Projects with Different Expiry Times

**Scenario:** Project A token expired 2 hours ago, Project B token expires in 1 hour

**Behavior:**
- Each project maintains independent auth state
- Switching to Project A may trigger reauth if server reachable
- Switching to Project B shows no prompt

---

### Edge Case 5: Grace Period Expires During Active Session

**Scenario:** User is actively filling a form when grace period ends

**Behavior:**
- Grace period checked on foreground transitions
- If user stays in app continuously, check may not trigger immediately
- Next foreground event (navigation, background/foreground) will trigger logout

**Potential Issue:** User could lose form data if not auto-saved

---

### Edge Case 6: Very Short Grace Period (Testing)

**Scenario:** Grace period modified to <1 minute for testing

**Behavior:**
- All logic still applies
- Hard deadline reached quickly
- May cause rapid forced logouts during testing

---

### Edge Case 7: Token Expires Exactly at Hard Deadline

**Scenario:** Edge case where `currentTime == hardDeadline`

**Behavior:**
```kotlin
if (currentTime > hardDeadline) {
    logout()
}
// At exactly hardDeadline, still in grace period
```

**Note:** The comparison uses `>` not `>=`, so exact deadline still allows access

---

## Summary Table

| Scenario | Token | Grace Window | Server | Outcome | Prompt? |
|----------|-------|--------------|--------|---------|---------|
| 1 | Valid | N/A | ✓ | Normal access | No |
| 2 | Valid | N/A | ✗ | Normal access | No |
| 3 | Valid | N/A | Offline | Normal access | No |
| 4 | Expired | 0-6h | ✓ | Soft expiry | **Yes** |
| 5 | Expired | 0-6h | ✗ | Silent grace | No |
| 6 | Expired | 0-6h | Offline | Silent grace | No |
| 7 | Expired | >6h | ✓ | **Hard logout** | N/A |
| 8 | Expired | >6h | ✗ | **Hard logout** | N/A |
| 9 | Expired | >6h | Offline | **Hard logout** | N/A |

---

## Implementation Notes

### Key Files
- `AiimsAuthManager.kt:checkTokenExpiry()` - Main logic
- `RealAuthClient.kt:checkReachability()` - Server check
- `AiimsAppLock.kt:onActivityResumed()` - Trigger point
- `AiimsLoginActivity.kt` - Reauth UI

### Constants
```kotlin
const val GRACE_PERIOD_MS = 6L * 60 * 60 * 1000  // 6 hours
const val EXPIRATION_THRESHOLD_MS = 24L * 60 * 60 * 1000  // 24 hours
const val REACHABILITY_TIMEOUT_MS = 5_000L  // 5 seconds
```

### Storage Keys
```kotlin
"auth_token_$projectId"
"expires_at_$projectId"
"user_data_$projectId"
"api_url_$projectId"
```

---

## ULTRA-DEEP ANALYSIS: Advanced Scenarios

This section covers complex scenarios involving multiple interacting variables, race conditions, and edge cases.

---

### 24-Hour Warning Scenarios (EXPIRING_SOON State)

#### SCENARIO 17: Token Expiring Within 24 Hours (Not Yet Expired)

**Conditions:**
- `tokenExpiryTime - currentTime <= 24 hours`
- `currentTime <= tokenExpiryTime` (still valid)
- Server reachable or unreachable

**System Behavior:**
```kotlin
_isExpiringSoon.value = true
_authState.value = AuthState.LOGGED_IN
_isSoftExpiry.value = false
```

**User Experience:**
- No forced prompts
- Token is still valid, app works normally
- Warning state for potential UI indicators (not currently implemented)

**Key Difference:** This is NOT the same as grace period - token is still valid.

---

#### SCENARIO 18: Token Within 24 Hours of Expiry + User Enters Grace Period

**Conditions:**
- Token was already in "expiring soon" state
- Token expires, entering grace period
- Server reachable

**State Transition:**
```
LOGGED_IN + _isExpiringSoon=true
    ↓ (token expires)
GRACE_PERIOD + _isSoftExpiry=true + _isExpiringSoon=false
```

**User Experience:**
1. Warning state ends
2. Reauth prompt immediately shown (if server reachable)
3. User must choose an action

---

### API Call Behavior During Grace Period

#### SCENARIO 19: API Call During Grace Period (Server Reachable)

**Conditions:**
- Token in grace period (expired)
- User attempts to submit a form/fetch data
- Server reachable

**Expected Behavior:**
```kotlin
// API call should include Authorization header
// Server will reject with 401 Unauthorized
// Client should handle 401 gracefully
```

**User Experience:**
- API call fails with 401
- May trigger reauth prompt
- User should reauthenticate before retrying

**Current Implementation Gap:** No explicit handling of 401 responses to trigger reauth.

---

#### SCENARIO 20: API Call During Grace Period (Server Unreachable)

**Conditions:**
- Token in grace period
- User attempts API call
- Server unreachable

**Expected Behavior:**
- Network error (timeout, no connection)
- No 401 (server not reached)

**User Experience:**
- Grace continues silently
- API call fails with network error
- User can work offline with local data

---

#### SCENARIO 21: API Call During Silent Grace (User Chose "Work Offline")

**Conditions:**
- Token in grace period
- User previously clicked "Work Offline"
- `_isSoftExpiry = false`
- User attempts API call

**Expected Behavior:**
- API call made with expired token
- Server returns 401
- Client may or may not trigger reauth

**Current Gap:** Silent grace doesn't prevent API calls, which will fail with 401.

---

### Race Conditions and Timing

#### SCENARIO 22: Token Expires During Active API Call

**Conditions:**
- API call initiated at T-1 second before expiry
- Token expires while call is in flight
- Response arrives at T+5 seconds (after expiry)

**Behavior:**
```kotlin
// Timeline:
T-1s: API call initiated with valid token
T0: Token expires (local check may trigger grace)
T+5s: API response arrives
```

**Possible Outcomes:**
1. **Server accepts** (server clock behind client, or clock skew tolerance)
2. **Server returns 401** (token already expired on server)

**Current Handling:** No special handling for this race condition.

---

#### SCENARIO 23: Reachability Check Completes After Hard Deadline

**Conditions:**
- Grace period active
- Reachability check initiated at T-1 second before deadline
- Hard deadline passes during check
- Check completes at T+5 seconds

**Behavior:**
```kotlin
// Timeline:
T-1s: checkReachability() initiated
T0: Hard deadline reached → logout() should execute
T+5s: Reachability check completes
```

**Race Condition:**
- If `logout()` executes first: User logged out, reachability result ignored
- If check completes first: May set `_isSoftExpiry = true`, then immediately logged out

**Current Handling:** No synchronization between these operations.

---

#### SCENARIO 24: Multiple Concurrent Reachability Checks

**Conditions:**
- Grace period active
- User rapidly switches activities
- Multiple `onActivityResumed()` callbacks fire

**Behavior:**
```kotlin
// Multiple reachability checks running simultaneously
check1: launch { checkReachability() }
check2: launch { checkReachability() } // First one still running
check3: launch { checkReachability() } // First two still running
```

**Possible Issues:**
- Wasted network resources
- Last check to complete determines state
- User may see prompt flicker

**Current Handling:** No deduplication or debouncing of concurrent checks.

---

### Network State Transitions During Grace

#### SCENARIO 25: Network Connects During Grace Period

**Conditions:**
- Device offline when grace period starts
- `_isSoftExpiry = false` (silent grace)
- User working offline
- Network becomes available

**Behavior:**
```kotlin
// Does app detect network change and recheck grace period?
// Currently: No automatic recheck on network change
```

**User Experience:**
- Silent grace continues
- Prompt only shown on next foreground transition
- Could stay in silent grace indefinitely until app restart

**Potential Issue:** User could have server access but never be prompted to reauth.

---

#### SCENARIO 26: Network Disconnects During Active Grace Prompt

**Conditions:**
- Grace period active, server reachable
- Reauth prompt shown to user
- User is considering options
- Network disconnects

**Behavior:**
```kotlin
// Prompt already shown, network state changed
// Does prompt dismiss automatically?
// Currently: No, prompt stays visible
```

**User Experience:**
- User sees reauth prompt
- If they try to login, it will fail (no network)
- If they click "Work Offline", it works (network not needed)

---

#### SCENARIO 27: Server Becomes Reachable During Silent Grace

**Conditions:**
- Grace period started with server unreachable
- Silent grace active (`_isSoftExpiry = false`)
- Server becomes reachable

**Behavior:**
```kotlin
// Does app detect server recovery?
// Currently: No automatic detection
```

**User Experience:**
- Silent grace continues
- User not prompted until next foreground event
- Grace period continues to tick down

---

### PIN and Biometric Interactions

#### SCENARIO 28: PIN Set + Grace Period + Server Reachable

**Conditions:**
- User has PIN set
- Token in grace period
- Server reachable
- App comes to foreground

**Priority Flow:**
```kotlin
// AiimsAppLock evaluates in order:
if (isSoftExpiry) {
    // Reauth takes priority over PIN
    startActivity(AiimsLoginActivity with is_reauth=true)
} else if (hasPIN) {
    startActivity(PinEntryActivity)
}
```

**User Experience:**
1. Reauth prompt shown (not PIN screen)
2. Reauth takes priority
3. After reauth, PIN may still be required on next foreground

---

#### SCENARIO 29: Biometric Auth + Grace Period

**Conditions:**
- User has biometric auth enabled
- Token in grace period
- Server reachable

**Behavior:**
```kotlin
// Similar to PIN - reauth takes priority
// Biometric auth bypassed when grace period active
```

**User Experience:**
- Biometric prompt NOT shown
- Reauth prompt shown instead
- After reauth, biometric works normally

---

### Telemetry Worker Behavior

#### SCENARIO 30: Telemetry Worker During Grace Period

**Conditions:**
- Telemetry worker scheduled (runs periodically)
- Token enters grace period
- Worker fires during grace

**Expected Behavior:**
```kotlin
// Worker makes API call with expired token
// Server returns 401
// Worker handles failure?
```

**Current Behavior:**
- Telemetry worker continues running
- API calls will fail with 401
- Failures handled silently (presumably)

---

#### SCENARIO 31: Telemetry Worker + Silent Grace

**Conditions:**
- Silent grace active (server unreachable)
- Telemetry worker fires

**Behavior:**
- Network error (server unreachable)
- Telemetry submission fails
- Worker retries later

---

#### SCENARIO 32: Hard Deadline Reached + Telemetry Worker

**Conditions:**
- Telemetry worker active
- Hard deadline reached
- `logout()` executed

**Behavior:**
```kotlin
telemetryWorker.cancel() // Called during logout
```

**Result:** Worker cancelled, no further telemetry submissions.

---

### Form and Data Operations

#### SCENARIO 33: Filling Form When Grace Period Expires

**Conditions:**
- User filling a long form
- Token valid when form opened
- Grace period expires (6 hours passed) during form entry

**Behavior:**
```kotlin
// Grace check on next activity transition
// If user stays on same screen, no immediate check
```

**User Experience:**
1. User can continue filling form (no immediate interruption)
2. Next navigation/background-foreground triggers check
3. Hard logout → Form data may be lost

**Risk:** Data loss if form not auto-saved.

---

#### SCENARIO 34: Form Submission During Grace Period

**Conditions:**
- User completes form
- Token in grace period
- User clicks "Submit"

**Expected Behavior:**
```kotlin
// Submit button → API call
// Token expired → 401 response
// Show error?
```

**Current Behavior:**
- Submission fails with auth error
- User should be prompted to reauthenticate
- After reauth, form can be resubmitted

**Gap:** No clear error handling for this scenario.

---

### Server-Side Scenarios

#### SCENARIO 35: Server-Side Token Revocation During Grace

**Conditions:**
- Token in grace period
- Server admin revokes token
- User attempts operation

**Behavior:**
```kotlin
// Client thinks token is in grace period
// Server has revoked token
// API returns 401
```

**User Experience:**
- Client may still show grace period state
- API calls fail with 401
- No automatic detection of server-side revocation

**Gap:** Client relies on local expiry time, doesn't detect server-side revocation.

---

#### SCENARIO 36: Server Maintenance Mode (503) During Reauth

**Conditions:**
- Token in grace period
- Server reachable (returns 503 Service Unavailable)
- User tries to reauthenticate

**Behavior:**
```kotlin
// /version.txt may return 503
// Login endpoint may return 503
```

**User Experience:**
- Reachability check may fail (interpreted as unreachable)
- Or reachability succeeds but login fails with 503
- User can choose "Work Offline"

---

#### SCENARIO 37: API Rate Limiting (429) During Reauth

**Conditions:**
- Token in grace period
- Server returns 429 Too Many Requests
- User tries to reauthenticate

**Behavior:**
```kotlin
// Login endpoint returns 429
// Should retry after delay
```

**User Experience:**
- Login fails with rate limit error
- User must wait before retrying
- Can choose "Work Offline" in meantime

---

### Multiple Device Scenarios

#### SCENARIO 38: Same User on Multiple Devices

**Conditions:**
- User logged in on Device A
- User logs in on Device B
- Device A token expires, enters grace period

**Server Behavior:**
```kotlin
// Unknown: Does server invalidate old tokens on new login?
// If yes: Device A may get 401 even during grace period
```

**Current Assumption:** Tokens are independent per device (no invalidation).

---

### Storage and Data Integrity

#### SCENARIO 39: Corrupted Token Data

**Conditions:**
- Token storage corrupted
- App attempts to read token/expiry

**Behavior:**
```kotlin
// What happens if stored token is invalid/missing?
// Should treat as logged out
```

**Current Handling:**
- Likely crashes or treats as logged out
- Should have explicit corruption handling

---

#### SCENARIO 40: Storage Migration During Grace

**Conditions:**
- Token in grace period
- App update migrates storage format
- Old expiry format → New expiry format

**Risks:**
- Expiry time may be misinterpreted
- Grace period may end prematurely or extend

**Mitigation:** Careful migration with backward compatibility.

---

### App Lifecycle Scenarios

#### SCENARIO 41: App Update During Grace Period

**Conditions:**
- Token in grace period
- User updates app
- New version installed

**Behavior:**
```kotlin
// Storage preserved across app updates
// Grace period state recalculated on first launch
```

**User Experience:**
- Grace period continues
- On first launch of new version, grace re-evaluated
- May show prompt if server reachable

---

#### SCENARIO 42: App Data Cleared During Grace

**Conditions:**
- Token in grace period
- User clears app data (Settings → Apps → Clear Data)

**Behavior:**
```kotlin
// All storage cleared
// Token, expiry, user data deleted
// Effectively logged out
```

**User Experience:**
- Must login from scratch
- Grace period irrelevant (data gone)

---

#### SCENARIO 43: App Reinstall During Grace

**Conditions:**
- Token in grace period
- User uninstalls app
- User reinstalls app

**Behavior:**
```kotlin
// All storage cleared on uninstall
// No recovery possible
```

**User Experience:**
- Must login from scratch
- No grace period restoration

---

### Clock and Time Scenarios

#### SCENARIO 44: Device Clock Set Backward

**Conditions:**
- Token in grace period (2 hours expired)
- User sets device clock back 3 hours

**Calculations:**
```kotlin
realTime: T+2h (grace active)
deviceTime: T-1h (token appears valid!)
currentTime <= tokenExpiryTime → treated as valid
```

**Impact:**
- Grace period effectively extended
- Hard deadline may never be reached
- Security vulnerability

**Current Gap:** No detection of backward time changes.

---

#### SCENARIO 45: Device Clock Set Forward

**Conditions:**
- Token still valid (not expired)
- User sets device clock forward 7 hours

**Calculations:**
```kotlin
realTime: T-1h (token valid)
deviceTime: T+6h (past grace period!)
currentTime > hardDeadline → logout
```

**Impact:**
- Premature forced logout
- User loses access even though token is valid

**Current Gap:** No detection of forward time changes.

---

#### SCENARIO 46: Client-Server Clock Skew

**Conditions:**
- Device clock 5 minutes behind server
- Token expiry calculated locally

**Impact:**
```kotlin
// Server sees token as expired 5 minutes earlier
// Client thinks token is still valid
// API calls fail with 401
```

**Current Handling:** No clock synchronization.

---

### Deep Link and Notification Scenarios

#### SCENARIO 47: Deep Link Opens App During Grace

**Conditions:**
- Token in grace period
- User taps deep link (e.g., from email)
- App opens to specific screen

**Behavior:**
```kotlin
// AiimsAppLock:onActivityResumed() triggered
// Grace period check runs
```

**User Experience:**
1. Deep link target screen starts to open
2. Reauth prompt may interrupt
3. User must handle reauth before accessing deep link content

---

#### SCENARIO 48: Notification Tap During Grace

**Conditions:**
- Token in grace period
- User taps notification
- App opens

**Behavior:**
```kotlin
// Similar to deep link
// Grace check on resume
```

**User Experience:**
- Reauth prompt shown before notification content
- May disrupt user workflow

---

### Shared Device Scenarios

#### SCENARIO 49: User Switching on Same Device (Multiple Users, Same/Different Projects)

**Conditions:**
- User A logged in, token in grace period
- User A logs out
- User B logs in (same device, different user)

**Behavior:**
```kotlin
// Only ONE user can be logged in at a time
// When User B logs in, User A is completely logged out
```

**User Experience:**
- User A's session is terminated (including grace period state)
- User B starts with fresh authentication
- Grace period is NOT preserved across user switches

**AppUser Constraint Implications:**
- **Single Active Session**: Only one user-project pair can be logged in at any time
- **Multiple Users Per Project**: Multiple users CAN share the same project, but NOT simultaneously
- **Project Binding**: Each user is permanently bound to their project

**Shared Device Examples:**

**Example 1: Same Project, Different Users**
```
Scenario: Alice and Bob both use Project A on shared device

Timeline:
1. Alice logs in → alice@example.com + Project A (grace period starts)
2. Alice logs out → Session cleared
3. Bob logs in → bob@example.com + Project A (fresh session)
   - Bob's grace period is independent
   - Alice's grace period state is lost
```

**Example 2: Different Projects**
```
Scenario: Alice (Project A) and Charlie (Project B) share device

Timeline:
1. Alice logs in → alice@example.com + Project A
2. Alice logs out → Session cleared
3. Charlie logs in → charlie@example.com + Project B
   - Different project (Charlie is bound to Project B)
   - Fresh authentication required
   - Alice's grace period state is lost
```

**Key Point:**
- User switching is a **complete session replacement**
- No state preservation between users
- Grace period resets for each new user login

---

### Emergency and Bypass Scenarios

#### SCENARIO 50: Emergency Access Mode

**Conditions:**
- Token expired past grace period
- Emergency situation requires app access

**Current Behavior:**
```kotlin
// No emergency bypass implemented
// Hard logout is enforced
```

**Gap:** No mechanism for emergency access extension.

---

### ~~AppUser Constraint Scenarios (51-55)~~ **ALL INVALID**

**Status:** Scenarios 51-55 are **NO LONGER VALID** due to the AppUser constraint.

**Reason:** These scenarios all assumed users could switch projects or attempt to access multiple projects. Under the AppUser constraint:

- Users are **permanently bound to ONE project**
- Users **CANNOT switch projects** under any circumstances
- Users **CANNOT attempt to access multiple projects**
- The server enforces this constraint

**What happens instead:**
- If a user is bound to Project A, they can ONLY access Project A
- To "switch" projects, the user must log out and a different user (bound to a different project) must log in
- This is **user switching**, not **project switching**
- See **Scenario 49** for user switching behavior

**Summary of Invalid Scenarios:**

| # | Original Scenario | Why Invalid |
|---|-------------------|-------------|
| 51 | Same user attempts multiple project access | User bound to one project permanently |
| 52 | Project switch during grace period | Users cannot switch projects |
| 53 | Concurrent project access attempts | Users cannot access multiple projects |
| 54 | Server-side user transfer between projects | User-project binding is permanent |
| 55 | Multiple users, same project interactions | This is now covered in Scenario 49 |

**Remaining Valid AppUser Consideration:**
- **Scenario 49** covers the valid case: Multiple users sharing a device (same or different projects)

---

### Summary of Ultra-Deep Scenarios

| # | Category | Scenario | Key Issue |
|---|----------|----------|-----------|
| 17-18 | Warning State | 24h expiring warning | Separate from grace period |
| 19-21 | API Calls | API calls during grace | 401 handling not explicit |
| 22-24 | Race Conditions | Concurrent checks | No synchronization |
| 25-27 | Network Changes | Network state changes | No automatic recheck |
| 28-29 | PIN/Biometric | Secondary auth interaction | Reauth takes priority |
| 30-32 | Telemetry | Worker during grace | Continues, fails silently |
| 33-34 | Forms | Form submission during grace | Data loss risk |
| 35-37 | Server State | Server-side revocation | No detection mechanism |
| 38 | Multi-Device | Multiple devices | Token independence assumed |
| 39-40 | Storage | Corruption/migration | Needs explicit handling |
| 41-43 | App Lifecycle | Update/reinstall scenarios | State preservation varies |
| 44-46 | Time Issues | Clock changes/skew | No detection (vulnerability) |
| 47-48 | External Triggers | Deep links/notifications | Reauth interrupts workflow |
| 49 | Shared Device | User switching (not project switching) | Per-user isolation, no state preservation |
| 50 | Emergency | Emergency bypass | Not implemented |
| ~~15~~ | **INVALID** | Project switching | Users cannot switch projects |
| ~~51-55~~ | **INVALID** | Project switching scenarios | Users bound to one project |

### AppUser Constraint: Key Implications

Since users **cannot switch projects** (permanently bound to one project), the following simplifications apply:

1. **No Project Switching Logic**: Client doesn't need to handle project switching within a session
2. **User Switching Only**: When a different user needs the app, current user logs out, new user logs in
3. **Simplified Authorization**: User can only access their permanently assigned project
4. **No 403 for Project Switching**: 403 errors would only occur for server-side configuration issues, not normal operation

**Valid User Flow:**
```
User A (bound to Project A) → Uses app → Logs out
User B (bound to Project A or B) → Logs in → Uses app → Logs out
User C (bound to their project) → Logs in → ...
```

---

## Recommendations

### Critical Issues

1. **Clock Change Detection** (Security)
   - Detect backward time changes to prevent grace period extension
   - Detect forward time changes to prevent premature logout
   - Use monotonic clock or server time sync

2. **401 Response Handling** (UX)
   - Global interceptor for 401 responses → Trigger grace period flow
   - Queue failed requests for retry after reauth
   - Since users cannot switch projects, 403 handling is simplified

3. **User Switching Handling** - **UPDATED**
   - Clear all session data when user logs out
   - No state preservation between user switches
   - Each new user login starts fresh (no grace period carryover)

4. **Form Data Protection** (Data Loss)
   - Auto-save form drafts
   - Warn before forced logout with unsaved changes
   - Preserve draft across logout if possible

### Important Improvements

5. **Reachability Check Optimization**
   - Deduplicate concurrent checks
   - Persist last check result with timestamp
   - Recheck on network state changes

6. **Grace State Persistence**
   - Persist `_isSoftExpiry` flag
   - Restore state after app kill
   - Track prompt dismissals

7. **Server-Side Revocation Detection**
   - Validate token with server periodically
   - Detect revocation during grace period
   - Handle unexpected 401s

### Nice to Have

8. **Emergency Bypass**
   - Admin override for forced logout
   - Time-limited emergency extension
   - Audit log of emergency access

9. **Better Time Sync**
   - Server time sync on login
   - Clock skew correction
   - NTP validation

10. **Testing Coverage**
    - Unit tests for all 50 valid scenarios
    - Integration tests for race conditions
    - Tests for user switching (not project switching)
    - Manual test scenarios for QA

---

## Implementation Priority Matrix

| Priority | Item | Impact | Effort | Category |
|----------|------|--------|--------|----------|
| P0 | Clock change detection | Critical | High | Security |
| P0 | 401 response handling | Critical | Medium | UX |
| P1 | Form data protection | High | Medium | Data Loss |
| P1 | User switching handling | High | Low | Session Management |
| P2 | Reachability optimization | Medium | Low | Performance |
| P2 | Grace state persistence | Medium | Low | UX |
| P3 | Emergency bypass | Low | Medium | Feature |
| P3 | Better time sync | Low | High | Reliability |

---

## Appendix: Complete Scenario Count

| Category | Scenario Range | Count | Notes |
|----------|----------------|-------|-------|
| Core Scenarios | 1-14, 16 | 15 | Scenario 15 invalid |
| 24-Hour Warning | 17-18 | 2 |  |
| API Call Behavior | 19-21 | 3 |  |
| Race Conditions | 22-24 | 3 |  |
| Network State Changes | 25-27 | 3 |  |
| PIN/Biometric | 28-29 | 2 |  |
| Telemetry Worker | 30-32 | 3 |  |
| Form Operations | 33-34 | 2 |  |
| Server-Side Scenarios | 35-37 | 3 |  |
| Multiple Device | 38 | 1 |  |
| Storage Issues | 39-40 | 2 |  |
| App Lifecycle | 41-43 | 3 |  |
| Clock/Time Issues | 44-46 | 3 |  |
| Deep Links/Notifications | 47-48 | 2 |  |
| Shared Device | 49 | 1 | User switching (not project switching) |
| Emergency Bypass | 50 | 1 |  |
| ~~Project Switching~~ | ~~15, 51-55~~ | ~~0~~ | **INVALID - Users cannot switch projects** |
| **VALID TOTAL** | **1-50 (excluding 15)** | **49** | **50 scenarios minus 1 invalid** |

**Summary:**
- **Total documented scenarios**: 55 (originally numbered 1-55)
- **Invalid scenarios**: 6 (Scenarios 15, 51-55) - assumed project switching which is not possible
- **Valid scenarios**: 49

---

## ULTRA-DEEP ANALYSIS: Missing Scenarios & Critical Gaps

This section identifies scenarios and edge cases NOT covered in the 49 documented scenarios, along with security vulnerabilities and architectural concerns.

---

### Gap Category 1: Time-Related Edge Cases

#### SCENARIO 56: Timezone Change During Grace Period

**Conditions:**
- User in Grace Period (2 hours expired)
- User travels across timezone boundaries
- Device timezone changes automatically

**Impact:**
```kotlin
// Before timezone change:
currentTime = 2025-12-28 14:00 UTC
tokenExpiryTime = 2025-12-28 10:00 UTC
graceRemaining = 4 hours

// After timezone change (UTC → UTC+5):
currentTime = 2025-12-28 19:00 (device shows 14:00 local)
// Internal time is still UTC, so no issue
BUT if app uses local time instead of UTC:
graceRemaining calculations become incorrect
```

**Current Handling:** Unknown - depends on whether app uses UTC or local time

**Risk:** Medium - Could cause premature logout or extended grace period

---

#### SCENARIO 57: Daylight Saving Time Transition During Grace

**Conditions:**
- Token expires near DST transition
- Grace period spans DST "spring forward" or "fall back"

**Impact:**
```kotlin
// Spring forward (clocks jump ahead 1 hour):
Real time elapsed: 7 hours
Device time elapsed: 6 hours (1 hour "disappeared")
If grace period checks device time: Grace extends by 1 hour

// Fall back (clocks jump back 1 hour):
Real time elapsed: 5 hours
Device time elapsed: 6 hours (1 hour "repeated")
If grace period checks device time: Grace ends 1 hour early
```

**Current Handling:** No explicit DST handling documented

**Risk:** Medium - Similar to clock manipulation

---

### Gap Category 2: Network State Race Conditions

#### SCENARIO 58: Network Dropped During Reauthentication

**Conditions:**
- Token in grace period, server reachable
- Reauth prompt shown
- User enters credentials and clicks "Login"
- Network drops during login API call

**Behavior:**
```kotlin
// Timeline:
T0: User submits credentials
T1: Network drops during API call
T2: Request times out or fails
```

**User Experience:**
- Login fails with network error
- User confused - were credentials wrong or network issue?
- Grace period continues (silent grace if server now unreachable)
- User can click "Work Offline" or retry

**Gap:** No clear error messaging to distinguish network failure from auth failure

---

#### SCENARIO 59: Network Restored During Silent Grace

**Conditions:**
- Grace period starts, server unreachable (silent grace)
- User works offline for 2 hours
- Network becomes available
- Server now reachable

**Current Behavior:**
- No automatic recheck on network restoration (documented in Scenario 25)
- Prompt only shown on next foreground transition

**Gap:**
- User could have server access but isn't prompted
- Grace period continues ticking while server is reachable
- User might work online for hours without reauthenticating

**Recommendation:** Re-evaluate grace period when network state changes from unreachable to reachable

---

#### SCENARIO 60: Intermittent Network Causing Prompt Spam

**Conditions:**
- Token in grace period
- Network alternates between reachable and unreachable every few seconds
- App triggers recheck on each network change

**Behavior:**
```kotlin
T0: Network reachable → Show reauth prompt → User clicks "Work Offline"
T30s: Network unreachable → Silent grace
T60s: Network reachable → Show reauth prompt again
T90s: Network unreachable → Silent grace
T120s: Network reachable → Show reauth prompt again
```

**User Experience:**
- Reauth prompt appears repeatedly
- User gets frustrated
- May accidentally reauthenticate when trying to dismiss

**Gap:** No debouncing or cooldown period for reauth prompts

---

### Gap Category 3: Storage and State Issues

#### SCENARIO 61: Storage Write Failure During Token Save

**Conditions:**
- User reauthenticates during grace period
- New token received from server
- Disk full or encrypted storage fails
- Token cannot be saved

**Behavior:**
```kotlin
try {
    authStorage.saveToken(newToken)  // FAILS
} catch (e: Exception) {
    // What happens?
    // 1. User thinks they logged in successfully
    // 2. Token not actually saved
    // 3. Next app launch shows login screen
}
```

**Current Handling:** Unknown - not documented

**Risk:** High - User thinks they authenticated but token wasn't saved

---

#### SCENARIO 62: Partial Storage Corruption

**Conditions:**
- Some auth data corrupted, some intact
- Example: Token valid, expiry_time corrupted

**Behavior:**
```kotlin
token = "valid_token_here"
expiryTime = null or "invalid_date"

// What happens during checkTokenExpiry()?
if (currentTime <= expiryTime) {  // NullPointerException or parse error
    // ??
}
```

**Current Handling:** Not documented

**Risk:** High - Could cause crashes or incorrect grace period state

---

#### SCENARIO 63: Concurrent Logout During Grace Check

**Conditions:**
- Token in grace period
- Grace check running in background (reachability check)
- User clicks "Logout" button simultaneously

**Race Condition:**
```kotlin
// Thread 1: Grace check
if (currentTime > expiryTime) {
    val reachable = checkReachability()  // Running...
}
// Thread 2: User logout
logout()  // Clears token, expiry time
// Thread 1: Grace check completes
if (reachable) {
    _isSoftExpiry.value = true  // But user already logged out!
}
```

**Current Handling:** No synchronization documented

**Risk:** Medium - State inconsistency after logout

---

### Gap Category 4: API Response Edge Cases

#### SCENARIO 64: Malformed 401 Response During Grace

**Conditions:**
- Token in grace period
- User tries API call
- Server returns 401 but with unexpected response format

**Behavior:**
```kotlin
// Expected 401 response:
{
  "error": "Token expired"
}

// Actual 401 response:
"Token expired"  // Plain text, not JSON
// OR
<html>Error page</html>  // HTML error page
// OR
Empty response body
```

**Current Handling:** Global 401 interceptor recommended, but format variations not considered

**Risk:** Medium - 401 handler might fail to parse response

---

#### SCENARIO 65: 401 vs 403 vs 404 During Grace

**Conditions:**
- Token in grace period
- User tries various API calls
- Different endpoints return different errors:

| Endpoint | Response | Meaning |
|----------|----------|---------|
| GET /api/user | 401 | Token expired (expected during grace) |
| GET /api/projects | 403 | Server-side permission issue |
| GET /api/old-endpoint | 404 | Endpoint deprecated |

**Gap:** How should client distinguish between:
- 401 due to expired token (trigger reauth)
- 403 due to permission issues (different error flow)
- 404 due to deprecated API (show different error)

**Risk:** Medium - Wrong error handling for non-expiry errors

---

### Gap Category 5: Logout-Related Scenarios

#### SCENARIO 66: Hard Logout During API Call

**Conditions:**
- User filling form
- Grace period approaches hard deadline
- User clicks "Submit" button
- Hard deadline reached during API call
- logout() called while API call in flight

**Race Condition:**
```kotlin
// Thread 1: API call
apiCall.submitForm(formData)  // In progress...
// Thread 2: Grace checker
if (currentTime > hardDeadline) {
    logout()  // Clears token!
}
// Thread 1: API call completes
// Response: 401 (token was cleared)
```

**User Experience:**
- Form submission fails
- User logged out
- Form data may be lost

**Gap:** No synchronization between logout and in-flight API calls

**Risk:** High - Data loss during hard deadline

---

#### SCENARIO 67: Logout Incomplete Due to Storage Failure

**Conditions:**
- Hard deadline reached
- logout() called
- Token cleared successfully
- User data storage fails (disk full, permission error)

**Behavior:**
```kotlin
logout() {
    authStorage.clearToken()  // Success
    authStorage.clearUserData()  // FAILS
    authState.value = AuthState.LOGGED_OUT  // Success
}
```

**State After Failed Logout:**
```
token: null (cleared)
userData: STILL PRESENT (clear failed)
authState: LOGGED_OUT
```

**Risk:** Medium - Partial data cleanup, privacy issue

---

#### SCENARIO 68: User Logs Out During Grace Period

**Conditions:**
- Token in grace period (2 hours expired)
- User clicks "Logout" button
- Logout flow executes

**Behavior:**
```kotlin
// Normal logout flow:
1. Call server logout endpoint (if reachable)
2. Submit final telemetry
3. Clear local storage
4. Navigate to login screen
```

**Gap:**
- Does user see reauth prompt before logout?
- Should logout be instant or confirm during grace?
- What happens to offline data queued for sync?

**User Experience:** Not clearly documented

---

### Gap Category 6: Telemetry and Background Workers

#### SCENARIO 69: Telemetry Worker Fails Silently During Grace

**Conditions:**
- Telemetry worker fires every 15 minutes
- Token enters grace period
- Worker fires, makes API call with expired token
- Server returns 401

**Behavior:**
```kotlin
// Telemetry worker implementation (presumed):
try {
    api.submitTelemetry(data)
} catch (e: Exception) {
    // Silent failure? Log error? Retry?
}
```

**Gap:**
- Does telemetry worker detect 401?
- Does it trigger reauth flow?
- Is telemetry data queued for later?
- Is user notified of telemetry failure?

**Risk:** Medium - Data loss, compliance issues

---

#### SCENARIO 70: Doze Mode App Kill During Grace

**Conditions:**
- Token in grace period
- App backgrounded
- Android Doze mode kills app process
- User reopens app

**Behavior:**
```kotlin
// App killed, all in-memory state lost
_isSoftExpiry = not persisted (Edge Case 3)
// On app restart:
// Grace period re-evaluated from stored expiry time
```

**Gap:**
- If user had dismissed reauth prompt, dismissal lost
- Prompt shown again on restart
- User experience inconsistent

**Recommendation:** Persist `_isSoftExpiry` flag and last prompt dismissal time

---

### Gap Category 7: Security Vulnerabilities

#### SCENARIO 71: Rooted Device with Clock Hack ~~**RESOLVED**~~

**Status:** ✅ **RESOLVED** (2025-12-28)

**Conditions:**
- User has rooted device
- User uses clock manipulation app
- Token in grace period (5 hours expired)
- User sets device clock back 6 hours

**Calculations:**
```kotlin
// Real time: T+5h (should be in grace period)
// Manipulated time: T-1h (token appears valid)
currentTime (manipulated) = T-1h
tokenExpiryTime = T0
// T-1h < T0, so token appears valid!
```

**Impact:**
- Grace period bypassed
- User can extend token indefinitely
- Security vulnerability

**Resolution:**
1. Created `ClockValidator` class that uses monotonic clock (`SystemClock.elapsedRealtime()`)
2. Stores anchor points (wall-clock time + monotonic time) in encrypted storage
3. Detects time jumps > 30 minutes (backward or forward)
4. Falls back to expected time when manipulation detected
5. Allows grace period continuation but prevents re-authentication until time is corrected

**Current Implementation:**
```kotlin
// ClockValidator usage in AiimsAuthManager:
val timeResult = clockValidator.getCurrentTime()
val currentTime = when (timeResult) {
    is ClockValidator.TimeResult.Valid -> {
        // Time is valid, clear manipulation flag if set
        timeResult.time
    }
    is ClockValidator.TimeResult.ManipulationDetected -> {
        // Show warning, allow grace but prevent re-auth
        _errorMessage.value = "Clock manipulation detected: ${timeResult.reason}"
        timeResult.expectedTime
    }
}
```

**Key Files:**
- `ClockValidator.kt` - New class for clock validation
- `AiimsSecureStorage.kt` - Added clock validation properties
- `AiimsAuthManager.kt` - Integrated ClockValidator
- `AiimsConstants.kt` - Added clock validation constants

**Threshold:** 30 minutes (configurable via `CLOCK_MANIPULATION_THRESHOLD_MS`)

**User Experience:**
- When clock manipulation detected: User sees warning message
- Grace period continues (offline work allowed)
- Re-authentication blocked until time is corrected
- On successful login: Clock synced with server (if available)

---

#### SCENARIO 72: Token Stored in Plain Text ~~**RESOLVED**~~

**Status:** ✅ **RESOLVED** (2025-12-28)

**Original Issue:**
- Tokens were stored in plain SharedPreferences in `AiimsAuthManager`
- `AiimsSecureStorage` existed but was not being used

**Resolution:**
1. Updated `AiimsAuthManager` to use `AiimsAuthStorage` (wraps `AiimsSecureStorage`) for sensitive data
2. Added `projectId` to encrypted storage for token validation
3. Updated Dagger dependency injection to provide `AiimsAuthStorage`

**Current Implementation:**
```kotlin
// Tokens now stored in encrypted storage via AiimsSecureStorage
// - Uses EncryptedSharedPreferences with AES-256-GCM
// - Hardware-backed Android Keystore
// - Note: setUserAuthenticationRequired removed due to lock screen requirement issues

// Sensitive data (encrypted):
// - auth_token
// - token_expiry
// - project_id
// - pin_hash, pin_salt
// - api_url

// Non-sensitive data (regular SharedPreferences):
// - user_id, user_email, user_name
// - is_authenticated
// - last_auth_timestamp
```

**Critical Gotcha: StrictMode ThreadPolicy Violation**

**Problem:** App crashed on startup with `StrictMode ThreadPolicy violation` due to disk I/O during encrypted storage initialization:

```
Crash Sequence:
Collect.onCreate()
→ Dagger creates AiimsAuthManager
→ Constructor calls refreshState() (line 142)
→ refreshState() calls authStorage.getProjectId() (line 171)
→ Triggers lazy initialization of masterKey (line 41)
→ MasterKey.Builder.build() does disk I/O to Android Keystore
→ StrictMode detects disk I/O on main thread → CRASH
```

**Fix:** Suppressed StrictMode during lazy initialization of `masterKey` and `encryptedPrefs`:

```kotlin
// Master key for encryption
// Suppress StrictMode for key generation as it requires disk I/O to Android Keystore
private val masterKey: MasterKey by lazy {
    val oldPolicy = StrictMode.getThreadPolicy()
    try {
        // Temporarily allow disk I/O for key generation
        StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.LAX)
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    } finally {
        StrictMode.setThreadPolicy(oldPolicy)
    }
}

// Encrypted preferences for sensitive data
// Suppress StrictMode for initialization as it may require disk I/O
private val encryptedPrefs: SharedPreferences by lazy {
    val oldPolicy = StrictMode.getThreadPolicy()
    try {
        // Temporarily allow disk I/O for encrypted prefs creation
        StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.LAX)
        EncryptedSharedPreferences.create(
            context,
            AiimsConstants.AIIMS_SECURE_PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } finally {
        StrictMode.setThreadPolicy(oldPolicy)
    }
}
```

**Why This Happens:**
- Android Keystore operations require disk I/O
- `AiimsAuthManager` constructor calls `refreshState()` immediately during Dagger injection
- `refreshState()` accesses encrypted storage properties, triggering lazy initialization
- This all happens on the main thread during app startup
- StrictMode with "death" penalty detects disk I/O and crashes the app

**Alternative Considered but NOT Used:**
- `setUserAuthenticationRequired(true)` - Requires secure lock screen to be enabled
- This would crash on devices without lock screen configured
- Fallback logic added but ultimately removed for simplicity

**Files Modified:**
- `AiimsSecureStorage.kt` - Added `projectId`, StrictMode suppression, removed user auth requirement
- `AiimsAuthStorage.kt` - Added `projectId` property
- `AiimsAuthManager.kt` - Uses encrypted storage for tokens
- `DaggerSetup.kt` - Provides `AiimsAuthStorage` dependency
- `AiimsConstants.kt` - Added `KEY_PROJECT_ID` constant

**Testing:**
- Build verified: `./gradlew :collect_app:assembleAiimsDebug` successful
- Login/logout flow tested successfully
- No tokens in plain SharedPreferences
- Encrypted storage initialization works without StrictMode crash

**References:**
- `data-isolation.md` - Updated documentation
- [collect-cpt] - Beads issue closed

---

### Gap Category 8: UI/UX Edge Cases

#### SCENARIO 73: User Force-Kills Reauth Screen

**Conditions:**
- Token in grace period
- Reauth prompt shown
- User force-kills app from recent apps
- User reopens app

**Behavior:**
```kotlin
// App killed
// On restart:
_isSoftExpiry = not persisted
// Grace check runs again
// Prompt shown again
```

**User Experience:**
- User cannot dismiss prompt by force-killing
- Prompt shown repeatedly
- Frustrating UX

**Gap:** No way for user to extend grace period intentionally

---

#### SCENARIO 74: Multiple Grace Period Prompts Stack

**Conditions:**
- Token in grace period
- Reauth prompt shown (Activity A)
- User receives phone call (Activity B overlays)
- App returns to foreground
- Another reauth check triggers
- Second reauth prompt shown

**Behavior:**
```kotlin
// Activity stack:
[AiimsLoginActivity (reauth)]
[PhoneCallActivity]
[AiimsLoginActivity (reauth)] // Duplicate!
```

**User Experience:**
- Multiple reauth screens in back stack
- User dismisses one, another appears
- Confusing UX

**Gap:** No check for existing reauth screen before launching new one

---

### Gap Category 9: Multi-Device Scenarios

#### SCENARIO 75: Same User on Multiple Devices

**Conditions:**
- User Alice logged in on Device A
- Token valid (not in grace)
- Alice logs in on Device B
- What happens to Device A's session?

**Server Behavior:** Unknown - not documented

**Possibilities:**
1. Server allows multiple simultaneous sessions
2. Server invalidates Device A token when Device B logs in
3. Server maintains both sessions independently

**If Case 2 (Token Invalidation):**
```
Device A: Token appears valid locally
Device A: Grace period check passes (within expiry)
Device A: API call fails with 401 (server invalidated)
```

**Gap:** No handling of server-initiated token invalidation

**Risk:** High - Unexpected logout on Device A

---

#### SCENARIO 76: Device A Grace Period, Device B Reauth

**Conditions:**
- User Alice on Device A: Token in grace period (3 hours expired)
- Alice logs in on Device B: New token obtained
- Alice returns to Device A

**Behavior:**
```
Device A: Old token (3 hours expired, 3 hours grace remaining)
Device B: New token (fresh, 24 hours until expiry)
Server: Which token is valid?
```

**Gap:** No clarity on whether server invalidates old tokens

**Risk:** Medium - Confusion about which device has valid session

---

### Gap Category 10: Integration Scenarios

#### SCENARIO 77: App Update During Hard Logout

**Conditions:**
- Hard deadline reached
- logout() executing
- App update starts simultaneously
- System kills app for update

**Behavior:**
```kotlin
// Partial logout:
authStorage.clearToken()  // Completed
authStorage.clearUserData()  // Interrupted by app kill
// App updates
// App launches
// State: Partial cleanup
```

**Gap:** No atomic operation guarantees for logout

**Risk:** Medium - Partial state after update

---

#### SCENARIO 78: Sync Adapter Conflict with Grace Period

**Conditions:**
- Android SyncAdapter configured for periodic sync
- Token enters grace period
- SyncAdapter triggers sync
- Sync fails with 401

**Behavior:**
```kotlin
// SyncAdapter implementation:
onPerformSync() {
    // Makes API call
    // Gets 401
    // What happens?
    // - Retries with exponential backoff?
    // - Triggers reauth?
    // - Shows notification?
}
```

**Gap:** No integration with Android SyncManager

**Risk:** Low - Sync failure, but not critical

---

### Summary of Missing Scenarios

| # | Scenario | Category | Risk | Status |
|---|----------|----------|------|--------|
| 56 | Timezone change during grace | Time | Medium | Not covered |
| 57 | DST transition during grace | Time | Medium | Not covered |
| 58 | Network dropped during reauth | Network | Medium | Not covered |
| 59 | Network restored during silent grace | Network | Low | Partially covered |
| 60 | Intermittent network causing prompt spam | Network | Low | Not covered |
| 61 | Storage write failure during token save | Storage | High | Not covered |
| 62 | Partial storage corruption | Storage | High | Not covered |
| 63 | Concurrent logout during grace check | Concurrency | Medium | Not covered |
| 64 | Malformed 401 response | API | Medium | Not covered |
| 65 | 401 vs 403 vs 404 distinction | API | Medium | Partially covered |
| 66 | Hard logout during API call | Concurrency | High | Not covered |
| 67 | Logout incomplete due to storage failure | Storage | Medium | Not covered |
| 68 | User logs out during grace period | UX | Low | Not covered |
| 69 | Telemetry worker fails silently | Background | Medium | Not covered |
| 70 | Doze mode app kill during grace | Lifecycle | Medium | Partially covered |
| 71 | Rooted device with clock hack | Security | **Critical** | ✅ **Resolved** |
| 72 | Token stored in plain text | Security | **Critical** | ✅ **Resolved** |
| 73 | User force-kills reauth screen | UX | Low | Partially covered |
| 74 | Multiple grace prompts stack | UX | Low | Not covered |
| 75 | Same user on multiple devices | Multi-device | High | Not covered |
| 76 | Device A grace, Device B reauth | Multi-device | Medium | Not covered |
| 77 | App update during hard logout | Integration | Medium | Not covered |
| 78 | Sync adapter conflict | Integration | Low | Not covered |

---

### Critical Security Vulnerabilities Identified

| # | Vulnerability | Impact | Mitigation | Status |
|---|--------------|--------|------------|--------|
| 1 | Clock manipulation extends grace | Critical | Use monotonic clock, server time validation | ✅ **Resolved** |
| 2 | Plain text token storage | Critical | Ensure encrypted storage works correctly | ✅ **Resolved** |
| 3 | No 401 detection on API calls | High | Global 401 interceptor | Not covered |
| 4 | Hard logout during API call loses data | High | Sync logout with API operations | Not covered |
| 5 | Multi-device token invalidation | High | Detect and handle server-initiated invalidation | Not covered |

---

### Updated Recommendations

### ~~New Critical Items~~ (UPDATED - C1 and C2 Resolved)

**~~C1. Clock Manipulation Detection~~** ~~(Security - CRITICAL)~~ ✅ **RESOLVED**
- Implemented using monotonic clock (`SystemClock.elapsedRealtime()`)
- Detects time jumps > 30 minutes (backward or forward)
- Stores anchor points in encrypted storage
- Allows grace period continuation but prevents re-authentication

**~~C2. Token Storage Security Audit~~** ~~(Security - CRITICAL)~~ ✅ **RESOLVED**
- Tokens now stored in encrypted storage via `AiimsSecureStorage`
- Uses EncryptedSharedPreferences with AES-256-GCM
- Hardware-backed Android Keystore

**C3. 401 Interceptor with Queue** (UX - High)
- Global OkHttp interceptor for 401 responses
- Queue failed requests during reauthentication
- Retry queued requests after successful reauth
- Distinguish 401 (token) from 403 (permission) errors

**C4. Atomic Logout Operations** (Data Loss - High)
- Synchronize logout with in-flight API calls
- Wait for or cancel pending operations before clearing data
- Use transactions for storage operations (all-or-nothing)
- Show warning if logout cannot complete

### Updated Priority Matrix

| Priority | Item | Impact | Effort | Category |
|----------|------|--------|--------|----------|
| ~~P0~~ | ~~Clock manipulation detection~~ | ~~Critical~~ | ~~High~~ | ~~Security~~ |
| ~~P0~~ | ~~Token storage security audit~~ | ~~Critical~~ | ~~Medium~~ | ~~Security~~ |
| P0 | 401 interceptor with queue | High | Medium | UX |
| P0 | Atomic logout operations | High | High | Data Loss |
| P1 | Network state recheck | Medium | Low | UX |
| P1 | Grace state persistence | Medium | Low | UX |
| P1 | Multi-device session handling | High | High | Architecture |
| P2 | Debounce reauth prompts | Low | Low | UX |
| P2 | Telemetry error handling | Medium | Medium | Background |

---

### Total Scenario Count (Updated)

| Category | Count |
|----------|-------|
| Previously documented valid scenarios | 49 |
| **New missing scenarios identified** | **23** |
| **New total scenarios to document** | **72** |
| Critical security scenarios | 2 |
| High-risk scenarios | 5 |
| Medium-risk scenarios | 10 |
| Low-risk scenarios | 6 |

---

### Gap Category 11: PIN Setup and Authentication Flow Security

#### SCENARIO 79: PIN Setup Bypass via Back Button ~~**RESOLVED**~~

**Status:** ✅ **RESOLVED** (2025-12-28)

**Conditions:**
- User clicks "Forgot PIN" → logged out, PIN cleared
- User logs in again
- `SetupPinActivity` starts (PIN setup required)
- User presses BACK button
- User returns to main app **without setting PIN**

**Attack Flow:**
```
1. User in MainMenuActivity, PIN set
2. User clicks "Forgot PIN" → logoutDueToFailedPin() called
3. Redirected to AiimsLoginActivity
4. User logs in successfully
5. authState becomes LOGGED_IN (PIN not set yet)
6. SetupPinActivity starts
7. User presses BACK
8. Returns to MainMenuActivity (was in back stack)
9. AiimsAppLock sees LOGGED_IN but PIN not set → does nothing
10. USER ACCESSES APP WITHOUT PIN!
```

**Root Cause:**
1. **Primary**: `AiimsAppLock.kt:60-62` - When `authState == LOGGED_IN` but PIN NOT set, no action taken
2. **Secondary**: `SetupPinActivity` had no `onBackPressed()` override
3. **Tertiary**: `navigateToPinSetup()` missing `FLAG_ACTIVITY_CLEAR_TASK`

**Impact:**
- **Severity**: Critical (P0)
- **CVSS**: 7.5 (High)
- **Exploitability**: Trivial (button press)
- Users could skip required two-factor authentication
- Multi-tenant security: On shared devices, users could access each other's data

**Resolution:**

1. **Added new AuthState**: `LOGGED_IN_REQUIRES_PIN`
```kotlin
enum class AuthState {
    INITIAL,
    LOGGED_IN,
    LOGGED_IN_REQUIRES_PIN,  // NEW: Must set PIN before accessing app
    LOGGED_OUT,
    ERROR
}
```

2. **Updated `AiimsLoginActivity.attemptLogin()`**: Set correct auth state based on reauth flag
```kotlin
if (isReauthMode) {
    // Category A: Token Refresh - PIN should already be set
    if (pinManager.isPinSet()) {
        navigateToMain()
    } else {
        navigateToPinSetup(result.token, result.expiresAt)
    }
} else {
    // Category B: Fresh Login / Forgot PIN / Logout
    authManager.updateAuthState(AuthState.LOGGED_IN_REQUIRES_PIN)
    navigateToPinSetup(result.token, result.expiresAt)
}
```

3. **Updated `AiimsAppLock.onActivityStarted()`**: Handle `LOGGED_IN_REQUIRES_PIN`
```kotlin
if (authState == AuthState.LOGGED_IN_REQUIRES_PIN) {
    val intent = Intent(application, SetupPinActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
    }
    application.startActivity(intent)
    return
}
```

4. **Added `SetupPinActivity.onBackPressed()`**: Block BACK button
```kotlin
override fun onBackPressed() {
    // Prevent bypass - user must complete PIN setup
    AlertDialog.Builder(this)
        .setTitle(getString(R.string.aiims_pin_required_title))
        .setMessage(getString(R.string.aiims_pin_required_message))
        .setPositiveButton(getString(R.string.aiims_button_ok), null)
        .setCancelable(false)
        .show()
}
```

**Re-authentication Flows Covered:**

| Flow | Trigger | PIN State | Auth State |
|------|---------|-----------|------------|
| Token Refresh (3 places) | `EXTRA_IS_REAUTH=true` | Preserved | `LOGGED_IN` |
| Forgot PIN | Button click | Cleared | `LOGGED_IN_REQUIRES_PIN` |
| Logout | Settings logout | Cleared | `LOGGED_IN_REQUIRES_PIN` |
| First-time login | Initial login | None | `LOGGED_IN_REQUIRES_PIN` |

**Files Changed:**
- `aiims-auth-module/src/main/java/org/aiims/odk/auth/managers/AiimsAuthManager.kt` - Added `LOGGED_IN_REQUIRES_PIN` to AuthState enum
- `aiims-auth-module/src/main/java/org/aiims/odk/auth/activities/AiimsLoginActivity.kt` - Set correct state based on `EXTRA_IS_REAUTH` flag
- `aiims-auth-module/src/main/java/org/aiims/odk/auth/utils/AiimsAppLock.kt` - Handle `LOGGED_IN_REQUIRES_PIN` (force PIN setup)
- `aiims-auth-module/src/main/java/org/aiims/odk/auth/activities/SetupPinActivity.kt` - Added `onBackPressed()` override
- `aiims-auth-module/src/main/res/values/strings.xml` - Added dialog strings

**Verification:**
- Build verified: `./gradlew assembleAiimsDebug` ✅
- BACK button: Shows "PIN Required" dialog, prevents exit ✅
- HOME button: `AiimsAppLock` redirects back to PIN setup on resume ✅

**Bonus Fix**: Fixed Time Card overlapping PIN entry boxes in `SetupPinActivity`
- Changed `pin_hint` constraint from `user_text_view` to `time_warning_card`

**References:**
- Beads issue: `collect-s0d` (closed)
- GitHub issue: https://github.com/drguptavivek/collect/issues/67 (closed)
- Git commit: `62e99dc5af`

---

### Updated Scenario Count (2025-12-28)

| Category | Count |
|----------|-------|
| Previously documented valid scenarios | 49 |
| **New missing scenarios identified** | **23** |
| **New fixed scenarios** | **1** |
| **New total scenarios** | **73** |
| Critical security scenarios (resolved) | 3 |
| High-risk scenarios | 5 |
| Medium-risk scenarios | 10 |
| Low-risk scenarios | 6 |

### Updated Security Vulnerabilities Status

| # | Vulnerability | Impact | Mitigation | Status |
|---|--------------|--------|------------|--------|
| 1 | Clock manipulation extends grace | Critical | Use monotonic clock, server time validation | ✅ **Resolved** |
| 2 | Plain text token storage | Critical | Ensure encrypted storage works correctly | ✅ **Resolved** |
| 3 | PIN setup bypass via BACK button | Critical | New AuthState, AppLock handler, onBackPressed block | ✅ **Resolved** |
| 4 | No 401 detection on API calls | High | Global 401 interceptor | Not covered |
| 5 | Hard logout during API call loses data | High | Sync logout with API operations | Not covered |
| 6 | Multi-device token invalidation | High | Detect and handle server-initiated invalidation | Not covered |
