# Changelog

All notable changes to MEDRES ODK Collect will be documented in this file.

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
