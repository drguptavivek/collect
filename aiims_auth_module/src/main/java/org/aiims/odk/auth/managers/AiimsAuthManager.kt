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
    private val context: Context,
    private val apiClient: AiimsApiClient,
    private val authStorage: org.aiims.odk.auth.storage.AiimsAuthStorage,
    private val securityUtils: org.aiims.odk.auth.utils.AiimsSecurityUtils
) {

    companion object {
        @Volatile
        private var INSTANCE: AiimsAuthManager? = null

        fun getInstance(context: Context): AiimsAuthManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: createInstance(context.applicationContext).also { INSTANCE = it }
            }
        }

        private fun createInstance(context: Context): AiimsAuthManager {
            val apiClient = AiimsApiClient.getInstance(context)
            val authStorage = org.aiims.odk.auth.storage.AiimsAuthStorage.getInstance(context)
            val securityUtils = org.aiims.odk.auth.utils.AiimsSecurityUtils.getInstance(context)
            return AiimsAuthManager(context, apiClient, authStorage, securityUtils)
        }
    }

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
        initializeAuthState()
    }

    private fun initializeAuthState() {
        _authState.value = AuthState.LOGGED_OUT
    }

    suspend fun login(email: String, password: String): AuthResult {
        return try {
            _isLoading.value = true
            _errorMessage.value = null

            val result = apiClient.login(email, password)
            if (result is AuthResult.Success) {
                _authState.value = AuthState.LOGGED_IN
                _currentUser.value = result.user
                authStorage.isAuthenticated = true
            }
            result
        } catch (e: Exception) {
            _errorMessage.value = e.message
            AuthResult.Error(e.message ?: "Login failed")
        } finally {
            _isLoading.value = false
        }
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