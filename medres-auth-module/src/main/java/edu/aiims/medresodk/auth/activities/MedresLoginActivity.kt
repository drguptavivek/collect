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
import edu.aiims.medresodk.auth.utils.MedresSettingsValidator
import edu.aiims.medresodk.auth.utils.PinManager
import edu.aiims.medresodk.auth.utils.TokenRevocationManager
import edu.aiims.medresodk.auth.analytics.MedresAppAnalytics
import java.net.URI
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
            val authPrefs = getSharedPreferences(
                edu.aiims.medresodk.auth.utils.MedresConstants.MEDRES_PREFS_NAME,
                Context.MODE_PRIVATE
            )
            val stagingStore = edu.aiims.medresodk.auth.qr.MedresQrStagingStore(authPrefs)

            // Read the typed staged context (set by MedresQrScannerActivity)
            val staged = stagingStore.read()

            when (staged) {
                is edu.aiims.medresodk.auth.qr.StagedDraftFormContext -> {
                    // --- DRAFT / DEMO path ---
                    android.util.Log.i(
                        "MedresLogin",
                        "Draft context staged — project ${staged.centralProjectId} form ${staged.formId}"
                    )
                    ensureDraftProjectConfigured(staged)

                    val label = staged.displayName?.let { "Draft Testing Mode: $it" } ?: "Draft Testing Mode: Draft Form Ready"
                    binding.statusText.text = label
                    binding.statusText.visibility = View.VISIBLE
                    binding.statusText.setTextColor(
                        ContextCompat.getColor(this, edu.aiims.medresodk.auth.R.color.medres_warning)
                    )

                    binding.usernameLayout.visibility = View.GONE
                    binding.passwordLayout.visibility = View.GONE
                    binding.loginButton.text = "Start Testing"
                    binding.loginButton.visibility = View.VISIBLE
                    binding.loginButton.isEnabled = true
                    binding.loginButton.setOnClickListener { navigateToMain() }

                    enableLoginUi(true)
                    return
                }

                is edu.aiims.medresodk.auth.qr.StagedMedresProjectContext -> {
                    // --- MEDRES PROJECT path — requires login ---
                    android.util.Log.i(
                        "MedresLogin",
                        "MEDRES project context staged — project ${staged.centralProjectId}"
                    )
                    centralProjectId = staged.centralProjectId
                    serverUrl = staged.authBaseUrl

                    authManager.setActiveProject(staged.centralProjectId)

                    val displayUrl = MedresProjectUtils.formatUrlForDisplay(staged.authBaseUrl)
                    binding.statusText.text = getString(
                        edu.aiims.medresodk.auth.R.string.medres_project_configured,
                        centralProjectId,
                        displayUrl
                    )
                    binding.statusText.visibility = View.VISIBLE
                    binding.statusText.setTextColor(
                        ContextCompat.getColor(this, android.R.color.black)
                    )

                    // Pre-fill username hint if provided
                    staged.usernameHint?.let { binding.usernameField.setText(it) }

                    binding.usernameLayout.visibility = View.VISIBLE
                    binding.passwordLayout.visibility = View.VISIBLE
                    binding.loginButton.text = "Login"
                    binding.loginButton.visibility = View.VISIBLE
                    binding.loginButton.setOnClickListener { attemptLogin() }

                    enableLoginUi(true)
                    binding.scanQrButton.setText(edu.aiims.medresodk.auth.R.string.medres_button_rescan_qr_code)
                    return
                }

                null -> {
                    // No staged context — fall back to currently active ODK project
                    detectActiveOdkProject(authPrefs)
                }
            }
        } catch (e: Exception) {
            binding.statusText.text = getString(
                edu.aiims.medresodk.auth.R.string.medres_error_reading_project_settings,
                e.message
            )
            enableLoginUi(false)
        }
    }

    /**
     * Fallback: derive project from the currently active ODK project settings.
     * Used when no QR has been scanned yet in the current session.
     */
    private fun detectActiveOdkProject(authPrefs: android.content.SharedPreferences) {
        val metaPrefs = getSharedPreferences("meta", Context.MODE_PRIVATE)
        currentSystemProjectId = metaPrefs.getString("current_project_id", null)

        var activeCentralPid: String? = null
        var activeServerUrl: String? = null

        if (!currentSystemProjectId.isNullOrBlank()) {
            val projectPrefs = getSharedPreferences(
                "general_prefs$currentSystemProjectId",
                Context.MODE_PRIVATE
            )
            activeServerUrl = projectPrefs.getString("server_url", null)
            activeCentralPid = MedresProjectUtils.getProjectIdFromUrl(activeServerUrl)
        }

        if (activeCentralPid != null && activeServerUrl != null) {
            centralProjectId = activeCentralPid
            serverUrl = activeServerUrl

            authManager.setActiveProject(activeCentralPid)
            authManager.setProjectMapping(activeCentralPid, currentSystemProjectId!!)

            val displayUrl = MedresProjectUtils.formatUrlForDisplay(activeServerUrl)
            binding.statusText.text = getString(
                edu.aiims.medresodk.auth.R.string.medres_project_configured,
                centralProjectId,
                displayUrl
            )
            binding.statusText.visibility = View.VISIBLE
            binding.statusText.setTextColor(
                ContextCompat.getColor(this, android.R.color.black)
            )

            binding.usernameLayout.visibility = View.VISIBLE
            binding.passwordLayout.visibility = View.VISIBLE
            binding.loginButton.text = "Login"
            binding.loginButton.visibility = View.VISIBLE
            binding.loginButton.setOnClickListener { attemptLogin() }

            enableLoginUi(true)
            binding.scanQrButton.setText(edu.aiims.medresodk.auth.R.string.medres_button_rescan_qr_code)
        } else {
            // No project at all
            binding.statusText.text = getString(
                edu.aiims.medresodk.auth.R.string.medres_no_project_configured
            )
            binding.statusText.visibility = View.VISIBLE
            enableLoginUi(false)
        }
    }

    private fun ensureDraftProjectConfigured(
        staged: edu.aiims.medresodk.auth.qr.StagedDraftFormContext
    ) {
        val stagedDraftIdentity = extractDraftProjectIdentity(staged.originalDraftUrl)
        val uuidGenerator = org.odk.collect.shared.strings.UUIDGenerator()
        val gson = com.google.gson.Gson()
        val metaPrefs = getSharedPreferences("meta", Context.MODE_PRIVATE)
        val metaSettings = MedresSettings(metaPrefs)
        val projectsRepo = org.odk.collect.projects.SharedPreferencesProjectsRepository(
            uuidGenerator,
            gson,
            metaSettings,
            org.odk.collect.settings.keys.MetaKeys.KEY_PROJECTS
        )

        var targetUuid: String? = null
        for (proj in projectsRepo.getAll()) {
            val projPrefs = getSharedPreferences("general_prefs${proj.uuid}", Context.MODE_PRIVATE)
            val projUrl = projPrefs.getString(org.odk.collect.settings.keys.ProjectKeys.KEY_SERVER_URL, null)
            val existingDraftIdentity = projUrl?.let { extractDraftProjectIdentity(it) }
            if (stagedDraftIdentity != null && stagedDraftIdentity == existingDraftIdentity) {
                targetUuid = proj.uuid
                val displayName = staged.displayName ?: ""
                if (displayName.isNotEmpty() && proj.name != displayName) {
                    projectsRepo.save(
                        org.odk.collect.projects.Project.Saved(
                            proj.uuid,
                            displayName,
                            proj.icon,
                            proj.color
                        )
                    )
                }
                break
            }
        }

        if (targetUuid == null) {
            val draftProjectName = staged.displayName ?: "Draft Project ${staged.centralProjectId}"
            val newProject = org.odk.collect.projects.Project.New(
                draftProjectName,
                staged.displayIcon ?: "D",
                "#f0ad4e"
            )
            targetUuid = projectsRepo.save(newProject).uuid
        }

        val projPrefs = getSharedPreferences("general_prefs$targetUuid", Context.MODE_PRIVATE)
        val generalJson = org.json.JSONObject(staged.generalSettingsJson)
        projPrefs.edit().apply {
            putString(org.odk.collect.settings.keys.ProjectKeys.KEY_SERVER_URL, staged.originalDraftUrl)
            putString(
                org.odk.collect.settings.keys.ProjectKeys.KEY_PROTOCOL,
                org.odk.collect.settings.keys.ProjectKeys.PROTOCOL_SERVER
            )

            val keysIterator = generalJson.keys()
            while (keysIterator.hasNext()) {
                val key = keysIterator.next()
                if (!MedresSettingsValidator.isValidGeneralKey(key)) {
                    android.util.Log.w("MedresLogin", "Ignoring invalid draft general key from QR: $key")
                    continue
                }

                if (key == org.odk.collect.settings.keys.ProjectKeys.KEY_SERVER_URL ||
                    key == org.odk.collect.settings.keys.ProjectKeys.KEY_PROTOCOL ||
                    key == org.odk.collect.settings.keys.ProjectKeys.KEY_USERNAME ||
                    key == org.odk.collect.settings.keys.ProjectKeys.KEY_PASSWORD) {
                    continue
                }

                val value = generalJson.get(key)
                when (value) {
                    is Boolean -> putBoolean(key, value)
                    is String -> putString(key, value)
                    is Int -> putInt(key, value)
                    is Long -> putLong(key, value)
                    is Double -> putFloat(key, value.toFloat())
                    else -> putString(key, value.toString())
                }
            }
            commit()
        }

        metaSettings.save(org.odk.collect.settings.keys.MetaKeys.CURRENT_PROJECT_ID, targetUuid)
        currentSystemProjectId = targetUuid
        serverUrl = staged.originalDraftUrl
        centralProjectId = staged.centralProjectId

        authManager.setActiveProject(staged.centralProjectId)
        authManager.setProjectMapping(staged.centralProjectId, targetUuid)
    }

    private fun extractDraftProjectIdentity(url: String): String? {
        return try {
            val uri = URI(url)
            val pathSegments = uri.path.orEmpty()
                .trimEnd('/')
                .split("/")
                .filter { it.isNotEmpty() }

            val projectIdx = pathSegments.indexOf("projects")
            val formIdx = pathSegments.indexOf("forms")
            if (projectIdx < 0 || formIdx < 0 || projectIdx + 1 >= pathSegments.size || formIdx + 1 >= pathSegments.size) {
                return null
            }

            val projectId = pathSegments[projectIdx + 1]
            val formId = pathSegments[formIdx + 1]
            val authority = uri.authority ?: return null
            val scheme = uri.scheme ?: "https"
            "$scheme://$authority|$projectId|$formId"
        } catch (_: Exception) {
            null
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
                        val authPrefs = getSharedPreferences(
                            edu.aiims.medresodk.auth.utils.MedresConstants.MEDRES_PREFS_NAME,
                            Context.MODE_PRIVATE
                        )
                        val stagingStore =
                            edu.aiims.medresodk.auth.qr.MedresQrStagingStore(authPrefs)
                        val stagedCtx = stagingStore.read()

                        // Only materialize ODK project for a MEDRES project QR.
                        // Draft QRs never go through login → never reach this path.
                        if (stagedCtx is edu.aiims.medresodk.auth.qr.StagedMedresProjectContext &&
                            stagedCtx.centralProjectId == pid
                        ) {
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

                            // Search by Central PID — stable even as the URL changes after tokenization
                            for (proj in allProjects) {
                                val projPrefs = getSharedPreferences("general_prefs${proj.uuid}", Context.MODE_PRIVATE)
                                val projUrl = projPrefs.getString(org.odk.collect.settings.keys.ProjectKeys.KEY_SERVER_URL, "") ?: ""
                                if (edu.aiims.medresodk.auth.utils.MedresProjectUtils.getProjectIdFromUrl(projUrl) == pid) {
                                    targetUuid = proj.uuid

                                    // Use typed staged project name — durable identity from QR metadata
                                    val qrProjectName = stagedCtx.projectName ?: ""
                                    if (qrProjectName.isNotEmpty() && proj.name != qrProjectName) {
                                        projectsRepo.save(
                                            org.odk.collect.projects.Project.Saved(
                                                proj.uuid, qrProjectName, proj.icon, proj.color
                                            )
                                        )
                                    }
                                    break
                                }
                            }

                            if (targetUuid == null) {
                                // Create new project — name comes from typed staged context
                                val projectName = stagedCtx.projectName ?: "MEDRES Project $pid"
                                val newProject = org.odk.collect.projects.Project.New(
                                    projectName, "A", "#3e9fcc"
                                )
                                targetUuid = projectsRepo.save(newProject).uuid
                            }

                            // APPLY SETTINGS (general + admin from staged context)
                            val generalJson = org.json.JSONObject(stagedCtx.generalSettingsJson)

                            val projPrefs = getSharedPreferences("general_prefs$targetUuid", Context.MODE_PRIVATE)
                            projPrefs.edit().apply {
                                putString(org.odk.collect.settings.keys.ProjectKeys.KEY_PROTOCOL, org.odk.collect.settings.keys.ProjectKeys.PROTOCOL_SERVER)
                                putString(org.odk.collect.settings.keys.ProjectKeys.KEY_USERNAME, username)
                                putString(org.odk.collect.settings.keys.ProjectKeys.KEY_PASSWORD, password)
                                val keysIterator = generalJson.keys()
                                while (keysIterator.hasNext()) {
                                    val key = keysIterator.next()
                                    if (!edu.aiims.medresodk.auth.utils.MedresSettingsValidator.isValidGeneralKey(key)) {
                                        android.util.Log.w("MedresLogin", "Ignoring invalid general key from QR: $key")
                                        continue
                                    }
                                    if (key == org.odk.collect.settings.keys.ProjectKeys.KEY_SERVER_URL ||
                                        key == org.odk.collect.settings.keys.ProjectKeys.KEY_USERNAME ||
                                        key == org.odk.collect.settings.keys.ProjectKeys.KEY_PASSWORD ||
                                        key == org.odk.collect.settings.keys.ProjectKeys.KEY_PROTOCOL) {
                                        continue
                                    }
                                    val value = generalJson.get(key)
                                    when (value) {
                                        is Boolean -> putBoolean(key, value)
                                        is String -> putString(key, value)
                                        is Int -> putInt(key, value)
                                        is Long -> putLong(key, value)
                                        is Double -> putFloat(key, value.toFloat())
                                        else -> putString(key, value.toString())
                                    }
                                }
                                commit()
                            }

                            // Apply ADMIN settings
                            val adminJson = org.json.JSONObject(stagedCtx.adminSettingsJson)
                            val adminPrefs = getSharedPreferences("admin_prefs$targetUuid", Context.MODE_PRIVATE)
                            adminPrefs.edit().apply {
                                val adminKeysIterator = adminJson.keys()
                                while (adminKeysIterator.hasNext()) {
                                    val key = adminKeysIterator.next()
                                    if (!edu.aiims.medresodk.auth.utils.MedresSettingsValidator.isValidAdminKey(key)) {
                                        android.util.Log.w("MedresLogin", "Ignoring invalid admin key from QR: $key")
                                        continue
                                    }
                                    val value = adminJson.get(key)
                                    when (value) {
                                        is Boolean -> putBoolean(key, value)
                                        else -> android.util.Log.w("MedresLogin", "Admin key $key has non-boolean value, skipping")
                                    }
                                }
                                commit()
                                android.util.Log.i("MedresLogin", "Applied admin settings from staged context")
                            }

                            // Set Active
                            metaSettings.save(org.odk.collect.settings.keys.MetaKeys.CURRENT_PROJECT_ID, targetUuid)
                            currentSystemProjectId = targetUuid

                            // Update Mappings
                            authManager.setProjectMapping(pid, targetUuid!!)

                            // RE-SYNC URL: write tokenized server_url
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
                        
                        // FIX: Update name if generic or if we have a better one from QR
                        // FIX: Hardcoded keys to solve persistence issue
                        val authPrefs = getSharedPreferences("medres_auth_prefs", Context.MODE_PRIVATE)
                        val savedProjectName = authPrefs.getString("auth_project_name", "")
                        android.util.Log.e("MedresLogin", "EXISTING PROJECT: Found Name in Prefs: '$savedProjectName' (Current: '${proj.name}')")

                        if (!savedProjectName.isNullOrEmpty() && proj.name != savedProjectName) {
                             val updatedProject = org.odk.collect.projects.Project.Saved(
                                 proj.uuid,
                                 savedProjectName,
                                 proj.icon,
                                 proj.color
                             )
                             projectsRepo.save(updatedProject)
                             android.util.Log.e("MedresLogin", "UPDATED project name to: $savedProjectName")
                        }
                        break
                    }
                }

                // Create new if not found
                if (targetProjectUuid == null) {
                    // Try to retrieve name from Auth Prefs (saved by QR Scanner)
                    // FIX: Hardcoded keys to solve persistence issue
                    val authPrefs = getSharedPreferences("medres_auth_prefs", Context.MODE_PRIVATE)
                    val savedProjectName = authPrefs.getString("auth_project_name", "")
                    android.util.Log.e("MedresLogin", "NEW PROJECT: Found Name in Prefs: '$savedProjectName'")

                    val finalProjectName = if (!savedProjectName.isNullOrEmpty()) {
                        savedProjectName
                    } else {
                        "MEDRES Project $centralPid"
                    }

                    val newProject = org.odk.collect.projects.Project.New(
                        finalProjectName,
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
