package edu.aiims.medresodk.auth.storage

import android.content.Context
import edu.aiims.medresodk.auth.api.User

/**
 * Main authentication storage implementation that wraps secure and regular storage.
 */
class MedresAuthStorageImpl private constructor(
    private val secureStorage: MedresSecureStorage
) : MedresAuthStorage {

    companion object {
        @Volatile
        private var INSTANCE: MedresAuthStorage? = null

        fun getInstance(context: Context): MedresAuthStorage {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: createInstance(context.applicationContext).also { INSTANCE = it }
            }
        }

        private fun createInstance(context: Context): MedresAuthStorage {
            val secureStorage = MedresSecureStorageImpl.getInstance(context)
            return MedresAuthStorageImpl(secureStorage)
        }
    }

    // ===== Authentication State =====
    override var isAuthenticated: Boolean
        get() = secureStorage.isAuthenticated
        set(value) {
            secureStorage.isAuthenticated = value
            if (!value) {
                // Clear sensitive data on logout
                secureStorage.clearSensitiveData()
            }
        }

    // ===== Device Tokens =====
    override var deviceToken: String
        get() = secureStorage.authToken ?: ""
        set(value) { secureStorage.authToken = value }

    override var tokenExpiry: Long?
        get() = secureStorage.tokenExpiry
        set(value) { secureStorage.tokenExpiry = value }

    override var projectId: String?
        get() = secureStorage.projectId
        set(value) { secureStorage.projectId = value }

    // ===== User Information =====
    override var userId: String?
        get() = secureStorage.userId
        set(value) { secureStorage.userId = value }

    override var userEmail: String?
        get() = secureStorage.userEmail
        set(value) { secureStorage.userEmail = value }

    override var userName: String?
        get() = secureStorage.userName
        set(value) { secureStorage.userName = value }

    // ===== PIN Security =====
    override var pinHash: String?
        get() = secureStorage.pinHash
        set(value) { secureStorage.pinHash = value }

    override var pinSalt: String?
        get() = secureStorage.pinSalt
        set(value) { secureStorage.pinSalt = value }

    override var pinAttempts: Int
        get() = secureStorage.pinAttempts
        set(value) { secureStorage.pinAttempts = value }

    override var lastPinAttempt: Long?
        get() = secureStorage.lastPinAttempt
        set(value) { secureStorage.lastPinAttempt = value }

    override var biometricEnabled: Boolean
        get() = secureStorage.biometricEnabled
        set(value) { secureStorage.biometricEnabled = value }

    override var biometricKeyAlias: String?
        get() = secureStorage.biometricKeyAlias
        set(value) { secureStorage.biometricKeyAlias = value }

    // ===== API Configuration =====
    override var apiUrl: String
        get() = secureStorage.apiUrl
        set(value) { secureStorage.apiUrl = value }

    override var lastAuthTimestamp: Long
        get() = secureStorage.lastAuthTimestamp
        set(value) { secureStorage.lastAuthTimestamp = value }

    // ===== High-Level Operations =====

    /**
     * Save user information from login response.
     */
    override fun saveUser(user: User) {
        userId = user.id
        // userEmail = user.username // Map username to email for legacy compat if needed, or just username
        userName = user.username
        userEmail = user.username
    }

    /**
     * Get current user as User object.
     */
    override fun getCurrentUser(): User? {
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
    override fun saveAuthSession(
        token: String,
        expiresAt: String,
        user: User,
        apiUrl: String
    ) {
        // Save tokens
        deviceToken = token
        // refreshToken gone
        tokenExpiry = edu.aiims.medresodk.auth.utils.ApiDateFormat.parse(expiresAt)?.time

        // Save project ID (for token validation)
        projectId = user.projectId

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
    override fun clearAuthData(): Boolean {
        isAuthenticated = false
        return secureStorage.clearAllAuthData()
    }

    /**
     * Update last authentication timestamp.
     */
    override fun updateLastAuthTimestamp() {
        lastAuthTimestamp = System.currentTimeMillis()
    }

    /**
     * Get authentication summary for debugging.
     */
    override fun getAuthSummary(): Map<String, Any> {
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
    override fun clearSensitiveData(): Boolean {
        // Clear secure storage (tokens, PINs, etc.)
        val secureCleared = secureStorage.clearSensitiveData()

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

        return secureCleared
    }
}
