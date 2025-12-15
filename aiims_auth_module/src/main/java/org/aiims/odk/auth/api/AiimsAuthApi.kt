package org.aiims.odk.auth.api

import retrofit2.Response
import retrofit2.http.*

/**
 * Retrofit API interface for AIIMS authentication endpoints.
 */
interface AiimsAuthApi {

    /**
     * Authenticate user with email and password.
     *
     * @param apiUrl Base API URL
     * @param request Login credentials and device info
     * @return Login response with user info and tokens
     */
    @POST
    suspend fun login(
        @Url apiUrl: String,
        @Body request: LoginRequest
    ): Response<ApiResponse<LoginResponse>>

    /**
     * Verify device token validity.
     *
     * @param apiUrl Base API URL
     * @param request Token verification request
     * @return Token verification response
     */
    @POST
    suspend fun verifyToken(
        @Url apiUrl: String,
        @Body request: VerifyTokenRequest
    ): Response<ApiResponse<VerifyTokenResponse>>

    /**
     * Refresh expired device token.
     *
     * @param apiUrl Base API URL
     * @param request Refresh token request
     * @return New tokens response
     */
    @POST
    suspend fun refreshToken(
        @Url apiUrl: String,
        @Body request: RefreshTokenRequest
    ): Response<ApiResponse<RefreshTokenResponse>>

    /**
     * Logout user and invalidate token.
     *
     * @param apiUrl Base API URL
     * @param request Logout request with token
     * @return Logout response
     */
    @POST
    suspend fun logout(
        @Url apiUrl: String,
        @Body request: LogoutRequest
    ): Response<ApiResponse<Unit>>

    /**
     * Register device for user.
     *
     * @param apiUrl Base API URL
     * @param request Device registration details
     * @return Registration response
     */
    @POST
    suspend fun registerDevice(
        @Url apiUrl: String,
        @Body request: DeviceRegistrationRequest
    ): Response<ApiResponse<Unit>>

    /**
     * Get user profile information.
     *
     * @param apiUrl Base API URL
     * @param authorization Bearer token
     * @return User profile response
     */
    @GET
    suspend fun getUserProfile(
        @Url apiUrl: String,
        @Header("Authorization") authorization: String
    ): Response<ApiResponse<User>>

    /**
     * Get schools for partner.
     *
     * @param apiUrl Base API URL
     * @param authorization Bearer token
     * @param partnerId Partner ID (optional, uses user's partner if not provided)
     * @return List of schools
     */
    @GET
    suspend fun getSchoolsByPartner(
        @Url apiUrl: String,
        @Header("Authorization") authorization: String,
        @Query("partnerId") partnerId: String? = null
    ): Response<ApiResponse<List<School>>>

    /**
     * Submit survey data.
     *
     * @param apiUrl Base API URL
     * @param authorization Bearer token
     * @param request Survey submission data
     * @return Submission response
     */
    @POST
    suspend fun submitSurvey(
        @Url apiUrl: String,
        @Header("Authorization") authorization: String,
        @Body request: SurveySubmissionRequest
    ): Response<ApiResponse<SurveySubmissionResponse>>

    /**
     * Bulk sync offline data.
     *
     * @param apiUrl Base API URL
     * @param authorization Bearer token
     * @param request Bulk sync data
     * @return Sync response with results
     */
    @POST
    suspend fun bulkSync(
        @Url apiUrl: String,
        @Header("Authorization") authorization: String,
        @Body request: BulkSyncRequest
    ): Response<ApiResponse<BulkSyncResponse>>
}