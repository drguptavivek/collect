package org.aiims.odk.auth.api

import android.content.Context
import android.os.Build
import android.util.Log
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.UUID

/**
 * Real authentication client that connects to backend API
 */
class RealAuthClient private constructor(
    private val context: Context,
    private val apiUrl: String
) {
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
                   lowerUrl.contains("10.0.2.") ||   // Covers 10.0.2.2 and subnet
                   lowerUrl.contains("192.168.") ||  // Covers 192.168.0.0/16
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

    companion object {
        @Volatile
        private var INSTANCE: RealAuthClient? = null
        @Volatile
        private var lastBaseUrl: String? = null

        fun getInstance(context: Context, apiUrl: String): RealAuthClient {
            return INSTANCE?.takeIf { lastBaseUrl == apiUrl } ?: synchronized(this) {
                INSTANCE?.takeIf { lastBaseUrl == apiUrl } ?: RealAuthClient(context, apiUrl).also {
                    INSTANCE = it
                    lastBaseUrl = apiUrl
                }
            }
        }
    }

    /**
     * Login to the real API
     */
    /**
     * Login to the Central Backend API
     */
    suspend fun login(projectId: String, username: String, password: String): AuthResult {
        return withContext(Dispatchers.IO) {
            try {
                Log.d("AiimsAuthClient", "Attempting login for user: $username on project: $projectId")

                // Create login request
                val request = LoginRequest(
                    username = username,
                    password = password
                )

                Log.d("AiimsAuthClient", "Making API call to ${getApiService()}")
                // Make API call
                val response = getApiService().login(projectId, request)

                Log.d("AiimsAuthClient", "Response code: ${response.code()}")

                if (response.isSuccessful) {
                    val body = response.body()
                    if (body != null) {
                        Log.d("AiimsAuthClient", "Login successful")
                        
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
                            expiresAt = body.expiresAt
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
     * Revoke device token by ID. If authToken is provided, send it as Bearer header.
     */
    /**
     * Revoke session by ID.
     */
    suspend fun revokeSession(projectId: String, userId: String, authToken: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val header = "Bearer $authToken"
                val response = getApiService().revokeSession(projectId, userId, header)
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

    /**
     * Generate or retrieve device ID
     */
    private fun generateDeviceId(): String {
        val aiimsPrefs = context.getSharedPreferences("aiims_auth_prefs", Context.MODE_PRIVATE)
        val collectMetaPrefs = context.getSharedPreferences("meta", Context.MODE_PRIVATE)

        // Prefer the existing Collect install ID (shown as Device ID in ODK settings)
        val existingCollectId = collectMetaPrefs.getString("metadata_installid", null)

        var deviceId = aiimsPrefs.getString("device_id", null) ?: existingCollectId

        if (deviceId == null) {
            // Generate the same shape as Collect: "collect:" + 16-char random string
            val randomSuffix = org.odk.collect.shared.strings.RandomString.randomString(16)
            deviceId = "collect:$randomSuffix"

            // Persist to both meta (so ODK shows it) and our local cache
            collectMetaPrefs.edit().putString("metadata_installid", deviceId).apply()
            aiimsPrefs.edit().putString("device_id", deviceId).apply()
        } else {
            // Ensure our cache stores it for consistency
            aiimsPrefs.edit().putString("device_id", deviceId).apply()
        }

        return deviceId
    }

    /**
     * Generate device information string
     */
    private fun generateDeviceInfo(): String {
        val manufacturer = Build.MANUFACTURER ?: "Unknown"
        val model = Build.MODEL ?: "Unknown"
        val osVersion = Build.VERSION.RELEASE ?: "Unknown"
        val appVersion = context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "Unknown"

        return "Android $osVersion; $manufacturer $model; App v$appVersion"
    }
}
