package org.aiims.odk.auth.utils

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
    fun savePin_storesPinAndResetsAttempts() {
        // Arrange
        val pin = "1234"

        // Act
        pinManager.savePin(pin)

        // Assert
        assertTrue(pinManager.isPinSet())
        assertTrue(pinManager.verifyPin(pin))
        assertEquals(0, pinManager.getFailedAttempts())
    }

    @Test
    fun verifyPin_success_resetsAttempts() {
        // Arrange
        pinManager.savePin("1234")
        // Simulate a failed attempt first
        pinManager.verifyPin("0000") // 1 failure
        assertEquals(1, pinManager.getFailedAttempts())

        // Act
        val result = pinManager.verifyPin("1234")

        // Assert
        assertTrue(result)
        assertEquals("Successful verification should clear attempts", 0, pinManager.getFailedAttempts())
    }

    @Test
    fun verifyPin_failure_incrementsAttempts() {
        // Arrange
        pinManager.savePin("1234")

        // Act & Assert
        assertFalse(pinManager.verifyPin("0000"))
        assertEquals(1, pinManager.getFailedAttempts())

        assertFalse(pinManager.verifyPin("9999"))
        assertEquals(2, pinManager.getFailedAttempts())
    }

    @Test
    fun isMaxAttemptsReached_returnsTrueAfterLimit() {
        // Arrange
        pinManager.savePin("1234")

        // Act: Fail 3 times
        pinManager.verifyPin("0000") // 1
        assertFalse(pinManager.isMaxAttemptsReached())

        pinManager.verifyPin("0000") // 2
        assertFalse(pinManager.isMaxAttemptsReached())

        pinManager.verifyPin("0000") // 3

        // Assert
        assertTrue("Should be locked out after 3 attempts", pinManager.isMaxAttemptsReached())
    }

    @Test
    fun clearPin_removesAllData() {
        // Arrange
        pinManager.savePin("1234")
        pinManager.verifyPin("0000") // Add some dirty state (attempts)

        // Act
        pinManager.clearPin()

        // Assert
        assertFalse(pinManager.isPinSet())
        assertEquals(0, pinManager.getFailedAttempts())
        // Verify underlying prefs are actually gone logic (implied by isPinSet)
    }
}
