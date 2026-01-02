# Protected Security Scenarios

This document details the security protection mechanisms implemented in the MEDRES ODK Collect fork. These safeguards ensure data integrity, session security, and resilience in field environments.

---

## 1. Clock Integrity & Grace Period
**Scenario**: An offline user manually changes the device time to stay within the 6-hour grace period and avoid a hard logout.

- **Protection**: **Clock Drift Detection & Hardware Sync**.
- **Implementation**: The system performs a hardware/server time synchronization check. It captures the server time from HTTP headers during successful logins/re-authentications and compares it against the local device clock.
- **Outcome**: If a significant drift or manual manipulation is detected, the app restricts further re-authentication and triggers a proactive security lock until the clock is corrected.
- **References**: `collect-a55`, `MedresAuthManagerTest.kt#grace period is re-evaluated when network restores`.

## 2. PIN & App-Lock Resilience
**Scenario**: Attempting to bypass the PIN entry screen using the system "Back" button or by force-stopping and cold-starting the app.

- **Protection**: **Un-skippable Lifecycle Interception**.
- **Implementation**: `ActivityLifecycleCallbacks` monitor the app's foreground state. Any attempt to resume the app from the background or a cold start triggers a mandatory `PinEntryActivity`.
- **Outcome**: The user is blocked from the main menu until a valid PIN is entered. The "Back" button on the PIN screen moves the app to the background rather than dismissing the lock.
- **References**: `collect-s0d`, `collect-0kd`, `MedresAppLockTest.kt`.

## 3. Shared-Device Isolation
**Scenario**: User A logs out on a shared device; User B logs in but attempts to use User A's previously set PIN or access their secure session tokens.

- **Protection**: **User-Scoped Security Clearing**.
- **Implementation**: The `MedresAuthManager` detects if the user ID has changed during a login or re-auth event.
- **Outcome**: If the user changes, the local PIN is **automatically cleared**. Secure tokens are isolated within `EncryptedSharedPreferences`, and previous session hashes are purged.
- **Note**: Per the shared-device policy, form data (Drafts/Filled) is preserved across users, but identity is strictly isolated.
- **References**: `collect-3yt`, `MedresAuthManagerTest.kt#login clears PIN when user changed`.

## 4. Project & QR Bypass Protection
**Scenario**: Using the standard ODK "Add Project" feature or scanning a generic ODK QR code to bypass the MEDRES authenticated environment.

- **Protection**: **Hardened Entry Points**.
- **Implementation**: The ODK "Projects" modal and "Add Project" UI elements are disabled. All QR code scanning is intercepted by the MEDRES Auth Layer.
- **Outcome**: Standard QR codes that omit MEDRES-specific metadata are rejected or redirected to the MEDRES Login flow, ensuring no unauthenticated "backdoors" exist.
- **References**: `collect-df0`, `collect-92k`, `collect-cc3`.

## 5. System Resilience & Error Handling
**Scenario**: A catastrophic failure occurs during a security wipe (e.g., storage corruption, disk full, or a network race condition).

- **Protection**: **Triple-Fail-Safe Logout**.
- **Implementation**: 
    1. **Retries**: The logout/wipe process retries storage operations three times.
    2. **Memory Purge**: Regardless of disk state, the in-memory session is force-cleared.
    3. **Cancellation**: In-flight reachability checks and API calls are immediately cancelled to prevent race conditions (Scenario 63).
- **Outcome**: Even if the secure storage fails to persist the "logged out" state, the app remains in a safe, unauthenticated memory state.
- **References**: `collect-40v`, `MedresAuthManagerTest.kt#logout cancels reachability check`, `MedresAuthManagerTest.kt#logout handles storage failure`.

## 6. Global 401 Interception
**Scenario**: A network request fails with an unauthorized (401) error in the background while the user is filling a form.

- **Protection**: **Global Auth Interceptor**.
- **Implementation**: A centralized interceptor catches all 401 responses and pauses the execution of subsequent requests.
- **Outcome**: Telemetry and data sync requests are queued or dropped gracefully, and the user is navigated to the re-authentication screen without losing their current form progress.
- **References**: `collect-49l`, `MedresAuthManagerTest.kt#submitTelemetry queues on 401 AuthError`.

## 7. Rate Limiting & DoS Protection
**Scenario**: A compromised script or a UI glitch triggers a flood of authentication or telemetry calls.

- **Protection**: **Client-Side Throttling**.
- **Implementation**: The `MedresAuthManager` implements a tiered rate-limiting window for sensitive API calls.
- **Outcome**: Rapid, repeated calls are debounced or rejected with a backoff error before hitting the network, protecting server resources and battery life.
- **References**: `collect-zhh`.

## 8. Concurrent Operation Sync
**Scenario**: Multiple background workers (Reachability, Telemetry, Sync) attempt to refresh tokens or log out at the same microsecond.

- **Protection**: **Mutex-Based Synchronization**.
- **Implementation**: All sensitive state transitions in `MedresAuthManager` are protected by a shared Kotlin Mutex.
- **Outcome**: Operations are linearized, preventing "Double Logout" or corrupted token states where one job clears the session while another is writing a new token.
- **References**: `collect-63g`, `MedresAuthManagerTest.kt#logout cancels reachability check`.

## 9. Storage Corruption Resilience
**Scenario**: The Android `EncryptedSharedPreferences` or the Telemetry Room DB becomes corrupted or enters a "Write-Only" state due to system failure.

- **Protection**: **State Self-Healing**.
- **Implementation**: The app detects `SecurityException` or `SQLiteException` during auth operations.
- **Outcome**: Instead of crashing continuously, the app performs an "Emergency Direct Clear" (Factory Reset for the Auth Layer), ensuring the user can at least re-login from a clean state.
- **References**: `collect-40v`, `MedresAuthManagerCorruptionTest.kt`.
