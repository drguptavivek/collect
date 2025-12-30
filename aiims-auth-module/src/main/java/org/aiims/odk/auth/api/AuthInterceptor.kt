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

        if (response.code == 401) {
            val url = originalRequest.url.toString()
            if (url.contains("/login") || url.contains("/restore")) {
                android.util.Log.i("AuthInterceptor", "Skipping re-auth for login/restore path.")
                return response
            }

            android.util.Log.w("AuthInterceptor", "Detected 401 for: $url")
            
            // Close the initial unauthorized response body to avoid leaks
            response.close()

            // Trigger re-authentication and wait for it
            val success = runBlocking {
                authManager.awaitReauthentication()
            }

            if (success) {
                android.util.Log.i("AuthInterceptor", "Re-authentication succeeded. Retrying request.")
                
                // Get fresh token
                val newToken = tokenProvider.getActiveProjectToken()
                
                if (newToken != null) {
                    // Build new request with updated token
                    val newRequest = originalRequest.newBuilder()
                        .header("Authorization", "Bearer $newToken")
                        .build()
                    
                    return chain.proceed(newRequest)
                }
            } else {
                android.util.Log.e("AuthInterceptor", "Re-authentication failed or cancelled.")
            }
        }

        return response
    }
}
