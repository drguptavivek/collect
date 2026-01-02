# MEDRES Preferences & Persistence

> Last Updated: 2025-12-31
> Reviewed At: 2025-12-31

This document details the configuration and persistence settings for the MEDRES customization of ODK Collect. It explains how settings are initialized, updated, and managed throughout the application lifecycle.

## 1. Storage Architecture

The application uses a combination of custom MEDRES-specific SharedPreferences and standard ODK Collect preferences.

### MEDRES-Specific Storage
| Preference File | Description | Security |
| :--- | :--- | :--- |
| `medres_auth_prefs` | Stores non-sensitive global state, project metadata, and staged configuration. | Private SharedPreferences |
| `medres_auth_secure` | Stores sensitive data using `EncryptedSharedPreferences`. | AES-256-GCM Encryption |

### Standard ODK Storage
| Preference File | Description | Keys |
| :--- | :--- | :--- |
| `meta` | Core application metadata. | `current_project_id`, `metadata_installid`, `projects` |
| `general_prefs[UUID]` | Settings for a specific ODK project. | `server_url`, `protocol`, `project_name` |

---

## 2. Detailed Preference Key Reference

### A. MEDRES Authentication Preferences (`medres_auth_prefs`)

This file is managed by both `MedresAuthManager` (for project-specific metadata) and `MedresSecureStorageImpl` (for global non-sensitive state).

#### Global State (Non-sensitive)
| Key | Purpose |
| :--- | :--- |
| `is_authenticated` | Simple boolean for UI logic/navigation. |
| `last_auth_timestamp` | Timestamp of the last successful login. |
| `first_launch` | Flag to trigger the setup/QR scanning flow. |
| `active_project_id` | Tracks the currently selected Central Project ID. |

#### Staged Configuration (From QR)
| Key | Purpose |
| :--- | :--- |
| `auth_url` | The base API URL for the Central server. |
| `auth_project_id` | The target Project ID on the Central server. |
| `qr_general_settings` | Raw JSON of ODK settings to apply later. |

#### Project-Specific Metadata
| Key Pattern | Purpose |
| :--- | :--- |
| `user_data_$pid` | JSON blob with user details (ID, Username, Expiry). |
| `api_url_$pid` | The base API URL configured for project `$pid`. |
| `project_name_$pid` | The human-readable name of project `$pid`. |
| `central_to_odk_$pid` | Mapping between Central ID and ODK UUID. |

### B. MEDRES Secure Storage (`medres_auth_secure`)

Encrypted storage for sensitive credentials and security metadata. Managed by `MedresSecureStorageImpl`.

| Key | Purpose |
| :--- | :--- |
| `auth_token` | The Bearer JWT used for all API requests. |
| `token_expiry` | Milliseconds timestamp of when the token expires. |
| `pin_hash` | PBKDF2-HMAC-SHA1 hash of the security PIN. |
| `pin_salt` | Unique salt used for the PIN hash. |
| `pin_attempts` | Counter for failed PIN entries. |
| `last_pin_attempt` | Timestamp of the last failed PIN entry (for lockout). |
| `project_id` | Validates that the token matches the active project. |

#### Clock Validation (Encrypted)
| Key | Purpose |
| :--- | :--- |
| `last_valid_wall_time` | Last known "true" wall time from API response. |
| `last_elapsed_realtime` | Monotonic time anchor for wall time verification. |
| `server_time_offset_ms` | Calculated skew between device and server time. |
| `clock_manipulation_detected` | Flag indicating persistent clock mismatch. |

---

## 3. Preference Lifecycle

### Phase 1: Application Installation & First Launch
On the first launch, the application initializes its base state:
- **`metadata_installid`**: Generated and stored in `meta` SharedPreferences. This serves as the `deviceId` for MEDRES telemetry.
- **`medres_auth_enabled`**: Read from resource values (defined in `build.gradle` flavor configuration).
- **`first_launch`**: Set to `true` in `medres_auth_prefs` to trigger initial configuration flows.

### Phase 2: Configuration (Staged State)
When a user scans a QR code or enters details manually via `MedresLoginActivity`:
- **Staged Details**: Base configuration is stored in `medres_auth_prefs` to enable the login UI without yet creating an ODK project.
- **`auth_url`**, **`auth_project_id`**, and **`qr_general_settings`** are populated.
- **UI State**: The login screen displays the configured project and changes the "Scan QR" button to **"Rescan QR Code"**.

### Phase 3: Login & Project Materialization
Upon successful authentication, the configuration is "materialized" into the standard ODK storage:
- **Tokenized URL**: A specialized URL is constructed and saved to `server_url` in `general_prefs[UUID]`.
  - Format: `<BaseURL>/key/<BearerToken>/projects/<PID>`
- **Project Creation**: A standard ODK project is created/updated in the `meta` prefs `projects` repository.
- **Settings Application**: Properties from `qr_general_settings` (like `form_update_mode`) are mapped to `general_prefs[UUID]`.
- **Identity**: `current_project_id` in `meta` is set to the ODK UUID of the new project.
- **Mapping**: `central_to_odk_$pid` is saved to link the systems.

---

## 4. Security Cleanup (Logout/Wipe) Behavior

When a security event occurs (manual logout, 3 failed PIN attempts, or hard expiry), the application follows an **"Option B" (Shared Device Stability)** model. It clears the sensitive security context but preserves project data to ensure continuity for the next user on the same device.

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

## 5. Related Tracking (Beads)

The MEDRES customization uses an internal issue tracker named **Beads**. References like **`collect-cc3`** or **`collect-mtf`** in logs refer to specific feature requirements in this tracker.

---
