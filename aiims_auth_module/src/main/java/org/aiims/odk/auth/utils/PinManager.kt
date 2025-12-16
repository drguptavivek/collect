package org.aiims.odk.auth.utils

import android.content.Context
import android.content.SharedPreferences

/**
 * Utility class for managing PIN storage and verification
 */
class PinManager private constructor(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "aiims_auth_prefs"
        private const val KEY_USER_PIN = "user_pin"
        private const val KEY_PIN_UPDATED_AT = "pin_updated_at"
        private const val KEY_PIN_ATTEMPTS = "pin_attempts"
        private const val MAX_ATTEMPTS = 3

        @Volatile
        private var INSTANCE: PinManager? = null

        fun getInstance(context: Context): PinManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: PinManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    /**
     * Save the user's PIN
     */
    fun savePin(pin: String) {
        prefs.edit().apply {
            putString(KEY_USER_PIN, pin)
            putLong(KEY_PIN_UPDATED_AT, System.currentTimeMillis())
            remove(KEY_PIN_ATTEMPTS) // Reset attempts on successful PIN save
            apply()
        }
        android.util.Log.d("PinManager", "PIN saved successfully")
    }

    /**
     * Verify if the entered PIN matches the stored PIN
     */
    fun verifyPin(enteredPin: String): Boolean {
        val storedPin = getStoredPin()
        return if (storedPin == enteredPin) {
            resetAttempts()
            true
        } else {
            incrementFailedAttempts()
            false
        }
    }

    /**
     * Check if a PIN is already set
     */
    fun isPinSet(): Boolean {
        val hasPin = prefs.contains(KEY_USER_PIN)
        val pin = prefs.getString(KEY_USER_PIN, "")
        android.util.Log.d("PinManager", "isPinSet: $hasPin, PIN: ${if (pin.isNullOrEmpty()) "null/empty" else "***"}")
        return hasPin
    }

    /**
     * Get the stored PIN
     */
    private fun getStoredPin(): String {
        return prefs.getString(KEY_USER_PIN, "") ?: ""
    }

    /**
     * Get the number of failed attempts
     */
    fun getFailedAttempts(): Int {
        return prefs.getInt(KEY_PIN_ATTEMPTS, 0)
    }

    /**
     * Check if max attempts reached
     */
    fun isMaxAttemptsReached(): Boolean {
        return getFailedAttempts() >= MAX_ATTEMPTS
    }

    /**
     * Increment failed attempts
     */
    private fun incrementFailedAttempts() {
        val attempts = getFailedAttempts() + 1
        prefs.edit().apply {
            putInt(KEY_PIN_ATTEMPTS, attempts)
            apply()
        }
    }

    /**
     * Reset failed attempts
     */
    private fun resetAttempts() {
        prefs.edit().apply {
            remove(KEY_PIN_ATTEMPTS)
            apply()
        }
    }

    /**
     * Clear all PIN data (used during logout)
     */
    fun clearPin() {
        prefs.edit().apply {
            remove(KEY_USER_PIN)
            remove(KEY_PIN_UPDATED_AT)
            remove(KEY_PIN_ATTEMPTS)
            apply()
        }
    }

    /**
     * Get when the PIN was last updated
     */
    fun getPinUpdatedAt(): Long {
        return prefs.getLong(KEY_PIN_UPDATED_AT, 0L)
    }
}