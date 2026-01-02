package edu.aiims.medresodk.auth.activities

import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import edu.aiims.medresodk.auth.injection.MedresAuthDependencyComponentProvider
import edu.aiims.medresodk.auth.managers.MedresAuthManager
import edu.aiims.medresodk.auth.utils.PinManager
import javax.inject.Inject

/**
 * Change PIN Activity
 * Allows users to change their existing 4-digit PIN
 */
class ChangePinActivity : MedresBaseActivity() {

    @Inject
    lateinit var pinManager: PinManager

    override fun injectDependencies() {
        (application as MedresAuthDependencyComponentProvider).medresAuthDependencyComponent.inject(this)
    }

    private lateinit var currentPinField: android.widget.EditText
    private lateinit var newPinField: android.widget.EditText
    private lateinit var confirmPinField: android.widget.EditText
    private lateinit var changeButton: com.google.android.material.button.MaterialButton
    private lateinit var cancelButton: com.google.android.material.button.MaterialButton
    private lateinit var progressBar: ProgressBar
    private lateinit var userTextView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(edu.aiims.medresodk.auth.R.layout.activity_change_pin)

        // Initialize views
        progressBar = findViewById(edu.aiims.medresodk.auth.R.id.progress_bar)
        userTextView = findViewById(edu.aiims.medresodk.auth.R.id.user_text_view)
        currentPinField = findViewById(edu.aiims.medresodk.auth.R.id.current_pin_field)
        newPinField = findViewById(edu.aiims.medresodk.auth.R.id.new_pin_field)
        confirmPinField = findViewById(edu.aiims.medresodk.auth.R.id.confirm_new_pin_field)
        changeButton = findViewById(edu.aiims.medresodk.auth.R.id.change_button)
        cancelButton = findViewById(edu.aiims.medresodk.auth.R.id.cancel_button)

        // Set up button click listeners
        changeButton.setOnClickListener {
            attemptChangePin()
        }
        
        cancelButton.setOnClickListener {
            finish()
        }

        // Load user data
        loadUserData()

        // Setup PIN box logic
        setupPinBoxLogic()
    }

    private fun setupPinBoxLogic() {
        val imm = getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager

        // Current PIN
        val currentBoxesContainer = findViewById<View>(edu.aiims.medresodk.auth.R.id.current_pin_boxes_container)
        val currentBoxes = arrayOf<TextView>(
            findViewById(edu.aiims.medresodk.auth.R.id.current_pin_box_1),
            findViewById(edu.aiims.medresodk.auth.R.id.current_pin_box_2),
            findViewById(edu.aiims.medresodk.auth.R.id.current_pin_box_3),
            findViewById(edu.aiims.medresodk.auth.R.id.current_pin_box_4)
        )
        val currentFocusRequester = View.OnClickListener {
            currentPinField.requestFocus()
            imm.showSoftInput(currentPinField, 0)
        }
        currentBoxesContainer.setOnClickListener(currentFocusRequester)
        currentBoxes.forEach { it.setOnClickListener(currentFocusRequester) }
        currentPinField.addTextChangedListener(createWatcher(currentBoxes) {
            newPinField.requestFocus()
            imm.showSoftInput(newPinField, 0)
        })

        // New PIN
        val newBoxesContainer = findViewById<View>(edu.aiims.medresodk.auth.R.id.new_pin_boxes_container)
        val newBoxes = arrayOf<TextView>(
            findViewById(edu.aiims.medresodk.auth.R.id.new_pin_box_1),
            findViewById(edu.aiims.medresodk.auth.R.id.new_pin_box_2),
            findViewById(edu.aiims.medresodk.auth.R.id.new_pin_box_3),
            findViewById(edu.aiims.medresodk.auth.R.id.new_pin_box_4)
        )
        val newFocusRequester = View.OnClickListener {
            newPinField.requestFocus()
            imm.showSoftInput(newPinField, 0)
        }
        newBoxesContainer.setOnClickListener(newFocusRequester)
        newBoxes.forEach { it.setOnClickListener(newFocusRequester) }
        newPinField.addTextChangedListener(createWatcher(newBoxes) {
            confirmPinField.requestFocus()
            imm.showSoftInput(confirmPinField, 0)
        })

        // Confirm PIN
        val confirmBoxesContainer = findViewById<View>(edu.aiims.medresodk.auth.R.id.confirm_new_pin_boxes_container)
        val confirmBoxes = arrayOf<TextView>(
            findViewById(edu.aiims.medresodk.auth.R.id.confirm_new_pin_box_1),
            findViewById(edu.aiims.medresodk.auth.R.id.confirm_new_pin_box_2),
            findViewById(edu.aiims.medresodk.auth.R.id.confirm_new_pin_box_3),
            findViewById(edu.aiims.medresodk.auth.R.id.confirm_new_pin_box_4)
        )
        val confirmFocusRequester = View.OnClickListener {
            confirmPinField.requestFocus()
            imm.showSoftInput(confirmPinField, 0)
        }
        confirmBoxesContainer.setOnClickListener(confirmFocusRequester)
        confirmBoxes.forEach { it.setOnClickListener(confirmFocusRequester) }
        confirmPinField.addTextChangedListener(createWatcher(confirmBoxes) {
            // End of chain
        })

        // Initial focus
        currentPinField.postDelayed({ currentFocusRequester.onClick(null) }, 300)
    }

    private fun createWatcher(boxes: Array<TextView>, onComplete: () -> Unit) = object : android.text.TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            val pin = s?.toString() ?: ""
            updateBoxes(pin, boxes)
            if (pin.length == 4) {
                onComplete()
            }
        }
        override fun afterTextChanged(s: android.text.Editable?) {}
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

    private fun attemptChangePin() {
        val currentPin = currentPinField.text.toString().trim()
        val newPin = newPinField.text.toString().trim()
        val confirmPin = confirmPinField.text.toString().trim()

        // Validation
        when {
            currentPin.isEmpty() -> {
                Toast.makeText(this, getString(edu.aiims.medresodk.auth.R.string.medres_error_pin_required), Toast.LENGTH_SHORT).show()
                currentPinField.requestFocus()
                return
            }
            currentPin.length != 4 -> {
                Toast.makeText(this, getString(edu.aiims.medresodk.auth.R.string.medres_error_pin_digits), Toast.LENGTH_SHORT).show()
                currentPinField.requestFocus()
                return
            }
            newPin.isEmpty() -> {
                Toast.makeText(this, getString(edu.aiims.medresodk.auth.R.string.medres_error_pin_required), Toast.LENGTH_SHORT).show()
                newPinField.requestFocus()
                return
            }
            newPin.length != 4 -> {
                Toast.makeText(this, getString(edu.aiims.medresodk.auth.R.string.medres_error_pin_digits), Toast.LENGTH_SHORT).show()
                newPinField.requestFocus()
                return
            }
            confirmPin.isEmpty() -> {
                Toast.makeText(this, getString(edu.aiims.medresodk.auth.R.string.medres_error_pin_confirm), Toast.LENGTH_SHORT).show()
                confirmPinField.requestFocus()
                return
            }
            confirmPin != newPin -> {
                Toast.makeText(this, getString(edu.aiims.medresodk.auth.R.string.medres_error_pin_mismatch), Toast.LENGTH_SHORT).show()
                confirmPinField.requestFocus()
                return
            }
            newPin == currentPin -> {
                Toast.makeText(this, getString(edu.aiims.medresodk.auth.R.string.medres_error_pin_different), Toast.LENGTH_SHORT).show()
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
                    getString(edu.aiims.medresodk.auth.R.string.medres_error_pin_incorrect),
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
                getString(edu.aiims.medresodk.auth.R.string.medres_pin_changed_success),
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
