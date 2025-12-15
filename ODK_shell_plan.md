# Plan: Adding Outer Authentication Shell to ODK Collect

## Overview
Implement a custom authentication layer for ODK Collect that integrates with the School Survey backend API while preserving all existing ODK functionality.

## Current ODK Collect Authentication Flow

### Project-Based Authentication (Current)
```
[App Launch]
     |
     v
[FirstLaunchSetup?]
     |-- YES --> [FirstLaunchActivity] --(QR/Manual)--> [MainMenuActivity]
     |
     NO
     |
     v
[MainMenuActivity] --(Get Blank Forms)--> [FormDownloadListActivity]
                                         |
                                         v
                                    [Server Auth Required?]
                                         |-- YES --> [ServerAuthDialogFragment]
                                         |                      |
                                         NO                   v
                                         |              [Download Forms]
                                         |
                                         v
                                    [Local Form List]
```

### Current Key Points
- No traditional user login screen
- Authentication is server-based (username/password for ODK server)
- Works offline after initial setup
- Multiple projects with different server configs
- Server auth only when downloading/syncing forms

## Desired Authentication Flow

### Two-Factor Authentication Shell
```
[App Launch]
     |
     v
[School Survey Auth Check]
     |
     |-- No Stored Session --> [LoginActivity] --(Success)--> [PinSetupActivity]
     |                                                           |
     v                                                           v
[Session Valid?]<-------------------(PIN Success)------------------+
     |
     |-- NO --> [LoginActivity] (Force online auth)
     |
     YES
     |
     v
[Offline Period Expired?]
     |-- YES --> [LoginActivity] (Force online auth)
     |
     NO
     |
     v
[PinActivity] --(Success)--> [FirstLaunchCheck?]
                                   |
                                   |-- YES --> [FirstLaunchActivity]
                                   |
                                   NO
                                   |
                                   v
                              [MainMenuActivity]
                                   |
                                   +--> All existing ODK functionality preserved
```

### Enhanced Flow Details

#### First-Time User Flow
1. **App Launch** → LoginActivity (no session stored)
2. **Login** → Email/Password against School Survey API
3. **Login Success** → Check if PIN exists → NO → PinSetupActivity
4. **PIN Setup** → Create mandatory 4-6 digit PIN
5. **PIN Confirmation** → Verify PIN in PinSetupConfirmActivity
6. **PIN Confirmed** → Check if ODK configured → NO → FirstLaunchActivity
7. **ODK Setup** → Configure ODK project (QR/Manual)
8. **Main Menu** → Full ODK access

#### Returning User Flow
1. **App Launch** → Check session validity
2. **Session Valid** → Check offline period
3. **Within Offline Period** → PinActivity
4. **PIN Success** → MainMenuActivity (direct access)

#### Forgot PIN Flow
1. **PinActivity** → User clicks "Forgot PIN"
2. **ForgotPinActivity** → Full re-authentication with email/password
3. **Login Success** → Must create new PIN → PinSetupActivity
4. **New PIN Set** → Continue to MainMenuActivity

#### Session Expired Flow
1. **App Launch** → Session expired or offline period expired
2. **Forced Re-auth** → LoginActivity
3. **Login Success** → Check if PIN exists
4. **PIN Exists?** → YES → PinActivity → MainMenuActivity
5. **PIN Exists?** → NO → PinSetupActivity → MainMenuActivity

## Architecture Overview

### Modular Design with AIIMS Namespace

The authentication shell is designed as a separate, modular component that can be easily:
1. **Added** to any ODK Collect version
2. **Removed** to return to vanilla ODK
3. **Updated** independently of ODK core
4. **Configured** via feature flags

### Authentication Layers (Modular)
```
┌─────────────────────────────────────────────────────────┐
│                AIIMS Authentication Shell                │
│  (Namespace: org.aiims.odk.auth)                       │
│                                                         │
│  ┌─────────────────────────────────────────────────┐    │
│  │          AIIMS Auth Module                      │    │
│  │  - AiimsLoginActivity                           │    │
│  │  - AiimsPinActivity                             │    │
│  │  - AiimsAuthManager                             │    │
│  │  - AiimsSettingsActivity                        │    │
│  └─────────────────────────────────────────────────┘    │
├─────────────────────────────────────────────────────────┤
│              ODK Collect Core (Unchanged)              │
│  (Original ODK code untouched)                        │
│                                                         │
│  - MainMenuActivity                                   │
│  - FirstLaunchActivity                                │
│  - FormDownloadListActivity                           │
│  - ServerAuthDialog (ODK servers)                     │
├─────────────────────────────────────────────────────────┤
│               AIIMS-ODK Bridge Layer                    │
│  (Minimal integration points)                          │
│                                                         │
│  - AiimsAuthInterceptor                               │
│  - AiimsLaunchWrapper                                 │
│  - AiimsSettingsProvider                              │
└─────────────────────────────────────────────────────────┘
```

### Namespace Structure

```
org.aiims.odk.auth/
├── activities/
│   ├── AiimsLoginActivity.kt
│   ├── AiimsPinActivity.kt
│   ├── AiimsSettingsActivity.kt
│   └── AiimsBaseActivity.kt
├── managers/
│   ├── AiimsAuthManager.kt
│   ├── AiimsSessionManager.kt
│   └── AiimsTokenManager.kt
├── storage/
│   ├── AiimsAuthStorage.kt
│   └── AiimsSecureStorage.kt
├── api/
│   ├── AiimsAuthApi.kt
│   ├── AiimsApiClient.kt
│   └── AiimsNetworkModule.kt
├── utils/
│   ├── AiimsSecurityUtils.kt
│   └── AiimsAuthUtils.kt
└── bridge/
    ├── AiimsOdkIntegrator.kt
    └── AiimsFeatureFlag.kt
```

## Key Requirements
1. **Outer Authentication Shell**: Add login/password and PIN-based authentication before accessing ODK features
2. **API Integration**: Authenticate against School Survey backend (Node.js/PostgreSQL)
3. **Preserve ODK Functionality**: All existing features (form download, data collection) remain unchanged
4. **Session Management**: Maintain authenticated state across app usage
5. **Offline Support**: Configurable offline period with cached sessions
6. **Two-Factor Authentication**: Both email/password AND PIN are mandatory
7. **No Role Mapping**: Any authenticated user can access all ODK features
8. **Modular Design**: AIIMS namespace for easy addition/removal from any ODK version
9. **Minimal ODK Modification**: Only 2-3 integration points in original ODK code

## Modular Integration Strategy

### 1. **Feature Flag Control**
```kotlin
// AiimsFeatureFlag.kt
object AiimsFeatureFlag {
    const val AIIMS_AUTH_ENABLED = "aiims_auth_enabled"

    fun isEnabled(context: Context): Boolean {
        return context.getSharedPreferences("aiims_config", Context.MODE_PRIVATE)
            .getBoolean(AIIMS_AUTH_ENABLED, false)
    }
}
```

### 2. **Wrapper for Launch Activities**
```kotlin
// AiimsLaunchWrapper.kt
class AiimsLaunchWrapper {
    fun handleAppLaunch(activity: Activity) {
        if (AiimsFeatureFlag.isEnabled(activity)) {
            // Launch AIIMS authentication flow
            activity.startActivity(Intent(activity, AiimsLoginActivity::class.java))
            activity.finish()
        } else {
            // Use default ODK flow
            activity.startActivity(Intent(activity, MainMenuActivity::class.java))
        }
    }
}
```

### 3. **Minimal ODK Code Changes**

**Only modify these ODK files:**

1. **AndroidManifest.xml**:
```xml
<!-- Add conditional launcher -->
<activity-alias
    android:name=".AiimsLauncher"
    android:enabled="@bool/aiims_auth_enabled">
    <intent-filter>
        <action android:name="android.intent.action.MAIN" />
        <category android:name="android.intent.category.LAUNCHER" />
    </intent-filter>
</activity-alias>
```

2. **Collect.java**:
```java
@Override
public void onCreate() {
    super.onCreate();

    // Initialize AIIMS if enabled
    if (AiimsFeatureFlag.isEnabled(this)) {
        AiimsOdkIntegrator.initialize(this);
    }
}
```

3. **MainMenuActivity.kt**:
```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    // Check AIIMS authentication if enabled
    if (AiimsFeatureFlag.isEnabled(this) && !AiimsAuthManager.isAuthenticated()) {
        AiimsOdkIntegrator.requireAuthentication(this)
        return
    }

    // Continue with normal ODK initialization
    initializeMainMenu()
}
```

### 4. **Gradle Module Structure**
```
// Root build.gradle
include ':collect_app'
include ':aiims_auth_module'  // Separate module for AIIMS features

// collect_app/build.gradle
dependencies {
    implementation project(':aiims_auth_module') {
        // Only include if feature flag is enabled
        transitive = false
    }
}

// aiims_auth_module/build.gradle
android {
    namespace 'org.aiims.odk.auth'
}
```

### 5. **Configuration Class**
```kotlin
// AiimsConfig.kt
data class AiimsConfig(
    val enabled: Boolean = false,
    val apiUrl: String = "http://localhost:5174/api",
    val enforceAuth: Boolean = true,
    val pinRequired: Boolean = true,
    val offlineDays: Int = 7
)

// Configured via:
// 1. Build-time Gradle config
// 2. Runtime JSON config
// 3. Remote config server
```

## Implementation Approach

### Phase 1: AIIMS Authentication Module

#### 1.1 Create AIIMS Authentication Module
**Module**: `aiims_auth_module/`
**Package**: `org.aiims.odk.auth`

**Core Components:**
- `activities/AiimsLoginActivity.kt` - Login screen with API URL option
- `activities/AiimsPinActivity.kt` - PIN verification
- `activities/AiimsSettingsActivity.kt` - Settings and user management
- `managers/AiimsAuthManager.kt` - Central authentication logic
- `storage/AiimsAuthStorage.kt` - Secure token and data storage
- `api/AiimsAuthApi.kt` - Retrofit client for School Survey API
- `managers/AiimsSessionManager.kt` - Session lifecycle management
- `utils/AiimsSecurityUtils.kt` - Encryption and security utilities
- `bridge/AiimsOdkIntegrator.kt` - ODK integration points

#### 1.2 Update Android Manifest
**Files**:
- `collect_app/src/main/AndroidManifest.xml` - Add conditional launcher activity-alias
- `aiims_auth_module/src/main/AndroidManifest.xml` - Define AIIMS activities

**Key Changes**:
```xml
<!-- In collect_app/AndroidManifest.xml -->
<activity-alias
    android:name=".AiimsLauncher"
    android:targetActivity="org.aiims.odk.auth.activities.AiimsLoginActivity"
    android:enabled="@bool/aiims_auth_enabled">
    <intent-filter>
        <action android:name="android.intent.action.MAIN" />
        <category android:name="android.intent.category.LAUNCHER" />
    </intent-filter>
</activity-alias>

<!-- Keep original launcher as fallback -->
<activity-alias
    android:name=".OdkLauncher"
    android:targetActivity="org.odk.collect.android.mainmenu.MainMenuActivity"
    android:enabled="@bool/odk_launcher_enabled">
    <intent-filter>
        <action android:name="android.intent.action.MAIN" />
        <category android:name="android.intent.category.LAUNCHER" />
    </intent-filter>
</activity-alias>
```

#### 1.3 API Integration Layer
**Module**: `aiims_auth_module/src/main/java/org/aiims/odk/auth/api/`
- `AiimsAuthApi.kt` - Interface for auth endpoints
- `AiimsAuthRequest.kt` - Login request data classes
- `AiimsAuthResponse.kt` - Login response with token/user info
- `AiimsApiClient.kt` - Singleton Retrofit client with interceptors
- `AiimsNetworkModule.kt` - Dagger/Hilt module for DI

### Phase 2: UI Implementation

#### 2.1 Authentication Flow
1. **Initial Login**: `LoginActivity` - Email/password authentication
2. **PIN Setup**: `PinSetupActivity` - Mandatory PIN setup after successful login
3. **PIN Entry**: `PinActivity` - PIN verification for returning users
4. **Forgot PIN**: `ForgotPinActivity` - Requires full re-authentication

#### 2.2 Authentication Screens
**Files**:
- `LoginActivity.kt` - Email/password login screen with API URL option
- `PinActivity.kt` - PIN verification screen (primary for returning users)
- `PinSetupActivity.kt` - Initial PIN creation after login
- `PinSetupConfirmActivity.kt` - Confirm PIN during setup
- `ForgotPinActivity.kt` - Full re-authentication flow
- `AuthSettingsActivity.kt` - Settings screen for PIN management and user details
- `AuthPreferences.kt` - Store auth tokens and PIN hash

#### 2.3 UI Layouts
**Files**:
- `res/layout/auth_activity.xml` - Main login screen
- `res/layout/pin_activity.xml` - PIN entry screen
- `res/layout/auth_loading.xml` - Loading overlay

### Phase 3: Integration Points

#### 3.1 Update Application Class
**File**: `collect_app/src/main/java/org/odk/collect/android/Collect.java`
- Initialize AuthManager in onCreate()
- Check authentication state before proceeding
- Handle logout/session expiry

#### 3.2 Modify Main Activities
**File**: `collect_app/src/main/java/org/odk/collect/android/activities/MainMenuActivity.kt`
- Add auth check in onCreate()
- Redirect to AuthActivity if not authenticated
- Add settings option in menu for AuthSettingsActivity

#### 3.3 Protect Critical Activities
**Files** to modify:
- `FormDownloadListActivity.java`
- `InstanceUploaderListActivity.java`
- `FirstLaunchActivity.java`
- All settings screens

Add base class or check in `onCreate()`:
```kotlin
if (!AuthManager.isAuthenticated()) {
    startActivity(Intent(this, AuthActivity::class.java))
    finish()
    return
}
```

#### 3.4 Add Settings Menu Item
Update main menu to include settings:
```xml
<!-- In main_menu.xml -->
<item
    android:id="@+id/auth_settings"
    android:title="Authentication Settings"
    android:icon="@drawable/ic_settings_24"
    app:showAsAction="ifRoom" />
```

In MainMenuFragment:
```kotlin
override fun onOptionsItemSelected(item: MenuItem): Boolean {
    when (item.itemId) {
        R.id.auth_settings -> {
            startActivity(Intent(requireContext(), AuthSettingsActivity::class.java))
            return true
        }
    }
    return super.onOptionsItemSelected(item)
}
```

### Phase 4: Backend API Integration

#### 4.1 API Configuration System

**API Endpoints (Based on API_DOCUMENTATION.md):**
- **Base URL**: Configurable (e.g., `http://localhost:5174/api`)
- **Authentication**:
  - `POST /api/auth/login` - Validate credentials, return device token
  - `POST /api/auth/verify` - Verify device token
  - `POST /api/auth/refresh` - Refresh device token
- **Data Operations**:
  - `POST /api/surveys/submit` - Submit survey forms
  - `POST /api/sync/upload` - Bulk upload forms
  - `GET /api/schools/by-partner` - Get schools for partner

**API Configuration Flow:**
1. **Initial Setup**: API URL provided during login (if different from default)
2. **Persistent Storage**: API URL saved in secure preferences
3. **Runtime Config**: Can be changed in settings screen
4. **Validation**: Test connectivity on URL change

#### 4.2 Authentication Flow with School Survey API

```kotlin
// Login Request Structure
{
  "email": "user@example.com",
  "password": "password123",
  "deviceId": "unique_device_id",
  "deviceInfo": "Samsung Galaxy S21, Android 12"
}

// Login Response
{
  "success": true,
  "user": {
    "id": "uuid",
    "email": "user@example.com",
    "role": "partner_manager|team_member|national_admin|data_manager",
    "partnerId": "uuid|null",
    "partnerName": "Partner Name|null",
    "name": "User Name"
  },
  "deviceToken": "jwt_token",
  "expiresAt": "2026-12-14T12:48:04.140Z",
  "requiresPinSetup": true
}
```

#### 4.3 Token Management
- JWT device tokens stored securely in AndroidKeyStore
- Automatic token refresh before expiry using `/api/auth/refresh`
- Token validation via `/api/auth/verify` before critical operations
- Fallback to cached credentials for offline access

### Phase 5: Data Persistence & Security

#### 5.1 Authentication Data Persistence Strategy

**Storage Architecture:**
- **EncryptedSharedPreferences**: For highly sensitive data (tokens, PINs, API URL)
- **Regular SharedPreferences**: For non-sensitive app state and settings
- **AndroidKeyStore**: For master encryption keys

**Data Classification & Storage:**

| Data Type | Storage Method | Security Level | Implementation |
|-----------|----------------|----------------|----------------|
| **Device Token** | EncryptedSharedPreferences (AES256-GCM) | 🔴 High | JWT token grants API access |
| **API URL** | EncryptedSharedPreferences | 🔴 High | Endpoint for sensitive data |
| **PIN Hash** | EncryptedSharedPreferences + Salt | 🔴 High | Local authentication key |
| **User Info** | Regular SharedPreferences | 🟡 Medium | Email, role, partner name |
| **Session State** | Regular SharedPreferences | 🟢 Low | Auth status, timestamps |
| **Settings** | Regular SharedPreferences | 🟢 Low | Offline period, preferences |

**AuthStorage Implementation:**
```kotlin
class AuthStorage @Inject constructor(@ApplicationContext private val context: Context) {
    // Encrypted storage for sensitive data
    private val encryptedPrefs = EncryptedSharedPreferences.create(
        "auth_secure",
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    // Regular storage for app state
    private val sharedPrefs = context.getSharedPreferences("auth_prefs", MODE_PRIVATE)
}
```

#### 5.2 PIN Security Implementation

**PIN Storage:**
```kotlin
fun savePin(pin: String) {
    val salt = UUID.randomUUID().toString()
    val pinHash = SecurityUtils.hashPIN(pin, salt)
    // Store as "salt:hash" format
    encryptedPrefs.edit().putString("pin_hash", "$salt:$pinHash").apply()
}

fun verifyPin(enteredPin: String): Boolean {
    val stored = encryptedPrefs.getString("pin_hash", null) ?: return false
    val (salt, hash) = stored.split(":")
    val enteredHash = SecurityUtils.hashPIN(enteredPin, salt)
    return hash == enteredHash
}
```

**PIN Security Features:**
- SHA-256 hashing with unique salt
- Failed attempt tracking (max 3 attempts)
- Force re-authentication after failed attempts
- Biometric authentication as alternative

#### 5.3 API URL Configuration Persistence

**URL Storage:**
```kotlin
// In LoginActivity - Save URL used for successful login
fun onLoginSuccess(apiUrl: String, token: String) {
    encryptedPrefs.edit()
        .putString("api_url", apiUrl)
        .putString("device_token", token)
        .putLong("token_expiry", System.currentTimeMillis() + TOKEN_DURATION)
        .apply()
}

// In AuthSettingsActivity - Update API URL
fun updateApiUrl(newUrl: String) {
    // Validate format
    if (!isValidUrl(newUrl)) throw IllegalArgumentException("Invalid URL")

    // Test connection
    testConnection(newUrl) { success ->
        if (success) {
            encryptedPrefs.edit().putString("api_url", newUrl).apply()
            // Refresh token with new URL
            refreshToken()
        }
    }
}
```

#### 5.4 Token Management

**Token Persistence:**
```kotlin
class TokenManager @Inject constructor(
    private val authStorage: AuthStorage,
    private val apiClient: ApiClient
) {
    fun saveToken(token: String, expiresAt: String) {
        authStorage.deviceToken = token
        authStorage.tokenExpiry = expiresAt.parseToMillis()
    }

    suspend fun refreshTokenIfNeeded(): Boolean {
        val currentTime = System.currentTimeMillis()
        val expiryTime = authStorage.tokenExpiry ?: return false

        // Refresh 1 day before expiry
        if (currentTime > expiryTime - TimeUnit.DAYS.toMillis(1)) {
            return try {
                val response = apiClient.refreshToken()
                saveToken(response.deviceToken, response.expiresAt)
                true
            } catch (e: Exception) {
                false
            }
        }
        return true
    }
}
```

#### 5.5 Security Measures

**Encryption:**
- AES-256-GCM for all sensitive data
- Master keys stored in AndroidKeyStore
- Unique salts for each PIN hash
- No plain text storage of passwords or tokens

**Network Security:**
- Certificate pinning for API endpoints
- HTTPS only for all authentication traffic
- Request/response logging disabled in production
- SSL/TLS validation

**Runtime Security:**
- Clear sensitive data from memory when not needed
- Minimum app backgrounding before re-auth required
- Biometric authentication option
- Secure flag on sensitive activities

**Data Retention:**
- Automatic token cleanup on expiry
- PIN reset after 3 failed attempts
- Secure data clearing on logout
- No backup of sensitive data (exclude from Auto Backup)

#### 5.6 File Locations

```
/data/data/org.odk.collect.android/
├── shared_prefs/
│   ├── auth_prefs.xml           // Regular preferences (user info, settings)
│   └── auth_secure.xml          // Encrypted preferences (tokens, PINs, API URL)
└── files/
    └── .android_secure/         // Master keys (AndroidKeyStore)
```

#### 5.7 Migration & Backup

**Data Migration:**
```kotlin
class AuthMigration {
    fun migrateFromV1() {
        // Migrate from plain text to encrypted storage
        val oldPrefs = context.getSharedPreferences("old_auth", MODE_PRIVATE)
        val token = oldPrefs.getString("token", null)
        if (token != null) {
            authStorage.deviceToken = token
            oldPrefs.edit().remove("token").apply()
        }
    }
}
```

**Backup Exclusions:**
```xml
<!-- AndroidManifest.xml -->
<application
    android:allowBackup="true"
    android:fullBackupContent="@xml/backup_descriptor">
    ...
</application>

<!-- res/xml/backup_descriptor.xml -->
<full-backup-content>
    <exclude domain="sharedpref" path="auth_secure.xml" />
</full-backup-content>
```

### Phase 6: Auth Settings Activity

#### 6.1 AuthSettingsActivity Implementation

**File**: `auth/AuthSettingsActivity.kt`

**Features**:
1. **User Information Display**:
   - Logged in user email
   - User role (partner_manager, team_member, etc.)
   - Partner name (if applicable)
   - Last login timestamp

2. **PIN Management**:
   - Change PIN button
   - Forgot PIN option
   - PIN attempts remaining display
   - Biometric enable/disable toggle

3. **API Configuration**:
   - Current API URL display
   - Change API URL option
   - Test connection button
   - API status indicator

4. **Security Settings**:
   - Offline access duration selector (7/14/30 days)
   - Auto-logout timeout setting
   - Clear stored data option

5. **Account Management**:
   - Logout button
   - Device management (view registered devices)
   - Sync status

#### 6.2 AuthSettingsActivity Layout

```xml
<!-- res/layout/auth_settings_activity.xml -->
<ScrollView>
    <LinearLayout>
        <!-- User Card -->
        <CardView>
            <TextView text="User Information" />
            <TextView id="user_email" />
            <TextView id="user_role" />
            <TextView id="partner_name" />
            <TextView id="last_login" />
        </CardView>

        <!-- PIN Management -->
        <CardView>
            <TextView text="PIN Settings" />
            <Button id="btn_change_pin" text="Change PIN" />
            <Button id="btn_forgot_pin" text="Forgot PIN" />
            <Switch id="switch_biometric" text="Enable Biometric" />
            <TextView id="pin_attempts" />
        </CardView>

        <!-- API Configuration -->
        <CardView>
            <TextView text="API Configuration" />
            <TextInputEditText id="input_api_url" />
            <Button id="btn_test_connection" text="Test Connection" />
            <TextView id="api_status" />
        </CardView>

        <!-- Offline Settings -->
        <CardView>
            <TextView text="Offline Access" />
            <Spinner id="spinner_offline_period" />
            <TextView id="last_sync" />
        </CardView>

        <!-- Actions -->
        <Button id="btn_logout" text="Logout" style="danger" />
        <Button id="btn_clear_data" text="Clear All Data" style="outline" />
    </LinearLayout>
</ScrollView>
```

#### 6.3 Settings Persistence

```kotlin
// AuthSettings.kt
object AuthSettings {
    const val KEY_API_URL = "auth_api_url"
    const val KEY_OFFLINE_PERIOD_DAYS = "offline_period_days"
    const val KEY_AUTO_LOGOUT_MINUTES = "auto_logout_minutes"
    const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"
    const val KEY_LAST_SYNC = "last_sync_timestamp"
    const val KEY_LOGIN_ATTEMPTS = "login_attempts_count"

    // Default values
    const val DEFAULT_API_URL = "http://localhost:5174/api"
    const val DEFAULT_OFFLINE_PERIOD = 7 // days
    const val DEFAULT_AUTO_LOGOUT = 30 // minutes
}
```

#### 6.4 API URL Change Flow

1. **User enters new URL** → Validate format
2. **Test Connection** → Send test request to `/api/auth/verify`
3. **Save New URL** → Update in secure preferences
4. **Refresh Token** → Request new token with new URL
5. **Update All API Calls** → Use new base URL

### Phase 6: Offline Support & Session Management

#### 6.1 Configurable Offline Period
- **Settings**: `AuthSettings.kt` - Configure offline access duration (e.g., 7, 14, 30 days)
- **Offline Validation**: Check last online authentication timestamp
- **Grace Period**: Allow offline access within configured period
- **Forced Re-auth**: Require online authentication after expiry

#### 6.2 Session Persistence
- **Session Store**: Secure JWT tokens with AndroidKeyStore
- **Last Auth Timestamp**: Track last successful online authentication
- **Auto-logout**: Configurable inactivity timeout (e.g., 30 minutes)
- **Session Refresh**: Automatic token refresh when online

#### 6.3 Offline Flow
1. Check if user has valid cached credentials
2. Verify offline period hasn't expired
3. Require PIN verification even when offline
4. Show offline status indicator
5. Queue operations until connection restored

## Implementation Order

1. **Create auth module structure**
2. **Implement API client and models**
3. **Build authentication UI screens**
4. **Update AndroidManifest and launcher**
5. **Add auth checks to main activities**
6. **Implement session management**
7. **Add PIN protection feature**
8. **Test offline scenarios**
9. **Security hardening**
10. **Integration testing**

## Critical Files to Modify

1. `AndroidManifest.xml` - Change launcher activity
2. `Collect.java` - Add auth initialization
3. `MainMenuActivity.kt` - Add auth check
4. `FirstLaunchActivity.kt` - Protect with auth
5. `FormDownloadListActivity.java` - Protect with auth
6. `InstanceUploaderListActivity.java` - Protect with auth
7. `build.gradle` - Add Retrofit/OkHttp dependencies

## New Files to Create

### AIIMS Authentication Module (`aiims_auth_module/`)

#### Activities
1. `src/main/java/org/aiims/odk/auth/activities/AiimsLoginActivity.kt` - Email/password login with API URL
2. `src/main/java/org/aiims/odk/auth/activities/AiimsPinActivity.kt` - PIN verification
3. `src/main/java/org/aiims/odk/auth/activities/AiimsPinSetupActivity.kt` - PIN creation
4. `src/main/java/org/aiims/odk/auth/activities/AiimsPinSetupConfirmActivity.kt` - PIN confirmation
5. `src/main/java/org/aiims/odk/auth/activities/AiimsForgotPinActivity.kt` - Re-authentication flow
6. `src/main/java/org/aiims/odk/auth/activities/AiimsSettingsActivity.kt` - Settings screen
7. `src/main/java/org/aiims/odk/auth/activities/AiimsBaseActivity.kt` - Base class for all AIIMS activities

#### Managers
8. `src/main/java/org/aiims/odk/auth/managers/AiimsAuthManager.kt` - Central auth logic
9. `src/main/java/org/aiims/odk/auth/managers/AiimsSessionManager.kt` - Session lifecycle
10. `src/main/java/org/aiims/odk/auth/managers/AiimsTokenManager.kt` - Token refresh logic

#### Storage
11. `src/main/java/org/aiims/odk/auth/storage/AiimsAuthStorage.kt` - Secure storage implementation
12. `src/main/java/org/aiims/odk/auth/storage/AiimsSecureStorage.kt` - EncryptedSharedPreferences wrapper

#### API Layer
13. `src/main/java/org/aiims/odk/auth/api/AiimsAuthApi.kt` - API interface
14. `src/main/java/org/aiims/odk/auth/api/AiimsApiClient.kt` - Retrofit client
15. `src/main/java/org/aiims/odk/auth/api/AiimsAuthRequest.kt` - Request models
16. `src/main/java/org/aiims/odk/auth/api/AiimsAuthResponse.kt` - Response models
17. `src/main/java/org/aiims/odk/auth/api/AiimsNetworkModule.kt` - DI module

#### Utils
18. `src/main/java/org/aiims/odk/auth/utils/AiimsSecurityUtils.kt` - Encryption and hashing
19. `src/main/java/org/aiims/odk/auth/utils/AiimsAuthUtils.kt` - Authentication utilities
20. `src/main/java/org/aiims/odk/auth/utils/AiimsConstants.kt` - Constants and keys

#### Bridge Layer (ODK Integration)
21. `src/main/java/org/aiims/odk/auth/bridge/AiimsOdkIntegrator.kt` - ODK integration points
22. `src/main/java/org/aiims/odk/auth/bridge/AiimsFeatureFlag.kt` - Feature flag management

### ODK Collect Modifications (Minimal)
1. `collect_app/src/main/java/org/odk/collect/android/Collect.java` - Add AIIMS initialization
2. `collect_app/src/main/java/org/odk/collect/android/mainmenu/MainMenuActivity.kt` - Add auth check
3. `collect_app/src/main/AndroidManifest.xml` - Add conditional launchers
4. `collect_app/src/main/res/values/aiims_config.xml` - AIIMS configuration booleans

### UI Layouts (in aiims_auth_module)
23. `src/main/res/layout/aiims_login_activity.xml`
24. `src/main/res/layout/aiims_pin_activity.xml`
25. `src/main/res/layout/aiims_pin_setup_activity.xml`
26. `src/main/res/layout/aiims_auth_loading.xml`
27. `src/main/res/layout/aiims_settings_activity.xml`

### Gradle Files
28. `aiims_auth_module/build.gradle` - Module configuration
29. `aiims_auth_module/src/main/AndroidManifest.xml` - AIIMS activities manifest

### Build Configuration
30. `collect_app/gradle.properties` - AIIMS feature flags
31. `collect_app/build.gradle` - Dependency on aiims_auth_module

## Dependencies to Add

```gradle
// Retrofit for API calls
implementation 'com.squareup.retrofit2:retrofit:2.9.0'
implementation 'com.squareup.retrofit2:converter-gson:2.9.0'

// OkHttp for networking
implementation 'com.squareup.okhttp3:okhttp:4.9.3'
implementation 'com.squareup.okhttp3:logging-interceptor:4.9.3'

// JWT token handling
implementation 'com.auth0.android:jwtdecode:2.0.1'

// AndroidX Security - For EncryptedSharedPreferences
implementation 'androidx.security:security-crypto:1.1.0-alpha06'

// Biometric authentication for PIN
implementation 'androidx.biometric:biometric:1.1.0'

// Lifecycle extensions for ViewModel
implementation 'androidx.lifecycle:lifecycle-viewmodel-ktx:2.6.2'
implementation 'androidx.lifecycle:lifecycle-livedata-ktx:2.6.2'

// Coroutines for async operations
implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3'
```

## Testing Strategy

1. **Unit Tests**: Test AuthManager, SessionManager logic
2. **Integration Tests**: Test API integration
3. **UI Tests**: Test authentication flow
4. **Security Tests**: Verify secure storage and network security

## Deployment Considerations

1. Maintain backward compatibility during rollout
2. Feature flag to enable/disable auth shell
3. Migration path for existing ODK installations
4. Documentation for administrators

## Authentication Flow Diagrams

### 1. App Launch and Authentication Check
```
┌─────────────────────────────────────────────────────────────────────────┐
│                           APP LAUNCH                                      │
└────────────────────────┬────────────────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                    AUTH STATE CHECK                                     │
│                                                                         │
│  • Check for stored session token?                                      │
│  • Check if offline period expired?                                     │
│  • Check if PIN is set?                                                 │
└────────────────────────┬────────────────────────────────────────────────┘
                         │
            ┌────────────┴────────────┐
            │                         │
            ▼                         ▼
    [No Stored Session]        [Has Session]
            │                         │
            ▼                         ▼
┌─────────────────────┐   ┌──────────────────────┐
│   LoginActivity     │   │   PinActivity        │
│                     │   │                      │
│ • Email field       │   │ • PIN keypad         │
│ • Password field    │   │ • Forgot PIN link    │
│ • Login button      │   │ • Attempts counter   │
│ • Network status    │   │ • Biometric option   │
└─────────┬───────────┘   └──────────┬───────────┘
          │                          │
          ▼                          ▼
    [Login Success]           [PIN Success?] ◄───┐
          │                          │          │
          ▼                    ┌─────┘          │
          │                    │               │
          ▼                    ▼               │
          │            [PIN Failed]            │
          │                    │               │
          │               ┌────┴─────┐          │
          │               │ForgotPin │          │
          │               │Activity │          │
          │               └────┬─────┘          │
          │                    │               │
          │                    ▼               │
          │            [Re-auth Required]       │
          │                    │               │
          │                    ▼               │
          │            ┌─────────────────────┐ │
          │            │   LoginActivity     │ │
          │            │ (With prefilled     │ │
          │            │  email)             │ │
          │            └─────────┬───────────┘ │
          │                      │           │
          │                      ▼           │
          │              [Login Success]     │
          │                      │           │
          └──────────────────────┼───────────┘
                                 │
                                 ▼
                    ┌─────────────────────┐
                    │   CHECK PIN STATE   │
                    │                     │
                    │ Does user have      │
                    │ a PIN set?          │
                    └─────────┬───────────┘
                              │
                    ┌─────────┴──────────┐
                    │                    │
                    ▼                    ▼
            [NO PIN SET]         [PIN EXISTS]
                    │                    │
                    ▼                    ▼
        ┌─────────────────────┐          │
        │  PinSetupActivity   │          │
        │                     │          │
        │ • Create PIN (4-6)  │          │
        │ • Strength meter    │          ▼
        │ • Continue button   │   ┌─────────────────────┐
        └─────────┬───────────┘   │   MainMenuActivity │
                  │               │                     │
                  ▼               │ • Fill Blank Form  │
            [PIN Created]        │ • Edit Saved Form  │
                  │               │ • Send Finalized  │
                  ▼               │ • Get Blank Form  │
        ┌─────────────────────┐   │ • Manage Forms    │
        │PinSetupConfirmAct   │   │ • View Sent Forms │
        │                     │   │ • Delete Saved   │
        │ • Confirm PIN       │   │ • Settings        │
        │ • Visual feedback   │   └─────────┬─────────┘
        │ • Success animation │             │
        └─────────┬───────────┘             ▼
                  │               ┌─────────────────────┐
                  ▼               │   ODK FEATURES      │
            [PIN Confirmed]       │                     │
                  │               │ • FormDownloadList  │
                  └───────────────┤ • BlankFormList     │
                                  │ • FormEntryActivity │
                                  │ • InstanceUploader  │
                                  │ • ServerAuthDialog  │
                                  │ • (All unchanged)   │
                                  └─────────────────────┘
```

### 2. First Launch Check (After Authentication)
```
[AUTHENTICATION SUCCESS]
        │
        ▼
┌─────────────────────────────┐
│ CHECK ODK INITIALIZATION    │
│                             │
│ • Has ODK been configured?  │
│ • Any existing projects?    │
└──────────────┬──────────────┘
               │
      ┌────────┴────────┐
      │                 │
      ▼                 ▼
[NO ODK CONFIG]    [ODK CONFIGURED]
      │                 │
      ▼                 ▼
┌─────────────────┐  ┌─────────────────┐
│FirstLaunchActivity│ │ MainMenuActivity│
│                   │ │                 │
│ • QR code scan    │ │ • All ODK      │
│ • Manual config   │ │   features      │
│ • Server settings │ │   accessible    │
│ • Project setup   │ └─────────────────┘
└─────────┬─────────┘
          │
          ▼
  [Setup Complete]
          │
          ▼
┌─────────────────┐
│MainMenuActivity │
│                 │
│ • All ODK       │
│   features      │
│   accessible    │
└─────────────────┘
```

### 3. PIN Setup Logic Flow
```
                    [LOGIN SUCCESS]
                            │
                            ▼
                ┌─────────────────────┐
                │   CHECK PIN STATE   │
                │                     │
                │ Does user have      │
                │ a PIN set?          │
                └─────────┬───────────┘
                          │
                ┌─────────┴──────────┐
                │                    │
                ▼                    ▼
        [NO PIN SET]         [PIN EXISTS]
                │                    │
                ▼                    │
        ┌─────────────────────┐    │
        │  PinSetupActivity   │    │
        │                     │    │
        │ • Create PIN (4-6)  │    │
        │ • Strength meter    │    ▼
        │ • Continue button   │  ┌─────────────────────┐
        └─────────┬───────────┘  │   MainMenuActivity │
                  │              │                     │
                  ▼              │ • Fill Blank Form  │
            [PIN Created]       │ • Edit Saved Form  │
                  │              │ • Send Finalized  │
                  ▼              │ • Get Blank Form  │
        ┌─────────────────────┐  │ • Manage Forms    │
        │PinSetupConfirmAct   │  │ • View Sent Forms │
        │                     │  │ • Delete Saved   │
        │ • Confirm PIN       │  │ • Settings        │
        │ • Visual feedback   │  └─────────┬─────────┘
        │ • Success animation │            │
        └─────────┬───────────┘            ▼
                  │            ┌─────────────────────┐
                  ▼            │   ODK FEATURES      │
            [PIN Confirmed]    │                     │
                  │            │ • FormDownloadList  │
                  └────────────┤ • BlankFormList     │
                               │ • FormEntryActivity │
                               │ • InstanceUploader  │
                               │ • ServerAuthDialog  │
                               │ • (All unchanged)   │
                               └─────────────────────┘
```

### 4. Forgot PIN Flow
```
[PinActivity]
     │
     ▼
[User Clicks "Forgot PIN"]
     │
     ▼
┌─────────────────────┐
│ ForgotPinActivity   │
│                     │
│ • Warning message   │
│ • Email prefilled   │
│ • Password required │
│ • Re-auth button    │
└─────────┬───────────┘
          │
          ▼
[Re-auth Success]
     │
     ▼
┌─────────────────────┐
│  PinSetupActivity   │
│                     │
│ • Create new PIN    │
│ (Must create new   │
│  PIN after forgot) │
└─────────┬───────────┘
          │
          ▼
[Continue with normal flow]
```

## Timeline Estimate

- **Week 1**: Phase 1 - Authentication Infrastructure
  - Create auth module structure
  - Implement API client and models
  - Set up dependency injection

- **Week 2**: Phase 2 - UI Implementation
  - Build login and PIN screens
  - Implement PIN setup flow
  - Create layouts and navigation

- **Week 3**: Phase 3 - Integration
  - Update AndroidManifest
  - Protect ODK activities
  - Implement session checks

- **Week 4**: Phase 4 - API Integration
  - Connect to School Survey backend
  - Implement token management
  - Handle authentication errors

- **Week 5**: Phase 5-6 - Security & Offline
  - Implement secure storage
  - Add offline period logic
  - Security hardening

- **Week 6**: Testing & Polish
  - Unit and integration tests
  - UI/UX refinements
  - Documentation

**Total**: 6 weeks for complete implementation

## Future ODK Updates - Easy Integration

### 1. **Version Compatibility**
The AIIMS authentication module is designed to work with ANY ODK Collect version:
- Minimal dependency on ODK internals
- Uses only public ODK APIs
- No modification of core ODK functionality

### 2. **Update Process**

#### When ODK Releases New Version:
1. **Download new ODK source**
2. **Apply these 3 minimal changes**:
   ```bash
   # 1. Add feature flag config
   cp ../aiims_shell/odk_config/aiims_config.xml new_odk/src/main/res/values/

   # 2. Update AndroidManifest.xml
   # Copy the launcher activity-alias section

   # 3. Add 5 lines to Collect.java
   # Add the AIIMS initialization check in onCreate()
   ```

#### Automated Update Script:
```bash
#!/bin/bash
# update_odk_with_aiims.sh

ODK_VERSION=$1
AIIMS_SHELL_PATH="../aiims_shell"

echo "Updating ODK $ODK_VERSION with AIIMS shell..."

# Copy feature flag config
cp $AIIMS_SHELL_PATH/odk_config/aiims_config.xml \
   odk-collect-$ODK_VERSION/collect_app/src/main/res/values/

# Update AndroidManifest
python3 $AIIMS_SHELL_PATH/scripts/update_manifest.py \
   odk-collect-$ODK_VERSION/collect_app/src/main/AndroidManifest.xml

# Update Collect.java
python3 $AIIMS_SHELL_PATH/scripts/update_collect_java.py \
   odk-collect-$ODK_VERSION/collect_app/src/main/java/.../Collect.java

# Build new version
cd odk-collect-$ODK_VERSION
./gradlew assembleRelease

echo "ODK $ODK_VERSION with AIIMS shell built successfully!"
```

### 3. **Configuration Options**

#### Build-Time Configuration:
```gradle
// gradle.properties
AIIMS_AUTH_ENABLED=true
AIIMS_DEFAULT_API_URL=https://api.aiims.gov.in
AIIMS_ENFORCE_PIN=true
AIIMS_OFFLINE_DAYS=7
```

#### Runtime Configuration:
```json
// assets/aiims_config.json
{
  "enabled": true,
  "apiUrl": "https://api.aiims.gov.in",
  "enforceAuth": true,
  "pinRequired": true,
  "offlineDays": 7,
  "customFeatures": {
    "enableBiometric": true,
    "autoSyncEnabled": true
  }
}
```

### 4. **Rollback Strategy**

If issues arise:
1. **Disable via Build**:
   ```gradle
   AIIMS_AUTH_ENABLED=false
   ```

2. **Disable at Runtime**:
   ```kotlin
   // In SettingsProvider
   settings.getUnprotectedSettings().save(AiimsFeatureFlag.AIIMS_AUTH_ENABLED, false)
   ```

3. **App Reverts** to vanilla ODK behavior automatically

### 5. **A/B Testing Support**

```kotlin
// Remote Config Integration
class AiimsRemoteConfig {
    fun shouldUseAiimsAuth(): Boolean {
        return Firebase.remoteConfig.getBoolean("aiims_auth_enabled")
    }
}
```

### 6. **Migration Path**

For existing ODK installations:
1. **Feature flag defaults to OFF** on first update
2. **User sees prompt**: "Enable AIIMS Secure Authentication?"
3. **Gradual rollout** via remote config
4. **No disruption** to existing data or workflows

### 7. **Module Independence**

The AIIMS module can be:
- **Compiled separately** as an AAR library
- **Distributed via Maven Central**
- **Updated independently** of ODK releases
- **Tested in isolation**

### 8. **Benefits for Future Updates**

- **No merge conflicts** with ODK changes
- **No dependencies** on ODK internal APIs
- **Parallel development** possible
- **Instant updates** without full app recompilation
- **Multiple ODK versions** supported simultaneously

This modular approach ensures that the AIIMS authentication shell can be easily applied to any ODK Collect version with minimal effort, while maintaining full functionality and security.

## Additional Feature: Custom Form Button

### Overview
Add a "Custom Form" button to ODK MainMenuActivity that launches a dedicated form activity with custom features for data saving, viewing, and API synchronization.

### Feature Specifications

#### 1. **Custom Form Button in Main Menu**
- **Location**: After "Manage Forms" button in MainMenuActivity
- **Icon**: `ic_description_24` or custom icon
- **Label**: "Custom Form"
- **Visibility**: Configurable via settings (default: visible)

#### 2. **CustomFormActivity Components**

##### 2.1 Data Entry Interface
```kotlin
// CustomFormActivity.kt
class CustomFormActivity : AppCompatActivity() {
    // Dynamic form fields based on configuration
    // Support for various input types: text, number, date, photo, GPS
    // Form validation
    // Draft saving capability
}
```

##### 2.2 Features Required

**Form Fields:**
- Dynamic form generation from API configuration
- Field types:
  - Text input (single/multi-line)
  - Number input (integer/decimal)
  - Date/Time picker
  - Photo capture
  - GPS location capture
  - Dropdown/Select
  - Checkbox group
  - Signature pad

**Data Management:**
- Local SQLite storage for form data
- Draft auto-save every 30 seconds
- Form versioning for edits
- Data export to CSV/Excel

**API Integration:**
- POST form data to custom API endpoint
- GET form configuration from API
- Sync status tracking
- Offline queue for pending uploads
- Retry mechanism with exponential backoff

#### 3. **File Structure**

```
collect_app/src/main/java/org/odk/collect/android/customform/
├── CustomFormActivity.kt              // Main form activity
├── CustomFormFragment.kt              // Form UI fragment
├── CustomFormViewModel.kt             // MVVM logic
├── models/
│   ├── CustomFormData.kt              // Data model
│   ├── FormField.kt                   // Field definition
│   └── FormConfig.kt                  // Form configuration
├── adapters/
│   ├── CustomFormAdapter.kt           // RecyclerView adapter
│   └── FieldViewHolder.kt             // View holders for field types
├── api/
│   ├── CustomFormApi.kt               // API interface
│   └── ApiClient.kt                   // Retrofit client
├── database/
│   ├── CustomFormDao.kt               // Room DAO
│   └── CustomFormDatabase.kt          // Room database
└── utils/
    ├── FormValidator.kt               // Validation logic
    └── SyncManager.kt                 // Sync management
```

#### 4. **Database Schema**

```kotlin
// CustomFormData Entity
@Entity(tableName = "custom_form_data")
data class CustomFormData(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val formType: String,
    val jsonData: String,  // JSON representation of form data
    val status: SyncStatus = SyncStatus.DRAFT,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val syncedAt: Long? = null,
    val syncAttempts: Int = 0
)

enum class SyncStatus {
    DRAFT, SUBMITTED, SYNCED, FAILED
}
```

#### 5. **API Endpoints Required**

```
GET /api/forms/config/{formType}  // Get form configuration
POST /api/forms/submit           // Submit form data
GET /api/forms/{id}             // Get existing form
PUT /api/forms/{id}             // Update form
POST /api/forms/sync-batch      // Batch sync multiple forms
```

#### 6. **Implementation Steps**

**Phase 1: Basic UI**
1. Add button to main_menu.xml
2. Create CustomFormActivity with basic layout
3. Implement dynamic form field rendering
4. Add field validation

**Phase 2: Data Management**
1. Implement Room database
2. Add CRUD operations
3. Implement draft auto-save
4. Add data export functionality

**Phase 3: API Integration**
1. Set up Retrofit client
2. Implement API endpoints
3. Add sync queue for offline support
4. Implement retry logic

**Phase 4: Advanced Features**
1. Photo/GPS integration
2. Form versioning
3. Conflict resolution
4. Analytics and reporting

#### 7. **Configuration Options**

```kotlin
// Settings to add
const val KEY_CUSTOM_FORM_ENABLED = "custom_form_enabled"
const val KEY_CUSTOM_FORM_API_URL = "custom_form_api_url"
const val KEY_CUSTOM_FORM_AUTO_SYNC = "custom_form_auto_sync"
const val KEY_CUSTOM_FORM_SYNC_INTERVAL = "custom_form_sync_interval"
```

#### 8. **Permissions Required**

```xml
<!-- In AndroidManifest.xml -->
<uses-permission android:name="android.permission.CAMERA" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />
```

#### 9. **Dependencies to Add**

```gradle
// Room database
implementation 'androidx.room:room-runtime:2.4.3'
implementation 'androidx.room:room-ktx:2.4.3'
kapt 'androidx.room:room-compiler:2.4.3'

// Retrofit for API
implementation 'com.squareup.retrofit2:retrofit:2.9.0'
implementation 'com.squareup.retrofit2:converter-gson:2.9.0'

// Image handling
implementation 'com.github.bumptech.glide:glide:4.14.2'

// Signature pad
implementation 'com.github.gcacace:signature-pad:1.3.1'

// Location services
implementation 'com.google.android.gms:play-services-location:21.0.1'
```

#### 10. **Timeline for Custom Form Feature**

- **Week 1**: UI implementation and dynamic form rendering
- **Week 2**: Database integration and local storage
- **Week 3**: API integration and synchronization
- **Week 4**: Advanced features and testing

**Total**: 4 additional weeks for complete Custom Form implementation

#### 11. **User Flow**

```
MainMenuActivity
    ↓ (tap "Custom Form")
CustomFormActivity
    ↓ (fill form)
[Save Draft] / [Submit]
    ↓
Local storage
    ↓ (background)
API sync (if online)
    ↓
Confirmation/Sync status
```

This feature provides a dedicated form system that can be customized for specific data collection needs while maintaining integration with the existing ODK Collect architecture.



 here are the buttons visible in MainMenuActivity:

  Main Menu Buttons:

  1. Start New Form (enter_data)
    - Primary button (prominent styling)
    - Opens BlankFormListActivity
    - Shows available blank forms to fill
  2. Review Data (review_data)
    - Icon: Edit (pencil)
    - Opens InstanceChooserList with EDIT_SAVED mode
    - Shows draft/saved forms that can be edited
    - Displays count of unsent forms
  3. Send Data (send_data)
    - Icon: Send
    - Opens InstanceUploaderListActivity
    - Shows finalized forms ready to upload
    - Displays count of forms to send
    - Has highlightable attribute (likely shows notification when forms are ready)
  4. View Sent Forms (view_sent_forms)
    - Icon: Check Circle
    - Opens InstanceChooserList with VIEW_SENT mode
    - Shows previously submitted forms
    - Displays count of sent forms
  5. Get Blank Form (get_forms)
    - Icon: Download
    - Opens FormDownloadListActivity
    - Downloads new forms from server
    - Only visible if server is configured
  6. Manage Forms (manage_forms)
    - Icon: Delete
    - Opens DeleteFormsActivity
    - Delete blank forms or saved instances
    - Form management operations

  Additional UI Elements:

  - App Name: Shows "ODK Collect" with version number
  - Version SHA: Shows commit hash (in development builds)
  - Projects Button: In toolbar (top-right) for project switching
  - Banners:
    - Google Drive deprecation banner (for old projects)
    - Minimum SDK deprecation banner

  Button Visibility Logic:

  Buttons are conditionally shown based on:
  - Whether forms exist to review/send/view
  - Whether a server is configured (for Get Blank Form)
  - User permissions and settings

  The Start New Form button is always visible as it's the primary action. All other buttons use MainMenuButton component with consistent styling and icons.