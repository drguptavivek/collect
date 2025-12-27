# AIIMS Collect PIN Behavior Documentation

This document describes the PIN security behavior in AIIMS Collect.

---

## Overview

The app uses a PIN-based lock mechanism (`AiimsAppLock`) to protect access when the app returns from the background.

---

## When is PIN Required?

| Scenario | PIN Required? | Reason |
|----------|---------------|--------|
| App reopened after **home button** | ✅ Yes | App was backgrounded |
| App reopened after **back button** | ✅ Yes | App was backgrounded |
| Navigation **within app** | ❌ No | App never left foreground |
| User is **not logged in** | ❌ No | No session to protect |
| **PIN not configured** | ❌ No | Nothing to verify |
| **Soft expiry** (token expired, server reachable) | 🔄 Login screen | Needs re-authentication |

---

## How It Works

### Technical Implementation (`AiimsAppLock.kt`)

```
┌─────────────────────────────────────────────────────────┐
│                   Activity Lifecycle                     │
├───────────────────┬─────────────────────────────────────┤
│ onActivityStarted │ startedActivities++                 │
│                   │ If shouldRequirePin → Launch PIN    │
├───────────────────┼─────────────────────────────────────┤
│ onActivityStopped │ startedActivities--                 │
│                   │ If counter == 0 → shouldRequirePin  │
└───────────────────┴─────────────────────────────────────┘
```

1. **Counter-based tracking**: Counts active activities
2. **Background detection**: When counter reaches 0, app is in background
3. **PIN trigger**: On next activity start, if logged in + PIN set → show PIN entry

---

## Auth Flow Bypass

The following activities bypass PIN requirement to prevent redirect loops:

- `AiimsLoginActivity`
- `PinEntryActivity`
- `SetupPinActivity`
- `ChangePinActivity`
- `AuthSettingsActivity`

---

## PIN Setup Flow

1. User logs in successfully
2. If no PIN set → Redirect to `SetupPinActivity`
3. User creates 4-6 digit PIN
4. PIN hash stored securely with salt

---

## PIN Entry Failure

| Failed Attempts | Action |
|-----------------|--------|
| 1-4 | Show remaining attempts |
| 5 | Force logout + clear session |

---

## Related Files

- [AiimsAppLock.kt](../aiims-auth-module/src/main/java/org/aiims/odk/auth/utils/AiimsAppLock.kt) - Lock mechanism
- [PinEntryActivity.kt](../aiims-auth-module/src/main/java/org/aiims/odk/auth/activities/PinEntryActivity.kt) - PIN entry UI
- [PinManager.kt](../aiims-auth-module/src/main/java/org/aiims/odk/auth/utils/PinManager.kt) - PIN storage
- [AiimsAppLockTest.kt](../aiims-auth-module/src/test/java/org/aiims/odk/auth/utils/AiimsAppLockTest.kt) - Unit tests
