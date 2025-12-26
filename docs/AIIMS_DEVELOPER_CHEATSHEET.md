# AIIMS ODK Collect - Developer Cheat Sheet

## 🚀 Local Development Setup

### 1. Backend Connectivity (Emulator)
*   **Problem**: The custom backend runs on your machine (`localhost`), but the Android Emulator sees `localhost` as itself.
*   **Solution**: The app (in DEBUG builds) automatically rewrites `central.local` to `10.0.2.2`.
*   **Setup**:
    1.  Ensure your local Central backend is running and accessible at `https://central.local`.
    2.  In the App Login screen, use:
        *   **Server URL**: `https://central.local`
        *   **Project ID**: `1` (or your target project ID)

### 2. SSL / HTTPS
*   **Debug Builds**: The app uses an `UnsafeTrustManager` in Debug mode. You do **not** need to install self-signed certificates on the emulator.
*   **Release Builds**: Strict SSL is enforced. Ensure your production server has a valid certificate.

## 🛠️ Debugging Tools

### Viewing Auth Token
1.  Navigate to **Admin Settings** -> **User Settings**.
2.  The current **Bearer Token** is displayed at the top of the screen.

### Inspecting Layouts
*   `ui_dump.xml` may be generated in the app storage during UI tests.

### Logs
*   Filter Logcat for `AiimsAuthManager` or `AiimsAppLock` to see authentication state transitions and PIN lock events.

## 📦 Build Configuration

### Gradle Modules
*   `:collect_app`: The main app module.
*   `:aiims-auth-module`: Contains all custom logic (Auth, PIN, API).
*   `:projects`, `:settings`, etc.: ODK shared modules.

### ProGuard / R8
*   **Important**: If you modify API models (`User`, `LoginResponse`), ensure they are kept in `proguard-rules.txt` or `consumer-rules.pro` to prevent Release build crashes.
    ```proguard
    -keep class org.aiims.odk.auth.api.** { *; }
    ```
