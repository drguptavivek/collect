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
 * Change PIN Activity
 * Allows users to change their existing 4-digit PIN
 */
class ChangePinActivity : AppCompatActivity() {

    private lateinit var authManager: AiimsAuthManager
    private lateinit var pinManager: PinManager
    private lateinit var currentPinField: EditText
    private lateinit var newPinField: EditText
    private lateinit var confirmPinField: EditText
    private lateinit var changeButton: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var userTextView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize auth manager and pin manager
        authManager = AiimsAuthManager.getInstance(this)
        pinManager = PinManager.getInstance(this)

        // Create layout
        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(50, 80, 50, 50)
        }

        // Title
        val title = TextView(this).apply {
            text = "Change PIN"
            textSize = 28f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, 20)
            gravity = android.view.Gravity.CENTER
        }

        // Subtitle
        val subtitle = TextView(this).apply {
            text = "Update your 4-digit PIN for quick access"
            textSize = 16f
            setTextColor(android.graphics.Color.GRAY)
            setPadding(0, 0, 0, 20)
            gravity = android.view.Gravity.CENTER
        }

        // User info
        userTextView = TextView(this).apply {
            text = "Welcome"
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

        // Current PIN field
        val currentPinHint = TextView(this).apply {
            text = "Current PIN:"
            textSize = 16f
            setPadding(0, 20, 0, 8)
        }

        currentPinField = EditText(this).apply {
            hint = "••••"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
            // Set max length via filters
            filters = arrayOf(android.text.InputFilter.LengthFilter(4))
        }

        // New PIN field
        val newPinHint = TextView(this).apply {
            text = "New PIN (4 digits):"
            textSize = 16f
            setPadding(0, 20, 0, 8)
        }

        newPinField = EditText(this).apply {
            hint = "1234"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
            // Set max length via filters
            filters = arrayOf(android.text.InputFilter.LengthFilter(4))
        }

        // Confirm new PIN field
        val confirmPinHint = TextView(this).apply {
            text = "Confirm New PIN:"
            textSize = 16f
            setPadding(0, 20, 0, 8)
        }

        confirmPinField = EditText(this).apply {
            hint = "1234"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
            // Set max length via filters
            filters = arrayOf(android.text.InputFilter.LengthFilter(4))
        }

        // Change PIN Button
        changeButton = Button(this).apply {
            text = "Change PIN"
            textSize = 18f
            setPadding(0, 30, 0, 30)
            setOnClickListener {
                attemptChangePin()
            }
        }

        // Cancel Button
        val cancelButton = Button(this).apply {
            text = "Cancel"
            textSize = 16f
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            setTextColor(android.graphics.Color.GRAY)
            setPadding(0, 10, 0, 10)
            setOnClickListener {
                finish()
            }
        }

        // Add all views to layout
        layout.addView(title)
        layout.addView(subtitle)
        layout.addView(userTextView)
        layout.addView(progressBar)
        layout.addView(currentPinHint)
        layout.addView(currentPinField)
        layout.addView(newPinHint)
        layout.addView(newPinField)
        layout.addView(confirmPinHint)
        layout.addView(confirmPinField)
        layout.addView(changeButton)
        layout.addView(cancelButton)

        setContentView(layout)

        // Load user data
        loadUserData()

        // Focus on current PIN field
        currentPinField.requestFocus()
    }

    private fun loadUserData() {
        lifecycleScope.launch {
            authManager.currentUser.collect { user ->
                user?.let {
                    val welcomeText = if (it.name.isNotEmpty()) {
                        "Hello,\n${it.name}"
                    } else {
                        "Hello,\n${it.email}"
                    }
                    userTextView.text = welcomeText
                }
            }
        }
    }

    private fun attemptChangePin() {
        val currentPin = currentPinField.text.toString().trim()
        val newPin = newPinField.text.toString().trim()
        val confirmPin = confirmPinField.text.toString().trim()

        // Validation
        when {
            currentPin.isEmpty() -> {
                currentPinField.error = "Current PIN is required"
                currentPinField.requestFocus()
                return
            }
            currentPin.length != 4 -> {
                currentPinField.error = "PIN must be 4 digits"
                currentPinField.requestFocus()
                return
            }
            newPin.isEmpty() -> {
                newPinField.error = "New PIN is required"
                newPinField.requestFocus()
                return
            }
            newPin.length != 4 -> {
                newPinField.error = "PIN must be 4 digits"
                newPinField.requestFocus()
                return
            }
            confirmPin.isEmpty() -> {
                confirmPinField.error = "Please confirm your new PIN"
                confirmPinField.requestFocus()
                return
            }
            confirmPin != newPin -> {
                confirmPinField.error = "New PINs do not match"
                confirmPinField.requestFocus()
                return
            }
            newPin == currentPin -> {
                newPinField.error = "New PIN must be different from current PIN"
                newPinField.requestFocus()
                return
            }
        }

        // Show loading state
        setLoading(true)

        // Verify current PIN and change to new PIN
        lifecycleScope.launch {
            // Simulate network delay
            kotlinx.coroutines.delay(1000)

            setLoading(false)

            // Verify current PIN
            if (!pinManager.verifyPin(currentPin)) {
                Toast.makeText(
                    this@ChangePinActivity,
                    "Current PIN is incorrect",
                    Toast.LENGTH_LONG
                ).show()
                currentPinField.text.clear()
                currentPinField.requestFocus()
                return@launch
            }

            // Save the new PIN
            pinManager.savePin(newPin)

            Toast.makeText(
                this@ChangePinActivity,
                "PIN changed successfully!",
                Toast.LENGTH_SHORT
            ).show()

            // Return to AuthSettings
            finish()
        }
    }

    
    private fun setLoading(loading: Boolean) {
        progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        changeButton.isEnabled = !loading
        currentPinField.isEnabled = !loading
        newPinField.isEnabled = !loading
        confirmPinField.isEnabled = !loading
    }
}