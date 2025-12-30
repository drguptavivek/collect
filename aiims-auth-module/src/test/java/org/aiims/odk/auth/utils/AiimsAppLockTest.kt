package org.aiims.odk.auth.utils

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.aiims.odk.auth.activities.AiimsLoginActivity
import org.aiims.odk.auth.activities.PinEntryActivity
import org.aiims.odk.auth.managers.AiimsAuthManager
import org.aiims.odk.auth.managers.AuthState
import org.aiims.odk.auth.managers.ProjectCleaner
import org.aiims.odk.auth.storage.FakeAiimsAuthStorage
import org.aiims.odk.auth.storage.FakeAiimsSecureStorage
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.any
import org.mockito.Mockito.never
import org.mockito.Mockito.spy
import org.mockito.Mockito.verify
import org.mockito.MockitoAnnotations
import org.robolectric.annotation.Config

/**
 * Unit tests for AiimsAppLock.
 * 
 * Uses Real AiimsAuthManager with Fakes to avoid mocking final classes.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [28])
class AiimsAppLockTest {

    private lateinit var application: Application

    // Use Real AuthManager with Fakes
    private lateinit var authManager: AiimsAuthManager
    private lateinit var authStorage: FakeAiimsAuthStorage
    private lateinit var secureStorage: FakeAiimsSecureStorage
    
    @Mock
    private lateinit var mockProjectCleaner: ProjectCleaner

    @Mock
    private lateinit var mockPinManager: PinManager

    @Mock
    private lateinit var mockActivity: Activity

    @Mock
    private lateinit var mockPinEntryActivity: PinEntryActivity

    @Mock
    private lateinit var mockLoginActivity: AiimsLoginActivity

    private lateinit var appLock: AiimsAppLock
    private lateinit var spyApplication: Application

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        application = ApplicationProvider.getApplicationContext()
        spyApplication = spy(application)
        
        // Setup Fakes
        authStorage = FakeAiimsAuthStorage()
        secureStorage = FakeAiimsSecureStorage()
        
        // Initialize Real AuthManager
        authManager = AiimsAuthManager(
            context = application,
            projectCleaner = mockProjectCleaner,
            pinManager = mockPinManager,
            authStorage = authStorage,
            secureStorage = secureStorage,
            telemetryDao = org.aiims.odk.auth.fakes.FakeTelemetryDao()
        )
        
        appLock = AiimsAppLock(spyApplication, authManager, mockPinManager)
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
}
