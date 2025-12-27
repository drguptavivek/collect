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
        val tokenText: TextView = findViewById(org.aiims.odk.auth.R.id.token_text)
        val deviceIdText: TextView = findViewById(org.aiims.odk.auth.R.id.device_id_text)
        val changePinButton: com.google.android.material.button.MaterialButton = findViewById(org.aiims.odk.auth.R.id.change_pin_button)
        val logoutButton: com.google.android.material.button.MaterialButton = findViewById(org.aiims.odk.auth.R.id.logout_button)

        // Set up click listeners
        changePinButton.setOnClickListener {
            changePin()
        }

        logoutButton.setOnClickListener {
            logout()
        }

        // Token and device ID click to copy
        tokenText.setOnClickListener {
            val clipboard = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText(getString(org.aiims.odk.auth.R.string.aiims_device_token_title), tokenText.text.toString())
            clipboard.setPrimaryClip(clip)
            android.widget.Toast.makeText(this@AuthSettingsActivity, getString(org.aiims.odk.auth.R.string.aiims_token_copied), android.widget.Toast.LENGTH_SHORT).show()
        }

        deviceIdText.setOnClickListener {
            val clipboard = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText(getString(org.aiims.odk.auth.R.string.aiims_device_id_title), deviceIdText.text.toString())
            clipboard.setPrimaryClip(clip)
            android.widget.Toast.makeText(this@AuthSettingsActivity, getString(org.aiims.odk.auth.R.string.aiims_device_id_copied), android.widget.Toast.LENGTH_SHORT).show()
        }

        // Load user data
        loadUserData(userDetailsText, tokenText, deviceIdText)
    }

    private fun loadUserData(userDetailsText: TextView, tokenText: TextView, deviceIdText: TextView) {
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

        // Get device token from Auth Manager
        val token = authManager.getActiveProjectToken() ?: getString(org.aiims.odk.auth.R.string.aiims_error_server)
        tokenText.text = token

        // Add hint about tap to copy
        tokenText.append("\n\n" + getString(org.aiims.odk.auth.R.string.aiims_copy_to_clipboard_hint))

        // Device ID comes from Collect meta prefs
        val deviceId = getSharedPreferences("meta", MODE_PRIVATE).getString("metadata_installid", "No device ID found")
        deviceIdText.text = deviceId ?: "No device ID found"
        deviceIdText.append("\n\n" + getString(org.aiims.odk.auth.R.string.aiims_copy_to_clipboard_hint))
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
