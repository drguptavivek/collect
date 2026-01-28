package edu.aiims.medresodk.auth.utils

import org.junit.Assert.*
import org.junit.Test
import org.odk.collect.settings.keys.ProjectKeys
import org.odk.collect.settings.keys.ProtectedProjectKeys

/**
 * Unit tests for MedresSettingsValidator.
 * Verifies that QR code setting keys are properly validated against known constants.
 */
class MedresSettingsValidatorTest {

    @Test
    fun `isValidGeneralKey returns true for valid ProjectKeys constants`() {
        // Test a sample of known valid general keys
        assertTrue(MedresSettingsValidator.isValidGeneralKey(ProjectKeys.KEY_AUTOSEND))
        assertTrue(MedresSettingsValidator.isValidGeneralKey(ProjectKeys.KEY_NAVIGATION))
        assertTrue(MedresSettingsValidator.isValidGeneralKey(ProjectKeys.KEY_IMAGE_SIZE))
        assertTrue(MedresSettingsValidator.isValidGeneralKey(ProjectKeys.KEY_FORM_UPDATE_MODE))
        assertTrue(MedresSettingsValidator.isValidGeneralKey(ProjectKeys.KEY_SERVER_URL))
        assertTrue(MedresSettingsValidator.isValidGeneralKey(ProjectKeys.KEY_USERNAME))
        assertTrue(MedresSettingsValidator.isValidGeneralKey(ProjectKeys.KEY_PASSWORD))
        assertTrue(MedresSettingsValidator.isValidGeneralKey(ProjectKeys.KEY_PROTOCOL))
        assertTrue(MedresSettingsValidator.isValidGeneralKey(ProjectKeys.KEY_DELETE_AFTER_SEND))
        assertTrue(MedresSettingsValidator.isValidGeneralKey(ProjectKeys.KEY_ANALYTICS))
    }

    @Test
    fun `isValidGeneralKey returns false for invalid keys`() {
        // Test invalid/malicious keys
        assertFalse(MedresSettingsValidator.isValidGeneralKey("fake_setting"))
        assertFalse(MedresSettingsValidator.isValidGeneralKey("malicious_key"))
        assertFalse(MedresSettingsValidator.isValidGeneralKey("inject_value"))
        assertFalse(MedresSettingsValidator.isValidGeneralKey(""))
        assertFalse(MedresSettingsValidator.isValidGeneralKey("random_string_123"))
    }

    @Test
    fun `isValidGeneralKey returns false for admin keys`() {
        // Admin keys should not be valid as general keys
        assertFalse(MedresSettingsValidator.isValidGeneralKey(ProtectedProjectKeys.KEY_CHANGE_SERVER))
        assertFalse(MedresSettingsValidator.isValidGeneralKey(ProtectedProjectKeys.KEY_AUTOSEND))
        assertFalse(MedresSettingsValidator.isValidGeneralKey(ProtectedProjectKeys.KEY_DELETE_SAVED))
    }

    @Test
    fun `isValidAdminKey returns true for valid ProtectedProjectKeys constants`() {
        // Test a sample of known valid admin keys
        assertTrue(MedresSettingsValidator.isValidAdminKey(ProtectedProjectKeys.KEY_CHANGE_SERVER))
        assertTrue(MedresSettingsValidator.isValidAdminKey(ProtectedProjectKeys.KEY_AUTOSEND))
        assertTrue(MedresSettingsValidator.isValidAdminKey(ProtectedProjectKeys.KEY_DELETE_SAVED))
        assertTrue(MedresSettingsValidator.isValidAdminKey(ProtectedProjectKeys.KEY_EDIT_SAVED))
        assertTrue(MedresSettingsValidator.isValidAdminKey(ProtectedProjectKeys.KEY_SEND_FINALIZED))
        assertTrue(MedresSettingsValidator.isValidAdminKey(ProtectedProjectKeys.KEY_ACCESS_SETTINGS))
        assertTrue(MedresSettingsValidator.isValidAdminKey(ProtectedProjectKeys.KEY_MOVING_BACKWARDS))
    }

    @Test
    fun `isValidAdminKey returns false for invalid keys`() {
        // Test invalid/malicious admin keys
        assertFalse(MedresSettingsValidator.isValidAdminKey("bad_admin_key"))
        assertFalse(MedresSettingsValidator.isValidAdminKey("malicious_admin"))
        assertFalse(MedresSettingsValidator.isValidAdminKey("inject_admin"))
        assertFalse(MedresSettingsValidator.isValidAdminKey(""))
        assertFalse(MedresSettingsValidator.isValidAdminKey("fake_lock_setting"))
    }

    @Test
    fun `isValidAdminKey returns false for general keys`() {
        // General keys should not be valid as admin keys
        assertFalse(MedresSettingsValidator.isValidAdminKey(ProjectKeys.KEY_AUTOSEND))
        assertFalse(MedresSettingsValidator.isValidAdminKey(ProjectKeys.KEY_NAVIGATION))
        assertFalse(MedresSettingsValidator.isValidAdminKey(ProjectKeys.KEY_SERVER_URL))
    }

    @Test
    fun `getValidGeneralKeysCount returns positive count`() {
        // Should have extracted multiple keys from ProjectKeys
        val count = MedresSettingsValidator.getValidGeneralKeysCount()
        assertTrue("Expected at least 20 general keys, got $count", count >= 20)
    }

    @Test
    fun `getValidAdminKeysCount returns positive count`() {
        // Should have all keys from ProtectedProjectKeys.allKeys()
        val count = MedresSettingsValidator.getValidAdminKeysCount()
        val expected = ProtectedProjectKeys.allKeys().size
        assertEquals("Admin keys count should match ProtectedProjectKeys.allKeys()", expected, count)
    }

    @Test
    fun `validator handles case-sensitive keys correctly`() {
        // Keys are case-sensitive
        assertTrue(MedresSettingsValidator.isValidGeneralKey("autosend"))
        assertFalse(MedresSettingsValidator.isValidGeneralKey("AUTOSEND"))
        assertFalse(MedresSettingsValidator.isValidGeneralKey("AutoSend"))
    }

    @Test
    fun `validator handles special characters in keys`() {
        // Test keys with underscores (valid) vs other special chars (invalid)
        assertTrue(MedresSettingsValidator.isValidGeneralKey("form_update_mode"))
        assertFalse(MedresSettingsValidator.isValidGeneralKey("form-update-mode"))
        assertFalse(MedresSettingsValidator.isValidGeneralKey("form.update.mode"))
        assertFalse(MedresSettingsValidator.isValidGeneralKey("form update mode"))
    }

    @Test
    fun `validator rejects SQL injection attempts`() {
        // Security test: reject SQL-like injection strings
        assertFalse(MedresSettingsValidator.isValidGeneralKey("'; DROP TABLE users; --"))
        assertFalse(MedresSettingsValidator.isValidGeneralKey("1' OR '1'='1"))
        assertFalse(MedresSettingsValidator.isValidAdminKey("admin' OR '1'='1"))
    }

    @Test
    fun `validator rejects path traversal attempts`() {
        // Security test: reject path traversal strings
        assertFalse(MedresSettingsValidator.isValidGeneralKey("../../../etc/passwd"))
        assertFalse(MedresSettingsValidator.isValidGeneralKey("..\\..\\..\\windows\\system32"))
        assertFalse(MedresSettingsValidator.isValidAdminKey("../../admin_override"))
    }

    @Test
    fun `validator rejects script injection attempts`() {
        // Security test: reject script-like strings
        assertFalse(MedresSettingsValidator.isValidGeneralKey("<script>alert('xss')</script>"))
        assertFalse(MedresSettingsValidator.isValidGeneralKey("javascript:alert(1)"))
        assertFalse(MedresSettingsValidator.isValidAdminKey("<img src=x onerror=alert(1)>"))
    }

    @Test
    fun `all ProtectedProjectKeys are valid admin keys`() {
        // Verify every key from ProtectedProjectKeys.allKeys() is validated correctly
        val allAdminKeys = ProtectedProjectKeys.allKeys()
        for (key in allAdminKeys) {
            assertTrue("Admin key '$key' should be valid", MedresSettingsValidator.isValidAdminKey(key))
        }
    }

    @Test
    fun `common ProjectKeys are valid general keys`() {
        // Test all common project keys we expect to use
        val commonKeys = listOf(
            ProjectKeys.KEY_AUTOSEND,
            ProjectKeys.KEY_NAVIGATION,
            ProjectKeys.KEY_IMAGE_SIZE,
            ProjectKeys.KEY_FORM_UPDATE_MODE,
            ProjectKeys.KEY_DELETE_AFTER_SEND,
            ProjectKeys.KEY_CONSTRAINT_BEHAVIOR,
            ProjectKeys.KEY_HIGH_RESOLUTION,
            ProjectKeys.KEY_GUIDANCE_HINT,
            ProjectKeys.KEY_ANALYTICS,
            ProjectKeys.KEY_APP_LANGUAGE,
            ProjectKeys.KEY_FONT_SIZE,
            ProjectKeys.KEY_BASEMAP_SOURCE
        )
        
        for (key in commonKeys) {
            assertTrue("General key '$key' should be valid", MedresSettingsValidator.isValidGeneralKey(key))
        }
    }
}
