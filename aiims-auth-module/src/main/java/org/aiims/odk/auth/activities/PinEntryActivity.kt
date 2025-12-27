package org.aiims.odk.auth.activities

import android.Manifest

import android.content.Intent

import android.content.pm.PackageManager

import android.os.Bundle

import android.view.View

import android.widget.Toast

import androidx.core.content.ContextCompat

import androidx.lifecycle.lifecycleScope

import kotlinx.coroutines.launch

import org.aiims.odk.auth.databinding.ActivityPinEntryBinding

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



    private lateinit var binding: ActivityPinEntryBinding



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



        // Initialize ViewBinding

        binding = ActivityPinEntryBinding.inflate(layoutInflater)

        setContentView(binding.root)



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



        // Setup UI

        binding.enterButton.setOnClickListener { attemptPinEntry() }

        binding.forgotPinButton.setOnClickListener { forgotPin() }

        binding.grantPermissionsButton.setOnClickListener { checkAndRequestPermissions(true) }



        // Update initial state

        updatePermissionStatusUI()



        // Load user data

        loadUserData()
        observeTokenExpiry()
        setupPinBoxLogic()
    }



    private fun setupPinBoxLogic() {
        // Set focus to the hidden field on container click or box click
        val focusRequester = View.OnClickListener {
            binding.pinField.requestFocus()
            val imm = getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.showSoftInput(binding.pinField, 0)
        }

        binding.pinBoxesContainer.setOnClickListener(focusRequester)
        binding.pinBox1.setOnClickListener(focusRequester)
        binding.pinBox2.setOnClickListener(focusRequester)
        binding.pinBox3.setOnClickListener(focusRequester)
        binding.pinBox4.setOnClickListener(focusRequester)

        binding.pinField.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val pin = s?.toString() ?: ""
                updatePinBoxes(pin)
                
                // Auto-submit if 4 digits entered
                if (pin.length == 4) {
                    attemptPinEntry()
                }
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        // Request initial focus
        binding.pinField.postDelayed({ focusRequester.onClick(null) }, 300)
    }

    private fun updatePinBoxes(pin: String) {
        val boxes = arrayOf(binding.pinBox1, binding.pinBox2, binding.pinBox3, binding.pinBox4)
        for (i in 0 until 4) {
            if (i < pin.length) {
                boxes[i].text = "•"
            } else {
                boxes[i].text = ""
            }
            
            // Highlight focused box (visual indicator for which square is current)
            boxes[i].isActivated = (i == pin.length)
        }
    }

    private fun observeTokenExpiry() {
        lifecycleScope.launch {
            authManager.isExpiringSoon.collect { expiringSoon ->
                // Update styling based on urgency
                val color = if (expiringSoon) {
                    binding.expiryReminderCard.setCardBackgroundColor(ContextCompat.getColor(this@PinEntryActivity, org.aiims.odk.auth.R.color.offline_background))
                    binding.expiryReminderCard.strokeColor = ContextCompat.getColor(this@PinEntryActivity, org.aiims.odk.auth.R.color.offline_border)
                    binding.expiryReminderText.text = getString(org.aiims.odk.auth.R.string.aiims_token_expiry_reminder)
                    ContextCompat.getColor(this@PinEntryActivity, org.aiims.odk.auth.R.color.offline_text)
                } else {
                    binding.expiryReminderCard.setCardBackgroundColor(ContextCompat.getColor(this@PinEntryActivity, org.aiims.odk.auth.R.color.aiims_surface_variant))
                    binding.expiryReminderCard.strokeColor = ContextCompat.getColor(this@PinEntryActivity, org.aiims.odk.auth.R.color.gray_medium)
                    binding.expiryReminderText.text = getString(org.aiims.odk.auth.R.string.aiims_token_status_title)
                    ContextCompat.getColor(this@PinEntryActivity, org.aiims.odk.auth.R.color.aiims_on_surface_variant)
                }
                
                binding.expiryReminderText.setTextColor(color)
                binding.expiryTimeText.setTextColor(color)
                binding.refreshTokenButton.setTextColor(color)
            }
        }

        lifecycleScope.launch {
            authManager.tokenExpiryTime.collect { expiryTime ->
                if (expiryTime > 0) {
                    binding.expiryReminderCard.visibility = View.VISIBLE
                    val remainingMs = expiryTime - System.currentTimeMillis()
                    if (remainingMs > 0) {
                        val hours = java.util.concurrent.TimeUnit.MILLISECONDS.toHours(remainingMs)
                        val minutes = java.util.concurrent.TimeUnit.MILLISECONDS.toMinutes(remainingMs) % 60
                        
                        val timeStr = if (hours > 0) {
                            "${hours}h ${minutes}m"
                        } else {
                            "${minutes}m"
                        }
                        binding.expiryTimeText.text = getString(org.aiims.odk.auth.R.string.aiims_token_expires_in, timeStr)
                    }
                }
            }
        }

        binding.refreshTokenButton.setOnClickListener {
            // Take user to login screen to refresh session
            val intent = Intent(this, AiimsLoginActivity::class.java)
            // Optional: Pass target project context if needed, though AuthManager handles it
            startActivity(intent)
        }
    }

    private fun loadUserData() {

        lifecycleScope.launch {

            authManager.currentUser.collect { user ->

                user?.let {

                    binding.userTextView.text = getString(org.aiims.odk.auth.R.string.aiims_welcome_back_user, it.username)

                }

            }

        }

    }



    private fun attemptPinEntry() {
        val pin = binding.pinField.text.toString().trim()

        // Validation
        when {
            pin.isEmpty() -> {
                Toast.makeText(this, getString(org.aiims.odk.auth.R.string.aiims_error_pin_required), Toast.LENGTH_SHORT).show()
                binding.pinField.requestFocus()
                return
            }
            pin.length != 4 -> {
                binding.pinField.requestFocus()
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



                    binding.pinField.text?.clear()

                    binding.pinField.requestFocus()

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

        intent.setClassName(this.packageName, "org.odk.collect.android.mainmenu.MainMenuActivity")

        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK

        startActivity(intent)

        finish()

    }



    private fun updatePermissionStatusUI() {

        // Location Status

        val hasLocation = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||

            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED



        if (hasLocation) {

            binding.locationStatusView.text = getString(org.aiims.odk.auth.R.string.aiims_location_access_granted)

            binding.locationStatusView.setTextColor(ContextCompat.getColor(this, org.aiims.odk.auth.R.color.aiims_success))

        } else {

            binding.locationStatusView.text = getString(org.aiims.odk.auth.R.string.aiims_location_access_required)

            binding.locationStatusView.setTextColor(ContextCompat.getColor(this, org.aiims.odk.auth.R.color.aiims_error))

        }



        // Notification Status (Android 13+)

        var hasNotif = true

        if (android.os.Build.VERSION.SDK_INT >= 33) {

            hasNotif = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

            if (hasNotif) {

                binding.notificationStatusView.text = getString(org.aiims.odk.auth.R.string.aiims_notifications_enabled)

                binding.notificationStatusView.setTextColor(ContextCompat.getColor(this, org.aiims.odk.auth.R.color.aiims_success))

            } else {

                binding.notificationStatusView.text = getString(org.aiims.odk.auth.R.string.aiims_notifications_disabled)

                binding.notificationStatusView.setTextColor(ContextCompat.getColor(this, org.aiims.odk.auth.R.color.aiims_error))

            }

        }



        // Show GRANT button if any permission is missing

        if (!hasLocation || !hasNotif) {

            binding.grantPermissionsButton.visibility = View.VISIBLE

        } else {

            binding.grantPermissionsButton.visibility = View.GONE

        }

    }



    private fun setLoading(loading: Boolean) {
        binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        binding.enterButton.isEnabled = !loading
        binding.pinField.isEnabled = !loading
    }



    override fun onBackPressed() {

        // Prevent backing out of the PIN screen. Minimize the app instead.

        moveTaskToBack(true)

    }

}
