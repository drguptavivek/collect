package org.aiims.odk.auth.utils

object AiimsConstants {

    // SharedPreferences Keys
    const val AIIMS_PREFS_NAME = "aiims_auth_prefs"
    const val AIIMS_SECURE_PREFS_NAME = "aiims_auth_secure"

    // Authentication State
    const val KEY_IS_AUTHENTICATED = "is_authenticated"
    const val KEY_AUTH_TOKEN = "auth_token"
    const val KEY_TOKEN_EXPIRY = "token_expiry"
    const val KEY_LAST_AUTH_TIMESTAMP = "last_auth_timestamp"

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

    // Feature Flags
    const val KEY_AIIMS_AUTH_ENABLED = "aiims_auth_enabled"
    const val KEY_DEBUG_MODE = "debug_mode"
    const val KEY_FIRST_LAUNCH = "first_launch"

    // Default Values
    const val DEFAULT_API_URL = "http://localhost:5174/api"
    const val DEFAULT_API_VERSION = "v1"
    const val MAX_PIN_ATTEMPTS = 3
    const val PIN_LENGTH_MIN = 4
    const val PIN_LENGTH_MAX = 6

    // Time Constants (in milliseconds)
    const val DAY_IN_MS = 24 * 60 * 60 * 1000L
    const val HOUR_IN_MS = 60 * 60 * 1000L
    const val MINUTE_IN_MS = 60 * 1000L

    // Biometric Constants
    const val BIOMETRIC_PROMPT_TITLE = "AIIMS Authentication"
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
    const val NOTIFICATION_CHANNEL_AUTH = "aiims_auth_channel"
    const val NOTIFICATION_CHANNEL_SYNC = "aiims_sync_channel"

    // Logging Tags
    const val TAG_AUTH = "AiimsAuth"
    const val TAG_PIN = "AiimsPin"
    const val TAG_BIOMETRIC = "AiimsBiometric"
    const val TAG_API = "AiimsApi"
    const val TAG_STORAGE = "AiimsStorage"

    // Date Formats
    const val API_DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"
}
