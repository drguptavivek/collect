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

        // Handle Re-Auth Mode
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
                        // Check if we need local PIN (optional feature, currently local-only)
                        // For now, proceed to Main Menu
                        navigateToMain()
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
            // Read "meta" prefs to get current project ID
            val metaPrefs = getSharedPreferences("meta", Context.MODE_PRIVATE)
            currentSystemProjectId = metaPrefs.getString("current_project_id", null)

            if (currentSystemProjectId.isNullOrBlank()) {
                showProjectMissingState()
                return
            }

            // Read Project Settings (ODK uses "general_prefs" + projectId)
            val prefsName = "general_prefs$currentSystemProjectId"
            val projectPrefs = getSharedPreferences(prefsName, Context.MODE_PRIVATE)
            serverUrl = projectPrefs.getString("server_url", null)

            // Extract Central Project ID
            centralProjectId = AiimsProjectUtils.getProjectIdFromUrl(serverUrl)

            if (centralProjectId != null && serverUrl != null) {
                // Set Active Project in Auth Manager
                authManager.setActiveProject(centralProjectId)
                binding.statusText.text = getString(org.aiims.odk.auth.R.string.aiims_project_configured, currentSystemProjectId, serverUrl)
                binding.statusText.visibility = View.VISIBLE
                enableLoginUi(true)
            } else {
                binding.statusText.text = getString(org.aiims.odk.auth.R.string.aiims_invalid_project_config, serverUrl)
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
        binding.scanQrButton.visibility = View.VISIBLE
        binding.loginButton.visibility = View.GONE
    }

    private fun enableLoginUi(enable: Boolean) {
        binding.usernameLayout.isEnabled = enable
        binding.passwordLayout.isEnabled = enable
        binding.loginButton.isEnabled = enable
        binding.loginButton.visibility = if (enable) View.VISIBLE else View.GONE
        binding.scanQrButton.visibility = if (!enable) View.VISIBLE else View.GONE
    }

    private fun attemptLogin() {
        val username = binding.usernameField.text.toString().trim()
        val password = binding.passwordField.text.toString()

        if (username.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, getString(org.aiims.odk.auth.R.string.aiims_error_invalid_credentials), Toast.LENGTH_SHORT).show()
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
                    Toast.makeText(this@AiimsLoginActivity, getString(org.aiims.odk.auth.R.string.aiims_welcome_user, result.user.username), Toast.LENGTH_SHORT).show()

                    // Trigger Telemetry with Location (Manager sends one without location, we refine it here)
                    lifecycleScope.launch {
                        authManager.submitTelemetry(getLastKnownLocation())
                    }

                    // SAVE CREDENTIALS TO ODK SETTINGS
                    if (currentSystemProjectId != null && currentSystemProjectId != "MANUAL_FALLBACK") {
                        try {
                            val projPrefs = getSharedPreferences("general_prefs$currentSystemProjectId", Context.MODE_PRIVATE)
                            projPrefs.edit()
                                .putString(org.odk.collect.settings.keys.ProjectKeys.KEY_USERNAME, username)
                                .putString(org.odk.collect.settings.keys.ProjectKeys.KEY_PASSWORD, password)
                                .commit()
                        } catch (e: Exception) {
                            // Ignored
                        }
                    }

                    // Check for PIN
                    if (pinManager.isPinSet()) {
                        navigateToMain()
                    } else {
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
        // Launch ODK's QR Code Tabs Activity
        try {
            val intent = Intent()
            intent.setClassName(this, "org.odk.collect.android.configure.qr.QRCodeTabsActivity")
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, getString(org.aiims.odk.auth.R.string.aiims_error_qr_scanner_launch), Toast.LENGTH_SHORT).show()
        }
    }

    override fun onBackPressed() {
        if (intent.getBooleanExtra("is_reauth", false)) {
            // Treat Back as Cancel/Snooze
            authManager.snoozeSoftExpiry()
            super.onBackPressed()
        } else {
            super.onBackPressed()
        }
    }

    private fun showManualUrlDialog() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 40, 50, 10)
        }

        val baseUrlInput = EditText(this).apply {
            hint = getString(org.aiims.odk.auth.R.string.aiims_base_url_hint)
            // Use existing base if feasible, or default
            val current = serverUrl ?: "https://central.local"
            // Strip project part if present for cleaner default
            setText(if (current.contains("/v1/projects")) current.substringBefore("/v1/projects") else current)
        }

        val projectIdInput = EditText(this).apply {
            hint = getString(org.aiims.odk.auth.R.string.aiims_project_id_hint)
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText(centralProjectId ?: "1")
        }

        layout.addView(TextView(this).apply { text = getString(org.aiims.odk.auth.R.string.aiims_server_base_url) })
        layout.addView(baseUrlInput)
        layout.addView(TextView(this).apply {
            text = getString(org.aiims.odk.auth.R.string.aiims_project_id_label)
            setPadding(0, 30, 0, 0)
        })
        layout.addView(projectIdInput)

        try {
            AlertDialog.Builder(this)
                .setTitle(getString(org.aiims.odk.auth.R.string.aiims_manual_configuration_title))
                .setView(layout)
                .setPositiveButton(getString(org.aiims.odk.auth.R.string.aiims_button_set)) { _, _ ->
                    val baseUrl = baseUrlInput.text.toString().trim().trimEnd('/')
                    val pid = projectIdInput.text.toString().trim()

                    if (baseUrl.isNotEmpty() && pid.isNotEmpty()) {
                        // Construct full URL: Base + /v1/projects/ + ID
                        val fullUrl = "$baseUrl/v1/projects/$pid"
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
                currentSystemProjectId = targetProjectUuid
                serverUrl = url
                centralProjectId = centralPid

                authManager.setActiveProject(centralPid)

                binding.statusText.text = getString(org.aiims.odk.auth.R.string.aiims_project_configured, centralPid, url)
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
