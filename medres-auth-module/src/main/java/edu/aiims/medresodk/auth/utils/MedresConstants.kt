package edu.aiims.medresodk.auth.utils

object MedresConstants {

    // SharedPreferences Keys
    const val MEDRES_PREFS_NAME = "medres_auth_prefs"
    const val MEDRES_SECURE_PREFS_NAME = "medres_auth_secure"

    // Authentication State
    const val KEY_IS_AUTHENTICATED = "is_authenticated"
    const val KEY_AUTH_TOKEN = "auth_token"
    const val KEY_TOKEN_EXPIRY = "token_expiry"
    const val KEY_LAST_AUTH_TIMESTAMP = "last_auth_timestamp"
    const val KEY_PROJECT_ID = "project_id"  // Project ID for token validation
    const val KEY_IS_SOFT_EXPIRY = "is_soft_expiry" // Persisted grace period state

    // User Information
    const val KEY_USER_ID = "user_id"
    const val KEY_USER_EMAIL = "user_email"
    const val KEY_USER_NAME = "user_name"

    // PIN Security
    const val KEY_PIN_HASH = "pin_hash"
    const val KEY_PIN_SALT = "pin_salt"
    const val KEY_PIN_ATTEMPTS = "pin_attempts"
    const val KEY_LAST_PIN_ATTEMPT = "last_pin_attempt"
    const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"
    const val KEY_BIOMETRIC_KEY_ALIAS = "biometric_key_alias"

    // API Configuration
    const val KEY_API_URL = "api_url"
    const val KEY_API_VERSION = "api_version"
    const val KEY_DEV_SERVER_IP = "dev_server_ip" // DEBUG only - overrides base URL

    // Auth Persistence Keys
    const val KEY_AUTH_URL = "auth_url"
    const val KEY_AUTH_PROJECT_ID = "auth_project_id"
    const val KEY_AUTH_PROJECT_NAME = "auth_project_name"
    const val KEY_QR_GENERAL_SETTINGS = "qr_general_settings"
    const val KEY_QR_ADMIN_SETTINGS = "qr_admin_settings"

    // Feature Flags
    const val KEY_MEDRES_AUTH_ENABLED = "medres_auth_enabled"
    const val KEY_DEBUG_MODE = "debug_mode"
    const val KEY_FIRST_LAUNCH = "first_launch"

    // Default Values
    const val DEFAULT_API_URL = "http://localhost:5174/api"
    const val DEFAULT_API_VERSION = "v1"
    const val MAX_PIN_ATTEMPTS = 3
    const val PIN_LENGTH_MIN = 4
    const val PIN_LENGTH_MAX = 6
    const val MAX_QR_PAYLOAD_SIZE = 4096 // 4KB limit for QR code payload
    const val MAX_QR_DECOMPRESSED_SIZE = 16384 // 16KB limit for decompressed payload (prevents decompression bombs)

    // Time Constants (in milliseconds)
    const val DAY_IN_MS = 24 * 60 * 60 * 1000L
    const val HOUR_IN_MS = 60 * 60 * 1000L
    const val MINUTE_IN_MS = 60 * 1000L
    const val GRACE_PERIOD_MS = 6L * HOUR_IN_MS

    // Expiry Reminder Tiers (in milliseconds before expiry)
    val EXPIRY_REMINDER_TIERS_MS = listOf(
        8L * HOUR_IN_MS,   // 8 hours
        3L * HOUR_IN_MS,   // 3 hours
        1L * HOUR_IN_MS,   // 1 hour
        30L * MINUTE_IN_MS, // 30 minutes
        15L * MINUTE_IN_MS, // 15 minutes
        3L * MINUTE_IN_MS   // 3 minutes
    )

    // Grace Period Notification Thresholds (Remaining time)
    val GRACE_NOTIFICATION_MARKS_MS = listOf(
        4L * HOUR_IN_MS,   // 4 hours remaining
        1L * HOUR_IN_MS,   // 1 hour remaining
        15L * MINUTE_IN_MS  // 15 minutes remaining
    )

    // Telemetry Configuration
    const val TELEMETRY_SYNC_INTERVAL_MINUTES = 20L

    // Biometric Constants
    const val BIOMETRIC_PROMPT_TITLE = "MEDRES Authentication"
    const val BIOMETRIC_PROMPT_SUBTITLE = "Use your fingerprint to authenticate"
    const val BIOMETRIC_PROMPT_NEGATIVE = "Cancel"

    // Error Codes
    object ErrorCodes {
        const val NETWORK_ERROR = 1001
        const val INVALID_CREDENTIALS = 1002
        const val TOKEN_EXPIRED = 1003
        const val TOKEN_INVALID = 1004
        const val USER_NOT_FOUND = 1005
        const val PIN_INCORRECT = 2001
        const val PIN_LOCKED = 2002
        const val BIOMETRIC_ERROR = 3001
        const val STORAGE_ERROR = 4001
        const val CONFIGURATION_ERROR = 5001
    }

    // Intent Extras
    const val EXTRA_EMAIL = "extra_email"
    const val EXTRA_FORCE_REAUTH = "extra_force_reauth"
    const val EXTRA_SHOW_PIN_SETUP = "extra_show_pin_setup"
    const val EXTRA_API_URL = "extra_api_url"
    const val EXTRA_DEEP_LINK_DATA = "extra_deep_link_data"

    // Request Codes
    const val REQUEST_CODE_BIOMETRIC = 1001
    const val REQUEST_CODE_SETTINGS = 1002
    const val REQUEST_CODE_CHANGE_API = 1003

    // Notification Channels
    const val NOTIFICATION_CHANNEL_AUTH = "medres_auth_channel"
    const val NOTIFICATION_CHANNEL_SYNC = "medres_sync_channel"

    // Logging Tags
    const val TAG_AUTH = "MedresAuth"
    const val TAG_PIN = "MedresPin"
    const val TAG_BIOMETRIC = "MedresBiometric"
    const val TAG_API = "MedresApi"
    const val TAG_STORAGE = "MedresStorage"

    // Date Formats
    const val API_DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"

    // Clock Validation Constants
    const val CLOCK_MANIPULATION_THRESHOLD_MS = 30 * 60 * 1000L // 30 minutes threshold
    const val KEY_LAST_VALID_WALL_TIME = "last_valid_wall_time"
    const val KEY_LAST_ELAPSED_REALTIME = "last_elapsed_realtime"
    const val KEY_SERVER_TIME_OFFSET_MS = "server_time_offset_ms"
    const val KEY_CLOCK_MANIPULATION_DETECTED = "clock_manipulation_detected"
}
