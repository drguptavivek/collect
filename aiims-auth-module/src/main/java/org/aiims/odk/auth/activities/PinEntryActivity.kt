package org.aiims.odk.auth.activities

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.aiims.odk.auth.injection.AiimsAuthDependencyComponentProvider
import org.aiims.odk.auth.utils.PinManager
import javax.inject.Inject

/**
 * PIN Entry Activity
 * For returning users who have already set up a PIN
 */
class PinEntryActivity : AiimsBaseActivity() {

    @Inject
    lateinit var pinManager: PinManager

    private lateinit var pinField: EditText
    private lateinit var enterButton: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var userTextView: TextView
    private lateinit var locationStatusView: TextView
    private lateinit var notificationStatusView: TextView
    private lateinit var grantPermissionsButton: Button

    override fun onResume() {
        super.onResume()
        // Check permissions again on resume
        checkAndRequestPermissions()
        // Update UI status
        updatePermissionStatusUI()
    }

    override fun injectDependencies() {
        (application as AiimsAuthDependencyComponentProvider).aiimsAuthDependencyComponent.inject(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Check permissions for Telemetry/Notifications
        checkAndRequestPermissions()

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
            text = getString(org.aiims.odk.auth.R.string.aiims_pin)
            textSize = 28f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, 20)
            gravity = android.view.Gravity.CENTER
        }

        // Subtitle
        val subtitle = TextView(this).apply {
            text = getString(org.aiims.odk.auth.R.string.aiims_enter_pin_message)
            textSize = 16f
            setTextColor(android.graphics.Color.GRAY)
            setPadding(0, 0, 0, 20)
            gravity = android.view.Gravity.CENTER
        }

        // User info
        userTextView = TextView(this).apply {
            text = getString(org.aiims.odk.auth.R.string.aiims_welcome_back_user, "")
            textSize = 16f
            setTextColor(android.graphics.Color.DKGRAY)
            setPadding(0, 0, 0, 40)
            gravity = android.view.Gravity.CENTER
        }

        // Permission Status Views
        locationStatusView = TextView(this).apply {
            textSize = 14f
            gravity = android.view.Gravity.CENTER
            setPadding(0, 0, 0, 10)
        }

        notificationStatusView = TextView(this).apply {
            textSize = 14f
            gravity = android.view.Gravity.CENTER
            setPadding(0, 0, 0, 20)
            visibility = if (android.os.Build.VERSION.SDK_INT >= 33) View.VISIBLE else View.GONE
        }

        // Update initial state
        updatePermissionStatusUI()

        // Progress bar (initially hidden)
        progressBar = ProgressBar(this).apply {
            visibility = View.GONE
            setPadding(0, 0, 0, 20)
        }

        // Grant Permissions Button (initially hidden)
        grantPermissionsButton = Button(this).apply {
            text = getString(org.aiims.odk.auth.R.string.aiims_button_grant_permissions)
            textSize = 16f
            setBackgroundColor(android.graphics.Color.parseColor("#1976D2")) // Blue
            setTextColor(android.graphics.Color.WHITE)
            setPadding(20, 10, 20, 10)
            visibility = View.GONE
            setOnClickListener {
                checkAndRequestPermissions(true) // Force request
            }
        }

        // PIN field
        val pinHint = TextView(this).apply {
            text = getString(org.aiims.odk.auth.R.string.aiims_pin_hint)
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
            text = getString(org.aiims.odk.auth.R.string.aiims_button_verify)
            textSize = 18f
            setPadding(0, 30, 0, 30)
            setOnClickListener {
                attemptPinEntry()
            }
        }

        // Forgot PIN
        val forgotPinButton = Button(this).apply {
            text = getString(org.aiims.odk.auth.R.string.aiims_button_forgot_pin)
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
        layout.addView(locationStatusView)
        layout.addView(notificationStatusView)
        layout.addView(grantPermissionsButton)
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
                    userTextView.text = getString(org.aiims.odk.auth.R.string.aiims_welcome_back_user, it.username)
                }
            }
        }
    }

    private fun attemptPinEntry() {
        val pin = pinField.text.toString().trim()

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
                    getString(org.aiims.odk.auth.R.string.aiims_error_too_many_attempts),
                    Toast.LENGTH_LONG
                ).show()

                // Clear session AND PIN
                authManager.logoutDueToFailedPin()
                return@launch
            }

            if (pinManager.verifyPin(pin)) {
                Toast.makeText(
                    this@PinEntryActivity,
                    getString(org.aiims.odk.auth.R.string.aiims_pin_success),
                    Toast.LENGTH_SHORT
                ).show()

                // Trigger Telemetry (Success)
                lifecycleScope.launch {
                    authManager.submitTelemetry(getLastKnownLocation())
                }

                // Navigate to main app
                navigateToMain()
            } else {
                if (pinManager.isMaxAttemptsReached()) {
                    Toast.makeText(
                        this@PinEntryActivity,
                        getString(org.aiims.odk.auth.R.string.aiims_error_max_attempts_reached),
                        Toast.LENGTH_LONG
                    ).show()
                    authManager.logoutDueToFailedPin()
                } else {
                    val attemptsLeft = 3 - pinManager.getFailedAttempts()
                    Toast.makeText(
                        this@PinEntryActivity,
                        getString(org.aiims.odk.auth.R.string.aiims_error_incorrect_pin_attempts, attemptsLeft),
                        Toast.LENGTH_SHORT
                    ).show()

                    // Trigger Telemetry (Failed Attempt)
                    lifecycleScope.launch {
                        authManager.submitTelemetry(getLastKnownLocation())
                    }

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
            getString(org.aiims.odk.auth.R.string.aiims_session_expired),
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

    private fun updatePermissionStatusUI() {
        // Location Status
        val hasLocation = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

        if (hasLocation) {
            locationStatusView.text = getString(org.aiims.odk.auth.R.string.aiims_location_access_granted)
            locationStatusView.setTextColor(Color.parseColor("#2E7D32")) // Green
        } else {
            locationStatusView.text = getString(org.aiims.odk.auth.R.string.aiims_location_access_required)
            locationStatusView.setTextColor(Color.parseColor("#C62828")) // Red
        }

        // Notification Status (Android 13+)
        var hasNotif = true
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            hasNotif = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            if (hasNotif) {
                notificationStatusView.text = getString(org.aiims.odk.auth.R.string.aiims_notifications_enabled)
                notificationStatusView.setTextColor(Color.parseColor("#2E7D32")) // Green
            } else {
                notificationStatusView.text = getString(org.aiims.odk.auth.R.string.aiims_notifications_disabled)
                notificationStatusView.setTextColor(Color.parseColor("#C62828")) // Red
            }
        }

        // Show GRANT button if any permission is missing
        if (!hasLocation || !hasNotif) {
            grantPermissionsButton.visibility = View.VISIBLE
        } else {
            grantPermissionsButton.visibility = View.GONE
        }
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