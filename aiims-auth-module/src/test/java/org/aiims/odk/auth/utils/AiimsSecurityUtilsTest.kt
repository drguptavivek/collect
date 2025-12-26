package org.aiims.odk.auth.utils

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import javax.crypto.spec.SecretKeySpec

@RunWith(AndroidJUnit4::class)
class AiimsSecurityUtilsTest {

    private lateinit var securityUtils: AiimsSecurityUtils
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        securityUtils = AiimsSecurityUtils.getInstance(context)
    }

    @Test
    fun generateSalt_returnsNonEmptyString() {
        val salt = securityUtils.generateSalt()
        assertNotNull(salt)
        assertTrue(salt.isNotEmpty())
    }

    @Test
    fun hashPin_returnsConsistentHash() {
        val pin = "1234"
        val salt = securityUtils.generateSalt()

        val hash1 = securityUtils.hashPin(pin, salt)
        val hash2 = securityUtils.hashPin(pin, salt)

        assertEquals(hash1, hash2)
    }

    @Test
    fun hashPin_returnsDifferentHashForDifferentSalts() {
        val pin = "1234"
        val salt1 = securityUtils.generateSalt()
        val salt2 = securityUtils.generateSalt()

        val hash1 = securityUtils.hashPin(pin, salt1)
        val hash2 = securityUtils.hashPin(pin, salt2)

        // Theoretically possible to collide, but astronomically unlikely
        assertFalse(hash1 == hash2)
    }

    @Test
    fun verifyPin_returnsTrueForCorrectPin() {
        val pin = "1234"
        val salt = securityUtils.generateSalt()
        val hash = securityUtils.hashPin(pin, salt)

        assertTrue(securityUtils.verifyPin(pin, hash, salt))
    }

    @Test
    fun verifyPin_returnsFalseForIncorrectPin() {
        val pin = "1234"
        val salt = securityUtils.generateSalt()
        val hash = securityUtils.hashPin(pin, salt)

        assertFalse(securityUtils.verifyPin("9999", hash, salt))
    }

    @Test
    fun encryptDecrypt_roundTripWorks() {
        // We'll manually create a key since Keystore operations might be flaky in pure Robolectric
        // without more complex shadowing, but let's try the pure crypto logic first.

        // Generate a standard AES Key for testing (bypassing Keystore for this specific test to isolate logic)
        val keyBytes = ByteArray(32) { i -> i.toByte() }
        val secretKey = SecretKeySpec(keyBytes, "AES")

        val originalText = "Sensitive Data 123"
        val encrypted = securityUtils.encrypt(originalText, secretKey)
        val decrypted = securityUtils.decrypt(encrypted, secretKey)

        assertEquals(originalText, decrypted)
    }

    @Test
    fun validatePinStrength_checkRules() {
        assertEquals(PinStrength.TOO_SHORT, securityUtils.validatePinStrength("123"))
        assertEquals(PinStrength.TOO_LONG, securityUtils.validatePinStrength("1".repeat(50)))
        assertEquals(PinStrength.TOO_SIMPLE, securityUtils.validatePinStrength("1111"))
        assertEquals(PinStrength.WEAK, securityUtils.validatePinStrength("1234")) // Matches \d+
        assertEquals(PinStrength.MEDIUM, securityUtils.validatePinStrength("abcd")) // Matches alphanumeric
        assertEquals(PinStrength.STRONG, securityUtils.validatePinStrength("Pin!")) // Special chars
    }

    // --- Negative Tests ---

    @Test(expected = javax.crypto.AEADBadTagException::class)
    fun decrypt_throwsExceptionWithWrongKey() {
        // Generate two different keys
        val keyBytes1 = ByteArray(32) { i -> i.toByte() }
        val key1 = SecretKeySpec(keyBytes1, "AES")

        val keyBytes2 = ByteArray(32) { i -> (i + 1).toByte() }
        val key2 = SecretKeySpec(keyBytes2, "AES")

        val originalText = "Secret Data"
        val encrypted = securityUtils.encrypt(originalText, key1)

        // Try to decrypt with wrong key - should fail GCM authentication
        securityUtils.decrypt(encrypted, key2)
    }

    @Test(expected = IllegalArgumentException::class)
    fun verifyPin_throwsOnInvalidSalt() {
        securityUtils.verifyPin("1234", "someHash", "NotBase64!!")
    }
}
