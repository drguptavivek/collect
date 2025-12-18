package org.aiims.odk.auth.api

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * Retrofit API interface for authentication
 */
/**
 * Retrofit API interface for authentication (Central Backend)
 */
interface AuthApiService {

    @POST("projects/{projectId}/app-users/login")
    suspend fun login(
        @Path("projectId") projectId: String,
        @Body request: LoginRequest
    ): Response<LoginResponse>

    @POST("projects/{projectId}/app-users/{id}/revoke")
    suspend fun revokeSession(
        @Path("projectId") projectId: String,
        @Path("id") userId: String,
        @Header("Authorization") authHeader: String
    ): Response<RevokeResponse>
}

/**
 * Login request body
 */
data class LoginRequest(
    val username: String,
    val password: String
)

/**
 * Login response body
 */
data class LoginResponse(
    val token: String,
    val projectId: Int,
    val expiresAt: String,
    val id: Int // App User ID
)

data class RevokeResponse(
    val success: Boolean
)

