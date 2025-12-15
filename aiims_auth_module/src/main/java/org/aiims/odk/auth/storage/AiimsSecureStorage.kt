package org.aiims.odk.auth.storage

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.aiims.odk.auth.utils.AiimsConstants
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Secure storage implementation using EncryptedSharedPreferences.
 *
 * Provides encrypted storage for sensitive authentication data like tokens,
 * PINs, and API URLs using Android's EncryptedSharedPreferences with
 * AES-256-GCM encryption.
 */
@Singleton
class AiimsSecureStorage @Inject constructor(
    private val context: Context
) {

    // Master key for encryption
    private val masterKey: MasterKey by lazy {
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }

    // Encrypted preferences for sensitive data
    private val encryptedPrefs: SharedPreferences by lazy {
        EncryptedSharedPreferences.create(
            context,
            AiimsConstants.AIIMS_SECURE_PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    // Regular preferences for non-sensitive data
    private val regularPrefs: SharedPreferences by lazy {
        context.getSharedPreferences(
            AiimsConstants.AIIMS_PREFS_NAME,
            Context.MODE_PRIVATE
        )
    }

    // ===== Device Token Storage =====
    var deviceToken: String
        get() = encryptedPrefs.getString(AiimsConstants.KEY_DEVICE_TOKEN, "") ?: ""
        set(value) = encryptedPrefs.edit().putString(AiimsConstants.KEY_DEVICE_TOKEN, value).apply()

    var refreshToken: String
        get() = encryptedPrefs.getString(AiimsConstants.KEY_REFRESH_TOKEN, "") ?: ""
        set(value) = encryptedPrefs.edit().putString(AiimsConstants.KEY_REFRESH_TOKEN, value).apply()

    var tokenExpiry: Long?
        get() = encryptedPrefs.getLong(AiimsConstants.KEY_TOKEN_EXPIRY, -1).takeIf { it != -1L }
        set(value) = encryptedPrefs.edit().putLong(AiimsConstants.KEY_TOKEN_EXPIRY, value ?: -1L).apply()

    // ===== PIN Security Storage =====
    var pinHash: String?
        get() = encryptedPrefs.getString(AiimsConstants.KEY_PIN_HASH, null)
        set(value) = encryptedPrefs.edit().putString(AiimsConstants.KEY_PIN_HASH, value).apply()

    var pinSalt: String?
        get() = encryptedPrefs.getString(AiimsConstants.KEY_PIN_SALT, null)
        set(value) = encryptedPrefs.edit().putString(AiimsConstants.KEY_PIN_SALT, value).apply()

    var pinAttempts: Int
        get() = encryptedPrefs.getInt(AiimsConstants.KEY_PIN_ATTEMPTS, 0)
        set(value) = encryptedPrefs.edit().putInt(AiimsConstants.KEY_PIN_ATTEMPTS, value).apply()

    var lastPinAttempt: Long?
        get() = encryptedPrefs.getLong(AiimsConstants.KEY_LAST_PIN_ATTEMPT, -1).takeIf { it != -1L }
        set(value) = encryptedPrefs.edit().putLong(AiimsConstants.KEY_LAST_PIN_ATTEMPT, value ?: -1L).apply()

    var biometricEnabled: Boolean
        get() = encryptedPrefs.getBoolean(AiimsConstants.KEY_BIOMETRIC_ENABLED, false)
        set(value) = encryptedPrefs.edit().putBoolean(AiimsConstants.KEY_BIOMETRIC_ENABLED, value).apply()

    var biometricKeyAlias: String?
        get() = encryptedPrefs.getString(AiimsConstants.KEY_BIOMETRIC_KEY_ALIAS, null)
        set(value) = encryptedPrefs.edit().putString(AiimsConstants.KEY_BIOMETRIC_KEY_ALIAS, value).apply()

    // ===== API Configuration Storage =====
    var apiUrl: String
        get() = encryptedPrefs.getString(AiimsConstants.KEY_API_URL, "") ?: ""
        set(value) = encryptedPrefs.edit().putString(AiimsConstants.KEY_API_URL, value).apply()

    var deviceId: String
        get() = encryptedPrefs.getString(AiimsConstants.KEY_DEVICE_ID, "") ?: ""
        set(value) = encryptedPrefs.edit().putString(AiimsConstants.KEY_DEVICE_ID, value).apply()

    // ===== Authentication State Storage (Regular - Non-sensitive) =====
    var isAuthenticated: Boolean
        get() = regularPrefs.getBoolean(AiimsConstants.KEY_IS_AUTHENTICATED, false)
        set(value) = regularPrefs.edit().putBoolean(AiimsConstants.KEY_IS_AUTHENTICATED, value).apply()

    var lastAuthTimestamp: Long
        get() = regularPrefs.getLong(AiimsConstants.KEY_LAST_AUTH_TIMESTAMP, 0L)
        set(value) = regularPrefs.edit().putLong(AiimsConstants.KEY_LAST_AUTH_TIMESTAMP, value).apply()

    // ===== User Information Storage (Regular - Non-sensitive) =====
    var userId: String?
        get() = regularPrefs.getString(AiimsConstants.KEY_USER_ID, null)
        set(value) = regularPrefs.edit().putString(AiimsConstants.KEY_USER_ID, value).apply()

    var userEmail: String?
        get() = regularPrefs.getString(AiimsConstants.KEY_USER_EMAIL, null)
        set(value) = regularPrefs.edit().putString(AiimsConstants.KEY_USER_EMAIL, value).apply()

    var userName: String?
        get() = regularPrefs.getString(AiimsConstants.KEY_USER_NAME, null)
        set(value) = regularPrefs.edit().putString(AiimsConstants.KEY_USER_NAME, value).apply()

    var userRole: String?
        get() = regularPrefs.getString(AiimsConstants.KEY_USER_ROLE, null)
        set(value) = regularPrefs.edit().putString(AiimsConstants.KEY_USER_ROLE, value).apply()

    var partnerId: String?
        get() = regularPrefs.getString(AiimsConstants.KEY_PARTNER_ID, null)
        set(value) = regularPrefs.edit().putString(AiimsConstants.KEY_PARTNER_ID, value).apply()

    var partnerName: String?
        get() = regularPrefs.getString(AiimsConstants.KEY_PARTNER_NAME, null)
        set(value) = regularPrefs.edit().putString(AiimsConstants.KEY_PARTNER_NAME, value).apply()

    // ===== Settings Storage (Regular) =====
    var offlinePeriodDays: Int
        get() = regularPrefs.getInt(AiimsConstants.KEY_OFFLINE_PERIOD_DAYS, AiimsConstants.DEFAULT_OFFLINE_PERIOD_DAYS)
        set(value) = regularPrefs.edit().putInt(AiimsConstants.KEY_OFFLINE_PERIOD_DAYS, value).apply()

    var autoLogoutMinutes: Int
        get() = regularPrefs.getInt(AiimsConstants.KEY_AUTO_LOGOUT_MINUTES, AiimsConstants.DEFAULT_AUTO_LOGOUT_MINUTES)
        set(value) = regularPrefs.edit().putInt(AiimsConstants.KEY_AUTO_LOGOUT_MINUTES, value).apply()

    var lastSyncTimestamp: Long
        get() = regularPrefs.getLong(AiimsConstants.KEY_LAST_SYNC_TIMESTAMP, 0L)
        set(value) = regularPrefs.edit().putLong(AiimsConstants.KEY_LAST_SYNC_TIMESTAMP, value).apply()

    var syncPendingCount: Int
        get() = regularPrefs.getInt(AiimsConstants.KEY_SYNC_PENDING_COUNT, 0)
        set(value) = regularPrefs.edit().putInt(AiimsConstants.KEY_SYNC_PENDING_COUNT, value).apply()

    // ===== Utility Methods =====

    /**
     * Clear all authentication data.
     */
    fun clearAllAuthData() {
        // Clear encrypted data
        encryptedPrefs.edit().clear().apply()

        // Clear regular auth data
        regularPrefs.edit()
            .remove(AiimsConstants.KEY_IS_AUTHENTICATED)
            .remove(AiimsConstants.KEY_USER_ID)
            .remove(AiimsConstants.KEY_USER_EMAIL)
            .remove(AiimsConstants.KEY_USER_NAME)
            .remove(AiimsConstants.KEY_USER_ROLE)
            .remove(AiimsConstants.KEY_PARTNER_ID)
            .remove(AiimsConstants.KEY_PARTNER_NAME)
            .remove(AiimsConstants.KEY_LAST_AUTH_TIMESTAMP)
            .apply()
    }

    /**
     * Clear only sensitive data (tokens, PIN).
     */
    fun clearSensitiveData() {
        encryptedPrefs.edit()
            .remove(AiimsConstants.KEY_DEVICE_TOKEN)
            .remove(AiimsConstants.KEY_REFRESH_TOKEN)
            .remove(AiimsConstants.KEY_TOKEN_EXPIRY)
            .remove(AiimsConstants.KEY_PIN_HASH)
            .remove(AiimsConstants.KEY_PIN_SALT)
            .remove(AiimsConstants.KEY_BIOMETRIC_KEY_ALIAS)
            .apply()
    }

    /**
     * Reset PIN attempts and lockout state.
     */
    fun resetPinAttempts() {
        encryptedPrefs.edit()
            .remove(AiimsConstants.KEY_PIN_ATTEMPTS)
            .remove(AiimsConstants.KEY_LAST_PIN_ATTEMPT)
            .apply()
    }

    /**
     * Increment failed PIN attempts.
     */
    fun incrementPinAttempts(): Int {
        val attempts = pinAttempts + 1
        pinAttempts = attempts
        lastPinAttempt = System.currentTimeMillis()
        return attempts
    }

    /**
     * Check if PIN is locked due to too many attempts.
     */
    fun isPinLocked(): Boolean {
        val attempts = pinAttempts
        val lastAttempt = lastPinAttempt ?: return false

        // Lock if max attempts reached and less than 24 hours since last attempt
        return attempts >= AiimsConstants.MAX_PIN_ATTEMPTS &&
                System.currentTimeMillis() - lastAttempt < AiimsConstants.DAY_IN_MS
    }

    /**
     * Get time until PIN unlock (in milliseconds).
     */
    fun getPinUnlockTime(): Long {
        val lastAttempt = lastPinAttempt ?: return 0
        val lockDuration = AiimsConstants.DAY_IN_MS
        val timePassed = System.currentTimeMillis() - lastAttempt
        return maxOf(0, lockDuration - timePassed)
    }

    /**
     * Check if device token is expired or about to expire.
     */
    fun isTokenExpired(): Boolean {
        val expiryTime = tokenExpiry ?: return true
        val currentTime = System.currentTimeMillis()
        // Consider expired if within 1 day of expiry
        return currentTime > (expiryTime - AiimsConstants.DAY_IN_MS)
    }

    /**
     * Export non-sensitive settings for backup.
     */
    fun exportSettings(): Map<String, Any> {
        return mapOf(
            "offlinePeriodDays" to offlinePeriodDays,
            "autoLogoutMinutes" to autoLogoutMinutes,
            "biometricEnabled" to biometricEnabled
        )
    }

    /**
     * Import settings from backup.
     */
    fun importSettings(settings: Map<String, Any>) {
        settings.forEach { (key, value) ->
            when (key) {
                "offlinePeriodDays" -> offlinePeriodDays = (value as? String)?.toIntOrNull() ?: offlinePeriodDays
                "autoLogoutMinutes" -> autoLogoutMinutes = (value as? String)?.toIntOrNull() ?: autoLogoutMinutes
                "biometricEnabled" -> biometricEnabled = (value as? Boolean) ?: biometricEnabled
            }
        }
    }
}