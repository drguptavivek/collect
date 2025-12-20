package org.aiims.odk.auth.api

/**
 * Interface for Authentication Client.
 * Abstracts the network calls to allow mocking in unit tests.
 */
interface AuthClient {
    suspend fun login(projectId: String, username: String, password: String): AuthResult
    suspend fun revokeSession(projectId: String, userId: String, authToken: String): Boolean
    suspend fun checkReachability(): Boolean
}
