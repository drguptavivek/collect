# Implementation Plan: Handle 401 in Telemetry Worker (collect-5zw)

## Goal
Gracefully handle 401 (Unauthorized) responses from the telemetry API. According to `telemetry.md`, a 401 response means **no telemetry was recorded** on the server. Therefore, the app must queue the data for later submission.

## Technical Details

### 401 vs Invalidated
- **HTTP 401**: Token is >2 days past expiry. **No telemetry is recorded on server.** We MUST queue in Room DB. The auth state will be handled by other processes (e.g., login activity or session interceptor).
- **HTTP 200 + status:"invalidated"**: Token is <2 days past expiry. **Telemetry IS recorded on server.** We should NOT queue (to avoid duplicates). The auth state will be handled by other processes.

## Proposed Changes

### 1. API Refactor (`aiims-auth-module`)
- **[MODIFY] `AuthClient.kt`**: Update `submitTelemetry` signature to return a sealed `TelemetryResult`.
- **[NEW] `TelemetryResult.kt`**:
    ```kotlin
    sealed class TelemetryResult {
        data class Success(val response: TelemetryResponse) : TelemetryResult()
        object AuthError : TelemetryResult() // 401
        data class Error(val message: String, val code: Int? = null) : TelemetryResult() // 5xx, 4xx (non-401)
        object NetworkError : TelemetryResult()
    }
    ```
- **[MODIFY] `RealAuthClient.kt`**: Implement new return type, mapping HTTP 401 to `AuthError`.

### 2. Manager Updates
- **[MODIFY] `AiimsAuthManager.kt`**:
    - Update `submitTelemetry` to handle `TelemetryResult`.
    - On `AuthError` (401):
        - Ensure request is **queued** in database.
        - Log the error but **do not logout**.
    - On `Success` with `invalidated` status:
        - Log the status but **do not logout**.

### 3. Worker Updates
- **[MODIFY] `TelemetryWorker.kt`**:
    - Ensure it doesn't delete from Room DB on `AuthError`.
    - Handle retry logic for `NetworkError`.

## Verification Plan

### Automated Tests
- **`AiimsAuthManagerTest.kt`**: Add test cases for 401 error during immediate submission and background flush.

### Manual Verification
1. **Force 401**: 
    - Mock a 401 in `FakeAuthClient`.
    - Attempt telemetry submission.
    - Verify telemetry entry remains in `TelemetryEntity` table (not deleted).
    - Verify user session remains active (no logout from telemetry).
2. **Re-auth and Flush**:
    - Simulate a successful token refresh (or login).
    - Verify worker (or manual flush) sends the previously queued 401 data successfully.
