package org.aiims.odk.auth.api

import android.content.Context
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

/**
 * Simple API client for AIIMS authentication (avoids StrictMode issues)
 */
class AiimsApiClient private constructor(
    private val context: Context,
    private val authStorage: org.aiims.odk.auth.storage.AiimsAuthStorage
) {
    companion object {
        @Volatile
        private var INSTANCE: AiimsApiClient? = null

        fun getInstance(context: Context): AiimsApiClient {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: createInstance(context.applicationContext).also { INSTANCE = it }
            }
        }

        private fun createInstance(context: Context): AiimsApiClient {
            // Ensure authStorage is created on the same thread
            val authStorage = org.aiims.odk.auth.storage.AiimsAuthStorage.getInstance(context)
            return AiimsApiClient(context, authStorage)
        }
    }

    // Main login method - fully asynchronous
    suspend fun login(email: String, password: String): AuthResult {
        return withContext(Dispatchers.IO) {
            try {
                // Ensure we're on IO thread before accessing authStorage
                val apiUrl = authStorage.apiUrl

                if (apiUrl.isBlank()) {
                    return@withContext AuthResult.Error("Server URL not configured")
                }

                // Simulate network operation without blocking main thread
                delay(500)

                // Basic validation
                if (email.contains("@") && password.isNotEmpty()) {
                    // Store authentication data (also on IO thread)
                    authStorage.deviceToken = "dummy-token-${System.currentTimeMillis()}"
                    authStorage.userId = "123"
                    authStorage.userEmail = email
                    authStorage.userName = "Test User"

                    val user = User(
                        id = "123",
                        email = email,
                        name = "Test User",
                        role = "team_member",
                        partnerId = null,
                        partnerName = null,
                        phoneNumber = null,
                        isActive = true,
                        dateActiveTill = null
                    )

                    AuthResult.Success(user)
                } else {
                    AuthResult.Error("Invalid credentials")
                }
            } catch (e: Exception) {
                AuthResult.Error("Login error: ${e.message}")
            }
        }
    }
}