package org.aiims.odk.auth.managers

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.aiims.odk.auth.api.AuthClient
import org.aiims.odk.auth.api.AuthResult
import org.aiims.odk.auth.api.User
import org.aiims.odk.auth.storage.AiimsAuthStorage
import org.aiims.odk.auth.storage.AiimsSecureStorage
import org.aiims.odk.auth.storage.db.TelemetryDao
import org.aiims.odk.auth.utils.PinManager
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.instanceOf
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class AiimsAuthManagerWriteFailureTest {

    private lateinit var context: Context
    private lateinit var authManager: AiimsAuthManager
    private val projectCleaner: ProjectCleaner = mock()
    private val pinManager: PinManager = mock()
    private val authClient: AuthClient = mock()
    
    // Mocks
    private val authStorage: AiimsAuthStorage = mock()
    private val secureStorage: AiimsSecureStorage = mock()
    private val telemetryDao: TelemetryDao = mock()

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        context = ApplicationProvider.getApplicationContext()
        
        // Clean slate
        context.getSharedPreferences("aiims_auth_prefs", Context.MODE_PRIVATE).edit().clear().commit()

        authManager = AiimsAuthManager(context, { projectCleaner }, pinManager, authStorage, secureStorage, telemetryDao)
        authManager.setAuthClient(authClient)
        // Use StandardTestDispatcher for ioDispatcher too to control execution
        authManager.setIoDispatcher(StandardTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `#login returns Error when storage write fails`() = runTest {
        val projectId = "1"
        val user = User("100", "testuser", projectId, "2099-01-01")
        
        // 1. Mock successful API login
        whenever(authClient.login(any(), any(), any(), any(), any())).thenReturn(
            AuthResult.Success(user, "valid_token", "2099-01-01")
        )
        
        // 2. Mock storage write FAILURE (simulating disk full or commit() returning false)
        doThrow(RuntimeException("Storage write failed: Disk full")).whenever(authStorage).saveAuthSession(any(), any(), any(), any())

        // 3. Attempt login
        val result = authManager.login(projectId, "user", "pass", "url")
        advanceUntilIdle()

        // 4. Verify result is Error, NOT Success
        assertThat("Login should fail if storage write fails", result, instanceOf(AuthResult.Error::class.java))
        val error = result as AuthResult.Error
        assertThat(error.message, equalTo("Storage write failed: Disk full"))
    }
}
