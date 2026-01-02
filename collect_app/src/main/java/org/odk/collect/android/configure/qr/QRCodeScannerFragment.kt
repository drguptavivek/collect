package org.odk.collect.android.configure.qr

import android.content.Context
import org.odk.collect.analytics.Analytics
import org.odk.collect.android.activities.ActivityUtils
import org.odk.collect.android.analytics.AnalyticsEvents
import org.odk.collect.android.fragments.BarCodeScannerFragment
import org.odk.collect.android.injection.DaggerUtils
import org.odk.collect.android.mainmenu.MainMenuActivity
import org.odk.collect.android.projects.ProjectsDataService
import org.odk.collect.android.storage.StoragePathProvider
import org.odk.collect.androidshared.ui.ToastUtils.showLongToast
import org.odk.collect.androidshared.ui.ToastUtils.showShortToast
import org.odk.collect.androidshared.utils.CompressionUtils
import org.odk.collect.projects.ProjectConfigurationResult
import org.odk.collect.settings.ODKAppSettingsImporter
import org.odk.collect.settings.SettingsProvider
import org.odk.collect.settings.keys.ProjectKeys
import org.odk.collect.strings.R
import java.io.File
import javax.inject.Inject

class QRCodeScannerFragment : BarCodeScannerFragment() {

    @Inject
    lateinit var settingsImporter: ODKAppSettingsImporter

    @Inject
    lateinit var projectsDataService: ProjectsDataService

    @Inject
    lateinit var storagePathProvider: StoragePathProvider

    @Inject
    lateinit var settingsProvider: SettingsProvider

    override fun onAttach(context: Context) {
        super.onAttach(context)
        DaggerUtils.getComponent(context).inject(this)
    }

    override fun handleScanningResult(result: String) {
        val oldProjectName = projectsDataService.requireCurrentProject().name
        
        // Capture old settings for MEDRES logout-on-change detection
        val oldServerUrl = settingsProvider.getUnprotectedSettings().getString(ProjectKeys.KEY_SERVER_URL)
        val oldUsername = settingsProvider.getUnprotectedSettings().getString(ProjectKeys.KEY_USERNAME)

        try {
            val settingsImportingResult = settingsImporter.fromJSON(
                CompressionUtils.decompress(result),
                projectsDataService.requireCurrentProject()
            )

            when (settingsImportingResult) {
                ProjectConfigurationResult.SUCCESS -> {
                    Analytics.log(AnalyticsEvents.RECONFIGURE_PROJECT)

                    val newProjectName = projectsDataService.requireCurrentProject().name
                    if (newProjectName != oldProjectName) {
                        File(storagePathProvider.getProjectRootDirPath() + File.separator + oldProjectName).delete()
                        File(storagePathProvider.getProjectRootDirPath() + File.separator + newProjectName).createNewFile()
                    }

                    showLongToast(
                        getString(R.string.successfully_imported_settings)
                    )
                    
                    // Check if MEDRES auth is enabled
                    if (isMedresAuthEnabled()) {
                        // Check if critical settings changed - trigger logout if so
                        val newServerUrl = settingsProvider.getUnprotectedSettings().getString(ProjectKeys.KEY_SERVER_URL)
                        val newUsername = settingsProvider.getUnprotectedSettings().getString(ProjectKeys.KEY_USERNAME)
                        
                        if (oldServerUrl != newServerUrl || oldUsername != newUsername) {
                            // Clear MEDRES auth tokens on server/username change
                            clearMedresAuthTokens()
                            showLongToast("Configuration changed. Please login again.")
                        }
                        
                        // Just finish to return to MEDRES login
                        requireActivity().finish()
                    } else {
                        ActivityUtils.startActivityAndCloseAllOthers(
                            requireActivity(),
                            MainMenuActivity::class.java
                        )
                    }
                }

                ProjectConfigurationResult.INVALID_SETTINGS -> {
                    showLongToast(
                        getString(
                            R.string.invalid_qrcode
                        )
                    )
                    restartScanning()
                }

                ProjectConfigurationResult.GD_PROJECT -> {
                    showLongToast(
                        getString(R.string.settings_with_gd_protocol)
                    )
                    restartScanning()
                }
            }
        } catch (e: Exception) {
            showShortToast(getString(R.string.invalid_qrcode))
            restartScanning()
        }
    }

    private fun isMedresAuthEnabled(): Boolean {
        return try {
            val resId = resources.getIdentifier("medres_auth_enabled", "bool", requireContext().packageName)
            if (resId != 0) resources.getBoolean(resId) else false
        } catch (e: Exception) {
            false
        }
    }

    private fun clearMedresAuthTokens() {
        try {
            val prefs = requireContext().getSharedPreferences("medres_auth_prefs", Context.MODE_PRIVATE)
            prefs.edit().clear().apply()
        } catch (e: Exception) {
            // Ignore - tokens may not exist
        }
    }

    override fun isQrOnly(): Boolean {
        return true
    }
}

