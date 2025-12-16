package org.aiims.odk.auth.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.aiims.odk.auth.api.RealAuthClient

/**
 * Handles marking device tokens for revocation and retrying when connectivity returns.
 */
object TokenRevocationManager {

    private const val PREFS_NAME = "aiims_auth_prefs"
    private const val KEY_PENDING_TOKEN_ID = "pending_revoke_token_id"
    private const val KEY_PENDING_AUTH_TOKEN = "pending_revoke_auth_token"
    private const val KEY_PENDING_API_URL = "pending_revoke_api_url"
    private const val KEY_PENDING_REASON = "pending_revoke_reason"
    private const val KEY_PENDING_AT = "pending_revoke_at"

    fun markPending(
        context: Context,
        tokenId: String,
        apiUrl: String,
        authToken: String?,
        reason: String
    ) {
        if (tokenId.isBlank() || apiUrl.isBlank()) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().apply {
            putString(KEY_PENDING_TOKEN_ID, tokenId)
            putString(KEY_PENDING_AUTH_TOKEN, authToken)
            putString(KEY_PENDING_API_URL, apiUrl)
            putString(KEY_PENDING_REASON, reason)
            putLong(KEY_PENDING_AT, System.currentTimeMillis())
            apply()
        }
        Log.d("TokenRevocation", "Marked token $tokenId for revocation (reason=$reason)")
    }

    suspend fun processPending(context: Context): Boolean {
        return withContext(Dispatchers.IO) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val tokenId = prefs.getString(KEY_PENDING_TOKEN_ID, null) ?: return@withContext false
            val apiUrl = prefs.getString(KEY_PENDING_API_URL, null) ?: return@withContext false
            val authToken = prefs.getString(KEY_PENDING_AUTH_TOKEN, null)

            if (!isNetworkAvailable(context)) {
                Log.d("TokenRevocation", "Network unavailable; will retry later")
                return@withContext false
            }

            val success = RealAuthClient.getInstance(context, apiUrl).revokeDeviceToken(tokenId, authToken)
            if (success) {
                prefs.edit().apply {
                    remove(KEY_PENDING_TOKEN_ID)
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
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
