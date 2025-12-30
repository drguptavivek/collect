package org.aiims.odk.auth.api

import okhttp3.Interceptor
import okhttp3.Response
import org.aiims.odk.auth.managers.AiimsAuthManager
import org.aiims.odk.auth.storage.AiimsTokenProvider
import kotlinx.coroutines.runBlocking

/**
 * Global OkHttp Interceptor to catch 401 (Unauthorized) responses.
 * When a 401 is detected, it triggers AIIMS re-authentication flow (PIN/Password) 
 * and retries the request upon success.
 */
class AuthInterceptor(
    private val authManager: AiimsAuthManager,
    private val tokenProvider: AiimsTokenProvider
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val response = chain.proceed(originalRequest)

        if (response.code == 403) {
            android.util.Log.w("AuthInterceptor", "[AiimsAuth] Detected 403 Forbidden for: ${originalRequest.url}. Check user roles/permissions.")
            return response
        }

        if (response.code == 401) {
            val url = originalRequest.url.toString()
            if (url.contains("/login") || url.contains("/restore")) {
                android.util.Log.i("AuthInterceptor", "[AiimsAuth] Skipping re-auth for login/restore path: $url")
                return response
            }

            android.util.Log.w("AuthInterceptor", "[AiimsAuth] Detected 401 Unauthorized for: $url. Triggering global re-authentication.")
            
            // Close the initial unauthorized response body to avoid leaks
            response.close()

            // Trigger re-authentication and wait for it
            val success = runBlocking {
                authManager.awaitReauthentication()
            }

            if (success) {
                android.util.Log.i("AuthInterceptor", "[AiimsAuth] Re-authentication SUCCEEDED. Retrying request: $url")
                
                // Get fresh token
                val newToken = tokenProvider.getActiveProjectToken()
                
                if (newToken != null) {
                    // Build new request with updated token
                    val newRequest = originalRequest.newBuilder()
                        .header("Authorization", "Bearer $newToken")
                        .build()
                    
                    return chain.proceed(newRequest)
                } else {
                     android.util.Log.e("AuthInterceptor", "[AiimsAuth] Re-auth succeeded but token is NULL. Cannot retry.")
                }
            } else {
                android.util.Log.e("AuthInterceptor", "[AiimsAuth] Re-authentication FAILED or CANCELLED for: $url")
            }
        }

        return response
    }
}
