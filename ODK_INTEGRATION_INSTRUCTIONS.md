# ODK Collect Integration Instructions for AIIMS Authentication

This document describes how to integrate the AIIMS authentication shell into ODK Collect with minimal code changes.

## Overview

The AIIMS authentication module is designed as a separate, modular component that can be easily added to or removed from any ODK Collect version without modifying core functionality. When enabled, the app will be branded as **"AIIMS ODK Collect"** with the AIIMS logo.

## Integration Steps

### 1. Update Settings Gradle

Add the AIIMS module to your settings.gradle.kts:

```kotlin
// In settings.gradle.kts
include ':collect_app'
include ':aiims_auth_module'  // Add this line
```

### 2. Update Collect App Build Gradle

Add the AIIMS module dependency to collect_app/build.gradle:

```gradle
// In collect_app/build.gradle
dependencies {
    // ... existing dependencies

    // Add AIIMS authentication module
    implementation project(':aiims_auth_module')

    // Required dependencies if not already present
    implementation 'com.google.dagger:hilt-android:2.48.1'
    kapt 'com.google.dagger:hilt-compiler:2.48.1'
}

// Add Hilt plugin if not already present
plugins {
    id 'dagger.hilt.android.plugin'
}
```

### 3. Update AndroidManifest.xml

Add conditional launcher activities to collect_app/src/main/AndroidManifest.xml:

```xml
<!-- Add before the closing </application> tag -->

<!-- AIIMS Launcher (enabled when auth feature flag is true) -->
<activity-alias
    android:name=".AiimsLauncher"
    android:targetActivity="org.aiims.odk.auth.activities.AiimsLoginActivity"
    android:enabled="@bool/aiims_auth_enabled">
    <intent-filter>
        <action android:name="android.intent.action.MAIN" />
        <category android:name="android.intent.category.LAUNCHER" />
    </intent-filter>
</activity-alias>

<!-- Original ODK Launcher (fallback when auth is disabled) -->
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

### 4. Create AIIMS Configuration Resources

Create collect_app/src/main/res/values/aiims_config.xml:

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <!-- Feature flags for AIIMS authentication -->
    <bool name="aiims_auth_enabled">false</bool>
    <bool name="odk_launcher_enabled">true</bool>

    <!-- Default configuration values -->
    <string name="aiims_default_api_url">http://localhost:5174/api</string>
    <integer name="aiims_default_offline_period">7</integer>
    <integer name="aiims_default_auto_logout">30</integer>
</resources>
```

### 5. Update Collect.java

Add AIIMS initialization to Collect.java:

```java
// In org.odk.collect.android.application.Collect

// Add these imports
import org.aiims.odk.auth.bridge.AiimsFeatureFlag;
import org.aiims.odk.auth.bridge.AiimsOdkIntegrator;
import dagger.hilt.android.HiltAndroidApp;

// Add @HiltAndroidApp annotation if not present
@HiltAndroidApp
public class Collect extends Application {
    // ... existing code

    @Override
    public void onCreate() {
        super.onCreate();

        // ... existing initialization code

        // Initialize AIIMS authentication if enabled
        AiimsOdkIntegrator.initialize(this);
    }

    @Override
    public void onTerminate() {
        // Cleanup AIIMS resources
        // No cleanup needed with Hilt

        super.onTerminate();
    }
}
```

### 6. Update MainMenuActivity

Add authentication check to MainMenuActivity.kt:

```kotlin
// In org.odk.collect.android.mainmenu.MainMenuActivity

// Add these imports
import org.aiims.odk.auth.bridge.AiimsOdkIntegrator

class MainMenuActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Check AIIMS authentication before proceeding
        if (!AiimsOdkIntegrator.isUserAuthenticated(this)) {
            AiimsOdkIntegrator.requireAuthentication(this)
            return
        }

        // ... existing onCreate code
    }

    // Update options menu to include AIIMS settings
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        super.onCreateOptionsMenu(menu)

        // Add AIIMS settings menu item if auth is enabled
        if (AiimsFeatureFlag.isEnabled(this)) {
            menu.add(0, R.id.aiims_settings, 0, R.string.aiims_settings)
                .setIcon(R.drawable.ic_settings_24)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
        }

        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.aiims_settings -> {
                AiimsOdkIntegrator.launchSettings(this)
                return true
            }
            // ... handle other menu items
        }
        return super.onOptionsItemSelected(item)
    }
}
```

### 7. Update Gradle Properties

Add AIIMS configuration to gradle.properties:

```properties
# AIIMS Authentication Configuration
aiims.auth.enabled=false
aiims.default.api.url=http://localhost:5174/api
aiims.enforce.pin=true
aiims.offline.days=7
aiims.auto.logout.minutes=30
```

### 8. Add String Resources

Add AIIMS strings to collect_app/src/main/res/values/strings.xml:

```xml
<!-- AIIMS Authentication -->
<string name="aiims_settings">Authentication Settings</string>
<string name="aiims_user_info">User Information</string>
<string name="aiims_logout">Logout</string>
<string name="aiims_login_required">Login Required</string>
<string name="aiims_session_expired">Session Expired</string>
```

### 9. App Branding (When AIIMS Auth is Enabled)

When the AIIMS authentication module is enabled:

1. **App Name**: Changes to "AIIMS ODK Collect"
2. **App Icon**: Uses AIIMS logo
3. **App Theme**: AIIMS-branded Material 3 theme
4. **Colors**: AIIMS primary (blue) and secondary (green) colors

The branding automatically reverts to ODK when the authentication is disabled.

## Build Configuration

### Enable AIIMS Authentication

To enable AIIMS authentication, set the feature flag:

1. **At Build Time**:
   ```gradle
   android {
       defaultConfig {
           buildConfigField "boolean", "AIIMS_AUTH_ENABLED", "true"
       }
   }
   ```

2. **At Runtime**:
   ```kotlin
   // Enable in code
   AiimsFeatureFlag.setEnabled(context, true)
   ```

3. **Via Resources**:
   ```xml
   <!-- In aiims_config.xml -->
   <bool name="aiims_auth_enabled">true</bool>
   <bool name="odk_launcher_enabled">false</bool>
   ```

## Testing the Integration

### Test Without AIIMS Auth

1. Build with `aiims_auth_enabled=false` (default)
2. App should launch directly to ODK main menu
3. No authentication prompts

### Test With AIIMS Auth

1. Build with `aiims_auth_enabled=true`
2. App should launch to AIIMS login screen
3. After login, app proceeds to ODK main menu
4. User info available in settings

## Remote Configuration

The AIIMS module supports remote configuration via feature flags:

```kotlin
// Update configuration from remote server
val remoteConfig = mapOf(
    "aiims_auth_enabled" to true,
    "offline_period_days" to 14,
    "auto_logout_minutes" to 60
)

AiimsFeatureFlag.updateFromRemoteConfig(context, remoteConfig)
```

## Security Considerations

1. **ProGuard Rules**: Add to proguard-rules.pro:
   ```proguard
   -keep class org.aiims.odk.auth.** { *; }
   -keep class dagger.hilt.** { *; }
   -keep class javax.inject.** { *; }
   ```

2. **Network Security**: Configure certificate pinning in production
3. **Backup Exclusions**: Exclude auth data from backup:
   ```xml
   <full-backup-content>
       <exclude domain="sharedpref" path="aiims_auth_secure.xml" />
   </full-backup-content>
   ```

## Rollback Strategy

To disable AIIMS authentication:

1. **Runtime**:
   ```kotlin
   AiimsFeatureFlag.setEnabled(context, false)
   ```

2. **Build Time**:
   ```gradle
   buildConfigField "boolean", "AIIMS_AUTH_ENABLED", "false"
   ```

3. **Configuration**:
   ```xml
   <bool name="aiims_auth_enabled">false</bool>
   ```

The app will automatically revert to vanilla ODK behavior.

## Troubleshooting

### Common Issues

1. **App Crashes on Launch**:
   - Check Hilt dependencies
   - Verify @HiltAndroidApp annotation
   - Ensure module is included in settings.gradle

2. **Authentication Not Working**:
   - Verify feature flag is enabled
   - Check API URL configuration
   - Review network permissions

3. **PIN Issues**:
   - Clear app data to reset PIN
   - Check secure storage initialization

### Debug Information

Get authentication status:
```kotlin
val status = AiimsOdkIntegrator.getAuthStatus(context)
Log.d("AIIMS", status.toString())
```

## Migration Path

For existing ODK installations:

1. Update with AIIMS module (auth disabled by default)
2. Enable auth when ready
3. Users will see authentication prompt on next launch
4. Existing data remains unaffected

This modular approach ensures zero disruption to existing ODK functionality while providing optional enhanced authentication.