package org.aiims.odk.auth.activities

import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.aiims.odk.auth.injection.AiimsAuthDependencyComponentProvider
import org.aiims.odk.auth.managers.AiimsAuthManager
import org.aiims.odk.auth.utils.PinManager
import javax.inject.Inject

/**
 * Change PIN Activity
 * Allows users to change their existing 4-digit PIN
 */
class ChangePinActivity : AiimsBaseActivity() {

    @Inject
    lateinit var pinManager: PinManager

    override fun injectDependencies() {
        (application as AiimsAuthDependencyComponentProvider).aiimsAuthDependencyComponent.inject(this)
    }

    private lateinit var currentPinField: com.google.android.material.textfield.TextInputEditText
    private lateinit var newPinField: com.google.android.material.textfield.TextInputEditText
    private lateinit var confirmPinField: com.google.android.material.textfield.TextInputEditText
    private lateinit var changeButton: com.google.android.material.button.MaterialButton
    private lateinit var cancelButton: com.google.android.material.button.MaterialButton
    private lateinit var progressBar: ProgressBar
    private lateinit var userTextView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(org.aiims.odk.auth.R.layout.activity_change_pin)

        // Initialize views
        progressBar = findViewById(org.aiims.odk.auth.R.id.progress_bar)
        userTextView = findViewById(org.aiims.odk.auth.R.id.user_text_view)
        currentPinField = findViewById(org.aiims.odk.auth.R.id.current_pin_field)
        newPinField = findViewById(org.aiims.odk.auth.R.id.new_pin_field)
        confirmPinField = findViewById(org.aiims.odk.auth.R.id.confirm_new_pin_field)
        changeButton = findViewById(org.aiims.odk.auth.R.id.change_button)
        cancelButton = findViewById(org.aiims.odk.auth.R.id.cancel_button)

        // Set up button click listeners
        changeButton.setOnClickListener {
            attemptChangePin()
        }
        
        cancelButton.setOnClickListener {
            finish()
        }

        // Load user data
        loadUserData()

        // Focus on current PIN field
        currentPinField.requestFocus()
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

    private fun attemptChangePin() {
        val currentPin = currentPinField.text.toString().trim()
        val newPin = newPinField.text.toString().trim()
        val confirmPin = confirmPinField.text.toString().trim()

        // Validation
        when {
            currentPin.isEmpty() -> {
                currentPinField.error = getString(org.aiims.odk.auth.R.string.aiims_error_pin_required)
                currentPinField.requestFocus()
                return
            }
            currentPin.length != 4 -> {
                currentPinField.error = getString(org.aiims.odk.auth.R.string.aiims_error_pin_digits)
                currentPinField.requestFocus()
                return
            }
            newPin.isEmpty() -> {
                newPinField.error = getString(org.aiims.odk.auth.R.string.aiims_error_pin_required)
                newPinField.requestFocus()
                return
            }
            newPin.length != 4 -> {
                newPinField.error = getString(org.aiims.odk.auth.R.string.aiims_error_pin_digits)
                newPinField.requestFocus()
                return
            }
            confirmPin.isEmpty() -> {
                confirmPinField.error = getString(org.aiims.odk.auth.R.string.aiims_error_pin_confirm)
                confirmPinField.requestFocus()
                return
            }
            confirmPin != newPin -> {
                confirmPinField.error = getString(org.aiims.odk.auth.R.string.aiims_error_pin_mismatch)
                confirmPinField.requestFocus()
                return
            }
            newPin == currentPin -> {
                newPinField.error = getString(org.aiims.odk.auth.R.string.aiims_error_pin_different)
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
                    getString(org.aiims.odk.auth.R.string.aiims_error_pin_incorrect),
                    Toast.LENGTH_LONG
                ).show()
                currentPinField.text?.clear()
                currentPinField.requestFocus()
                return@launch
            }

            // Save the new PIN
            pinManager.savePin(newPin)

            Toast.makeText(
                this@ChangePinActivity,
                getString(org.aiims.odk.auth.R.string.aiims_pin_changed_success),
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
