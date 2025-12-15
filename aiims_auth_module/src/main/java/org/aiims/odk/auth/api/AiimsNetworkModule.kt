package org.aiims.odk.auth.api

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

/**
 * Dagger Hilt module for providing network-related dependencies.
 */
@Module
@InstallIn(SingletonComponent::class)
object AiimsNetworkModule {

    /**
     * Provide application context.
     */
    @Provides
    @Singleton
    fun provideApplicationContext(@ApplicationContext context: Context): Context = context

    /**
     * Provide coroutine scope for the application.
     */
    @Provides
    @Singleton
    fun provideCoroutineScope(): CoroutineScope {
        return CoroutineScope(SupervisorJob())
    }

    /**
     * Provide API client singleton.
     */
    @Provides
    @Singleton
    fun provideAiimsApiClient(
        @ApplicationContext context: Context,
        authStorage: org.aiims.odk.auth.storage.AiimsAuthStorage
    ): AiimsApiClient {
        return AiimsApiClient(context, authStorage)
    }

    /**
     * Provide API service interface.
     */
    @Provides
    @Singleton
    fun provideAiimsAuthApi(apiClient: AiimsApiClient): AiimsAuthApi {
        return apiClient.apiService
    }

    /**
     * Provide secure storage.
     */
    @Provides
    @Singleton
    fun provideAiimsSecureStorage(
        @ApplicationContext context: Context
    ): org.aiims.odk.auth.storage.AiimsSecureStorage {
        return org.aiims.odk.auth.storage.AiimsSecureStorage(context)
    }

    /**
     * Provide auth storage.
     */
    @Provides
    @Singleton
    fun provideAiimsAuthStorage(
        secureStorage: org.aiims.odk.auth.storage.AiimsSecureStorage
    ): org.aiims.odk.auth.storage.AiimsAuthStorage {
        return org.aiims.odk.auth.storage.AiimsAuthStorage(secureStorage)
    }

    /**
     * Provide security utilities.
     */
    @Provides
    @Singleton
    fun provideAiimsSecurityUtils(
        @ApplicationContext context: Context
    ): org.aiims.odk.auth.utils.AiimsSecurityUtils {
        return org.aiims.odk.auth.utils.AiimsSecurityUtils(context)
    }

    /**
     * Provide authentication manager.
     */
    @Provides
    @Singleton
    fun provideAiimsAuthManager(
        @ApplicationContext context: Context,
        apiClient: AiimsApiClient,
        authStorage: org.aiims.odk.auth.storage.AiimsAuthStorage,
        securityUtils: org.aiims.odk.auth.utils.AiimsSecurityUtils
    ): org.aiims.odk.auth.managers.AiimsAuthManager {
        return org.aiims.odk.auth.managers.AiimsAuthManager(context, apiClient, authStorage, securityUtils)
    }

    /**
     * Provide session manager.
     */
    @Provides
    @Singleton
    fun provideAiimsSessionManager(
        authManager: org.aiims.odk.auth.managers.AiimsAuthManager,
        authStorage: org.aiims.odk.auth.storage.AiimsAuthStorage,
        scope: CoroutineScope
    ): org.aiims.odk.auth.managers.AiimsSessionManager {
        return org.aiims.odk.auth.managers.AiimsSessionManager(authManager, authStorage, scope)
    }

    /**
     * Provide token manager.
     */
    @Provides
    @Singleton
    fun provideAiimsTokenManager(
        apiClient: AiimsApiClient,
        authStorage: org.aiims.odk.auth.storage.AiimsAuthStorage,
        scope: CoroutineScope
    ): org.aiims.odk.auth.managers.AiimsTokenManager {
        return org.aiims.odk.auth.managers.AiimsTokenManager(apiClient, authStorage, scope)
    }
}