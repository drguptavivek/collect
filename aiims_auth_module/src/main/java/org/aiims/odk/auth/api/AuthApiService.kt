package org.aiims.odk.auth.api

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * Retrofit API interface for authentication
 */
interface AuthApiService {

    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>

    @POST("device-tokens/{id}/revoke")
    suspend fun revokeDeviceToken(
        @Path("id") tokenId: String,
        @Header("Authorization") authHeader: String? = null
    ): Response<RevokeResponse>
}

/**
 * Login request body
 */
data class LoginRequest(
    val email: String,
    val password: String,
    val deviceId: String,
    val deviceInfo: String
)

/**
 * Login response body
 */
data class LoginResponse(
    val success: Boolean,
    val user: UserData? = null,
    val deviceToken: String? = null,
    val expiresAt: String? = null,
    val requiresPinSetup: Boolean? = false,
    val message: String? = null,
    val error: String? = null
)

data class RevokeResponse(
    val success: Boolean,
    val message: String? = null,
    val error: String? = null
)

/**
 * User data from API response
 */
data class UserData(
    val id: String,
    val email: String,
    val role: String,
    val partnerId: String? = null,
    val partnerName: String? = null,
    val name: String
)
