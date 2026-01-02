# MEDRES vs. Standard ODK Boundaries

> Last Updated: 2025-12-31

## Overview
This document defines the architectural boundaries between the MEDRES customization and the standard ODK Collect codebase. The goal of this boundary is to ensure that MEDRES-specific logic is isolated, making it easier to sync with upstream ODK updates and preventing custom logic from inadvertently affecting standard ODK behavior.

---

## 1. Module Boundaries

### `open-rosa` (Standard)
- **Rule**: **Zero custom code.**
- **Purpose**: Implements the OpenRosa protocol (form discovery, submission, etc.).
- **Boundary**: This module must remain identical to the upstream version. Any networking requirement from the MEDRES layer must be satisfied via implementation of interfaces (e.g., `TokenProvider`) or configuration, never by modifying the library's classes.

### `medres-auth-module` (Custom)
- **Rule**: Contains all MEDRES-specific logic.
- **Purpose**: Authentication (Bearer Token), Security (PIN, App Lock), Telemetry, and MEDRES API clients.
- **Boundary**: Functions as a standalone library that the main app depends on. It does not know about ODK's form management or database structures.

### `collect_app` (Integration Layer)
- **Purpose**: The "glue" that connects standard ODK modules with the MEDRES Auth module.
- **Boundary**: Uses Dagger/Hilt to inject MEDRES implementations into ODK interfaces.
  - **Example**: Injecting an MEDRES `TokenProvider` into ODK's `OkHttpConnection`.

---

## 2. Networking Boundary

| Feature | Standard ODK / OpenRosa | MEDRES Customization |
|---------|-------------------------|---------------------|
| **Protocol** | `OpenRosaHttpInterface` | `RealAuthClient` (Retrofit) |
| **Auth** | Basic Auth (supported by ODK) | Bearer Token (MEDRES-only) |
| **401 Handling**| Propagates error to ODK Core | `AuthInterceptor` + blocking re-auth |
| **Target APIs** | `/formList`, `/submission` | `/telemetry`, `/login`, `/revoke` |

> [!IMPORTANT]
> **Interception Boundary**: 401 interception and automatic retry is strictly limited to MEDRES-specific API calls (`RealAuthClient`). Standard ODK network calls for form management are **not** intercepted and do not trigger MEDRES re-authentication flows automatically. This prevents "magic" behavior that could break standard ODK sync logic.

---

## 3. UI & Security Boundary

- **Pre-menu Security**: MEDRES-specific activities (`MedresLoginActivity`, `PinEntryActivity`) guard the entry points to the standard ODK `MainMenuActivity`.
- **Lifecycle Monitoring**: `MedresAppLock` monitors activity lifecycle to re-trigger the PIN screen. It is registered at the application level but focuses on MEDRES-defined security states.

## 4. Persistence & Preference Boundary
 
 MEDRES uses a tiered storage architecture to separate sensitive authentication data from standard application settings.
 
 ### Storage Tiers
 1. **MEDRES Secure Storage (`medres_auth_secure`)**: Hardware-backed encrypted storage (`EncryptedSharedPreferences`). Stores tokens, PINs, and sensitive endpoints. Core ODK has no visibility into this tier.
 2. **MEDRES Metadata Storage (`medres_auth_prefs`)**: Standard SharedPreferences for MEDRES-specific state (e.g., `is_authenticated`, `active_project_id`).
 3. **Standard ODK Storage (`meta`, `general_prefs`)**: Standard ODK preference files. 
 
 ### The "Materialization" Boundary
 A key boundary exists during the login/setup phase:
 - **Staged State**: Before login, MEDRES logic stores target configuration (Base URL, PID) in `medres_auth_prefs`. Standard ODK remains in a "clean" state with no projects configured.
 - **Project Materialization**: Upon successful login, MEDRES logic "materializes" the config into ODK. It creates an ODK Project and forces specific settings into its `general_prefs` (e.g., setting the `server_url` to a tokenized format).
 
 ### Mapping Boundary
 The `central_to_odk_$pid` key in `medres_auth_prefs` acts as the definitive bridge between the MEDRES/Central Project ID and the internal ODK UUID. This mapping allows MEDRES logic to target the correct ODK project for settings updates or data cleanup without modifying ODK's internal registry.
 
 ---
 
 ## 5. Cleanup Rules & Shared Device Boundary
 
 - **Logout**: MEDRES logic clears MEDRES-specific tokens, PIN, and user metadata.
- **Data Retention (Option B)**: Standard ODK *forms*, *instances*, and *settings* are **preserved** to support shared device scenarios. (See [Data Isolation](data-isolation.md#6-security-cleanup-logoutwipe-behavior)).
- **Rationale**: This ensures that when User B logs into the same project on a shared tablet, they do not need to re-download forms and can see User A's drafts if continuity is required.
 
 > [!IMPORTANT]
 > **Bridge Mechanism & Circularity**: The `ProjectCleaner` is provided by the standard ODK core but consumed by the MEDRES `MedresAuthManager`. To avoid a `StackOverflowError` during Dagger initialization, it is injected as `dagger.Lazy<ProjectCleaner>`. This breaks the circular dependency chain: `MedresAuthManager` → `ProjectCleaner` → `InstancesDataService` → `OpenRosaHttpInterface` → `MedresAuthManager`.
 
 ---
 
 ## 6. Summary of Integration Points

| Integration Point | Mechanism | Implementation |
|-------------------|-----------|----------------|
| **Dependency Injection** | Dagger `AppDependencyModule` | Injects MEDRES components into ODK interfaces |
| **Networking** | `TokenProvider` | Provides MEDRES Bearer Token to OpenRosa client |
| **Lifecycle** | `ActivityLifecycleCallbacks` | `MedresAppLock` manages PIN/Auth state checks |
| **Project Setup** | `MedresLoginActivity` | Populates ODK `ProjectsRepository` with Central details |

---

## Related Documentation
- [Architecture Overview](overview.md)
- [Authentication Architecture](authentication.md)
- [Collect Telemetry](Collect_telemetry.md)
- [Data Isolation](data-isolation.md)
