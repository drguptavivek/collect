# Customizations in ODK Collect (AIIMS Fork)

This document outlines the architectural and functional changes made to this version of ODK Collect compared to the standard [upstream repository](https://github.com/getodk/collect).

## 1. AIIMS Authentication Module (`aiims_auth_module`)
A new, standalone Android library module responsible for managing custom `aiims.ac.in` authentication.

### Key Components
- **AiimsAuthManager**: Single source of truth for authentication state (LOGGED_IN, LOGGED_OUT, REQUIRE_PIN). Manages session lifecycle.
- **AiimsLoginActivity**: Custom activity for username/password login against the AIIMS API.
- **PIN Security**:
  - `SetupPinActivity`: Enforces a 4-digit PIN setup immediately after login.
  - `PinEntryActivity`: Validates the PIN.
  - `AiimsAppLock`: Automatically locks the app when it moves to the background, requiring PIN re-entry on return.
- **Token Management**:
  - `AiimsTokenManager`: Stores and retrieves Auth/Refresh tokens encrypted in `SharedPreferences`.
  - `TokenRevocationManager`: Handles reliable token revocation on logout (with retry logic).
- **Data Isolation**:
  - `ProjectCleaner`: Interface and logic to clear project-specific data (forms, instances, etc.) upon user logout to prevent data leakage between users.

## 2. Core App Integration (`collect_app`)
The main application module has been modified to integrate the auth module.

### Initialization & Lifecycle
- **Collect.java**:
  - Initializes `AiimsAuthManager` in `onCreate()`.
  - Registers `AiimsAppLock` lifecycle callbacks to handle auto-locking.
- **AppDependencyModule**:
  - Updated Dagger configuration to provide `ProjectCleaner` implementation to the auth module.
  - Configures dependencies to be aware of the custom auth context.

### UI Changes
- **Auth Settings**: Added an "Auth Settings" screen accessible from the main menu, allowing users to:
  - Change PIN.
  - View User Profile.
  - Logout (triggering data cleanup).
- **Resources**: Added custom strings, colors, and themes for the AIIMS branding.

## 3. Networking Modifications (`open-rosa`)
The OpenRosa networking layer was updated to support custom Bearer token authentication while maintaining compatibility with standard ODK servers.

### Key Changes
- **OkHttpOpenRosaServerClientProvider**:
  - Updated to accept a `TokenProvider` interface.
  - Injects `Authorization: Bearer <token>` header into requests if a token is available.
- **TokenProvider Interface**: New interface implemented by `AiimsTokenManager` to supply valid tokens to the networking layer.

## 4. Configuration & Build
- **settings.gradle**: Includes `:aiims_auth_module`.
- **libs.versions.toml**: Updated to manage dependencies for the new module.
- **AndroidManifest.xml**:
  - Added permissions/activities for the auth module.
  - registered `AiimsLoginActivity` and other auth activities.

## 5. Summary of New Features
| Feature | Description |
| :--- | :--- |
| **Custom Login** | Replaces/Augments standard server setup with AIIMS implementation. |
| **App Locking** | Auto-lock on background; PIN required to resume. |
| **User Isolation** | Complete cleanup of forms/instances on logout. |
| **Auth Settings** | Dedicated menu for account management. |
