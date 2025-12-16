# AIIMS Authentication Module - Implementation Handoff

## Overview
This document provides a comprehensive overview of the AIIMS authentication module implemented for ODK Collect. The module adds a complete two-factor authentication system with email/password + PIN support.

## Architecture
- **Module Name**: `aiims_auth_module`
- **Package**: `org.aiims.odk.auth`
- **Feature Flag**: Enabled via `aiims_auth_enabled` boolean
- **Launch Mode**: Uses activity alias to switch between AIIMS and standard ODK launchers

## Key Files and Functions

### 1. Core Authentication Files

#### `/aiims_auth_module/src/main/java/org/aiims/odk/auth/activities/AiimsLoginActivity.kt`
- **Purpose**: Main login screen for email/password authentication
- **Key Functions**:
  - `onCreate()`: Sets up UI with email, password, and API URL fields
  - `attemptLogin()`: Validates input and calls authentication API
  - Checks auth state on launch to redirect to PIN entry if already logged in
- **UI Components**: AIIMS logo, email/password fields, server URL input, login button

#### `/aiims_auth_module/src/main/java/org/aiims/odk/auth/activities/SetupPinActivity.kt`
- **Purpose**: Initial PIN setup for new users
- **Key Functions**:
  - `onCreate()`: Creates PIN setup UI with user name display
  - `attemptPinSetup()`: Validates and saves 4-digit PIN
  - `loadUserData()`: Displays logged-in user's name
- **Features**: No skip option (PIN is mandatory), user name display

#### `/aiims_auth_module/src/main/java/org/aiims/odk/auth/activities/PinEntryActivity.kt`
- **Purpose**: PIN entry for returning users
- **Key Functions**:
  - `onCreate()`: Checks if PIN is set, redirects to login if not
  - `attemptPinEntry()`: Verifies PIN with attempt tracking
  - `forgotPin()`: Clears session but preserves PIN
- **Security**: Maximum 3 failed attempts before logout

#### `/aiims_auth_module/src/main/java/org/aiims/odk/auth/activities/ChangePinActivity.kt`
- **Purpose**: Allows users to change their existing PIN
- **Key Functions**:
  - `attemptChangePin()`: Validates current PIN and sets new one
  - `loadUserData()`: Displays user's name
- **Validation**: Current PIN verification, new PIN confirmation

#### `/aiims_auth_module/src/main/java/org/aiims/odk/auth/activities/AuthSettingsActivity.kt`
- **Purpose**: Settings screen accessible from main ODK menu
- **Key Functions**:
  - `loadUserData()`: Fetches and displays user information
  - `logout()`: Logs out user and clears all data
  - `changePin()`: Launches Change PIN activity
- **UI**: User details, device token (tap to copy), logout button

### 2. Authentication Management

#### `/aiims_auth_module/src/main/java/org/aiims/odk/auth/managers/AiimsAuthManager.kt`
- **Purpose**: Central authentication state management
- **Key Functions**:
  - `login()`: Handles email/password login with API integration
  - `logout()`: Clears all authentication data including PIN
  - `logoutDueToFailedPin()`: Clears auth state but preserves PIN
  - `persistAuthState()`: Saves auth state to SharedPreferences
  - `persistAuthStateWithoutClearing()`: Updates only auth state, preserves data
  - `getPersistedAuthState()`: Retrieves auth state with PIN existence check
- **States**: INITIAL, LOGGED_IN, LOGGED_OUT, REQUIRES_PIN, ERROR

#### `/aiims_auth_module/src/main/java/org/aiims/odk/auth/utils/PinManager.kt`
- **Purpose**: PIN storage and verification utilities
- **Key Functions**:
  - `savePin()`: Stores encrypted PIN
  - `verifyPin()`: Validates entered PIN against stored PIN
  - `isPinSet()`: Checks if PIN exists
  - `getFailedAttempts()`: Returns failed attempt count
  - `isMaxAttemptsReached()`: Checks if 3 attempts failed
  - `clearPin()`: Removes PIN data
- **Security**: Tracks failed attempts, prevents brute force

### 3. API Integration

#### `/aiims_auth_module/src/main/java/org/aiims/odk/auth/api/RealAuthClient.kt`
- **Purpose**: Real API client for server authentication
- **Key Functions**:
  - `login()`: Makes API call with device ID and info
  - Handles network operations on IO dispatcher
- **Features**: Device ID generation, proper error handling

#### `/aiims_auth_module/src/main/java/org/aiims/odk/auth/api/AuthApiService.kt`
- **Purpose**: Retrofit API interface definitions
- **Endpoints**: Login endpoint with device authentication

### 4. Data Models

#### `/aiims_auth_module/src/main/java/org/aiims/odk/auth/api/AuthResult.kt`
- **Purpose**: Authentication result sealed class
- **Types**: Success, RequiresPin, Error, Canceled

#### `/aiims_auth_module/src/main/java/org/aiims/odk/auth/api/User.kt`
- **Purpose**: User data model
- **Fields**: id, email, name, role, partnerId, partnerName, etc.

### 5. Utilities

#### `/aiims_auth_module/src/main/java/org/aiims/odk/auth/utils/ApiDateFormat.kt`
- **Purpose**: Date formatting for API responses
- **Functions**: Parse ISO 8601 date strings

#### `/aiims_auth_module/src/main/java/org/aiims/odk/auth/utils/DeviceInfo.kt`
- **Purpose**: Device information collection
- **Functions**: Get device ID, model, OS version

### 6. Integration Points

#### `/collect_app/src/main/java/org/odk/collect/android/mainmenu/MainMenuFragment.kt`
- **Added**: Auth Settings menu item in toolbar
- **Function**: Launches AuthSettingsActivity when clicked

#### `/collect_app/src/main/res/menu/main_menu.xml`
- **Added**: Authentication Settings menu item with settings icon

#### `/collect_app/src/main/AndroidManifest.xml`
- **Added**: Activity aliases for launcher switching
  - `AiimsLauncher`: Points to AiimsLoginActivity when enabled
- **Configuration**: Feature flag controls which launcher is active

## Authentication Flow

### 1. Initial Login Flow
```
Launch App → Check AIIMS Auth Flag
    ├─ Disabled: Standard ODK Login
    └─ Enabled: AiimsLoginActivity
          ├─ Email/Password/Login URL → API Call
          ├─ Success: Navigate to ODK Main
          └─ RequiresPin: SetupPinActivity
                ├─ Set 4-digit PIN
                └─ Save PIN → Navigate to ODK Main
```

### 2. App Relaunch Flow
```
Launch App → Check Persisted State
    ├─ No Auth: Login Screen
    ├─ LOGGED_IN + PIN Set: PinEntryActivity
    └─ REQUIRES_PIN + No PIN: SetupPinActivity
          └─ Check PIN existence → Update to LOGGED_IN if PIN exists
```

### 3. PIN Entry Flow
```
PinEntryActivity
    ├─ Enter 4-digit PIN
    ├─ Verify against stored PIN
    ├─ Success: Navigate to ODK Main
    ├─ Failed: Show attempts remaining
    └─ 3 Failed Attempts:
          ├─ logoutDueToFailedPin()
          └─ Navigate to Login (PIN preserved)
```

### 4. PIN Change Flow
```
AuthSettings → ChangePinActivity
    ├─ Enter Current PIN
    ├─ Verify Current PIN
    ├─ Enter New PIN + Confirm
    ├─ Save New PIN
    └─ Return to AuthSettings
```

## Security Features

1. **Two-Factor Authentication**: Email/password + 4-digit PIN
2. **PIN Attempt Limiting**: Maximum 3 failed attempts
3. **Session Persistence**: Remembers login state across app launches
4. **Device Authentication**: Includes device ID in API calls
5. **Token Management**: JWT token handling with expiration
6. **Audit Trail**: Failed login attempts logged

## Storage

### SharedPreferences (`aiims_auth_prefs`)
- `auth_state`: Current authentication state
- `user_data`: User information (JSON)
- `auth_token`: JWT authentication token
- `expires_at`: Token expiration timestamp
- `user_pin`: Encrypted 4-digit PIN
- `pin_updated_at`: PIN last update timestamp
- `pin_attempts`: Failed PIN attempt count

## Configuration

### Feature Flags (`aiims_config.xml`)
```xml
<bool name="aiims_auth_enabled">true</bool>
```

### Gradle Dependencies
- Retrofit2 for API calls
- Coroutines for async operations
- Material Design 3 components

## Build Instructions

1. Enable/disable via `aiims_auth_enabled` flag
2. Ensure API endpoint is configured (default: http://localhost:5175/api/)
3. Build with: `./gradlew assembleDebug`
4. Install APK: `adb install -r ODK-Collect-debug.apk`

## Known Issues & Considerations

1. **Token Expiration**: Currently handles expired tokens by requiring re-login
2. **Offline Mode**: PIN works offline but requires initial online login
3. **Multiple Devices**: Each device requires separate authentication
4. **PIN Recovery**: Currently requires full re-login if PIN is forgotten

## Future Enhancements

1. **Biometric Support**: Add fingerprint/face unlock
2. **PIN Complexity**: Allow longer or alphanumeric PINs
3. **PIN Recovery**: Implement PIN recovery via email
4. **Session Timeout**: Configurable auto-logout after inactivity
5. **Multi-User Support**: Switch between different user accounts

## Testing Checklist

- [ ] Initial login with valid credentials
- [ ] PIN setup flow completion
- [ ] App relaunch shows PIN entry
- [ ] Incorrect PIN attempts handling
- [ ] PIN change functionality
- [ ] Logout from AuthSettings
- [ ] Failed PIN logout preserves PIN
- [ ] User name display on PIN screens
- [ ] Device token display and copy
- [ ] API URL configuration