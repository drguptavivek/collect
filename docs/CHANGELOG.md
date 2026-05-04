# Changelog

## [v2026.1.2-MEDRES-RC2] - 2026-04-10
### Added
- **Finalized QR Flow Rewrite**: Full integration of the type-safe QR architecture with the v2026.1.2 upstream base.
- **Race Condition Resolution**: Removed the "Scan New QR" button from Auth Settings (Option B) to eliminate complex mode-transition bugs and intercepting PIN screens.
- **Project Isolation**: Strict guards to prevent Draft Testing Mode data (forms/submissions) from leaking into the main production project.
- **Improved Materialization**: Separated username hint storage from session user_name to allow better credential pre-filling without logic loops.

### Technical Details
- **Version Code**: 5117
- **Base Version**: Upstream ODK Collect v2026.1.2
- **Test Coverage**: 181 unit tests (including 30+ new QR parser and flow contract tests).

## [v2026.1.2-MEDRES-RC1] - 2026-03-29
### Added
- **Upstream Migration**: Successfully merged ODK Collect v2026.1.2 (from v2025.3.3).
- **QR Flow Rewrite (Initial)**: Introduced `MedresQrParser` and `MedresQrStagingStore` to replace ad-hoc URL substring checks.
- **Security Guardrails**: Added 5-layer validation for QR payloads including size limits and compression ratio checks.
- **Intelligent QR Detection**: Automatic branching between Production Project QRs and Draft Testing QRs.

### Technical Details
- **Version Code**: 5116
- **Base Version**: Upstream ODK Collect v2026.1.2

---

## [v2025.3.3-MEDRES-RC4] - 2026-01-28
### Added
- **Automated Project Details Update**: Project details (name, etc.) are now automatically fetched and updated from the server upon successful PIN entry/setup.
- **Background Update**: Returning users will see their project name refreshed silently in the background after login.
- **Integration**: Added `fetchAndUpdateProjectDetails` to `AuthManager` and integrated it with `SetupPinActivity` and `PinEntryActivity`.
- **UI Improvements**: Refactored `AuthSettingsActivity` to use the unified project update logic.

### Fixed
- **Project Name Persistence**: Resolved an issue where the generic "MEDRES Project #" would persist even after successful login.
- **Setup Flow**: Ensured the dashboard immediately reflects the correct project name on first-time setup.

---
All notable changes to MEDRES ODK Collect will be documented in this file.

## [v2025.3.3-MEDRES-RC3] - 2026-01-28

### Release Candidate 3

#### Added
- **Release Signing Guide**: Added comprehensive documentation for production release signing and certificate verification.
- **Trusted Keystore**: Successfully generated and configured a dedicated release keystore for the MEDRES flavor.

#### Technical Details
- **Version Code**: 5115
- **Version Name**: v2025.3.3-MEDRES-RC3-MEDRES
- **Min SDK**: 29 (Android 10)
- **Target SDK**: 35 (Android 15)
- **Compile SDK**: 36
- **Tested On**: Android 14 (Samsung SM-E146B)

## [v2025.3.3-MEDRES-RC2] - 2026-01-02

### Release Candidate 2

#### Fixed
- **Auth 404 Error**: Fixed missing `/v1` prefix in login URL during manual project configuration (collect-uyw)

### Technical Details
- **Version Code**: 5115
- **Version Name**: v2025.3.3-MEDRES-RC2-MEDRES

## [v2025.3.3-MEDRES-RC1] - 2026-01-01

### Release Candidate 1

**Based on**: ODK Collect v2025.3.3 (November 1, 2025)

This is the first release candidate for MEDRES ODK Collect, marking a major milestone with comprehensive MEDRES-specific customizations built on top of the standard ODK Collect application.

### Major Features

#### MEDRES Authentication Module
-   **Custom Authentication**: Replaced standard ODK authentication with Bearer Token system using `MedresAuthManager`
-   **PIN Security**: Enforced local PIN setup immediately after login with configurable PIN length (4-6 digits)
-   **Re-authentication Flow**: Automatic re-authentication on 401 responses with seamless token refresh
-   **App Lock**: Automatically locks the app when minimized; requires PIN on resume
-   **Multi-Project Support**: Seamless switching between ODK projects with isolated authentication states

#### Offline Telemetry System
-   **Offline Storage**: Room database for queuing telemetry events when device is offline
-   **Background Sync**: WorkManager-based periodic synchronization of queued telemetry
-   **Intelligent Retry**: Automatic retry with exponential backoff for failed submissions
-   **Error Handling**: Drops 4xx errors, retries 5xx and network errors

#### Accessibility Improvements
-   **WCAG 2.1 Level AA Compliance**: Full accessibility support across authentication flows
-   **Screen Reader Support**: Enhanced TalkBack support with proper content descriptions
-   **Keyboard Navigation**: Complete keyboard navigation support for all interactive elements
-   **Touch Targets**: Minimum 48dp touch targets for all interactive elements
-   **Visual Contrast**: WCAG AA compliant color contrast ratios (4.5:1 for text)
-   **Focus Indicators**: Clear visual focus indicators for keyboard navigation

### Improvements

#### Performance Optimizations
-   **Reachability Checks**: TTL-based caching (5-minute default) to reduce network calls
-   **HTTP Optimization**: Switched to HEAD requests instead of GET for reachability checks
-   **Reduced Timeouts**: Connect timeout reduced to 3s, read timeout to 3s (from 30s)
-   **Battery Efficiency**: Significant reduction in battery usage through intelligent caching

#### Project Management
-   **MEDRES Restrictions**: Disabled project creation, deletion, and QR code import for MEDRES flavor
-   **Data Isolation**: Enhanced data cleanup on logout (deletes blank forms, preserves filled forms)
-   **Settings Persistence**: Fixed manual project configuration to persist correctly

#### Developer Experience
-   **Local Development**: Custom DNS resolver (`central.local` -> `10.0.2.2`) for emulator testing
-   **SSL Bypass**: Debug builds bypass SSL verification for local development
-   **Comprehensive Documentation**: 60+ documentation files covering architecture, operations, and development

### Fixed
-   **Persistent Forms Bug**: Blank forms now properly deleted on logout
-   **PIN Persistence Bug**: PIN correctly cleared upon logout
-   **StrictMode Crash**: Database operations moved to background thread
-   **Manual Configuration**: Settings now persist correctly in `general_prefs`
-   **Dark Mode UI**: Fixed input field visibility in dark mode (white text on white background)

### Known Issues

#### Test Suite
-   **Upstream Test Failures**: Some pre-existing test failures inherited from ODK Collect v2025.3.3:
    -   `maps` module: FragmentScenario compilation errors (2 tests)
    -   `material` module: FragmentScenario compilation errors (7 tests)
    -   `collect_app` module: SSL certificate test failures (3 tests), Project settings tests (2 tests)
-   **Note**: These failures are in upstream ODK modules and do not affect MEDRES-specific functionality

### Technical Details

-   **Version Code**: 5113
-   **Version Name**: v2025.3.3-MEDRES-RC1-MEDRES
-   **Min SDK**: 21 (Android 5.0)
-   **Target SDK**: 34 (Android 14)
-   **APK Sizes**: 
    -   Release: ~24MB
    -   Debug: ~57MB

### Testing Focus for RC1

Testers should focus on:
1. **Authentication Flow**: Login, PIN setup, re-authentication on 401
2. **Offline Mode**: Telemetry queuing and synchronization
3. **Accessibility**: Screen reader support (TalkBack), keyboard navigation
4. **Network Performance**: Reachability checks, battery usage
5. **Project Management**: Project switching, data isolation on logout

### Documentation

Comprehensive documentation available in `docs/medres-custom/`:
-   Architecture: 15 documents covering design, boundaries, and technical details
-   Operations: 8 documents for deployment, versioning, and maintenance
-   Development: 10 documents for contributing and development workflows
-   Testing: 6 documents for QA and testing procedures

### Upgrade Notes

This is the first release of MEDRES ODK Collect. For future upgrades:
-   Always backup data before upgrading
-   Review breaking changes in release notes
-   Test authentication flow after upgrade
-   Verify telemetry synchronization

---

## Previous Versions

This is the initial release candidate. Previous development builds were not versioned.
