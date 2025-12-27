package org.aiims.odk.auth.activities

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
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

    private lateinit var pinField: EditText
    private lateinit var confirmPinField: EditText
    private lateinit var setupButton: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var userTextView: TextView

    // Store the authentication token from login
    private var authToken: String = ""
    private var expiresAt: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Get token data from intent
        authToken = intent.getStringExtra("auth_token") ?: ""
        expiresAt = intent.getStringExtra("expires_at") ?: ""

        // Create layout
        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(50, 100, 50, 50)
        }

        // Title
        val title = TextView(this).apply {
            text = getString(org.aiims.odk.auth.R.string.aiims_pin_setup_title)
            textSize = 28f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, 20)
            gravity = android.view.Gravity.CENTER
        }

        // Subtitle
        val subtitle = TextView(this).apply {
            text = getString(org.aiims.odk.auth.R.string.aiims_pin_setup_message)
            textSize = 16f
            setTextColor(android.graphics.Color.GRAY)
            setPadding(0, 0, 0, 20)
            gravity = android.view.Gravity.CENTER
        }

        // User info
        userTextView = TextView(this).apply {
            text = getString(org.aiims.odk.auth.R.string.aiims_welcome_user, "")
            textSize = 16f
            setTextColor(android.graphics.Color.DKGRAY)
            setPadding(0, 0, 0, 40)
            gravity = android.view.Gravity.CENTER
        }

        // Progress bar (initially hidden)
        progressBar = ProgressBar(this).apply {
            visibility = View.GONE
            setPadding(0, 0, 0, 20)
        }

        // PIN field
        val pinHint = TextView(this).apply {
            text = getString(org.aiims.odk.auth.R.string.aiims_pin_hint)
            textSize = 16f
            setPadding(0, 20, 0, 8)
        }

        pinField = EditText(this).apply {
            hint = "1234"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
            // Set max length via filters
            filters = arrayOf(android.text.InputFilter.LengthFilter(4))
        }

        // Confirm PIN field
        val confirmPinHint = TextView(this).apply {
            text = getString(org.aiims.odk.auth.R.string.aiims_confirm_pin_hint)
            textSize = 16f
            setPadding(0, 20, 0, 8)
        }

        confirmPinField = EditText(this).apply {
            hint = "1234"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
            // Set max length via filters
            filters = arrayOf(android.text.InputFilter.LengthFilter(4))
        }

        // Setup Button
        setupButton = Button(this).apply {
            text = getString(org.aiims.odk.auth.R.string.aiims_button_setup)
            textSize = 18f
            setPadding(0, 30, 0, 30)
            setOnClickListener {
                attemptPinSetup()
            }
        }

        // Add all views to layout
        layout.addView(title)
        layout.addView(subtitle)
        layout.addView(userTextView)
        layout.addView(pinHint)
        layout.addView(pinField)
        layout.addView(confirmPinHint)
        layout.addView(confirmPinField)
        layout.addView(setupButton)
        layout.addView(progressBar, 0) // Insert progress bar at the beginning

        setContentView(layout)

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