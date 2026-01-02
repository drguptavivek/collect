# MEDRES ODK Collect Documentation

> **Last Updated**: 2026-01-01
> **Project**: MEDRES Custom Fork of ODK Collect
> **Version**: v2025.3.3-MEDRES-RC2-DEV
> **Branch**: `vg-work`

---

MEDRES ODK Collect is a specialized fork of ODK Collect transformed into a secure, institutional-grade data collection tool. It introduces a modular authentication system, security PINs, and intelligent data isolation for shared-device environments.

- **Advanced Authentication**: Replaces ultra-long-lived tokens with a short-validity token-based system (JWT) and user-initiated re-authentication.
- **PIN Security**: Mandatory 4-digit PIN with a 3-attempt session wipe policy.
- **Data Isolation**: Project-based data separation with shared-device support.
- **Offline Resilience**: 6-hour offline grace period and intelligent server reachability checks.
- **Telemetry**: Robust offline telemetry queuing with background synchronization.

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

- [README.md](README.md) - This file (entry point)
- [01-QUICKSTART.md](01-QUICKSTART.md) - Getting started guide

### 01-ARCHITECTURE (System Design)
- [overview.md](01-ARCHITECTURE/overview.md) - Architecture overview with diagrams
- [authentication.md](01-ARCHITECTURE/authentication.md) - Auth flow & state machine
- [medres_vs_standard_boundary.md](01-ARCHITECTURE/medres_vs_standard_boundary.md) - Custom vs Core boundaries
- [Collect_telemetry.md](01-ARCHITECTURE/Collect_telemetry.md) - Telemetry system design
- [activities.md](01-ARCHITECTURE/activities.md) - Activity reference
- [data-isolation.md](01-ARCHITECTURE/data-isolation.md) - SharedPreferences & File isolation
- [form_updating_in_app.md](01-ARCHITECTURE/form_updating_in_app.md) - "Match Server" sync logic
- [logging_and_events.md](01-ARCHITECTURE/logging_and_events.md) - File logging & crash reporting
- [multiuser-persistence.md](01-ARCHITECTURE/multiuser-persistence.md) - Shared-device session handling
- [network_and_reachability.md](01-ARCHITECTURE/network_and_reachability.md) - Reachability & TTL caching
- [saved_preferences.md](01-ARCHITECTURE/saved_preferences.md) - Encrypted vs default preference keys
- [bpmn-processes.md](01-ARCHITECTURE/bpmn-processes.md) - Documentation of BPMN business logic

### 02-DEVELOPMENT (Workflows)
- [setup.md](02-DEVELOPMENT/setup.md) - Local dev environment
- [building.md](02-DEVELOPMENT/building.md) - Build instructions
- [debugging.md](02-DEVELOPMENT/debugging.md) - Debugging tools & tips
- [commands.md](02-DEVELOPMENT/commands.md) - Common Android/Gradle commands

### 03-API (Endpoints)
- [reference.md](03-API/reference.md) - Complete API reference

### 04-OPERATIONS (Release)
- [maintenance.md](04-OPERATIONS/maintenance.md) - Syncing with upstream
- [versioning.md](04-OPERATIONS/versioning.md) - Versioning strategy & RC workflow
- [sync-log.md](04-OPERATIONS/sync-log.md) - Sync history

### 05-FEATURES (Modules)
- [qr-workflow.md](05-FEATURES/qr-workflow.md) - QR code configuration
- [pin-security.md](05-FEATURES/pin-security.md) - PIN security system
- [security-scenarios.md](05-FEATURES/security-scenarios.md) - **Protected Security Scenarios & Resilience**
- [telemetry-system.md](05-FEATURES/telemetry-system.md) - **Offline Telemetry & Sync**
- [app-lifecycle-security.md](05-FEATURES/app-lifecycle-security.md) - **Background Lifecycle & App-Lock**
- [shared-device-continuity.md](05-FEATURES/shared-device-continuity.md) - **Data Continuity vs Security Isolation**
- [logging-diagnostics.md](05-FEATURES/logging-diagnostics.md) - **Encrypted Logs & diagnostics**
- [accessibility.md](05-FEATURES/accessibility.md) - **Accessibility Compliance (WCAG 2.1)**

### Project Context & Plans
- [MEDRES_DEVELOPER_CHEATSHEET.md](MEDRES_DEVELOPER_CHEATSHEET.md) - Flashcard-style quick reference
- [MEDRES_PROJECT_KNOWLEDGE.md](MEDRES_PROJECT_KNOWLEDGE.md) - High-level project context
- [DEMO-APP-USER.md](DEMO-APP-USER.md) - Demo user credentials & roles
- [IMPLEMENTATION_PLAN_401_HANDLING.md](IMPLEMENTATION_PLAN_401_HANDLING.md) - Design for 401 interceptor
- [TEST_REFACTOR_PLAN.md](TEST_REFACTOR_PLAN.md) - Plan for unit/integrated tests

### Archive
- [multiuser_persistence.md](archive/multiuser_persistence.md) - Old multiuser implementation notes
- [ODK-default-setup.md](archive/ODK-default-setup.md) - Reference ODK setup (pre-customization)


---

## Key Features

### 1. Advanced Authentication
- Short-validity tokens (configurable per project on Central)
- Automatic token injection via OkHttp interceptors
- User-initiated re-authentication flow with reminders
- 6-hour offline grace period

### 2. PIN Security
- Mandatory 4-digit PIN setup after initial login
- Application lock immediately on backgrounding
- 3 failed attempts results in an automatic session wipe

### 3. Project-Based Data Isolation
- Blank forms automatically deleted on logout
- Completed instances retained to support shared device workflows
- Isolated SharedPreferences and file directories per project

### 4. Local Development Support
- Automatic `central.local` → `10.0.2.2` mapping (debug builds)
- SSL bypass for local development
- Dedicated MEDRES debug build variant

---

## Build Variants

| Variant | Command | Application ID | Purpose |
|---------|---------|----------------|---------|
| MEDRES Debug | `./gradlew assembleMedresDebug` | `org.medres.odk.collect` | Development |
| MEDRES Release | `./gradlew assembleMedresRelease` | `org.medres.odk.collect` | Production |

---

## Branch Strategy

| Branch | Purpose | Remote |
|--------|---------|--------|
| `master` | Tracks upstream ODK Collect (vanilla) | `origin` |
| `vg-work` | MEDRES customizations (active development) | `origin` |

### Data Layer
 
| Component | Responsibility |
|-----------|---------------|
| `ProjectCleaner` | Selective cleanup targeting blank forms and media. |
| `TelemetryWorker` | Background synchronization of queued events. |
| `medres_auth_prefs` | Encrypted storage for session metadata and PIN hashes. |
| `TelemetryEntity` | Local persistence for telemetry events during offline periods. |

---

## Code Location

- **MEDRES Auth Module**: `medres_auth_module/`
- **Main App Modifications**: `collect_app/src/main/`
- **Network Layer**: `open-rosa/.../OkHttpOpenRosaServerClientProvider.java`

---

## Contributing

When making changes to MEDRES customizations:

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
| PIN not required on resume | Check `MedresAppLock` lifecycle registration |

---

## References

- [ODK Collect Repository](https://github.com/getodk/collect)
- [ODK Central API](https://docs.getodk.org/central-api/)
- [Android Developer Documentation](https://developer.android.com/)

---

## Support

For issues specific to this fork, check the relevant documentation section or review the architecture diagrams. For general ODK Collect issues, refer to the [official ODK documentation](https://docs.getodk.org/).
