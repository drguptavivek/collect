package org.aiims.odk.auth.managers

import android.content.Context
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.aiims.odk.auth.api.*

/**
 * Simplified auth manager for build
 */
class AiimsAuthManager private constructor(
    private val context: Context
) {

    companion object {
        @Volatile
        private var INSTANCE: AiimsAuthManager? = null

        fun getInstance(context: Context): AiimsAuthManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AiimsAuthManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val _authState = MutableStateFlow(AuthState.LOGGED_OUT)
    val authState: Flow<AuthState> = _authState.asStateFlow()

    private val _currentUser = MutableStateFlow<org.aiims.odk.auth.api.User?>(null)
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
                    result
                }
                is AuthResult.RequiresPin -> {
                    _authState.value = AuthState.REQUIRES_PIN
                    _currentUser.value = result.user
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

    suspend fun logout() {
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