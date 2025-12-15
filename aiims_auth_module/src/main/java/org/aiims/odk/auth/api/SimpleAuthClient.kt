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
    }
}