package org.aiims.odk.auth.activities

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.aiims.odk.auth.injection.AiimsAuthDependencyComponentProvider
import org.aiims.odk.auth.managers.AiimsAuthManager
import javax.inject.Inject

/**
 * Authentication Settings Activity
 * Displays user details, device token, and options to log out or change PIN
 */
class AuthSettingsActivity : AiimsBaseActivity() {

    @Inject
    lateinit var settingsProvider: org.odk.collect.settings.SettingsProvider

    override fun injectDependencies() {
        (application as AiimsAuthDependencyComponentProvider).aiimsAuthDependencyComponent.inject(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(org.aiims.odk.auth.R.layout.activity_auth_settings)

        // Initialize views
        val userDetailsText: TextView = findViewById(org.aiims.odk.auth.R.id.user_details_text)
        val tokenStatusText: TextView = findViewById(org.aiims.odk.auth.R.id.token_status_text)
        val tokenValidityText: TextView = findViewById(org.aiims.odk.auth.R.id.token_validity_text)
        val deviceIdText: TextView = findViewById(org.aiims.odk.auth.R.id.device_id_text)
        val clockWarningText: TextView = findViewById(org.aiims.odk.auth.R.id.clock_warning_text) // New View
        val getProjectDetailsButton: com.google.android.material.button.MaterialButton = findViewById(org.aiims.odk.auth.R.id.get_project_details_button)
        val changePinButton: com.google.android.material.button.MaterialButton = findViewById(org.aiims.odk.auth.R.id.change_pin_button)
        val exportLogsButton: com.google.android.material.button.MaterialButton = findViewById(org.aiims.odk.auth.R.id.export_logs_button)
        val refreshTokenButton: com.google.android.material.button.MaterialButton = findViewById(org.aiims.odk.auth.R.id.refresh_token_button)
        val logoutButton: com.google.android.material.button.MaterialButton = findViewById(org.aiims.odk.auth.R.id.logout_button)

        // Set up click listeners
        getProjectDetailsButton.setOnClickListener {
            showProjectDetails()
        }

        changePinButton.setOnClickListener {
            changePin()
        }
        
        exportLogsButton.setOnClickListener {
            exportLogs()
        }

        refreshTokenButton.setOnClickListener {
            refreshToken()
        }

        logoutButton.setOnClickListener {
            logout()
        }

        // Device ID click to copy
        deviceIdText.setOnClickListener {
            val clipboard = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText(getString(org.aiims.odk.auth.R.string.aiims_device_id_title), deviceIdText.text.toString())
            clipboard.setPrimaryClip(clip)
            android.widget.Toast.makeText(this@AuthSettingsActivity, getString(org.aiims.odk.auth.R.string.aiims_device_id_copied), android.widget.Toast.LENGTH_SHORT).show()
        }

        // Load user data
        loadUserData(userDetailsText, tokenStatusText, tokenValidityText, deviceIdText)
        
        // Setup Clock Warning Observer
        setupClockObserver(clockWarningText)
    }

    private fun exportLogs() {
        val progressDialog = android.app.ProgressDialog.show(
            this,
            null,
            getString(org.aiims.odk.auth.R.string.aiims_exporting_logs),
            true
        )

        lifecycleScope.launch {
            val zipFile = org.aiims.odk.auth.utils.LogExporter.exportLogs(this@AuthSettingsActivity, settingsProvider)
            
            progressDialog.dismiss()

            if (zipFile != null) {
                shareLogFile(zipFile)
            } else {
                 android.widget.Toast.makeText(
                    this@AuthSettingsActivity,
                    getString(org.aiims.odk.auth.R.string.aiims_no_logs_found),
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun shareLogFile(file: java.io.File) {
        try {
            val uri = androidx.core.content.FileProvider.getUriForFile(
                this,
                "$packageName.provider",
                file
            )

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/zip"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            startActivity(Intent.createChooser(intent, "Share Logs"))
        } catch (e: Exception) {
             android.widget.Toast.makeText(
                this@AuthSettingsActivity,
                getString(org.aiims.odk.auth.R.string.aiims_error_exporting_logs),
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }
    
    private fun setupClockObserver(warningText: TextView) {
        lifecycleScope.launch {
            authManager.serverTimeDifferenceMs.collect { diffMs ->
                if (kotlin.math.abs(diffMs) > 30 * 60 * 1000L) { // > 30 minutes difference
                    val hours = kotlin.math.abs(diffMs) / (60 * 60 * 1000)
                    val minutes = (kotlin.math.abs(diffMs) / (60 * 1000)) % 60

                    val diffStr = if (hours > 0) {
                        "${hours}h ${minutes}m"
                    } else {
                        "${minutes}m"
                    }

                    // diffMs > 0 means device time is ahead of server time
                    // diffMs < 0 means device time is behind server time
                    val direction = if (diffMs > 0) "ahead of" else "behind"
                    warningText.text = "⚠ Device time is $diffStr $direction server time."
                    warningText.visibility = android.view.View.VISIBLE
                } else {
                    warningText.visibility = android.view.View.GONE
                }
            }
        }
    }

    private fun loadUserData(userDetailsText: TextView, tokenStatusText: TextView, tokenValidityText: TextView, deviceIdText: TextView) {
        lifecycleScope.launch {
            authManager.currentUser.collect { user ->
                user?.let {
                    // Get project name from storage (fetched during login)
                    val projectName = getSharedPreferences("aiims_auth", MODE_PRIVATE)
                        .getString("project_name_${it.projectId}", null) ?: it.projectId
                    
                    // Get base API URL for display
                    val apiUrl = authManager.getActiveProjectApiUrl() ?: "N/A"

                    val userDetails = """
                        Username: ${it.username}
                        Project: $projectName
                        Server: $apiUrl
                    """.trimIndent()

                    userDetailsText.text = userDetails
                }
            }
        }

        // Observe token expiry and validated time to display correct validity status
        lifecycleScope.launch {
            kotlinx.coroutines.flow.combine(
                authManager.tokenExpiryTime,
                authManager.validatedCurrentTime
            ) { expiryTime, currentTime ->
                if (expiryTime > 0) {
                    val expiryDate = java.util.Date(expiryTime)
                    val displayFormatter = java.text.SimpleDateFormat("MMM dd, yyyy HH:mm", java.util.Locale.getDefault())
                    tokenStatusText.text = "Valid until: ${displayFormatter.format(expiryDate)}"
                    
                    if (currentTime > 0) {
                        val hoursLeft = ((expiryTime - currentTime) / (1000 * 60 * 60)).toInt()
                        
                        if (hoursLeft >= 0) {
                            tokenValidityText.text = "✓ Token is valid ($hoursLeft hours remaining)"
                            tokenValidityText.setTextColor(getColor(org.aiims.odk.auth.R.color.aiims_success))
                        } else {
                            tokenValidityText.text = "⚠ Token has expired"
                            tokenValidityText.setTextColor(getColor(org.aiims.odk.auth.R.color.aiims_error))
                        }
                    } else {
                         // Fallback if validated time is not yet available (should be rare)
                         tokenValidityText.text = "Validating time..."
                         tokenValidityText.setTextColor(getColor(org.aiims.odk.auth.R.color.aiims_secondary))
                    }
                } else {
                    // Try to load from persistence if flow is empty (e.g. strict mode or init issue)
                    val persistedExpiry = authManager.getActiveProjectTokenExpiry()
                    if (persistedExpiry != null) {
                         tokenStatusText.text = "Token expiry: $persistedExpiry"
                         // We can't validate reliably without validated time, so just show label
                         tokenValidityText.text = "Checking validity..."
                    } else {
                        tokenStatusText.text = "No token information available"
                        tokenValidityText.text = ""
                    }
                }
            }.collect {}
        }

        // Device ID comes from Collect meta prefs
        val deviceId = getSharedPreferences("meta", MODE_PRIVATE).getString("metadata_installid", "No device ID found")
        deviceIdText.text = deviceId ?: "No device ID found"
    }

    private fun showProjectDetails() {
        lifecycleScope.launch {
            var progressDialog: android.app.ProgressDialog? = null
            var currentUser: org.aiims.odk.auth.api.User? = null
            
            try {
                // Show loading
                progressDialog = android.app.ProgressDialog.show(
                    this@AuthSettingsActivity,
                    "Loading",
                    "Fetching project details...",
                    true
                )

                // Get current user and token
                authManager.currentUser.collect { user ->
                    currentUser = user
                    // Cancel collection after first emission
                    throw kotlinx.coroutines.CancellationException()
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Expected - used to break out of collect
            }

            try {
                val token = authManager.getActiveProjectToken()
                val user = currentUser  // Local copy for smart cast

                if (user == null || token == null) {
                    progressDialog?.dismiss()
                    android.widget.Toast.makeText(
                        this@AuthSettingsActivity,
                        "Unable to fetch project details: Not logged in",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                    return@launch
                }

                // Get API URL from AuthManager
                val apiUrl = authManager.getActiveProjectApiUrl()

                if (apiUrl == null) {
                    progressDialog?.dismiss()
                    android.widget.Toast.makeText(
                        this@AuthSettingsActivity,
                        "Unable to fetch project details: API URL not found",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                    return@launch
                }

                // Fetch project details
                val authClient = org.aiims.odk.auth.api.RealAuthClient.getInstance(this@AuthSettingsActivity, apiUrl)
                val projectInfo = authClient.fetchProject(user.projectId, token)

                progressDialog?.dismiss()

                if (projectInfo != null) {
                    // Update project name in Collect settings
                    authManager.updateCollectProjectName(user.projectId, projectInfo.name)

                    // Show project details in dialog
                    val message = """
                        Project ID: ${projectInfo.id}
                        Name: ${projectInfo.name}
                        Description: ${projectInfo.description ?: "N/A"}
                        Archived: ${if (projectInfo.archived) "Yes" else "No"}
                    """.trimIndent()

                    android.app.AlertDialog.Builder(this@AuthSettingsActivity)
                        .setTitle("Project Details")
                        .setMessage(message)
                        .setPositiveButton("OK", null)
                        .show()
                } else {
                    android.app.AlertDialog.Builder(this@AuthSettingsActivity)
                        .setTitle("Fetch Failed")
                        .setMessage("Failed to fetch project details for Project ID: ${user.projectId}. Please ensures your account has sufficient permissions in ODK Central.")
                        .setPositiveButton("OK", null)
                        .show()
                }
            } catch (e: Exception) {
                progressDialog?.dismiss()
                android.widget.Toast.makeText(
                    this@AuthSettingsActivity,
                    "Error: ${e.message}",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun refreshToken() {
        // Get current username to pre-fill in login screen
        lifecycleScope.launch {
            // Collect once from the Flow to get current user
            authManager.currentUser.collect { user ->
                user?.let {
                    // Navigate to login screen for re-authentication
                    // Do NOT logout - preserve PIN and session data
                    val intent = Intent()
                    intent.setClass(this@AuthSettingsActivity, org.aiims.odk.auth.activities.AiimsLoginActivity::class.java)
                    intent.putExtra("EXTRA_IS_REAUTH", true)
                    intent.putExtra("EXTRA_REAUTH_USERNAME", it.username)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                }
                // Cancel the flow collection after first emission
                return@collect
            }
        }
    }

    private fun changePin() {
        // Launch Change PIN activity
        val intent = Intent()
        intent.setClass(this@AuthSettingsActivity, org.aiims.odk.auth.activities.ChangePinActivity::class.java)
        startActivity(intent)
    }

    private fun logout() {
        lifecycleScope.launch {
            authManager.logout()

            android.widget.Toast.makeText(
                this@AuthSettingsActivity,
                "Logged out successfully",
                android.widget.Toast.LENGTH_SHORT
            ).show()

            // Navigate to login screen
            val intent = Intent()
            intent.setClass(this@AuthSettingsActivity, org.aiims.odk.auth.activities.AiimsLoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }
}
