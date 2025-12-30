# AIIMS vs. Standard ODK Boundaries

> Last Updated: 2025-12-30

## Overview
This document defines the architectural boundaries between the AIIMS customization and the standard ODK Collect codebase. The goal of this boundary is to ensure that AIIMS-specific logic is isolated, making it easier to sync with upstream ODK updates and preventing custom logic from inadvertently affecting standard ODK behavior.

---

## 1. Module Boundaries

### `open-rosa` (Standard)
- **Rule**: **Zero custom code.**
- **Purpose**: Implements the OpenRosa protocol (form discovery, submission, etc.).
- **Boundary**: This module must remain identical to the upstream version. Any networking requirement from the AIIMS layer must be satisfied via implementation of interfaces (e.g., `TokenProvider`) or configuration, never by modifying the library's classes.

### `aiims-auth-module` (Custom)
- **Rule**: Contains all AIIMS-specific logic.
- **Purpose**: Authentication (Bearer Token), Security (PIN, App Lock), Telemetry, and AIIMS API clients.
- **Boundary**: Functions as a standalone library that the main app depends on. It does not know about ODK's form management or database structures.

### `collect_app` (Integration Layer)
- **Purpose**: The "glue" that connects standard ODK modules with the AIIMS Auth module.
- **Boundary**: Uses Dagger/Hilt to inject AIIMS implementations into ODK interfaces.
  - **Example**: Injecting an AIIMS `TokenProvider` into ODK's `OkHttpConnection`.

---

## 2. Networking Boundary

| Feature | Standard ODK / OpenRosa | AIIMS Customization |
|---------|-------------------------|---------------------|
| **Protocol** | `OpenRosaHttpInterface` | `RealAuthClient` (Retrofit) |
| **Auth** | Basic Auth (supported by ODK) | Bearer Token (AIIMS-only) |
| **401 Handling**| Propagates error to ODK Core | `AuthInterceptor` + blocking re-auth |
| **Target APIs** | `/formList`, `/submission` | `/telemetry`, `/login`, `/revoke` |

> [!IMPORTANT]
> **Interception Boundary**: 401 interception and automatic retry is strictly limited to AIIMS-specific API calls (`RealAuthClient`). Standard ODK network calls for form management are **not** intercepted and do not trigger AIIMS re-authentication flows automatically. This prevents "magic" behavior that could break standard ODK sync logic.

---

## 3. UI & Security Boundary

- **Pre-menu Security**: AIIMS-specific activities (`AiimsLoginActivity`, `PinEntryActivity`) guard the entry points to the standard ODK `MainMenuActivity`.
- **Lifecycle Monitoring**: `AiimsAppLock` monitors activity lifecycle to re-trigger the PIN screen. It is registered at the application level but focuses on AIIMS-defined security states.

## 4. Persistence & Preference Boundary
 
 AIIMS uses a tiered storage architecture to separate sensitive authentication data from standard application settings.
 
 ### Storage Tiers
 1. **AIIMS Secure Storage (`aiims_auth_secure`)**: Hardware-backed encrypted storage (`EncryptedSharedPreferences`). Stores tokens, PINs, and sensitive endpoints. Core ODK has no visibility into this tier.
 2. **AIIMS Metadata Storage (`aiims_auth_prefs`)**: Standard SharedPreferences for AIIMS-specific state (e.g., `is_authenticated`, `active_project_id`).
 3. **Standard ODK Storage (`meta`, `general_prefs`)**: Standard ODK preference files. 
 
 ### The "Materialization" Boundary
 A key boundary exists during the login/setup phase:
 - **Staged State**: Before login, AIIMS logic stores target configuration (Base URL, PID) in `aiims_auth_prefs`. Standard ODK remains in a "clean" state with no projects configured.
 - **Project Materialization**: Upon successful login, AIIMS logic "materializes" the config into ODK. It creates an ODK Project and forces specific settings into its `general_prefs` (e.g., setting the `server_url` to a tokenized format).
 
 ### Mapping Boundary
 The `central_to_odk_$pid` key in `aiims_auth_prefs` acts as the definitive bridge between the AIIMS/Central Project ID and the internal ODK UUID. This mapping allows AIIMS logic to target the correct ODK project for settings updates or data cleanup without modifying ODK's internal registry.
 
 ---
 
 ## 5. Cleanup Rules & Shared Device Boundary
 
 - **Logout**: AIIMS logic clears AIIMS-specific tokens and user metadata.
 - **Data Retention**: Standard ODK *forms* are wiped by AIIMS `ProjectCleaner` to prevent cross-user visibility. However, ODK *instances* (completed submissions) are **preserved**.
 - **Rationale**: This explicitly satisfies the **Shared Device Safety** requirement (originally defined in `multiuser-persistence.md`), allowing multiple users to operate on the same device without data loss while maintaining user privacy via form cleanup.
 
 ---
 
 ## 6. Summary of Integration Points

| Integration Point | Mechanism | Implementation |
|-------------------|-----------|----------------|
| **Dependency Injection** | Dagger `AppDependencyModule` | Injects AIIMS components into ODK interfaces |
| **Networking** | `TokenProvider` | Provides AIIMS Bearer Token to OpenRosa client |
| **Lifecycle** | `ActivityLifecycleCallbacks` | `AiimsAppLock` manages PIN/Auth state checks |
| **Project Setup** | `AiimsLoginActivity` | Populates ODK `ProjectsRepository` with Central details |

---

## Related Documentation
- [Architecture Overview](overview.md)
- [Authentication Architecture](authentication.md)
- [Collect Telemetry](Collect_telemetry.md)
- [Data Isolation](data-isolation.md)
