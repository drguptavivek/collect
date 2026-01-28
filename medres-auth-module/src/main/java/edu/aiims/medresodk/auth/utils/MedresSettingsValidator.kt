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
                } catch (e: Exception) { 
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
    
    /**
     * Checks if a general setting key is valid.
     * @param key The setting key to validate
     * @return true if the key exists in ProjectKeys, false otherwise
     */
    fun isValidGeneralKey(key: String): Boolean = key in validGeneralKeys
    
    /**
     * Checks if an admin setting key is valid.
     * @param key The admin setting key to validate
     * @return true if the key exists in ProtectedProjectKeys, false otherwise
     */
    fun isValidAdminKey(key: String): Boolean = key in validAdminKeys
    
    /**
     * Returns the count of valid general keys (for debugging/testing).
     */
    fun getValidGeneralKeysCount(): Int = validGeneralKeys.size
    
    /**
     * Returns the count of valid admin keys (for debugging/testing).
     */
    fun getValidAdminKeysCount(): Int = validAdminKeys.size
}
