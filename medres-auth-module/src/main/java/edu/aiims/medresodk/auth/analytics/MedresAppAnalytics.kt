package edu.aiims.medresodk.auth.analytics

import android.util.Log

/**
 * Centralized analytics helper for MEDRES Authentication Module.
 * Defines event names and logging utility methods.
 * 
 * DESIGN NOTE: This module is strictly isolated and does NOT depend on 
 * org.odk.collect.analytics avoids circular dependencies and maintains modularity.
 */
object MedresAppAnalytics {

    private const val TAG = "MedresAnalytics"

    // --- Event Names (Auth) ---
    private const val AUTH_LOGIN_ATTEMPT = "medres_auth_login_attempt"
    private const val AUTH_LOGIN_SUCCESS = "medres_auth_login_success"
    private const val AUTH_LOGIN_FAILED = "medres_auth_login_failed"
    private const val AUTH_TOKEN_EXPIRED = "medres_auth_token_expired"
    private const val AUTH_GRACE_PERIOD_STARTED = "medres_auth_grace_period_started"
    private const val AUTH_HARD_LOGOUT = "medres_auth_hard_logout"
    private const val AUTH_REAUTH_PROMPT = "medres_auth_reauth_prompt"

    // --- Event Names (Network) ---
    private const val NET_ERROR = "medres_net_error"
    private const val NET_SERVER_TIME_OFFSET = "medres_net_server_time_offset"

    // --- Event Names (Security) ---
    private const val SEC_CLOCK_MANIPULATION = "medres_sec_clock_manipulation"
    private const val SEC_MANIPULATION_CLEARED = "medres_sec_manipulation_cleared"

    // --- Event Names (Storage) ---
    private const val STORE_ERROR = "medres_store_error"

    // --- Internal Logging Helper ---
    // --- Internal Logging Helper ---
    
    fun init(context: android.content.Context) {
        MedresFileLogger.init(context)
    }

    private fun logEvent(event: String, params: Map<String, String>? = null) {
        val message = if (params != null) {
            "Event: $event Params: $params"
        } else {
            "Event: $event"
        }

        // Always write to file
        MedresFileLogger.log("INFO", TAG, message)

        try {
            if (edu.aiims.medresodk.auth.BuildConfig.DEBUG) {
                 Log.i(TAG, message)
            }
        } catch (e: RuntimeException) {
            // Safe fallback for tests or if BuildConfig fails
             println("$TAG [TEST_FALLBACK]: $message")
        }
    }

    // --- Auth Logging ---

    fun logLoginAttempt() {
        logEvent(AUTH_LOGIN_ATTEMPT)
    }

    fun logLoginSuccess() {
        logEvent(AUTH_LOGIN_SUCCESS)
    }

    fun logLoginFailed(reason: String) {
        logEvent(AUTH_LOGIN_FAILED, mapOf("reason" to reason))
    }

    fun logTokenExpired() {
        logEvent(AUTH_TOKEN_EXPIRED)
    }

    fun logGracePeriodStarted() {
        logEvent(AUTH_GRACE_PERIOD_STARTED)
    }

    fun logHardLogout(reason: String) {
        logEvent(AUTH_HARD_LOGOUT, mapOf("reason" to reason))
    }

    fun logReauthPromptShown() {
        logEvent(AUTH_REAUTH_PROMPT)
    }

    // --- Network Logging ---

    fun logNetworkError(type: String) {
        logEvent(NET_ERROR, mapOf("type" to type))
    }
    
    fun logServerTimeOffset(offsetMs: String) {
         logEvent(NET_SERVER_TIME_OFFSET, mapOf("offset_ms" to offsetMs))
    }

    // --- Security Logging ---

    fun logClockManipulationDetected() {
        logEvent(SEC_CLOCK_MANIPULATION)
    }

    fun logClockManipulationCleared() {
        logEvent(SEC_MANIPULATION_CLEARED)
    }

    // --- Storage Logging ---

    fun logStorageError(type: String) {
        logEvent(STORE_ERROR, mapOf("type" to type))
    }
}
