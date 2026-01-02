package edu.aiims.medresodk.auth.api

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Simple API client for MEDRES authentication (avoids StrictMode issues)
 */
class MedresApiClient private constructor(
    private val context: Context,
    private val authStorage: edu.aiims.medresodk.auth.storage.MedresAuthStorage
) {
    companion object {
        @Volatile
        private var INSTANCE: MedresApiClient? = null

        fun getInstance(context: Context): MedresApiClient {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: createInstance(context.applicationContext).also { INSTANCE = it }
            }
        }

        private fun createInstance(context: Context): MedresApiClient {
            // Ensure authStorage is created on the same thread
            val authStorage = edu.aiims.medresodk.auth.storage.MedresAuthStorageImpl.getInstance(context)
            return MedresApiClient(context, authStorage)
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
                        username = email,
                        projectId = "1",
                        expiresAt = null
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
