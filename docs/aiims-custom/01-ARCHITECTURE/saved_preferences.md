# AIIMS Preferences & Persistence

This document details the configuration and persistence settings for the AIIMS customization of ODK Collect. It explains how settings are initialized, updated, and managed throughout the application lifecycle.

## 1. Storage Architecture

The application uses a combination of custom AIIMS-specific SharedPreferences and standard ODK Collect preferences.

### AIIMS-Specific Storage
| Preference File | Description | Security |
| :--- | :--- | :--- |
| `aiims_auth_prefs` | Stores non-sensitive authentication state, user metadata, and project-specific API configurations. | Private SharedPreferences |
| `aiims_auth_secure` | Stores sensitive data using `EncryptedSharedPreferences` including tokens, PIN hashes, and sensitive API URLs. | Hardware-backed Encryption |

### Standard ODK Storage
| Preference File | Description | Keys |
| :--- | :--- | :--- |
| `meta` | Core application metadata. | `current_project_id`, `metadata_installid`, `projects` |
| `general_prefs[UUID]` | Settings for a specific ODK project. | `server_url`, `protocol`, `project_name` |

---

## 2. Preference Lifecycle

### Phase 1: Application Installation & First Launch
On the first launch, the application initializes its base state:
- **`metadata_installid`**: Generated and stored in `meta` SharedPreferences. This serves as the `deviceId` for AIIMS telemetry.
- **`aiims_auth_enabled`**: Read from resource values (defined in `build.gradle` flavor configuration).
- **`first_launch`**: Set to `true` in `aiims_auth_prefs` to trigger initial configuration flows.

### Phase 2: Configuration (Staged State)
When a user scans a QR code or enters details manually via `AiimsLoginActivity`:
- **Staged Details**: Base configuration is stored in `aiims_auth_prefs` to enable the login UI without yet creating an ODK project.
- **`auth_url`**: The base API URL (e.g., `https://central.example.com/v1`).
- **`auth_project_id`**: The numeric Central Project ID.
- **`qr_general_settings`**: A raw JSON blob from the QR code containing standard ODK settings.
- **UI State**: The login screen displays the configured project and changes the "Scan QR" button to **"Rescan QR Code"**.

### Phase 3: Login & Project Materialization
Upon successful authentication, the configuration is "materialized" into the standard ODK storage:
- **Tokenized URL**: A specialized URL is constructed and saved to `server_url` in `general_prefs[UUID]`.
  - Format: `<BaseURL>/key/<BearerToken>/projects/<PID>`
- **Project Creation**: A standard ODK project is created/updated in the `meta` prefs `projects` repository.
- **Settings Application**: Properties from `qr_general_settings` (like `form_update_mode`) are mapped to `general_prefs[UUID]`.
- **Identity**: `current_project_id` in `meta` is set to the ODK UUID of the new project.
- **`auth_token`**: The Bearer JWT is stored in `aiims_auth_secure`.
- **`user_data`**: User id, username, and email are stored in `aiims_auth_prefs`.
- **`token_expiry`**: Expiration timestamp stored in `aiims_auth_secure`.
- **`server_url`**: Updated in the project-specific `general_prefs[UUID]` to the tokenized URL (e.g., `.../v1/key/{token}/projects/{id}`).
- **`protocol`**: Set to `odk_default` in `general_prefs[UUID]`.
- **`project_name`**: Updated in both `meta` prefs (project list) and `general_prefs[UUID]` from the Central project metadata.

---

## 3. Detailed Preference Key Reference

### A. AIIMS Authentication Preferences (`aiims_auth_prefs`)
These settings manage the high-level authentication state and staged configuration.

| Key | Purpose | Source |
| :--- | :--- | :--- |
| `active_project_id` | Tracks the currently selected Central Project ID. | `AiimsLoginActivity` (from detection or QR staging) |
| `is_authenticated` | Simple boolean for UI logic/navigation. | `AiimsAuthManager` (set to `true` on successful login) |
| `auth_url` | The base API URL for the Central server. | `AiimsQrScannerActivity` (parsed from QR `server_url`) |
| `auth_project_id` | The target Project ID on the Central server. | `AiimsQrScannerActivity` (parsed from QR `project_id`) |
| `qr_general_settings` | Raw JSON of ODK settings to apply later. | `AiimsQrScannerActivity` (scanned QR content) |
| `user_data_$pid` | JSON blob with user details (ID, Username). | `AiimsAuthManager` (from Auth API success response) |
| `central_to_odk_$pid` | Mapping between Central ID and ODK UUID. | `AiimsAuthManager` (created during project materialization) |
| `project_name_$pid` | The human-readable name of the project. | `AiimsAuthManager` (fetched from `/projects/:id` API) |
| `first_launch` | Flag to trigger the setup/QR scanning flow. | Initialized to `true` on app install. |

### B. AIIMS Secure Storage (`aiims_auth_secure`)
Encrypted storage for sensitive credentials and security metadata.

| Key | Purpose | Source |
| :--- | :--- | :--- |
| `auth_token` | The Bearer JWT used for all API requests. | `AiimsAuthManager` (from Auth API success response) |
| `token_expiry` | Milliseconds timestamp of when the token expires. | `AiimsAuthManager` (from Auth API `expiresAt`) |
| `pin_hash` | PBKDF2 hash of the 4-digit security PIN. | `PinManager` (from User input during PIN setup) |
| `pin_salt` | Unique salt used for the PIN hash. | `PinManager` (generated locally during PIN setup) |
| `last_valid_wall_time` | Tracks last known "true" time for clock security. | `ClockValidator` (from `Date` headers in API responses) |
| `project_id` | Validates that the token matches the active project. | `AiimsAuthManager` (persisted during login) |

### C. ODK Core Preferences (`meta`)
Global metadata that drives the core ODK Collect engine.

| Key | Purpose | Source |
| :--- | :--- | :--- |
| `current_project_id` | The ODK UUID of the project currently in use. | `AiimsLoginActivity` (set after login/materialization) |
| `metadata_installid` | The unique ID for this app installation. | `InstallIDGenerator` (generated on first app launch) |
| `projects` | List of all configured projects (Name, Icon, UUID). | Added by `AiimsLoginActivity` using `ProjectsRepository` |

### D. ODK Project Settings (`general_prefs[UUID]`)
Settings specific to the currently active ODK workspace.

| Key | Purpose | Source |
| :--- | :--- | :--- |
| `server_url` | The tokenized URL used for form syncing. | `AiimsLoginActivity` (Constructed: `base + /key/ + token + /pid`) |
| `protocol` | The ODK protocol to use for syncing. | Hardcoded to `odk_default` in `AiimsLoginActivity`. |
| `username` | The username for audit logs and form metadata. | `AiimsLoginActivity` (from the User's login input) |
| `password` | The password (often ignored by Central token auth). | `AiimsLoginActivity` (from the User's login input) |
| `form_update_mode` | Determines how forms are checked for updates. | `AiimsLoginActivity` (mapped from `qr_general_settings`) |

---

## 4. Related Projects & Context (Beads)

The AIIMS customization uses an internal issue tracker named **Beads**. References found in commit logs and code are context for development history:
- **`Beads`**: The project's issue tracking system (synced via the `beads-sync` branch).
- **`collect-cc3`**: Issue ID referring to "Collect Customization 3" or related feature/bug.
- **`collect-mtf`**: Issue ID likely referring to "Modified Token Flow" or similar authentication refactoring.

> [!NOTE]
> These identifiers are used for traceability between code changes and project requirements.

---

## 5. Security & Isolation Logic

- **Logout**: Clears `auth_token` and `user_data` but **retains** project data (forms/instances) to support shared device scenarios where different users collect data for the same project.
- **Project Cleaner**: In some flows, `ProjectCleaner` is used to wipe blank forms to prevent cross-user visibility while maintaining data isolation.
