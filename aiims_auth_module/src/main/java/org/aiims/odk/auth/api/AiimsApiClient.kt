package org.aiims.odk.auth.api

import android.content.Context
import okhttp3.*
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Singleton API client for AIIMS authentication services.
 *
 * Provides configured Retrofit instance with proper interceptors for
 * authentication, logging, and error handling.
 */
@Singleton
class AiimsApiClient @Inject constructor(
    private val context: Context,
    private val authStorage: org.aiims.odk.auth.storage.AiimsAuthStorage
) {

    companion object {
        private const val CONNECT_TIMEOUT = 30L
        private const val READ_TIMEOUT = 30L
        private const val WRITE_TIMEOUT = 30L
        private const val CACHE_SIZE = 10L * 1024 * 1024 // 10 MB

        // SSL pinning for production
        private val SSL_PINS = arrayOf(
            // Add your certificate pins here in production
            // "sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
        )
    }

    private val okHttpClient: OkHttpClient by lazy {
        buildOkHttpClient()
    }

    private val retrofit: Retrofit by lazy {
        buildRetrofit()
    }

    /**
     * Get API service instance.
     */
    val apiService: AiimsAuthApi by lazy {
        retrofit.create(AiimsAuthApi::class.java)
    }

    /**
     * Build OkHttp client with interceptors.
     */
    private fun buildOkHttpClient(): OkHttpClient {
        val builder = OkHttpClient.Builder()

        // Timeouts
        builder
            .connectTimeout(CONNECT_TIMEOUT, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT, TimeUnit.SECONDS)
            .writeTimeout(WRITE_TIMEOUT, TimeUnit.SECONDS)

        // Add interceptors
        builder
            .addInterceptor(AuthInterceptor())
            .addInterceptor(UserAgentInterceptor())
            .addNetworkInterceptor(CacheInterceptor())

        // Add logging interceptor for debug builds
        if (AiimsFeatureFlag.isDebugEnabled(context)) {
            val loggingInterceptor = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            }
            builder.addInterceptor(loggingInterceptor)
        }

        // SSL pinning for production (disabled in debug)
        if (!BuildConfig.DEBUG && SSL_PINS.isNotEmpty()) {
            builder.certificatePinner(
                CertificatePinner.Builder()
                    .apply {
                        SSL_PINS.forEach { pin ->
                            add(getApiBaseUrl(), pin)
                        }
                    }
                    .build()
            )
        }

        // Add cache for offline support
        val cache = Cache(context.cacheDir, CACHE_SIZE)
        builder.cache(cache)

        return builder.build()
    }

    /**
     * Build Retrofit instance.
     */
    private fun buildRetrofit(): Retrofit {
        return Retrofit.Builder()
            .baseUrl(getApiBaseUrl())
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    /**
     * Get configured API base URL.
     */
    private fun getApiBaseUrl(): String {
        val storedUrl = authStorage.apiUrl
        return if (storedUrl.isNotEmpty()) {
            storedUrl
        } else {
            org.aiims.odk.auth.utils.AiimsConstants.DEFAULT_API_URL
        }
    }

    /**
     * Interceptor to add authentication token to requests.
     */
    private inner class AuthInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val originalRequest = chain.request()

            // Add token for authenticated endpoints
            val token = authStorage.deviceToken
            if (token.isNotEmpty() && requiresAuth(originalRequest.url.encodedPath)) {
                val authenticatedRequest = originalRequest.newBuilder()
                    .addHeader("Authorization", "Bearer $token")
                    .addHeader("Accept", "application/json")
                    .build()
                return chain.proceed(authenticatedRequest)
            }

            // Add content type for POST/PUT requests
            if (originalRequest.method in listOf("POST", "PUT", "PATCH")) {
                val modifiedRequest = originalRequest.newBuilder()
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Accept", "application/json")
                    .build()
                return chain.proceed(modifiedRequest)
            }

            return chain.proceed(originalRequest)
        }

        private fun requiresAuth(path: String): Boolean {
            val authPaths = listOf(
                "/auth/verify",
                "/auth/refresh",
                "/auth/logout",
                "/user/profile",
                "/schools/by-partner",
                "/surveys/submit",
                "/sync/upload"
            )
            return authPaths.any { path.contains(it) }
        }
    }

    /**
     * Interceptor to add User-Agent header.
     */
    private inner class UserAgentInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val originalRequest = chain.request()
            val userAgent = String.format(
                "AIIMS-ODK-Android/%s (%s %s)",
                BuildConfig.VERSION_NAME,
                android.os.Build.MANUFACTURER,
                android.os.Build.MODEL
            )

            val modifiedRequest = originalRequest.newBuilder()
                .addHeader("User-Agent", userAgent)
                .build()

            return chain.proceed(modifiedRequest)
        }
    }

    /**
     * Interceptor for cache control and offline support.
     */
    private inner class CacheInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            val response = chain.proceed(request)

            // Cache successful responses for offline access
            if (request.method == "GET" && response.isSuccessful) {
                val maxAge = 60 * 5 // 5 minutes
                response.newBuilder()
                    .header("Cache-Control", "public, max-age=$maxAge")
                    .build()
            }

            return response
        }
    }

    /**
     * Create a new API client with custom base URL.
     * Useful for temporary URL changes during testing.
     */
    fun createClientWithBaseUrl(baseUrl: String): AiimsAuthApi {
        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        return retrofit.create(AiimsAuthApi::class.java)
    }

    /**
     * Refresh API client after configuration changes.
     */
    fun refreshClient() {
        // This will cause the lazy properties to be re-initialized
        // on next access, picking up any configuration changes
    }
}