package org.aiims.odk.auth.managers

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.aiims.odk.auth.api.AuthClient
import org.aiims.odk.auth.api.AuthResult
import org.aiims.odk.auth.api.RealAuthClient
import org.aiims.odk.auth.api.User
import org.aiims.odk.auth.utils.AiimsProjectUtils
import org.json.JSONObject

/**
 * Authentication Manager for Central Backend.
 * Supports Multi-Project Isolation.
 */
class AiimsAuthManager private constructor(
    private val context: Context,
    private val projectCleaner: ProjectCleaner
) {

    companion object {
        @Volatile
        private var INSTANCE: AiimsAuthManager? = null
        private const val PREFS_NAME = "aiims_auth_prefs"

        // Global keys
        private const val KEY_ACTIVE_PROJECT_ID = "active_project_id"

        @JvmStatic
        fun getInstance(context: Context): AiimsAuthManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: throw IllegalStateException("AiimsAuthManager must be initialized with ProjectCleaner first")
            }
        }

        @JvmStatic
        fun init(context: Context, projectCleaner: ProjectCleaner): AiimsAuthManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AiimsAuthManager(context.applicationContext, projectCleaner).also { INSTANCE = it }
            }
        }

        @androidx.annotation.VisibleForTesting
        fun resetInstanceForTesting() {
            INSTANCE = null
        }
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var authClientForTesting: AuthClient? = null
    private var ioDispatcherForTesting: CoroutineDispatcher? = null

    @androidx.annotation.VisibleForTesting
    fun setAuthClient(client: AuthClient) {
        authClientForTesting = client
    }

    @androidx.annotation.VisibleForTesting
    fun setIoDispatcher(dispatcher: CoroutineDispatcher) {
        ioDispatcherForTesting = dispatcher
    }


    // Reactive state for the *Active* Project
    private val _authState = MutableStateFlow(AuthState.INITIAL)
    val authState: Flow<AuthState> = _authState.asStateFlow()

    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: Flow<User?> = _currentUser.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: Flow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: Flow<String?> = _errorMessage.asStateFlow()

    private val _isSoftExpiry = MutableStateFlow(false)
    val isSoftExpiry: Flow<Boolean> = _isSoftExpiry.asStateFlow()

    // Current active project context
    private var activeProjectId: String? = null
    
    // 6 Hours in Milliseconds
    private val GRACE_PERIOD_MS = 6L * 60 * 60 * 1000

    init {
        // Restore last active project or default state
        activeProjectId = prefs.getString(KEY_ACTIVE_PROJECT_ID, null)
        refreshState()
    }

    /**
     * Sets the active project context.
     * Use this when the app switches projects or detects the current project.
     */
    fun setActiveProject(projectId: String?) {
        if (activeProjectId != projectId) {
            activeProjectId = projectId
            prefs.edit().putString(KEY_ACTIVE_PROJECT_ID, projectId).apply()
            // Reset soft expiry on project switch
            _isSoftExpiry.value = false
            refreshState()
        }
    }

    /**
     * Refresh in-memory flows based on the Active Project's persisted state.
     */
    private fun refreshState() {
        if (activeProjectId == null) {
            _authState.value = AuthState.LOGGED_OUT // Or INITIAL
            _currentUser.value = null
            _isSoftExpiry.value = false
            return
        }
        val pid = activeProjectId!!
        val token = getPersistedToken(pid)
        val user = getPersistedUser(pid)
        val expiresAt = getPersistedExpiresAt(pid)

        if (token != null && user != null) {
            val expiryTime = parseExpiryTime(expiresAt)
            val currentTime = System.currentTimeMillis()
            val hardDeadline = expiryTime + GRACE_PERIOD_MS
            
            if (currentTime > hardDeadline) {
                 // HARD LOGOUT: Exceeded 6-hour grace
                 println("DEBUG_AUTH: Hard deadline exceeded. Logging out.")
                 // We need to launch logout
                 scope.launch { logoutProject(pid) }
                 return
            }
            
            if (currentTime <= expiryTime) {
                // VALID
                _authState.value = AuthState.LOGGED_IN
                _currentUser.value = user
                _isSoftExpiry.value = false
            } else {
                // GRACE PERIOD (Expired but within 6h)
                println("DEBUG_AUTH: In Grace Period. Token expired $expiresAt")
                
                // Optimistically allow login
                _authState.value = AuthState.LOGGED_IN
                _currentUser.value = user
                
                // Background Reachability Check
                 scope.launch {
                    val apiUrl = getApiUrlForProject(pid)
                    var serverReachable = false
                    
                    if (apiUrl != null) {
                         try {
                            val client = getAuthClient(apiUrl)
                            serverReachable = client.checkReachability()
                        } catch (e: Exception) {
                            serverReachable = false
                        }
                    }
                    
                    if (serverReachable) {
                        println("DEBUG_AUTH: Server reachable in grace period. Setting Soft Expiry.")
                        // SOFT EXPIRY: Prompt user, but do not force logout yet
                        _isSoftExpiry.value = true
                    } else {
                         println("DEBUG_AUTH: Server unreachable. Maintaining Offline Grace.")
                        // OFFLINE GRACE: Keep logged in, silent
                        _isSoftExpiry.value = false
                    }
                }
            }
        } else {
            _authState.value = AuthState.LOGGED_OUT
            _currentUser.value = null
            _isSoftExpiry.value = false
        }
    }

    private fun parseExpiryTime(expiresAt: String?): Long {
         if (expiresAt == null) return 0L
         return try {
              org.aiims.odk.auth.utils.ApiDateFormat.parse(expiresAt)?.time ?: 0L
         } catch (e: Exception) {
             0L
         }
    }

    /**
     * Login for a specific project.
     */
    suspend fun login(projectId: String, username: String, password: String, apiUrl: String): AuthResult {
        return try {
            _isLoading.value = true
            _errorMessage.value = null

            // Use authentication client
            val client = getAuthClient(apiUrl)
            val result = client.login(projectId, username, password)

            when (result) {
                is AuthResult.Success -> {
                    // Check if user changed -> Clear PIN
                    checkAndClearPinIfUserChanged(projectId, result.user)

                    // Persist for this project
                    persistSession(projectId, result.user, result.token, result.expiresAt, apiUrl)
                    
                    // Reset soft expiry
                    _isSoftExpiry.value = false
                    
                    // If this matches the active project, update state immediately
                    if (activeProjectId == projectId) {
                        refreshState()
                    }
                    result
                }
                is AuthResult.Error -> {
                    _errorMessage.value = result.message
                    result
                }
                else -> result
            }
        } catch (e: Exception) {
            _errorMessage.value = e.message
            AuthResult.Error(e.message ?: "Login failed")
        } finally {
            _isLoading.value = false
        }
    }

    private fun checkAndClearPinIfUserChanged(projectId: String, newUser: User) {
        val oldUser = getPersistedUser(projectId)
        if (oldUser != null && oldUser.id != newUser.id) {
             android.util.Log.d("AiimsAuthManager", "User changed from ${oldUser.id} to ${newUser.id}. Clearing PIN.")
             org.aiims.odk.auth.utils.PinManager.getInstance(context).clearPin()
        }
    }

    /**
     * Logout for the Active Project.
     */
    suspend fun logout() {
        val pid = activeProjectId ?: return
        logoutProject(pid)
        _isSoftExpiry.value = false
    }

    /**
     * Logout logic for a specific project.
     */
    private suspend fun logoutProject(projectId: String) {
        val token = getPersistedToken(projectId)
        val user = getPersistedUser(projectId)
        val apiUrl = getApiUrlForProject(projectId) // We might need to store API URL per project too

        // Revoke if possible
        if (token != null && user != null && !apiUrl.isNullOrBlank()) {
             try {
                 val client = getAuthClient(apiUrl)
                 client.revokeSession(projectId, user.id, token)
             } catch (e: Exception) {
                 // Best effort
             }
        }

        // Clear persistence
        clearSession(projectId)

        // Clear ODK forms and instances for this project (Isolation)
        kotlinx.coroutines.withContext(ioDispatcherForTesting ?: kotlinx.coroutines.Dispatchers.IO) {
            android.util.Log.d("AiimsAuth", "Attempting to clear project data for: $projectId")
            try {
                projectCleaner.clearProjectData(projectId)
                android.util.Log.d("AiimsAuth", "Finished clearing project data for: $projectId")
            } catch (e: Exception) {
                android.util.Log.e("AiimsAuth", "Failed to clear project data", e)
            }
        }
        
        // Clear local PIN
        org.aiims.odk.auth.utils.PinManager.getInstance(context).clearPin()

        if (activeProjectId == projectId) {
            refreshState()
        }
    }

    private fun persistSession(projectId: String, user: User, token: String, expiresAt: String, apiUrl: String) {
        prefs.edit().apply {
            putString(keyToken(projectId), token)
            putString(keyExpiresAt(projectId), expiresAt)
            putString(keyApiUrl(projectId), apiUrl)
            
            val userJson = JSONObject().apply {
                put("id", user.id)
                put("username", user.username)
                put("projectId", user.projectId)
                put("expiresAt", user.expiresAt)
                // Legacy fields
                put("name", user.name)
                put("role", user.role)
            }.toString()
            putString(keyUser(projectId), userJson)
            
            apply()
        }
    }

    private fun clearSession(projectId: String) {
        prefs.edit().apply {
            remove(keyToken(projectId))
            remove(keyUser(projectId))
            remove(keyExpiresAt(projectId))
            remove(keyApiUrl(projectId))
            apply()
        }
    }

    // --- Helpers for Persistence keys ---
    private fun keyToken(pid: String) = "auth_token_$pid"
    private fun keyUser(pid: String) = "user_data_$pid"
    private fun keyExpiresAt(pid: String) = "expires_at_$pid"
    private fun keyApiUrl(pid: String) = "api_url_$pid"

    // --- Retrieval ---
    private fun getPersistedToken(pid: String): String? = prefs.getString(keyToken(pid), null)
    private fun getPersistedExpiresAt(pid: String): String? = prefs.getString(keyExpiresAt(pid), null)
    private fun getApiUrlForProject(pid: String): String? = prefs.getString(keyApiUrl(pid), null)

    private fun getPersistedUser(pid: String): User? {
        val jsonStr = prefs.getString(keyUser(pid), null) ?: return null
        return try {
            val json = JSONObject(jsonStr)
            User(
                id = json.getString("id"),
                username = json.getString("username"),
                projectId = json.getString("projectId"),
                expiresAt = if (json.has("expiresAt")) json.getString("expiresAt") else null,
                name = json.optString("name", json.getString("username")),
                role = json.optString("role", "App User")
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun isTokenExpired(expiresAt: String?): Boolean {
        if (expiresAt == null) return true
        return try {
             val expiryTime = org.aiims.odk.auth.utils.ApiDateFormat.parse(expiresAt)?.time ?: 0L
             System.currentTimeMillis() >= expiryTime
        } catch (e: Exception) {
            true 
        }
    }
    
    // --- Legacy / Compatibility ---
    fun getCurrentAuthState(): AuthState = _authState.value
    fun getIsSoftExpiry(): Boolean = _isSoftExpiry.value
    
    fun updateAuthState(state: AuthState) {
        _authState.value = state
    }

    fun logoutDueToFailedPin() {
        scope.launch {
            logout()
        }
    }

    fun snoozeSoftExpiry() {
        // User cancelled re-auth. Snooze check? 
        // For now, just keep the flag true? No, if we keep flag true, AppLock will loop.
        // We probably want to suppress the flag until next refresh?
        // Actually, if user hits BACK, they go to main menu. onActivityStarted triggers.
        // Strategies:
        // 1. Set flag false temporarily?
        // 2. Add 'snoozed' state?
        // Let's just set _isSoftExpiry = false for now until next refresh triggers it?
        // But refresh triggers on every resume? No, refreshState is internal.
        // We need a way to say "User knows, ignored it".
        // Let's leave value as TRUE, but AppLock needs to know if it JUST launched it.
        // Better: Use a "Snooze" method that sets it to false. The check runs in background anyway.
        // Re-enabling logic: When does it turn back on? 
        // Only if refreshState is called again (e.g. app restart or manual refresh).
        _isSoftExpiry.value = false 
    }

    /**
     * Get the token for the currently active project.
     * Used for UI display in Settings.
     */
    fun getActiveProjectToken(): String? {
        val pid = activeProjectId ?: return null
        return getPersistedToken(pid)
    }

    private fun getAuthClient(apiUrl: String): AuthClient {
        return authClientForTesting ?: RealAuthClient.getInstance(context, apiUrl)
    }
}

enum class AuthState {
    INITIAL,
    LOGGED_IN,
    LOGGED_OUT,
    // REQUIRES_PIN - Removed, now Local only
    ERROR
}
