package edu.aiims.medresodk.auth.utils

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import edu.aiims.medresodk.auth.api.AuthClient
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.nullValue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class TokenRevocationManagerTest {

    private lateinit var context: Context
    private val authClient: AuthClient = mock()
    private val testDispatcher = UnconfinedTestDispatcher()

    private val PREFS_NAME = "medres_auth_prefs"
    private val KEY_PENDING_PROJECT_ID = "pending_revoke_project_id"

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()

        // Reset state
        TokenRevocationManager.resetForTesting()
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()

        // Inject mocks
        TokenRevocationManager.setAuthClient(authClient)
        TokenRevocationManager.setIoDispatcher(testDispatcher)
        TokenRevocationManager.setNetworkAvailable(true) // Default to online
    }

    @Test
    fun `#markPending saves to shared preferences`() {
        // Act
        TokenRevocationManager.markPending(context, "p1", "u1", "url", "token", "logout")

        // Assert
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        assertThat(prefs.getString(KEY_PENDING_PROJECT_ID, null), equalTo("p1"))
    }

    @Test
    fun `#processPending does nothing when offline`() = runTest {
        // Arrange
        TokenRevocationManager.markPending(context, "p1", "u1", "url", "token", "logout")
        TokenRevocationManager.setNetworkAvailable(false) // Offline

        // Act
        val result = TokenRevocationManager.processPending(context)

        // Assert
        assertThat(result, equalTo(false))
        verify(authClient, never()).revokeSession(any(), any(), any(), any())

        // Ensure data is still pending
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        assertThat(prefs.getString(KEY_PENDING_PROJECT_ID, null), equalTo("p1"))
    }

    @Test
    fun `#processPending calls revoke and clears when success`() = runTest {
        // Arrange
        TokenRevocationManager.markPending(context, "p1", "u1", "url", "token", "logout")
        TokenRevocationManager.setNetworkAvailable(true) // Online
        whenever(authClient.revokeSession(any(), any(), any(), any())).thenReturn(true)

        // Act
        val result = TokenRevocationManager.processPending(context)

        // Assert
        assertThat(result, equalTo(true))
        verify(authClient).revokeSession(org.mockito.kotlin.eq("p1"), org.mockito.kotlin.eq("u1"), org.mockito.kotlin.eq("token"), any())

        // Ensure data is cleared
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        assertThat(prefs.getString(KEY_PENDING_PROJECT_ID, null), nullValue())
    }

    @Test
    fun `#processPending keeps data when failure`() = runTest {
        // Arrange
        TokenRevocationManager.markPending(context, "p1", "u1", "url", "token", "logout")
        TokenRevocationManager.setNetworkAvailable(true) // Online
        whenever(authClient.revokeSession(any(), any(), any(), any())).thenReturn(false) // Fail

        // Act
        val result = TokenRevocationManager.processPending(context)

        // Assert
        assertThat(result, equalTo(false))
        verify(authClient).revokeSession(org.mockito.kotlin.eq("p1"), org.mockito.kotlin.eq("u1"), org.mockito.kotlin.eq("token"), any())

        // Ensure data is RETAINED for retry
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        assertThat(prefs.getString(KEY_PENDING_PROJECT_ID, null), equalTo("p1"))
    }
}
