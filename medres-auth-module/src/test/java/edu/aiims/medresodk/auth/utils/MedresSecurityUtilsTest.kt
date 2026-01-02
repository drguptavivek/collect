package edu.aiims.medresodk.auth.utils

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.not
import org.hamcrest.Matchers.notNullValue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import javax.crypto.spec.SecretKeySpec

@RunWith(AndroidJUnit4::class)
class MedresSecurityUtilsTest {

    private lateinit var securityUtils: MedresSecurityUtils
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        securityUtils = MedresSecurityUtils.getInstance(context)
    }

    @Test
    fun `#generateSalt returns non-empty string`() {
        val salt = securityUtils.generateSalt()
        assertThat(salt, notNullValue())
        assertThat(salt.isNotEmpty(), equalTo(true))
    }

    @Test
    fun `#hashPin returns consistent hash`() {
        val pin = "1234"
        val salt = securityUtils.generateSalt()

        val hash1 = securityUtils.hashPin(pin, salt)
        val hash2 = securityUtils.hashPin(pin, salt)

        assertThat(hash1, equalTo(hash2))
    }

    @Test
    fun `#hashPin returns different hash for different salts`() {
        val pin = "1234"
        val salt1 = securityUtils.generateSalt()
        val salt2 = securityUtils.generateSalt()

        val hash1 = securityUtils.hashPin(pin, salt1)
        val hash2 = securityUtils.hashPin(pin, salt2)

        // Theoretically possible to collide, but astronomically unlikely
        assertThat(hash1, not(equalTo(hash2)))
    }

    @Test
    fun `#verifyPin returns true for correct PIN`() {
        val pin = "1234"
        val salt = securityUtils.generateSalt()
        val hash = securityUtils.hashPin(pin, salt)

        assertThat(securityUtils.verifyPin(pin, hash, salt), equalTo(true))
    }

    @Test
    fun `#verifyPin returns false for incorrect PIN`() {
        val pin = "1234"
        val salt = securityUtils.generateSalt()
        val hash = securityUtils.hashPin(pin, salt)

        assertThat(securityUtils.verifyPin("9999", hash, salt), equalTo(false))
    }

    @Test
    fun `#encrypt and #decrypt round-trip works`() {
        // We'll manually create a key since Keystore operations might be flaky in pure Robolectric
        // without more complex shadowing, but let's try the pure crypto logic first.

        // Generate a standard AES Key for testing (bypassing Keystore for this specific test to isolate logic)
        val keyBytes = ByteArray(32) { i -> i.toByte() }
        val secretKey = SecretKeySpec(keyBytes, "AES")

        val originalText = "Sensitive Data 123"
        val encrypted = securityUtils.encrypt(originalText, secretKey)
        val decrypted = securityUtils.decrypt(encrypted, secretKey)

        assertThat(decrypted, equalTo(originalText))
    }

    @Test
    fun `#validatePinStrength checks rules correctly`() {
        assertThat(securityUtils.validatePinStrength("123"), equalTo(PinStrength.TOO_SHORT))
        assertThat(securityUtils.validatePinStrength("1".repeat(50)), equalTo(PinStrength.TOO_LONG))
        assertThat(securityUtils.validatePinStrength("1111"), equalTo(PinStrength.TOO_SIMPLE))
        assertThat(securityUtils.validatePinStrength("1234"), equalTo(PinStrength.WEAK)) // Matches \d+
        assertThat(securityUtils.validatePinStrength("abcd"), equalTo(PinStrength.MEDIUM)) // Matches alphanumeric
        assertThat(securityUtils.validatePinStrength("Pin!"), equalTo(PinStrength.STRONG)) // Special chars
    }

    // --- Negative Tests ---

    @Test(expected = javax.crypto.AEADBadTagException::class)
    fun `#decrypt throws exception with wrong key`() {
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
    fun `#verifyPin throws on invalid salt`() {
        securityUtils.verifyPin("1234", "someHash", "NotBase64!!")
    }
}
