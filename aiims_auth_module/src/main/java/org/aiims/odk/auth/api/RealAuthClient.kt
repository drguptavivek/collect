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
                retrofit ?: Retrofit.Builder()
                    .baseUrl(apiUrl)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()
                    .also { retrofit = it }
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
    suspend fun login(email: String, password: String): AuthResult {
        return withContext(Dispatchers.IO) {
            try {
                Log.d("AiimsAuthClient", "Attempting login for email: ${email.lowercase().trim()}")

                // Generate device info
                val deviceId = generateDeviceId()
                val deviceInfo = generateDeviceInfo()

                Log.d("AiimsAuthClient", "Device ID: $deviceId")
                Log.d("AiimsAuthClient", "Device Info: $deviceInfo")

                // Create login request
                val request = LoginRequest(
                    email = email.lowercase().trim(),
                    password = password,
                    deviceId = deviceId,
                    deviceInfo = deviceInfo
                )

                Log.d("AiimsAuthClient", "Making API call to ${getApiService()}")
                // Make API call
                val response = getApiService().login(request)

                Log.d("AiimsAuthClient", "Response code: ${response.code()}")

                if (response.isSuccessful) {
                    Log.d("AiimsAuthClient", "Response successful")
                    val loginResponse = response.body()

                    if (loginResponse?.success == true) {
                        Log.d("AiimsAuthClient", "Login successful, user data received")
                        val userData = loginResponse.user
                        if (userData != null) {
                            Log.d("AiimsAuthClient", "User: ${userData.name}, Role: ${userData.role}")
                            val user = User(
                                id = userData.id,
                                email = userData.email,
                                name = userData.name,
                                role = userData.role,
                                partnerId = userData.partnerId,
                                partnerName = userData.partnerName,
                                phoneNumber = null,
                                isActive = true,
                                dateActiveTill = null
                            )

                            if (loginResponse.requiresPinSetup == true) {
                                Log.d("AiimsAuthClient", "PIN setup required")
                                AuthResult.RequiresPin(
                                    user = user,
                                    token = loginResponse.deviceToken ?: "",
                                    expiresAt = loginResponse.expiresAt ?: ""
                                )
                            } else {
                                Log.d("AiimsAuthClient", "Login complete without PIN")
                                AuthResult.Success(
                                    user = user,
                                    token = loginResponse.deviceToken ?: "",
                                    expiresAt = loginResponse.expiresAt ?: ""
                                )
                            }
                        } else {
                            Log.e("AiimsAuthClient", "No user data in response")
                            AuthResult.Error("No user data received")
                        }
                    } else {
                        Log.e("AiimsAuthClient", "Login failed: ${loginResponse?.error ?: loginResponse?.message}")
                        AuthResult.Error(loginResponse?.error ?: loginResponse?.message ?: "Login failed")
                    }
                } else {
                    // Handle HTTP errors
                    Log.e("AiimsAuthClient", "HTTP Error: ${response.code()}")
                    when (response.code()) {
                        400 -> AuthResult.Error("Invalid request: Missing required fields")
                        401 -> AuthResult.Error("Invalid credentials")
                        403 -> AuthResult.Error("Access forbidden")
                        404 -> AuthResult.Error("API endpoint not found")
                        500 -> AuthResult.Error("Server error. Please try again later")
                        else -> AuthResult.Error("Login failed: HTTP ${response.code()}")
                    }
                }
            } catch (e: Exception) {
                // Handle network errors
                Log.e("AiimsAuthClient", "Login exception: ${e.message}", e)
                AuthResult.Error("Network error: ${e.message}")
            }
        }
    }

    /**
     * Revoke device token by ID. If authToken is provided, send it as Bearer header.
     */
    suspend fun revokeDeviceToken(tokenId: String, authToken: String?): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val header = if (!authToken.isNullOrBlank()) "Bearer $authToken" else null
                val response = getApiService().revokeDeviceToken(tokenId, header)
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body?.success == true) {
                        Log.d("AiimsAuthClient", "Device token revoked: $tokenId")
                        true
                    } else {
                        Log.e("AiimsAuthClient", "Revoke failed: ${body?.error ?: body?.message}")
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
