package org.aiims.odk.auth.managers

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.aiims.odk.auth.api.*
import org.json.JSONObject

/**
 * Simplified auth manager for build
 */
class AiimsAuthManager private constructor(
    private val context: Context
) {

    companion object {
        @Volatile
        private var INSTANCE: AiimsAuthManager? = null
        private const val PREFS_NAME = "aiims_auth_prefs"
        private const val KEY_AUTH_STATE = "auth_state"
        private const val KEY_USER_DATA = "user_data"
        private const val KEY_AUTH_TOKEN = "auth_token"
        private const val KEY_EXPIRES_AT = "expires_at"

        fun getInstance(context: Context): AiimsAuthManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AiimsAuthManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _authState = MutableStateFlow(getPersistedAuthState())
    val authState: Flow<AuthState> = _authState.asStateFlow()

    private val _currentUser = MutableStateFlow<org.aiims.odk.auth.api.User?>(getPersistedUser())
    val currentUser: Flow<org.aiims.odk.auth.api.User?> = _currentUser.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: Flow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: Flow<String?> = _errorMessage.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    suspend fun login(email: String, password: String, apiUrl: String): AuthResult {
        return try {
            _isLoading.value = true
            _errorMessage.value = null

            // Use real authentication client
            val realAuthClient = RealAuthClient.getInstance(context, apiUrl)
            val result = realAuthClient.login(email, password)

            when (result) {
                is AuthResult.Success -> {
                    _authState.value = AuthState.LOGGED_IN
                    _currentUser.value = result.user
                    // Persist the auth state
                    persistAuthState(AuthState.LOGGED_IN, result.user, null, null)
                    result
                }
                is AuthResult.RequiresPin -> {
                    _authState.value = AuthState.REQUIRES_PIN
                    _currentUser.value = result.user
                    // Persist that PIN is required
                    persistAuthState(AuthState.REQUIRES_PIN, result.user, result.token, result.expiresAt)
                    result
                }
                else -> {
                    result
                }
            }
        } catch (e: Exception) {
            _errorMessage.value = e.message
            AuthResult.Error(e.message ?: "Login failed")
        } finally {
            _isLoading.value = false
        }
    }

    private fun getPersistedAuthState(): AuthState {
        return try {
            val stateString = prefs.getString(KEY_AUTH_STATE, null)
            android.util.Log.d("AiimsAuthManager", "Retrieved auth state: $stateString")
            when (stateString) {
                "LOGGED_IN" -> {
                    // Only consider logged in if token is not expired
                    if (!isTokenExpired()) {
                        android.util.Log.d("AiimsAuthManager", "Token valid, state: LOGGED_IN")
                        AuthState.LOGGED_IN
                    } else {
                        android.util.Log.d("AiimsAuthManager", "Token expired, state: LOGGED_OUT")
                        AuthState.LOGGED_OUT
                    }
                }
                "REQUIRES_PIN" -> {
                    android.util.Log.d("AiimsAuthManager", "State: REQUIRES_PIN")
                    AuthState.REQUIRES_PIN
                }
                else -> {
                    android.util.Log.d("AiimsAuthManager", "State: LOGGED_OUT (default)")
                    AuthState.LOGGED_OUT
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("AiimsAuthManager", "Error getting persisted auth state", e)
            AuthState.LOGGED_OUT
        }
    }

    private fun getPersistedUser(): org.aiims.odk.auth.api.User? {
        return try {
            val userJson = prefs.getString(KEY_USER_DATA, null)
            if (userJson != null) {
                val json = JSONObject(userJson)
                org.aiims.odk.auth.api.User(
                    id = json.getString("id"),
                    email = json.getString("email"),
                    name = json.getString("name"),
                    role = json.getString("role"),
                    partnerId = json.optString("partnerId", null),
                    partnerName = json.optString("partnerName", null),
                    phoneNumber = null,
                    isActive = json.optBoolean("isActive", true),
                    dateActiveTill = json.optString("dateActiveTill", null)
                )
            } else null
        } catch (e: Exception) {
            null
        }
    }

    private fun isTokenExpired(): Boolean {
        return try {
            val expiresAt = prefs.getString(KEY_EXPIRES_AT, null)
            if (expiresAt == null) return true

            val expiryTime = org.aiims.odk.auth.utils.ApiDateFormat.parse(expiresAt)?.time ?: 0L
            System.currentTimeMillis() >= expiryTime
        } catch (e: Exception) {
            true
        }
    }

    fun persistAuthState(state: AuthState, user: org.aiims.odk.auth.api.User?, token: String?, expiresAt: String?) {
        prefs.edit().apply {
            putString(KEY_AUTH_STATE, state.name)

            user?.let {
                val userJson = JSONObject().apply {
                    put("id", it.id)
                    put("email", it.email)
                    put("name", it.name)
                    put("role", it.role)
                    it.partnerId?.let { put("partnerId", it) }
                    it.partnerName?.let { put("partnerName", it) }
                    put("isActive", it.isActive)
                    it.dateActiveTill?.let { put("dateActiveTill", it) }
                }.toString()
                putString(KEY_USER_DATA, userJson)
            }

            token?.let { putString(KEY_AUTH_TOKEN, it) }
            expiresAt?.let { putString(KEY_EXPIRES_AT, it) }

            apply()
        }
    }

    fun persistAuthStateWithoutClearing(state: AuthState) {
        // Only update the auth state, keep existing user data and tokens
        prefs.edit().apply {
            putString(KEY_AUTH_STATE, state.name)
            apply()
        }
        android.util.Log.d("AiimsAuthManager", "Updated auth state to: ${state.name} without clearing existing data")
    }

    suspend fun logout() {
        // Clear persisted data
        prefs.edit().clear().apply()

        _authState.value = AuthState.LOGGED_OUT
        _currentUser.value = null
        _isLoading.value = false
    }
}

/**
 * Authentication states
 */
enum class AuthState {
    INITIAL,
    LOGGED_IN,
    LOGGED_OUT,
    REQUIRES_PIN,
    ERROR
}