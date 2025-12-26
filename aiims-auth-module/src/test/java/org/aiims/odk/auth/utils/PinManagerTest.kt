package org.aiims.odk.auth.utils

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PinManagerTest {

    private lateinit var context: Context
    private lateinit var pinManager: PinManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        // Reset Singleton and Prefs to ensure clean state
        PinManager.resetInstanceForTesting()
        context.getSharedPreferences("aiims_auth_prefs", Context.MODE_PRIVATE).edit().clear().commit()

        pinManager = PinManager.getInstance(context)
    }

    @Test
    fun `#savePin stores PIN and resets attempts`() {
        // Arrange
        val pin = "1234"

        // Act
        pinManager.savePin(pin)

        // Assert
        assertThat(pinManager.isPinSet(), equalTo(true))
        assertThat(pinManager.verifyPin(pin), equalTo(true))
        assertThat(pinManager.getFailedAttempts(), equalTo(0))
    }

    @Test
    fun `#verifyPin resets attempts when success`() {
        // Arrange
        pinManager.savePin("1234")
        // Simulate a failed attempt first
        pinManager.verifyPin("0000") // 1 failure
        assertThat(pinManager.getFailedAttempts(), equalTo(1))

        // Act
        val result = pinManager.verifyPin("1234")

        // Assert
        assertThat(result, equalTo(true))
        assertThat(pinManager.getFailedAttempts(), equalTo(0))
    }

    @Test
    fun `#verifyPin increments attempts when failure`() {
        // Arrange
        pinManager.savePin("1234")

        // Act & Assert
        assertThat(pinManager.verifyPin("0000"), equalTo(false))
        assertThat(pinManager.getFailedAttempts(), equalTo(1))

        assertThat(pinManager.verifyPin("9999"), equalTo(false))
        assertThat(pinManager.getFailedAttempts(), equalTo(2))
    }

    @Test
    fun `#isMaxAttemptsReached returns true after limit`() {
        // Arrange
        pinManager.savePin("1234")

        // Act: Fail 3 times
        pinManager.verifyPin("0000") // 1
        assertThat(pinManager.isMaxAttemptsReached(), equalTo(false))

        pinManager.verifyPin("0000") // 2
        assertThat(pinManager.isMaxAttemptsReached(), equalTo(false))

        pinManager.verifyPin("0000") // 3

        // Assert
        assertThat(pinManager.isMaxAttemptsReached(), equalTo(true))
    }

    @Test
    fun `#clearPin removes all data`() {
        // Arrange
        pinManager.savePin("1234")
        pinManager.verifyPin("0000") // Add some dirty state (attempts)

        // Act
        pinManager.clearPin()

        // Assert
        assertThat(pinManager.isPinSet(), equalTo(false))
        assertThat(pinManager.getFailedAttempts(), equalTo(0))
    }
}
