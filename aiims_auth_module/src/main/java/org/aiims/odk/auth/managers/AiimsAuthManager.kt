package org.aiims.odk.auth.managers

import android.content.Context
import android.provider.Settings
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.aiims.odk.auth.api.*
import org.aiims.odk.auth.storage.AiimsAuthStorage
import org.aiims.odk.auth.utils.AiimsConstants
import org.aiims.odk.auth.utils.AiimsSecurityUtils
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Central authentication manager that coordinates all authentication operations.
 *
 * Handles login, logout, PIN verification, token management, and offline access.
 * Provides reactive state updates via StateFlow for UI components.
 */
@Singleton
class AiimsAuthManager @Inject constructor(
    private val context: Context,
    private val apiClient: AiimsApiClient,
    private val authStorage: AiimsAuthStorage,
    private val securityUtils: AiimsSecurityUtils
) {

    private val _authState = MutableStateFlow(AuthState.INITIAL)
    val authState: Flow<AuthState> = _authState.asStateFlow()

    private val _currentUser = MutableStateFlow<org.aiims.odk.auth.api.User?>(null)
    val currentUser: Flow<org.aiims.odk.auth.api.User?> = _currentUser.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: Flow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: Flow<String?> = _errorMessage.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    init {
        // Initialize authentication state on startup
        initializeAuthState()

        // Generate device ID if not exists
        if (authStorage.deviceId.isEmpty()) {
            authStorage.deviceId = securityUtils.generateDeviceId()
        }
    }

    /**
     * Initialize authentication state from stored data.
     */
    private fun initializeAuthState() {
        scope.launch {
            if (authStorage.isAuthenticated) {
                // Check if session is still valid
                if (isSessionValid()) {
                    _authState.value = AuthState.AUTHENTICATED
                    _currentUser.value = authStorage.getCurrentUser()
                } else {
                    // Session expired, require re-authentication
                    _authState.value = AuthState.SESSION_EXPIRED
                    authStorage.isAuthenticated = false
                }
            } else {
                _authState.value = AuthState.UNAUTHENTICATED
            }
        }
    }

    /**
     * Login with email and password.
     *
     * @param email User email
     * @param password User password
     * @param apiUrl API URL (optional, uses stored if not provided)
     * @return Result indicating success or failure
     */
    suspend fun login(
        email: String,
        password: String,
        apiUrl: String = authStorage.apiUrl.ifEmpty { AiimsConstants.DEFAULT_API_URL }
    ): AuthResult {
        _isLoading.value = true
        _errorMessage.value = null

        return withContext(Dispatchers.IO) {
            try {
                // Prepare login request
                val deviceId = authStorage.deviceId
                val deviceInfo = getDeviceInfo()
                val request = LoginRequest(email, password, deviceId, deviceInfo)

                // Make API call
                val response = apiClient.apiService.login(
                    apiUrl = "$apiUrl${AiimsConstants.ApiEndpoints.AUTH_LOGIN}",
                    request = request
                )

                if (response.isSuccessful) {
                    val apiResponse = response.body()
                    if (apiResponse?.success == true) {
                        val loginData = apiResponse.data!!

                        // Store session
                        authStorage.saveAuthSession(
                            token = loginData.deviceToken,
                            refreshToken = loginData.refreshToken,
                            expiresAt = loginData.expiresAt,
                            user = loginData.user,
                            apiUrl = apiUrl
                        )

                        // Save API URL if different
                        if (apiUrl != authStorage.apiUrl) {
                            authStorage.apiUrl = apiUrl
                        }

                        _authState.value = AuthState.AUTHENTICATED
                        _currentUser.value = loginData.user

                        // Return result with PIN setup requirement
                        AuthResult.Success(
                            requiresPinSetup = loginData.requiresPinSetup,
                            isFirstLogin = loginData.isFirstLogin
                        )
                    } else {
                        _errorMessage.value = apiResponse?.error?.message ?: "Login failed"
                        AuthResult.Error(_errorMessage.value!!)
                    }
                } else {
                    val errorMessage = parseApiError(response)
                    _errorMessage.value = errorMessage
                    AuthResult.Error(errorMessage)
                }
            } catch (e: Exception) {
                val errorMsg = "Network error: ${e.message}"
                _errorMessage.value = errorMsg
                AuthResult.Error(errorMsg)
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Verify PIN.
     *
     * @param PIN to verify
     * @return Result indicating success or failure
     */
    suspend fun verifyPin(pin: String): AuthResult {
        // Check if PIN is locked
        if (authStorage.isPinLocked()) {
            val unlockTime = authStorage.getPinUnlockTime()
            _errorMessage.value = "PIN locked. Try again in ${unlockTime / 60000} minutes"
            return AuthResult.Error(_errorMessage.value!!)
        }

        // Get stored PIN hash and salt
        val storedHash = authStorage.pinHash
        val salt = authStorage.pinSalt

        return if (storedHash != null && salt != null) {
            if (securityUtils.verifyPin(pin, storedHash, salt)) {
                // PIN correct
                authStorage.resetPinAttempts()
                authStorage.updateLastAuthTimestamp()
                _authState.value = AuthState.AUTHENTICATED
                AuthResult.Success
            } else {
                // PIN incorrect
                val attempts = authStorage.incrementPinAttempts()
                if (attempts >= AiimsConstants.MAX_PIN_ATTEMPTS) {
                    _errorMessage.value = "Too many incorrect attempts. PIN locked for 24 hours."
                    AuthResult.Error(_errorMessage.value!!)
                } else {
                    val remaining = AiimsConstants.MAX_PIN_ATTEMPTS - attempts
                    _errorMessage.value = "Incorrect PIN. $remaining attempts remaining."
                    AuthResult.Error(_errorMessage.value!!)
                }
            }
        } else {
            _errorMessage.value = "PIN not set"
            AuthResult.Error(_errorMessage.value!!)
        }
    }

    /**
     * Setup PIN for user.
     *
     * @param PIN to set
     * @param confirmPIN to verify
     * @return Result indicating success or failure
     */
    suspend fun setupPin(pin: String, confirmPin: String): AuthResult {
        // Validate PINs
        if (pin != confirmPin) {
            _errorMessage.value = "PINs do not match"
            return AuthResult.Error(_errorMessage.value!!)
        }

        val strength = securityUtils.validatePinStrength(pin)
        when (strength) {
            AiimsSecurityUtils.PinStrength.TOO_SHORT -> {
                _errorMessage.value = "PIN too short (minimum ${AiimsConstants.PIN_LENGTH_MIN} digits)"
                return AuthResult.Error(_errorMessage.value!!)
            }
            AiimsSecurityUtils.PinStrength.TOO_LONG -> {
                _errorMessage.value = "PIN too long (maximum ${AiimsConstants.PIN_LENGTH_MAX} digits)"
                return AuthResult.Error(_errorMessage.value!!)
            }
            AiimsSecurityUtils.PinStrength.TOO_SIMPLE -> {
                _errorMessage.value = "PIN too simple. Use different digits"
                return AuthResult.Error(_errorMessage.value!!)
            }
            else -> {
                // PIN is acceptable
            }
        }

        return try {
            // Generate salt and hash
            val salt = securityUtils.generateSalt()
            val hash = securityUtils.hashPin(pin, salt)

            // Store PIN
            authStorage.pinHash = hash
            authStorage.pinSalt = salt
            authStorage.resetPinAttempts()

            _authState.value = AuthState.AUTHENTICATED
            AuthResult.Success
        } catch (e: Exception) {
            _errorMessage.value = "Failed to set PIN: ${e.message}"
            AuthResult.Error(_errorMessage.value!!)
        }
    }

    /**
     * Refresh authentication token.
     *
     * @return true if refresh was successful
     */
    suspend fun refreshToken(): Boolean {
        val refreshToken = authStorage.refreshToken
        if (refreshToken.isEmpty()) {
            return false
        }

        return try {
            val response = apiClient.apiService.refreshToken(
                apiUrl = "${authStorage.apiUrl}${AiimsConstants.ApiEndpoints.AUTH_REFRESH}",
                request = RefreshTokenRequest(refreshToken)
            )

            if (response.isSuccessful) {
                val apiResponse = response.body()
                if (apiResponse?.success == true) {
                    val tokenData = apiResponse.data!!
                    authStorage.deviceToken = tokenData.deviceToken
                    authStorage.refreshToken = tokenData.refreshToken
                    authStorage.tokenExpiry = parseTimestamp(tokenData.expiresAt)
                    true
                } else {
                    false
                }
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Logout user and clear session.
     */
    suspend fun logout() {
        _isLoading.value = true

        try {
            // Call logout API if token exists
            val token = authStorage.deviceToken
            if (token.isNotEmpty()) {
                apiClient.apiService.logout(
                    apiUrl = "${authStorage.apiUrl}${AiimsConstants.ApiEndpoints.AUTH_LOGOUT}",
                    request = LogoutRequest(
                        deviceToken = token,
                        deviceId = authStorage.deviceId
                    )
                )
            }
        } catch (e: Exception) {
            // Continue with local logout even if API call fails
        }

        // Clear local data
        authStorage.clearAuthData()
        _authState.value = AuthState.UNAUTHENTICATED
        _currentUser.value = null
        _isLoading.value = false
    }

    /**
     * Check if current session is valid.
     */
    private fun isSessionValid(): Boolean {
        // Check if tokens exist
        if (authStorage.deviceToken.isEmpty()) {
            return false
        }

        // Check token expiry
        if (authStorage.isTokenExpired()) {
            // Try to refresh token
            return runBlocking { refreshToken() }
        }

        // Check offline access period
        if (!authStorage.isOfflineAccessAllowed()) {
            return false
        }

        // Check auto-logout timeout
        if (authStorage.shouldAutoLogout()) {
            return false
        }

        return true
    }

    /**
     * Get device information for API requests.
     */
    private fun getDeviceInfo(): String {
        val manufacturer = android.os.Build.MANUFACTURER
        val model = android.os.Build.MODEL
        val version = android.os.Build.VERSION.RELEASE
        return "$manufacturer $model, Android $version"
    }

    /**
     * Parse API error response.
     */
    private fun parseApiError(response: retrofit2.Response<*>): String {
        return try {
            val errorBody = response.errorBody()?.string()
            if (errorBody?.isNotEmpty() == true) {
                // Try to parse error from API response
                // This would need proper JSON parsing
                "Authentication failed"
            } else {
                when (response.code()) {
                    400 -> "Invalid request"
                    401 -> "Invalid credentials"
                    403 -> "Access denied"
                    404 -> "Service not found"
                    429 -> "Too many requests"
                    500 -> "Server error"
                    503 -> "Service unavailable"
                    else -> "Authentication failed (${response.code()})"
                }
            }
        } catch (e: Exception) {
            "Authentication failed"
        }
    }

    /**
     * Parse ISO 8601 timestamp.
     */
    private fun parseTimestamp(timestamp: String): Long {
        return try {
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
                .parse(timestamp)?.time ?: 0L
        } catch (e: Exception) {
            0L
        }
    }

    /**
     * Force re-authentication.
     */
    fun forceReauth() {
        _authState.value = AuthState.REAUTH_REQUIRED
        authStorage.isAuthenticated = false
    }

    /**
     * Get authentication state summary.
     */
    fun getAuthStateSummary(): Map<String, Any> {
        return authStorage.getAuthSummary()
    }
}

/**
 * Authentication states.
 */
enum class AuthState {
    INITIAL,
    UNAUTHENTICATED,
    AUTHENTICATED,
    SESSION_EXPIRED,
    REAUTH_REQUIRED,
    PIN_LOCKED
}

/**
 * Authentication result.
 */
sealed class AuthResult {
    object Success : AuthResult() {
        var requiresPinSetup: Boolean = false
            private set

        var isFirstLogin: Boolean = false
            private set

        fun withPinSetup(requires: Boolean, firstLogin: Boolean = false): AuthResult {
            val result = Success
            result.requiresPinSetup = requires
            result.isFirstLogin = firstLogin
            return result
        }
    }

    data class Error(val message: String) : AuthResult()
}