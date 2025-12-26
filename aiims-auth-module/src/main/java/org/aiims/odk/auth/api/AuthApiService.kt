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
        @Header("Authorization") authHeader: String,
        @Body request: RevokeRequest
    ): Response<RevokeResponse>

    @POST("projects/{projectId}/app-users/telemetry")
    suspend fun submitTelemetry(
        @Path("projectId") projectId: String,
        @Header("Authorization") authHeader: String,
        @Body request: TelemetryRequest
    ): Response<TelemetryResponse>
}

/**
 * Login request body
 */
data class LoginRequest(
    val username: String,
    val password: String,
    val deviceId: String,
    val comments: String? = null
)

/**
 * Revoke request body
 */
data class RevokeRequest(
    val deviceId: String
)

/**
 * Telemetry request body
 */
data class TelemetryRequest(
    val deviceId: String,
    val collectVersion: String,
    val deviceDateTime: String, // UTC ISO
    val location: TelemetryLocation
)

data class TelemetryLocation(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double?,
    val accuracy: Float?,
    val speed: Float?,
    val bearing: Float?,
    val provider: String?
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

data class TelemetryResponse(
    val id: Int,
    val dateTime: String
)

