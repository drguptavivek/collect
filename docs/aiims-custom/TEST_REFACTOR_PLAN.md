# Test Refactor Plan: AIIMS Auth Module

## Problem
The current unit tests for `AiimsAuthManager` and related components are failing or flaky due to:
1.  **Reliance on `AndroidKeyStore`**: `AiimsSecureStorage` uses `EncryptedSharedPreferences`, which depends on the Android KeyStore. This component is not fully available or functional in specific Robolectric environments, leading to `KeyStoreException` or `NullPointerException`.
2.  **Mocking Final Classes**: Attempts to mock `AiimsAuthStorage` (a final Kotlin class) using Mockito fail because the project does not have `mock-maker-inline` enabled/configured by default. This results in errors when trying to stub properties or methods.
3.  **Inconsistency with ODK Patterns**: Standard ODK Collect architecture prefers using **Fakes** (in-memory implementations) or **Interfaces** for data layers to facilitate robust testing, rather than complex mocking of concrete Android-dependent classes.

## Goal
Standardize `aiims-auth-module` testing to align with ODK Collect patterns, ensuring tests are:
-   **Reliable**: Run consistently without flaky dependencies on Android system components (KeyStore).
-   **Fast**: Use in-memory Fakes.
-   **Maintainable**: Avoid complex Mockito syntax for final classes.

## Proposed Changes

### 1. Extract Interfaces
Convert concrete storage classes into interfaces to decouple implementation details.

*   **`AiimsAuthStorage`** -> `AiimsAuthStorage` (Interface) + `AiimsAuthStorageImpl` (Real)
*   **`AiimsSecureStorage`** -> `AiimsSecureStorage` (Interface) + `AiimsSecureStorageImpl` (Real)

### 2. Create Fakes (Test Source Set)
Implement in-memory versions of these interfaces for usage in tests.

*   **`FakeAiimsAuthStorage`**: Wraps a `MutableMap<String, Any?>` to store auth state (token, projectId, etc.).
*   **`FakeAiimsSecureStorage`**: Wraps a `MutableMap` to store secure preferences (clock validation data).

### 3. Refactor Consumers
Update `AiimsAuthManager`, `ClockValidator`, and other consumers to depend on the *Interfaces* rather than concrete classes.

### 4. Update DI (Dagger)
Update `AuthModule` (or `DaggerSetup`) to provide `AiimsAuthStorageImpl` when `AiimsAuthStorage` is requested.

### 5. Rewrite Tests
Refactor `AiimsAuthManagerTest` and `AiimsTelemetryTest` to:
-   Instantiate `FakeAiimsAuthStorage` and `FakeAiimsSecureStorage` in `setUp()`.
-   Pass these fakes to the Manager.
-   Assert state against the Fakes directly (e.g., `fakeStorage.assertToken("foo")`) instead of `verify(mock)`.

## Verification
-   Run `./gradlew :aiims-auth-module:testDebugUnitTest`
-   Ensure all tests pass without `KeyStore` errors.

## Impact Analysis
This change is **purely structural** and has **zero impact** on the runtime behavior of the released app:
1.  **Runtime**: The dependency injection will be updated to provide `AiimsAuthStorageImpl` (which contains the exact same code as the original class) whenever the interface is requested. The app logic remains identical.
2.  **Safety**: We are not changing *how* data is stored, only *how* the code refers to the storage class.
3.  **Performance**: No measurable difference (interface dispatch is negligible).

