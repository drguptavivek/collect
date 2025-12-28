# AIIMS Preferences & Persistence

This document explains how the AIIMS customization manages data persistence, bridging custom AIIMS requirements with standard ODK Collect storage.

## Overview

The AIIMS customization uses a dual-layer storage approach:
1.  **AIIMS Specific Storage**: Dedicated SharedPreferences for authentication, security (PIN), and cross-project mapping.
2.  **Standard ODK Storage**: Leverages standard ODK SharedPreferences to ensure core features (Forms, Submissions) function correctly while being constrained by AIIMS rules.

---

## 1. AIIMS Specific Storage (`aiims_auth_prefs`)

All AIIMS-specific data is stored in a private SharedPreference file named `aiims_auth_prefs`.

### Authentication & Session
- **`active_project_id`**: Stores the **Central Project ID** (Int/String) of the currently selected AIIMS project.
- **`auth_token_$projectId`**: The Bearer Token (JWT) for the specific project.
- **`user_data_$projectId`**: JSON string containing user details (ID, Username).
- **`expires_at_$projectId`**: UTC ISO timestamp of when the token expires.
- **`api_url_$projectId`**: The base URL of the Central server for this project.

### PIN Security
- **`pin_hash`**: PBKDF2 hash of the 4-digit security PIN.
- **`pin_salt`**: Unique salt used for hashing.
- **`pin_updated_at`**: Timestamp of the last PIN update.
- **`pin_attempts`**: Integer counting consecutive failed PIN entries.

### Project Mapping
- **`central_to_odk_$centralPid`**: Maps an AIIMS/Central Project ID to the internal ODK Project UUID. This is critical for data isolation.

---

## 2. Standard ODK Storage Integration

The AIIMS customization "drives" standard ODK settings to maintain compatibility.

### Meta Settings (`meta`)
- **`current_project_id`**: AIIMS logic sets this to the ODK UUID corresponding to the active AIIMS project.
- **`metadata_installid`**: Standard ODK Install ID, used as `deviceId` in AIIMS telemetry and API calls.
- **`projects`**: Standard ODK project repository; AIIMS adds/removes projects here during login/logout.

### Project Specific Settings (`general_prefs[UUID]`)
When a user logs in via AIIMS:
- **`server_url`**: Forced to the AIIMS Central URL (e.g., `https://central.example.com/v1/projects/1`).
- **`protocol`**: Forced to `odk_default`.
- **`project_name`**: Automatically updated based on the project name fetched from the AIIMS backend.

---

## 3. Storage Security

> [!IMPORTANT]
> **No Plaintext PINs**: The 4-digit PIN is never stored in plaintext. It is hashed using PBKDF2 with a salt.
> **Token Storage**: Bearer tokens are stored in private SharedPreferences. While private to the app, they are not hardware-encrypted in the current implementation.

---

## 4. Lifecycle & Cleanup

- **Logout**:
    - Clears all keys in `aiims_auth_prefs` for the specific project.
    - Clears the global `pin_hash` and `pin_salt`.
    - **Note**: ODK project data (forms/instances) is **preserved** to support shared device scenarios, but is inaccessible without a valid login to that project.
- **Failed PIN**:
    - After 3 failed attempts, the app performs a local logout, wiping the session tokens and PIN.

---

## Related Files
- [AiimsAuthManager.kt](../../aiims-auth-module/src/main/java/org/aiims/odk/auth/managers/AiimsAuthManager.kt)
- [PinManager.kt](../../aiims-auth-module/src/main/java/org/aiims/odk/auth/utils/PinManager.kt)
- [AiimsLoginActivity.kt](../../aiims-auth-module/src/main/java/org/aiims/odk/auth/activities/AiimsLoginActivity.kt)
