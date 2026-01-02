package edu.aiims.medresodk.auth.api

import okhttp3.Interceptor
import okhttp3.Response
import edu.aiims.medresodk.auth.managers.MedresAuthManager
import edu.aiims.medresodk.auth.storage.MedresTokenProvider
import kotlinx.coroutines.runBlocking

/**
 * Global OkHttp Interceptor to catch 401 (Unauthorized) responses.
 * When a 401 is detected, it triggers MEDRES re-authentication flow (PIN/Password) 
 * and retries the request upon success.
 */
class AuthInterceptor(
    private val authManager: MedresAuthManager,
    private val tokenProvider: MedresTokenProvider
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val response = chain.proceed(originalRequest)

        if (response.code == 403) {
            android.util.Log.w("AuthInterceptor", "[MedresAuth] Detected 403 Forbidden for: ${originalRequest.url}. Check user roles/permissions.")
            return response
        }

        if (response.code == 401) {
            val url = originalRequest.url.toString()
            if (url.contains("/login") || url.contains("/restore")) {
                android.util.Log.i("AuthInterceptor", "[MedresAuth] Skipping re-auth for login/restore path: $url")
                return response
            }

            android.util.Log.w("AuthInterceptor", "[MedresAuth] Detected 401 Unauthorized for: $url. Triggering global re-authentication.")
            
            // Close the initial unauthorized response body to avoid leaks
            response.close()

            // Trigger re-authentication and wait for it
            val success = runBlocking {
                authManager.awaitReauthentication()
            }

            if (success) {
                android.util.Log.i("AuthInterceptor", "[MedresAuth] Re-authentication SUCCEEDED. Retrying request: $url")
                
                // Get fresh token
                val newToken = tokenProvider.getActiveProjectToken()
                
                if (newToken != null) {
                    // Build new request with updated token
                    val requestBuilder = originalRequest.newBuilder()
                        .header("Authorization", "Bearer $newToken")

                    // Also update URL if it's tokenized: /key/<token>/
                    val originalUrl = originalRequest.url.toString()
                    if (originalUrl.contains("/key/")) {
                        val newUrl = replaceTokenInUrl(originalUrl, newToken)
                        requestBuilder.url(newUrl)
                        android.util.Log.i("AuthInterceptor", "[MedresAuth] Updated tokenized URL for retry: $newUrl")
                    }
                    
                    return chain.proceed(requestBuilder.build())
                } else {
                     android.util.Log.e("AuthInterceptor", "[MedresAuth] Re-auth succeeded but token is NULL. Cannot retry.")
                }
            } else {
                android.util.Log.e("AuthInterceptor", "[MedresAuth] Re-authentication FAILED or CANCELLED for: $url")
            }
        }

        return response
    }

    /**
     * Replaces the token in a Central URL like .../key/<TOKEN>/projects/...
     */
    private fun replaceTokenInUrl(url: String, newToken: String): String {
        return try {
            if (url.contains("/key/")) {
                val beforeKey = url.substringBefore("/key/")
                val afterKey = url.substringAfter("/key/")
                // afterKey is: OLD_TOKEN/projects/1/formList
                val afterToken = afterKey.substringAfter("/", "")
                if (afterToken.isNotEmpty()) {
                    "$beforeKey/key/$newToken/$afterToken"
                } else {
                    // Just ends with /key/TOKEN
                    "$beforeKey/key/$newToken"
                }
            } else {
                url
            }
        } catch (e: Exception) {
            url
        }
    }
}
