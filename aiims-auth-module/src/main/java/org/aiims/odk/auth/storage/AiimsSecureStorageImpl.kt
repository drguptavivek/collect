package org.aiims.odk.auth.storage

import android.content.Context
import android.content.SharedPreferences
import android.os.StrictMode
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.aiims.odk.auth.utils.AiimsConstants

/**
 * Secure storage implementation using EncryptedSharedPreferences.
 *
 * Provides encrypted storage for sensitive authentication data like tokens,
 * PINs, and API URLs using Android's EncryptedSharedPreferences with
 * AES-256-GCM encryption.
 */
class AiimsSecureStorageImpl private constructor(
    private val context: Context
) : AiimsSecureStorage {

    companion object {
        private const val TAG = "AiimsSecureStorage"

        @Volatile
        private var INSTANCE: AiimsSecureStorage? = null

        fun getInstance(context: Context): AiimsSecureStorage {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: createInstance(context.applicationContext).also { INSTANCE = it }
            }
        }

        private fun createInstance(context: Context): AiimsSecureStorage {
            return AiimsSecureStorageImpl(context)
        }
    }

    // Master key for encryption
    // Suppress StrictMode for key generation as it requires disk I/O to Android Keystore
    private val masterKey: MasterKey by lazy {
        val oldPolicy = StrictMode.getThreadPolicy()
        try {
            // Temporarily allow disk I/O for key generation
            StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.LAX)
            MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
        } finally {
            StrictMode.setThreadPolicy(oldPolicy)
        }
    }

    // Encrypted preferences for sensitive data
    // Suppress StrictMode for initialization as it may require disk I/O
    private val encryptedPrefs: SharedPreferences by lazy {
        val oldPolicy = StrictMode.getThreadPolicy()
        try {
            // Temporarily allow disk I/O for encrypted prefs creation
            StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.LAX)
            EncryptedSharedPreferences.create(
                context,
                AiimsConstants.AIIMS_SECURE_PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } finally {
            StrictMode.setThreadPolicy(oldPolicy)
        }
    }

    // Regular preferences for non-sensitive data
    private val regularPrefs: SharedPreferences by lazy {
        context.getSharedPreferences(
            AiimsConstants.AIIMS_PREFS_NAME,
            Context.MODE_PRIVATE
        )
    }

    override var authToken: String?
        get() = encryptedPrefs.getString(AiimsConstants.KEY_AUTH_TOKEN, null)
        set(value) = encryptedPrefs.edit().putString(AiimsConstants.KEY_AUTH_TOKEN, value).apply()

    override var tokenExpiry: Long?
        get() = encryptedPrefs.getLong(AiimsConstants.KEY_TOKEN_EXPIRY, -1).takeIf { it != -1L }
        set(value) = encryptedPrefs.edit().putLong(AiimsConstants.KEY_TOKEN_EXPIRY, value ?: -1L).apply()

    override var projectId: String?
        get() = encryptedPrefs.getString(AiimsConstants.KEY_PROJECT_ID, null)
        set(value) = encryptedPrefs.edit().putString(AiimsConstants.KEY_PROJECT_ID, value).apply()

    // ===== PIN Security Storage =====
    override var pinHash: String?
        get() = encryptedPrefs.getString(AiimsConstants.KEY_PIN_HASH, null)
        set(value) = encryptedPrefs.edit().putString(AiimsConstants.KEY_PIN_HASH, value).apply()

    override var pinSalt: String?
        get() = encryptedPrefs.getString(AiimsConstants.KEY_PIN_SALT, null)
        set(value) = encryptedPrefs.edit().putString(AiimsConstants.KEY_PIN_SALT, value).apply()

    override var pinAttempts: Int
        get() = encryptedPrefs.getInt(AiimsConstants.KEY_PIN_ATTEMPTS, 0)
        set(value) = encryptedPrefs.edit().putInt(AiimsConstants.KEY_PIN_ATTEMPTS, value).apply()

    override var lastPinAttempt: Long?
        get() = encryptedPrefs.getLong(AiimsConstants.KEY_LAST_PIN_ATTEMPT, -1).takeIf { it != -1L }
        set(value) = encryptedPrefs.edit().putLong(AiimsConstants.KEY_LAST_PIN_ATTEMPT, value ?: -1L).apply()

    override var biometricEnabled: Boolean
        get() = encryptedPrefs.getBoolean(AiimsConstants.KEY_BIOMETRIC_ENABLED, false)
        set(value) = encryptedPrefs.edit().putBoolean(AiimsConstants.KEY_BIOMETRIC_ENABLED, value).apply()

    override var biometricKeyAlias: String?
        get() = encryptedPrefs.getString(AiimsConstants.KEY_BIOMETRIC_KEY_ALIAS, null)
        set(value) = encryptedPrefs.edit().putString(AiimsConstants.KEY_BIOMETRIC_KEY_ALIAS, value).apply()

    // ===== API Configuration Storage =====
    override var apiUrl: String
        get() = encryptedPrefs.getString(AiimsConstants.KEY_API_URL, "") ?: ""
        set(value) = encryptedPrefs.edit().putString(AiimsConstants.KEY_API_URL, value).apply()

    // ===== Clock Validation Storage (Encrypted) =====
    /**
     * Last validated wall-clock time (System.currentTimeMillis()).
     * Used as anchor point for clock manipulation detection.
     */
    override var lastValidWallTime: Long?
        get() = encryptedPrefs.getLong(AiimsConstants.KEY_LAST_VALID_WALL_TIME, -1).takeIf { it != -1L }
        set(value) = encryptedPrefs.edit().putLong(AiimsConstants.KEY_LAST_VALID_WALL_TIME, value ?: -1L).apply()

    /**
     * Corresponding monotonic time (SystemClock.elapsedRealtime()) for lastValidWallTime.
     * This cannot be manipulated and is used to calculate expected wall time.
     */
    override var lastElapsedRealtime: Long?
        get() = encryptedPrefs.getLong(AiimsConstants.KEY_LAST_ELAPSED_REALTIME, -1).takeIf { it != -1L }
        set(value) = encryptedPrefs.edit().putLong(AiimsConstants.KEY_LAST_ELAPSED_REALTIME, value ?: -1L).apply()

    /**
     * Offset between server time and local time in milliseconds.
     * Calculated during server time sync for better accuracy.
     */
    override var serverTimeOffsetMs: Long
        get() = encryptedPrefs.getLong(AiimsConstants.KEY_SERVER_TIME_OFFSET_MS, 0L)
        set(value) = encryptedPrefs.edit().putLong(AiimsConstants.KEY_SERVER_TIME_OFFSET_MS, value).apply()

    /**
     * Flag indicating whether clock manipulation has been detected.
     * When true, prevents re-authentication until user corrects device time.
     */
    override var clockManipulationDetected: Boolean
        get() = encryptedPrefs.getBoolean(AiimsConstants.KEY_CLOCK_MANIPULATION_DETECTED, false)
        set(value) = encryptedPrefs.edit().putBoolean(AiimsConstants.KEY_CLOCK_MANIPULATION_DETECTED, value).apply()

    // ===== Authentication State Storage (Regular - Non-sensitive) =====
    override var isAuthenticated: Boolean
        get() = regularPrefs.getBoolean(AiimsConstants.KEY_IS_AUTHENTICATED, false)
        set(value) = regularPrefs.edit().putBoolean(AiimsConstants.KEY_IS_AUTHENTICATED, value).apply()

    override var lastAuthTimestamp: Long
        get() = regularPrefs.getLong(AiimsConstants.KEY_LAST_AUTH_TIMESTAMP, 0L)
        set(value) = regularPrefs.edit().putLong(AiimsConstants.KEY_LAST_AUTH_TIMESTAMP, value).apply()

    // ===== User Information Storage (Regular - Non-sensitive) =====
    override var userId: String?
        get() = regularPrefs.getString(AiimsConstants.KEY_USER_ID, null)
        set(value) = regularPrefs.edit().putString(AiimsConstants.KEY_USER_ID, value).apply()

    override var userEmail: String?
        get() = regularPrefs.getString(AiimsConstants.KEY_USER_EMAIL, null)
        set(value) = regularPrefs.edit().putString(AiimsConstants.KEY_USER_EMAIL, value).apply()

    override var userName: String?
        get() = regularPrefs.getString(AiimsConstants.KEY_USER_NAME, null)
        set(value) = regularPrefs.edit().putString(AiimsConstants.KEY_USER_NAME, value).apply()

    // ===== Utility Methods =====

    /**
     * Clear all authentication data.
     */
    override fun clearAllAuthData() {
        // Clear encrypted data
        encryptedPrefs.edit().clear().apply()

        // Clear regular auth data
        regularPrefs.edit()
            .remove(AiimsConstants.KEY_IS_AUTHENTICATED)
            .remove(AiimsConstants.KEY_USER_ID)
            .remove(AiimsConstants.KEY_USER_EMAIL)
            .remove(AiimsConstants.KEY_USER_NAME)
            .remove(AiimsConstants.KEY_LAST_AUTH_TIMESTAMP)
            .apply()
    }

    /**
     * Clear only sensitive data (tokens, PIN, clock validation).
     */
    override fun clearSensitiveData() {
        encryptedPrefs.edit()
            .remove(AiimsConstants.KEY_AUTH_TOKEN)
            .remove(AiimsConstants.KEY_TOKEN_EXPIRY)
            .remove(AiimsConstants.KEY_PROJECT_ID)
            .remove(AiimsConstants.KEY_PIN_HASH)
            .remove(AiimsConstants.KEY_PIN_SALT)
            .remove(AiimsConstants.KEY_BIOMETRIC_KEY_ALIAS)
            .remove(AiimsConstants.KEY_LAST_VALID_WALL_TIME)
            .remove(AiimsConstants.KEY_LAST_ELAPSED_REALTIME)
            .remove(AiimsConstants.KEY_SERVER_TIME_OFFSET_MS)
            .remove(AiimsConstants.KEY_CLOCK_MANIPULATION_DETECTED)
            .apply()
    }

    /**
     * Reset PIN attempts and lockout state.
     */
    override fun resetPinAttempts() {
        encryptedPrefs.edit()
            .remove(AiimsConstants.KEY_PIN_ATTEMPTS)
            .remove(AiimsConstants.KEY_LAST_PIN_ATTEMPT)
            .apply()
    }

    /**
     * Increment failed PIN attempts.
     */
    override fun incrementPinAttempts(): Int {
        val attempts = pinAttempts + 1
        pinAttempts = attempts
        lastPinAttempt = System.currentTimeMillis()
        return attempts
    }

    /**
     * Check if PIN is locked due to too many attempts.
     */
    override fun isPinLocked(): Boolean {
        val attempts = pinAttempts
        val lastAttempt = lastPinAttempt ?: return false

        // Lock if max attempts reached and less than 24 hours since last attempt
        return attempts >= AiimsConstants.MAX_PIN_ATTEMPTS &&
            System.currentTimeMillis() - lastAttempt < AiimsConstants.DAY_IN_MS
    }

    /**
     * Get time until PIN unlock (in milliseconds).
     */
    override fun getPinUnlockTime(): Long {
        val lastAttempt = lastPinAttempt ?: return 0
        val lockDuration = AiimsConstants.DAY_IN_MS
        val timePassed = System.currentTimeMillis() - lastAttempt
        return maxOf(0, lockDuration - timePassed)
    }

    /**
     * Check if device token is expired or about to expire.
     */
    override fun isTokenExpired(): Boolean {
        val expiryTime = tokenExpiry ?: return true
        val currentTime = System.currentTimeMillis()
        // Consider expired if within 1 day of expiry
        return currentTime > (expiryTime - AiimsConstants.DAY_IN_MS)
    }

    /**
     * Export non-sensitive settings for backup.
     */
    override fun exportSettings(): Map<String, Any> {
        return mapOf(
            "biometricEnabled" to biometricEnabled
        )
    }

    /**
     * Import settings from backup.
     */
    override fun importSettings(settings: Map<String, Any>) {
        settings.forEach { (key, value) ->
            when (key) {
                "biometricEnabled" -> biometricEnabled = (value as? Boolean) ?: biometricEnabled
            }
        }
    }
}
