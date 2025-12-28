package org.aiims.odk.auth.managers

import android.util.Log
import android.content.Context
import android.content.SharedPreferences
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
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
import org.aiims.odk.auth.api.TelemetryLocation
import org.aiims.odk.auth.api.TelemetryRequest
import org.aiims.odk.auth.api.User
import org.aiims.odk.auth.storage.AiimsAuthStorage
import org.aiims.odk.auth.storage.AiimsSecureStorage
import org.aiims.odk.auth.work.TelemetryWorker
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import com.google.gson.Gson
import org.odk.collect.projects.Project
import org.odk.collect.projects.SharedPreferencesProjectsRepository
import org.odk.collect.shared.strings.UUIDGenerator
import org.odk.collect.settings.keys.MetaKeys

/**
 * Authentication Manager for Central Backend.
 * Supports Multi-Project Isolation.
 */
@Singleton
class AiimsAuthManager @Inject constructor(
    private val context: Context,
    private val projectCleaner: ProjectCleaner,
    private val pinManager: org.aiims.odk.auth.utils.PinManager,
    private val authStorage: AiimsAuthStorage,
    secureStorage: AiimsSecureStorage
) {

    companion object {
        private const val PREFS_NAME = "aiims_auth_prefs"

        // Global keys
        private const val KEY_ACTIVE_PROJECT_ID = "active_project_id"
    }

    // Clock validator for detecting clock manipulation
    private val clockValidator = org.aiims.odk.auth.security.ClockValidator.getInstance(secureStorage)

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

    private fun getProjectsRepository(): SharedPreferencesProjectsRepository {
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

    private val _isExpiringSoon = MutableStateFlow(false)
    val isExpiringSoon: Flow<Boolean> = _isExpiringSoon.asStateFlow()

    private val _tokenExpiryTime = MutableStateFlow(0L)
    val tokenExpiryTime: Flow<Long> = _tokenExpiryTime.asStateFlow()

    // Validated current time from ClockValidator (for UI display)
    private val _validatedCurrentTime = MutableStateFlow(0L)
    val validatedCurrentTime: Flow<Long> = _validatedCurrentTime.asStateFlow()

    // Server/device time difference in milliseconds (for warnings)
    private val _serverTimeDifferenceMs = MutableStateFlow(0L)
    val serverTimeDifferenceMs: Flow<Long> = _serverTimeDifferenceMs.asStateFlow()

    // Current active project context
    private var activeProjectId: String? = null

    // 6 Hours in Milliseconds
    private val GRACE_PERIOD_MS = 6L * 60 * 60 * 1000
    // 24 Hours in Milliseconds for reminder
    private val EXPIRATION_THRESHOLD_MS = 24L * 60 * 60 * 1000

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
            org.aiims.odk.auth.utils.ApiDateFormat.format(java.util.Date(expiryMs))
        }

        if (token != null && user != null) {
            val expiryTime = authStorage.tokenExpiry ?: 0L
            _tokenExpiryTime.value = expiryTime

            // Get validated time from ClockValidator
            val timeResult = clockValidator.getCurrentTime()
            val currentTime = when (timeResult) {
                is org.aiims.odk.auth.security.ClockValidator.TimeResult.Valid -> {
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
                is org.aiims.odk.auth.security.ClockValidator.TimeResult.ManipulationDetected -> {
                    // Clock manipulation detected - allow grace but prevent re-auth
                    _errorMessage.value = "Clock manipulation detected: ${timeResult.reason}. Please correct your device time to re-authenticate."
                    _isSoftExpiry.value = false // Don't show re-auth prompt
                    // Calculate server/device difference (expected time is what we should use)
                    // Positive = device is behind server, Negative = device is ahead of server
                    val deviceTime = System.currentTimeMillis()
                    _serverTimeDifferenceMs.value = deviceTime - timeResult.expectedTime
                    // Use the expected time for grace period calculation
                    timeResult.expectedTime
                }
            }

            // Update validated current time flow for UI
            _validatedCurrentTime.value = currentTime

            val hardDeadline = expiryTime + GRACE_PERIOD_MS

            // Check if expiring soon (within 24 hours)
            _isExpiringSoon.value = currentTime + EXPIRATION_THRESHOLD_MS > expiryTime

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

                // Ensure Telemetry Worker is scheduled
                startPeriodicTelemetry()
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
                        // Check if clock manipulation detected - if so, don't show re-auth prompt
                        if (!clockValidator.isManipulationDetected()) {
                            _isSoftExpiry.value = true
                        } else {
                            _isSoftExpiry.value = false
                        }
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
            _isExpiringSoon.value = false
            _tokenExpiryTime.value = 0L
            _validatedCurrentTime.value = System.currentTimeMillis()
            _serverTimeDifferenceMs.value = 0L
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
                        Log.d("AiimsAuthManager", "Synced clock with server time: ${result.serverTime}")
                    } else {
                        Log.w("AiimsAuthManager", "No server time in login response - clock not validated")
                    }

                    // Reset soft expiry and clock manipulation flag
                    _isSoftExpiry.value = false
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
                            Log.d("AiimsAuthManager", "Stored project name: ${projectInfo.name} for project $projectId")
                            
                            // SYNC TO ODK SETTINGS
                            updateCollectProjectName(projectId, projectInfo.name)
                        }
                    } catch (e: Exception) {
                        Log.e("AiimsAuthManager", "Failed to fetch project name: ${e.message}")
                        // Non-critical, continue with login
                    }

                    // Trigger Telemetry
                    submitTelemetry(null)

                    // Start Background Worker
                    startPeriodicTelemetry()

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
            pinManager.clearPin()
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
        clearSession(projectId)

        // OPTION B: Do NOT clear project data on logout
        // Forms, instances, and cache persist across user sessions for shared device scenarios.
        // This allows User B to see forms/drafts from User A when logging into the same project.
        // See docs/vg-user-behaviour.md for rationale.
        android.util.Log.d("AiimsAuth", "Logout: Preserving project data for: $projectId (Option B - shared device)")

        // Clear local PIN
        pinManager.clearPin()

        // Clear clock validation data
        clockValidator.clear()

        if (activeProjectId == projectId) {
            refreshState()
        }

        // Stop telemetry worker
        stopPeriodicTelemetry()
    }

    private fun persistSession(projectId: String, user: User, token: String, expiresAt: String, apiUrl: String) {
        // Store sensitive data (token, expiry) in encrypted storage via AiimsAuthStorage
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
    }

    private fun clearSession(projectId: String) {
        // Clear sensitive data from encrypted storage
        authStorage.clearAuthData()

        // Clear project-specific non-sensitive data from SharedPreferences
        prefs.edit().apply {
            remove(keyUser(projectId))
            remove(keyApiUrl(projectId))
            apply()
        }
    }

    // --- Helpers for Persistence keys ---
    fun setProjectMapping(centralPid: String, systemUuid: String) {
        prefs.edit().putString("central_to_odk_$centralPid", systemUuid).apply()
        Log.d("AiimsAuthManager", "Mapped Central ID $centralPid to ODK UUID $systemUuid")
    }

    fun updateCollectProjectName(centralPid: String, newName: String) {
        var systemUuid = prefs.getString("central_to_odk_$centralPid", null)
        
        try {
            val repository = getProjectsRepository()
            
            // Backfill: If mapping is missing, look it up by URL
            if (systemUuid == null) {
                Log.d("AiimsAuthManager", "Mapping missing for Central ID $centralPid. Searching by URL...")
                val allProjects = repository.getAll()
                for (project in allProjects) {
                    val projPrefs = context.getSharedPreferences("general_prefs${project.uuid}", Context.MODE_PRIVATE)
                    val url = projPrefs.getString("server_url", "") ?: ""
                    if (url.contains("/v1/projects/$centralPid")) {
                        systemUuid = project.uuid
                        setProjectMapping(centralPid, systemUuid)
                        Log.d("AiimsAuthManager", "Backfilled mapping: Central $centralPid -> ODK $systemUuid")
                        break
                    }
                }
            }

            if (systemUuid == null) {
                Log.w("AiimsAuthManager", "Could not find ODK project for Central ID $centralPid")
                return
            }

            val existingProject = repository.get(systemUuid)
            if (existingProject != null && existingProject.name != newName) {
                val updatedProject = existingProject.copy(name = newName)
                repository.save(updatedProject)
                Log.d("AiimsAuthManager", "Updated ODK project $systemUuid name to: $newName")
            }
        } catch (e: Exception) {
            Log.e("AiimsAuthManager", "Error updating ODK project name", e)
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
                org.aiims.odk.auth.utils.ApiDateFormat.format(java.util.Date(expiryMs))
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
            val expiryTime = org.aiims.odk.auth.utils.ApiDateFormat.parse(expiresAt)?.time ?: 0L
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

    private fun getDeviceId(): String {
        return context.getSharedPreferences("meta", Context.MODE_PRIVATE)
            .getString("metadata_installid", "unknown_device") ?: "unknown_device"
    }

    /**
     * Submit telemetry data to the backend immediately.
     */
    suspend fun submitTelemetry(location: android.location.Location?) {
        val pid = activeProjectId ?: return
        val token = getPersistedToken(pid) ?: return
        val apiUrl = getApiUrlForProject(pid) ?: return

        scope.launch(ioDispatcherForTesting ?: Dispatchers.IO) {
            try {
                val client = getAuthClient(apiUrl)
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
                    TelemetryLocation(0.0, 0.0, null, null, null, null, "unknown")
                }

                val request = TelemetryRequest(
                    deviceId = deviceId,
                    collectVersion = "Collect/Unknown",
                    deviceDateTime = org.aiims.odk.auth.utils.ApiDateFormat.format(java.util.Date()),
                    location = telemetryLocation
                )

                val result = client.submitTelemetry(pid, token, request)

                // Sync clock with server time from telemetry response
                // This provides continuous clock validation (every 20 minutes)
                if (result?.serverTime != null) {
                    val serverTime = parseIsoDateTime(result.serverTime)
                    if (serverTime != null) {
                        clockValidator.syncWithServerTime(
                            serverTime = serverTime,
                            localTime = System.currentTimeMillis(),
                            forceSync = false  // Only adjust if offset is reasonable
                        )
                        Log.d("AiimsAuthManager", "Clock synced with server time from telemetry")
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("AiimsAuthManager", "Failed to submit telemetry", e)
            }
        }
    }

    private fun startPeriodicTelemetry() {
        try {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val workRequest = PeriodicWorkRequestBuilder<TelemetryWorker>(20, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "AiimsTelemetryWorker",
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )
        } catch (e: Exception) {
            android.util.Log.e("AiimsAuthManager", "Failed to schedule telemetry worker", e)
        }
    }

    private fun stopPeriodicTelemetry() {
        try {
            WorkManager.getInstance(context).cancelUniqueWork("AiimsTelemetryWorker")
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
                Log.e("AiimsAuthManager", "Failed to parse ISO datetime: $isoString")
                null
            }
        }
    }
}

enum class AuthState {
    INITIAL,
    LOGGED_IN,
    LOGGED_OUT,

    // REQUIRES_PIN - Removed, now Local only
    ERROR
}
