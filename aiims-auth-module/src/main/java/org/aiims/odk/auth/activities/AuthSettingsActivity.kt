package org.aiims.odk.auth.activities

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
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

        // Create layout
        val layout = ScrollView(this).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT
            )
            setPadding(40, 60, 40, 60)
        }

        val container = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        // Title
        val title = TextView(this).apply {
            text = getString(org.aiims.odk.auth.R.string.aiims_auth_settings_title)
            textSize = 24f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, 40)
        }

        // User Details Section
        val userDetailsTitle = TextView(this).apply {
            text = getString(org.aiims.odk.auth.R.string.aiims_user_details_title)
            textSize = 18f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 20, 0, 16)
        }

        val userDetailsText = TextView(this).apply {
            textSize = 16f
            setPadding(0, 0, 0, 24)
        }

        // Device Token Section
        val tokenTitle = TextView(this).apply {
            text = getString(org.aiims.odk.auth.R.string.aiims_device_token_title)
            textSize = 18f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 20, 0, 16)
        }

        val tokenText = TextView(this).apply {
            textSize = 14f
            typeface = android.graphics.Typeface.MONOSPACE
            setPadding(0, 0, 0, 24)
        }

        // Set click listener after creation to avoid 'text' property issue
        tokenText.setOnClickListener {
            // Copy token to clipboard
            val clipboard = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText(getString(org.aiims.odk.auth.R.string.aiims_device_token_title), tokenText.text.toString())
            clipboard.setPrimaryClip(clip)
            android.widget.Toast.makeText(this@AuthSettingsActivity, getString(org.aiims.odk.auth.R.string.aiims_token_copied), android.widget.Toast.LENGTH_SHORT).show()
        }

        // Device ID Section
        val deviceIdTitle = TextView(this).apply {
            text = getString(org.aiims.odk.auth.R.string.aiims_device_id_title)
            textSize = 18f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 20, 0, 16)
        }

        val deviceIdText = TextView(this).apply {
            textSize = 14f
            typeface = android.graphics.Typeface.MONOSPACE
            setPadding(0, 0, 0, 24)
        }

        deviceIdText.setOnClickListener {
            val clipboard = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText(getString(org.aiims.odk.auth.R.string.aiims_device_id_title), deviceIdText.text.toString())
            clipboard.setPrimaryClip(clip)
            android.widget.Toast.makeText(this@AuthSettingsActivity, getString(org.aiims.odk.auth.R.string.aiims_device_id_copied), android.widget.Toast.LENGTH_SHORT).show()
        }

        // Change PIN Button
        val changePinButton = Button(this).apply {
            text = getString(org.aiims.odk.auth.R.string.aiims_button_change)
            textSize = 16f
            setPadding(0, 30, 0, 30)
            setOnClickListener {
                changePin()
            }
        }

        // Logout Button
        val logoutButton = Button(this).apply {
            text = getString(org.aiims.odk.auth.R.string.aiims_logout)
            textSize = 16f
            setBackgroundColor(android.graphics.Color.parseColor("#FF5252"))
            setTextColor(android.graphics.Color.WHITE)
            setPadding(0, 30, 0, 30)
            setOnClickListener {
                logout()
            }
        }

        // Back Button
        val backButton = Button(this).apply {
            text = getString(org.aiims.odk.auth.R.string.aiims_button_back_to_odk)
            textSize = 16f
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            setTextColor(android.graphics.Color.BLUE)
            setPadding(0, 20, 0, 0)
            setOnClickListener {
                finish()
            }
        }

        // Add all views to container
        container.addView(title)
        container.addView(userDetailsTitle)
        container.addView(userDetailsText)
        container.addView(tokenTitle)
        container.addView(tokenText)
        container.addView(deviceIdTitle)
        container.addView(deviceIdText)
        container.addView(changePinButton)
        container.addView(logoutButton)
        container.addView(backButton)

        // Add container to scroll view
        layout.addView(container)
        setContentView(layout)

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
