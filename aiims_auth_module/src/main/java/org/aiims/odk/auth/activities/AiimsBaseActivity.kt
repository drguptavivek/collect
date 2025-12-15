package org.aiims.odk.auth.activities

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import org.aiims.odk.auth.managers.AiimsAuthManager

/**
 * Placeholder base activity for AIIMS authentication
 */
abstract class AiimsBaseActivity : AppCompatActivity() {

    protected lateinit var authManager: AiimsAuthManager
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize auth manager
        authManager = AiimsAuthManager.getInstance(this)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressed()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}