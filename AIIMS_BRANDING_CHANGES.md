# AIIMS ODK Collect - Branding Changes

## Overview

The AIIMS authentication module includes a complete branding solution that transforms ODK Collect into "AIIMS ODK Collect" when authentication is enabled. The branding automatically reverts when authentication is disabled.

## Branding Elements

### 1. App Name and Logo

**When AIIMS Auth is Enabled:**
- App Name: **"AIIMS ODK Collect"**
- App Icon: AIIMS logo (A7226E)
- Tagline: "School Eye Health Survey"

**When AIIMS Auth is Disabled:**
- App Name: "ODK Collect"
- App Icon: Original ODK logo
- No AIIMS branding visible

### 2. Color Scheme

AIIMS brand colors based on AIIMS visual identity:

#### Primary Colors
- **Primary**: `#005D8B` (AIIMS Blue)
- **Primary Dark**: `#004575`
- **Primary Light**: `#0076A3`
- **Primary Container**: `#E0EFFF`

#### Secondary Colors
- **Secondary**: `#5D8B00` (AIIMS Green)
- **Secondary Dark**: `#4A7000`
- **Secondary Light**: `#7BA31F`
- **Secondary Container**: `#E8FFD4`

#### Status Colors
- **Success**: `#2E7D32` (Green)
- **Warning**: `#ED6C02` (Orange)
- **Error**: `#D32F2F` (Red)
- **Info**: `#0288D1` (Blue)

### 3. Typography

Using Material 3 type scale:
- **Headline Large**: 32sp, Bold
- **Headline Medium**: 28sp, Medium
- **Body Large**: 16sp, Regular
- **Body Medium**: 14sp, Regular

### 4. UI Components

#### Buttons
- Filled buttons with AIIMS primary color
- Outlined buttons for secondary actions
- Text buttons for tertiary actions

#### Text Fields
- Outlined boxes with floating labels
- AIIMS primary color for focused state
- Icons for email, password, and API URL

#### Cards
- Rounded corners (16dp)
- Subtle elevation (4dp)
- Clean white background

### 5. Splash Screen

Features:
- AIIMS logo prominently displayed (200dp)
- App name "AIIMS ODK Collect" in bold
- Tagline "School Eye Health Survey"
- Loading indicator during initialization
- Clean AIIMS blue background

### 6. Login Screen

Features:
- AIIMS logo and branding
- Clean card-based layout
- Email and password fields with icons
- Optional API URL configuration
- Loading states with progress indicators
- Offline mode indicator

## Implementation Details

### Resource Files Created

```
aiims_auth_module/src/main/res/
├── drawable/
│   ├── aiims_logo.png                 # AIIMS logo
│   └── offline_indicator_background.xml
├── layout/
│   ├── aiims_splash_activity.xml      # Splash screen layout
│   └── aiims_login_activity.xml       # Login screen layout
├── values/
│   ├── strings.xml                    # All text strings
│   ├── colors.xml                     # AIIMS color palette
│   ├── dimens.xml                     # Spacing and sizing
│   └── themes.xml                     # Material 3 theme
```

### AndroidManifest.xml

```xml
<application
    android:label="@string/aiims_app_name"
    android:icon="@drawable/aiims_logo"
    android:roundIcon="@drawable/aiims_logo"
    android:theme="@style/Theme.AiimsAuth">
    ...
</application>
```

### Theme Configuration

```xml
<style name="Theme.AiimsAuth" parent="Theme.Material3.DayNight">
    <item name="colorPrimary">@color/aiims_primary</item>
    <item name="colorSecondary">@color/aiims_secondary</item>
    <!-- Additional theme attributes -->
</style>
```

## Conditional Branding

The branding is controlled by the feature flag:

```kotlin
// In AiimsFeatureFlag
fun isEnabled(context: Context): Boolean {
    return context.getSharedPreferences("aiims_config")
        .getBoolean("aiims_auth_enabled", false)
}
```

### When Enabled (`aiims_auth_enabled = true`)
- App icon changes to AIIMS logo
- App name changes to "AIIMS ODK Collect"
- AIIMS theme applied to all auth screens
- AIIMS branding in launcher and recent apps

### When Disabled (`aiims_auth_enabled = false`)
- Original ODK branding remains
- No AIIMS visual elements visible
- App functions as vanilla ODK Collect

## User Experience

1. **First Launch** (AIIMS enabled):
   - Shows AIIMS splash screen
   - App appears as "AIIMS ODK Collect" in launcher

2. **Authentication Flow**:
   - Consistent AIIMS branding throughout
   - Professional, trustworthy appearance
   - Clear visual hierarchy

3. **Main App** (after auth):
   - Seamless transition to ODK interface
   - AIIMS branding visible in app name only
   - All ODK functionality preserved

## Benefits of AIIMS Branding

1. **Professional Identity**: Clear association with AIIMS institution
2. **Trust Building**: Users recognize the trusted AIIMS brand
3. **Distinctive Look**: Differentiates from standard ODK Collect
4. **Institution Pride**: Promotes AIIMS brand visibility
5. **User Confidence**: Professional appearance builds user trust

## Technical Considerations

1. **Asset Management**: AIIMS logo included in module resources
2. **Theme Inheritance**: Extends Material 3 for consistency
3. **Dark Mode Support**: All colors work in light/dark themes
4. **Scalability**: Vector or PNG resources for all densities
5. **Accessibility**: Proper contrast ratios for all text

The AIIMS branding creates a professional, trustworthy appearance while maintaining full ODK Collect functionality and providing seamless user experience.