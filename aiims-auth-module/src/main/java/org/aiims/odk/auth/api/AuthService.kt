package org.aiims.odk.auth.api

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * Authentication API service interface
 */
interface AuthService {

    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>
}