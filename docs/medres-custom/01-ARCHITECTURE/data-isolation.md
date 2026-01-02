# MEDRES Preferences & Persistence
> Last Updated: 2025-12-31
> Reviewed At: 2025-12-31

This document explains how the MEDRES customization manages data persistence, bridging custom MEDRES requirements with standard ODK Collect storage.

## Overview

The MEDRES customization uses a dual-layer storage approach:
1.  **MEDRES Encrypted Storage** (`medres_auth_secure`): Hardware-backed encrypted storage for sensitive authentication data (tokens, PINs).
2.  **MEDRES Regular Storage** (`medres_auth_prefs`): Standard SharedPreferences for non-sensitive global state and project-specific metadata.
3.  **Standard ODK Storage**: Leverages standard ODK SharedPreferences to ensure core features (Forms, Submissions) function correctly while being constrained by MEDRES rules.

---

## 1. MEDRES Encrypted Storage (`medres_auth_secure`)

Sensitive authentication data is stored using Android's `EncryptedSharedPreferences` with `AES256_GCM` encryption via the hardware-backed Android Keystore.

### Encrypted Data (Global)
- **`auth_token`**: The Bearer Token (JWT) for the currently active project.
- **`token_expiry`**: Token expiry timestamp (milliseconds since epoch).
- **`project_id`**: The Central Project ID for token validation.
- **`pin_hash`**: PBKDF2 hash of the 4-digit security PIN.
- **`pin_salt`**: Unique salt used for hashing.
- **`pin_attempts`**: Integer counting consecutive failed PIN entries.
- **`last_pin_attempt`**: Timestamp of the last PIN attempt.
- **`last_valid_wall_time`**: Last known "true" wall time from API response.
- **`api_url`**: The base URL of the Central server.

### Security Properties
- **Encryption**: AES-256-GCM for values, AES-256-SIV for keys.
- **Key Storage**: Android Keystore with hardware backing when available.
- **Key Scheme**: AES256_GCM.

---

## 2. MEDRES Regular Storage (`medres_auth_prefs`)

Non-sensitive data is stored in standard SharedPreferences. This file contains both global state and project-specific metadata.

### Global Authentication State
- **`is_authenticated`**: Boolean flag indicating authentication state.
- **`last_auth_timestamp`**: Timestamp of the last successful authentication.
- **`active_project_id`**: Currently active Central project ID.
- **`first_launch`**: Initial setup flag.

### Per-Project Metadata (Multi-Project Support)
The following keys are scoped per-project using the `$pid` suffix:
- **`user_data_$pid`**: JSON string containing user details (ID, Username).
- **`api_url_$pid`**: The base URL of the Central server for this specific project.
- **`project_name_$pid`**: Cached project name for display.
- **`central_to_odk_$pid`**: Maps an MEDRES/Central Project ID to the internal ODK Project UUID.

---

## 3. Standard ODK Storage Integration

The MEDRES customization "drives" standard ODK settings to maintain compatibility.

### Meta Settings (`meta`)
- **`current_project_id`**: MEDRES logic sets this to the ODK UUID corresponding to the active MEDRES project.
- **`metadata_installid`**: Standard ODK Install ID, used as `deviceId` in MEDRES telemetry and API calls.
- **`projects`**: Standard ODK project repository.

### Project Specific Settings (`general_prefs[UUID]`)
When a user logs in via MEDRES:
- **`server_url`**: Tokenized URL format: `<BaseURL>/key/<BearerToken>/projects/<PID>`.
- **`protocol`**: Hardcoded to `odk_default`.
- **`project_name`**: Automatically updated from Central project metadata.

---

## 4. Storage Security

> [!IMPORTANT]
> **Hardware-Backed Encryption**: All sensitive data (tokens, PINs) are stored in `medres_auth_secure` using Android's `EncryptedSharedPreferences`.
> **No Plaintext Secrets**: No sensitive authentication data (JWTs, PIN hashes) is stored in regular preferences.
> **Isolation**: Project-specific metadata ensures that switching projects immediately shifts the security context.

---

## 5. Token Lifecycle & Encryption

1. **Login**: Token received from server, stored in `MedresSecureStorage` (encrypted). `project_id` stored alongside for validation.
2. **Usage**: Retrieved from encrypted storage, validated against active project, injected into API requests via OkHttp interceptor.
3. **Logout**: `MedresAuthStorage.clearAuthData()` called. All sensitive data cleared from encrypted storage. Non-sensitive metadata (like project names) preserved for project switching history.

---

## 6. Security Cleanup (Logout/Wipe) Behavior

When a security event occurs (manual logout, 3 failed PIN attempts, or hard expiry), the application clears the sensitive security context but preserves project data to support **Shared Device** scenarios.

| Category | Item | Action | Rationale |
|----------|------|--------|-----------|
| **Security** | Auth Token (JWT) | ✅ **Wiped** | Prevents unauthorized API access. |
| **Security** | Security PIN | ✅ **Wiped** | Forces next user to set their own PIN. |
| **Security** | Session State | ✅ **Wiped** | Clears `is_authenticated` and `active_project_id`. |
| **Security** | Clock State | ✅ **Wiped** | Resets `last_valid_wall_time` to prevent replay. |
| **User Data** | User Profile | ✅ **Wiped** | Clears `user_data_$pid` (ID, Username). |
| **Project Data** | Blank Forms | ❌ **Preserved** | Bandwidth efficiency; shared team access. |
| **Project Data** | Saved Instances | ❌ **Preserved** | Team visibility; shared device drafts. |
| **Project Data** | Submitted History | ❌ **Preserved** | Local audit trail for device users. |
| **Configuration** | Project URL/Name | ❌ **Preserved** | Simplifies re-login for the next user. |
| **Configuration** | ODK Settings | ❌ **Preserved** | Maintains ODK core stability. |

---

## Related Files
- [MedresSecureStorage.kt](../../medres-auth-module/src/main/java/edu.aiims.medresodk.auth/storage/MedresSecureStorage.kt)
- [MedresAuthStorage.kt](../../medres-auth-module/src/main/java/edu.aiims.medresodk.auth/storage/MedresAuthStorage.kt)
- [MedresAuthManager.kt](../../medres-auth-module/src/main/java/edu.aiims.medresodk.auth/managers/MedresAuthManager.kt)
- [PinManager.kt](../../medres-auth-module/src/main/java/edu.aiims.medresodk.auth/utils/PinManager.kt)
- [MedresLoginActivity.kt](../../medres-auth-module/src/main/java/edu.aiims.medresodk.auth/activities/MedresLoginActivity.kt)
- [vg-user-behaviour.md](../../.wiki/vg-user-behaviour.md)
