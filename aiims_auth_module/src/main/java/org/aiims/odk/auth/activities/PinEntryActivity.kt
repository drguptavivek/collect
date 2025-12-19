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
import org.aiims.odk.auth.utils.PinManager

/**
 * PIN Entry Activity
 * For returning users who have already set up a PIN
 */
class PinEntryActivity : AppCompatActivity() {

    private lateinit var authManager: AiimsAuthManager
    private lateinit var pinManager: PinManager
    private lateinit var pinField: EditText
    private lateinit var enterButton: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var userTextView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize auth manager and pin manager
        authManager = AiimsAuthManager.getInstance(this)
        pinManager = PinManager.getInstance(this)

        // Check if PIN is set, if not go to login
        if (!pinManager.isPinSet()) {
            finish()
            val intent = Intent()
            intent.setClass(this@PinEntryActivity, org.aiims.odk.auth.activities.AiimsLoginActivity::class.java)
            startActivity(intent)
            return
        }

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
            setPadding(0, 0, 0, 20)
            gravity = android.view.Gravity.CENTER
        }

        // User info
        userTextView = TextView(this).apply {
            text = "Welcome back"
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
        layout.addView(userTextView)
        layout.addView(pinHint)
        layout.addView(pinField)
        layout.addView(enterButton)
        layout.addView(forgotPinButton)
        layout.addView(progressBar, 0) // Insert progress bar at the beginning

        setContentView(layout)

        // Load user data
        loadUserData()
    }

    private fun loadUserData() {
        lifecycleScope.launch {
            authManager.currentUser.collect { user ->
                user?.let {
                    val welcomeText = if (it.name.isNotEmpty()) {
                        "Welcome back,\n${it.name}"
                    } else {
                        "Welcome back,\n${it.email}"
                    }
                    userTextView.text = welcomeText
                }
            }
        }
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

        // Verify PIN against stored PIN
        lifecycleScope.launch {
            // Simulate network delay
            kotlinx.coroutines.delay(500)

            setLoading(false)

            if (pinManager.isMaxAttemptsReached()) {
                Toast.makeText(
                    this@PinEntryActivity,
                    "Too many failed attempts. Please login again.",
                    Toast.LENGTH_LONG
                ).show()

                // Clear session AND PIN
                authManager.logoutDueToFailedPin()
                return@launch
            }

            if (pinManager.verifyPin(pin)) {
                Toast.makeText(
                    this@PinEntryActivity,
                    "PIN verified successfully",
                    Toast.LENGTH_SHORT
                ).show()

                // Navigate to main app
                navigateToMain()
            } else {
                if (pinManager.isMaxAttemptsReached()) {
                    Toast.makeText(
                        this@PinEntryActivity,
                        "Max attempts reached. Logging out...",
                        Toast.LENGTH_LONG
                    ).show()
                    authManager.logoutDueToFailedPin()
                } else {
                    val attemptsLeft = 3 - pinManager.getFailedAttempts()
                    Toast.makeText(
                        this@PinEntryActivity,
                        "Incorrect PIN. $attemptsLeft attempts remaining.",
                        Toast.LENGTH_SHORT
                    ).show()
                    pinField.text.clear()
                    pinField.requestFocus()
                }
            }
        }
    }

    private fun forgotPin() {
        // Clear the session but preserve PIN
        Toast.makeText(
            this,
            "Session cleared. Please login again.",
            Toast.LENGTH_LONG
        ).show()

        authManager.logoutDueToFailedPin()
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

    override fun onBackPressed() {
        // Prevent backing out of the PIN screen. Minimize the app instead.
        moveTaskToBack(true)
    }
}