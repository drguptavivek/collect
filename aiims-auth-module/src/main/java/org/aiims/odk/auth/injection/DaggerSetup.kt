package org.aiims.odk.auth.injection

import android.app.Application
import dagger.BindsInstance
import dagger.Component
import dagger.Module
import dagger.Provides
import org.aiims.odk.auth.activities.AiimsLoginActivity
import org.aiims.odk.auth.activities.AuthSettingsActivity
import org.aiims.odk.auth.activities.ChangePinActivity
import org.aiims.odk.auth.activities.PinEntryActivity
import org.aiims.odk.auth.activities.SetupPinActivity
import org.aiims.odk.auth.managers.AiimsAuthManager
import org.aiims.odk.auth.managers.ProjectCleaner
import org.aiims.odk.auth.storage.AiimsAuthStorage
import org.aiims.odk.auth.storage.AiimsAuthStorageImpl
import org.aiims.odk.auth.storage.AiimsSecureStorage
import org.aiims.odk.auth.storage.AiimsSecureStorageImpl
import org.aiims.odk.auth.utils.PinManager
import javax.inject.Singleton

interface AiimsAuthDependencyComponentProvider {
    val aiimsAuthDependencyComponent: AiimsAuthDependencyComponent
}

@Component(modules = [AiimsAuthDependencyModule::class])
@Singleton
interface AiimsAuthDependencyComponent {

    @Component.Builder
    interface Builder {
        @BindsInstance
        fun application(application: Application): Builder

        fun aiimsAuthDependencyModule(module: AiimsAuthDependencyModule): Builder

        fun build(): AiimsAuthDependencyComponent
    }

    fun inject(activity: AiimsLoginActivity)
    fun inject(activity: PinEntryActivity)
    fun inject(activity: SetupPinActivity)
    fun inject(activity: ChangePinActivity)
    fun inject(activity: AuthSettingsActivity)

    val authManager: AiimsAuthManager
    val pinManager: PinManager
}

@Module
open class AiimsAuthDependencyModule {

    @Provides
    open fun providesProjectCleaner(): ProjectCleaner {
        throw UnsupportedOperationException("This should be overridden by dependent application")
    }

    @Provides
    @Singleton
    open fun providesAiimsSecureStorage(application: Application): AiimsSecureStorage {
        return AiimsSecureStorageImpl.getInstance(application)
    }

    @Provides
    @Singleton
    open fun providesAiimsAuthStorage(application: Application): AiimsAuthStorage {
        return AiimsAuthStorageImpl.getInstance(application)
    }

    @Provides
    @Singleton
    open fun providesAiimsAuthManager(
        application: Application,
        projectCleaner: ProjectCleaner,
        pinManager: PinManager,
        authStorage: AiimsAuthStorage,
        secureStorage: AiimsSecureStorage
    ): AiimsAuthManager {
        return AiimsAuthManager(application, projectCleaner, pinManager, authStorage, secureStorage)
    }

    @Provides
    @Singleton
    open fun providesPinManager(application: Application): PinManager {
        return PinManager(application)
    }
}
