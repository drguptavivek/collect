# Test Refactor: MEDRES Auth Module

**Status**: Completed (Commit `0be04a1a14`)
**Date**: 2025-12-30

## 1. Problem Statement
The `medres-auth-module` unit tests were suffering from flakiness and runtime failures due to two primary architectural issues:

1.  **Hard Dependency on Android KeyStore**: The `MedresSecureStorage` class relied on `EncryptedSharedPreferences`, which internally attempts to access the Android KeyStore. In Robolectric test environments, the KeyStore is often unavailable or behaves inconsistently, leading to `KeyStoreException` and `NullPointerException`.
2.  **Mocking Final Classes**: Kotlin classes are `final` by default. The tests attempted to mock `MedresAuthManager` and `MedresAuthStorage` using Mockito without the `mock-maker-inline` extension enabled. This caused "NeverWantedButInvoked" errors because the mocks were not actually proxying calls correctly, leading to test logic executing against uninitialized objects.

## 2. Solution: Dependency Inversion Architecture
To resolve this, we applied the **Dependency Inversion Principle (DIP)**. We decoupled the business logic (Managers) from the concrete data persistence layer (Storage).

### Architecture Changes

#### Before (Tightly Coupled)
```mermaid
graph LR
    Manager[MedresAuthManager] -->|Direct Dependency| ConcreteStorage[MedresSecureStorage]
    ConcreteStorage -->|Uses| KeyStore[Android KeyStore]
```
*Result: Tests crash because they implicitly load the KeyStore.*

#### After (Decoupled with Interfaces)
```mermaid
graph LR
    Manager[MedresAuthManager] -->|Depends on| Interface[<<Interface>>\nMedresSecureStorage]
    RealImpl[MedresSecureStorageImpl] ..->|Implements| Interface
    FakeImpl[FakeMedresSecureStorage] ..->|Implements| Interface
```
*Result: Tests inject `FakeMedresSecureStorage`, completely bypassing the KeyStore.*

## 3. Implementation Details

### A. Interface Extraction
We split the existing monolithic storage classes into Interfaces and Implementations.

*   **`MedresAuthStorage` (Interface)**: Defines the contract for general auth data (User ID, Project ID, Token).
*   **`MedresAuthStorageImpl`**: The original concrete implementation that uses standard `SharedPreferences`.
*   **`MedresSecureStorage` (Interface)**: Defines the contract for sensitive data (Refresh Token, PIN, Clock validation).
*   **`MedresSecureStorageImpl`**: The original concrete implementation that uses `EncryptedSharedPreferences`.

### B. The "Fake" Pattern (Test Doubles)
Instead of using fragile Mockito mocks, we implemented **Fakes** in the test source set. Fakes are working implementations that store state in memory.

*   **`FakeMedresAuthStorage`**: Uses a `MutableMap<String, Any?>` to store preferences.
*   **`FakeMedresSecureStorage`**: Simulates secure storage behavior in memory.

**Why Fakes?**
*   **Behavioral Verification**: Fakes maintain state. If you call `saveToken("abc")`, a subsequent call to `getToken()` actually returns `"abc"`. Mocks require manual stubbing for every single interaction (`when(mock.getToken()).thenReturn(...)`), which is error-prone.
*   **Speed**: Fakes run instantly in memory without invoking Android framework code.

### C. Dependency Injection Updates
We updated `DaggerSetup.kt` to bind the new interfaces to their real implementations for the production app.

```kotlin
@Provides
fun providesMedresAuthStorage(context: Context): MedresAuthStorage {
    return MedresAuthStorageImpl.getInstance(context) // Inject Real Impl
}
```

## 4. Test Refactoring

### `MedresAuthManagerTest`
*   **Old Approach**: Used `@Mock lateinit var storage: MedresAuthStorage`. Failed because the class was final.
*   **New Approach**:
    ```kotlin
    private lateinit var authStorage: FakeMedresAuthStorage
    
    @Before
    fun setUp() {
        authStorage = FakeMedresAuthStorage() // Use Fake
        authManager = MedresAuthManager(..., authStorage, ...)
    }
    ```

### `MedresAppLockTest`
This test was refactored to verify the integration between the AppLock logic and the Auth Manager.

*   **Old Approach**: Mocked `MedresAuthManager`. Failed with `NeverWantedButInvoked` because the mock didn't update its internal state (`LOGIN` vs `LOGOUT`).
*   **New Approach**: Used the **Real** `MedresAuthManager` injected with **Fakes**.
    *   Added `@VisibleForTesting fun setIsSoftExpiry(value: Boolean)` to `MedresAuthManager` to allow tests to forcefully simulate soft token expiry without complex time/network manipulation.

## 5. Summary of Files Changed

| Component | File | Change Description |
| :--- | :--- | :--- |
| **Interfaces** | `MedresAuthStorage.kt`<br>`MedresSecureStorage.kt` | Extracted public API contracts. |
| **Implementations** | `MedresAuthStorageImpl.kt`<br>`MedresSecureStorageImpl.kt` | Renamed original classes to implement interfaces. |
| **Fakes** | `FakeMedresAuthStorage.kt`<br>`FakeMedresSecureStorage.kt` | Created in-memory implementations for testing. |
| **DI** | `DaggerSetup.kt` | Updated Dagger modules to provide `*Impl` classes. |
| **Tests** | `MedresAuthManagerTest.kt`<br>`MedresAppLockTest.kt` | Rewritten to use Fakes instead of Mocks. |
