package edu.aiims.medresodk.auth.storage

/**
 * Interface for MEDRES Secure Storage.
 * Provides encrypted storage for sensitive authentication data.
 */
interface MedresSecureStorage {
    var authToken: String?
    var tokenExpiry: Long?
    var projectId: String?

    // ===== PIN Security Storage =====
    var pinHash: String?
    var pinSalt: String?
    var pinAttempts: Int
    var lastPinAttempt: Long?
    var biometricEnabled: Boolean
    var biometricKeyAlias: String?

    // ===== API Configuration Storage =====
    var apiUrl: String

    // ===== Clock Validation Storage (Encrypted) =====
    var lastValidWallTime: Long?
    var lastElapsedRealtime: Long?
    var serverTimeOffsetMs: Long
    var clockManipulationDetected: Boolean

    // ===== Authentication State Storage (Regular - Non-sensitive) =====
    var isAuthenticated: Boolean
    var lastAuthTimestamp: Long

    // ===== User Information Storage (Regular - Non-sensitive) =====
    var userId: String?
    var userEmail: String?
    var userName: String?

    // ===== Utility Methods =====
    fun clearAllAuthData(): Boolean
    fun clearSensitiveData(): Boolean
    fun resetPinAttempts()
    fun incrementPinAttempts(): Int
    fun isPinLocked(): Boolean
    fun getPinUnlockTime(): Long
    fun isTokenExpired(): Boolean
    fun exportSettings(): Map<String, Any>
    fun importSettings(settings: Map<String, Any>)
}
