# Logging & Diagnostics

Troubleshooting field issues in a secure environment requires robust, offline-capable diagnostic tools. MEDRES ODK Collect includes a secure log export utility designed for remote support without compromising PII (Personally Identifiable Information).

---

## 1. Encrypted Local Logging
- **Implementation**: The `MedresFileLogger` captures critical auth events, network failures, and system crashes.
- **Storage**: Logs are stored in a private, app-internal directory, shielded from other applications.
- **Retention**: Periodic log rotation ensures that diagnostic data does not consume excessive device storage.
- **References**: `MedresFileLogger`, `MedresFileLoggerTest`.

## 2. Secure Log Export
- **Scenario**: A user encounters a recurring bug and needs to provide logs to the technical team.
- **Protection**: **FileProvider Integration**.
- **Implementation**: The app uses an Android `FileProvider` to share log files via the system "Share" sheet (e.g., via Email or WhatsApp).
- **Security**: Logs are shared via URI permissions rather than being copied to public storage, mitigating the risk of persistent data leaks.
- **References**: `collect-ddn`, `collect-92m`.

## 3. Diagnostics UI
- **Location**: Found under **Auth Settings > Diagnostics**.
- **Features**:
    - **Export Logs**: Generates the latest log bundle for sharing.
    - **Clear Logs**: Allows the user to manually purge diagnostic data.
    - **System Summary**: Displays current token status, clock drift, and sync health.
- **References**: `AuthSettingsActivity`.

## 4. Resilience
- **Write Failure Handling**: The logger gracefully handles disk-full scenarios by pausing writes instead of crashing the host process.
- **Async Execution**: Logging operations are offloaded to a background thread to ensure zero impact on UI performance.

---

## Technical Reference
- **Provider**: `edu.aiims.medresodk.auth.utils.MedresLogFileProvider`
- **Exporter**: `MedresFileLogger.exportLogs()`
