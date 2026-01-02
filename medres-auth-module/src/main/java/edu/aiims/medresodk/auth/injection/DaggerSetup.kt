package edu.aiims.medresodk.auth.injection

import android.app.Application
import dagger.BindsInstance
import dagger.Component
import dagger.Module
import dagger.Provides
import edu.aiims.medresodk.auth.activities.MedresLoginActivity
import edu.aiims.medresodk.auth.activities.AuthSettingsActivity
import edu.aiims.medresodk.auth.activities.ChangePinActivity
import edu.aiims.medresodk.auth.activities.PinEntryActivity
import edu.aiims.medresodk.auth.activities.SetupPinActivity
import edu.aiims.medresodk.auth.managers.MedresAuthManager
import edu.aiims.medresodk.auth.managers.ProjectCleaner
import edu.aiims.medresodk.auth.storage.MedresAuthStorage
import edu.aiims.medresodk.auth.storage.MedresAuthStorageImpl
import edu.aiims.medresodk.auth.storage.MedresSecureStorage
import edu.aiims.medresodk.auth.storage.MedresSecureStorageImpl
import edu.aiims.medresodk.auth.utils.PinManager
import javax.inject.Singleton

interface MedresAuthDependencyComponentProvider {
    val medresAuthDependencyComponent: MedresAuthDependencyComponent
}

@Component(modules = [MedresAuthDependencyModule::class])
@Singleton
interface MedresAuthDependencyComponent {

    @Component.Builder
    interface Builder {
        @BindsInstance
        fun application(application: Application): Builder

        fun medresAuthDependencyModule(module: MedresAuthDependencyModule): Builder

        fun build(): MedresAuthDependencyComponent
    }

    fun inject(activity: MedresLoginActivity)
    fun inject(activity: PinEntryActivity)
    fun inject(activity: SetupPinActivity)
    fun inject(activity: ChangePinActivity)
    fun inject(activity: AuthSettingsActivity)

    val authManager: MedresAuthManager
    val pinManager: PinManager
}

@Module
open class MedresAuthDependencyModule {

    @Provides
    open fun providesProjectCleaner(): ProjectCleaner {
        throw UnsupportedOperationException("This should be overridden by dependent application")
    }

    @Provides
    open fun providesSettingsProvider(): org.odk.collect.settings.SettingsProvider {
        throw UnsupportedOperationException("This should be overridden by dependent application")
    }

    @Provides
    @Singleton
    open fun providesMedresSecureStorage(application: Application): MedresSecureStorage {
        return MedresSecureStorageImpl.getInstance(application)
    }

    @Provides
    @Singleton
    open fun providesMedresAuthStorage(application: Application): MedresAuthStorage {
        return MedresAuthStorageImpl.getInstance(application)
    }

    @Provides
    @Singleton
    open fun providesMedresDatabase(application: Application): edu.aiims.medresodk.auth.storage.db.MedresDatabase {
        return androidx.room.Room.databaseBuilder(
            application,
            edu.aiims.medresodk.auth.storage.db.MedresDatabase::class.java,
            "medres_db"
        ).build()
    }

    @Provides
    open fun providesTelemetryDao(database: edu.aiims.medresodk.auth.storage.db.MedresDatabase): edu.aiims.medresodk.auth.storage.db.TelemetryDao {
        return database.telemetryDao()
    }

    @Provides
    @Singleton
    open fun providesMedresNetworkStateMonitor(application: Application): edu.aiims.medresodk.auth.utils.MedresNetworkStateMonitor {
        return edu.aiims.medresodk.auth.utils.MedresNetworkStateMonitorImpl(application)
    }

    @Provides
    @Singleton
    open fun providesMedresAuthManager(
        application: Application,
        projectCleaner: ProjectCleaner,
        pinManager: PinManager,
        authStorage: MedresAuthStorage,
        secureStorage: MedresSecureStorage,
        telemetryDao: edu.aiims.medresodk.auth.storage.db.TelemetryDao,
        networkStateMonitor: edu.aiims.medresodk.auth.utils.MedresNetworkStateMonitor
    ): MedresAuthManager {
        // IMPORTANT: Must remain wrapped in Lazy { ... } to prevent Dagger circular dependency (StackOverflowError).
        // This pattern MUST NOT be disturbed when writing tests or making code changes.
        return MedresAuthManager(application, { projectCleaner }, pinManager, authStorage, secureStorage, telemetryDao, networkStateMonitor)
    }

    @Provides
    @Singleton
    open fun providesPinManager(application: Application): PinManager {
        return PinManager(application)
    }
}
