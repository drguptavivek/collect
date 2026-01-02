package edu.aiims.medresodk.auth.utils

import android.content.Context
import android.content.SharedPreferences
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Utility class for managing PIN storage and verification.
 * 
 * PINs are stored as PBKDF2 hashes with a unique salt for security.
 * Never stores plaintext PIN.
 */
@Singleton
class PinManager @Inject constructor(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val securityUtils: MedresSecurityUtils = MedresSecurityUtils.getInstance(context)

    companion object {
        private const val PREFS_NAME = "medres_auth_prefs"
        private const val KEY_PIN_HASH = "pin_hash"
        private const val KEY_PIN_SALT = "pin_salt"
        private const val KEY_PIN_UPDATED_AT = "pin_updated_at"
        private const val KEY_PIN_ATTEMPTS = "pin_attempts"
        private const val MAX_ATTEMPTS = 3
        
        // Legacy key for migration detection
        private const val KEY_USER_PIN_LEGACY = "user_pin"
    }

    /**
     * Save the user's PIN securely using PBKDF2 hashing.
     */
    fun savePin(pin: String) {
        val salt = securityUtils.generateSalt()
        val hash = securityUtils.hashPin(pin, salt)
        
        prefs.edit().apply {
            putString(KEY_PIN_HASH, hash)
            putString(KEY_PIN_SALT, salt)
            putLong(KEY_PIN_UPDATED_AT, System.currentTimeMillis())
            remove(KEY_PIN_ATTEMPTS) // Reset attempts on successful PIN save
            // Remove legacy plaintext PIN if it exists
            remove(KEY_USER_PIN_LEGACY)
            apply()
        }
        android.util.Log.d("PinManager", "PIN saved securely (hashed)")
    }

    /**
     * Verify if the entered PIN matches the stored hash.
     */
    fun verifyPin(enteredPin: String): Boolean {
        val storedHash = prefs.getString(KEY_PIN_HASH, null)
        val storedSalt = prefs.getString(KEY_PIN_SALT, null)
        
        if (storedHash == null || storedSalt == null) {
            android.util.Log.w("PinManager", "No PIN hash/salt found for verification")
            return false
        }
        
        val matches = securityUtils.verifyPin(enteredPin, storedHash, storedSalt)
        
        return if (matches) {
            resetAttempts()
            true
        } else {
            incrementFailedAttempts()
            false
        }
    }

    /**
     * Check if a PIN is already set (has valid hash and salt).
     */
    fun isPinSet(): Boolean {
        val hasHash = prefs.contains(KEY_PIN_HASH) && prefs.contains(KEY_PIN_SALT)
        val hash = prefs.getString(KEY_PIN_HASH, null)
        val salt = prefs.getString(KEY_PIN_SALT, null)
        val isValid = hasHash && !hash.isNullOrEmpty() && !salt.isNullOrEmpty()
        android.util.Log.d("PinManager", "isPinSet: $isValid")
        return isValid
    }

    /**
     * Get the number of failed attempts.
     */
    fun getFailedAttempts(): Int {
        return prefs.getInt(KEY_PIN_ATTEMPTS, 0)
    }

    /**
     * Check if max attempts reached.
     */
    fun isMaxAttemptsReached(): Boolean {
        return getFailedAttempts() >= MAX_ATTEMPTS
    }

    /**
     * Increment failed attempts.
     */
    private fun incrementFailedAttempts() {
        val attempts = getFailedAttempts() + 1
        prefs.edit().apply {
            putInt(KEY_PIN_ATTEMPTS, attempts)
            apply()
        }
    }

    /**
     * Reset failed attempts.
     */
    private fun resetAttempts() {
        prefs.edit().apply {
            remove(KEY_PIN_ATTEMPTS)
            apply()
        }
    }

    /**
     * Clear all PIN data (used during logout).
     */
    fun clearPin() {
        prefs.edit().apply {
            remove(KEY_PIN_HASH)
            remove(KEY_PIN_SALT)
            remove(KEY_PIN_UPDATED_AT)
            remove(KEY_PIN_ATTEMPTS)
            // Also clear legacy key if exists
            remove(KEY_USER_PIN_LEGACY)
            apply()
        }
        android.util.Log.d("PinManager", "PIN cleared")
    }

    /**
     * Get when the PIN was last updated.
     */
    fun getPinUpdatedAt(): Long {
        return prefs.getLong(KEY_PIN_UPDATED_AT, 0L)
    }
}
