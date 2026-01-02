package org.odk.collect.android.injection.config

import edu.aiims.medresodk.auth.injection.MedresAuthDependencyModule
import edu.aiims.medresodk.auth.managers.ProjectCleaner

class CollectMedresAuthDependencyModule(private val appDependencyComponent: AppDependencyComponent) : MedresAuthDependencyModule() {

    override fun providesProjectCleaner(): ProjectCleaner {
        return appDependencyComponent.projectCleaner()
    }

    override fun providesSettingsProvider(): org.odk.collect.settings.SettingsProvider {
        return appDependencyComponent.settingsProvider()
    }
}
