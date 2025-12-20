package org.aiims.odk.auth.managers

import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.aiims.odk.auth.api.AuthClient
import org.aiims.odk.auth.api.AuthResult
import org.aiims.odk.auth.api.User
import org.aiims.odk.auth.utils.PinManager
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.Shadows.shadowOf

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class AiimsAuthManagerTest {

    private lateinit var context: Context
    private lateinit var authManager: AiimsAuthManager
    private val projectCleaner: ProjectCleaner = mock()
    private val authClient: AuthClient = mock()
    private lateinit var pinManager: PinManager

    @Before
    fun setUp() {
        // Use StandardTestDispatcher for control
        Dispatchers.setMain(StandardTestDispatcher())
        context = ApplicationProvider.getApplicationContext()
        
        // Ensure clean state
        AiimsAuthManager.resetInstanceForTesting()
        context.getSharedPreferences("aiims_auth_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        
        // Initialize Managers
        authManager = AiimsAuthManager.init(context, projectCleaner)
        authManager.setAuthClient(authClient)
        // Note: We don't attach testScheduler here because setUp runs outside runTest
        // But StandardTestDispatcher() works.
        authManager.setIoDispatcher(StandardTestDispatcher())
        
        pinManager = PinManager.getInstance(context)
        pinManager.clearPin()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        AiimsAuthManager.resetInstanceForTesting()
    }

    @Test
    fun login_success_storesTokenAndUpdatesState() = runTest {
        val projectId = "1"
        val user = User("100", "testuser", projectId, "2099-01-01T00:00:00.000Z")
        val token = "test_token"
        
        whenever(authClient.login(any(), any(), any())).thenReturn(
            AuthResult.Success(user, token, user.expiresAt!!)
        )

        val result = authManager.login(projectId, "testuser", "password", "https://api.example.com")
        advanceUntilIdle() // Process coroutines

        assertTrue(result is AuthResult.Success)
        authManager.setActiveProject(projectId)
        advanceUntilIdle()
        
        assertEquals(AuthState.LOGGED_IN, authManager.authState.first())
        assertEquals("testuser", authManager.currentUser.first()?.username)
        assertEquals(token, authManager.getActiveProjectToken())
    }

    @Test
    fun login_failure_returnsErrorAndDoesNotChangeState() = runTest {
        val projectId = "1"
        whenever(authClient.login(any(), any(), any())).thenReturn(
            AuthResult.Error("Invalid credentials")
        )

        val result = authManager.login(projectId, "testuser", "wrongpassword", "https://api.example.com")
        advanceUntilIdle()

        assertTrue(result is AuthResult.Error)
        assertEquals("Invalid credentials", (result as AuthResult.Error).message)
        
        authManager.setActiveProject(projectId)
        advanceUntilIdle()
        assertEquals(AuthState.LOGGED_OUT, authManager.authState.first())
    }

    @Test
    fun login_withDifferentUser_clearsPin() = runTest {
        val projectId = "1"
        val userA = User("A", "userA", projectId, "2099-01-01T00:00:00.000Z")
        val userB = User("B", "userB", projectId, "2099-01-01T00:00:00.000Z")
        
        whenever(authClient.login(any(), any(), any())).thenReturn(AuthResult.Success(userA, "tokenA", userA.expiresAt!!))
        authManager.login(projectId, "userA", "pass", "url")
        authManager.setActiveProject(projectId)
        advanceUntilIdle()
        
        pinManager.savePin("1234")
        assertTrue(pinManager.isPinSet())

        whenever(authClient.login(any(), any(), any())).thenReturn(AuthResult.Success(userB, "tokenB", userB.expiresAt!!))
        authManager.login(projectId, "userB", "pass", "url")
        advanceUntilIdle()

        assertFalse("PIN should be cleared when user changes", pinManager.isPinSet())
    }
    
    @Test
    fun login_withSameUser_keepsPin() = runTest {
        val projectId = "1"
        val userA = User("A", "userA", projectId, "2099-01-01T00:00:00.000Z")
        
        whenever(authClient.login(any(), any(), any())).thenReturn(AuthResult.Success(userA, "tokenA", userA.expiresAt!!))
        authManager.login(projectId, "userA", "pass", "url")
        authManager.setActiveProject(projectId)
        advanceUntilIdle()
        
        pinManager.savePin("1234")
        assertTrue(pinManager.isPinSet())

        authManager.login(projectId, "userA", "pass", "url")
        advanceUntilIdle()

        assertTrue("PIN should remain when user is the same", pinManager.isPinSet())
    }

    @Test
    fun logout_revokesTokenAndClearsData() = runTest {
        val projectId = "1"
        
        val user = User("100", "testuser", projectId, "2099-01-01T00:00:00.000Z")
        whenever(authClient.login(any(), any(), any())).thenReturn(AuthResult.Success(user, "token", user.expiresAt!!))
        whenever(authClient.revokeSession(any(), any(), any())).thenReturn(true)
        
        authManager.login(projectId, "user", "pass", "url")
        authManager.setActiveProject(projectId)
        advanceUntilIdle()
        
        assertNotNull("Token should be present before logout", authManager.getActiveProjectToken())

        authManager.logout()
        advanceUntilIdle()

        verify(authClient, org.mockito.kotlin.atLeastOnce()).revokeSession(any(), any(), any())
        verify(projectCleaner).clearProjectData(projectId)
        assertEquals(AuthState.LOGGED_OUT, authManager.authState.first())
        assertNull(authManager.getActiveProjectToken())
    }

    @Test
    fun refreshState_detectsTokenExpiry_andExpiresIfServerReachable() = runTest {
        val projectId = "1"
        val user = User("100", "testuser", projectId, "2000-01-01T00:00:00.000Z") // Expired
        whenever(authClient.login(any(), any(), any())).thenReturn(AuthResult.Success(user, "token", user.expiresAt!!))
        authManager.login(projectId, "user", "pass", "url")
        authManager.setActiveProject(projectId)
        advanceUntilIdle()
        
        // Stub using runBlocking + doReturn
        runBlocking {
            doReturn(true).whenever(authClient).checkReachability()
        }
        
        // Reset memory
        AiimsAuthManager.resetInstanceForTesting()
        val newAuthManager = AiimsAuthManager.init(context, projectCleaner)
        newAuthManager.setAuthClient(authClient)
        
        // Critical: Set dispatcher linked to THIS runTest's scheduler
        newAuthManager.setIoDispatcher(StandardTestDispatcher(testScheduler))
        
        newAuthManager.setActiveProject(projectId)
        
        // Advance time for reachability check and logout
        advanceUntilIdle()
        shadowOf(Looper.getMainLooper()).idle()
        
        // Verify Check Logic
        verify(authClient, org.mockito.kotlin.atLeastOnce()).checkReachability()
        verify(authClient, org.mockito.kotlin.atLeastOnce()).revokeSession(any(), any(), any())
        
        assertEquals(AuthState.LOGGED_OUT, newAuthManager.authState.first())
    }

    @Test
    fun refreshState_detectsTokenExpiry_andAllowsGraceIfServerUnreachable() = runTest {
        val projectId = "1"
        val user = User("100", "testuser", projectId, "2000-01-01T00:00:00.000Z") 
        
        whenever(authClient.login(any(), any(), any())).thenReturn(AuthResult.Success(user, "token", user.expiresAt!!))
        authManager.login(projectId, "user", "pass", "url")
        advanceUntilIdle()
        
        runBlocking {
             doReturn(false).whenever(authClient).checkReachability()
        }
        
        AiimsAuthManager.resetInstanceForTesting()
        val newAuthManager = AiimsAuthManager.init(context, projectCleaner)
        newAuthManager.setAuthClient(authClient)
        newAuthManager.setIoDispatcher(StandardTestDispatcher(testScheduler))
        
        newAuthManager.setActiveProject(projectId)
        
        advanceUntilIdle()
        shadowOf(Looper.getMainLooper()).idle()
        
        val state = newAuthManager.authState.first()
        assertTrue("State should be LOGGED_IN during grace period", state == AuthState.LOGGED_IN)
        verify(authClient).checkReachability()
    }
}
