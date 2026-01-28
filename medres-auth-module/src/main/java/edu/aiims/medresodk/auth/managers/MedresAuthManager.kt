package edu.aiims.medresodk.auth.managers

import android.util.Log
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import edu.aiims.medresodk.auth.api.AuthClient
import edu.aiims.medresodk.auth.api.AuthResult
import edu.aiims.medresodk.auth.api.RealAuthClient
import edu.aiims.medresodk.auth.api.TelemetryLocation
import edu.aiims.medresodk.auth.api.TelemetryRequest
import edu.aiims.medresodk.auth.api.User
import edu.aiims.medresodk.auth.api.TelemetryEvent
import edu.aiims.medresodk.auth.storage.MedresAuthStorage
import edu.aiims.medresodk.auth.storage.MedresSecureStorage
import edu.aiims.medresodk.auth.work.TelemetryWorker
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import com.google.gson.Gson
import org.odk.collect.projects.Project
import org.odk.collect.projects.SharedPreferencesProjectsRepository
import org.odk.collect.shared.strings.UUIDGenerator
import org.odk.collect.settings.keys.MetaKeys
import edu.aiims.medresodk.auth.utils.MedresConstants
import edu.aiims.medresodk.auth.analytics.MedresAppAnalytics
/**
 * Authentication Manager for Central Backend.
 * Supports Multi-Project Isolation.
 */
@Singleton
class MedresAuthManager @Inject constructor(
    private val context: Context,
    // IMPORTANT: Injected as Lazy to break circular dependency:
    // MedresAuthManager -> ProjectCleaner -> InstancesDataService -> OpenRosaHttpInterface -> MedresAuthManager.
    // This pattern MUST NOT be disturbed when writing tests or making code changes.
    private val projectCleaner: dagger.Lazy<ProjectCleaner>,
    private val pinManager: edu.aiims.medresodk.auth.utils.PinManager,
    private val authStorage: MedresAuthStorage,
    secureStorage: MedresSecureStorage,
    private val telemetryDao: edu.aiims.medresodk.auth.storage.db.TelemetryDao,
    private val networkStateMonitor: edu.aiims.medresodk.auth.utils.MedresNetworkStateMonitor? = null // Optional for now
) {

    companion object {
        private const val PREFS_NAME = "medres_auth_prefs"

        // Global keys
        private const val KEY_ACTIVE_PROJECT_ID = "active_project_id"
        private const val KEY_LAST_DISMISSAL_TIME = "last_soft_expiry_dismissal"

        fun generateEventId(): String {
            return java.util.UUID.randomUUID().toString()
        }
    }

    // Clock validator for detecting clock manipulation
    private val clockValidator = edu.aiims.medresodk.auth.security.ClockValidator.getInstance(secureStorage)

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

    @androidx.annotation.VisibleForTesting
    internal fun getProjectsRepository(): SharedPreferencesProjectsRepository {
        val metaPrefs = context.getSharedPreferences("meta", Context.MODE_PRIVATE)
        val metaSettings = object : org.odk.collect.shared.settings.Settings {
            override fun save(key: String, value: Any?) {
                when (value) {
                    is String -> metaPrefs.edit().putString(key, value).apply()
                    is Boolean -> metaPrefs.edit().putBoolean(key, value).apply()
                    is Long -> metaPrefs.edit().putLong(key, value).apply()
                    is Int -> metaPrefs.edit().putInt(key, value).apply()
                    is Float -> metaPrefs.edit().putFloat(key, value).apply()
                    is Set<*> -> metaPrefs.edit().putStringSet(key, value as Set<String>).apply()
                }
            }
            override fun getString(key: String): String? = metaPrefs.getString(key, null)
            override fun getBoolean(key: String): Boolean = metaPrefs.getBoolean(key, false)
            override fun getLong(key: String): Long = metaPrefs.getLong(key, 0L)
            override fun getInt(key: String): Int = metaPrefs.getInt(key, 0)
            override fun getFloat(key: String): Float = metaPrefs.getFloat(key, 0f)
            override fun getStringSet(key: String): Set<String>? = metaPrefs.getStringSet(key, null)
            override fun getAll(): Map<String, *> = metaPrefs.all
            override fun contains(key: String): Boolean = metaPrefs.contains(key)
            override fun remove(key: String) { metaPrefs.edit().remove(key).apply() }
            override fun clear() { metaPrefs.edit().clear().apply() }
            override fun setDefaultForAllSettingsWithoutValues() {}
            override fun saveAll(prefs: Map<String, Any?>) {}
            override fun reset(key: String) {}
            override fun registerOnSettingChangeListener(listener: org.odk.collect.shared.settings.Settings.OnSettingChangeListener) {}
            override fun unregisterOnSettingChangeListener(listener: org.odk.collect.shared.settings.Settings.OnSettingChangeListener) {}
        }
        return SharedPreferencesProjectsRepository(
            UUIDGenerator(),
            Gson(),
            metaSettings,
            MetaKeys.KEY_PROJECTS
        )
    }

    // Reactive state for the *Active* Project
    // Re-authentication Coordination
    private val reauthMutex = Mutex()
    private var reauthDeferred: CompletableDeferred<Boolean>? = null
    private val _isReauthenticating = MutableStateFlow(false)
    val isReauthenticating: StateFlow<Boolean> = _isReauthenticating.asStateFlow()

    private val _authState = MutableStateFlow(AuthState.INITIAL)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private var reachabilityJob: Job? = null

    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private var lastNetworkCheckTime = 0L

    init {
        // Observe network state changes to trigger re-checks
        networkStateMonitor?.let { monitor ->
            scope.launch {
                monitor.isNetworkAvailable.collect { available ->
                    if (available) {
                        val currentTime = System.currentTimeMillis()
                        if (currentTime - lastNetworkCheckTime > 30000L) { // 30s cooldown
                            lastNetworkCheckTime = currentTime
                            
                            // Only trigger if we are in grace period or expired
                            val expiry = _tokenExpiryTime.value
                            if (expiry > 0 && currentTime > expiry) {
                                println("DEBUG_AUTH: Network restored during grace. Triggering refreshState.")
                                refreshState()
                            }
                        }
                    }
                }
            }
        }
    }

    private val _isSoftExpiry = MutableStateFlow(prefs.getBoolean(MedresConstants.KEY_IS_SOFT_EXPIRY, false))
    val isSoftExpiry: StateFlow<Boolean> = _isSoftExpiry.asStateFlow()

    private fun updateSoftExpiry(value: Boolean) {
        _isSoftExpiry.value = value
        prefs.edit().putBoolean(MedresConstants.KEY_IS_SOFT_EXPIRY, value).apply()
    }

    private val _isExpiringSoon = MutableStateFlow(false)
    val isExpiringSoon: StateFlow<Boolean> = _isExpiringSoon.asStateFlow()

    // Time remaining until token expiry (for UI countdown/tier display)
    private val _timeRemainingMs = MutableStateFlow(Long.MAX_VALUE)
    val timeRemainingMs: StateFlow<Long> = _timeRemainingMs.asStateFlow()

    private val _tokenExpiryTime = MutableStateFlow(0L)
    val tokenExpiryTime: StateFlow<Long> = _tokenExpiryTime.asStateFlow()

    private val _hardDeadlineTime = MutableStateFlow(0L)
    val hardDeadlineTime: StateFlow<Long> = _hardDeadlineTime.asStateFlow()

    // Validated current time from ClockValidator (for UI display)
    private val _validatedCurrentTime = MutableStateFlow(0L)
    val validatedCurrentTime: StateFlow<Long> = _validatedCurrentTime.asStateFlow()

    // Server/device time difference in milliseconds (for warnings)
    private val _serverTimeDifferenceMs = MutableStateFlow(0L)
    val serverTimeDifferenceMs: Flow<Long> = _serverTimeDifferenceMs.asStateFlow()

    // Current active project context
    private var activeProjectId: String? = null

    init {
        // Initialize Analytics/Logging
        MedresAppAnalytics.init(context)

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
            updateSoftExpiry(false)
        }
        refreshState()
    }

    /**
     * Refresh in-memory flows based on the Active Project's persisted state.
     */
    private fun refreshState() {
        try {
            if (activeProjectId == null) {
                _authState.value = AuthState.LOGGED_OUT // Or INITIAL
                _currentUser.value = null
                updateSoftExpiry(false)
                return
            }
            val pid = activeProjectId!!
            // Check if this is the active project in secure storage
            val secureProjectId = authStorage.projectId

            // Token from encrypted storage
            val token = if (secureProjectId == pid) {
                authStorage.deviceToken.takeIf { it.isNotEmpty() }
            } else {
                null
            }
            val user = getPersistedUser(pid)
            val expiresAt = authStorage.tokenExpiry?.let { expiryMs ->
                edu.aiims.medresodk.auth.utils.ApiDateFormat.format(java.util.Date(expiryMs))
            }

            if (token != null && user != null) {
                val expiryTime = authStorage.tokenExpiry ?: 0L
                _tokenExpiryTime.value = expiryTime

                // Get validated time from ClockValidator
                val currentTime = updateTimeState()

                val hardDeadline = expiryTime + MedresConstants.GRACE_PERIOD_MS
                _hardDeadlineTime.value = hardDeadline

                // Calculate time remaining and check if expiring soon (any tier crossed)
                val timeRemaining = expiryTime - currentTime
                _timeRemainingMs.value = timeRemaining
                _isExpiringSoon.value = MedresConstants.EXPIRY_REMINDER_TIERS_MS.any { tier -> timeRemaining <= tier }

                if (currentTime > hardDeadline) {
                    // HARD LOGOUT: Exceeded 6-hour grace
                    println("DEBUG_AUTH: Hard deadline exceeded. Logging out.")
                    MedresAppAnalytics.logHardLogout("grace_period_exceeded")
                    // We need to launch logout
                    scope.launch(ioDispatcherForTesting ?: Dispatchers.Main) { logoutProject(pid) }
                    return
                }

                if (currentTime <= expiryTime) {
                    // VALID
                    _authState.value = AuthState.LOGGED_IN
                    _currentUser.value = user
                    updateSoftExpiry(false)

                    // Ensure Telemetry Worker is scheduled
                    startPeriodicTelemetry()
                    // Cancel any active grace notifications
                    cancelGracePeriodNotifications()
                } else {
                    // GRACE PERIOD (Expired but within 6h)
                    println("DEBUG_AUTH: In Grace Period. Token expired $expiresAt")
                    MedresAppAnalytics.logGracePeriodStarted()
                    
                    // Schedule grace notifications if not already scheduled
                    scheduleGracePeriodNotifications(expiryTime)

                    // Optimistically allow login
                    _authState.value = AuthState.LOGGED_IN
                    _currentUser.value = user

                    // Background Reachability Check
                    reachabilityJob?.cancel()
                    reachabilityJob = scope.launch(ioDispatcherForTesting ?: Dispatchers.Main) {
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
                            // RACE CONDITION CHECK (Scenario 63):
                            // Ensure the user is still logged in and the project hasn't changed.
                            if (activeProjectId != pid || _authState.value != AuthState.LOGGED_IN) {
                                println("DEBUG_AUTH: Race condition detected. Ignoring reachability result.")
                                return@launch
                            }

                            println("DEBUG_AUTH: Server reachable in grace period. Checking dismiss status.")
                            
                            // Check for snooze (15 minutes = 900000 ms)
                            val lastDismissal = prefs.getLong(KEY_LAST_DISMISSAL_TIME, 0L)
                            val isSnoozed = System.currentTimeMillis() - lastDismissal < 900000L

                            if (!isSnoozed && !clockValidator.isManipulationDetected()) {
                                println("DEBUG_AUTH: Not snoozed. Setting Soft Expiry.")
                                updateSoftExpiry(true)
                            } else {
                                println("DEBUG_AUTH: Dismissal active or clock manipulation. Suppressing Soft Expiry.")
                                updateSoftExpiry(false)
                            }
                        } else {
                            println("DEBUG_AUTH: Server unreachable. Maintaining Offline Grace.")
                            // OFFLINE GRACE: Keep logged in, silent
                            updateSoftExpiry(false)
                        }
                    }
                }
            } else {
                // Token missing or User missing.
                // CHECK FOR DEMO/DRAFT MODE BEFORE LOGGING OUT
                if (isDraftProject(pid)) {
                    android.util.Log.i("MedresAuthManager", "Draft Project detected ($pid). Entering DEMO_MODE.")
                    _authState.value = AuthState.DEMO_MODE
                    _currentUser.value = User(
                         id = "demo_user",
                         username = "Draft Tester",
                         projectId = pid
                    )
                    updateSoftExpiry(false)
                    _isExpiringSoon.value = false
                    _tokenExpiryTime.value = 0L
                    
                    // In Demo Mode, we assume the server is reachable via the Draft URL
                    // But we don't have a standard token to check reachability effectively
                    // So we treat it as "Always Online" or "Best Effort"
                } else {
                    _authState.value = AuthState.LOGGED_OUT
                    _currentUser.value = null
                    updateSoftExpiry(false)
                    _isExpiringSoon.value = false
                    _tokenExpiryTime.value = 0L
                    _validatedCurrentTime.value = System.currentTimeMillis()
                    _serverTimeDifferenceMs.value = 0L
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("MedresAuthManager", "Critical: Auth storage corruption detected during refreshState", e)
            // Fallback to safe state
            _authState.value = AuthState.LOGGED_OUT
            _currentUser.value = null
            updateSoftExpiry(false)
            _isExpiringSoon.value = false
            _tokenExpiryTime.value = 0L

            // Clear potentially corrupted data for safety?
            // Optional: run a cleanup task. For now, fail safe to LOGGED_OUT is priority.
            if (activeProjectId != null) {
                 try {
                     clearSession(activeProjectId!!)
                 } catch (cleanupEx: Exception) {
                     android.util.Log.e("MedresAuthManager", "Failed to clear corrupted session", cleanupEx)
                 }
            }
        }
    }

    /**
     * Checks if the given project is a Draft/Demo project.
     * Logic: Inspects ODK Project settings for 'server_url' containing '/draft'.
     */
    fun isDraftProject(projectId: String): Boolean {
        return try {
            val repository = getProjectsRepository()
            val allProjects = repository.getAll()
            
            for (project in allProjects) {
                // Check 1: Project Name contains "[Draft]"
                if (project.name.contains("[Draft]", ignoreCase = true)) {
                    android.util.Log.i("MedresAuthManager", "Draft detected by name: ${project.name}")
                    return true
                }

                // Check 2: URL analysis
                val projPrefs = context.getSharedPreferences("general_prefs${project.uuid}", Context.MODE_PRIVATE)
                val url = projPrefs.getString("server_url", "") ?: ""
                
                // Detection Logic:
                // USER REQUIREMENT: Must contain BOTH "/draft" and "/test/"
                // URL structure: .../v1/test/<TOKEN>/projects/<PID>/forms/<FORM_ID>/draft
                
                if (url.contains("/projects/$projectId") && (url.contains("/draft") && url.contains("/test/"))) {
                     android.util.Log.i("MedresAuthManager", "Draft detected by URL: $url")
                    return true
                }
            }
            false
        } catch (e: Exception) {
            android.util.Log.e("MedresAuthManager", "Error checking for draft project", e)
            false
        }
    }
    
    /**
     * Updates time-related flows (validatedCurrentTime, serverTimeDifferenceMs) 
     * based on ClockValidator state. Returns the validated current time.
     */
    private fun updateTimeState(): Long {
        val timeResult = clockValidator.getCurrentTime()
        val currentTime = when (timeResult) {
            is edu.aiims.medresodk.auth.security.ClockValidator.TimeResult.Valid -> {
                // Clear manipulation flag if time is now valid
                if (clockValidator.isManipulationDetected()) {
                    clockValidator.resetManipulationFlag()
                }
                // Calculate server/device difference for UI warning
                // Positive = device is behind server, Negative = device is ahead of server
                val deviceTime = System.currentTimeMillis()
                _serverTimeDifferenceMs.value = deviceTime - timeResult.time
                timeResult.time
            }
            is edu.aiims.medresodk.auth.security.ClockValidator.TimeResult.ManipulationDetected -> {
                // Clock manipulation detected - allow grace but prevent re-auth
                _errorMessage.value = "Clock manipulation detected: ${timeResult.reason}. Please correct your device time to re-authenticate."
                updateSoftExpiry(false) // Don't show re-auth prompt
                MedresAppAnalytics.logClockManipulationDetected()
                // Calculate server/device difference (expected time is what we should use)
                val deviceTime = System.currentTimeMillis()
                _serverTimeDifferenceMs.value = deviceTime - timeResult.expectedTime
                // Use the expected time for grace period calculation
                timeResult.expectedTime
            }
        }
        
        // Update validated current time flow for UI
        _validatedCurrentTime.value = currentTime
        return currentTime
    }

    private fun parseExpiryTime(expiresAt: String?): Long {
        if (expiresAt == null) return 0L
        return try {
            edu.aiims.medresodk.auth.utils.ApiDateFormat.parse(expiresAt)?.time ?: 0L
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
            val deviceId = getDeviceId()
            val comments = getMetadataComments()
            val result = client.login(projectId, username, password, deviceId, comments)

            when (result) {
                is AuthResult.Success -> {
                    // Check if user changed -> Clear PIN
                    checkAndClearPinIfUserChanged(projectId, result.user)

                    // Persist for this project
                    persistSession(projectId, result.user, result.token, result.expiresAt, apiUrl)

                    // Validate and sync clock with server time from HTTP response Date header
                    // This is the ACTUAL current server time, not calculated from expiry
                    if (result.serverTime != null) {
                        clockValidator.syncWithServerTime(
                            serverTime = result.serverTime,
                            localTime = System.currentTimeMillis(),
                            forceSync = true  // Always trust server time on login
                        )
                        Log.d("MedresAuthManager", "Synced clock with server time: ${result.serverTime}")
                    } else {
                        Log.w("MedresAuthManager", "No server time in login response - clock not validated")
                    }

                    // Reset soft expiry and clock manipulation flag
                    updateSoftExpiry(false)
                    clockValidator.resetManipulationFlag()

                    // If this matches the active project, update state immediately
                    if (activeProjectId == projectId) {
                        refreshState()
                    }

                    // Fetch and store project name
                    try {
                        val projectInfo = client.fetchProject(projectId, result.token)
                        if (projectInfo != null) {
                            // Store project name in shared preferences for this project
                            prefs.edit().putString("project_name_$projectId", projectInfo.name).apply()
                            Log.d("MedresAuthManager", "Stored project name: ${projectInfo.name} for project $projectId")
                            
                            // SYNC TO ODK SETTINGS
                            updateCollectProjectName(projectId, projectInfo.name)
                        }
                    } catch (e: Exception) {
                        Log.e("MedresAuthManager", "Failed to fetch project name: ${e.message}")
                        // Non-critical, continue with login
                    }

                    // Trigger Telemetry
                    submitTelemetry(null)

                    // Start Background Worker
                    startPeriodicTelemetry()
                    cancelGracePeriodNotifications()

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
            android.util.Log.d("MedresAuthManager", "User changed from ${oldUser.id} to ${newUser.id}. Clearing PIN.")
            pinManager.clearPin()
        }
    }

    /**
     * Logout for the Active Project.
     */
    suspend fun logout() {
        val pid = activeProjectId ?: return
        logoutProject(pid)
        updateSoftExpiry(false)
    }

    /**
     * Logout logic for a specific project.
     */
    private suspend fun logoutProject(projectId: String) {
        reachabilityJob?.cancel()
        val token = getPersistedToken(projectId)
        val user = getPersistedUser(projectId)
        val apiUrl = getApiUrlForProject(projectId) // We might need to store API URL per project too

        // Revoke if possible
        if (token != null && user != null && !apiUrl.isNullOrBlank()) {
            try {
                val client = getAuthClient(apiUrl)
                val deviceId = getDeviceId()
                client.revokeSession(projectId, user.id, token, deviceId)
            } catch (e: Exception) {
                // Best effort
            }
        }

        // Trigger Telemetry before clearing session (allows using the valid token)
        try {
            // Note: We are using the token which is about to be cleared.
            // submitTelemetry is async (launch), so we need to capture the values.
            // Actually, submitTelemetry retrieves values from persistence/mem.
            // If we clear session immediately, it might fail.
            // Let's pass the token explicitly? No, submitTelemetry reads from persistence.
            // We should call it before clearSession.
            submitTelemetry(null)
        } catch (e: Exception) {
            // Ignore
        }

        // Clear persistence
        val cleared = clearSession(projectId)
        if (!cleared) {
            Log.e("MedresAuth", "CRITICAL: Logout incomplete for project $projectId due to storage failure.")
            MedresAppAnalytics.logStorageError("logout_failure")
        }

        // OPTION B: Do NOT clear project data on logout
        // Forms, instances, and cache persist across user sessions for shared device scenarios.
        // This allows User B to see forms/drafts from User A when logging into the same project.
        // See docs/vg-user-behaviour.md for rationale.
        android.util.Log.d("MedresAuth", "Logout: Preserving project data for: $projectId (Option B - shared device)")

        // Final fallback: even if disk failure occurred, we must clear memory state
        updateSoftExpiry(false)
        _authState.value = AuthState.LOGGED_OUT

        // Clear local PIN
        pinManager.clearPin()

        // Clear clock validation data
        clockValidator.clear()

        if (activeProjectId == projectId) {
            refreshState()
        }

        // Stop telemetry and grace workers
        stopPeriodicTelemetry()
        cancelGracePeriodNotifications()
    }

    private suspend fun persistSession(projectId: String, user: User, token: String, expiresAt: String, apiUrl: String) {
        kotlinx.coroutines.withContext(ioDispatcherForTesting ?: Dispatchers.IO) {
            // Store sensitive data (token, expiry) in encrypted storage via MedresAuthStorage
            authStorage.saveAuthSession(
                token = token,
                expiresAt = expiresAt,
                user = user,
                apiUrl = apiUrl
            )

            // Store user data (non-sensitive) in plain SharedPreferences for project context
            prefs.edit().apply {
                putString(keyUser(projectId), JSONObject().apply {
                    put("id", user.id)
                    put("username", user.username)
                    put("projectId", user.projectId)
                    put("expiresAt", user.expiresAt)
                }.toString())
                putString(keyApiUrl(projectId), apiUrl)
                apply()
            }

            // SYNC TO ODK SETTINGS: Ensure server_url is tokenized
            updateCollectProjectUrl(projectId, token)
        }
    }

    private fun clearSession(projectId: String): Boolean {
        var success = false
        var attempts = 0
        val maxRetries = 2

        while (!success && attempts <= maxRetries) {
            try {
                if (attempts > 0) {
                    Log.w("MedresAuth", "Retrying session clearance for project $projectId (attempt ${attempts + 1})")
                }

                // Clear sensitive data from encrypted storage
                val authCleared = authStorage.clearAuthData()

                // Clear project-specific non-sensitive data from SharedPreferences
                val prefsCleared = prefs.edit().apply {
                    remove(keyUser(projectId))
                    remove(keyApiUrl(projectId))
                    // Also clear dismissal time on explicit logout/clear to reset behavior
                    remove(KEY_LAST_DISMISSAL_TIME)
                    remove(MedresConstants.KEY_IS_SOFT_EXPIRY)
                }.commit()

                success = authCleared && prefsCleared
            } catch (e: Exception) {
                Log.e("MedresAuth", "Exception during session clearance: ${e.message}", e)
                success = false
            }

            if (!success) {
                attempts++
                if (attempts <= maxRetries) {
                    // Small delay before retry
                    Thread.sleep(100)
                }
            }
        }

        if (!success) {
            // Last resort: try to clear SharedPreferences directly if all else fails
            try {
                Log.e("MedresAuth", "Session clearance failed consistently. Attempting emergency direct clear.")
                prefs.edit().clear().commit()
            } catch (e: Exception) {
                Log.e("MedresAuth", "Emergency direct clear failed: ${e.message}")
            }
        }

        return success
    }

    // --- Helpers for Persistence keys ---
    fun setProjectMapping(centralPid: String, systemUuid: String) {
        prefs.edit().putString("central_to_odk_$centralPid", systemUuid).apply()
        Log.d("MedresAuthManager", "Mapped Central ID $centralPid to ODK UUID $systemUuid")
    }

    fun updateCollectProjectName(centralPid: String, newName: String) {
        var systemUuid = prefs.getString("central_to_odk_$centralPid", null)
        
        try {
            val repository = getProjectsRepository()
            
            // Backfill: If mapping is missing, look it up by URL
            if (systemUuid == null) {
                Log.d("MedresAuthManager", "Mapping missing for Central ID $centralPid. Searching by URL...")
                val allProjects = repository.getAll()
                for (project in allProjects) {
                    val projPrefs = context.getSharedPreferences("general_prefs${project.uuid}", Context.MODE_PRIVATE)
                    val url = projPrefs.getString("server_url", "") ?: ""
                    
                    // Match standard: .../v1/projects/{pid}
                    // Match tokenized: .../v1/key/{token}/projects/{pid}
                    
                    // Simple check: Does it end with /projects/{pid} (taking potential trailing slash into account)
                    val cleanUrl = url.trimEnd('/')
                    if (cleanUrl.endsWith("/projects/$centralPid")) {
                        systemUuid = project.uuid
                        setProjectMapping(centralPid, systemUuid)
                        Log.d("MedresAuthManager", "Backfilled mapping: Central $centralPid -> ODK $systemUuid")
                        break
                    }
                }
            }

            if (systemUuid == null) {
                Log.w("MedresAuthManager", "Could not find ODK project for Central ID $centralPid")
                return
            }

            if (newName.isNotEmpty()) {
                val existingProject = repository.get(systemUuid)
                if (existingProject != null && existingProject.name != newName) {
                    val updatedProject = existingProject.copy(name = newName)
                    repository.save(updatedProject)
                    Log.d("MedresAuthManager", "Updated ODK project $systemUuid name to: $newName")
                }
            }
        } catch (e: Exception) {
            Log.e("MedresAuthManager", "Error updating ODK project name", e)
        }
    }

    /**
     * Updates the ODK Project's server_url with the tokenized version.
     * Format: <BaseURL>/v1/key/<TOKEN>/projects/<PID>
     */
    fun updateCollectProjectUrl(centralPid: String, token: String) {
        var systemUuid = prefs.getString("central_to_odk_$centralPid", null)
        
        try {
            if (systemUuid == null) {
                // Try backfilling mapping if missing
                updateCollectProjectName(centralPid, "") 
                systemUuid = prefs.getString("central_to_odk_$centralPid", null)
            }

            if (systemUuid == null) {
                Log.w("MedresAuthManager", "Could not find ODK project for Central ID $centralPid to update URL")
                return
            }

            val apiUrl = getApiUrlForProject(centralPid)
            if (apiUrl != null) {
                val apiBase = edu.aiims.medresodk.auth.utils.MedresProjectUtils.formatUrlForApi(apiUrl)
                // Ensure apiBase is just the /v1 part
                val cleanBase = if (apiBase!!.contains("/projects/")) {
                    apiBase.substringBefore("/projects/")
                } else {
                    apiBase
                }
                
                val tokenizedUrl = "$cleanBase/key/$token/projects/$centralPid"
                
                val projPrefs = context.getSharedPreferences("general_prefs$systemUuid", Context.MODE_PRIVATE)
                val currentUrl = projPrefs.getString("server_url", null)
                
                if (currentUrl != tokenizedUrl) {
                    projPrefs.edit().putString("server_url", tokenizedUrl).apply()
                    Log.i("MedresAuthManager", "[MedresAuth] Updated ODK project $systemUuid server_url to tokenized: $tokenizedUrl")
                }
            }
        } catch (e: Exception) {
            Log.e("MedresAuthManager", "Error updating ODK project URL", e)
        }
    }

    /**
     * Fetches the latest project details from the server and updates the local name.
     * Returns true if successful, false otherwise.
     */
    suspend fun fetchAndUpdateProjectDetails(context: Context): Boolean {
        return kotlinx.coroutines.withContext(ioDispatcherForTesting ?: Dispatchers.IO) {
            try {
                val pid = activeProjectId ?: return@withContext false
                val token = getPersistedToken(pid) ?: return@withContext false
                val apiUrl = getApiUrlForProject(pid) ?: return@withContext false
                
                val client = edu.aiims.medresodk.auth.api.RealAuthClient.getInstance(context, apiUrl)
                val projectInfo = client.fetchProject(pid, token)

                if (projectInfo != null) {
                    updateCollectProjectName(pid, projectInfo.name)
                    // Also update internal prefs
                    prefs.edit().putString("project_name_$pid", projectInfo.name).apply()
                    Log.i("MedresAuthManager", "Refreshed project name from server: ${projectInfo.name}")
                    return@withContext true
                }
                false
            } catch (e: Exception) {
                Log.e("MedresAuthManager", "Error fetching project details: ${e.message}")
                false
            }
        }
    }

    private fun keyToken(pid: String) = "auth_token_$pid"
    private fun keyUser(pid: String) = "user_data_$pid"
    private fun keyExpiresAt(pid: String) = "expires_at_$pid"
    private fun keyApiUrl(pid: String) = "api_url_$pid"

    // --- Retrieval ---
    private fun getPersistedToken(pid: String): String? {
        // Only return token from secure storage if it matches the active project
        return if (authStorage.projectId == pid) {
            authStorage.deviceToken.takeIf { it.isNotEmpty() }
        } else {
            null
        }
    }

    private fun getPersistedExpiresAt(pid: String): String? {
        // Only return expiry from secure storage if it matches the active project
        return if (authStorage.projectId == pid) {
            authStorage.tokenExpiry?.let { expiryMs ->
                edu.aiims.medresodk.auth.utils.ApiDateFormat.format(java.util.Date(expiryMs))
            }
        } else {
            null
        }
    }

    private fun getApiUrlForProject(pid: String): String? = prefs.getString(keyApiUrl(pid), null)

    fun getActiveProjectApiUrl(): String? {
        val pid = activeProjectId ?: return null
        return getApiUrlForProject(pid)
    }

    private fun getPersistedUser(pid: String): User? {
        val jsonStr = prefs.getString(keyUser(pid), null) ?: return null
        return try {
            val json = JSONObject(jsonStr)
            User(
                id = json.getString("id"),
                username = json.getString("username"),
                projectId = json.getString("projectId"),
                expiresAt = if (json.has("expiresAt")) json.getString("expiresAt") else null
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun isTokenExpired(expiresAt: String?): Boolean {
        if (expiresAt == null) return true
        return try {
            val expiryTime = edu.aiims.medresodk.auth.utils.ApiDateFormat.parse(expiresAt)?.time ?: 0L
            System.currentTimeMillis() >= expiryTime
        } catch (e: Exception) {
            true
        }
    }

    // --- Legacy / Compatibility ---
    fun getCurrentAuthState(): AuthState = _authState.value
    fun getIsSoftExpiry(): Boolean = _isSoftExpiry.value
    
    fun getActiveProjectTokenExpiry(): String? {
        val pid = activeProjectId ?: return null
        return getPersistedExpiresAt(pid)
    }

    fun updateAuthState(state: AuthState) {
        _authState.value = state
    }

    @androidx.annotation.VisibleForTesting
    fun setIsSoftExpiry(value: Boolean) {
        updateSoftExpiry(value)
    }

    /**
     * Called by the global AuthInterceptor when it detects a 401.
     * Pauses the request until re-authentication is complete.
     */
    suspend fun awaitReauthentication(): Boolean {
        // If re-auth is already in progress, just wait for it
        reauthMutex.withLock {
            if (_isReauthenticating.value) {
                return reauthDeferred?.await() ?: false
            }
        }
        
        // Trigger re-authentication
        return onAuthenticationRequired()
    }

    /**
     * Triggers the re-authentication UI (MedresLoginActivity in re-auth mode).
     * Returns true if re-authentication succeeded.
     */
    suspend fun onAuthenticationRequired(): Boolean {
        reauthMutex.withLock {
            if (_isReauthenticating.value) {
                return reauthDeferred?.await() ?: false
            }

            _isReauthenticating.value = true
            val deferred = CompletableDeferred<Boolean>()
            reauthDeferred = deferred

            android.util.Log.i("MedresAuthManager", "Authentication required. Launching re-auth UI.")
            
            // Launch Login Activity in Re-Auth mode
            launchReauthUi()
            
            return try {
                deferred.await()
            } finally {
                _isReauthenticating.value = false
                reauthDeferred = null
            }
        }
    }

    /**
     * Called by MedresLoginActivity or PinEntryActivity when re-authentication is finished.
     */
    fun onReauthenticationComplete(success: Boolean) {
        android.util.Log.i("MedresAuthManager", "Re-authentication complete. Success: $success")
        reauthDeferred?.complete(success)
    }

    private fun launchReauthUi() {
        val application = context as Application
        val intent = Intent()
        intent.setClassName(application.packageName, "edu.aiims.medresodk.auth.activities.MedresLoginActivity")
        intent.putExtra("EXTRA_IS_REAUTH", true)
        
        // Try to get current username
        val user = _currentUser.value
        user?.let { u ->
            intent.putExtra("EXTRA_REAUTH_USERNAME", u.username)
        }
        
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        application.startActivity(intent)
    }

    fun logoutDueToFailedPin() {
        scope.launch(ioDispatcherForTesting ?: Dispatchers.Main) {
            logout()
        }
    }

    fun snoozeSoftExpiry() {
        // Persist snooze time to prevent immediate re-prompt on restart
        prefs.edit().putLong(KEY_LAST_DISMISSAL_TIME, System.currentTimeMillis()).apply()
        updateSoftExpiry(false)
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
        return authClientForTesting ?: RealAuthClient.getInstance(context, apiUrl, this)
    }

    private fun getDeviceId(): String {
        return context.getSharedPreferences("meta", Context.MODE_PRIVATE)
            .getString("metadata_installid", "unknown_device") ?: "unknown_device"
    }

    private val gson = Gson()

    /**
     * Submit telemetry data to the backend immediately.
     */
    suspend fun submitTelemetry(location: android.location.Location?, event: TelemetryEvent? = null) {
        val pid = activeProjectId ?: return
        val token = getPersistedToken(pid)
        val apiUrl = getApiUrlForProject(pid)

        // Queue if no token or URL (shouldn't happen if logged in, but just in case)
        if (token == null || apiUrl == null) {
             // Can't queue without project context effectively if not logged in? 
             // Actually we have pid. But if we don't have API URL yet...
             // Let's assume valid session for now.
             return
        }

        scope.launch(ioDispatcherForTesting ?: Dispatchers.IO) {
            val deviceId = getDeviceId()

            // Format location
            val telemetryLocation = if (location != null) {
                TelemetryLocation(
                    latitude = location.latitude,
                    longitude = location.longitude,
                    altitude = if (location.hasAltitude()) location.altitude else null,
                    accuracy = if (location.hasAccuracy()) location.accuracy else null,
                    speed = if (location.hasSpeed()) location.speed else null,
                    bearing = if (location.hasBearing()) location.bearing else null,
                    provider = location.provider
                )
            } else {
                null
            }

            val request = TelemetryRequest(
                deviceId = deviceId,
                collectVersion = "Collect/Unknown",
                deviceDateTime = edu.aiims.medresodk.auth.utils.ApiDateFormat.format(java.util.Date()),
                location = telemetryLocation,
                events = if (event != null) listOf(event) else null
            )

            try {
            // Try to send immediately
            val client = getAuthClient(apiUrl)
            val result = client.submitTelemetry(pid, token, request)

            when (result) {
                is edu.aiims.medresodk.auth.api.TelemetryResult.Success -> {
                    val body = result.response
                    // Check for server-side invalidation
                    if (body.status == "invalidated") {
                         android.util.Log.w("MedresAuthManager", "Telemetry response indicates session invalidated for pid: $pid.")
                         // Note: Logout is handled by dedicated auth processes, not here.
                    }

                    // Sync clock with server time from telemetry response
                    if (body.serverTime != null) {
                        val serverTime = parseIsoDateTime(body.serverTime)
                        if (serverTime != null) {
                            clockValidator.syncWithServerTime(
                                serverTime = serverTime,
                                localTime = System.currentTimeMillis(),
                                forceSync = false
                            )
                            updateTimeState()
                        }
                    }
                    
                    // Process any queued items
                    processQueuedTelemetry(pid, token, apiUrl)
                }
                is edu.aiims.medresodk.auth.api.TelemetryResult.AuthError -> {
                    android.util.Log.w("MedresAuthManager", "Telemetry submission failed with 401. Queuing for pid: $pid.")
                    queueTelemetry(request, pid)
                }
                is edu.aiims.medresodk.auth.api.TelemetryResult.NetworkError,
                is edu.aiims.medresodk.auth.api.TelemetryResult.ApiError -> {
                    android.util.Log.e("MedresAuthManager", "Telemetry submission failed. Queuing for pid: $pid.")
                    queueTelemetry(request, pid)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("MedresAuthManager", "Unexpected error in telemetry submission. Queuing.", e)
            queueTelemetry(request, pid)
        }
        }
    }

    private suspend fun queueTelemetry(request: TelemetryRequest, projectId: String) {
        try {
            val json = gson.toJson(request)
            val entity = edu.aiims.medresodk.auth.storage.db.TelemetryEntity(
                data = json,
                projectId = projectId
            )
            telemetryDao.insert(entity)
            android.util.Log.d("MedresAuthManager", "Queued telemetry event offline for project $projectId.")
        } catch (e: Exception) {
            android.util.Log.e("MedresAuthManager", "Failed to queue telemetry", e)
        }
    }

    suspend fun processQueuedTelemetry(projectId: String, token: String, apiUrl: String) {
        // This should probably be called by Worker or after successful submit
        try {
            val pending = telemetryDao.getAll()
            if (pending.isEmpty()) return

            val client = getAuthClient(apiUrl)
            
            pending.forEach { entity ->
                // Only process for this project
                if (entity.projectId == projectId) {
                    try {
                        val request = gson.fromJson(entity.data, TelemetryRequest::class.java)
                        val result = client.submitTelemetry(projectId, token, request)
            
                        when (result) {
                            is edu.aiims.medresodk.auth.api.TelemetryResult.Success -> {
                                // Success, delete from DB
                                telemetryDao.delete(entity.id)
                                android.util.Log.d("MedresAuthManager", "Successfully submitted queued telemetry id: ${entity.id}")
                            }
                            is edu.aiims.medresodk.auth.api.TelemetryResult.AuthError -> {
                                // 401 error, server did not record it. Keep in queue.
                                // Stop processing this project for now as it will likely continue to fail 401
                                android.util.Log.w("MedresAuthManager", "Queued telemetry submission failed with 401. Keeping in queue for pid: $projectId")
                                return@forEach
                            }
                            is edu.aiims.medresodk.auth.api.TelemetryResult.NetworkError,
                            is edu.aiims.medresodk.auth.api.TelemetryResult.ApiError -> {
                                // Error, keep in queue for retry. 
                                // Increment attempt count maybe?
                                return@forEach
                            }
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("MedresAuthManager", "Failed to process queued item ${entity.id}", e)
                        // Keep in DB, retry later
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("MedresAuthManager", "Error processing queued telemetry", e)
        }
    }

    suspend fun flushOfflineQueue() {
        try {
            val pending = telemetryDao.getAll()
            if (pending.isEmpty()) return

            val projects = pending.map { it.projectId }.distinct()
            
            projects.forEach { pid ->
                val token = getPersistedToken(pid)
                val apiUrl = getApiUrlForProject(pid)
                
                if (token != null && apiUrl != null) {
                   processQueuedTelemetry(pid, token, apiUrl)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("MedresAuthManager", "Failed to flush offline queue", e)
        }
    }

    private fun showSessionExpiredNotification(message: String) {
        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            
            // Re-create channel to ensure it exists (reusing app channel ID)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                val channelId = "collect_notification_channel"
                val channelName = "ODK Collect"
                val channel = android.app.NotificationChannel(channelId, channelName, android.app.NotificationManager.IMPORTANCE_DEFAULT)
                notificationManager.createNotificationChannel(channel)
            }

            val intent = android.content.Intent(context, edu.aiims.medresodk.auth.activities.MedresLoginActivity::class.java)
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
            val pendingIntent = android.app.PendingIntent.getActivity(
                context, 
                0, 
                intent, 
                android.app.PendingIntent.FLAG_ONE_SHOT or android.app.PendingIntent.FLAG_IMMUTABLE
            )

            val notification = androidx.core.app.NotificationCompat.Builder(context, "collect_notification_channel")
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle("Session Expired")
                .setContentText(message)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
                .build()
                
            notificationManager.notify(1337, notification)
        } catch (e: Exception) {
            android.util.Log.e("MedresAuthManager", "Error constructing notification", e)
        }
    }

    private fun startPeriodicTelemetry() {
        try {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val workRequest = PeriodicWorkRequestBuilder<TelemetryWorker>(
                MedresConstants.TELEMETRY_SYNC_INTERVAL_MINUTES,
                TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "MedresTelemetryWorker",
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )
        } catch (e: Exception) {
            android.util.Log.e("MedresAuthManager", "Failed to schedule telemetry worker", e)
        }
    }

    private fun stopPeriodicTelemetry() {
        try {
            WorkManager.getInstance(context).cancelUniqueWork("MedresTelemetryWorker")
        } catch (e: Exception) {
            // Ignore
        }
    }

    fun showGracePeriodNotification(remainingText: String) {
        showSessionExpiredNotification("Final auto-logout in $remainingText. Re-authenticate now to avoid data loss.")
    }

    private fun scheduleGracePeriodNotifications(expiryTime: Long) {
        try {
            val workManager = WorkManager.getInstance(context)
            val currentTime = System.currentTimeMillis()

            MedresConstants.GRACE_NOTIFICATION_MARKS_MS.forEach { remainingMs ->
                val triggerTime = expiryTime + MedresConstants.GRACE_PERIOD_MS - remainingMs
                val delayMs = triggerTime - currentTime

                if (delayMs > 0) {
                    val hours = remainingMs / MedresConstants.HOUR_IN_MS
                    val minutes = (remainingMs / MedresConstants.MINUTE_IN_MS) % 60
                    val remainingText = if (hours > 0) "${hours}h" else "${minutes}m"

                    val data = androidx.work.Data.Builder()
                        .putString(edu.aiims.medresodk.auth.work.GracePeriodNotificationWorker.KEY_REMAINING_TIME, remainingText)
                        .build()

                    val workRequest = androidx.work.OneTimeWorkRequestBuilder<edu.aiims.medresodk.auth.work.GracePeriodNotificationWorker>()
                        .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                        .setInputData(data)
                        .addTag("GraceNotification")
                        .build()

                    workManager.enqueueUniqueWork(
                        "GraceNotification_$remainingMs",
                        androidx.work.ExistingWorkPolicy.KEEP,
                        workRequest
                    )
                    Log.d("MedresAuthManager", "Scheduled grace notification for $remainingText remaining (in ${delayMs/1000}s)")
                }
            }
        } catch (e: Exception) {
            Log.e("MedresAuthManager", "Failed to schedule grace notifications", e)
        }
    }

    private fun cancelGracePeriodNotifications() {
        try {
            WorkManager.getInstance(context).cancelAllWorkByTag("GraceNotification")
            Log.d("MedresAuthManager", "Cancelled all grace notifications")
        } catch (e: Exception) {
            // Ignore
        }
    }

    private fun getMetadataComments(): String {
        return try {
            JSONObject().apply {
                put("manufacturer", android.os.Build.MANUFACTURER)
                put("model", android.os.Build.MODEL)
                put("os_version", android.os.Build.VERSION.RELEASE)
                put("location", "unknown")
            }.toString()
        } catch (e: Exception) {
            "{}"
        }
    }

    /**
     * Parse ISO 8601 datetime string to milliseconds since epoch.
     * Handles formats: "2025-12-21T10:02:00.000Z" or similar
     */
    private fun parseIsoDateTime(isoString: String): Long? {
        return try {
            val format = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US)
            format.timeZone = java.util.TimeZone.getTimeZone("GMT")
            format.parse(isoString)?.time
        } catch (e: Exception) {
            try {
                val format = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", java.util.Locale.US)
                format.timeZone = java.util.TimeZone.getTimeZone("GMT")
                format.parse(isoString)?.time
            } catch (e2: Exception) {
                Log.e("MedresAuthManager", "Failed to parse ISO datetime: $isoString")
                null
            }
        }
    }
}

enum class AuthState {
    INITIAL,
    LOGGED_IN,
    LOGGED_IN_REQUIRES_PIN,  // Logged in but must set up PIN first (Forgot PIN, Logout, First-time login)
    DEMO_MODE,               // Specialized mode for Draft/Testing (No strict auth)
    LOGGED_OUT,
    ERROR
}
