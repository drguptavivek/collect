package edu.aiims.medresodk.auth.managers

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
import edu.aiims.medresodk.auth.api.AuthClient
import edu.aiims.medresodk.auth.api.AuthResult
import edu.aiims.medresodk.auth.api.User
import edu.aiims.medresodk.auth.api.TelemetryEvent
import edu.aiims.medresodk.auth.api.TelemetryRequest
import edu.aiims.medresodk.auth.storage.FakeMedresAuthStorage
import edu.aiims.medresodk.auth.storage.FakeMedresSecureStorage
import edu.aiims.medresodk.auth.fakes.FakeTelemetryDao
import edu.aiims.medresodk.auth.utils.PinManager
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
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.Shadows.shadowOf

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class MedresAuthManagerTest {

    private lateinit var context: Context
    private lateinit var authManager: MedresAuthManager
    private val projectCleaner: ProjectCleaner = mock()
    private val authClient: AuthClient = mock()
    private lateinit var pinManager: PinManager
    
    // Fakes
    private lateinit var authStorage: FakeMedresAuthStorage
    private lateinit var secureStorage: FakeMedresSecureStorage
    private lateinit var telemetryDao: FakeTelemetryDao
    private val networkStateMonitor: edu.aiims.medresodk.auth.utils.MedresNetworkStateMonitor = mock()
    private val networkAvailableFlow = kotlinx.coroutines.flow.MutableStateFlow(false)

    private lateinit var testDispatcher: kotlinx.coroutines.test.TestDispatcher

    @Before
    fun setUp() {
        edu.aiims.medresodk.auth.security.ClockValidator.resetInstance()
        testDispatcher = kotlinx.coroutines.test.UnconfinedTestDispatcher()
        Dispatchers.setMain(testDispatcher)
        context = ApplicationProvider.getApplicationContext()

        // Initialize Fakes
        authStorage = FakeMedresAuthStorage()
        secureStorage = FakeMedresSecureStorage()
        telemetryDao = FakeTelemetryDao()
        whenever(networkStateMonitor.isNetworkAvailable).thenReturn(networkAvailableFlow)

        // Ensure clean state (Though fakes are new instances)
        context.getSharedPreferences("medres_auth_prefs", Context.MODE_PRIVATE).edit().clear().commit()

        // Initialize Managers
        pinManager = PinManager(context)
        authManager = MedresAuthManager(context, { projectCleaner }, pinManager, authStorage, secureStorage, telemetryDao, networkStateMonitor)
        authManager.setAuthClient(authClient)
        authManager.setIoDispatcher(testDispatcher)

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
        
        // Verify against Fake
        assertThat(authStorage.deviceToken, equalTo(token))
        assertThat(authStorage.userId, equalTo("100"))
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
        
        // Check Fake Storage
        assertThat(authStorage.isAuthenticated, equalTo(false))
        assertThat(authStorage.deviceToken, equalTo("")) // Cleared
    }

    @Test
    fun `#logout preserves project data for next user on shared device - Option B`() = runTest {
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
        val expiredDate = edu.aiims.medresodk.auth.utils.ApiDateFormat.format(java.util.Date(oneHourAgo))

        val user = User("100", "testuser", projectId, expiredDate)

        whenever(authClient.login(any(), any(), any(), any(), any())).thenReturn(AuthResult.Success(user, "token", user.expiresAt!!))
        authManager.login(projectId, "user", "pass", "url")
        advanceUntilIdle()

        runBlocking {
            doReturn(false).whenever(authClient).checkReachability()
        }

        // Recreating authManager to simulate app restart, using SAME Fakes
        val newAuthManager = MedresAuthManager(context, { projectCleaner }, pinManager, authStorage, secureStorage, telemetryDao)
        newAuthManager.setAuthClient(authClient)
        newAuthManager.setIoDispatcher(testDispatcher)

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
        val expiredDate = edu.aiims.medresodk.auth.utils.ApiDateFormat.format(java.util.Date(sevenHoursAgo))

        val user = User("100", "testuser", projectId, expiredDate)

        whenever(authClient.login(any(), any(), any(), any(), any())).thenReturn(AuthResult.Success(user, "token", user.expiresAt!!))
        authManager.login(projectId, "user", "pass", "url")

        // Reuse Fakes for persistence check
        val newAuthManager = MedresAuthManager(context, { projectCleaner }, pinManager, authStorage, secureStorage, telemetryDao)
        newAuthManager.setAuthClient(authClient)
        newAuthManager.setIoDispatcher(testDispatcher)

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
        val now = System.currentTimeMillis()
        val oneHourAgo = now - (1 * 60 * 60 * 1000)
        val expiredDate = edu.aiims.medresodk.auth.utils.ApiDateFormat.format(java.util.Date(oneHourAgo))

        val user = User("100", "testuser", projectId, expiredDate)

        whenever(authClient.login(any(), any(), any(), any(), any())).thenReturn(AuthResult.Success(user, "token", user.expiresAt!!))
        authManager.login(projectId, "user", "pass", "url")

        // Server Reachable
        runBlocking {
            doReturn(true).whenever(authClient).checkReachability()
        }

        val newAuthManager = MedresAuthManager(context, { projectCleaner }, pinManager, authStorage, secureStorage, telemetryDao)
        newAuthManager.setAuthClient(authClient)
        newAuthManager.setIoDispatcher(testDispatcher)

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
    fun `#submitTelemetry queues request when offline or error`() = runTest {
        val projectId = "1"
        val user = User("100", "testuser", projectId, "2099-01-01T00:00:00.000Z")
        whenever(authClient.login(any(), any(), any(), any(), any())).thenReturn(AuthResult.Success(user, "token", user.expiresAt!!))
        authManager.login(projectId, "user", "pass", "url")
        authManager.setActiveProject(projectId)
        advanceUntilIdle()

        // Simulate Error (Network)
        whenever(authClient.submitTelemetry(any(), any(), any())).thenReturn(edu.aiims.medresodk.auth.api.TelemetryResult.NetworkError)

        authManager.submitTelemetry(null)
        advanceUntilIdle()

        // Verify inserted into DAO
        runBlocking {
            val pending = telemetryDao.getAll()
            assertThat(pending.size, equalTo(1))
            assertThat(pending[0].projectId, equalTo(projectId))
        }
    }

    @Test
    fun `#flushOfflineQueue submits pending items`() = runTest {
        val projectId = "1"
        val user = User("100", "testuser", projectId, "2099-01-01T00:00:00.000Z")
        whenever(authClient.login(any(), any(), any(), any(), any())).thenReturn(AuthResult.Success(user, "token", user.expiresAt!!))
        authManager.login(projectId, "user", "pass", "url")
        authManager.setActiveProject(projectId)
        advanceUntilIdle()

        // Pre-populate DAO with a failed item
        val json = "{\"deviceId\":\"test\",\"collectVersion\":\"1\",\"deviceDateTime\":\"now\",\"location\":null}"
        val entity = edu.aiims.medresodk.auth.storage.db.TelemetryEntity(data = json, projectId = projectId)
        telemetryDao.insert(entity)

        // Mock success for flush
        val response = edu.aiims.medresodk.auth.api.TelemetryResponse(1, "now", null, "ok")
        whenever(authClient.submitTelemetry(any(), any(), any())).thenReturn(edu.aiims.medresodk.auth.api.TelemetryResult.Success(response))

        authManager.flushOfflineQueue()
        advanceUntilIdle()

        // Verify submitted
        verify(authClient).submitTelemetry(org.mockito.kotlin.eq(projectId), org.mockito.kotlin.eq("token"), any())
        
        // Verify deleted from DAO
        runBlocking {
            val pending = telemetryDao.getAll()
            assertThat(pending.size, equalTo(0))
        }
    }

    @Test
    fun `#submitTelemetry sends correct data with location`() = runTest {
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

        val response = edu.aiims.medresodk.auth.api.TelemetryResponse(1, "now", null, "ok")
        whenever(authClient.submitTelemetry(any(), any(), any())).thenReturn(edu.aiims.medresodk.auth.api.TelemetryResult.Success(response))

        authManager.submitTelemetry(mockLocation)
        advanceUntilIdle()

        verify(authClient).submitTelemetry(org.mockito.kotlin.eq(projectId), org.mockito.kotlin.eq("token"), org.mockito.kotlin.check {
            assertThat(it.deviceId, equalTo("unknown_device"))
            assertThat(it.location!!.latitude.toString(), equalTo("10.0"))
            assertThat(it.location!!.longitude.toString(), equalTo("20.0"))
            assertThat(it.location!!.provider, equalTo("gps"))
            assertThat(it.deviceDateTime, notNullValue())
            assertThat(it.events, nullValue())
        })
    }

    @Test
    fun `#submitTelemetry sends null location if not provided`() = runTest {
        val projectId = "1"
        val user = User("100", "testuser", projectId, "2099-01-01T00:00:00.000Z")
        whenever(authClient.login(any(), any(), any(), any(), any())).thenReturn(AuthResult.Success(user, "token", user.expiresAt!!))
        authManager.login(projectId, "user", "pass", "url")
        authManager.setActiveProject(projectId)
        advanceUntilIdle()

        val response = edu.aiims.medresodk.auth.api.TelemetryResponse(1, "now", null, "ok")
        whenever(authClient.submitTelemetry(any(), any(), any())).thenReturn(edu.aiims.medresodk.auth.api.TelemetryResult.Success(response))

        authManager.submitTelemetry(null)
        advanceUntilIdle()

        verify(authClient).submitTelemetry(org.mockito.kotlin.eq(projectId), org.mockito.kotlin.eq("token"), org.mockito.kotlin.check {
            assertThat(it.location, nullValue())
            assertThat(it.events, nullValue())
        })
    }

    @Test
    fun `#submitTelemetry sends events if provided`() = runTest {
        val projectId = "1"
        val user = User("100", "testuser", projectId, "2099-01-01T00:00:00.000Z")
        whenever(authClient.login(any(), any(), any(), any(), any())).thenReturn(AuthResult.Success(user, "token", user.expiresAt!!))
        authManager.login(projectId, "user", "pass", "url")
        authManager.setActiveProject(projectId)
        advanceUntilIdle()

        val event = TelemetryEvent(
            id = MedresAuthManager.generateEventId(),
            type = "TEST_EVENT",
            timestamp = "2025-01-01",
            payload = mapOf("key" to "value")
        )

        val response = edu.aiims.medresodk.auth.api.TelemetryResponse(1, "now", null, "ok")
        whenever(authClient.submitTelemetry(any(), any(), any())).thenReturn(edu.aiims.medresodk.auth.api.TelemetryResult.Success(response))

        authManager.submitTelemetry(null, event)
        advanceUntilIdle()

        verify(authClient).submitTelemetry(org.mockito.kotlin.eq(projectId), org.mockito.kotlin.eq("token"), org.mockito.kotlin.check {
            assertThat(it.events, notNullValue())
            assertThat(it.events!!.size, equalTo(1))
            assertThat(it.events!![0].type, equalTo("TEST_EVENT"))
            assertThat(it.events!![0].payload!!["key"], equalTo("value"))
        })
    }

    @Test
    fun `#submitTelemetry queues on 401 AuthError without logging out`() = runTest {
        val projectId = "1"
        val user = User("100", "testuser", projectId, "2099-01-01T00:00:00.000Z")
        whenever(authClient.login(any(), any(), any(), any(), any())).thenReturn(AuthResult.Success(user, "token", user.expiresAt!!))
        authManager.login(projectId, "user", "pass", "url")
        authManager.setActiveProject(projectId)
        advanceUntilIdle()

        // Mock 401
        whenever(authClient.submitTelemetry(any(), any(), any())).thenReturn(edu.aiims.medresodk.auth.api.TelemetryResult.AuthError)

        authManager.submitTelemetry(null)
        advanceUntilIdle()

        // Verify queued
        runBlocking {
            val pending = telemetryDao.getAll()
            assertThat("Should be queued on 401", pending.size, equalTo(1))
        }

        // Verify NOT logged out
        assertThat("Should NOT log out on 401 from telemetry", authManager.authState.first(), equalTo(AuthState.LOGGED_IN))
    }

    @Test
    fun `#logout cancels reachability check to prevent race condition (Scenario 63)`() = runTest {
        val projectId = "1"
        val now = System.currentTimeMillis()
        val oneHourAgo = now - (1 * 60 * 60 * 1000)
        val expiredDate = edu.aiims.medresodk.auth.utils.ApiDateFormat.format(java.util.Date(oneHourAgo))
        val user = User("100", "testuser", projectId, expiredDate)

        // Mock login
        whenever(authClient.login(any(), any(), any(), any(), any())).thenReturn(AuthResult.Success(user, "token", user.expiresAt!!))
        authManager.login(projectId, "user", "pass", "url")
        
        // Use StandardTestDispatcher for ALL manager operations to control order
        val standardDispatcher = kotlinx.coroutines.test.StandardTestDispatcher(testScheduler)
        
        val testManager = MedresAuthManager(context, { projectCleaner }, pinManager, authStorage, secureStorage, telemetryDao)
        testManager.setAuthClient(authClient)
        testManager.setIoDispatcher(standardDispatcher)

        // Mock reachability to return true but we will control WHEN it runs
        whenever(authClient.checkReachability()).thenReturn(true)

        // 1. Trigger refreshState (launches background job)
        testManager.setActiveProject(projectId)
        
        // 2. Advance to start the job but pause it if possible? 
        // With StandardTestDispatcher, the job is just sitting in the queue.
        
        // 3. Trigger Logout (also a suspend function, runs on test thread)
        testManager.logout()
        
        // 4. Now advance everything. 
        // Job A (Reachability) and Job B (Logout's internal parts) will run.
        // Even if Reachability runs and finishes, it should see that we are logged out.
        advanceUntilIdle()

        // Assert: isSoftExpiry should remain false
        assertThat("isSoftExpiry must NOT become true after logout", testManager.getIsSoftExpiry(), equalTo(false))
        assertThat("Should be in LOGGED_OUT state", testManager.authState.value, equalTo(AuthState.LOGGED_OUT))
    }

    @Test
    fun `#logout handles storage failure with retries and force-logout (Scenario 67)`() = runTest {
        val projectId = "1"
        val user = User("100", "testuser", projectId, "2099-01-01T00:00:00.000Z")
        
        // Setup initial logged-in state
        whenever(authClient.login(any(), any(), any(), any(), any())).thenReturn(AuthResult.Success(user, "token", user.expiresAt!!))
        authManager.login(projectId, "user", "pass", "url")
        authManager.setActiveProject(projectId)
        advanceUntilIdle()
        
        assertThat(authManager.authState.first(), equalTo(AuthState.LOGGED_IN))

        // Mock MedresAuthStorage to FAIL during clearAuthData
        val mockStorage = mock<edu.aiims.medresodk.auth.storage.MedresAuthStorage>()
        whenever(mockStorage.clearAuthData()).thenReturn(false)
        
        // Recreate manager with failing mock storage
        val failingAuthManager = MedresAuthManager(context, { projectCleaner }, pinManager, mockStorage, secureStorage, telemetryDao)
        failingAuthManager.setAuthClient(authClient)
        failingAuthManager.setIoDispatcher(testDispatcher)
        
        // Mock active project ID in prefs for logout to work
        context.getSharedPreferences("medres_auth_prefs", Context.MODE_PRIVATE)
            .edit().putString("active_project_id", projectId).commit()

        failingAuthManager.logout()
        advanceUntilIdle()

        // Verify it retried (3 attempts total: 1 initial + 2 retries)
        verify(mockStorage, org.mockito.kotlin.times(3)).clearAuthData()
        
        // Verify state is LOGGED_OUT in memory even if storage failed
        assertThat("Must transition to LOGGED_OUT in memory despite storage failure", 
            failingAuthManager.authState.first(), equalTo(AuthState.LOGGED_OUT))
    }

    @Test
    fun `#isSoftExpiry is persisted across manager restarts (Scenario 73)`() = runTest {
        val projectId = "1"
        // Token expired 10 minutes ago
        val expiryTime = System.currentTimeMillis() - 600000L
        val expiresAt = edu.aiims.medresodk.auth.utils.ApiDateFormat.format(java.util.Date(expiryTime))
        val user = User("100", "testuser", projectId, expiresAt)
        
        // Setup initial state in storage
        whenever(authClient.login(any(), any(), any(), any(), any())).thenReturn(AuthResult.Success(user, "token", expiresAt))
        authManager.login(projectId, "user", "pass", "url")
        authManager.setActiveProject(projectId)
        advanceUntilIdle()
        
        // Manual override for TDD: Simulate being in grace period with isSoftExpiry already set
        context.getSharedPreferences("medres_auth_prefs", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("is_soft_expiry", true)
            .putString("active_project_id", projectId)
            .commit()
            
        // Setup authStorage to return the expired token info
        // (Assuming authStorage/secureStorage persistence is already working from login call)
            
        // Recreate manager
        val restartManager = MedresAuthManager(context, { projectCleaner }, pinManager, authStorage, secureStorage, telemetryDao)
        
        assertThat("isSoftExpiry should be true after restart if persisted", 
            restartManager.getIsSoftExpiry(), equalTo(true))
            
        // Verify it's cleared on logout
        restartManager.logout()
        advanceUntilIdle()
        
        assertThat("isSoftExpiry should be cleared after logout", 
            context.getSharedPreferences("medres_auth_prefs", Context.MODE_PRIVATE).getBoolean("is_soft_expiry", false), 
            equalTo(false))
    }

    @Test
    fun `#hardDeadlineTime is updated when token enters grace period (Scenario 68)`() = runTest {
        val projectId = "1"
        val expiryTime = System.currentTimeMillis() - 1000L // Just expired
        val expiresAt = edu.aiims.medresodk.auth.utils.ApiDateFormat.format(java.util.Date(expiryTime))
        val user = User("100", "testuser", projectId, expiresAt)
        
        // Setup state
        whenever(authClient.login(any(), any(), any(), any(), any())).thenReturn(AuthResult.Success(user, "token", expiresAt))
        authManager.login(projectId, "user", "pass", "url")
        authManager.setActiveProject(projectId)
        advanceUntilIdle()
        
        val expectedHardDeadline = expiryTime + (6 * 60 * 60 * 1000L) // 6 hours grace
        
        assertThat("hardDeadlineTime should be expiry + 6h", 
            authManager.hardDeadlineTime.first(), equalTo(expectedHardDeadline))
    }

    @Test
    fun `grace period is re-evaluated when network restores (Scenario 59)`() = runTest {
        val projectId = "1"
        val expiryTime = System.currentTimeMillis() - 1000L // Expired
        val expiresAt = edu.aiims.medresodk.auth.utils.ApiDateFormat.format(java.util.Date(expiryTime))
        val user = User("100", "testuser", projectId, expiresAt)
        
        // 1. Initial State: Expired, Offline Grace (Server Unreachable)
        whenever(authClient.login(any(), any(), any(), any(), any())).thenReturn(AuthResult.Success(user, "token", expiresAt))
        whenever(authClient.checkReachability()).thenReturn(false) // Unreachable initially
        
        authManager.login(projectId, "user", "pass", "url")
        authManager.setActiveProject(projectId)
        advanceUntilIdle()
        
        assertThat("Should NOT be soft expiry yet (silent grace)", authManager.getIsSoftExpiry(), equalTo(false))
        
        // 2. Simulate Network Restoration
        whenever(authClient.checkReachability()).thenReturn(true) // Now reachable
        networkAvailableFlow.value = true
        advanceUntilIdle()
        
        // 3. Verify: Reachability check triggered and isSoftExpiry updated
        assertThat("Should be soft expiry now that network is restored", authManager.getIsSoftExpiry(), equalTo(true))
        verify(authClient, org.mockito.kotlin.times(2)).checkReachability()
    }
}
