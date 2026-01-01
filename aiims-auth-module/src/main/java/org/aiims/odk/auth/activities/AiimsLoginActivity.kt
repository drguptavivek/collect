package org.aiims.odk.auth.activities

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.aiims.odk.auth.api.AuthResult
import org.aiims.odk.auth.databinding.ActivityAiimsLoginBinding
import org.aiims.odk.auth.injection.AiimsAuthDependencyComponentProvider
import org.aiims.odk.auth.managers.AiimsAuthManager
import org.aiims.odk.auth.utils.AiimsProjectUtils
import org.aiims.odk.auth.utils.PinManager
import org.aiims.odk.auth.utils.TokenRevocationManager
import org.aiims.odk.auth.analytics.AiimsAppAnalytics
import javax.inject.Inject

/**
 * AIIMS Login Activity (Central Backend Version)
 * Automatically detects the current ODK Project and authenticates against it.
 */
class AiimsLoginActivity : AiimsBaseActivity() {

    @Inject
    lateinit var pinManager: PinManager

    private lateinit var binding: ActivityAiimsLoginBinding

    // Active Project Context
    private var currentSystemProjectId: String? = null // ODK's internal UUID
    private var centralProjectId: String? = null // Central's integer ID
    private var serverUrl: String? = null
    
    // Re-authentication mode flag (used for token refresh flow)
    private var isReauthMode = false

    override fun injectDependencies() {
        (application as AiimsAuthDependencyComponentProvider).aiimsAuthDependencyComponent.inject(this)
    }

    override fun onResume() {
        super.onResume()
        checkAndRequestPermissions()
        // Update UI status
        updatePermissionStatusUI()
        // Re-detect project in case user scanned QR code
        detectCurrentProject()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize ViewBinding
        binding = ActivityAiimsLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Check permissions (Location + Notification)
        checkAndRequestPermissions()

        // UI Setup
        setupListeners()

        // Apply brightness filter to logo in dark mode
        applyLogoFilter()

        // Update initial state
        updatePermissionStatusUI()

        // Process any pending revocations
        lifecycleScope.launch {
            TokenRevocationManager.processPending(this@AiimsLoginActivity)
        }

        // Detect Project
        detectCurrentProject()

        // Handle Re-Auth Mode (token refresh)
        isReauthMode = intent.getBooleanExtra("EXTRA_IS_REAUTH", false)
        if (isReauthMode) {
            val username = intent.getStringExtra("EXTRA_REAUTH_USERNAME")
            binding.appTitle.text = "Re-authenticate"
            val reauthMessage = "Please enter your password to refresh your session"
            binding.statusText.text = reauthMessage
            // Announce for screen readers
            binding.statusText.announceForAccessibility("Re-authentication required. $reauthMessage")
            
            // Pre-fill username and make it read-only
            if (username != null) {
                binding.usernameField.setText(username)
                binding.usernameField.isEnabled = false
                binding.usernameField.alpha = 0.6f
            }
            
            // Focus on password field
            binding.passwordField.requestFocus()
            AiimsAppAnalytics.logReauthPromptShown()
        }
        
        // Handle legacy soft expiry mode
        if (intent.getBooleanExtra("is_reauth", false)) {
            binding.appTitle.text = getString(org.aiims.odk.auth.R.string.aiims_session_expired_title)
            binding.statusText.text = getString(org.aiims.odk.auth.R.string.aiims_session_expired_message)
            binding.offlineButton.visibility = View.VISIBLE
        }

        // Observe Auth State
        lifecycleScope.launch {
            authManager.authState.collect { state ->
                when (state) {
                    org.aiims.odk.auth.managers.AuthState.LOGGED_IN -> {
                        // Skip auto-navigation if in re-auth mode - user must enter password first
                        if (!isReauthMode) {
                            navigateToMain()
                        }
                    }
                    else -> {
                        // Stay on login screen
                    }
                }
            }
        }
    }

    private fun setupListeners() {
        binding.loginButton.setOnClickListener { attemptLogin() }
        binding.scanQrButton.setOnClickListener { launchQrScanner() }
        binding.settingsButton.setOnClickListener { showManualUrlDialog() }
        binding.grantPermissionsButton.setOnClickListener { checkAndRequestPermissions(true) }
        binding.offlineButton.setOnClickListener {
            authManager.snoozeSoftExpiry()
            finish()
        }
    }

    private fun detectCurrentProject() {
        try {
            val authPrefs = getSharedPreferences(org.aiims.odk.auth.utils.AiimsConstants.AIIMS_PREFS_NAME, Context.MODE_PRIVATE)

            // Read "meta" prefs to get current project ID
            val metaPrefs = getSharedPreferences("meta", Context.MODE_PRIVATE)
            currentSystemProjectId = metaPrefs.getString("current_project_id", null)

            // FALLBACK 1: If ODK project missing, check for Staged Auth Details
            if (currentSystemProjectId.isNullOrBlank()) {
                val authUrl = authPrefs.getString(org.aiims.odk.auth.utils.AiimsConstants.KEY_AUTH_URL, null)
                val authPid = authPrefs.getString(org.aiims.odk.auth.utils.AiimsConstants.KEY_AUTH_PROJECT_ID, null)

                if (!authUrl.isNullOrBlank() && !authPid.isNullOrBlank()) {
                    // Temporarily set context to allow login
                    serverUrl = authUrl
                    centralProjectId = authPid
                    authManager.setActiveProject(authPid!!)
                    
                    val displayUrl = AiimsProjectUtils.formatUrlForDisplay(authUrl)
                    binding.statusText.text = getString(org.aiims.odk.auth.R.string.aiims_project_configured, authPid, displayUrl)
                    binding.statusText.visibility = View.VISIBLE
                    
                    // Already configured (staged) -> Rescan
                    binding.scanQrButton.setText(org.aiims.odk.auth.R.string.aiims_button_rescan_qr_code)
                    
                    enableLoginUi(true)
                    return
                }
            }

            if (currentSystemProjectId.isNullOrBlank()) {
                showProjectMissingState()
                return
            }

            // Read Project Settings (ODK uses "general_prefs" + projectId)
            val projectPrefs = getSharedPreferences("general_prefs$currentSystemProjectId", Context.MODE_PRIVATE)
            val odkServerUrl = projectPrefs.getString("server_url", null)

            // Extract Central Project ID
            centralProjectId = AiimsProjectUtils.getProjectIdFromUrl(odkServerUrl)

            if (centralProjectId != null) {
                // Set Active Project in Auth Manager
                authManager.setActiveProject(centralProjectId!!)
                
                // Prioritize Auth URL from AIIMS Prefs for UI display
                val authUrl = authPrefs.getString(org.aiims.odk.auth.utils.AiimsConstants.KEY_AUTH_URL, null)
                    ?: authManager.getActiveProjectApiUrl()
                    ?: odkServerUrl?.substringBefore("/v1") // Fallback
                
                serverUrl = authUrl
                
                // Ensure mapping is established
                authManager.setProjectMapping(centralProjectId!!, currentSystemProjectId!!)
                
                val displayUrl = AiimsProjectUtils.formatUrlForDisplay(serverUrl)
                binding.statusText.text = getString(org.aiims.odk.auth.R.string.aiims_project_configured, centralProjectId, displayUrl)
                binding.statusText.visibility = View.VISIBLE
                
                // Configured -> Rescan
                binding.scanQrButton.setText(org.aiims.odk.auth.R.string.aiims_button_rescan_qr_code)
                
                enableLoginUi(true)
            } else {
                binding.statusText.text = getString(org.aiims.odk.auth.R.string.aiims_invalid_project_config, odkServerUrl)
                binding.statusText.visibility = View.VISIBLE
                enableLoginUi(false)
            }
        } catch (e: Exception) {
            binding.statusText.text = getString(org.aiims.odk.auth.R.string.aiims_error_reading_project_settings, e.message)
            enableLoginUi(false)
        }
    }

    private fun showProjectMissingState() {
        binding.statusText.text = getString(org.aiims.odk.auth.R.string.aiims_no_project_configured)
        binding.statusText.visibility = View.VISIBLE
        enableLoginUi(false)
        
        // Missing -> Scan
        binding.scanQrButton.setText(org.aiims.odk.auth.R.string.aiims_button_scan_qr_code)
        binding.scanQrButton.visibility = View.VISIBLE
        binding.loginButton.visibility = View.GONE
    }

    private fun enableLoginUi(enable: Boolean) {
        binding.usernameLayout.isEnabled = enable
        binding.passwordLayout.isEnabled = enable
        binding.loginButton.isEnabled = enable
        binding.loginButton.visibility = if (enable) View.VISIBLE else View.GONE
        
        // Scan/Rescan button is ALWAYS visible to allow correcting config
        binding.scanQrButton.visibility = View.VISIBLE
    }

    private fun attemptLogin() {
        val username = binding.usernameField.text.toString().trim()
        val password = binding.passwordField.text.toString()

        if (username.isEmpty() || password.isEmpty()) {
            val errorMsg = getString(org.aiims.odk.auth.R.string.aiims_error_invalid_credentials)
            Toast.makeText(this, errorMsg, Toast.LENGTH_SHORT).show()
            // Announce for screen readers
            binding.root.announceForAccessibility(getString(org.aiims.odk.auth.R.string.aiims_accessibility_login_error, errorMsg))
            return
        }

        val pid = centralProjectId ?: return
        val url = serverUrl ?: return

        binding.loginProgress.visibility = View.VISIBLE
        binding.loginButton.isEnabled = false

        lifecycleScope.launch {
            val result = authManager.login(pid, username, password, url)
            binding.loginProgress.visibility = View.GONE
            binding.loginButton.isEnabled = true

            when (result) {
                is AuthResult.Success -> {
                    val welcomeMsg = getString(org.aiims.odk.auth.R.string.aiims_welcome_user, result.user.username)
                    Toast.makeText(this@AiimsLoginActivity, welcomeMsg, Toast.LENGTH_SHORT).show()
                    // Announce for screen readers
                    binding.root.announceForAccessibility("Login successful. $welcomeMsg")

                    // POST-LOGIN: CREATE/UPDATE ODK PROJECT
                    try {
                        // 1. Check for Pending/Staged Auth Details
                        val authPrefs = getSharedPreferences(org.aiims.odk.auth.utils.AiimsConstants.AIIMS_PREFS_NAME, Context.MODE_PRIVATE)
                        val stagedAuthUrl = authPrefs.getString(org.aiims.odk.auth.utils.AiimsConstants.KEY_AUTH_URL, null)
                        val stagedPid = authPrefs.getString(org.aiims.odk.auth.utils.AiimsConstants.KEY_AUTH_PROJECT_ID, null)

                        if (stagedAuthUrl == url && stagedPid == pid) {
                            // MATCHED! This is a fresh login for a scanned QR.
                            
                            // Initialize ODK Repositories
                            val uuidGenerator = org.odk.collect.shared.strings.UUIDGenerator()
                            val gson = com.google.gson.Gson()
                            val metaPrefs = getSharedPreferences("meta", Context.MODE_PRIVATE)
                            val metaSettings = AiimsSettings(metaPrefs)
                            val projectsRepo = org.odk.collect.projects.SharedPreferencesProjectsRepository(
                                uuidGenerator, gson, metaSettings, org.odk.collect.settings.keys.MetaKeys.KEY_PROJECTS
                            )
                            
                            // FIND OR CREATE PROJECT (Preserve Data)
                            var targetUuid: String? = null
                            val allProjects = projectsRepo.getAll()
                            
                             // Search by PID (better than URL now since URL changes)
                            for (proj in allProjects) {
                                val projPrefs = getSharedPreferences("general_prefs${proj.uuid}", Context.MODE_PRIVATE)
                                val serverUrl = projPrefs.getString(org.odk.collect.settings.keys.ProjectKeys.KEY_SERVER_URL, "") ?: ""
                                if (AiimsProjectUtils.getProjectIdFromUrl(serverUrl) == pid) {
                                    targetUuid = proj.uuid
                                    break
                                }
                            }
                            
                             if (targetUuid == null) {
                                // Create new
                                val jsonStr = authPrefs.getString(org.aiims.odk.auth.utils.AiimsConstants.KEY_QR_GENERAL_SETTINGS, "{}")
                                val json = org.json.JSONObject(jsonStr)
                                val projectSection = json.optJSONObject("project") ?: org.json.JSONObject()
                                val projectName = projectSection.optString("name", "AIIMS Project $pid")
                                
                                val newProject = org.odk.collect.projects.Project.New(
                                    projectName, "A", "#3e9fcc"
                                )
                                targetUuid = projectsRepo.save(newProject).uuid
                             }
                             
                             // CONSTRUCT TOKENIZED URL
                             // Format: <BaseURL>/key/<TOKEN>/projects/<PID>
                             // stagedAuthUrl now includes version (e.g. .../v1)
                             val tokenizedUrl = "$stagedAuthUrl/key/${result.token}/projects/$pid"
                             
                             // APPLY SETTINGS
                             val generalJsonStr = authPrefs.getString(org.aiims.odk.auth.utils.AiimsConstants.KEY_QR_GENERAL_SETTINGS, "{}")
                             val generalJson = org.json.JSONObject(generalJsonStr)
                             
                             val projPrefs = getSharedPreferences("general_prefs$targetUuid", Context.MODE_PRIVATE)
                             projPrefs.edit().apply {
                                 putString(org.odk.collect.settings.keys.ProjectKeys.KEY_SERVER_URL, tokenizedUrl)
                                 putString(org.odk.collect.settings.keys.ProjectKeys.KEY_PROTOCOL, org.odk.collect.settings.keys.ProjectKeys.PROTOCOL_SERVER)
                                 putString(org.odk.collect.settings.keys.ProjectKeys.KEY_USERNAME, username)
                                 putString(org.odk.collect.settings.keys.ProjectKeys.KEY_PASSWORD, password)
                                 // Apply other settings from QR
                                 putString(org.odk.collect.settings.keys.ProjectKeys.KEY_FORM_UPDATE_MODE, generalJson.optString("form_update_mode", "manual"))
                                 commit() // Sync
                             }
                             
                             // Set Active
                             metaSettings.save(org.odk.collect.settings.keys.MetaKeys.CURRENT_PROJECT_ID, targetUuid)
                             currentSystemProjectId = targetUuid
                             serverUrl = tokenizedUrl
                             
                             // Update Mappings
                             authManager.setProjectMapping(pid, targetUuid!!)
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("AiimsLogin", "Failed to setup ODK project after login", e)
                        // Non-fatal? We still have a session. But ODK layer relies on it.
                    }

                    // Trigger Telemetry with Location (Manager sends one without location, we refine it here)
                    lifecycleScope.launch {
                        authManager.submitTelemetry(getLastKnownLocation())
                    }

                    // Navigate based on reauth mode and PIN state
                    if (isReauthMode) {
                        // Notify manager that re-auth succeeded
                        authManager.onReauthenticationComplete(true)

                        // Category A: Token Refresh - PIN should already be set
                        if (pinManager.isPinSet()) {
                            navigateToMain()
                        } else {
                            // Edge case: PIN was somehow cleared during token refresh
                            navigateToPinSetup(result.token, result.expiresAt)
                        }
                    } else {
                        // Category B: Fresh Login / Forgot PIN / Logout
                        // Set auth state to LOGGED_IN_REQUIRES_PIN to enforce PIN setup
                        authManager.updateAuthState(org.aiims.odk.auth.managers.AuthState.LOGGED_IN_REQUIRES_PIN)
                        navigateToPinSetup(result.token, result.expiresAt)
                    }
                }
                is AuthResult.Error -> {
                    Toast.makeText(this@AiimsLoginActivity, result.message, Toast.LENGTH_LONG).show()
                }
                else -> {}
            }
        }
    }

    private fun navigateToMain() {
        val intent = Intent()
        intent.setClassName(this.packageName, "org.odk.collect.android.mainmenu.MainMenuActivity")
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun navigateToPinSetup(token: String, expiresAt: String) {
        val intent = Intent(this, org.aiims.odk.auth.activities.SetupPinActivity::class.java)
        intent.putExtra("authToken", token)
        intent.putExtra("expiresAt", expiresAt)
        startActivity(intent)
        finish()
    }

    private fun launchQrScanner() {
        // Launch AIIMS QR Scanner Activity
        val intent = Intent(this, org.aiims.odk.auth.activities.AiimsQrScannerActivity::class.java)
        startActivity(intent)
    }

    override fun onBackPressed() {
        if (isReauthMode) {
            // Cancel re-authentication
            authManager.onReauthenticationComplete(false)
            
            // In re-auth mode, return to PIN entry (cancel the refresh attempt)
            val intent = Intent(this, PinEntryActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        } else if (intent.getBooleanExtra("is_reauth", false)) {
            // Legacy soft expiry mode - treat Back as Cancel/Snooze
            authManager.snoozeSoftExpiry()
            super.onBackPressed()
        } else {
            super.onBackPressed()
        }
    }

    private fun showManualUrlDialog() {
        // Inflate the styled dialog layout
        val dialogView = layoutInflater.inflate(org.aiims.odk.auth.R.layout.dialog_manual_config, null)
        
        // Get references to views
        val baseUrlInput = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(org.aiims.odk.auth.R.id.baseUrlInput)
        val projectIdInput = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(org.aiims.odk.auth.R.id.projectIdInput)
        val devServerSection = dialogView.findViewById<LinearLayout>(org.aiims.odk.auth.R.id.devServerSection)
        val devServerInput = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(org.aiims.odk.auth.R.id.devServerInput)

        // Load saved dev server IP (DEBUG only)
        val devServerPrefs = getSharedPreferences("aiims_dev_prefs", Context.MODE_PRIVATE)
        val savedDevIp = devServerPrefs.getString(org.aiims.odk.auth.utils.AiimsConstants.KEY_DEV_SERVER_IP, null)

        // Set default values
        val currentUrl = serverUrl ?: "https://central.local"
        baseUrlInput.setText(AiimsProjectUtils.formatUrlForDisplay(currentUrl))
        projectIdInput.setText(centralProjectId ?: "1")

        // DEBUG ONLY: Show dev server section
        if (org.aiims.odk.auth.BuildConfig.DEBUG) {
            devServerSection.visibility = View.VISIBLE
            devServerInput.setText(savedDevIp ?: "")
        }

        try {
            com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle(getString(org.aiims.odk.auth.R.string.aiims_manual_configuration_title))
                .setView(dialogView)
                .setPositiveButton(getString(org.aiims.odk.auth.R.string.aiims_button_set)) { _, _ ->
                    var baseUrl = baseUrlInput.text.toString().trim().trimEnd('/')
                    val pid = projectIdInput.text.toString().trim()

                    // DEBUG: Override with dev server IP if set
                    if (org.aiims.odk.auth.BuildConfig.DEBUG) {
                        val devIp = devServerInput.text.toString().trim()
                        if (devIp.isNotEmpty()) {
                            // Save for future use
                            devServerPrefs.edit()
                                .putString(org.aiims.odk.auth.utils.AiimsConstants.KEY_DEV_SERVER_IP, devIp)
                                .apply()
                            // Override base URL with dev server
                            baseUrl = if (devIp.startsWith("http")) devIp else "https://$devIp"
                            android.util.Log.d("AiimsLogin", "DEBUG: Using dev server: $baseUrl")
                        } else {
                            // Clear saved dev IP
                            devServerPrefs.edit()
                                .remove(org.aiims.odk.auth.utils.AiimsConstants.KEY_DEV_SERVER_IP)
                                .apply()
                        }
                    }

                    if (baseUrl.isNotEmpty() && pid.isNotEmpty()) {
                        // Sanitize Base URL to ensure /v1 is present for internal use
                        val apiBaseUrl = AiimsProjectUtils.formatUrlForApi(baseUrl)
                        // Construct full URL: Base + /projects/ + ID
                        val fullUrl = "$apiBaseUrl/projects/$pid"
                        manualConfigureProject(fullUrl)
                    } else {
                        Toast.makeText(this, getString(org.aiims.odk.auth.R.string.aiims_error_manual_config_missing), Toast.LENGTH_SHORT).show()
                    }
                }
                .setNeutralButton(getString(org.aiims.odk.auth.R.string.aiims_button_direct_url)) { _, _ ->
                }
                .setNegativeButton(getString(org.aiims.odk.auth.R.string.aiims_button_cancel), null)
                .show()
        } catch (e: Exception) {
            Toast.makeText(this, getString(org.aiims.odk.auth.R.string.aiims_error_reading_project_settings, e.message), Toast.LENGTH_SHORT).show()
            e.printStackTrace()
        }
    }

    private fun manualConfigureProject(url: String) {
        val centralPid = AiimsProjectUtils.getProjectIdFromUrl(url)
        if (centralPid != null) {
            try {
                // Initialize Helpers
                val uuidGenerator = org.odk.collect.shared.strings.UUIDGenerator()
                val gson = com.google.gson.Gson()

                // Create Meta Settings Wrapper
                val metaPrefs = getSharedPreferences("meta", Context.MODE_PRIVATE)
                val metaSettings = AiimsSettings(metaPrefs)

                // Initialize Repository
                val projectsRepo = org.odk.collect.projects.SharedPreferencesProjectsRepository(
                    uuidGenerator,
                    gson,
                    metaSettings,
                    org.odk.collect.settings.keys.MetaKeys.KEY_PROJECTS
                )

                // FIND OR CREATE PROJECT: Preserve multi-user data
                var targetProjectUuid: String? = null
                val allProjects = projectsRepo.getAll()

                for (proj in allProjects) {
                    val projPrefs = getSharedPreferences("general_prefs${proj.uuid}", Context.MODE_PRIVATE)
                    val projUrl = projPrefs.getString(org.odk.collect.settings.keys.ProjectKeys.KEY_SERVER_URL, null)
                    if (projUrl == url) {
                        targetProjectUuid = proj.uuid
                        android.util.Log.d("AiimsLogin", "Found existing project for URL $url: $targetProjectUuid")
                        break
                    }
                }

                // Create new if not found
                if (targetProjectUuid == null) {
                    val newProject = org.odk.collect.projects.Project.New(
                        "AIIMS Project $centralPid",
                        "A",
                        "#3e9fcc"
                    )
                    val saved = projectsRepo.save(newProject)
                    targetProjectUuid = saved.uuid
                    android.util.Log.d("AiimsLogin", "Created fresh project for URL $url: $targetProjectUuid")
                }

                // FORCE UPDATE SETTINGS (Existing or New)
                // Use commit() to ensure persistence immediately
                val projPrefs = getSharedPreferences("general_prefs$targetProjectUuid", Context.MODE_PRIVATE)
                projPrefs.edit()
                    .putString(org.odk.collect.settings.keys.ProjectKeys.KEY_SERVER_URL, url)
                    .putString(org.odk.collect.settings.keys.ProjectKeys.KEY_PROTOCOL, org.odk.collect.settings.keys.ProjectKeys.PROTOCOL_SERVER)
                    .commit() // Sync save

                // Set as Active Project
                metaSettings.save(org.odk.collect.settings.keys.MetaKeys.CURRENT_PROJECT_ID, targetProjectUuid)

                // Update Local State
                val displayUrl = AiimsProjectUtils.formatUrlForDisplay(url)
                currentSystemProjectId = targetProjectUuid
                serverUrl = url // Store with /v1 internally
                centralProjectId = centralPid

                authManager.setActiveProject(centralPid)
                authManager.setProjectMapping(centralPid, targetProjectUuid)

                binding.statusText.text = getString(org.aiims.odk.auth.R.string.aiims_project_configured, centralPid, displayUrl)
                binding.statusText.visibility = View.VISIBLE
                enableLoginUi(true)

                Toast.makeText(this, getString(org.aiims.odk.auth.R.string.aiims_project_saved, targetProjectUuid), Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                android.util.Log.e("AiimsLogin", "Error saving project", e)
                Toast.makeText(this, getString(org.aiims.odk.auth.R.string.aiims_error_saving_project, e.message), Toast.LENGTH_LONG).show()

                // Fallback
                currentSystemProjectId = "MANUAL_FALLBACK"
                serverUrl = url
                centralProjectId = centralPid
                authManager.setActiveProject(centralPid)
                enableLoginUi(true)
            }
        } else {
            Toast.makeText(this, getString(org.aiims.odk.auth.R.string.aiims_error_invalid_url_format), Toast.LENGTH_LONG).show()
        }
    }

    // Local Settings Implementation to bridge SharedPreferences -> ODK Settings Interface
    private class AiimsSettings(private val prefs: android.content.SharedPreferences) : org.odk.collect.shared.settings.Settings {
        override fun save(key: String, value: Any?) {
            val editor = prefs.edit()
            when (value) {
                is String -> editor.putString(key, value)
                is Boolean -> editor.putBoolean(key, value)
                is Long -> editor.putLong(key, value)
                is Int -> editor.putInt(key, value)
                is Float -> editor.putFloat(key, value)
                is Set<*> -> editor.putStringSet(key, value as Set<String>)
                null -> editor.remove(key)
                else -> throw IllegalArgumentException("Unsupported type")
            }
            editor.apply()
        }

        override fun getString(key: String) = prefs.getString(key, null)
        override fun getBoolean(key: String) = prefs.getBoolean(key, false)
        override fun getLong(key: String) = prefs.getLong(key, 0L)
        override fun getInt(key: String) = prefs.getInt(key, 0)
        override fun getFloat(key: String) = prefs.getFloat(key, 0f)
        override fun getStringSet(key: String): Set<String>? = prefs.getStringSet(key, null)
        override fun getAll(): Map<String, *> = prefs.all
        override fun contains(key: String) = prefs.contains(key)
        override fun remove(key: String) { prefs.edit().remove(key).apply() }
        override fun clear() { prefs.edit().clear().apply() }

        // Unused stubs
        override fun setDefaultForAllSettingsWithoutValues() {}
        override fun saveAll(prefs: Map<String, Any?>) {
            prefs.forEach { save(it.key, it.value) }
        }
        override fun reset(key: String) { remove(key) }
        override fun registerOnSettingChangeListener(listener: org.odk.collect.shared.settings.Settings.OnSettingChangeListener) {}
        override fun unregisterOnSettingChangeListener(listener: org.odk.collect.shared.settings.Settings.OnSettingChangeListener) {}
    }

    private fun applyLogoFilter() {
        // Check if dark mode is enabled
        val nightModeFlags = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
        if (nightModeFlags == android.content.res.Configuration.UI_MODE_NIGHT_YES) {
            // Apply brightness filter to make logo visible in dark mode
            val colorMatrix = android.graphics.ColorMatrix().apply {
                // Increase brightness by 40%
                set(floatArrayOf(
                    1.4f, 0f, 0f, 0f, 50f,   // Red
                    0f, 1.4f, 0f, 0f, 50f,   // Green
                    0f, 0f, 1.4f, 0f, 50f,   // Blue
                    0f, 0f, 0f, 1f, 0f      // Alpha
                ))
            }
            binding.logoView.colorFilter = android.graphics.ColorMatrixColorFilter(colorMatrix)
        }
    }
    private fun updatePermissionStatusUI() {
        // Location Status
        val hasLocation = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

        if (hasLocation) {
            binding.locationStatus.text = getString(org.aiims.odk.auth.R.string.aiims_location_access_granted)
            binding.locationStatus.setTextColor(ContextCompat.getColor(this, org.aiims.odk.auth.R.color.aiims_success))
        } else {
            binding.locationStatus.text = getString(org.aiims.odk.auth.R.string.aiims_location_access_required)
            binding.locationStatus.setTextColor(ContextCompat.getColor(this, org.aiims.odk.auth.R.color.aiims_error))
        }

        // Notification Status (Android 13+)
        var hasNotif = true
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            hasNotif = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            if (hasNotif) {
                binding.notificationStatus.text = getString(org.aiims.odk.auth.R.string.aiims_notifications_enabled)
                binding.notificationStatus.setTextColor(ContextCompat.getColor(this, org.aiims.odk.auth.R.color.aiims_success))
            } else {
                binding.notificationStatus.text = getString(org.aiims.odk.auth.R.string.aiims_notifications_disabled)
                binding.notificationStatus.setTextColor(ContextCompat.getColor(this, org.aiims.odk.auth.R.color.aiims_error))
            }
        }

        // Show GRANT button if any permission is missing
        if (!hasLocation || !hasNotif) {
            binding.grantPermissionsButton.visibility = View.VISIBLE
        } else {
            binding.grantPermissionsButton.visibility = View.GONE
        }
    }
}
