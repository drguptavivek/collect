package org.aiims.odk.auth.utils

import android.app.Activity
import android.app.Application
import android.content.Intent
import org.aiims.odk.auth.activities.AiimsLoginActivity
import org.aiims.odk.auth.activities.AuthSettingsActivity
import org.aiims.odk.auth.activities.ChangePinActivity
import org.aiims.odk.auth.activities.PinEntryActivity
import org.aiims.odk.auth.activities.SetupPinActivity
import org.aiims.odk.auth.managers.AiimsAuthManager
import org.aiims.odk.auth.managers.AuthState

/**
 * Watches app foreground/background transitions and forces PIN entry when returning to the app.
 */
class AiimsAppLock(private val application: Application) : Application.ActivityLifecycleCallbacks {

    private var startedActivities = 0
    private var shouldRequirePin = false

    override fun onActivityStarted(activity: Activity) {
        startedActivities++

        if (!shouldRequirePin) return
        if (activity.isAuthFlowActivity()) {
            // Already on an auth screen; no need to launch PIN entry again.
            shouldRequirePin = false
            return
        }

        val authManager = AiimsAuthManager.getInstance(application)
        val pinManager = PinManager.getInstance(application)
        val authState = authManager.getCurrentAuthState()

        if (authState == AuthState.LOGGED_IN) {
            if (authManager.getIsSoftExpiry()) {
                shouldRequirePin = false
                val intent = Intent(application, AiimsLoginActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra("is_reauth", true)
                }
                application.startActivity(intent)
                return
            }

            if (pinManager.isPinSet()) {
                shouldRequirePin = false
                val intent = Intent(application, PinEntryActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                application.startActivity(intent)
            } else {
                shouldRequirePin = false
            }
        } else {
            shouldRequirePin = false
        }
    }

    override fun onActivityStopped(activity: Activity) {
        startedActivities--
        if (startedActivities <= 0) {
            // App moved to background
            shouldRequirePin = true
            startedActivities = 0
        }
    }

    // Unused lifecycle callbacks
    override fun onActivityCreated(activity: Activity, savedInstanceState: android.os.Bundle?) {}
    override fun onActivityResumed(activity: Activity) {}
    override fun onActivityPaused(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: android.os.Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {}

    private fun Activity.isAuthFlowActivity(): Boolean {
        return this is AiimsLoginActivity ||
            this is PinEntryActivity ||
            this is SetupPinActivity ||
            this is ChangePinActivity ||
            this is AuthSettingsActivity
    }
}
