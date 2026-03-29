# MEDRES ODK Collect
<p align="center">
<img src="docs/medres-custom/assets/MEDRES_collect_logo_cropped.png" width="200">
</p>

> **Version**: v2026.1.2-MEDRES-RC1 | **Status**: Release Candidate  
> **Upstream**: ODK Collect v2026.1.2

## Overview

MEDRES ODK Collect is a secure, institutional-grade fork of ODK Collect designed for field medical research in resource-constrained environments. It introduces Bearer Token authentication, mandatory PIN security, intelligent QR workflows, and offline resilience for shared-device deployments.

## Key Features

- **Bearer Token Authentication**: Short-lived tokens (3-day validity) with server-side revocation and 6-hour offline grace period
- **PIN Security**: Mandatory 4-digit PIN with 3-attempt wipe, hardware-backed encryption (AES-256-GCM)
- **Intelligent QR Workflows**: Automatic detection of Project QRs (production) vs. Draft QRs (testing) with 5-layer security validation
- **Offline Resilience**: 6-hour grace period, intelligent server reachability checks, offline telemetry queuing
- **Shared Device Support**: Forms wiped on logout, instances preserved, user-level PIN isolation
- **Comprehensive Telemetry**: 20-minute heartbeat with location, battery, security events
- **WCAG 2.1 Level AA**: Full accessibility compliance for authentication flows

## User Journey

```mermaid
flowchart TD
    Start([Start App]) --> Access{Authenticated?}
    
    Access -- No --> Login[Login with Credentials]
    Login --> PINSetup{PIN Set?}
    
    PINSetup -- No --> CreatePIN[Create 4-Digit PIN]
    CreatePIN --> Active[Active Session]
    
    PINSetup -- Yes --> Active
    Access -- Yes --> Active
    
    Active --> Background[App in Background]
    Background --> Foreground[App Foregrounded]
    Foreground --> PINEntry[Enter 4-Digit PIN]
    
    PINEntry -- Success --> Active
    PINEntry -- 3 Failures --> Wipe[Session Wipe]
    Wipe -->|Clears PIN & Token ONLY| Login
```

## Technical Architecture

```mermaid
graph TB
    subgraph "MEDRES Auth Layer"
        AuthUI[MedresLoginActivity]
        AuthMgr[MedresAuthManager]
        AppLock[MedresAppLock]
        PinMgr[PinManager]
    end

    subgraph "ODK Core & Integration"
        TokenProvider[Token Provider]
        ODKCore[ODK Collect Core]
        Projects[Project Repository]
    end

    subgraph "Storage & Backend"
        SecureStorage[(Secure Auth Storage)]
        ODKStorage[(ODK Preference Files)]
        Central[ODK Central API]
    end

    AuthUI --> AuthMgr
    AuthMgr --> SecureStorage
    AuthMgr --> Projects
    AuthMgr --> Central
    
    AppLock --> PinMgr
    PinMgr --> SecureStorage
    
    ODKCore -- Requests Token --> TokenProvider
    TokenProvider -- Reads --> SecureStorage
    
    AuthMgr -- "Materializes Config" --> ODKStorage
```

## Security Hardening

- **QR Security**: 5-layer validation prevents decompression bombs, key injection, credential theft
- **PIN Bypass Protection**: Mandatory re-auth on cold starts and background resume
- **Shared-Device Isolation**: Automatic PIN clearance on user change
- **Brute Force Protection**: 3 failed PIN attempts trigger immediate session wipe
- **Clock Manipulation Detection**: Prevents grace period exploits via device time changes
- **Encrypted Storage**: Hardware-backed AES-256-GCM for tokens, pins, and credentials
- **API Guard**: Mutex-based sync for concurrent operations, rate limiting for auth calls

Full details: [Security Scenarios & Resilience](docs/medres-custom/05-FEATURES/security-scenarios.md)

## Documentation

### Core Documentation
1. **[Quickstart Guide](docs/medres-custom/01-QUICKSTART.md)** - Getting started with MEDRES
2. **[Architecture Overview](docs/medres-custom/01-ARCHITECTURE/overview.md)** - Technical documentation and system flows
3. **[API Specification](docs/medres-custom/03-API/reference.md)** - Backend endpoint definitions
4. **[Maintenance Guide](docs/medres-custom/04-OPERATIONS/maintenance.md)** - Merging upstream changes
5. **[Versioning Strategy](docs/medres-custom/04-OPERATIONS/versioning.md)** - Release candidate workflow

### QR Code Workflows
6. **[QR Codes Reference](docs/medres-custom/01-ARCHITECTURE/qr-codes.md)** - QR types, detection logic, and security
7. **[Scanning Draft QRs](docs/medres-custom/01-ARCHITECTURE/scanning-draft-form-QRs.md)** - Testing draft forms in Demo Mode

### Operations & Security
8. **[Release Signing Guide](docs/medres-custom/04-OPERATIONS/release-signing.md)** - Production release procedures
9. **[Building Guide](docs/medres-custom/02-DEVELOPMENT/building.md)** - Build workflows for all flavors
10. **[CHANGELOG](docs/CHANGELOG.md)** - Version history and release notes

## Technical Specifications

| Requirement | Specification |
| :--- | :--- |
| **Minimum OS** | Android 10 (API 29) |
| **Target OS** | Android 15 (API 35) |
| **Build SDK** | API 36 |
| **Architecture** | ARM/x86 (64-bit optimized) |
| **Permissions** | Location, Camera, Notifications, Storage |

## Backend (ODK Central)

MEDRES requires a heavily modified ODK Central backend with:
- Bearer Token authentication (3-day JWT tokens, configurable per-project)
- Telemetry endpoint (receives device location, battery, security events)
- App user management (automated password generation)
- Project-specific security policies (token validity, grace periods, PIN rotation)
- Audit logs (login history, security events)

Backend Documentation: [ODK Central Docs](docs/medres-custom/ODK_Central_docs/)

## Screenshots

Click to view authentication and security workflows

### Login & Configuration
<p align="center">
  <img src="docs/medres-custom/screenshots/00_login-screen.png" width="200" alt="Login Screen">
  <img src="docs/medres-custom/screenshots/01_manual_config.png" width="200" alt="Manual Config">
  <img src="docs/medres-custom/screenshots/02_QR_Config.png" width="200" alt="QR Config">
</p>

### PIN Setup & Security
<p align="center">
  <img src="docs/medres-custom/screenshots/03_set_pin.png" width="200" alt="Set PIN">
  <img src="docs/medres-custom/screenshots/06_Reenter_pin.png" width="200" alt="Re-enter PIN">
  <img src="docs/medres-custom/screenshots/08_date_drift_detection.png" width="200" alt="Clock Drift">
</p>

### Authenticated Session
<p align="center">
  <img src="docs/medres-custom/screenshots/04_post_login_with_new_settings_icon.png" width="200" alt="Post Login">
  <img src="docs/medres-custom/screenshots/05_post_login_settings.png" width="200" alt="Settings">
  <img src="docs/medres-custom/screenshots/07_Refresh_token.png" width="200" alt="Token Refresh">
</p>

### Diagnostics & Permissions
<p align="center">
  <img src="docs/medres-custom/screenshots/09_logs_export.png" width="200" alt="Logs Export">
  <img src="docs/medres-custom/screenshots/10_save_logs.png" width="200" alt="Save Logs">
  <img src="docs/medres-custom/screenshots/11_permissions_check.png" width="200" alt="Permissions">
</p>

### Backend Screenshots
<img src="docs/medres-custom/ODK_Central_docs/screenshots/01_app_users_list.png" width="600" alt="App Users">
<img src="docs/medres-custom/ODK_Central_docs/screenshots/03_telemetry_table.png" width="600" alt="Telemetry">
<img src="docs/medres-custom/ODK_Central_docs/screenshots/04_telemetry_map.png" width="600" alt="Telemetry Map">



## What's New in RC4

### Intelligent QR Code Detection
Automatic QR type detection separates production data collection from form testing:

- **Project QRs**: Trigger login flow for production data collection
- **Draft QRs**: Enter Demo Mode for testing form updates
- **Standard ODK QRs**: Rejected with error messages (incompatible with MEDRES auth)
- **Legacy ODK QRs**: Blocked to prevent security bypass

This prevents data contamination and configuration errors.

### 5-Layer QR Security Validation
1. **Size Limits**: Max 4KB compressed, 16KB decompressed (prevents decompression bombs)
2. **Key Validation**: Settings validated against `ProjectKeys` whitelist (prevents injection)
3. **Type Safety**: Boolean/String/Integer type checking (prevents type confusion)
4. **Sensitive Key Protection**: Blocks `server_url`, `username`, `password` override (prevents credential theft)
5. **Demo Mode Validation**: Draft QRs require both `/draft` and `/test/` in URL

### Automatic Project Name Updates
Project names update automatically after authentication.

## Credits

Based on the official [ODK Collect](https://github.com/getodk/collect) project.
