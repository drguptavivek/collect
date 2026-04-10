package edu.aiims.medresodk.auth.activities

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import edu.aiims.medresodk.auth.injection.MedresAuthDependencyComponentProvider
import edu.aiims.medresodk.auth.managers.MedresAuthManager
import javax.inject.Inject

/**
 * Authentication Settings Activity
 * Displays user details, device token, and options to log out or change PIN
 */
class AuthSettingsActivity : MedresBaseActivity() {

    @Inject
    lateinit var settingsProvider: org.odk.collect.settings.SettingsProvider

    override fun injectDependencies() {
        (application as MedresAuthDependencyComponentProvider).medresAuthDependencyComponent.inject(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(edu.aiims.medresodk.auth.R.layout.activity_auth_settings)

        // Initialize views
        val userDetailsText: TextView = findViewById(edu.aiims.medresodk.auth.R.id.user_details_text)
        val tokenStatusText: TextView = findViewById(edu.aiims.medresodk.auth.R.id.token_status_text)
        val tokenValidityText: TextView = findViewById(edu.aiims.medresodk.auth.R.id.token_validity_text)
        val deviceIdText: TextView = findViewById(edu.aiims.medresodk.auth.R.id.device_id_text)
        val signatureText: TextView = findViewById(edu.aiims.medresodk.auth.R.id.signature_text)
        val clockWarningText: TextView = findViewById(edu.aiims.medresodk.auth.R.id.clock_warning_text) // New View
        val getProjectDetailsButton: com.google.android.material.button.MaterialButton = findViewById(edu.aiims.medresodk.auth.R.id.get_project_details_button)
        val changePinButton: com.google.android.material.button.MaterialButton = findViewById(edu.aiims.medresodk.auth.R.id.change_pin_button)
        val exportLogsButton: com.google.android.material.button.MaterialButton = findViewById(edu.aiims.medresodk.auth.R.id.export_logs_button)
        val saveLogsButton: com.google.android.material.button.MaterialButton = findViewById(edu.aiims.medresodk.auth.R.id.save_logs_button)
        val refreshTokenButton: com.google.android.material.button.MaterialButton = findViewById(edu.aiims.medresodk.auth.R.id.refresh_token_button)
        val logoutButton: com.google.android.material.button.MaterialButton = findViewById(edu.aiims.medresodk.auth.R.id.logout_button)

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

        saveLogsButton.setOnClickListener {
            saveLogs()
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
            val clip = android.content.ClipData.newPlainText(getString(edu.aiims.medresodk.auth.R.string.medres_device_id_title), deviceIdText.text.toString())
            clipboard.setPrimaryClip(clip)
            android.widget.Toast.makeText(this@AuthSettingsActivity, getString(edu.aiims.medresodk.auth.R.string.medres_device_id_copied), android.widget.Toast.LENGTH_SHORT).show()
        }

        // Load user data
        loadUserData(userDetailsText, tokenStatusText, tokenValidityText, deviceIdText)
        
        // Load app signature
        loadAppSignature(signatureText)
        
        // Setup Clock Warning Observer
        setupClockObserver(clockWarningText)
    }

    private fun exportLogs() {
        val loadingDialog = createLoadingDialog(
            getString(edu.aiims.medresodk.auth.R.string.medres_exporting_logs)
        )
        loadingDialog.show()

        lifecycleScope.launch {
            val zipFile = edu.aiims.medresodk.auth.utils.LogExporter.exportLogs(this@AuthSettingsActivity, settingsProvider)
            
            loadingDialog.dismiss()

            if (zipFile != null) {
                shareLogFile(zipFile)
            } else {
                 android.widget.Toast.makeText(
                    this@AuthSettingsActivity,
                    getString(edu.aiims.medresodk.auth.R.string.medres_no_logs_found),
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun saveLogs() {
        val loadingDialog = createLoadingDialog(
            getString(edu.aiims.medresodk.auth.R.string.medres_exporting_logs)
        )
        loadingDialog.show()

        lifecycleScope.launch {
            val zipFile = edu.aiims.medresodk.auth.utils.LogExporter.exportLogs(this@AuthSettingsActivity, settingsProvider)
            
            if (zipFile != null) {
                val success = edu.aiims.medresodk.auth.utils.LogExporter.saveToDownloads(this@AuthSettingsActivity, zipFile)
                loadingDialog.dismiss()

                if (success) {
                    android.widget.Toast.makeText(
                        this@AuthSettingsActivity,
                        getString(edu.aiims.medresodk.auth.R.string.medres_logs_saved_to_downloads),
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                } else {
                    android.widget.Toast.makeText(
                        this@AuthSettingsActivity,
                        getString(edu.aiims.medresodk.auth.R.string.medres_error_exporting_logs),
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            } else {
                loadingDialog.dismiss()
                android.widget.Toast.makeText(
                    this@AuthSettingsActivity,
                    getString(edu.aiims.medresodk.auth.R.string.medres_no_logs_found),
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
                getString(edu.aiims.medresodk.auth.R.string.medres_error_exporting_logs),
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
                    val projectName = getSharedPreferences(
                        edu.aiims.medresodk.auth.utils.MedresConstants.MEDRES_PREFS_NAME,
                        MODE_PRIVATE
                    )
                        .getString("project_name_${it.projectId}", null) ?: it.projectId
                    
                    // Get base API URL for display
                    val apiUrl = authManager.getActiveProjectApiUrl() ?: "N/A"
                    val displayUrl = edu.aiims.medresodk.auth.utils.MedresProjectUtils.formatUrlForDisplay(apiUrl)

                    val userDetails = """
                        Username: ${it.username}
                        Project: $projectName
                        Server: $displayUrl
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
                            tokenValidityText.setTextColor(getColor(edu.aiims.medresodk.auth.R.color.medres_success))
                        } else {
                            tokenValidityText.text = "⚠ Token has expired"
                            tokenValidityText.setTextColor(getColor(edu.aiims.medresodk.auth.R.color.medres_error))
                        }
                    } else {
                         // Fallback if validated time is not yet available (should be rare)
                         tokenValidityText.text = "Validating time..."
                         tokenValidityText.setTextColor(getColor(edu.aiims.medresodk.auth.R.color.medres_secondary))
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

    private fun loadAppSignature(signatureText: TextView) {
        try {
            val packageInfo = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                packageManager.getPackageInfo(packageName, android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES)
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(packageName, android.content.pm.PackageManager.GET_SIGNATURES)
            }

            val signatures = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                packageInfo.signingInfo?.apkContentsSigners
            } else {
                @Suppress("DEPRECATION")
                packageInfo.signatures
            }

            if (signatures != null && signatures.isNotEmpty()) {
                val cert = signatures[0].toByteArray()
                val md = java.security.MessageDigest.getInstance("SHA-256")
                val publicKey = md.digest(cert)
                val hexString = publicKey.joinToString(":") { "%02X".format(it) }
                signatureText.text = hexString

                // Make it clickable to copy
                signatureText.setOnClickListener {
                    val clipboard = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    val clip = android.content.ClipData.newPlainText("App Signature", hexString)
                    clipboard.setPrimaryClip(clip)
                    android.widget.Toast.makeText(this, "Signature copied to clipboard", android.widget.Toast.LENGTH_SHORT).show()
                }
            } else {
                signatureText.text = "Unknown"
            }
        } catch (e: Exception) {
            signatureText.text = "Error: ${e.message}"
        }
    }

    private fun showProjectDetails() {
        lifecycleScope.launch {
            var loadingDialog: androidx.appcompat.app.AlertDialog? = null
            
            try {
                loadingDialog = createLoadingDialog("Fetching project details...")
                loadingDialog.show()

                val success = authManager.fetchAndUpdateProjectDetails(this@AuthSettingsActivity)
                
                loadingDialog.dismiss()

                if (success) {
                    // Fetch the updated name from manager or prefs
                    authManager.currentUser.value?.let { user ->
                         val projectName = getSharedPreferences(
                            edu.aiims.medresodk.auth.utils.MedresConstants.MEDRES_PREFS_NAME,
                            MODE_PRIVATE
                        )
                            .getString("project_name_${user.projectId}", user.projectId)
                         
                         android.app.AlertDialog.Builder(this@AuthSettingsActivity)
                            .setTitle("Project Details")
                            .setMessage("Project Name: $projectName\n\nSuccessfully updated from server.")
                            .setPositiveButton("OK", null)
                            .show()
                    } ?: run {
                        android.app.AlertDialog.Builder(this@AuthSettingsActivity)
                            .setTitle("Project Details")
                            .setMessage("Project details were refreshed, but there is no active authenticated user context to display.")
                            .setPositiveButton("OK", null)
                            .show()
                    }
                } else {
                    android.app.AlertDialog.Builder(this@AuthSettingsActivity)
                        .setTitle("Update Failed")
                        .setMessage("Could not fetch project details. Make sure you are logged in to a normal MEDRES project and that the server is reachable.")
                        .setPositiveButton("OK", null)
                        .show()
                }
            } catch (e: Exception) {
                loadingDialog?.dismiss()
                android.widget.Toast.makeText(this@AuthSettingsActivity, "Error: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun createLoadingDialog(message: String): androidx.appcompat.app.AlertDialog {
        val container = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            setPadding(48, 40, 48, 40)
            gravity = android.view.Gravity.CENTER_VERTICAL
        }

        val progressBar = android.widget.ProgressBar(this).apply {
            isIndeterminate = true
        }

        val messageView = TextView(this).apply {
            text = message
            setPadding(32, 0, 0, 0)
        }

        container.addView(progressBar)
        container.addView(
            messageView,
            android.widget.LinearLayout.LayoutParams(
                0,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        )

        return com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setView(container)
            .setCancelable(false)
            .create()
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
                    intent.setClass(this@AuthSettingsActivity, edu.aiims.medresodk.auth.activities.MedresLoginActivity::class.java)
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
        intent.setClass(this@AuthSettingsActivity, edu.aiims.medresodk.auth.activities.ChangePinActivity::class.java)
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
            intent.setClass(this@AuthSettingsActivity, edu.aiims.medresodk.auth.activities.MedresLoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }

}
