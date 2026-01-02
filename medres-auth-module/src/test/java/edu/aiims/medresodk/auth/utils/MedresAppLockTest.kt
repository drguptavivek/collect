package edu.aiims.medresodk.auth.utils

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import edu.aiims.medresodk.auth.activities.MedresLoginActivity
import edu.aiims.medresodk.auth.activities.PinEntryActivity
import edu.aiims.medresodk.auth.managers.MedresAuthManager
import edu.aiims.medresodk.auth.managers.AuthState
import edu.aiims.medresodk.auth.managers.ProjectCleaner
import edu.aiims.medresodk.auth.storage.FakeMedresAuthStorage
import edu.aiims.medresodk.auth.storage.FakeMedresSecureStorage
import edu.aiims.medresodk.auth.security.ClockValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.setMain
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.any
import org.mockito.Mockito.never
import org.mockito.Mockito.spy
import org.mockito.Mockito.verify
import org.mockito.Mockito.times
import org.mockito.MockitoAnnotations
import org.robolectric.annotation.Config

/**
 * Unit tests for MedresAppLock.
 * 
 * Uses Real MedresAuthManager with Fakes to avoid mocking final classes.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [28])
class MedresAppLockTest {

    private lateinit var application: Application

    // Use Real AuthManager with Fakes
    private lateinit var authManager: MedresAuthManager
    private lateinit var authStorage: FakeMedresAuthStorage
    private lateinit var secureStorage: FakeMedresSecureStorage
    
    @Mock
    private lateinit var mockProjectCleaner: ProjectCleaner

    @Mock
    private lateinit var mockPinManager: PinManager

    @Mock
    private lateinit var mockActivity: Activity

    @Mock
    private lateinit var mockPinEntryActivity: PinEntryActivity

    @Mock
    private lateinit var mockLoginActivity: MedresLoginActivity

    private lateinit var appLock: MedresAppLock
    private lateinit var spyApplication: Application

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        ClockValidator.resetInstance()
        Dispatchers.setMain(testDispatcher)
        MockitoAnnotations.openMocks(this)
        application = ApplicationProvider.getApplicationContext()
        spyApplication = spy(application)
        
        // Setup Fakes
        authStorage = FakeMedresAuthStorage()
        secureStorage = FakeMedresSecureStorage()
        
        // Initialize Real AuthManager
        authManager = MedresAuthManager(
            context = application,
            projectCleaner = { mockProjectCleaner },
            pinManager = mockPinManager,
            authStorage = authStorage,
            secureStorage = secureStorage,
            telemetryDao = edu.aiims.medresodk.auth.fakes.FakeTelemetryDao()
        )
        
        appLock = MedresAppLock(spyApplication, authManager, mockPinManager)
    }

    @Test
    fun `onActivityStarted launches PinEntryActivity on app launch`() {
        // Arrange: First activity start (fresh launch)
        authManager.updateAuthState(AuthState.LOGGED_IN)
        `when`(mockPinManager.isPinSet()).thenReturn(true)

        // Act
        appLock.onActivityStarted(mockActivity)

        // Assert: PIN activity SHOULD be launched
        verify(spyApplication).startActivity(any(Intent::class.java))
    }

    @Test
    fun `onActivityStopped sets shouldRequirePin when app goes to background`() {
        // Arrange: Simulate one activity started
        appLock.onActivityStarted(mockActivity)

        // Act: Simulate that activity stopping (app going to background)
        appLock.onActivityStopped(mockActivity)

        // Assert: Internal state should now require PIN
        authManager.updateAuthState(AuthState.LOGGED_IN)
        // Soft expiry false by default in Fake/Manager
        `when`(mockPinManager.isPinSet()).thenReturn(true)

        appLock.onActivityStarted(mockActivity)

        // Verify PIN entry activity was launched
        verify(spyApplication).startActivity(any(Intent::class.java))
    }

    @Test
    fun `onActivityStarted launches PinEntryActivity when returning from background with PIN set`() {
        // Arrange: Simulate app going to background then foreground
        appLock.onActivityStarted(mockActivity)
        appLock.onActivityStopped(mockActivity)

        authManager.updateAuthState(AuthState.LOGGED_IN)
        `when`(mockPinManager.isPinSet()).thenReturn(true)

        // Act
        appLock.onActivityStarted(mockActivity)

        // Assert: Verify startActivity was called
        verify(spyApplication).startActivity(any(Intent::class.java))
    }

    @Test
    fun `onActivityStarted launches LoginActivity for reauth when soft expiry`() {
        // Arrange: Simulate app going to background then foreground
        appLock.onActivityStarted(mockActivity)
        appLock.onActivityStopped(mockActivity)

        authManager.updateAuthState(AuthState.LOGGED_IN)
        authManager.setIsSoftExpiry(true)

        // Act
        appLock.onActivityStarted(mockActivity)

        // Assert: Login activity should be launched
        verify(spyApplication).startActivity(any(Intent::class.java))
    }

    @Test
    fun `onActivityStarted does not launch PIN when user is not logged in`() {
        // Arrange: Simulate background/foreground but not logged in
        appLock.onActivityStarted(mockActivity)
        appLock.onActivityStopped(mockActivity)

        authManager.updateAuthState(AuthState.LOGGED_OUT)

        // Act
        appLock.onActivityStarted(mockActivity)

        // Assert: No activity launched
        verify(spyApplication, never()).startActivity(any())
    }

    @Test
    fun `onActivityStarted does not launch PIN when PIN is not set`() {
        // Arrange
        appLock.onActivityStarted(mockActivity)
        appLock.onActivityStopped(mockActivity)

        authManager.updateAuthState(AuthState.LOGGED_IN)
        `when`(mockPinManager.isPinSet()).thenReturn(false)

        // Act
        appLock.onActivityStarted(mockActivity)

        // Assert: No activity launched because PIN is not set
        verify(spyApplication, never()).startActivity(any())
    }



    @Test
    fun `onActivityStarted skips PIN for auth flow activities`() {
        // Arrange: Simulate returning from background
        appLock.onActivityStarted(mockActivity)
        appLock.onActivityStopped(mockActivity)

        // Act: Start with PinEntryActivity (an auth flow activity)
        appLock.onActivityStarted(mockPinEntryActivity)

        // Assert: No additional activity launched (already on auth screen)
        verify(spyApplication, never()).startActivity(any())
    }

    @Test
    fun `onActivityStarted skips PIN for login activity`() {
        // Arrange
        appLock.onActivityStarted(mockActivity)
        appLock.onActivityStopped(mockActivity)

        // Act: Start with LoginActivity
        appLock.onActivityStarted(mockLoginActivity)

        // Assert: No additional activity launched
        verify(spyApplication, never()).startActivity(any())
    }

    @Test
    fun `multiple activities do not trigger background state`() {
        // Arrange: Start two activities
        appLock.onActivityStarted(mockActivity)
        appLock.onActivityStarted(mockActivity)

        // Stop one activity (app still in foreground)
        appLock.onActivityStopped(mockActivity)

        // Start a new activity - should not require PIN
        authManager.updateAuthState(AuthState.LOGGED_IN)
        `when`(mockPinManager.isPinSet()).thenReturn(true)

        appLock.onActivityStarted(mockActivity)

        // Assert: No PIN required because app never went to background
        verify(spyApplication, never()).startActivity(any())
    }

    @Test
    fun `onActivityStarted does not launch second LoginActivity if one is already being started`() {
        // Arrange: Token in soft expiry
        authManager.updateAuthState(AuthState.LOGGED_IN)
        authManager.setIsSoftExpiry(true)
        
        // 1. First activity starts (Transition from 0 to 1 activity - App comes to foreground)
        appLock.onActivityStarted(mockActivity)
        verify(spyApplication, times(1)).startActivity(any()) // First launch

        // 2. Second activity starts (e.g. if multiple activities are in the stack)
        // startedActivities is now 2. shouldRequirePin is now false.
        appLock.onActivityStarted(mockActivity)

        // Assert: Verify startActivity was NOT called a second time
        verify(spyApplication, times(1)).startActivity(any())
    }
}
