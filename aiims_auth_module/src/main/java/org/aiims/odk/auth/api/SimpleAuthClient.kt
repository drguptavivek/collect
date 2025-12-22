package org.aiims.odk.auth.api

import kotlinx.coroutines.delay

/**
 * Very simple authentication client with no storage operations
 */
class SimpleAuthClient {

    // Simple login with no disk operations to avoid StrictMode
    suspend fun login(email: String, password: String): AuthResult {
        // Simulate network delay
        delay(500)

        // Basic validation
        return if (email.contains("@") && password.isNotEmpty()) {
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
    }
}