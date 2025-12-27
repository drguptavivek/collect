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
        val changePinButton: com.google.android.material.button.MaterialButton = findViewById(org.aiims.odk.auth.R.id.change_pin_button)
        val refreshTokenButton: com.google.android.material.button.MaterialButton = findViewById(org.aiims.odk.auth.R.id.refresh_token_button)
        val logoutButton: com.google.android.material.button.MaterialButton = findViewById(org.aiims.odk.auth.R.id.logout_button)

        // Set up click listeners
        changePinButton.setOnClickListener {
            changePin()
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
    }

    private fun loadUserData(userDetailsText: TextView, tokenStatusText: TextView, tokenValidityText: TextView, deviceIdText: TextView) {
        lifecycleScope.launch {
            authManager.currentUser.collect { user ->
                user?.let {
                    val userDetails = """
                        Username: ${it.username}
                        Project ID: ${it.projectId}
                    """.trimIndent()

                    userDetailsText.text = userDetails
                }
            }
        }

        // Get token expiry from Auth Manager
        val expiresAt = authManager.getActiveProjectTokenExpiry()
        if (expiresAt != null) {
            try {
                val expiryDate = org.aiims.odk.auth.utils.ApiDateFormat.parse(expiresAt)
                
                if (expiryDate != null) {
                    val displayFormatter = java.text.SimpleDateFormat("MMM dd, yyyy HH:mm", java.util.Locale.getDefault())
                    tokenStatusText.text = "Valid until: ${displayFormatter.format(expiryDate)}"
                    
                    // Check if token is still valid
                    val now = java.util.Date()
                    if (expiryDate.after(now)) {
                        val hoursLeft = ((expiryDate.time - now.time) / (1000 * 60 * 60)).toInt()
                        tokenValidityText.text = "✓ Token is valid ($hoursLeft hours remaining)"
                        tokenValidityText.setTextColor(getColor(org.aiims.odk.auth.R.color.aiims_success))
                    } else {
                        tokenValidityText.text = "⚠ Token has expired"
                        tokenValidityText.setTextColor(getColor(org.aiims.odk.auth.R.color.aiims_error))
                    }
                } else {
                    tokenStatusText.text = "Token expiry: $expiresAt"
                    tokenValidityText.text = "Unable to parse expiry date"
                }
            } catch (e: Exception) {
                tokenStatusText.text = "Token expiry: $expiresAt"
                tokenValidityText.text = "Error: ${e.message}"
            }
        } else {
            tokenStatusText.text = "No token information available"
            tokenValidityText.text = ""
        }

        // Device ID comes from Collect meta prefs
        val deviceId = getSharedPreferences("meta", MODE_PRIVATE).getString("metadata_installid", "No device ID found")
        deviceIdText.text = deviceId ?: "No device ID found"
    }

    private fun refreshToken() {
        // Logout first to clear current session, then navigate to login screen
        lifecycleScope.launch {
            authManager.logout()
            
            // Navigate to login screen for re-authentication
            val intent = Intent()
            intent.setClass(this@AuthSettingsActivity, org.aiims.odk.auth.activities.AiimsLoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
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
