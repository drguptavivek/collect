package org.aiims.odk.auth.activities

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.aiims.odk.auth.api.AuthResult
import org.aiims.odk.auth.managers.AiimsAuthManager
import org.aiims.odk.auth.utils.AiimsProjectUtils
import org.aiims.odk.auth.utils.TokenRevocationManager
import java.io.File

/**
 * AIIMS Login Activity (Central Backend Version)
 * Automatically detects the current ODK Project and authenticates against it.
 */
class AiimsLoginActivity : AppCompatActivity() {

    private lateinit var authManager: AiimsAuthManager
    private lateinit var usernameField: EditText
    private lateinit var passwordField: EditText
    private lateinit var loginButton: Button
    private lateinit var scanQrButton: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var statusText: TextView

    // Active Project Context
    private var currentSystemProjectId: String? = null // ODK's internal UUID
    private var centralProjectId: String? = null // Central's integer ID
    private var serverUrl: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        authManager = AiimsAuthManager.getInstance(this)

        // UI Setup
        createLayout()

        // Process any pending revocations
        lifecycleScope.launch {
            TokenRevocationManager.processPending(this@AiimsLoginActivity)
        }

        // Detect Project
        detectCurrentProject()

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

    private fun detectCurrentProject() {
        try {
            // Read "meta" prefs to get current project ID
            val metaPrefs = getSharedPreferences("meta", Context.MODE_PRIVATE)
            currentSystemProjectId = metaPrefs.getString("current_project_id", null)

            if (currentSystemProjectId.isNullOrBlank()) {
                showProjectMissingState()
                return
            }

            // Read Project Settings
            val prefsName = "org.odk.collect.android_preferences_$currentSystemProjectId"
            val projectPrefs = getSharedPreferences(prefsName, Context.MODE_PRIVATE)
            serverUrl = projectPrefs.getString("server_url", null)

            // Extract Central Project ID
            centralProjectId = AiimsProjectUtils.getProjectIdFromUrl(serverUrl)

            if (centralProjectId != null && serverUrl != null) {
                // Set Active Project in Auth Manager
                authManager.setActiveProject(centralProjectId)
                statusText.text = "Project Configured: $currentSystemProjectId\nServer: $serverUrl"
                statusText.visibility = View.VISIBLE
                enableLoginUi(true)
            } else {
                statusText.text = "Invalid Project Configuration.\nURL: $serverUrl"
                statusText.visibility = View.VISIBLE
                enableLoginUi(false)
            }

        } catch (e: Exception) {
            statusText.text = "Error reading project settings: ${e.message}"
            enableLoginUi(false)
        }
    }

    private fun showProjectMissingState() {
        statusText.text = "No Project Configured. Please scan a QR code."
        statusText.visibility = View.VISIBLE
        enableLoginUi(false)
        scanQrButton.visibility = View.VISIBLE
        loginButton.visibility = View.GONE
    }
    
    private fun enableLoginUi(enable: Boolean) {
        usernameField.isEnabled = enable
        passwordField.isEnabled = enable
        loginButton.isEnabled = enable
        loginButton.visibility = if (enable) View.VISIBLE else View.GONE
        scanQrButton.visibility = if (!enable) View.VISIBLE else View.GONE
    }

    private fun attemptLogin() {
        val username = usernameField.text.toString().trim()
        val password = passwordField.text.toString()

        if (username.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Please enter credentials", Toast.LENGTH_SHORT).show()
            return
        }

        val pid = centralProjectId ?: return
        val url = serverUrl ?: return

        progressBar.visibility = View.VISIBLE
        loginButton.isEnabled = false

        lifecycleScope.launch {
            val result = authManager.login(pid, username, password, url)
            progressBar.visibility = View.GONE
            loginButton.isEnabled = true

            when (result) {
                is AuthResult.Success -> {
                    Toast.makeText(this@AiimsLoginActivity, "Welcome ${result.user.username}", Toast.LENGTH_SHORT).show()
                    
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
                    val pinManager = org.aiims.odk.auth.utils.PinManager.getInstance(this@AiimsLoginActivity)
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
        intent.setClassName("org.odk.collect.android", "org.odk.collect.android.mainmenu.MainMenuActivity")
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
            Toast.makeText(this, "Could not launch QR Scanner", Toast.LENGTH_SHORT).show()
        }
    }

    private fun createLayout() {
        // Main container with ScrollView for small screens
        val scrollView = android.widget.ScrollView(this).apply {
            isFillViewport = true
        }

        val mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, 0, 0, 0)
        }

        // Header Frame: Settings Icon (Top Right) + Logo (Center)
        val headerFrame = android.widget.FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 50, 0, 20)
            }
        }

        // Settings Icon
        val settingsBtn = android.widget.ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_menu_preferences)
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            setOnClickListener { showManualUrlDialog() }
            layoutParams = android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.TOP or Gravity.END
                setMargins(0, 0, 30, 0)
            }
        }

        // Logo
        val logoView = android.widget.ImageView(this).apply {
            try {
                // Assuming R.drawable.aiims_logo exists and is accessible
                // If not, we fall back or catch exception to avoid crash
                setImageResource(org.aiims.odk.auth.R.drawable.aiims_logo)
            } catch (e: Exception) {
                // Fallback text if resource issue
            }
            adjustViewBounds = true
            maxHeight = 300
            layoutParams = android.widget.FrameLayout.LayoutParams(
                400, // Width
                400  // Height
            ).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = 100 // Push down below settings icon
            }
        }

        headerFrame.addView(logoView)
        headerFrame.addView(settingsBtn)

        val contentLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 50, 50, 50)
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val title = TextView(this).apply {
            text = "AIIMS ODK Collect"
            textSize = 24f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 40)
        }

        statusText = TextView(this).apply {
            text = "Initializing..."
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 20)
        }

        usernameField = EditText(this).apply {
            hint = "Username"
        }

        passwordField = EditText(this).apply {
            hint = "Password"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        
        progressBar = ProgressBar(this).apply {
            visibility = View.GONE
        }

        loginButton = Button(this).apply {
            text = "Login"
            setOnClickListener { attemptLogin() }
        }

        scanQrButton = Button(this).apply {
            text = "Scan QR Code"
            visibility = View.GONE
            setOnClickListener { launchQrScanner() }
        }

        contentLayout.addView(title)
        contentLayout.addView(statusText)
        contentLayout.addView(usernameField)
        contentLayout.addView(passwordField)
        contentLayout.addView(progressBar)
        contentLayout.addView(loginButton)
        contentLayout.addView(scanQrButton)

        mainLayout.addView(headerFrame)
        mainLayout.addView(contentLayout)
        
        scrollView.addView(mainLayout)

        setContentView(scrollView)
    }

    private fun showManualUrlDialog() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 40, 50, 10)
        }

        val baseUrlInput = EditText(this).apply {
            hint = "Base URL (e.g. https://central.local)"
            // Use existing base if feasible, or default
            val current = serverUrl ?: "https://central.local"
            // Strip project part if present for cleaner default
            setText(if (current.contains("/v1/projects")) current.substringBefore("/v1/projects") else current)
        }
        
        val projectIdInput = EditText(this).apply {
            hint = "Project ID (e.g. 1)"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText(centralProjectId ?: "1")
        }

        layout.addView(TextView(this).apply { text = "Server Base URL" })
        layout.addView(baseUrlInput)
        layout.addView(TextView(this).apply { 
            text = "Project ID"
            setPadding(0, 30, 0, 0)
        })
        layout.addView(projectIdInput)

        android.app.AlertDialog.Builder(this)
            .setTitle("Manual Configuration")
            .setView(layout)
            .setPositiveButton("Set") { _, _ ->
                val baseUrl = baseUrlInput.text.toString().trim().trimEnd('/')
                val pid = projectIdInput.text.toString().trim()
                
                if (baseUrl.isNotEmpty() && pid.isNotEmpty()) {
                    // Construct full URL: Base + /v1/projects/ + ID
                    val fullUrl = "$baseUrl/v1/projects/$pid"
                    manualConfigureProject(fullUrl)
                } else {
                    Toast.makeText(this, "Please enter both Base URL and Project ID", Toast.LENGTH_SHORT).show()
                }
            }
            .setNeutralButton("Direct URL") { _, _ ->
            }
            .setNegativeButton("Cancel", null)
            .show()
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

                // Check for existing project
                var targetProjectUuid: String? = null
                val allProjects = projectsRepo.getAll()
                
                for (proj in allProjects) {
                    val projPrefs = getSharedPreferences("general_prefs${proj.uuid}", Context.MODE_PRIVATE)
                    val projUrl = projPrefs.getString(org.odk.collect.settings.keys.ProjectKeys.KEY_SERVER_URL, null)
                    if (projUrl == url) {
                        targetProjectUuid = proj.uuid
                        break
                    }
                }

                // Create if not exists
                if (targetProjectUuid == null) {
                    val newProject = org.odk.collect.projects.Project.New(
                        "Manual Project $centralPid", 
                        "M", 
                        "#3e9fcc"
                    )
                    val saved = projectsRepo.save(newProject)
                    targetProjectUuid = saved.uuid
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
                
                statusText.text = "Configured & Saved (ID: $centralPid)\nServer: $url"
                statusText.visibility = View.VISIBLE
                enableLoginUi(true)
                
                Toast.makeText(this, "Project Saved: $targetProjectUuid", Toast.LENGTH_SHORT).show()

            } catch (e: Exception) {
                android.util.Log.e("AiimsLogin", "Error saving project", e)
                Toast.makeText(this, "Error saving project: ${e.message}", Toast.LENGTH_LONG).show()
                
                // Fallback
                currentSystemProjectId = "MANUAL_FALLBACK"
                serverUrl = url
                centralProjectId = centralPid
                authManager.setActiveProject(centralPid)
                enableLoginUi(true)
            }
        } else {
             Toast.makeText(this, "Invalid Project URL format", Toast.LENGTH_LONG).show()
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
}
