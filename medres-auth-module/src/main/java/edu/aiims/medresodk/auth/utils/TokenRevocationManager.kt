package edu.aiims.medresodk.auth.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import edu.aiims.medresodk.auth.api.AuthClient
import edu.aiims.medresodk.auth.api.RealAuthClient

/**
 * Handles marking device tokens for revocation and retrying when connectivity returns.
 */
object TokenRevocationManager {

    private const val PREFS_NAME = "medres_auth_prefs"
    private const val KEY_PENDING_PROJECT_ID = "pending_revoke_project_id"
    private const val KEY_PENDING_USER_ID = "pending_revoke_user_id"
    private const val KEY_PENDING_AUTH_TOKEN = "pending_revoke_auth_token"
    private const val KEY_PENDING_API_URL = "pending_revoke_api_url"
    private const val KEY_PENDING_REASON = "pending_revoke_reason"
    private const val KEY_PENDING_AT = "pending_revoke_at"

    private var authClientForTesting: AuthClient? = null
    private var ioDispatcherForTesting: CoroutineDispatcher? = null
    private var isNetworkAvailableForTesting: Boolean? = null

    @androidx.annotation.VisibleForTesting
    fun setAuthClient(client: AuthClient) {
        authClientForTesting = client
    }

    @androidx.annotation.VisibleForTesting
    fun setIoDispatcher(dispatcher: CoroutineDispatcher) {
        ioDispatcherForTesting = dispatcher
    }

    @androidx.annotation.VisibleForTesting
    fun setNetworkAvailable(available: Boolean) {
        isNetworkAvailableForTesting = available
    }

    @androidx.annotation.VisibleForTesting
    fun resetForTesting() {
        authClientForTesting = null
        ioDispatcherForTesting = null
        isNetworkAvailableForTesting = null
    }

    fun markPending(
        context: Context,
        projectId: String,
        userId: String,
        apiUrl: String,
        authToken: String?,
        reason: String
    ) {
        if (projectId.isBlank() || userId.isBlank() || apiUrl.isBlank()) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().apply {
            putString(KEY_PENDING_PROJECT_ID, projectId)
            putString(KEY_PENDING_USER_ID, userId)
            putString(KEY_PENDING_AUTH_TOKEN, authToken)
            putString(KEY_PENDING_API_URL, apiUrl)
            putString(KEY_PENDING_REASON, reason)
            putLong(KEY_PENDING_AT, System.currentTimeMillis())
            apply()
        }
        Log.d("TokenRevocation", "Marked session for user $userId (project $projectId) for revocation")
    }

    suspend fun processPending(context: Context): Boolean {
        return withContext(ioDispatcherForTesting ?: Dispatchers.IO) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val projectId = prefs.getString(KEY_PENDING_PROJECT_ID, null) ?: return@withContext false
            val userId = prefs.getString(KEY_PENDING_USER_ID, null) ?: return@withContext false
            val apiUrl = prefs.getString(KEY_PENDING_API_URL, null) ?: return@withContext false
            val authToken = prefs.getString(KEY_PENDING_AUTH_TOKEN, null) ?: ""

            if (!isNetworkAvailable(context)) {
                Log.d("TokenRevocation", "Network unavailable; will retry later")
                return@withContext false
            }

            val client = authClientForTesting ?: RealAuthClient.getInstance(context, apiUrl)
            val deviceId = context.getSharedPreferences("meta", Context.MODE_PRIVATE)
                .getString("metadata_installid", "unknown_device") ?: "unknown_device"

            val success = client.revokeSession(projectId, userId, authToken, deviceId)
            if (success) {
                prefs.edit().apply {
                    remove(KEY_PENDING_PROJECT_ID)
                    remove(KEY_PENDING_USER_ID)
                    remove(KEY_PENDING_AUTH_TOKEN)
                    remove(KEY_PENDING_API_URL)
                    remove(KEY_PENDING_REASON)
                    remove(KEY_PENDING_AT)
                    apply()
                }
            }
            success
        }
    }

    private fun isNetworkAvailable(context: Context): Boolean {
        if (isNetworkAvailableForTesting != null) {
            return isNetworkAvailableForTesting!!
        }
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
