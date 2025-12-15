package org.aiims.odk.auth.utils

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import java.security.KeyStore
import java.security.SecureRandom
import java.security.spec.KeySpec
import java.util.Base64
import java.util.UUID
import java.util.concurrent.Executor
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Security utilities for PIN hashing, encryption, and biometric authentication.
 *
 * Provides secure cryptographic operations for PIN storage, data encryption,
 * and biometric key management using Android Keystore.
 */
class AiimsSecurityUtils private constructor(
    private val context: Context
) {

    companion object {
        @Volatile
        private var INSTANCE: AiimsSecurityUtils? = null

        fun getInstance(context: Context): AiimsSecurityUtils {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: createInstance(context.applicationContext).also { INSTANCE = it }
            }
        }

        private fun createInstance(context: Context): AiimsSecurityUtils {
            return AiimsSecurityUtils(context)
        }

        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val AES_CIPHER_TRANSFORMATION = "AES/GCM/NoPadding"
        private const val PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256"
        private const val PBKDF2_ITERATIONS = 100000
        private const val SALT_LENGTH = 32
        private const val KEY_LENGTH = 256
        private const val IV_LENGTH = 12
    }

    private val secureRandom = SecureRandom()
    private val executor: Executor = ContextCompat.getMainExecutor(context)

    /**
     * Generate a secure random salt for PIN hashing.
     *
     * @return Base64 encoded salt
     */
    fun generateSalt(): String {
        val salt = ByteArray(SALT_LENGTH)
        secureRandom.nextBytes(salt)
        return Base64.getEncoder().encodeToString(salt)
    }

    /**
     * Hash PIN with salt using PBKDF2.
     *
     * @param PIN to hash
     * @param salt Base64 encoded salt
     * @return Base64 encoded hash
     */
    fun hashPin(pin: String, salt: String): String {
        val saltBytes = Base64.getDecoder().decode(salt)
        val spec: KeySpec = PBEKeySpec(
            pin.toCharArray(),
            saltBytes,
            PBKDF2_ITERATIONS,
            KEY_LENGTH
        )

        val factory = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM)
        val hash = factory.generateSecret(spec).encoded
        return Base64.getEncoder().encodeToString(hash)
    }

    /**
     * Verify PIN against stored hash.
     *
     * @param PIN to verify
     * @param storedHash Base64 encoded stored hash
     * @param salt Base64 encoded salt
     * @return true if PIN matches
     */
    fun verifyPin(pin: String, storedHash: String, salt: String): Boolean {
        val computedHash = hashPin(pin, salt)
        return computedHash == storedHash
    }

    /**
     * Generate a unique device ID.
     *
     * @return Unique device identifier
     */
    fun generateDeviceId(): String {
        return "android-${UUID.randomUUID()}"
    }

    /**
     * Generate a secure random session ID.
     *
     * @return Session identifier
     */
    fun generateSessionId(): String {
        return UUID.randomUUID().toString()
    }

    /**
     * Generate a random initialization vector for AES encryption.
     *
     * @return Random IV bytes
     */
    fun generateIv(): ByteArray {
        val iv = ByteArray(IV_LENGTH)
        secureRandom.nextBytes(iv)
        return iv
    }

    /**
     * Encrypt data using AES-GCM.
     *
     * @param data Data to encrypt
     * @param key AES key
     * @return Encrypted data with IV prepended
     */
    fun encrypt(data: String, key: SecretKey): String {
        val cipher = Cipher.getInstance(AES_CIPHER_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        val encryptedData = cipher.doFinal(data.toByteArray(Charsets.UTF_8))

        // Combine IV and encrypted data
        val combined = iv + encryptedData
        return Base64.getEncoder().encodeToString(combined)
    }

    /**
     * Decrypt data using AES-GCM.
     *
     * @param encryptedData Encrypted data with IV
     * @param key AES key
     * @return Decrypted data
     */
    fun decrypt(encryptedData: String, key: SecretKey): String {
        val combined = Base64.getDecoder().decode(encryptedData)

        // Extract IV and encrypted data
        val iv = combined.sliceArray(0 until IV_LENGTH)
        val data = combined.sliceArray(IV_LENGTH until combined.size)

        val cipher = Cipher.getInstance(AES_CIPHER_TRANSFORMATION)
        val spec = IvParameterSpec(iv)
        cipher.init(Cipher.DECRYPT_MODE, key, spec)

        val decryptedData = cipher.doFinal(data)
        return String(decryptedData, Charsets.UTF_8)
    }

    /**
     * Generate AES key from password.
     *
     * @param password Derivation password
     * @param salt Salt for derivation
     * @return AES secret key
     */
    fun generateAesKey(password: String, salt: ByteArray): SecretKey {
        val spec: KeySpec = PBEKeySpec(
            password.toCharArray(),
            salt,
            PBKDF2_ITERATIONS,
            KEY_LENGTH
        )

        val factory = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM)
        val keyBytes = factory.generateSecret(spec).encoded
        return SecretKeySpec(keyBytes, "AES")
    }

    /**
     * Store secret key in Android Keystore.
     *
     * @param alias Key alias
     * @param key Secret key to store
     * @return true if successful
     */
    fun storeKeyInKeystore(alias: String, key: SecretKey): Boolean {
        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
            keyStore.load(null)

            // Set key entry protection parameters
            val spec = KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setUserAuthenticationRequired(true)
                .setUserAuthenticationValidityDurationSeconds(30)
                .build()

            // Delete existing key if present
            if (keyStore.containsAlias(alias)) {
                keyStore.deleteEntry(alias)
            }

            keyStore.setEntry(
                alias,
                KeyStore.SecretKeyEntry(key),
                null
            )

            true
        } catch (e: Exception) {
            android.util.Log.e(AiimsConstants.TAG_AUTH, "Failed to store key in keystore", e)
            false
        }
    }

    /**
     * Get secret key from Android Keystore.
     *
     * @param alias Key alias
     * @return Secret key or null if not found
     */
    fun getKeyFromKeystore(alias: String): SecretKey? {
        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
            keyStore.load(null)

            val entry = keyStore.getEntry(alias, null) as? KeyStore.SecretKeyEntry
            entry?.secretKey
        } catch (e: Exception) {
            android.util.Log.e(AiimsConstants.TAG_AUTH, "Failed to get key from keystore", e)
            null
        }
    }

    /**
     * Check if biometric authentication is available.
     *
     * @return Biometric authentication status
     */
    fun checkBiometricAvailability(): BiometricStatus {
        val biometricManager = BiometricManager.from(context)
        return when (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)) {
            BiometricManager.BIOMETRIC_SUCCESS -> BiometricStatus.AVAILABLE
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> BiometricStatus.NO_HARDWARE
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> BiometricStatus.HW_UNAVAILABLE
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricStatus.NONE_ENROLLED
            BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED -> BiometricStatus.SECURITY_UPDATE_REQUIRED
            BiometricManager.BIOMETRIC_ERROR_UNSUPPORTED -> BiometricStatus.UNSUPPORTED
            BiometricManager.BIOMETRIC_STATUS_UNKNOWN -> BiometricStatus.UNKNOWN
            else -> BiometricStatus.UNKNOWN
        }
    }

    /**
     * Show biometric authentication prompt.
     *
     * @param activity Host activity
     * @param title Prompt title
     * @param subtitle Prompt subtitle
     * @param onSuccess Callback on successful authentication
     * @param onFailure Callback on failed authentication
     * @param onError Callback on error
     */
    fun showBiometricPrompt(
        activity: FragmentActivity,
        title: String = AiimsConstants.BIOMETRIC_PROMPT_TITLE,
        subtitle: String = AiimsConstants.BIOMETRIC_PROMPT_SUBTITLE,
        onSuccess: () -> Unit,
        onFailure: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setNegativeButtonText(AiimsConstants.BIOMETRIC_PROMPT_NEGATIVE)
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .build()

        val biometricPrompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    onSuccess()
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    onFailure()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    onError(errString.toString())
                }
            }
        )

        biometricPrompt.authenticate(promptInfo)
    }

    /**
     * Clear sensitive data from memory.
     *
     * @param data CharArray to clear
     */
    fun clearSensitiveData(data: CharArray) {
        data.fill('\u0000')
    }

    /**
     * Validate PIN strength.
     *
     * @param PIN to validate
     * @return PIN strength validation result
     */
    fun validatePinStrength(pin: String): PinStrength {
        return when {
            pin.length < AiimsConstants.PIN_LENGTH_MIN -> PinStrength.TOO_SHORT
            pin.length > AiimsConstants.PIN_LENGTH_MAX -> PinStrength.TOO_LONG
            pin.all { it == pin[0] } -> PinStrength.TOO_SIMPLE
            pin.matches(Regex("\\d+")) && pin.length >= 4 -> PinStrength.WEAK
            pin.matches(Regex("^[a-zA-Z0-9]{4,}$")) -> PinStrength.MEDIUM
            pin.matches(Regex("^[a-zA-Z0-9!@#$%^&*()_+]{4,}$")) -> PinStrength.STRONG
            else -> PinStrength.INVALID
        }
    }

    /**
     * Generate a secure random API key.
     *
     * @param length Key length in bytes
     * @return Base64 encoded API key
     */
    fun generateApiKey(length: Int = 32): String {
        val key = ByteArray(length)
        secureRandom.nextBytes(key)
        return Base64.getEncoder().encodeToString(key)
    }
}

/**
 * Biometric authentication status.
 */
enum class BiometricStatus {
    AVAILABLE,
    NO_HARDWARE,
    HW_UNAVAILABLE,
    NONE_ENROLLED,
    SECURITY_UPDATE_REQUIRED,
    UNSUPPORTED,
    UNKNOWN
}

/**
 * PIN strength validation result.
 */
enum class PinStrength {
    TOO_SHORT,
    TOO_LONG,
    TOO_SIMPLE,
    WEAK,
    MEDIUM,
    STRONG,
    INVALID
}