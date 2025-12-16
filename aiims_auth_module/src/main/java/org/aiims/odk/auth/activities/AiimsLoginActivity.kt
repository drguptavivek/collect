package org.aiims.odk.auth.activities

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.aiims.odk.auth.api.AuthResult
import org.aiims.odk.auth.managers.AiimsAuthManager

/**
 * AIIMS Login Activity
 * Handles user authentication with email, password, and API URL
 */
class AiimsLoginActivity : AppCompatActivity() {

    private lateinit var authManager: AiimsAuthManager
    private lateinit var emailField: EditText
    private lateinit var passwordField: EditText
    private lateinit var urlField: EditText
    private lateinit var loginButton: Button
    private lateinit var progressBar: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize auth manager
        authManager = AiimsAuthManager.getInstance(this)

        // Check authentication state
        lifecycleScope.launch {
            authManager.authState.collect { state ->
                when (state) {
                    org.aiims.odk.auth.managers.AuthState.LOGGED_IN -> {
                        // User is already logged in, ask for PIN
                        val intent = Intent()
                        intent.setClass(this@AiimsLoginActivity, org.aiims.odk.auth.activities.PinEntryActivity::class.java)
                        startActivity(intent)
                        finish()
                    }
                    org.aiims.odk.auth.managers.AuthState.REQUIRES_PIN -> {
                        // User logged in but needs to set up PIN
                        Toast.makeText(this@AiimsLoginActivity, "Please set up your PIN", Toast.LENGTH_SHORT).show()
                        val intent = Intent()
                        intent.setClass(this@AiimsLoginActivity, org.aiims.odk.auth.activities.SetupPinActivity::class.java)
                        startActivity(intent)
                        finish()
                    }
                    org.aiims.odk.auth.managers.AuthState.LOGGED_OUT -> {
                        // Show login screen (default behavior)
                    }
                    else -> {
                        // Show login screen (default behavior)
                    }
                }
            }
        }

        // Create layout
        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(50, 100, 50, 50)
        }

        // AIIMS Logo
        val logoImageView = android.widget.ImageView(this).apply {
            setImageResource(org.aiims.odk.auth.R.drawable.aiims_logo)
            adjustViewBounds = true
            setPadding(0, 0, 0, 40)
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = android.view.Gravity.CENTER
            }
        }

        // Title
        val title = android.widget.TextView(this).apply {
            text = "AIIMS ODK Collect"
            textSize = 28f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, 60)
            gravity = android.view.Gravity.CENTER
        }

        // Subtitle
        val subtitle = android.widget.TextView(this).apply {
            text = "Sign in to your account"
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



        // Email
        val emailHint = android.widget.TextView(this).apply {
            text = "Email:"
            textSize = 16f
            setPadding(0, 20, 0, 8)
        }

        emailField = EditText(this).apply {
            hint = "Enter your email"
            inputType = android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        }

        // Password
        val passwordHint = android.widget.TextView(this).apply {
            text = "Password:"
            textSize = 16f
            setPadding(0, 20, 0, 8)
        }

        passwordField = EditText(this).apply {
            hint = "Enter your password"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }

        // Login Button
        loginButton = Button(this).apply {
            text = "Login"
            textSize = 18f
            setPadding(0, 30, 0, 30)
            setOnClickListener {
                attemptLogin()
            }
        }


        // API URL
        val urlHint = android.widget.TextView(this).apply {
            text = "Server URL:"
            textSize = 16f
            setPadding(0, 10, 0, 8)
        }

        urlField = EditText(this).apply {
            hint = "http://localhost:5175/api/"
            inputType = android.text.InputType.TYPE_TEXT_VARIATION_URI
            setText("http://localhost:5175/api/") // Default URL
        }

        // Add all views to layout
        layout.addView(logoImageView)
        layout.addView(title)
        layout.addView(subtitle)
        layout.addView(emailHint)
        layout.addView(emailField)
        layout.addView(passwordHint)
        layout.addView(passwordField)
        layout.addView(loginButton)
        layout.addView(urlHint)
        layout.addView(urlField)
        layout.addView(progressBar, 0) // Insert progress bar at the beginning

        setContentView(layout)
    }

    private fun attemptLogin() {
        val email = emailField.text.toString().trim()
        val password = passwordField.text.toString()
        val apiUrl = urlField.text.toString().trim()

        // Basic validation
        when {
            email.isEmpty() -> {
                emailField.error = "Email is required"
                emailField.requestFocus()
                return
            }
            password.isEmpty() -> {
                passwordField.error = "Password is required"
                passwordField.requestFocus()
                return
            }
            apiUrl.isEmpty() -> {
                urlField.error = "Server URL is required"
                urlField.requestFocus()
                return
            }
        }

        // Show loading state
        setLoading(true)

        // Perform authentication
        lifecycleScope.launch {
            try {
                val result = authManager.login(email, password, apiUrl)

                setLoading(false)

                when (result) {
                    is AuthResult.Success -> {
                        Toast.makeText(
                            this@AiimsLoginActivity,
                            "Login successful! Welcome ${result.user.name}",
                            Toast.LENGTH_SHORT
                        ).show()

                        // Launch main ODK activity
                        val intent = Intent()
                        intent.setClassName("org.odk.collect.android", "org.odk.collect.android.mainmenu.MainMenuActivity")
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        startActivity(intent)
                        finish()
                    }
                    is AuthResult.Error -> {
                        Toast.makeText(
                            this@AiimsLoginActivity,
                            "Login failed: ${result.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    is AuthResult.Canceled -> {
                        Toast.makeText(
                            this@AiimsLoginActivity,
                            "Login cancelled",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    is AuthResult.RequiresPin -> {
                        // Navigate to PIN setup
                        val intent = Intent()
                        intent.setClass(this@AiimsLoginActivity, org.aiims.odk.auth.activities.SetupPinActivity::class.java)
                        intent.putExtra("authToken", result.token)
                        intent.putExtra("expiresAt", result.expiresAt)
                        startActivity(intent)
                        finish()
                    }
                }
            } catch (e: Exception) {
                setLoading(false)
                Toast.makeText(
                    this@AiimsLoginActivity,
                    "Network error: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun setLoading(loading: Boolean) {
        progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        loginButton.isEnabled = !loading
        emailField.isEnabled = !loading
        passwordField.isEnabled = !loading
        urlField.isEnabled = !loading
    }

    private fun navigateToMain() {
        // Launch main ODK activity
        val intent = Intent()
        intent.setClassName("org.odk.collect.android", "org.odk.collect.android.mainmenu.MainMenuActivity")
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}