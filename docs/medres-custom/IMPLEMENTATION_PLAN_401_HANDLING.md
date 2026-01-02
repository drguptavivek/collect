# Architecture: MEDRES-Auth 401 Handling & Retry (collect-49l)

## Overview
This document describes the final architecture for handling HTTP 401 (Unauthorized) responses within the MEDRES-specific authentication and telemetry flows.

## Core Design Principle: Isolation
To preserve the integrity of the core ODK Collect modules (specifically `open-rosa`), 401 interception and automatic re-authentication are strictly isolated to the `medres-auth` layer. 

- **ODK Core APIs**: Requests for forms, submissions, etc., are **not** intercepted. If they receive a 401, they propagate the error according to standard ODK behavior.
- **MEDRES APIs**: Requests for telemetry, project info, etc., handled via `RealAuthClient` are intercepted globally.

## Implementation Details

### 1. Global Interceptor (`medres-auth-module`)
- **`AuthInterceptor.kt`**: An OkHttp interceptor that catches 401 status codes.
- **Retry Logic**: When a 401 is detected, the interceptor calls `MedresAuthManager.awaitReauthentication()`. 
- **Resumption**: If re-authentication succeeds (user enters PIN), the original request is updated with the new token and retried automatically.

### 2. Coordination (`MedresAuthManager.kt`)
- **`reauthMutex`**: Ensures that multiple concurrent 401s from different background tasks (e.g., synchronous telemetry and project refresh) trigger only a single re-authentication UI prompt.
- **`reauthDeferred`**: Used to notify all waiting requests once the user has successfully re-authenticated or cancelled.

### 3. Re-use of UI
- Re-authentication utilizes the existing `MedresLoginActivity` in a specialized "re-auth mode" (pre-filled username, localized messaging). This ensures a consistent security experience without duplicating complex biometric/PIN logic.

## Verification
The implementation has been verified to:
- Successfully retry MEDRES API calls after re-authentication.
- Not interfere with standard ODK form management.
- Be compatible with `minSdkVersion 21` (Room 2.6.1).
