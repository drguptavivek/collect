# Telemetry System

The MEDRES ODK Collect telemetry system is designed for high-fidelity event tracking in offline-first environments. It ensures that security events and device health metrics are captured and eventually synchronized with the server, even with intermittent connectivity.

---

## 1. Data Collection & Location
- **Device Health**: Captures battery level, storage availability, and system clock drift.
- **Security Events**: Tracks failed PIN attempts, manual logouts, and token refreshes.
- **Location Context**: If permissions are granted, the system captures Latitude/Longitude for every telemetry payload to provide geographic context for field operations.

## 2. Offline Persistence (Room DB)
When the device is offline or the server is unreachable, telemetry payloads are not discarded.
- **Implementation**: The system uses a dedicated Room database (`MedresDatabase`) with a `TelemetryEntity` table.
- **Queueing Logic**: If an immediate API submission fails (due to network error or 5xx server error), the JSON payload is serialized and stored locally.
- **Resilience**: The queue is persistent across app restarts and system reboots.
- **References**: `collect-2px`, `collect-a22`, `TelemetryDao`, `TelemetryEntity`.

## 3. Background Synchronization
- **WorkManager Integration**: A `TelemetryWorker` is responsible for periodic synchronization.
- **Frequency**: The worker attempts to flush the offline queue every **15 minutes** (while the device has network connectivity).
- **Backoff Policy**: Uses an exponential backoff policy for failed synchronization attempts.

## 4. Unauthorized (401) Handling
- **Graceful Failure**: If the telemetry API returns a **401 Unauthorized** error, the system recognizes that the session has expired or been invalidated.
- **Logic**: Instead of immediately logging the user out (which could disrupt form filling), the telemetry request is **re-queued** for the next authenticated session, and the re-authentication flow is triggered globally.
- **References**: `collect-5zw`, `MedresAuthManager#submitTelemetry`.

## 5. API Resilience
- **Status Filtering**: 
    - **4xx Errors** (except 401): Payloads are dropped to prevent poison-pill requests from blocking the queue.
    - **5xx/Network Errors**: Payloads are retained and retried.
- **Batching**: The system supports future expansion for batching multiple events into a single compressed payload.

---

## Technical Reference
- **Client**: `AuthClient.submitTelemetry(...)`
- **Manager**: `MedresAuthManager.submitTelemetry(...)`
- **Worker**: `edu.aiims.medresodk.auth.workers.TelemetryWorker`
