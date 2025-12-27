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
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.notNullValue
import org.hamcrest.Matchers.nullValue
import org.junit.After
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
        context.getSharedPreferences("aiims_auth_prefs", Context.MODE_PRIVATE).edit().clear().commit()

        // Initialize Managers
        pinManager = PinManager(context)
        authManager = AiimsAuthManager(context, projectCleaner, pinManager)
        authManager.setAuthClient(authClient)
        // Note: We don't attach testScheduler here because setUp runs outside runTest
        // But StandardTestDispatcher() works.
        authManager.setIoDispatcher(StandardTestDispatcher())

        pinManager = PinManager(context)
        pinManager.clearPin()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `#login stores token and updates state when success`() = runTest {
        val projectId = "1"
        val user = User("100", "testuser", projectId, "2099-01-01T00:00:00.000Z")
        val token = "test_token"

        whenever(authClient.login(any(), any(), any(), any(), any())).thenReturn(
            AuthResult.Success(user, token, user.expiresAt!!)
        )

        val result = authManager.login(projectId, "testuser", "password", "https://api.example.com")
        advanceUntilIdle() // Process coroutines

        assertThat(result is AuthResult.Success, equalTo(true))
        authManager.setActiveProject(projectId)
        advanceUntilIdle()

        assertThat(authManager.authState.first(), equalTo(AuthState.LOGGED_IN))
        assertThat(authManager.currentUser.first()?.username, equalTo("testuser"))
        assertThat(authManager.getActiveProjectToken(), equalTo(token))
    }

    @Test
    fun `#login returns error and does not change state when failure`() = runTest {
        val projectId = "1"
        whenever(authClient.login(any(), any(), any(), any(), any())).thenReturn(
            AuthResult.Error("Invalid credentials")
        )

        val result = authManager.login(projectId, "testuser", "wrongpassword", "https://api.example.com")
        advanceUntilIdle()

        assertThat(result is AuthResult.Error, equalTo(true))
        assertThat((result as AuthResult.Error).message, equalTo("Invalid credentials"))

        authManager.setActiveProject(projectId)
        advanceUntilIdle()
        assertThat(authManager.authState.first(), equalTo(AuthState.LOGGED_OUT))
    }

    @Test
    fun `#login clears PIN when user changed`() = runTest {
        val projectId = "1"
        val userA = User("A", "userA", projectId, "2099-01-01T00:00:00.000Z")
        val userB = User("B", "userB", projectId, "2099-01-01T00:00:00.000Z")

        whenever(authClient.login(any(), any(), any(), any(), any())).thenReturn(AuthResult.Success(userA, "tokenA", userA.expiresAt!!))
        authManager.login(projectId, "userA", "pass", "url")
        authManager.setActiveProject(projectId)
        advanceUntilIdle()

        pinManager.savePin("1234")
        assertThat(pinManager.isPinSet(), equalTo(true))

        whenever(authClient.login(any(), any(), any(), any(), any())).thenReturn(AuthResult.Success(userB, "tokenB", userB.expiresAt!!))
        authManager.login(projectId, "userB", "pass", "url")
        advanceUntilIdle()

        assertThat("PIN should be cleared when user changes", pinManager.isPinSet(), equalTo(false))
    }

    @Test
    fun `#login keeps PIN when user is the same`() = runTest {
        val projectId = "1"
        val userA = User("A", "userA", projectId, "2099-01-01T00:00:00.000Z")

        whenever(authClient.login(any(), any(), any(), any(), any())).thenReturn(AuthResult.Success(userA, "tokenA", userA.expiresAt!!))
        authManager.login(projectId, "userA", "pass", "url")
        authManager.setActiveProject(projectId)
        advanceUntilIdle()

        pinManager.savePin("1234")
        assertThat(pinManager.isPinSet(), equalTo(true))

        authManager.login(projectId, "userA", "pass", "url")
        advanceUntilIdle()

        assertThat("PIN should remain when user is the same", pinManager.isPinSet(), equalTo(true))
    }

    @Test
    fun `#logout revokes token but preserves project data - Option B`() = runTest {
        val projectId = "1"

        val user = User("100", "testuser", projectId, "2099-01-01T00:00:00.000Z")
        whenever(authClient.login(any(), any(), any(), any(), any())).thenReturn(AuthResult.Success(user, "token", user.expiresAt!!))
        whenever(authClient.revokeSession(any(), any(), any(), any())).thenReturn(true)

        authManager.login(projectId, "user", "pass", "url")
        authManager.setActiveProject(projectId)
        advanceUntilIdle()

        assertThat("Token should be present before logout", authManager.getActiveProjectToken(), notNullValue())

        authManager.logout()
        advanceUntilIdle()

        // Token revocation should be called
        verify(authClient, org.mockito.kotlin.atLeastOnce()).revokeSession(any(), any(), any(), any())
        
        // OPTION B: Project data should NOT be cleared on logout
        // This preserves forms and instances for shared device scenarios
        verify(projectCleaner, org.mockito.kotlin.never()).clearProjectData(any())
        
        assertThat(authManager.authState.first(), equalTo(AuthState.LOGGED_OUT))
        assertThat(authManager.getActiveProjectToken(), nullValue())
    }

    @Test
    fun `#logout preserves project data for next user on shared device - Option B`() = runTest {
        // This test verifies the intentional behavior for AIIMS shared device deployments:
        // When User A logs out, their forms and instances remain on device
        // so User B logging into the same project can access them.
        val projectId = "1"

        val userA = User("A", "userA", projectId, "2099-01-01T00:00:00.000Z")
        whenever(authClient.login(any(), any(), any(), any(), any())).thenReturn(AuthResult.Success(userA, "tokenA", userA.expiresAt!!))
        whenever(authClient.revokeSession(any(), any(), any(), any())).thenReturn(true)

        // User A logs in
        authManager.login(projectId, "userA", "pass", "url")
        authManager.setActiveProject(projectId)
        advanceUntilIdle()
        
        // User A logs out
        authManager.logout()
        advanceUntilIdle()

        // Verify: projectCleaner should NEVER be called (Option B)
        // This ensures forms, instances, and cache are preserved
        verify(projectCleaner, org.mockito.kotlin.never()).clearProjectData(any())

        // User B can now log into the same project and see User A's data
        val userB = User("B", "userB", projectId, "2099-01-01T00:00:00.000Z")
        whenever(authClient.login(any(), any(), any(), any(), any())).thenReturn(AuthResult.Success(userB, "tokenB", userB.expiresAt!!))
        
        authManager.login(projectId, "userB", "pass", "url")
        authManager.setActiveProject(projectId)
        advanceUntilIdle()

        // User B is successfully logged in to the same project
        assertThat(authManager.authState.first(), equalTo(AuthState.LOGGED_IN))
        assertThat(authManager.currentUser.first()?.username, equalTo("userB"))
        
        // Still no project data cleared
        verify(projectCleaner, org.mockito.kotlin.never()).clearProjectData(any())
    }

    @Test
    fun `#refreshState detects token expiry and allows grace if server unreachable`() = runTest {
        val projectId = "1"
        // Expires 1 hour ago (Within 6h grace)
        val now = System.currentTimeMillis()
        val oneHourAgo = now - (1 * 60 * 60 * 1000)
        val expiredDate = org.aiims.odk.auth.utils.ApiDateFormat.format(java.util.Date(oneHourAgo))

        val user = User("100", "testuser", projectId, expiredDate)

        whenever(authClient.login(any(), any(), any(), any(), any())).thenReturn(AuthResult.Success(user, "token", user.expiresAt!!))
        authManager.login(projectId, "user", "pass", "url")
        advanceUntilIdle()

        runBlocking {
            doReturn(false).whenever(authClient).checkReachability()
        }

        val newAuthManager = AiimsAuthManager(context, projectCleaner, pinManager)
        newAuthManager.setAuthClient(authClient)
        newAuthManager.setIoDispatcher(StandardTestDispatcher(testScheduler))

        newAuthManager.setActiveProject(projectId)

        advanceUntilIdle()
        shadowOf(Looper.getMainLooper()).idle()

        val state = newAuthManager.authState.first()
        assertThat("State should be LOGGED_IN during grace period", state, equalTo(AuthState.LOGGED_IN))
        assertThat("Should not be Soft Expiry if unreachable", newAuthManager.getIsSoftExpiry(), equalTo(false))
        verify(authClient).checkReachability()
    }

    @Test
    fun `#refreshState enforces hard deadline after 6 hours`() = runTest {
        val projectId = "1"
        // Expires 7 hours ago (> 6h grace)
        val now = System.currentTimeMillis()
        val sevenHoursAgo = now - (7 * 60 * 60 * 1000)
        val expiredDate = org.aiims.odk.auth.utils.ApiDateFormat.format(java.util.Date(sevenHoursAgo))

        val user = User("100", "testuser", projectId, expiredDate)

        whenever(authClient.login(any(), any(), any(), any(), any())).thenReturn(AuthResult.Success(user, "token", user.expiresAt!!))
        authManager.login(projectId, "user", "pass", "url")

        // even if server is unreachable, hard deadline kills it (actually code doesn't check reachability for hard deadline)
        // refreshState checks hard deadline first

        val newAuthManager = AiimsAuthManager(context, projectCleaner, pinManager)
        newAuthManager.setAuthClient(authClient)
        newAuthManager.setIoDispatcher(StandardTestDispatcher(testScheduler))

        newAuthManager.setActiveProject(projectId)

        advanceUntilIdle() // Coroutine for logout

        assertThat(newAuthManager.authState.first(), equalTo(AuthState.LOGGED_OUT))
        // OPTION B: Even on hard deadline, project data is NOT cleared
        verify(projectCleaner, org.mockito.kotlin.never()).clearProjectData(any())
        // Reachability should NOT have been called
        verify(authClient, org.mockito.kotlin.never()).checkReachability()
    }

    @Test
    fun `#refreshState sets soft expiry if reachable within grace`() = runTest {
        val projectId = "1"
        // Expires 1 hour ago (Within 6h grace)
        val now = System.currentTimeMillis()
        val oneHourAgo = now - (1 * 60 * 60 * 1000)
        val expiredDate = org.aiims.odk.auth.utils.ApiDateFormat.format(java.util.Date(oneHourAgo))

        val user = User("100", "testuser", projectId, expiredDate)

        whenever(authClient.login(any(), any(), any(), any(), any())).thenReturn(AuthResult.Success(user, "token", user.expiresAt!!))
        authManager.login(projectId, "user", "pass", "url")

        // Server Reachable
        runBlocking {
            doReturn(true).whenever(authClient).checkReachability()
        }

        val newAuthManager = AiimsAuthManager(context, projectCleaner, pinManager)
        newAuthManager.setAuthClient(authClient)
        newAuthManager.setIoDispatcher(StandardTestDispatcher(testScheduler))

        newAuthManager.setActiveProject(projectId)

        advanceUntilIdle()
        shadowOf(Looper.getMainLooper()).idle()

        // Assert: Logged IN (Grace) but Soft Expiry TRUE
        assertThat(newAuthManager.authState.first(), equalTo(AuthState.LOGGED_IN))
        assertThat("Should set Soft Expiry", newAuthManager.getIsSoftExpiry(), equalTo(true))

        // Should NOT have logged out
        verify(authClient, org.mockito.kotlin.never()).revokeSession(any(), any(), any(), any())
    }

    @Test
    fun `#submitTelemetry sends correct data`() = runTest {
        val projectId = "1"
        val user = User("100", "testuser", projectId, "2099-01-01T00:00:00.000Z")
        whenever(authClient.login(any(), any(), any(), any(), any())).thenReturn(AuthResult.Success(user, "token", user.expiresAt!!))
        authManager.login(projectId, "user", "pass", "url")
        authManager.setActiveProject(projectId)
        advanceUntilIdle()

        // Mock Location
        val mockLocation = mock<android.location.Location>()
        whenever(mockLocation.latitude).thenReturn(10.0)
        whenever(mockLocation.longitude).thenReturn(20.0)
        whenever(mockLocation.provider).thenReturn("gps")

        authManager.submitTelemetry(mockLocation)
        advanceUntilIdle()

        verify(authClient).submitTelemetry(org.mockito.kotlin.eq(projectId), org.mockito.kotlin.eq("token"), org.mockito.kotlin.check {
            assertThat(it.deviceId, equalTo("unknown_device"))
            assertThat(it.location.latitude.toString(), equalTo("10.0"))
            assertThat(it.location.longitude.toString(), equalTo("20.0"))
            assertThat(it.location.provider, equalTo("gps"))
            assertThat(it.deviceDateTime, notNullValue())
        })
    }
}
