package org.aiims.odk.auth.activities

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.aiims.odk.auth.injection.AiimsAuthDependencyComponentProvider
import org.aiims.odk.auth.utils.PinManager
import javax.inject.Inject

/**
 * PIN Setup Activity
 * Required after initial login for two-factor authentication
 */
class SetupPinActivity : AiimsBaseActivity() {

    @Inject
    lateinit var pinManager: PinManager

    override fun injectDependencies() {
        (application as AiimsAuthDependencyComponentProvider).aiimsAuthDependencyComponent.inject(this)
    }

    private lateinit var pinField: com.google.android.material.textfield.TextInputEditText
    private lateinit var confirmPinField: com.google.android.material.textfield.TextInputEditText
    private lateinit var setupButton: com.google.android.material.button.MaterialButton
    private lateinit var progressBar: ProgressBar
    private lateinit var userTextView: TextView

    // Store the authentication token from login
    private var authToken: String = ""
    private var expiresAt: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(org.aiims.odk.auth.R.layout.activity_setup_pin)

        // Get token data from intent
        authToken = intent.getStringExtra("auth_token") ?: ""
        expiresAt = intent.getStringExtra("expires_at") ?: ""

        // Initialize views
        progressBar = findViewById(org.aiims.odk.auth.R.id.progress_bar)
        userTextView = findViewById(org.aiims.odk.auth.R.id.user_text_view)
        pinField = findViewById(org.aiims.odk.auth.R.id.pin_field)
        confirmPinField = findViewById(org.aiims.odk.auth.R.id.confirm_pin_field)
        setupButton = findViewById(org.aiims.odk.auth.R.id.setup_button)

        // Set up button click listener
        setupButton.setOnClickListener {
            attemptPinSetup()
        }

        // Load user data
        loadUserData()
    }

    private fun loadUserData() {
        lifecycleScope.launch {
            authManager.currentUser.collect { user ->
                user?.let {
                    userTextView.text = getString(org.aiims.odk.auth.R.string.aiims_welcome_user, it.username)
                }
            }
        }
    }

    private fun attemptPinSetup() {
        val pin = pinField.text.toString().trim()
        val confirmPin = confirmPinField.text.toString().trim()

        // Validation
        when {
            pin.isEmpty() -> {
                pinField.error = getString(org.aiims.odk.auth.R.string.aiims_error_pin_required)
                pinField.requestFocus()
                return
            }
            pin.length != 4 -> {
                pinField.error = getString(org.aiims.odk.auth.R.string.aiims_error_pin_digits)
                pinField.requestFocus()
                return
            }
            confirmPin.isEmpty() -> {
                confirmPinField.error = getString(org.aiims.odk.auth.R.string.aiims_error_pin_confirm)
                confirmPinField.requestFocus()
                return
            }
            pin != confirmPin -> {
                confirmPinField.error = getString(org.aiims.odk.auth.R.string.aiims_error_pin_mismatch)
                confirmPinField.requestFocus()
                return
            }
        }

        // Show loading state
        setLoading(true)

        // For now, simulate PIN setup (in production, this would call the backend API)
        lifecycleScope.launch {
            // Simulate API call
            kotlinx.coroutines.delay(500)

            setLoading(false)

            // Save the PIN
            pinManager.savePin(pin)

            // Update auth state to LOGGED_IN now that PIN is set
            android.util.Log.d("SetupPinActivity", "PIN setup complete, setting state to LOGGED_IN")
            authManager.updateAuthState(org.aiims.odk.auth.managers.AuthState.LOGGED_IN)

            Toast.makeText(
                this@SetupPinActivity,
                getString(org.aiims.odk.auth.R.string.aiims_pin_setup_success),
                Toast.LENGTH_SHORT
            ).show()

            // Navigate to main app
            navigateToMain()
        }
    }

    private fun navigateToMain() {
        // Launch main ODK activity
        val intent = Intent()
        intent.setClassName(this.packageName, "org.odk.collect.android.mainmenu.MainMenuActivity")
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun setLoading(loading: Boolean) {
        progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        setupButton.isEnabled = !loading
        pinField.isEnabled = !loading
        confirmPinField.isEnabled = !loading
    }
}