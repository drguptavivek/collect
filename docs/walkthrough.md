# Central Backend Auth Implementation Walkthrough

## Summary
Refactored the `aiims_auth_module` to support the new Central Backend Authentication flow (Short Token), replacing the legacy PIN-based system. Added **Multi-Project Support** to allow seamless switching between ODK projects.

## Changes

### 1. Multi-Project Support
-   **Problem**: Previous auth was global. Switching projects would overwrite credentials.
-   **Solution**: Authentication state (Token, User ID) is now scoped by ODK Project ID.
-   **Implementation**: `AiimsAuthManager` now stores data with keys like `auth_token_{projectId}`. It tracks the "Active Project" to expose the correct state to the UI.

### 2. Login Flow (Short Token)
-   **New Flow**:
    1.  User scans ODK Central QR Code (Configures Server URL).
    2.  App detects Project ID from the URL (e.g., `.../projects/5` -> ID 5).
    3.  User enters **Username** & **Password**.
    4.  App requests Short-Lived Token from `POST /projects/{id}/app-users/login`.
    5.  Token & Expiry stored locally.
-   **Removed**: Backend-enforced PIN state (`REQUIRES_PIN`). PIN is now purely a local app-lock feature.

### 3. API Updates
-   Refactored `AuthApiService` to match `vg_api.md` schema.
-   **Local Testing**: Implemented `UnsafeOkHttpClient` in `RealAuthClient` to allow localhost/emulator connectivity (ignoring SSL errors for `10.0.2.x`, `192.168.x.x`).
-   **Protocol Fix**: Forced `HTTP/1.1` for unsafe clients to prevent HTTP 421 (Misdirected Request) errors on local setups.
-   **URL Sanitization**: Updated `RealAuthClient` to strip `/projects/{id}` from the base URL to prevent path duplication.
-   **Custom DNS**: Implemented in-app DNS to map `central-dev`, `central.dev`, and `central.local` to `10.0.2.2` locally, allowing the use of correct hostnames from the emulator without server-side changes.
-   **ODK Integration**:
    -   **New Manual Configuration Screen**: Replaced the single "URL" input with a comprehensive dialog asking for:
        -   **Base URL**: Defaults to `https://central.local`.
        -   **Project ID**: Defaults to `1`.
    Manual configuration now programmatically creates ODK projects using a local `Settings` implementation. It writes directly to ODK's `general_prefs<uuid>` file (fixing the incorrect `demo.getodk.org` fallback caused by using the wrong filename) and strictly enforces `Protocol=odk_default`, logging in correctly with saved credentials.
-   **PIN Flow**: After successful login, the app checks if a local PIN is set. If not, it redirects the user to the **Setup PIN** screen instead of the Main Menu, ensuring security compliance.
-   **Settings UI**: The **Authentication Settings** screen (accessible from Main Activity) now correctly displays the active **Session Token**, allowing for debugging and verification.
-   Added `AiimsProjectUtils` to helper class to extract Project ID from URLs.

### 4. UI Updates
-   **AiimsLoginActivity**:
    -   Removed manual URL entry (auto-detected from ODK Settings).
    -   Replaced Email field with **Username** field.
    -   Added "Scan QR" button if no project is configured.

## Verification

### Manual Testing Steps
1.  **Project Setup**:
    -   Launch App.
    -   If "No Project Configured", click "Scan QR Code".
    -   Scan a valid ODK Central App User QR Code.
2.  **Login**:
    -   Verify status text shows "Project Configured: ...".
    -   Enter App User **Username** and **Password**.
    -   Click Login.
    -   Verify "Welcome {username}" toast and transition to Main Menu.
3.  **Persistence**:
    -   Close and Reopen App.
    -   Verify you remain logged in.
4.  **Multi-Project (Optional)**:
    -   Add a second project in ODK Settings.
    -   Switch to it.
    -   Verify you are prompted to Login (for the new project).
    -   Switch back to first project.
    -   Verify you are **automatically logged in** (session preserved).

### Code Validation
-   `AuthApiService` uses `@Path("projectId")` correctly.
-   `AiimsAuthManager` persists keys with `_$projectId` suffix.
-   **Build Verification**: `assembleDebug` passed successfully (Step 210).

### 5. PIN Security Verification
-   **Initial Setup**: Verified `SetupPinActivity` launches immediately after login if PIN is not set.
-   **App Lock**: Verified `AiimsAppLock` detects when the app moves to background. On resume, it launches `PinEntryActivity`.
-   **Secure PIN Screen**: Verified `PinEntryActivity` overrides `onBackPressed` to minimize the app, preventing users from bypassing the lock by pressing Back.

### 6. Data Isolation Verification
-   **Logout Cleanup**: Verified that logging out triggers `ProjectCleaner`, which:
    -   Deletes all **Blank Forms** (preventing new users from seeing old user's forms).
    -   **Important**: Uses ODK Project UUID (not Central ID) to target the correct storage paths.
    -   Clears local caches and triggers `FormsDataService.refresh()`.
    -   **PIN**: Clears local PIN via `PinManager` to ensure complete logout.
-   **Instance Preservation**: Verified that **Saved Instances** are NOT deleted during logout, ensuring previously collected data is safe.

### 7. Release Candidate Status
**Date**: 2025-12-18
**Version**: `v2025.1.0-RC1` (Build 5113)
**Status**: **PASSED**
-   All functional requirements for Authentication, PIN Security, and Data Isolation have been met and verified.
-   Critical bugs regarding Form Persistence and PIN Clearing have been resolved.
-   System acts as a Release Candidate for field testing.
