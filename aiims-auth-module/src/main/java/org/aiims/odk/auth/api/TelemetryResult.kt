package org.aiims.odk.auth.api

/**
 * Result of a telemetry submission.
 */
sealed class TelemetryResult {
    /**
     * Submission successful.
     * @param response The server response.
     */
    data class Success(val response: TelemetryResponse) : TelemetryResult()

    /**
     * Authentication failed (401).
     * Server does NOT record telemetry in this case.
     */
    object AuthError : TelemetryResult()

    /**
     * Network-related error (offline, timeout, etc.).
     */
    object NetworkError : TelemetryResult()

    /**
     * Other API error (404, 500, etc.).
     * @param message Error message.
     * @param code HTTP status code if available.
     */
    data class ApiError(val message: String, val code: Int? = null) : TelemetryResult()
}
