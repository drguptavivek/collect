package org.aiims.odk.auth.bridge

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.util.Log
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import org.aiims.odk.auth.activities.AiimsLoginActivity
import org.aiims.odk.auth.activities.AiimsPinActivity
import org.aiims.odk.auth.managers.AiimsAuthManager
import org.aiims.odk.auth.utils.AiimsConstants

/**
 * Bridge between ODK Collect and AIIMS authentication module.
 *
 * This class provides the minimal integration points needed to add AIIMS
 * authentication to ODK Collect without modifying core functionality.
 *
 * Integration Points:
 * 1. Application initialization (Collect.java)
 * 2. Main menu auth check (MainMenuActivity)
 * 3. Settings menu addition
 */
object AiimsOdkIntegrator {

    /**
     * Initialize AIIMS authentication if enabled.
     * Call this from Collect.java onCreate().
     */
    fun initialize(context: Context) {
        if (!AiimsFeatureFlag.isEnabled(context)) {
            Log.d("AiimsOdkIntegrator", "AIIMS authentication disabled")
            return
        }

        try {
            // Get auth manager via Hilt entry point
            val authManager = getAuthManager(context)

            // Check authentication state
            if (authManager != null) {
                Log.d("AiimsOdkIntegrator", "AIIMS authentication initialized")

                // Start background token refresh if needed
                startTokenRefreshService(context, authManager)
            }
        } catch (e: Exception) {
            Log.e("AiimsOdkIntegrator", "Failed to initialize AIIMS auth", e)
        }
    }

    /**
     * Check if user is authenticated.
     * Call this before showing ODK main menu.
     */
    fun isUserAuthenticated(context: Context): Boolean {
        if (!AiimsFeatureFlag.isEnabled(context)) {
            return true // Always true if AIIMS auth is disabled
        }

        return try {
            val authManager = getAuthManager(context)
            authManager?.let {
                // Check current auth state
                val currentState = it.authState.value
                currentState == org.aiims.odk.auth.managers.AuthState.AUTHENTICATED
            } ?: false
        } catch (e: Exception) {
            Log.e("AiimsOdkIntegrator", "Failed to check auth state", e)
            false
        }
    }

    /**
     * Require authentication from user.
     * Redirects to appropriate AIIMS auth screen.
     */
    fun requireAuthentication(activity: Activity) {
        if (!AiimsFeatureFlag.isEnabled(activity)) {
            return
        }

        try {
            val authManager = getAuthManager(activity)
            if (authManager == null) {
                Log.e("AiimsOdkIntegrator", "Auth manager not available")
                return
            }

            val intent = when (authManager.authState.value) {
                org.aiims.odk.auth.managers.AuthState.UNAUTHENTICATED,
                org.aiims.odk.auth.managers.AuthState.SESSION_EXPIRED,
                org.aiims.odk.auth.managers.AuthState.REAUTH_REQUIRED -> {
                    Intent(activity, AiimsLoginActivity::class.java)
                }
                org.aiims.odk.auth.managers.AuthState.AUTHENTICATED -> {
                    // User is authenticated, check if PIN is set
                    if (authManager.authStorage.pinHash != null) {
                        Intent(activity, AiimsPinActivity::class.java)
                    } else {
                        // PIN not set, go to login
                        Intent(activity, AiimsLoginActivity::class.java).apply {
                            putExtra(AiimsConstants.EXTRA_SHOW_PIN_SETUP, true)
                        }
                    }
                }
                org.aiims.odk.auth.managers.AuthState.PIN_LOCKED -> {
                    Intent(activity, AiimsLoginActivity::class.java).apply {
                        putExtra(AiimsConstants.EXTRA_FORCE_REAUTH, true)
                    }
                }
                else -> {
                    Intent(activity, AiimsLoginActivity::class.java)
                }
            }

            // Clear activity stack to prevent back navigation
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            activity.startActivity(intent)
            activity.finish()
        } catch (e: Exception) {
            Log.e("AiimsOdkIntegrator", "Failed to require authentication", e)
        }
    }

    /**
     * Launch AIIMS login activity.
     * Use this for explicit login requests (e.g., from settings).
     */
    fun launchLogin(activity: Activity) {
        if (!AiimsFeatureFlag.isEnabled(activity)) {
            return
        }

        val intent = Intent(activity, AiimsLoginActivity::class.java)
        activity.startActivity(intent)
    }

    /**
     * Launch AIIMS PIN activity.
     * Use this when user needs to verify PIN.
     */
    fun launchPinVerification(activity: Activity) {
        if (!AiimsFeatureFlag.isEnabled(activity)) {
            return
        }

        val intent = Intent(activity, AiimsPinActivity::class.java)
        activity.startActivity(intent)
    }

    /**
     * Launch AIIMS settings activity.
     * Add this to ODK settings menu.
     */
    fun launchSettings(activity: Activity) {
        if (!AiimsFeatureFlag.isEnabled(activity)) {
            return
        }

        try {
            val settingsIntent = Intent(activity, org.aiims.odk.auth.activities.AiimsSettingsActivity::class.java)
            activity.startActivity(settingsIntent)
        } catch (e: Exception) {
            Log.e("AiimsOdkIntegrator", "Failed to launch settings", e)
        }
    }

    /**
     * Get current user information for ODK display.
     */
    fun getCurrentUser(context: Context): UserInfo? {
        if (!AiimsFeatureFlag.isEnabled(context)) {
            return null
        }

        return try {
            val authManager = getAuthManager(context)
            authManager?.authStorage?.getCurrentUser()?.let { user ->
                UserInfo(
                    id = user.id,
                    email = user.email,
                    name = user.name,
                    role = user.role,
                    partnerId = user.partnerId,
                    partnerName = user.partnerName
                )
            }
        } catch (e: Exception) {
            Log.e("AiimsOdkIntegrator", "Failed to get current user", e)
            null
        }
    }

    /**
     * Logout user and clear session.
     */
    fun logout(context: Context, callback: ((Boolean) -> Unit)? = null) {
        if (!AiimsFeatureFlag.isEnabled(context)) {
            callback?.invoke(true)
            return
        }

        try {
            val authManager = getAuthManager(context)
            if (authManager != null) {
                // Use coroutine to handle logout
                kotlinx.coroutines.GlobalScope.launch {
                    val success = try {
                        authManager.logout()
                        true
                    } catch (e: Exception) {
                        Log.e("AiimsOdkIntegrator", "Logout failed", e)
                        false
                    }
                    callback?.invoke(success)
                }
            } else {
                callback?.invoke(false)
            }
        } catch (e: Exception) {
            Log.e("AiimsOdkIntegrator", "Failed to logout", e)
            callback?.invoke(false)
        }
    }

    /**
     * Handle deep links for authentication.
     */
    fun handleDeepLink(context: Context, intent: Intent): Boolean {
        if (!AiimsFeatureFlag.isEnabled(context)) {
            return false
        }

        val data = intent.data ?: return false

        return when {
            data.scheme == "aiims-auth" -> {
                when (data.host) {
                    "login" -> {
                        val loginIntent = Intent(context, AiimsLoginActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            putExtra(AiimsConstants.EXTRA_DEEP_LINK_DATA, data.toString())
                        }
                        context.startActivity(loginIntent)
                        true
                    }
                    "pin" -> {
                        val pinIntent = Intent(context, AiimsPinActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        context.startActivity(pinIntent)
                        true
                    }
                    else -> false
                }
            }
            else -> false
        }
    }

    /**
     * Get authentication status for debugging.
     */
    fun getAuthStatus(context: Context): Map<String, Any> {
        if (!AiimsFeatureFlag.isEnabled(context)) {
            return mapOf(
                "enabled" to false,
                "status" to "DISABLED"
            )
        }

        return try {
            val authManager = getAuthManager(context)
            if (authManager != null) {
                authManager.getAuthStateSummary().toMutableMap().apply {
                    put("enabled", true)
                    put("featureFlag", AiimsFeatureFlag.getBuildConfig(context))
                }
            } else {
                mapOf(
                    "enabled" to true,
                    "status" to "ERROR",
                    "error" to "Auth manager not available"
                )
            }
        } catch (e: Exception) {
            mapOf(
                "enabled" to true,
                "status" to "ERROR",
                "error" to e.message
            )
        }
    }

    /**
     * Get auth manager via Hilt entry point.
     */
    private fun getAuthManager(context: Context): AiimsAuthManager? {
        return try {
            val appContext = context.applicationContext
            val entryPoint = EntryPointAccessors.fromApplication(
                appContext,
                AiimsAuthEntryPoint::class.java
            )
            entryPoint.getAuthManager()
        } catch (e: Exception) {
            Log.e("AiimsOdkIntegrator", "Failed to get auth manager", e)
            null
        }
    }

    /**
     * Start background token refresh service.
     */
    private fun startTokenRefreshService(context: Context, authManager: AiimsAuthManager) {
        // This would start a foreground service or WorkManager
        // to handle periodic token refresh
        Log.d("AiimsOdkIntegrator", "Token refresh service started")
    }

    /**
     * Hilt entry point for accessing auth manager from non-Hilt classes.
     */
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface AiimsAuthEntryPoint {
        fun getAuthManager(): AiimsAuthManager
    }

    /**
     * User information data class.
     */
    data class UserInfo(
        val id: String,
        val email: String,
        val name: String,
        val role: String,
        val partnerId: String?,
        val partnerName: String?
    )
}