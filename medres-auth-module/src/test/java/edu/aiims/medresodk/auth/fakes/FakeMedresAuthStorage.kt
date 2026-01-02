package edu.aiims.medresodk.auth.storage

import edu.aiims.medresodk.auth.api.User

/**
 * Fake implementation of MedresAuthStorage for unit testing.
 * Stores data in memory.
 */
class FakeMedresAuthStorage : MedresAuthStorage {

    private val data = mutableMapOf<String, Any?>()

    override var isAuthenticated: Boolean
        get() = data["isAuthenticated"] as? Boolean ?: false
        set(value) { data["isAuthenticated"] = value }

    override var deviceToken: String
        get() = data["deviceToken"] as? String ?: ""
        set(value) { data["deviceToken"] = value }

    override var tokenExpiry: Long?
        get() = data["tokenExpiry"] as? Long
        set(value) { data["tokenExpiry"] = value }

    override var projectId: String?
        get() = data["projectId"] as? String
        set(value) { data["projectId"] = value }

    override var userId: String?
        get() = data["userId"] as? String
        set(value) { data["userId"] = value }

    override var userEmail: String?
        get() = data["userEmail"] as? String
        set(value) { data["userEmail"] = value }

    override var userName: String?
        get() = data["userName"] as? String
        set(value) { data["userName"] = value }

    override var pinHash: String?
        get() = data["pinHash"] as? String
        set(value) { data["pinHash"] = value }

    override var pinSalt: String?
        get() = data["pinSalt"] as? String
        set(value) { data["pinSalt"] = value }

    override var pinAttempts: Int
        get() = data["pinAttempts"] as? Int ?: 0
        set(value) { data["pinAttempts"] = value }

    override var lastPinAttempt: Long?
        get() = data["lastPinAttempt"] as? Long
        set(value) { data["lastPinAttempt"] = value }

    override var biometricEnabled: Boolean
        get() = data["biometricEnabled"] as? Boolean ?: false
        set(value) { data["biometricEnabled"] = value }

    override var biometricKeyAlias: String?
        get() = data["biometricKeyAlias"] as? String
        set(value) { data["biometricKeyAlias"] = value }

    override var apiUrl: String
        get() = data["apiUrl"] as? String ?: ""
        set(value) { data["apiUrl"] = value }

    override var lastAuthTimestamp: Long
        get() = data["lastAuthTimestamp"] as? Long ?: 0L
        set(value) { data["lastAuthTimestamp"] = value }

    override fun saveUser(user: User) {
        userId = user.id
        userName = user.username
        userEmail = user.username
    }

    override fun getCurrentUser(): User? {
        val id = userId ?: return null
        return User(id, userName ?: "", projectId ?: "", null)
    }

    override fun saveAuthSession(token: String, expiresAt: String, user: User, apiUrl: String) {
        deviceToken = token
        tokenExpiry = edu.aiims.medresodk.auth.utils.ApiDateFormat.parse(expiresAt)?.time
        projectId = user.projectId
        saveUser(user)
        this.apiUrl = apiUrl
        isAuthenticated = true
        lastAuthTimestamp = System.currentTimeMillis()
    }

    override fun clearAuthData(): Boolean {
        isAuthenticated = false
        return clearSensitiveData()
    }

    override fun updateLastAuthTimestamp() {
        lastAuthTimestamp = System.currentTimeMillis()
    }

    override fun getAuthSummary(): Map<String, Any> {
        return mapOf(
            "isAuthenticated" to isAuthenticated,
            "userId" to (userId ?: "")
        )
    }

    override fun clearSensitiveData(): Boolean {
        deviceToken = ""
        tokenExpiry = null
        projectId = null
        pinHash = null
        pinSalt = null
        biometricKeyAlias = null
        // And other sensitive fields
        return true
    }
}
