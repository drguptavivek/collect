package org.aiims.odk.auth.storage

/**
 * Fake implementation of AiimsSecureStorage for unit testing.
 * Stores data in memory.
 */
class FakeAiimsSecureStorage : AiimsSecureStorage {

    private val data = mutableMapOf<String, Any?>()

    override var authToken: String?
        get() = data["authToken"] as? String
        set(value) { data["authToken"] = value }

    override var tokenExpiry: Long?
        get() = data["tokenExpiry"] as? Long
        set(value) { data["tokenExpiry"] = value }

    override var projectId: String?
        get() = data["projectId"] as? String
        set(value) { data["projectId"] = value }

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

    override var lastValidWallTime: Long?
        get() = data["lastValidWallTime"] as? Long
        set(value) { data["lastValidWallTime"] = value }

    override var lastElapsedRealtime: Long?
        get() = data["lastElapsedRealtime"] as? Long
        set(value) { data["lastElapsedRealtime"] = value }

    override var serverTimeOffsetMs: Long
        get() = data["serverTimeOffsetMs"] as? Long ?: 0L
        set(value) { data["serverTimeOffsetMs"] = value }

    override var clockManipulationDetected: Boolean
        get() = data["clockManipulationDetected"] as? Boolean ?: false
        set(value) { data["clockManipulationDetected"] = value }

    override var isAuthenticated: Boolean
        get() = data["isAuthenticated"] as? Boolean ?: false
        set(value) { data["isAuthenticated"] = value }

    override var lastAuthTimestamp: Long
        get() = data["lastAuthTimestamp"] as? Long ?: 0L
        set(value) { data["lastAuthTimestamp"] = value }

    override var userId: String?
        get() = data["userId"] as? String
        set(value) { data["userId"] = value }

    override var userEmail: String?
        get() = data["userEmail"] as? String
        set(value) { data["userEmail"] = value }

    override var userName: String?
        get() = data["userName"] as? String
        set(value) { data["userName"] = value }

    override fun clearAllAuthData(): Boolean {
        data.clear()
        return true
    }

    override fun clearSensitiveData(): Boolean {
        data.remove("authToken")
        data.remove("tokenExpiry")
        // Remove other sensitive keys
        return true
    }

    override fun resetPinAttempts() {
        pinAttempts = 0
        lastPinAttempt = null
    }

    override fun incrementPinAttempts(): Int {
        pinAttempts++
        return pinAttempts
    }

    override fun isPinLocked(): Boolean {
        // Simple mock logic
        return pinAttempts >= 5
    }

    override fun getPinUnlockTime(): Long {
        return 0L
    }

    override fun isTokenExpired(): Boolean {
        // Simple mock logic
        return false
    }

    override fun exportSettings(): Map<String, Any> {
        return mapOf()
    }

    override fun importSettings(settings: Map<String, Any>) {
        // No-op
    }
}
