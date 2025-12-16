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
import org.aiims.odk.auth.managers.AiimsAuthManager

/**
 * PIN Entry Activity
 * For returning users who have already set up a PIN
 */
class PinEntryActivity : AppCompatActivity() {

    private lateinit var authManager: AiimsAuthManager
    private lateinit var pinField: EditText
    private lateinit var enterButton: Button
    private lateinit var progressBar: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize auth manager
        authManager = AiimsAuthManager.getInstance(this)

        // Check if user is already logged in
        lifecycleScope.launch {
            authManager.authState.collect { state ->
                if (state == org.aiims.odk.auth.managers.AuthState.LOGGED_OUT) {
                    // User logged out, go to login
                    finish()
                    val intent = Intent()
                    intent.setClass(this@PinEntryActivity, org.aiims.odk.auth.activities.AiimsLoginActivity::class.java)
                    startActivity(intent)
                }
            }
        }

        // Create layout
        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(50, 150, 50, 50)
        }

        // Title
        val title = TextView(this).apply {
            text = "Enter PIN"
            textSize = 28f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, 20)
            gravity = android.view.Gravity.CENTER
        }

        // Subtitle
        val subtitle = TextView(this).apply {
            text = "Enter your 4-digit PIN to continue"
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
            text = "PIN:"
            textSize = 16f
            setPadding(0, 20, 0, 8)
        }

        pinField = EditText(this).apply {
            hint = "••••"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
            // Set max length via filters
            filters = arrayOf(android.text.InputFilter.LengthFilter(4))
        }

        // Enter Button
        enterButton = Button(this).apply {
            text = "Enter"
            textSize = 18f
            setPadding(0, 30, 0, 30)
            setOnClickListener {
                attemptPinEntry()
            }
        }

        // Forgot PIN
        val forgotPinButton = Button(this).apply {
            text = "Forgot PIN?"
            textSize = 14f
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            setTextColor(android.graphics.Color.BLUE)
            setPadding(0, 10, 0, 10)
            setOnClickListener {
                forgotPin()
            }
        }

        // Add all views to layout
        layout.addView(title)
        layout.addView(subtitle)
        layout.addView(pinHint)
        layout.addView(pinField)
        layout.addView(enterButton)
        layout.addView(forgotPinButton)
        layout.addView(progressBar, 0) // Insert progress bar at the beginning

        setContentView(layout)
    }

    private fun attemptPinEntry() {
        val pin = pinField.text.toString().trim()

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
        }

        // Show loading state
        setLoading(true)

        // For now, accept any 4-digit PIN (in production, verify against stored PIN)
        lifecycleScope.launch {
            // Simulate PIN verification
            kotlinx.coroutines.delay(500)

            setLoading(false)

            // For demo purposes, accept any PIN
            Toast.makeText(
                this@PinEntryActivity,
                "PIN verified successfully",
                Toast.LENGTH_SHORT
            ).show()

            // Navigate to main app
            navigateToMain()
        }
    }

    private fun forgotPin() {
        // For demo purposes, clear the session and go to login
        Toast.makeText(
            this,
            "Session cleared. Please login again.",
            Toast.LENGTH_LONG
        ).show()

        lifecycleScope.launch {
            authManager.logout()
        }
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
        enterButton.isEnabled = !loading
        pinField.isEnabled = !loading
    }
}