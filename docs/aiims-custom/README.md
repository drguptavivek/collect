# AIIMS ODK Collect Documentation

> **Last Updated**: 2025-12-28
> **Project**: AIIMS Custom Fork of ODK Collect
> **Branch**: `vg-work`

---

## Overview

This is a customized fork of [ODK Collect](https://github.com/getodk/collect) for AIIMS (All India Institute of Medical Sciences). The fork implements a custom authentication and security workflow including:

- **Bearer Token Authentication**: Replaces standard ODK Basic Auth with short-lived JWT tokens
- **PIN Security**: Mandatory 4-digit PIN with 3-attempt wipe policy
- **Data Isolation**: Project-based data separation with shared device support
- **Offline Grace Period**: 6-hour offline grace after token expiry
- **Telemetry**: Background device presence and location tracking

---

## Quick Links

| For... | Go to... |
|--------|----------|
| **Getting Started** | [01-QUICKSTART.md](01-QUICKSTART.md) |
| **Building the App** | [02-DEVELOPMENT/building.md](02-DEVELOPMENT/building.md) |
| **Local Development Setup** | [02-DEVELOPMENT/setup.md](02-DEVELOPMENT/setup.md) |
| **Architecture Overview** | [01-ARCHITECTURE/overview.md](01-ARCHITECTURE/overview.md) |
| **API Reference** | [03-API/reference.md](03-API/reference.md) |
| **Maintenance & Release** | [04-OPERATIONS/maintenance.md](04-OPERATIONS/maintenance.md) |
| **Versioning Guide** | [04-OPERATIONS/versioning.md](04-OPERATIONS/versioning.md) |

---

## Documentation Structure

```
docs/aiims-custom/
├── README.md                    # This file - entry point
├── 01-QUICKSTART.md             # Getting started guide
│
├── 01-ARCHITECTURE/             # System design & components
│   ├── overview.md              # Architecture overview with diagrams
│   ├── authentication.md        # Auth flow & state machine
│   ├── activities.md            # Activity reference
│   └── data-isolation.md        # Persistence & storage
│
├── 02-DEVELOPMENT/              # Developer workflows
│   ├── setup.md                 # Local dev environment
│   ├── building.md              # Build instructions
│   ├── debugging.md             # Debugging tools & tips
│   └── commands.md              # Common Android/Gradle commands
│
├── 03-API/                      # Backend API documentation
│   └── reference.md             # Complete API reference
│
├── 04-OPERATIONS/               # Maintenance & release
│   ├── maintenance.md           # Syncing with upstream
│   ├── versioning.md            # Versioning strategy
│   └── sync-log.md              # Sync history
│
├── 05-FEATURES/                 # Feature documentation
│   ├── qr-workflow.md           # QR code configuration
│   └── pin-security.md          # PIN security system
│
└── archive/                     # Deprecated documentation
    ├── FEAT_MULTIUSER_PERSISTENCE.md
    └── ODK-default-setup.md
```

---

## Key Features

### 1. Bearer Token Authentication
- Short-lived tokens (default 3 days, configurable)
- Automatic token injection via OkHttp interceptors
- 6-hour offline grace period

### 2. PIN Security
- Mandatory 4-digit PIN setup after login
- App lock on resume (background detection)
- 3 failed attempts = session wipe

### 3. Project-Based Data Isolation
- Blank forms deleted on logout
- Completed instances retained (shared device support)
- Separate SharedPreferences per project

### 4. Local Development Support
- Automatic `central.local` → `10.0.2.2` mapping (debug builds)
- SSL bypass for local development
- Dedicated AIIMS debug build variant

---

## Architecture Diagram

```mermaid
graph TB
    subgraph "AIIMS Auth Module"
        AuthMgr[AiimsAuthManager]
        PinMgr[PinManager]
        AppLock[AiimsAppLock]
        Cleaner[ProjectCleaner]
    end

    subgraph "ODK Core"
        Projects[ProjectsRepository]
        Forms[FormsDataService]
        Network[OkHttpOpenRosaServerClient]
    end

    subgraph "Storage"
        AuthPrefs[aiims_auth_prefs]
        ODKPrefs[general_prefs{UUID}]
        DB[(SQLite)]
    end

    Login[AiimsLoginActivity] --> AuthMgr
    AuthMgr --> AuthPrefs
    AuthMgr --> Network
    AuthMgr --> Cleaner
    Cleaner --> Forms
    AppLock --> PinMgr
    AuthMgr --> Projects

    Network --> AuthMgr
```

---

## Build Variants

| Variant | Command | Application ID | Purpose |
|---------|---------|----------------|---------|
| AIIMS Debug | `./gradlew assembleAiimsDebug` | `org.aiims.odk.collect` | Development |
| AIIMS Release | `./gradlew assembleAiimsRelease` | `org.aiims.odk.collect` | Production |

---

## Branch Strategy

| Branch | Purpose | Remote |
|--------|---------|--------|
| `master` | Tracks upstream ODK Collect (vanilla) | `origin` |
| `vg-work` | AIIMS customizations (active development) | `origin` |

> **Important**: This fork cannot produce true vanilla ODK builds. For vanilla ODK, use the `master` branch or official ODK Collect.

---

## Code Location

- **AIIMS Auth Module**: `aiims_auth_module/`
- **Main App Modifications**: `collect_app/src/main/`
- **Network Layer**: `open-rosa/.../OkHttpOpenRosaServerClientProvider.java`

---

## Contributing

When making changes to AIIMS customizations:

1. **Test both debug and release builds** - ProGuard/R8 rules may need updates
2. **Update documentation** - Keep docs in sync with code changes
3. **Check ProGuard rules** - API models must be kept in `collect_app/proguard-rules.pro`
4. **Verify data isolation** - Ensure forms are wiped on logout

---

## Troubleshooting

| Issue | Solution |
|-------|----------|
| Login crash in Release build | Check ProGuard rules for API models |
| Forms persist after logout | Verify `ProjectCleaner` UUID resolution |
| Local dev connection fails | Ensure `central.local` → `10.0.2.2` mapping is active |
| PIN not required on resume | Check `AiimsAppLock` lifecycle registration |

---

## References

- [ODK Collect Repository](https://github.com/getodk/collect)
- [ODK Central API](https://docs.getodk.org/central-api/)
- [Android Developer Documentation](https://developer.android.com/)

---

## Support

For issues specific to this fork, check the relevant documentation section or review the architecture diagrams. For general ODK Collect issues, refer to the [official ODK documentation](https://docs.getodk.org/).
