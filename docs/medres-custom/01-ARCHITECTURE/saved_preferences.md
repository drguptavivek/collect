# MEDRES Preferences & Persistence

> Last Updated: 2026-04-10
> Reviewed At: 2026-04-10

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
| `staged_qr_type` | Typed discriminator for staged QR context. Values: `medres_project`, `draft_form`. |
| `auth_url` | Base API URL for MEDRES project login flow. |
| `auth_project_id` | Target Central Project ID for MEDRES project login flow. |
| `auth_project_name` | Persistent project name staged from MEDRES project QR. |
| `auth_username_hint` | Optional username hint shown on login UI. |
| `qr_general_settings` | Raw JSON of non-sensitive ODK project settings to apply after successful login. |
| `qr_admin_settings` | Raw JSON of admin settings to apply after successful login. |
| `draft_url` | Full draft/test URL preserved exactly as scanned. |
| `draft_project_id` | Project ID derived from the draft URL path. |
| `draft_form_id` | Form ID derived from the draft URL path. |
| `draft_display_name` | Draft form label shown in the UI. This is display-only metadata. |
| `draft_display_icon` | Optional draft icon from QR metadata. |
| `draft_general_settings` | General settings bundled with a draft QR. Used only for draft testing context. |

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
- The scanner first classifies the QR into one of two accepted staged contexts:
  - **MEDRES Project QR**: stages `auth_url`, `auth_project_id`, `auth_project_name`, `auth_username_hint`, `qr_general_settings`, and `qr_admin_settings`.
  - **Draft Form QR**: stages `draft_url`, `draft_project_id`, `draft_form_id`, `draft_display_name`, `draft_display_icon`, and `draft_general_settings`.
- **UI State**: The login screen displays the configured project and changes the "Scan QR" button to **"Rescan QR Code"**.
- **No broad wipe**: rescanning clears only session/security keys and staged QR keys. Preserved project mappings and shared-device continuity data remain intact.

### Phase 3: Login & Project Materialization
Upon successful authentication, a staged **MEDRES Project QR** is "materialized" into standard ODK storage:
- **Tokenized URL**: A specialized URL is constructed and saved to `server_url` in `general_prefs[UUID]`.
  - Format: `<BaseURL>/key/<BearerToken>/projects/<PID>`
- **Project Creation**: A standard ODK project is created/updated in the `meta` prefs `projects` repository.
- **Settings Application**: Properties from `qr_general_settings` (like `form_update_mode`) are mapped to `general_prefs[UUID]`.
- **Admin Locks**: Properties from `qr_admin_settings` are applied to the project's admin preferences after key validation.
- **Identity**: `current_project_id` in `meta` is set to the ODK UUID of the new project.
- **Mapping**: `central_to_odk_$pid` is saved to link the systems.

### Phase 4: Draft Testing Materialization
When a **Draft Form QR** is staged:
- **No username/password login is required**. The QR already contains the test token in its URL.
- The full `draft_url` is preserved and becomes the active `server_url` for a dedicated draft-testing ODK project.
- `draft_display_name` is used only for the login/status UI (`Draft Testing Mode`) and must not overwrite the persistent name of the production MEDRES project.
- Draft testing is isolated from the main MEDRES project onboarding flow.

---

## 4. Security Cleanup (Logout/Wipe) Behavior

When a security event occurs (manual logout, 3 failed PIN attempts, hard expiry, or QR rescan), the application follows an **"Option B" (Shared Device Stability)** model. It clears only the sensitive security context and staged QR state while preserving project data to ensure continuity for the next user on the same device.

| Category | Item | Action | Rationale |
|----------|------|--------|-----------|
| **Security** | Auth Token (JWT) | ✅ **Wiped** | Prevents unauthorized API access. |
| **Security** | Security PIN | ✅ **Wiped** | Forces next user to set their own PIN. |
| **Security** | Session State | ✅ **Wiped** | Clears auth/session flags and staged QR type. |
| **Security** | Clock State | ✅ **Wiped** | Resets `last_valid_wall_time` to prevent replay. |
| **User Data** | User Profile | ✅ **Wiped** | Clears `user_data_$pid` (ID, Username). |
| **QR Staging** | `auth_*`, `draft_*`, `qr_*` staged keys | ✅ **Wiped** | Prevents stale QR context leaking across rescans or users. |
| **Project Data** | Blank Forms | ❌ **Preserved** | Bandwidth efficiency; shared team access. |
| **Project Data** | Saved Instances | ❌ **Preserved** | Team visibility; shared device drafts. |
| **Project Data** | Submitted History | ❌ **Preserved** | Local audit trail for device users. |
| **Configuration** | Project URL/Name | ❌ **Preserved** | Simplifies re-login for the next user. |
| **Configuration** | ODK Settings | ❌ **Preserved** | Maintains ODK core stability. |
