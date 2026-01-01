# App-Lifecycle & Security Locking

Maintaining a secure session requires constant monitoring of the application state. The AIIMS ODK Collect fork implements a robust lifecycle-aware locking mechanism to prevent unauthorized access when the device is left unattended.

---

## 1. Lifecycle-Aware App Lock
- **Implementation**: The `AiimsAppLock` class implements `Application.ActivityLifecycleCallbacks`.
- **Logic**: It tracks the number of "started" activities. When the count drops to zero, the application is considered to be in the **background**.
- **Lock Trigger**: Immediately upon the app returning to the foreground (transitioning from 0 to 1 started activity), the system triggers the security lock.
- **Outcome**: A mandatory `PinEntryActivity` (or `AiimsLoginActivity` if the token has expired) is launched, covering the entire UI.
- **References**: `collect-s2g`, `AiimsAppLock.kt`.

## 2. Cold Start Security
- **Scenario**: The user force-stops the app or the OS kills the process.
- **Protection**: On fresh initialization, the `AiimsAuthManager` checks the current `AuthState`.
- **Policy**: If the user was previously logged in, the app **requires a PIN immediately** before showing the main menu, regardless of the previous background duration.
- **References**: `collect-0kd`.

## 3. Re-Authentication Debouncing
- **Scenario**: Multiple background jobs or UI components detect a session expiry simultaneously.
- **Protection**: **Auth-Prompt Debouncing**.
- **Logic**: The system uses a flag to track if a re-authentication prompt (PIN or Login) is already visible.
- **Outcome**: Prevents the "stacked dialog" or "flicker" effect where multiple login screens open on top of each other.
- **References**: `collect-36a`, `collect-86z`.

## 4. Activity State Preservation
- **Shared Session**: The PIN lock state is shared across all activities in the task stack.
- **Bypass Prevention**: Any activity that is NOT the `PinEntryActivity` or `AiimsLoginActivity` will automatically redirect to the lock screen if a session lock is active.

---

## Technical Reference
- **Manager**: `AiimsAppLock` (Registered in `DaggerSetup` / `Collect` Application class)
- **UI Interaction**: `PinEntryActivity`, `AiimsLoginActivity`
