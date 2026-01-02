package edu.aiims.medresodk.auth.managers

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import edu.aiims.medresodk.auth.api.AuthClient
import edu.aiims.medresodk.auth.storage.MedresAuthStorage
import edu.aiims.medresodk.auth.storage.MedresSecureStorage
import edu.aiims.medresodk.auth.storage.db.TelemetryDao
import edu.aiims.medresodk.auth.utils.PinManager
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class MedresAuthManagerCorruptionTest {

    private lateinit var context: Context
    private lateinit var authManager: MedresAuthManager
    private val projectCleaner: ProjectCleaner = mock()
    private val pinManager: PinManager = mock()
    private val authClient: AuthClient = mock()
    
    // Mocks that simulate corruption
    private val authStorage: MedresAuthStorage = mock()
    private val secureStorage: MedresSecureStorage = mock()
    private val telemetryDao: TelemetryDao = mock()

    @Before
    fun setUp() {
        edu.aiims.medresodk.auth.security.ClockValidator.resetInstance()
        Dispatchers.setMain(StandardTestDispatcher())
        context = ApplicationProvider.getApplicationContext()

        // Clean slate prefs
        context.getSharedPreferences("medres_auth_prefs", Context.MODE_PRIVATE).edit().clear().commit()

        authManager = MedresAuthManager(context, { projectCleaner }, pinManager, authStorage, secureStorage, telemetryDao)
        authManager.setAuthClient(authClient)
        authManager.setIoDispatcher(StandardTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `#refreshState handles exception from storage read gracefully`() = runTest {
        val projectId = "1"
        
        // Setup initial active project to trigger refreshState
        context.getSharedPreferences("medres_auth_prefs", Context.MODE_PRIVATE)
            .edit()
            .putString("active_project_id", projectId)
            .apply()

        // Simulate corruption: Storage throws RuntimeException (e.g. key store error, format error)
        whenever(authStorage.projectId).thenThrow(RuntimeException("Storage corrupted"))

        try {
            // This calls refreshState internally because activeProjectId matches
            authManager.setActiveProject(projectId)
            advanceUntilIdle()
            
            // If we get here without crash, verify state is safe
            assertThat(authManager.authState.first(), equalTo(AuthState.LOGGED_OUT))
        } catch (e: Exception) {
            throw AssertionError("MedresAuthManager crashed on corrupted storage", e)
        }
    }
    
    @Test
    fun `#refreshState handles exception from tokenExpiry read gracefully`() = runTest {
        val projectId = "1"
        
        // Setup valid project ID
        whenever(authStorage.projectId).thenReturn(projectId)
        // Setup valid token
        whenever(authStorage.deviceToken).thenReturn("valid_token")
        
        // Simulate corruption: tokenExpiry read fails
        whenever(authStorage.tokenExpiry).thenThrow(RuntimeException("Expiry time corrupted"))

        try {
            authManager.setActiveProject(projectId)
            advanceUntilIdle()
            
            // Should default to logged out/safe state
            assertThat(authManager.authState.first(), equalTo(AuthState.LOGGED_OUT))
        } catch (e: Exception) {
            throw AssertionError("MedresAuthManager crashed on corrupted tokenExpiry", e)
        }
    }
}
