package org.aiims.odk.auth.activities

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.aiims.odk.auth.api.AuthResult
import org.aiims.odk.auth.managers.AiimsAuthManager

/**
 * PIN Setup Activity
 * Required after initial login for two-factor authentication
 */
class SetupPinActivity : AppCompatActivity() {

    private lateinit var authManager: AiimsAuthManager
    private lateinit var pinField: EditText
    private lateinit var confirmPinField: EditText
    private lateinit var setupButton: Button
    private lateinit var progressBar: ProgressBar

    // Store the authentication token from login
    private var authToken: String = ""
    private var expiresAt: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize auth manager
        authManager = AiimsAuthManager.getInstance(this)

        // Get token data from intent
        authToken = intent.getStringExtra("authToken") ?: ""
        expiresAt = intent.getStringExtra("expiresAt") ?: ""

        // Create layout
        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(50, 100, 50, 50)
        }

        // Title
        val title = TextView(this).apply {
            text = "Setup PIN"
            textSize = 28f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, 20)
            gravity = android.view.Gravity.CENTER
        }

        // Subtitle
        val subtitle = TextView(this).apply {
            text = "Create a 4-digit PIN for quick access"
            textSize = 16f
            setTextColor(android.graphics.Color.GRAY)
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
            text = "Enter PIN (4 digits):"
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
            text = "Confirm PIN:"
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
            text = "Setup PIN"
            textSize = 18f
            setPadding(0, 30, 0, 30)
            setOnClickListener {
                attemptPinSetup()
            }
        }

        // Skip button (optional - can be removed if PIN is mandatory)
        val skipButton = Button(this).apply {
            text = "Skip for now"
            textSize = 14f
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            setTextColor(android.graphics.Color.GRAY)
            setPadding(0, 10, 0, 10)
            setOnClickListener {
                skipPinSetup()
            }
        }

        // Add all views to layout
        layout.addView(title)
        layout.addView(subtitle)
        layout.addView(pinHint)
        layout.addView(pinField)
        layout.addView(confirmPinHint)
        layout.addView(confirmPinField)
        layout.addView(setupButton)
        layout.addView(skipButton)
        layout.addView(progressBar, 0) // Insert progress bar at the beginning

        setContentView(layout)
    }

    private fun attemptPinSetup() {
        val pin = pinField.text.toString().trim()
        val confirmPin = confirmPinField.text.toString().trim()

        // Validation
        when {
            pin.isEmpty() -> {
                pinField.error = "PIN is required"
                pinField.requestFocus()
                return
            }
            pin.length != 4 -> {
                pinField.error = "PIN must be 4 digits"
                pinField.requestFocus()
                return
            }
            confirmPin.isEmpty() -> {
                confirmPinField.error = "Please confirm your PIN"
                confirmPinField.requestFocus()
                return
            }
            pin != confirmPin -> {
                confirmPinField.error = "PINs do not match"
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

            // Store PIN (simplified - in production, use secure storage)
            // For demo purposes, we'll just mark PIN as set
            Toast.makeText(
                this@SetupPinActivity,
                "PIN setup successful!",
                Toast.LENGTH_SHORT
            ).show()

            // Navigate to main app
            navigateToMain()
        }
    }

    private fun skipPinSetup() {
        Toast.makeText(
            this,
            "PIN setup skipped. You can set it up later in settings.",
            Toast.LENGTH_LONG
        ).show()
        navigateToMain()
    }

    private fun navigateToMain() {
        // Launch main ODK activity
        val intent = Intent()
        intent.setClassName("org.odk.collect.android", "org.odk.collect.android.mainmenu.MainMenuActivity")
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