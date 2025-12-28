package org.aiims.odk.auth.api

/**
 * Authentication result wrapper
 */
sealed class AuthResult {
    data class Success(
        val user: User,
        val token: String = "",
        val expiresAt: String = "",
        val serverTime: Long? = null  // Server time from Date header (milliseconds since epoch)
    ) : AuthResult()
    data class Error(val message: String) : AuthResult()
    object Canceled : AuthResult()
    data class RequiresPin(
        val user: User,
        val token: String,
        val expiresAt: String
    ) : AuthResult()
}
