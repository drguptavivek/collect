package org.aiims.odk.auth.api

/**
 * Login response data class
 */
data class LoginResponse(
    val success: Boolean,
    val message: String? = null,
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val expiresAt: Long? = null,
    val requiresPinSetup: Boolean? = false,
    val user: User? = null
)