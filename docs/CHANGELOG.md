# Changelog

## [v2025.1.0-RC1] - 2025-12-18

### Release Candidate 1
This is the first Release Candidate for the new AIIMS Authentication Module integration.

### Added
-   **Custom Authentication**: Replaced standard ODK Auth with a Bearer Token system using `AiimsAuthManager`.
-   **PIN Security**: Enforced local PIN setup immediately after login.
-   **App Lock**: Automatically locks the app when minimized; requires PIN on resume.
-   **Multi-Project Support**: Seamless switching between ODK projects with isolated authentication states.
-   **Local Development Support**: Integrated custom DNS resolver (`central.local` -> `10.0.2.2`) and SSL bypass for debug builds.

### Changed
-   **Logout Behavior**: Logging out now triggers a `ProjectCleaner` that:
    -   Deletes all **Blank Forms** to ensure data isolation between users.
    -   Preserves **Saved Instances** (Filled Forms) to allow multi-user workflows.
    -   Clears local caches and refreshes the UI immediately.
    -   Clears the local PIN.

### Fixed
-   **Persistent Forms Bug**: Fixed an issue where blank forms were not deleted on logout due to correct ODK UUIDs not being resolved.
-   **PIN Persistence Bug**: Fixed an issue where the PIN was not cleared upon logout.
-   **StrictMode Crash**: Fixed a crash during data cleanup by offloading database operations to a background thread.
-   **Manual Configuration**: Fixed manual project configuration to correctly write to `general_prefs` instead of project-specific files, ensuring settings persist.

### Dependencies
-   Updated `collect_app/build.gradle` version code to `5113`.
