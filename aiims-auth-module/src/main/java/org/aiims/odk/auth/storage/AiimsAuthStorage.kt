package org.aiims.odk.auth.storage

import android.content.Context
import org.aiims.odk.auth.api.User

/**
 * Main authentication storage interface that wraps secure and regular storage.
 *
 * Provides a unified interface for storing and retrieving authentication data,
 * abstracting away the distinction between secure and regular storage.
 */
class AiimsAuthStorage private constructor(
    private val secureStorage: AiimsSecureStorage
) {

    companion object {
        @Volatile
        private var INSTANCE: AiimsAuthStorage? = null

        fun getInstance(context: Context): AiimsAuthStorage {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: createInstance(context.applicationContext).also { INSTANCE = it }
            }
        }

        private fun createInstance(context: Context): AiimsAuthStorage {
            val secureStorage = AiimsSecureStorage.getInstance(context)
            return AiimsAuthStorage(secureStorage)
        }
    }

    // ===== Authentication State =====
    var isAuthenticated: Boolean
        get() = secureStorage.isAuthenticated
        set(value) {
            secureStorage.isAuthenticated = value
            if (!value) {
                // Clear sensitive data on logout
                secureStorage.clearSensitiveData()
            }
        }

    // ===== Device Tokens =====
    var deviceToken: String
        get() = secureStorage.authToken ?: ""
        set(value) { secureStorage.authToken = value }

    var tokenExpiry: Long?
        get() = secureStorage.tokenExpiry
        set(value) { secureStorage.tokenExpiry = value }

    // ===== User Information =====
    var userId: String?
        get() = secureStorage.userId
        set(value) { secureStorage.userId = value }

    var userEmail: String?
        get() = secureStorage.userEmail
        set(value) { secureStorage.userEmail = value }

    var userName: String?
        get() = secureStorage.userName
        set(value) { secureStorage.userName = value }

    // ===== PIN Security =====
    var pinHash: String?
        get() = secureStorage.pinHash
        set(value) { secureStorage.pinHash = value }

    var pinSalt: String?
        get() = secureStorage.pinSalt
        set(value) { secureStorage.pinSalt = value }

    var pinAttempts: Int
        get() = secureStorage.pinAttempts
        set(value) { secureStorage.pinAttempts = value }

    var lastPinAttempt: Long?
        get() = secureStorage.lastPinAttempt
        set(value) { secureStorage.lastPinAttempt = value }

    var biometricEnabled: Boolean
        get() = secureStorage.biometricEnabled
        set(value) { secureStorage.biometricEnabled = value }

    var biometricKeyAlias: String?
        get() = secureStorage.biometricKeyAlias
        set(value) { secureStorage.biometricKeyAlias = value }

    // ===== API Configuration =====
    var apiUrl: String
        get() = secureStorage.apiUrl
        set(value) { secureStorage.apiUrl = value }

    var lastAuthTimestamp: Long
        get() = secureStorage.lastAuthTimestamp
        set(value) { secureStorage.lastAuthTimestamp = value }

    // ===== High-Level Operations =====

    /**
     * Save user information from login response.
     */
    fun saveUser(user: User) {
        userId = user.id
        // userEmail = user.username // Map username to email for legacy compat if needed, or just username
        userName = user.username
        userEmail = user.username
    }

    /**
     * Get current user as User object.
     */
    fun getCurrentUser(): User? {
        val id = userId ?: return null
        return User(
            id = id,
            username = userName ?: ""
            // role, partnerId, etc are gone
        )
    }

    /**
     * Save complete authentication session.
     */
    fun saveAuthSession(
        token: String,
        expiresAt: String,
        user: User,
        apiUrl: String
    ) {
        // Save tokens
        deviceToken = token
        // refreshToken gone
        tokenExpiry = org.aiims.odk.auth.utils.ApiDateFormat.parse(expiresAt)?.time

        // Save user info
        saveUser(user)

        // Save API URL
        this.apiUrl = apiUrl

        // Mark as authenticated
        isAuthenticated = true

        // Update last auth timestamp
        lastAuthTimestamp = System.currentTimeMillis()
    }

    /**
     * Clear all authentication data (logout).
     */
    fun clearAuthData() {
        isAuthenticated = false
        secureStorage.clearAllAuthData()
    }

    /**
     * Update last authentication timestamp.
     */
    fun updateLastAuthTimestamp() {
        lastAuthTimestamp = System.currentTimeMillis()
    }

    /**
     * Get authentication summary for debugging.
     */
    fun getAuthSummary(): Map<String, Any> {
        return mapOf(
            "isAuthenticated" to isAuthenticated,
            "userId" to (userId ?: ""),
            "userEmail" to (userEmail ?: ""),
            "hasPin" to (pinHash != null),
            "biometricEnabled" to biometricEnabled,
            "apiUrl" to apiUrl,
            "tokenExpired" to secureStorage.isTokenExpired(),
            "pinLocked" to secureStorage.isPinLocked()
        )
    }

    // ===== Methods =====

    /**
     * Clear all sensitive authentication data
     */
    fun clearSensitiveData() {
        // Clear secure storage (tokens, PINs, etc.)
        secureStorage.clearSensitiveData()

        // Clear authentication state
        isAuthenticated = false
        userId = null
        userEmail = null
        userName = null
        pinHash = null
        pinSalt = null
        pinAttempts = 0
        lastPinAttempt = null
        biometricEnabled = false
        biometricKeyAlias = null
        lastAuthTimestamp = 0
    }
}
