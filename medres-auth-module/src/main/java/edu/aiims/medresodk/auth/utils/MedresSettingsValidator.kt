package edu.aiims.medresodk.auth.utils

import org.odk.collect.settings.keys.ProjectKeys
import org.odk.collect.settings.keys.ProtectedProjectKeys

/**
 * Validates setting keys from QR codes against known ProjectKeys and ProtectedProjectKeys.
 * Prevents invalid or malicious keys from being applied to app preferences.
 */
object MedresSettingsValidator {

    /**
     * Set of valid general setting keys extracted from ProjectKeys using reflection.
     * ProjectKeys doesn't have an allKeys() method, so we extract all KEY_* constants.
     */
    private val validGeneralKeys: Set<String> by lazy {
        ProjectKeys::class.java.declaredFields
            .filter { it.name.startsWith("KEY_") }
            .mapNotNull {
                try {
                    it.get(null) as? String
                } catch (_: Exception) {
                    null
                }
            }
            .toSet()
    }

    /**
     * Set of valid admin setting keys from ProtectedProjectKeys.allKeys().
     */
    private val validAdminKeys: Set<String> by lazy {
        ProtectedProjectKeys.allKeys().toSet()
    }

    fun isValidGeneralKey(key: String): Boolean = key in validGeneralKeys

    fun isValidAdminKey(key: String): Boolean = key in validAdminKeys

    fun getValidGeneralKeysCount(): Int = validGeneralKeys.size

    fun getValidAdminKeysCount(): Int = validAdminKeys.size
}
