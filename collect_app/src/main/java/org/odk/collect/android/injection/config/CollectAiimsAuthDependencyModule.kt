package org.odk.collect.android.injection.config

import org.aiims.odk.auth.injection.AiimsAuthDependencyModule
import org.aiims.odk.auth.managers.ProjectCleaner

class CollectAiimsAuthDependencyModule(private val appDependencyComponent: AppDependencyComponent) : AiimsAuthDependencyModule() {

    override fun providesProjectCleaner(): ProjectCleaner {
        return appDependencyComponent.projectCleaner()
    }

    override fun providesSettingsProvider(): org.odk.collect.settings.SettingsProvider {
        return appDependencyComponent.settingsProvider()
    }
}
