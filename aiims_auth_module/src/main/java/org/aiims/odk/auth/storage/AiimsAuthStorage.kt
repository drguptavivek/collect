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
        get() = secureStorage.deviceToken
        set(value) { secureStorage.deviceToken = value }

    var refreshToken: String
        get() = secureStorage.refreshToken
        set(value) { secureStorage.refreshToken = value }

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

    var userRole: String?
        get() = secureStorage.userRole
        set(value) { secureStorage.userRole = value }

    var partnerId: String?
        get() = secureStorage.partnerId
        set(value) { secureStorage.partnerId = value }

    var partnerName: String?
        get() = secureStorage.partnerName
        set(value) { secureStorage.partnerName = value }

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

    var deviceId: String
        get() = secureStorage.deviceId
        set(value) { secureStorage.deviceId = value }

    // ===== Settings =====
    var offlinePeriodDays: Int
        get() = secureStorage.offlinePeriodDays
        set(value) { secureStorage.offlinePeriodDays = value }

    var autoLogoutMinutes: Int
        get() = secureStorage.autoLogoutMinutes
        set(value) { secureStorage.autoLogoutMinutes = value }

    var lastAuthTimestamp: Long
        get() = secureStorage.lastAuthTimestamp
        set(value) { secureStorage.lastAuthTimestamp = value }

    var lastSyncTimestamp: Long
        get() = secureStorage.lastSyncTimestamp
        set(value) { secureStorage.lastSyncTimestamp = value }

    var syncPendingCount: Int
        get() = secureStorage.syncPendingCount
        set(value) { secureStorage.syncPendingCount = value }

    // ===== High-Level Operations =====

    /**
     * Save user information from login response.
     */
    fun saveUser(user: User) {
        userId = user.id
        userEmail = user.email
        userName = user.name
        userRole = user.role
        partnerId = user.partnerId
        partnerName = user.partnerName
    }

    /**
     * Get current user as User object.
     */
    fun getCurrentUser(): User? {
        val id = userId ?: return null
        return User(
            id = id,
            email = userEmail ?: "",
            name = userName ?: "",
            role = userRole ?: "",
            partnerId = partnerId,
            partnerName = partnerName,
            phoneNumber = null,
            isActive = true,
            dateActiveTill = null
        )
    }

    /**
     * Save complete authentication session.
     */
    fun saveAuthSession(
        token: String,
        refreshToken: String,
        expiresAt: String,
        user: User,
        apiUrl: String
    ) {
        // Save tokens
        deviceToken = token
        this.refreshToken = refreshToken
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
     * Check if offline access is allowed.
     */
    fun isOfflineAccessAllowed(): Boolean {
        val lastAuth = lastAuthTimestamp
        if (lastAuth == 0L) return false

        val offlinePeriodMs = offlinePeriodDays * 24 * 60 * 60 * 1000L
        val currentTime = System.currentTimeMillis()
        return (currentTime - lastAuth) <= offlinePeriodMs
    }

    /**
     * Check if auto-logout should occur.
     */
    fun shouldAutoLogout(): Boolean {
        val lastAuth = lastAuthTimestamp
        if (lastAuth == 0L) return false

        val timeoutMs = autoLogoutMinutes * 60 * 1000L
        val currentTime = System.currentTimeMillis()
        return (currentTime - lastAuth) > timeoutMs
    }

    /**
     * Update last authentication timestamp.
     */
    fun updateLastAuthTimestamp() {
        lastAuthTimestamp = System.currentTimeMillis()
    }

    /**
     * Get user-friendly role display name.
     */
    fun getUserRoleDisplayName(): String {
        return when (userRole) {
            "national_admin" -> "National Administrator"
            "data_manager" -> "Data Manager"
            "partner_manager" -> "Partner Manager"
            "team_member" -> "Team Member"
            else -> "Unknown Role"
        }
    }

    /**
     * Check if user has partner access.
     */
    fun hasPartnerAccess(): Boolean {
        return partnerId != null && partnerId!!.isNotEmpty()
    }

    /**
     * Get authentication summary for debugging.
     */
    fun getAuthSummary(): Map<String, Any> {
        return mapOf(
            "isAuthenticated" to isAuthenticated,
            "userId" to (userId ?: ""),
            "userEmail" to (userEmail ?: ""),
            "userRole" to (userRole ?: ""),
            "partnerId" to (partnerId ?: ""),
            "hasPin" to (pinHash != null),
            "biometricEnabled" to biometricEnabled,
            "apiUrl" to apiUrl,
            "deviceId" to deviceId,
            "offlineAccessAllowed" to isOfflineAccessAllowed(),
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
        userRole = null
        partnerId = null
        partnerName = null
        pinHash = null
        pinSalt = null
        pinAttempts = 0
        lastPinAttempt = null
        biometricEnabled = false
        biometricKeyAlias = null
        lastAuthTimestamp = 0
        syncPendingCount = 0
        lastSyncTimestamp = 0
    }
}