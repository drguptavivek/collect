package edu.aiims.medresodk.auth.storage

import android.content.Context
import android.content.SharedPreferences

/**
 * Simple token provider that reads auth tokens from SharedPreferences.
 * 
 * This class is designed to be lightweight and independent of the full
 * MedresAuthManager dependency graph, avoiding circular dependencies.
 */
class MedresTokenProvider private constructor(
    private val context: Context
) {
    companion object {
        private const val PREFS_NAME = "medres_auth_prefs"
        private const val KEY_ACTIVE_PROJECT_ID = "active_project_id"
        private const val PREFIX_AUTH_TOKEN = "auth_token_"
        private const val KEY_LEGACY_TOKEN = "auth_token"

        @Volatile
        private var INSTANCE: MedresTokenProvider? = null

        fun getInstance(context: Context): MedresTokenProvider {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: MedresTokenProvider(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Get the auth token for the currently active project.
     * 
     * @return Bearer token string or null if not authenticated
     */
    fun getActiveProjectToken(): String? {
        return try {
            // Try active project first
            val activePid = prefs.getString(KEY_ACTIVE_PROJECT_ID, null)
            if (activePid != null) {
                val token = prefs.getString(PREFIX_AUTH_TOKEN + activePid, null)
                if (token != null) return token
            }

            // Fallback to legacy/default token
            prefs.getString(KEY_LEGACY_TOKEN, null)
        } catch (e: Exception) {
            null
        }
    }
}
