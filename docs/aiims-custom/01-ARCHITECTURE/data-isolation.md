# AIIMS Preferences & Persistence
> Last Updated: 2025-12-28
> Reviewed At: 2025-12-28

This document explains how the AIIMS customization manages data persistence, bridging custom AIIMS requirements with standard ODK Collect storage.

## Overview

The AIIMS customization uses a dual-layer storage approach:
1.  **AIIMS Encrypted Storage** (`aiims_auth_secure`): Hardware-backed encrypted storage for sensitive authentication data (tokens, PINs).
2.  **AIIMS Regular Storage** (`aiims_auth_prefs`): Standard SharedPreferences for non-sensitive data and project metadata.
3.  **Standard ODK Storage**: Leverages standard ODK SharedPreferences to ensure core features (Forms, Submissions) function correctly while being constrained by AIIMS rules.

---

## 1. AIIMS Encrypted Storage (`aiims_auth_secure`)

Sensitive authentication data is stored using Android's `EncryptedSharedPreferences` with `AES256_GCM` encryption via the hardware-backed Android Keystore.

### Encrypted Data
- **`auth_token`**: The Bearer Token (JWT) for the currently active project.
- **`token_expiry`**: Token expiry timestamp (milliseconds since epoch).
- **`project_id`**: The Central Project ID for token validation.
- **`pin_hash`**: PBKDF2 hash of the 4-digit security PIN.
- **`pin_salt`**: Unique salt used for hashing.
- **`pin_attempts`**: Integer counting consecutive failed PIN entries.
- **`last_pin_attempt`**: Timestamp of the last PIN attempt.
- **`biometric_enabled`**: Whether biometric auth is enabled.
- **`biometric_key_alias`**: Key alias for biometric authentication.
- **`api_url`**: The base URL of the Central server.

### Security Properties
- **Encryption**: AES-256-GCM for values, AES-256-SIV for keys
- **Key Storage**: Android Keystore with hardware backing when available
- **User Authentication**: Device unlock required to access the key
- **Key Scheme**: AES256_GCM

### Implementation
- **File**: `AiimsSecureStorage.kt`
- **API**: AndroidX Security Crypto (`EncryptedSharedPreferences`, `MasterKey`)

---

## 2. AIIMS Regular Storage (`aiims_auth_prefs`)

Non-sensitive data is stored in standard SharedPreferences.

### Authentication State
- **`is_authenticated`**: Boolean flag indicating authentication state.
- **`last_auth_timestamp`**: Timestamp of the last successful authentication.

### User Information (Non-Sensitive)
- **`user_id`**: User ID for reference.
- **`user_email`**: User email/username.
- **`user_name`**: User display name.

### Per-Project Metadata (Multi-Project Support)
The following keys are scoped per-project using the project ID suffix:
- **`user_data_$projectId`**: JSON string containing user details (ID, Username).
- **`api_url_$projectId`**: The base URL of the Central server for this project.

### Project Mapping
- **`central_to_odk_$centralPid`**: Maps an AIIMS/Central Project ID to the internal ODK Project UUID. This is critical for data isolation.
- **`project_name_$projectId`**: Cached project name for display.

---

## 3. Standard ODK Storage Integration

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

## 4. Storage Security

> [!IMPORTANT]
> **Hardware-Backed Encryption**: All sensitive data (tokens, PINs) are stored in `aiims_auth_secure` using Android's `EncryptedSharedPreferences` with hardware-backed Android Keystore.
> **User Authentication Required**: The encryption key requires device unlock to be accessed (`setUserAuthenticationRequired(true)`).
> **No Plaintext Secrets**: No sensitive authentication data is stored in plain text.
> **Token Validation**: Token in encrypted storage includes `project_id` for validation - tokens are only used if they match the active project.

### Security Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    Security Layers                          │
├─────────────────────────────────────────────────────────────┤
│  Sensitive Data (Tokens, PINs)                              │
│    ↓                                                         │
│  AiimsSecureStorage → EncryptedSharedPreferences             │
│    ↓                                                         │
│  Android Keystore (Hardware-backed)                         │
│    ↓                                                         │
│  AES-256-GCM Encryption                                     │
│    ↓                                                         │
│  User Authentication Required (Device Unlock)               │
└─────────────────────────────────────────────────────────────┘
```

---

## 5. Token Lifecycle & Encryption

1. **Login**:
   - Token received from server
   - Stored in `AiimsSecureStorage` (encrypted)
   - `project_id` stored alongside token for validation

2. **Token Usage**:
   - Retrieved from encrypted storage
   - Validated against active `project_id`
   - Injected into API requests via OkHttp interceptor

3. **Logout**:
   - `AiimsAuthStorage.clearAuthData()` called
   - All sensitive data cleared from encrypted storage
   - Non-sensitive metadata preserved in regular SharedPreferences

---

## 6. Lifecycle & Cleanup

- **Logout**:
    - Clears all sensitive data from `aiims_auth_secure`
    - Clears user data from `aiims_auth_prefs`
    - Preserves project mapping for data isolation
    - **Note**: ODK project data (forms/instances) is **preserved** to support shared device scenarios, but is inaccessible without a valid login to that project.
- **Failed PIN**:
    - After 3 failed attempts, the app performs a local logout, wiping the session tokens and PIN.

---

## Related Files
- [AiimsSecureStorage.kt](../../aiims-auth-module/src/main/java/org/aiims/odk/auth/storage/AiimsSecureStorage.kt)
- [AiimsAuthStorage.kt](../../aiims-auth-module/src/main/java/org/aiims/odk/auth/storage/AiimsAuthStorage.kt)
- [AiimsAuthManager.kt](../../aiims-auth-module/src/main/java/org/aiims/odk/auth/managers/AiimsAuthManager.kt)
- [PinManager.kt](../../aiims-auth-module/src/main/java/org/aiims/odk/auth/utils/PinManager.kt)
- [AiimsLoginActivity.kt](../../aiims-auth-module/src/main/java/org/aiims/odk/auth/activities/AiimsLoginActivity.kt)
