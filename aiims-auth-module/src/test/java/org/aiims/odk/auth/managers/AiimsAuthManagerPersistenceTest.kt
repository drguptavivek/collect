package org.aiims.odk.auth.managers

import android.content.Context
import android.content.SharedPreferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.aiims.odk.auth.api.AuthClient
import org.aiims.odk.auth.storage.AiimsAuthStorage
import org.aiims.odk.auth.storage.AiimsSecureStorage
import org.aiims.odk.auth.storage.db.TelemetryDao
import kotlinx.coroutines.flow.StateFlow
import org.aiims.odk.auth.utils.PinManager
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.kotlin.eq

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class AiimsAuthManagerPersistenceTest {

    private val context: Context = mock()
    private val prefs: SharedPreferences = mock()
    private val editor: SharedPreferences.Editor = mock()
    private val projectCleaner: ProjectCleaner = mock()
    private val pinManager: PinManager = mock()
    private val authStorage: AiimsAuthStorage = mock()
    private val secureStorage: AiimsSecureStorage = mock()
    private val telemetryDao: TelemetryDao = mock()
    private val authClient: AuthClient = mock()

    private lateinit var authManager: AiimsAuthManager

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        
        // Mock SharedPreferences
        whenever(context.getSharedPreferences(any(), any())).thenReturn(prefs)
        whenever(prefs.edit()).thenReturn(editor)
        whenever(editor.putLong(any(), any())).thenReturn(editor)
        whenever(editor.putString(any(), anyOrNull())).thenReturn(editor)
        whenever(editor.remove(any())).thenReturn(editor)
        whenever(editor.apply()).then {}
        
        // Default mocks
        whenever(authStorage.projectId).thenReturn("project-1")
        whenever(authStorage.deviceToken).thenReturn("valid-token")
        
        // Initialize Manager with Lazy projectCleaner
        authManager = AiimsAuthManager(
            context,
            { projectCleaner },
            pinManager,
            authStorage,
            secureStorage,
            telemetryDao
        )
        authManager.setAuthClient(authClient)
        authManager.setIoDispatcher(UnconfinedTestDispatcher())
    }

    @Test
    fun `snoozeSoftExpiry saves current timestamp to preferences`() = runTest {
        // GIVEN: Manager is initialized
        
        // WHEN: snoozeSoftExpiry is called
        authManager.snoozeSoftExpiry()
        
        // THEN: Current time is saved to prefs with correct key
        verify(editor).putLong(eq("last_soft_expiry_dismissal"), any())
        verify(editor).apply()
    }

    @Test
    fun `refreshState suppresses soft expiry if snoozed`() = runTest {
        // GIVEN: Token is expired but within grace period
        val expiry = System.currentTimeMillis() - 1000L
        whenever(authStorage.tokenExpiry).thenReturn(expiry)
        
        // GIVEN: Server is reachable
        whenever(authClient.checkReachability()).thenReturn(true)
        whenever(authStorage.deviceToken).thenReturn("valid-token")
        whenever(authStorage.projectId).thenReturn("project-1")
        
        // GIVEN: Dismissal occurred 5 minutes ago (Snoozed)
        val fiveMinutesAgo = System.currentTimeMillis() - (5 * 60 * 1000L)
        whenever(prefs.getLong(eq("last_soft_expiry_dismissal"), eq(0L))).thenReturn(fiveMinutesAgo)

        // WHEN: refreshState is triggered
        authManager.setActiveProject("project-1")
        advanceUntilIdle()
        
        // THEN: isSoftExpiry is FALSE
        assertThat(authManager.isSoftExpiry.value, equalTo(false))
    }
}
