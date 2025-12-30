package org.aiims.odk.auth.storage

import org.aiims.odk.auth.api.User

/**
 * Interface for AIIMS Auth Storage.
 * Main authentication storage interface that wraps secure and regular storage.
 */
interface AiimsAuthStorage {
    // ===== Authentication State =====
    var isAuthenticated: Boolean

    // ===== Device Tokens =====
    var deviceToken: String
    var tokenExpiry: Long?
    var projectId: String?

    // ===== User Information =====
    var userId: String?
    var userEmail: String?
    var userName: String?

    // ===== PIN Security =====
    var pinHash: String?
    var pinSalt: String?
    var pinAttempts: Int
    var lastPinAttempt: Long?
    var biometricEnabled: Boolean
    var biometricKeyAlias: String?

    // ===== API Configuration =====
    var apiUrl: String

    var lastAuthTimestamp: Long

    // ===== High-Level Operations =====
    fun saveUser(user: User)
    fun getCurrentUser(): User?
    fun saveAuthSession(token: String, expiresAt: String, user: User, apiUrl: String)
    fun clearAuthData()
    fun updateLastAuthTimestamp()
    fun getAuthSummary(): Map<String, Any>
    fun clearSensitiveData()
}
