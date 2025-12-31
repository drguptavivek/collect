package org.aiims.odk.auth.api

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.net.HttpURLConnection
import java.net.URL
import org.aiims.odk.auth.managers.AiimsAuthManager
import org.aiims.odk.auth.storage.AiimsTokenProvider

/**
 * Real authentication client that connects to backend API
 */
class RealAuthClient private constructor(
    private val context: Context,
    private val apiUrl: String,
    private val authManager: AiimsAuthManager? = null
) : AuthClient {
    private var retrofit: Retrofit? = null
    private var apiService: AuthApiService? = null

    // Lazy initialization of Retrofit
    private fun getRetrofit(): Retrofit {
        return retrofit ?: synchronized(this) {
            // Check if we need an unsafe client (for local emulator/LAN testing against self-signed certs)
            val clientBuilder = okhttp3.OkHttpClient.Builder()

            if (shouldUseUnsafeClient(apiUrl)) {
                configureUnsafeClient(clientBuilder)
            }

            // ADD GLOBAL 401 INTERCEPTOR
            authManager?.let { am: AiimsAuthManager ->
                clientBuilder.addInterceptor(AuthInterceptor(
                    am,
                    AiimsTokenProvider.getInstance(context)
                ))
            }

            // Sanitize URL: Remove project path if present, as ApiService adds it
            // e.g. https://server/v1/projects/1 -> https://server/v1/
            var sanitizedUrl = apiUrl
            // Ensure we strip off the specific project path but keep the base (usually v1/)
            if (sanitizedUrl.contains("/projects/")) {
                sanitizedUrl = sanitizedUrl.substringBefore("/projects/") + "/"
            }
            // Retrofit requires base URL to end with /
            if (!sanitizedUrl.endsWith("/")) {
                sanitizedUrl += "/"
            }

            retrofit ?: Retrofit.Builder()
                .baseUrl(sanitizedUrl)
                .client(clientBuilder.build())
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .also { retrofit = it }
        }
    }

    private fun shouldUseUnsafeClient(url: String): Boolean {
        val lowerUrl = url.lowercase()
        return lowerUrl.contains("localhost") ||
            lowerUrl.contains("127.0.0.1") ||
            lowerUrl.contains("10.0.2.") || // Covers 10.0.2.2 and subnet
            lowerUrl.contains("192.168.") || // Covers 192.168.0.0/16
            lowerUrl.contains("central-dev") ||
            lowerUrl.contains("central.dev") ||
            lowerUrl.contains("central.local")
    }

    private fun configureUnsafeClient(builder: okhttp3.OkHttpClient.Builder) {
        try {
            // Create a trust manager that does not validate certificate chains
            val trustAllCerts = arrayOf<javax.net.ssl.TrustManager>(object : javax.net.ssl.X509TrustManager {
                override fun checkClientTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {}
                override fun checkServerTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {}
                override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = arrayOf()
            })

            // Install the all-trusting trust manager
            val sslContext = javax.net.ssl.SSLContext.getInstance("SSL")
            sslContext.init(null, trustAllCerts, java.security.SecureRandom())

            // Create an ssl socket factory with our all-trusting manager
            val sslSocketFactory = sslContext.socketFactory

            builder.sslSocketFactory(sslSocketFactory, trustAllCerts[0] as javax.net.ssl.X509TrustManager)
            builder.hostnameVerifier { _, _ -> true }

            // Force HTTP/1.1 to avoid HTTP 421 (Misdirected Request) errors common with HTTP/2 + Self Signed/Local IPs
            builder.protocols(java.util.Collections.singletonList(okhttp3.Protocol.HTTP_1_1))

            // Custom DNS: Map 'central-dev' and 'central.dev' to 10.0.2.2 (Simulator Localhost)
            // This allows us to use the hostname (satisfying Nginx SNI/Host headers) but route to the host machine
            builder.dns(object : okhttp3.Dns {
                override fun lookup(hostname: String): List<java.net.InetAddress> {
                    if (hostname.equals("central-dev", ignoreCase = true) ||
                        hostname.equals("central.dev", ignoreCase = true) ||
                        hostname.equals("central.local", ignoreCase = true)) {
                        try {
                            return java.util.Collections.singletonList(java.net.InetAddress.getByName("10.0.2.2"))
                        } catch (e: Exception) {
                            Log.w("RealAuthClient", "Custom DNS resolution failed for $hostname")
                        }
                    }
                    return okhttp3.Dns.SYSTEM.lookup(hostname)
                }
            })

            Log.w("RealAuthClient", "UNSAFE SSL: Enabled for $apiUrl (Mapped central-dev -> 10.0.2.2)")
        } catch (e: Exception) {
            Log.e("RealAuthClient", "Error creating unsafe client", e)
        }
    }

    // Lazy initialization of API service
    private fun getApiService(): AuthApiService {
        return apiService ?: synchronized(this) {
            apiService ?: getRetrofit().create(AuthApiService::class.java).also { apiService = it }
        }
    }



    /**
     * Login to the real API
     */

    /**
     * Login to the Central Backend API
     */
    override suspend fun login(projectId: String, username: String, password: String, deviceId: String, comments: String?): AuthResult {
        return withContext(Dispatchers.IO) {
            try {
                Log.d("AiimsAuthClient", "Attempting login for user: $username on project: $projectId device: $deviceId")

                // Create login request
                val request = LoginRequest(
                    username = username,
                    password = password,
                    deviceId = deviceId,
                    comments = comments
                )

                Log.d("AiimsAuthClient", "Making API call to ${getApiService()}")
                // Make API call
                val response = getApiService().login(projectId, request)

                Log.d("AiimsAuthClient", "Response code: ${response.code()}")

                if (response.isSuccessful) {
                    val body = response.body()
                    if (body != null) {
                        Log.d("AiimsAuthClient", "Login successful")

                        // Parse server time from response body (ISO 8601 format)
                        val serverTime = body.serverTime?.let { parseIsoDateTime(it) }

                        // Construct User object from response + input
                        val user = User(
                            id = body.id.toString(),
                            username = username,
                            projectId = body.projectId.toString(),
                            expiresAt = body.expiresAt
                        )

                        AuthResult.Success(
                            user = user,
                            token = body.token,
                            expiresAt = body.expiresAt,
                            serverTime = serverTime
                        )
                    } else {
                        Log.e("AiimsAuthClient", "Empty response body")
                        AuthResult.Error("Empty response from server")
                    }
                } else {
                    // Handle HTTP errors
                Log.e("AiimsAuthClient", "HTTP Error: ${response.code()}")
                when (response.code()) {
                    400 -> AuthResult.Error("Invalid request")
                    401 -> AuthResult.Error("Invalid credentials")
                    403 -> AuthResult.Error("Access forbidden")
                    404 -> AuthResult.Error("Project or User not found")
                    429 -> {
                        // Rate limited - extract retry-after if present
                        val retryAfter = response.headers()["Retry-After"]?.toLongOrNull() ?: 600 // Default 10 min
                        val retryMinutes = (retryAfter / 60).coerceAtLeast(1)
                        AuthResult.Error("Too many login attempts. Please try again in $retryMinutes minutes.")
                    }
                    500 -> AuthResult.Error("Server error")
                    else -> AuthResult.Error("Login failed: HTTP ${response.code()}")
                }
                }
            } catch (e: Exception) {
                Log.e("AiimsAuthClient", "Login exception: ${e.message}", e)
                AuthResult.Error("Network error: ${e.message}")
            }
        }
    }

    /**
     * Extract server time from HTTP response Date header.
     * Returns null if the header is missing or invalid.
     * NOTE: Now deprecated - serverTime is provided in response body.
     */
    private fun extractServerTime(response: retrofit2.Response<LoginResponse>): Long? {
        return extractServerTimeFromResponse(response)
    }

    /**
     * Parse ISO 8601 datetime string to milliseconds since epoch.
     * Handles formats: "2025-12-21T10:02:00.000Z" or "2025-12-21T10:02:00.000+00:00"
     */
    private fun parseIsoDateTime(isoString: String): Long? {
        return try {
            // Try parsing with timezone indicator
            val format = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US)
            format.timeZone = java.util.TimeZone.getTimeZone("GMT")
            format.parse(isoString)?.time
        } catch (e: Exception) {
            try {
                // Fallback to parsing without 'Z' suffix
                val format = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", java.util.Locale.US)
                format.timeZone = java.util.TimeZone.getTimeZone("GMT")
                format.parse(isoString)?.time
            } catch (e2: Exception) {
                Log.w("AiimsAuthClient", "Failed to parse ISO datetime: $isoString")
                null
            }
        }
    }

    /**
     * Extract server time from HTTP Date header (generic version).
     * Returns null if the header is missing or invalid.
     * NOTE: Fallback method - primary source should be response body serverTime field.
     */
    private fun extractServerTimeFromResponse(response: retrofit2.Response<*>): Long? {
        val dateHeader = response.headers()["Date"] ?: return null
        return try {
            // HTTP Date format: "Wed, 21 Oct 2015 07:28:00 GMT"
            val format = java.text.SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", java.util.Locale.US)
            format.timeZone = java.util.TimeZone.getTimeZone("GMT")
            format.parse(dateHeader)?.time
        } catch (e: Exception) {
            Log.w("AiimsAuthClient", "Failed to parse server Date header: $dateHeader")
            null
        }
    }

    /**
     * Revoke device token by ID. If authToken is provided, send it as Bearer header.
     */

    /**
     * Revoke session by ID.
     */
    override suspend fun revokeSession(projectId: String, userId: String, authToken: String, deviceId: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val header = "Bearer $authToken"
                val request = RevokeRequest(deviceId = deviceId)
                val response = getApiService().revokeSession(projectId, userId, header, request)
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body?.success == true) {
                        Log.d("AiimsAuthClient", "Session revoked: $userId")
                        true
                    } else {
                        Log.e("AiimsAuthClient", "Revoke failed")
                        false
                    }
                } else {
                    Log.e("AiimsAuthClient", "Revoke HTTP error ${response.code()}")
                    false
                }
            } catch (e: Exception) {
                Log.e("AiimsAuthClient", "Revoke exception: ${e.message}", e)
                false
            }
        }
    }

    override suspend fun checkReachability(): Boolean = withContext(Dispatchers.IO) {
        try {
            // Remove trailing slash if present then append /version.txt
            val cleanUrl = if (apiUrl.endsWith("/")) apiUrl.dropLast(1) else apiUrl
            val url = URL("$cleanUrl/version.txt")

            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000 // 5 seconds timeout
            connection.readTimeout = 5000

            // Handle different response codes gracefully
            val responseCode = try {
                connection.responseCode
            } catch (e: java.io.IOException) {
                -1 // Network failure
            }

            val reachable = responseCode == HttpURLConnection.HTTP_OK
            Log.d("AiimsAuthClient", "Reachability check to $url returned: $responseCode (Reachable: $reachable)")
            reachable
        } catch (e: Exception) {
            Log.e("AiimsAuthClient", "Reachability check failed", e)
            false
        }
    }

    override suspend fun submitTelemetry(projectId: String, authToken: String, request: TelemetryRequest): TelemetryResult {
        return withContext(Dispatchers.IO) {
            try {
                val header = "Bearer $authToken"
                val response = getApiService().submitTelemetry(projectId, header, request)

                if (response.isSuccessful) {
                    val body = response.body()
                    if (body != null) {
                        TelemetryResult.Success(body)
                    } else {
                        TelemetryResult.ApiError("Empty response body", response.code())
                    }
                } else {
                    Log.e("AiimsAuthClient", "Telemetry failed: ${response.code()}")
                    when (response.code()) {
                        401 -> TelemetryResult.AuthError
                        else -> TelemetryResult.ApiError("Telemetry submission failed", response.code())
                    }
                }
            } catch (e: Exception) {
                Log.e("AiimsAuthClient", "Telemetry exception: ${e.message}", e)
                TelemetryResult.NetworkError
            }
        }
    }

    override suspend fun fetchProject(projectId: String, authToken: String): ProjectResponse? {
        return withContext(Dispatchers.IO) {
            try {
                val header = "Bearer $authToken"
                
                // Use the specific project endpoint (now allowed by server modification)
                val response = getApiService().getProject(projectId, header)

                if (response.isSuccessful) {
                    response.body()
                } else {
                    val errorBody = response.errorBody()?.string()
                    Log.e("AiimsAuthClient", "Fetch project failed: ${response.code()} Body: $errorBody")
                    null
                }
            } catch (e: Exception) {
                Log.e("AiimsAuthClient", "Fetch project exception: ${e.message}", e)
                null
            }
        }
    }


    companion object {
        @Volatile
        private var INSTANCE: RealAuthClient? = null

        @Volatile
        private var lastBaseUrl: String? = null

        @Volatile
        private var lastAuthManager: AiimsAuthManager? = null

        fun getInstance(context: Context, apiUrl: String, authManager: AiimsAuthManager? = null): RealAuthClient {
            return INSTANCE?.takeIf { lastBaseUrl == apiUrl && lastAuthManager == authManager } 
                ?: synchronized(this) {
                    INSTANCE?.takeIf { lastBaseUrl == apiUrl && lastAuthManager == authManager } 
                        ?: RealAuthClient(context, apiUrl, authManager).also {
                            INSTANCE = it
                            lastBaseUrl = apiUrl
                            lastAuthManager = authManager
                        }
                }
        }
    }
}
