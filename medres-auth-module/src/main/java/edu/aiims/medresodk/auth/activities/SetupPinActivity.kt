package edu.aiims.medresodk.auth.activities

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import edu.aiims.medresodk.auth.injection.MedresAuthDependencyComponentProvider
import edu.aiims.medresodk.auth.utils.PinManager
import javax.inject.Inject

/**
 * PIN Setup Activity
 * Required after initial login for two-factor authentication
 */
class SetupPinActivity : MedresBaseActivity() {

    @Inject
    lateinit var pinManager: PinManager

    override fun injectDependencies() {
        (application as MedresAuthDependencyComponentProvider).medresAuthDependencyComponent.inject(this)
    }

    private lateinit var pinField: android.widget.EditText
    private lateinit var confirmPinField: android.widget.EditText
    private lateinit var setupButton: com.google.android.material.button.MaterialButton
    private lateinit var progressBar: ProgressBar
    private lateinit var userTextView: TextView
    private lateinit var timeWarningCard: com.google.android.material.card.MaterialCardView
    private lateinit var timeWarningText: TextView

    // Store the authentication token from login
    private var authToken: String = ""
    private var expiresAt: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(edu.aiims.medresodk.auth.R.layout.activity_setup_pin)

        // Get token data from intent
        authToken = intent.getStringExtra("auth_token") ?: ""
        expiresAt = intent.getStringExtra("expires_at") ?: ""

        // Initialize views
        progressBar = findViewById(edu.aiims.medresodk.auth.R.id.progress_bar)
        userTextView = findViewById(edu.aiims.medresodk.auth.R.id.user_text_view)
        timeWarningCard = findViewById(edu.aiims.medresodk.auth.R.id.time_warning_card)
        timeWarningText = findViewById(edu.aiims.medresodk.auth.R.id.time_warning_text)
        pinField = findViewById(edu.aiims.medresodk.auth.R.id.pin_field)
        confirmPinField = findViewById(edu.aiims.medresodk.auth.R.id.confirm_pin_field)
        setupButton = findViewById(edu.aiims.medresodk.auth.R.id.setup_button)

        // Set up button click listener
        setupButton.setOnClickListener {
            attemptPinSetup()
        }

        // Load user data
        loadUserData()

        // Show time warning if device time differs from server time
        observeTimeDifference()

        // Setup PIN box logic
        setupPinBoxLogic()
    }

    private fun observeTimeDifference() {
        lifecycleScope.launch {
            authManager.serverTimeDifferenceMs.collect { diffMs ->
                if (kotlin.math.abs(diffMs) > 30 * 60 * 1000L) { // > 30 minutes difference
                    val hours = kotlin.math.abs(diffMs) / (60 * 60 * 1000)
                    val minutes = (kotlin.math.abs(diffMs) / (60 * 1000)) % 60

                    val diffStr = if (hours > 0) {
                        "${hours}h ${minutes}m"
                    } else {
                        "${minutes}m"
                    }

                    // diffMs > 0 means device time is ahead of server time
                    // diffMs < 0 means device time is behind server time
                    val direction = if (diffMs > 0) "ahead of" else "behind"
                    timeWarningText.text = "⚠️ Device time is $diffStr $direction server time. Please Correct it to avoid auto-logout"
                    timeWarningCard.visibility = View.VISIBLE
                } else {
                    timeWarningCard.visibility = View.GONE
                }
            }
        }
    }

    private fun setupPinBoxLogic() {
        val imm = getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager

        // Setup for main PIN field
        val pinBoxesContainer = findViewById<View>(edu.aiims.medresodk.auth.R.id.pin_boxes_container)
        val pinBoxes = arrayOf<TextView>(
            findViewById(edu.aiims.medresodk.auth.R.id.pin_box_1),
            findViewById(edu.aiims.medresodk.auth.R.id.pin_box_2),
            findViewById(edu.aiims.medresodk.auth.R.id.pin_box_3),
            findViewById(edu.aiims.medresodk.auth.R.id.pin_box_4)
        )

        val pinFocusRequester = View.OnClickListener {
            pinField.requestFocus()
            imm.showSoftInput(pinField, 0)
        }
        pinBoxesContainer.setOnClickListener(pinFocusRequester)
        pinBoxes.forEach { it.setOnClickListener(pinFocusRequester) }

        pinField.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val pin = s?.toString() ?: ""
                updateBoxes(pin, pinBoxes)
                if (pin.length == 4) {
                    confirmPinField.requestFocus()
                    imm.showSoftInput(confirmPinField, 0)
                }
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        // Setup for confirm PIN field
        val confirmPinBoxesContainer = findViewById<View>(edu.aiims.medresodk.auth.R.id.confirm_pin_boxes_container)
        val confirmPinBoxes = arrayOf<TextView>(
            findViewById(edu.aiims.medresodk.auth.R.id.confirm_pin_box_1),
            findViewById(edu.aiims.medresodk.auth.R.id.confirm_pin_box_2),
            findViewById(edu.aiims.medresodk.auth.R.id.confirm_pin_box_3),
            findViewById(edu.aiims.medresodk.auth.R.id.confirm_pin_box_4)
        )

        val confirmFocusRequester = View.OnClickListener {
            confirmPinField.requestFocus()
            imm.showSoftInput(confirmPinField, 0)
        }
        confirmPinBoxesContainer.setOnClickListener(confirmFocusRequester)
        confirmPinBoxes.forEach { it.setOnClickListener(confirmFocusRequester) }

        confirmPinField.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val pin = s?.toString() ?: ""
                updateBoxes(pin, confirmPinBoxes)
                if (pin.length == 4) {
                    // Stay here or submit
                }
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        // Initial focus
        pinField.postDelayed({ pinFocusRequester.onClick(null) }, 300)
    }

    private fun updateBoxes(pin: String, boxes: Array<TextView>) {
        for (i in 0 until 4) {
            if (i < pin.length) {
                boxes[i].text = "•"
            } else {
                boxes[i].text = ""
            }
            boxes[i].isActivated = (i == pin.length)
        }
    }

    private fun loadUserData() {
        lifecycleScope.launch {
            authManager.currentUser.collect { user ->
                user?.let {
                    userTextView.text = getString(edu.aiims.medresodk.auth.R.string.medres_welcome_user, it.username)
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
                Toast.makeText(this, getString(edu.aiims.medresodk.auth.R.string.medres_error_pin_required), Toast.LENGTH_SHORT).show()
                pinField.requestFocus()
                return
            }
            pin.length != 4 -> {
                Toast.makeText(this, getString(edu.aiims.medresodk.auth.R.string.medres_error_pin_digits), Toast.LENGTH_SHORT).show()
                pinField.requestFocus()
                return
            }
            confirmPin.isEmpty() -> {
                Toast.makeText(this, getString(edu.aiims.medresodk.auth.R.string.medres_error_pin_confirm), Toast.LENGTH_SHORT).show()
                confirmPinField.requestFocus()
                return
            }
            pin != confirmPin -> {
                Toast.makeText(this, getString(edu.aiims.medresodk.auth.R.string.medres_error_pin_mismatch), Toast.LENGTH_SHORT).show()
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

            // PIN setup complete
            
             // Trigger background project details update
            launch {
                try {
                     // We run this async and don't block navigation, 
                     // or we can wait a bit if we want the user to see "Updating..."
                     // Ideally, just fire and forget or show a quick toast
                     authManager.fetchAndUpdateProjectDetails(this@SetupPinActivity)
                } catch (e: Exception) {
                    // Ignore errors, don't block login
                }
            }

            // Update auth state to LOGGED_IN now that PIN is set
            android.util.Log.d("SetupPinActivity", "PIN setup complete, setting state to LOGGED_IN")
            authManager.updateAuthState(edu.aiims.medresodk.auth.managers.AuthState.LOGGED_IN)

            Toast.makeText(
                this@SetupPinActivity,
                getString(edu.aiims.medresodk.auth.R.string.medres_pin_setup_success),
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

    override fun onBackPressed() {
        // Prevent bypass - user must complete PIN setup to use the app
        // Show dialog explaining that PIN is required
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(getString(edu.aiims.medresodk.auth.R.string.medres_pin_required_title))
            .setMessage(getString(edu.aiims.medresodk.auth.R.string.medres_pin_required_message))
            .setPositiveButton(getString(edu.aiims.medresodk.auth.R.string.medres_button_ok), null)
            .setCancelable(false)
            .show()
    }
}
