package org.aiims.odk.auth.activities

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.aiims.odk.auth.managers.AiimsAuthManager
import org.aiims.odk.auth.bridge.AiimsFeatureFlag
import javax.inject.Inject

/**
 * Base activity for all AIIMS authentication activities.
 *
 * Provides common functionality including:
 * - Theme management
 * - Progress indicator handling
 * - Error message display
 * - Authentication state observation
 * - Lifecycle management
 */
abstract class AiimsBaseActivity : AppCompatActivity() {

    @Inject
    lateinit var authManager: AiimsAuthManager

    private var isShowingProgress = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Apply theme
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)

        // Inject dependencies
        (application as? dagger.hilt.android.HiltAndroidApplication)?.let {
            // Dependencies are automatically injected via @Inject fields
        }

        // Observe authentication state
        observeAuthState()
    }

    /**
     * Observe authentication state changes.
     */
    private fun observeAuthState() {
        lifecycleScope.launch {
            authManager.authState.collect { state ->
                onAuthStateChanged(state)
            }
        }

        lifecycleScope.launch {
            authManager.isLoading.collect { loading ->
                showProgress(loading)
            }
        }

        lifecycleScope.launch {
            authManager.errorMessage.collect { error ->
                error?.let { showErrorMessage(it) }
            }
        }
    }

    /**
     * Called when authentication state changes.
     * Override in subclasses to handle specific state changes.
     */
    protected open fun onAuthStateChanged(state: org.aiims.odk.auth.managers.AuthState) {
        // Default implementation does nothing
        // Override in subclasses as needed
    }

    /**
     * Show/hide progress indicator.
     */
    protected open fun showProgress(show: Boolean) {
        isShowingProgress = show
        // Override in subclasses to show actual progress UI
    }

    /**
     * Show error message to user.
     */
    protected open fun showErrorMessage(message: String) {
        // Override in subclasses to show actual error UI
    }

    /**
     * Check if AIIMS authentication is enabled.
     */
    protected fun isAiimsAuthEnabled(): Boolean {
        return AiimsFeatureFlag.isEnabled(this)
    }

    /**
     * Check if debug mode is enabled.
     */
    protected fun isDebugEnabled(): Boolean {
        return AiimsFeatureFlag.isDebugEnabled(this)
    }

    /**
     * Enable/disable views during loading.
     */
    protected fun setViewsEnabled(enabled: Boolean, vararg views: View) {
        views.forEach { view ->
            view.isEnabled = enabled && !isShowingProgress
        }
    }

    /**
     * Set up action bar with back button.
     */
    protected fun setupActionBar(title: String?, showBackButton: Boolean = true) {
        supportActionBar?.apply {
            this.title = title
            setDisplayHomeAsUpEnabled(showBackButton)
            setDisplayShowHomeEnabled(showBackButton)
        }
    }

    /**
     * Handle home button press (back navigation).
     */
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressed()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    /**
     * Get string resource safely.
     */
    protected fun safeGetString(resId: Int, default: String = ""): String {
        return try {
            getString(resId)
        } catch (e: Exception) {
            default
        }
    }

    /**
     * Show keyboard on a view.
     */
    protected fun showKeyboard(view: View) {
        view.requestFocus()
        val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        imm.showSoftInput(view, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
    }

    /**
     * Hide keyboard.
     */
    protected fun hideKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        imm.hideSoftInputFromWindow(currentFocus?.windowToken, 0)
    }

    /**
     * Log debug message if debug mode is enabled.
     */
    protected fun debugLog(tag: String, message: String) {
        if (isDebugEnabled()) {
            android.util.Log.d(tag, message)
        }
    }

    /**
     * Check if activity should be finished on auth state change.
     */
    protected fun shouldFinishOnAuthChange(): Boolean {
        // Default behavior: finish activity on successful authentication
        // Override in subclasses for different behavior
        return true
    }
}