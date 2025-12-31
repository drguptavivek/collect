package org.aiims.odk.auth.analytics

import android.util.Log

/**
 * Centralized analytics helper for AIIMS Authentication Module.
 * Defines event names and logging utility methods.
 * 
 * DESIGN NOTE: This module is strictly isolated and does NOT depend on 
 * org.odk.collect.analytics avoids circular dependencies and maintains modularity.
 */
object AiimsAppAnalytics {

    private const val TAG = "AiimsAnalytics"

    // --- Event Names (Auth) ---
    private const val AUTH_LOGIN_ATTEMPT = "aiims_auth_login_attempt"
    private const val AUTH_LOGIN_SUCCESS = "aiims_auth_login_success"
    private const val AUTH_LOGIN_FAILED = "aiims_auth_login_failed"
    private const val AUTH_TOKEN_EXPIRED = "aiims_auth_token_expired"
    private const val AUTH_GRACE_PERIOD_STARTED = "aiims_auth_grace_period_started"
    private const val AUTH_HARD_LOGOUT = "aiims_auth_hard_logout"
    private const val AUTH_REAUTH_PROMPT = "aiims_auth_reauth_prompt"

    // --- Event Names (Network) ---
    private const val NET_ERROR = "aiims_net_error"
    private const val NET_SERVER_TIME_OFFSET = "aiims_net_server_time_offset"

    // --- Event Names (Security) ---
    private const val SEC_CLOCK_MANIPULATION = "aiims_sec_clock_manipulation"
    private const val SEC_MANIPULATION_CLEARED = "aiims_sec_manipulation_cleared"

    // --- Event Names (Storage) ---
    private const val STORE_ERROR = "aiims_store_error"

    // --- Internal Logging Helper ---
    private fun logEvent(event: String, params: Map<String, String>? = null) {
        try {
            if (params != null) {
                Log.i(TAG, "Event: $event Params: $params")
            } else {
                Log.i(TAG, "Event: $event")
            }
        } catch (e: RuntimeException) {
            // Safe fallback for tests where Log is not mocked
            println("$TAG [TEST_FALLBACK]: Event: $event Params: $params")
        }
        // TODO: Hook into a persistent analytics provider if needed later
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
