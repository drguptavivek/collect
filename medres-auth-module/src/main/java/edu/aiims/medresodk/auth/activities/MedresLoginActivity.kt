package edu.aiims.medresodk.auth.activities

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
import edu.aiims.medresodk.auth.api.AuthResult
import edu.aiims.medresodk.auth.databinding.ActivityMedresLoginBinding
import edu.aiims.medresodk.auth.injection.MedresAuthDependencyComponentProvider
import edu.aiims.medresodk.auth.managers.MedresAuthManager
import edu.aiims.medresodk.auth.utils.MedresProjectUtils
import edu.aiims.medresodk.auth.utils.PinManager
import edu.aiims.medresodk.auth.utils.TokenRevocationManager
import edu.aiims.medresodk.auth.analytics.MedresAppAnalytics
import javax.inject.Inject

/**
 * MEDRES Login Activity (Central Backend Version)
 * Automatically detects the current ODK Project and authenticates against it.
 */
class MedresLoginActivity : MedresBaseActivity() {

    @Inject
    lateinit var pinManager: PinManager

    private lateinit var binding: ActivityMedresLoginBinding

    // Active Project Context
    private var currentSystemProjectId: String? = null // ODK's internal UUID
    private var centralProjectId: String? = null // Central's integer ID
    private var serverUrl: String? = null
    
    // Re-authentication mode flag (used for token refresh flow)
    private var isReauthMode = false

    override fun injectDependencies() {
        (application as MedresAuthDependencyComponentProvider).medresAuthDependencyComponent.inject(this)
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
        binding = ActivityMedresLoginBinding.inflate(layoutInflater)
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
            TokenRevocationManager.processPending(this@MedresLoginActivity)
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
            MedresAppAnalytics.logReauthPromptShown()
        }
        
        // Handle legacy soft expiry mode
        if (intent.getBooleanExtra("is_reauth", false)) {
            binding.appTitle.text = getString(edu.aiims.medresodk.auth.R.string.medres_session_expired_title)
            binding.statusText.text = getString(edu.aiims.medresodk.auth.R.string.medres_session_expired_message)
            binding.offlineButton.visibility = View.VISIBLE
        }

        // Observe Auth State
        lifecycleScope.launch {
            authManager.authState.collect { state ->
                when (state) {
                    edu.aiims.medresodk.auth.managers.AuthState.LOGGED_IN -> {
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
            val authPrefs = getSharedPreferences(edu.aiims.medresodk.auth.utils.MedresConstants.MEDRES_PREFS_NAME, Context.MODE_PRIVATE)

            // 1. Get Staged Auth Details (Last Scanned)
            val stagedUrl = authPrefs.getString(edu.aiims.medresodk.auth.utils.MedresConstants.KEY_AUTH_URL, null)
            val stagedPid = authPrefs.getString(edu.aiims.medresodk.auth.utils.MedresConstants.KEY_AUTH_PROJECT_ID, null)

            // 2. Get Current Active ODK Project
            val metaPrefs = getSharedPreferences("meta", Context.MODE_PRIVATE)
            currentSystemProjectId = metaPrefs.getString("current_project_id", null)
            
            // Derive Central ID from Current ODK Project (if exists)
            var activeCentralPid: String? = null
            var activeServerUrl: String? = null
            
            if (!currentSystemProjectId.isNullOrBlank()) {
                 val projectPrefs = getSharedPreferences("general_prefs$currentSystemProjectId", Context.MODE_PRIVATE)
                 activeServerUrl = projectPrefs.getString("server_url", null)
                 activeCentralPid = MedresProjectUtils.getProjectIdFromUrl(activeServerUrl)
            }

            // 3. Determine Target Project (Prioritize Staged if Different)
            var targetPid: String? = activeCentralPid
            var targetUrl: String? = activeServerUrl
            var isStagedOverride = false

            if (!stagedPid.isNullOrBlank() && !stagedUrl.isNullOrBlank()) {
                 // Check if Staged (Scanned) matches Active
                 // Override if PID mismatch OR URL mismatch (e.g. switching from Draft URL to Main URL for same Project ID)
                 if (activeCentralPid == null || activeCentralPid != stagedPid || activeServerUrl != stagedUrl) {
                     targetPid = stagedPid
                     targetUrl = stagedUrl
                     isStagedOverride = true
                     android.util.Log.i("MedresLogin", "Staged Auth matches ($stagedPid) but differs from Active. Switching UI context.")
                 }
            }

            // 4. Apply Target Configuration
            if (targetPid != null) {
                // Check if Draft/Demo
                val checkUrlForDraft = targetUrl ?: ""
                
                // We need to use authManager.isDraftProject but it relies on ODK Project settings.
                // If this is a STAGED OVERRIDE, the ODK project might not exist or be active yet.
                // So we check the URL string directly first.
                // USER REQUIREMENT: Must contain BOTH /draft and /test/
                val checkUrlIsDraft = checkUrlForDraft.contains("/draft") && checkUrlForDraft.contains("/test/")
                
                val isDraft = if (isStagedOverride) {
                    // TRUST THE SCANNED URL. Do not fallback to potentially stale ODK project config.
                    checkUrlIsDraft
                } else {
                    // Use scanned URL check OR fallback to manager (for existing sessions)
                    checkUrlIsDraft || authManager.isDraftProject(targetPid)
                }

                if (isDraft) {
                     // DRAFT / DEMO MODE UI
                     // ... same logic as before ...
                     android.util.Log.i("MedresLogin", "Draft Project detected ($targetPid). Entering Demo Mode.")
                     if (isStagedOverride) {
                         // Apply Staged to Auth Manager Context temporarily
                         authManager.setActiveProject(targetPid)
                         serverUrl = targetUrl
                         centralProjectId = targetPid
                     } else {
                         // Existing Demo Project
                         serverUrl = activeServerUrl
                         centralProjectId = activeCentralPid ?: targetPid
                     }

                    binding.statusText.text = "Demo Mode: Draft Project Active"
                    binding.statusText.visibility = View.VISIBLE
                    binding.statusText.setTextColor(ContextCompat.getColor(this, edu.aiims.medresodk.auth.R.color.medres_warning))
                    
                    binding.usernameLayout.visibility = View.GONE
                    binding.passwordLayout.visibility = View.GONE
                    binding.loginButton.text = "Enter Demo Mode"
                    binding.loginButton.visibility = View.VISIBLE
                    binding.loginButton.isEnabled = true
                    binding.loginButton.setOnClickListener { navigateToMain() }
                    
                    enableLoginUi(true)
                    return
                }

                // STANDARD PROJECT UI
                centralProjectId = targetPid
                serverUrl = targetUrl
                
                authManager.setActiveProject(centralProjectId!!)
                if (currentSystemProjectId != null && !isStagedOverride) {
                    authManager.setProjectMapping(centralProjectId!!, currentSystemProjectId!!)
                }

                // Format URL for display (Prioritize MEDRES Prefs -> Staged -> ODK)
                val displayUrlString = if (isStagedOverride) targetUrl else {
                    authPrefs.getString(edu.aiims.medresodk.auth.utils.MedresConstants.KEY_AUTH_URL, null) 
                    ?: authManager.getActiveProjectApiUrl() 
                    ?: targetUrl?.substringBefore("/v1")
                }
                
                val displayUrl = MedresProjectUtils.formatUrlForDisplay(displayUrlString)
                binding.statusText.text = getString(edu.aiims.medresodk.auth.R.string.medres_project_configured, centralProjectId, displayUrl)
                binding.statusText.visibility = View.VISIBLE
                // Reset color logic - use default text color or specific success color
                binding.statusText.setTextColor(ContextCompat.getColor(this, android.R.color.black))

                // Reset Login UI standard state
                binding.usernameLayout.visibility = View.VISIBLE
                binding.passwordLayout.visibility = View.VISIBLE
                binding.loginButton.text = "Login"
                binding.loginButton.visibility = View.VISIBLE
                binding.loginButton.setOnClickListener { attemptLogin() }
                
                // If visual state was previously hidden/disabled
                enableLoginUi(true)
                
                // Update "Rescan" button
                binding.scanQrButton.setText(edu.aiims.medresodk.auth.R.string.medres_button_rescan_qr_code)

            } else {
                // NO VALID PROJECT (Active or Staged)
                 if (activeServerUrl != null && (activeServerUrl.contains("/draft") || activeServerUrl.contains("/test/"))) {
                     // Fallback detection for Drafts where PID extraction failed
                     // (This block might be redundant with the isDraft check above, but keeps safety)
                     android.util.Log.i("MedresLogin", "Draft URL detected (fallback type 2). Entering Demo Mode.")
                     binding.statusText.text = "Demo Mode: Draft Project Active"
                     binding.statusText.visibility = View.VISIBLE
                     binding.statusText.setTextColor(ContextCompat.getColor(this, edu.aiims.medresodk.auth.R.color.medres_warning))
                     binding.usernameLayout.visibility = View.GONE
                     binding.passwordLayout.visibility = View.GONE
                     binding.loginButton.text = "Enter Demo Mode"
                     binding.loginButton.visibility = View.VISIBLE
                     binding.loginButton.setOnClickListener { navigateToMain() }
                     enableLoginUi(true)
                     return
                 }

                binding.statusText.text = getString(edu.aiims.medresodk.auth.R.string.medres_invalid_project_config, activeServerUrl)
                binding.statusText.visibility = View.VISIBLE
                enableLoginUi(false)
            }
        } catch (e: Exception) {
            binding.statusText.text = getString(edu.aiims.medresodk.auth.R.string.medres_error_reading_project_settings, e.message)
            enableLoginUi(false)
        }
    }

    private fun showProjectMissingState() {
        binding.statusText.text = getString(edu.aiims.medresodk.auth.R.string.medres_no_project_configured)
        binding.statusText.visibility = View.VISIBLE
        enableLoginUi(false)
        
        // Missing -> Scan
        binding.scanQrButton.setText(edu.aiims.medresodk.auth.R.string.medres_button_scan_qr_code)
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
            val errorMsg = getString(edu.aiims.medresodk.auth.R.string.medres_error_invalid_credentials)
            Toast.makeText(this, errorMsg, Toast.LENGTH_SHORT).show()
            // Announce for screen readers
            binding.root.announceForAccessibility(getString(edu.aiims.medresodk.auth.R.string.medres_accessibility_login_error, errorMsg))
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
                    val welcomeMsg = getString(edu.aiims.medresodk.auth.R.string.medres_welcome_user, result.user.username)
                    Toast.makeText(this@MedresLoginActivity, welcomeMsg, Toast.LENGTH_SHORT).show()
                    // Announce for screen readers
                    binding.root.announceForAccessibility("Login successful. $welcomeMsg")

                    // POST-LOGIN: CREATE/UPDATE ODK PROJECT
                    try {
                        // 1. Check for Pending/Staged Auth Details
                        val authPrefs = getSharedPreferences(edu.aiims.medresodk.auth.utils.MedresConstants.MEDRES_PREFS_NAME, Context.MODE_PRIVATE)
                        val stagedAuthUrl = authPrefs.getString(edu.aiims.medresodk.auth.utils.MedresConstants.KEY_AUTH_URL, null)
                        val stagedPid = authPrefs.getString(edu.aiims.medresodk.auth.utils.MedresConstants.KEY_AUTH_PROJECT_ID, null)

                        if (stagedAuthUrl == url && stagedPid == pid) {
                            // MATCHED! This is a fresh login for a scanned QR.
                            
                            // Initialize ODK Repositories
                            val uuidGenerator = org.odk.collect.shared.strings.UUIDGenerator()
                            val gson = com.google.gson.Gson()
                            val metaPrefs = getSharedPreferences("meta", Context.MODE_PRIVATE)
                            val metaSettings = MedresSettings(metaPrefs)
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
                                if (edu.aiims.medresodk.auth.utils.MedresProjectUtils.getProjectIdFromUrl(serverUrl) == pid) {
                                    targetUuid = proj.uuid
                                    break
                                }
                            }
                            
                             if (targetUuid == null) {
                                // Create new
                                val jsonStr = authPrefs.getString(edu.aiims.medresodk.auth.utils.MedresConstants.KEY_QR_GENERAL_SETTINGS, "{}")
                                val json = org.json.JSONObject(jsonStr)
                                val projectSection = json.optJSONObject("project") ?: org.json.JSONObject()
                                val projectName = projectSection.optString("name", "MEDRES Project $pid")
                                
                                val newProject = org.odk.collect.projects.Project.New(
                                    projectName, "A", "#3e9fcc"
                                )
                                targetUuid = projectsRepo.save(newProject).uuid
                             }
                             
                             // APPLY SETTINGS (Common settings, URL is already handled by authManager.persistSession)
                             val generalJsonStr = authPrefs.getString(edu.aiims.medresodk.auth.utils.MedresConstants.KEY_QR_GENERAL_SETTINGS, "{}")
                             val generalJson = org.json.JSONObject(generalJsonStr)
                             
                             val projPrefs = getSharedPreferences("general_prefs$targetUuid", Context.MODE_PRIVATE)
                             projPrefs.edit().apply {
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
                             
                             // Update Mappings
                             authManager.setProjectMapping(pid, targetUuid!!)

                             // RE-SYNC URL: Since targetUuid might have just been created/found, 
                             // and authManager.login might have run before targetUuid was known/mapped.
                             authManager.updateCollectProjectUrl(pid, result.token)
                             
                             // Update local activity state for UI
                             serverUrl = projPrefs.getString(org.odk.collect.settings.keys.ProjectKeys.KEY_SERVER_URL, null)
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("MedresLogin", "Failed to setup ODK project after login", e)
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
                        authManager.updateAuthState(edu.aiims.medresodk.auth.managers.AuthState.LOGGED_IN_REQUIRES_PIN)
                        navigateToPinSetup(result.token, result.expiresAt)
                    }
                }
                is AuthResult.Error -> {
                    Toast.makeText(this@MedresLoginActivity, result.message, Toast.LENGTH_LONG).show()
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
        val intent = Intent(this, edu.aiims.medresodk.auth.activities.SetupPinActivity::class.java)
        intent.putExtra("authToken", token)
        intent.putExtra("expiresAt", expiresAt)
        startActivity(intent)
        finish()
    }

    private fun launchQrScanner() {
        // Launch MEDRES QR Scanner Activity
        val intent = Intent(this, edu.aiims.medresodk.auth.activities.MedresQrScannerActivity::class.java)
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
        val dialogView = layoutInflater.inflate(edu.aiims.medresodk.auth.R.layout.dialog_manual_config, null)
        
        // Get references to views
        val baseUrlInput = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(edu.aiims.medresodk.auth.R.id.baseUrlInput)
        val projectIdInput = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(edu.aiims.medresodk.auth.R.id.projectIdInput)
        val devServerSection = dialogView.findViewById<LinearLayout>(edu.aiims.medresodk.auth.R.id.devServerSection)
        val devServerInput = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(edu.aiims.medresodk.auth.R.id.devServerInput)

        // Load saved dev server IP (DEBUG only)
        val devServerPrefs = getSharedPreferences("medres_dev_prefs", Context.MODE_PRIVATE)
        val savedDevIp = devServerPrefs.getString(edu.aiims.medresodk.auth.utils.MedresConstants.KEY_DEV_SERVER_IP, null)

        // Set default values
        val currentUrl = serverUrl ?: "https://central.local"
        baseUrlInput.setText(MedresProjectUtils.formatUrlForDisplay(currentUrl))
        projectIdInput.setText(centralProjectId ?: "1")

        // DEBUG ONLY: Show dev server section
        if (edu.aiims.medresodk.auth.BuildConfig.DEBUG) {
            devServerSection.visibility = View.VISIBLE
            devServerInput.setText(savedDevIp ?: "")
        }

        try {
            com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle(getString(edu.aiims.medresodk.auth.R.string.medres_manual_configuration_title))
                .setView(dialogView)
                .setPositiveButton(getString(edu.aiims.medresodk.auth.R.string.medres_button_set)) { _, _ ->
                    var baseUrl = baseUrlInput.text.toString().trim().trimEnd('/')
                    val pid = projectIdInput.text.toString().trim()

                    // DEBUG: Override with dev server IP if set
                    if (edu.aiims.medresodk.auth.BuildConfig.DEBUG) {
                        val devIp = devServerInput.text.toString().trim()
                        if (devIp.isNotEmpty()) {
                            // Save for future use
                            devServerPrefs.edit()
                                .putString(edu.aiims.medresodk.auth.utils.MedresConstants.KEY_DEV_SERVER_IP, devIp)
                                .apply()
                            // Override base URL with dev server
                            baseUrl = if (devIp.startsWith("http")) devIp else "https://$devIp"
                            android.util.Log.d("MedresLogin", "DEBUG: Using dev server: $baseUrl")
                        } else {
                            // Clear saved dev IP
                            devServerPrefs.edit()
                                .remove(edu.aiims.medresodk.auth.utils.MedresConstants.KEY_DEV_SERVER_IP)
                                .apply()
                        }
                    }

                    if (baseUrl.isNotEmpty() && pid.isNotEmpty()) {
                        // Sanitize Base URL to ensure /v1 is present for internal use
                        val apiBaseUrl = MedresProjectUtils.formatUrlForApi(baseUrl)
                        // Construct full URL: Base + /projects/ + ID
                        val fullUrl = "$apiBaseUrl/projects/$pid"
                        manualConfigureProject(fullUrl)
                    } else {
                        Toast.makeText(this, getString(edu.aiims.medresodk.auth.R.string.medres_error_manual_config_missing), Toast.LENGTH_SHORT).show()
                    }
                }
                .setNeutralButton(getString(edu.aiims.medresodk.auth.R.string.medres_button_direct_url)) { _, _ ->
                }
                .setNegativeButton(getString(edu.aiims.medresodk.auth.R.string.medres_button_cancel), null)
                .show()
        } catch (e: Exception) {
            Toast.makeText(this, getString(edu.aiims.medresodk.auth.R.string.medres_error_reading_project_settings, e.message), Toast.LENGTH_SHORT).show()
            e.printStackTrace()
        }
    }

    private fun manualConfigureProject(url: String) {
        val centralPid = MedresProjectUtils.getProjectIdFromUrl(url)
        if (centralPid != null) {
            try {
                // Initialize Helpers
                val uuidGenerator = org.odk.collect.shared.strings.UUIDGenerator()
                val gson = com.google.gson.Gson()

                // Create Meta Settings Wrapper
                val metaPrefs = getSharedPreferences("meta", Context.MODE_PRIVATE)
                val metaSettings = MedresSettings(metaPrefs)

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
                        android.util.Log.d("MedresLogin", "Found existing project for URL $url: $targetProjectUuid")
                        break
                    }
                }

                // Create new if not found
                if (targetProjectUuid == null) {
                    val newProject = org.odk.collect.projects.Project.New(
                        "MEDRES Project $centralPid",
                        "A",
                        "#3e9fcc"
                    )
                    val saved = projectsRepo.save(newProject)
                    targetProjectUuid = saved.uuid
                    android.util.Log.d("MedresLogin", "Created fresh project for URL $url: $targetProjectUuid")
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
                val displayUrl = MedresProjectUtils.formatUrlForDisplay(url)
                currentSystemProjectId = targetProjectUuid
                serverUrl = url // Store with /v1 internally
                centralProjectId = centralPid

                authManager.setActiveProject(centralPid)
                authManager.setProjectMapping(centralPid, targetProjectUuid)

                binding.statusText.text = getString(edu.aiims.medresodk.auth.R.string.medres_project_configured, centralPid, displayUrl)
                binding.statusText.visibility = View.VISIBLE
                enableLoginUi(true)

                Toast.makeText(this, getString(edu.aiims.medresodk.auth.R.string.medres_project_saved, targetProjectUuid), Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                android.util.Log.e("MedresLogin", "Error saving project", e)
                Toast.makeText(this, getString(edu.aiims.medresodk.auth.R.string.medres_error_saving_project, e.message), Toast.LENGTH_LONG).show()

                // Fallback
                currentSystemProjectId = "MANUAL_FALLBACK"
                serverUrl = url
                centralProjectId = centralPid
                authManager.setActiveProject(centralPid)
                enableLoginUi(true)
            }
        } else {
            Toast.makeText(this, getString(edu.aiims.medresodk.auth.R.string.medres_error_invalid_url_format), Toast.LENGTH_LONG).show()
        }
    }

    // Local Settings Implementation to bridge SharedPreferences -> ODK Settings Interface
    private class MedresSettings(private val prefs: android.content.SharedPreferences) : org.odk.collect.shared.settings.Settings {
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
            binding.locationStatus.text = getString(edu.aiims.medresodk.auth.R.string.medres_location_access_granted)
            binding.locationStatus.setTextColor(ContextCompat.getColor(this, edu.aiims.medresodk.auth.R.color.medres_success))
        } else {
            binding.locationStatus.text = getString(edu.aiims.medresodk.auth.R.string.medres_location_access_required)
            binding.locationStatus.setTextColor(ContextCompat.getColor(this, edu.aiims.medresodk.auth.R.color.medres_error))
        }

        // Notification Status (Android 13+)
        var hasNotif = true
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            hasNotif = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            if (hasNotif) {
                binding.notificationStatus.text = getString(edu.aiims.medresodk.auth.R.string.medres_notifications_enabled)
                binding.notificationStatus.setTextColor(ContextCompat.getColor(this, edu.aiims.medresodk.auth.R.color.medres_success))
            } else {
                binding.notificationStatus.text = getString(edu.aiims.medresodk.auth.R.string.medres_notifications_disabled)
                binding.notificationStatus.setTextColor(ContextCompat.getColor(this, edu.aiims.medresodk.auth.R.color.medres_error))
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
