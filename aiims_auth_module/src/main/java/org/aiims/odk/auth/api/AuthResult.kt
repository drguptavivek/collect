package org.aiims.odk.auth.api

/**
 * Authentication result wrapper
 */
sealed class AuthResult {
    data class Success(val user: User) : AuthResult()
    data class Error(val message: String) : AuthResult()
    object Canceled : AuthResult()
    object RequiresPin : AuthResult()
}