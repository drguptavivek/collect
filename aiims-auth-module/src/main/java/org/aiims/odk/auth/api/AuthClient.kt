package org.aiims.odk.auth.api

/**
 * Interface for Authentication Client.
 * Abstracts the network calls to allow mocking in unit tests.
 */
interface AuthClient {
    suspend fun login(projectId: String, username: String, password: String, deviceId: String, comments: String?): AuthResult
    suspend fun revokeSession(projectId: String, userId: String, authToken: String, deviceId: String): Boolean
    suspend fun checkReachability(): Boolean
    suspend fun submitTelemetry(projectId: String, authToken: String, request: TelemetryRequest): TelemetryResponse?
    suspend fun fetchProject(projectId: String, authToken: String): ProjectResponse?
}
