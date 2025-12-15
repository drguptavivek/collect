package org.aiims.odk.auth.api

/**
 * Login request data class
 */
data class LoginRequest(
    val email: String,
    val password: String
)