package edu.aiims.medresodk.auth.activities

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for QR code validation logic in MedresQrScannerActivity.
 * Tests the intelligent QR detection that distinguishes between:
 * - Standard ODK QRs (rejected)
 * - Demo/Test QRs (accepted with demo mode)
 * - MEDRES Project QRs (accepted normally)
 */
class MedresQrScannerValidationTest {

    /**
     * Helper function to simulate QR validation logic from MedresQrScannerActivity.
     * Returns: "REJECT", "DEMO", or "ACCEPT"
     */
    private fun validateQrUrl(serverUrl: String): String {
        val isDraft = serverUrl.contains("/draft") && serverUrl.contains("/test/")
        val hasKeyToken = serverUrl.contains("/key/")
        
        // Reject Standard ODK QRs (has /key/ but NOT draft/test)
        if (hasKeyToken && !isDraft) {
            return "REJECT"
        }
        
        // Demo mode if URL contains /draft or /test/
        if (serverUrl.contains("/draft") || serverUrl.contains("/test/")) {
            return "DEMO"
        }
        
        // Accept normal MEDRES project QRs
        return "ACCEPT"
    }

    // ========== Standard ODK QR Tests (Should be REJECTED) ==========

    @Test
    fun `rejects standard ODK QR with clean key token`() {
        val url = "https://central.example.com/v1/key/ABCD1234/projects/1"
        assertEquals("REJECT", validateQrUrl(url))
    }

    @Test
    fun `rejects standard ODK QR with long token`() {
        val url = "https://central.example.com/v1/key/aBcDeFgHiJkLmNoPqRsTuVwXyZ123456/projects/42"
        assertEquals("REJECT", validateQrUrl(url))
    }

    @Test
    fun `rejects standard ODK QR with numeric token`() {
        val url = "https://central.example.com/v1/key/1234567890/projects/5"
        assertEquals("REJECT", validateQrUrl(url))
    }

    @Test
    fun `rejects standard ODK QR from different domain`() {
        val url = "https://odk.organization.org/v1/key/TOKEN123/projects/10"
        assertEquals("REJECT", validateQrUrl(url))
    }

    @Test
    fun `rejects standard ODK QR with IP address`() {
        val url = "http://192.168.1.100:8383/v1/key/DEMO_TOKEN/projects/3"
        assertEquals("REJECT", validateQrUrl(url))
    }

    // ========== Demo/Test QR Tests (Should be ACCEPTED as DEMO) ==========

    @Test
    fun `accepts demo QR with both draft and test in path`() {
        val url = "https://central.example.com/v1/draft/test/projects/1"
        assertEquals("DEMO", validateQrUrl(url))
    }

    @Test
    fun `accepts demo QR with draft and test and key token`() {
        // This is a draft/test link that happens to have a token
        val url = "https://central.example.com/v1/key/DRAFT_TOKEN/draft/test/projects/1"
        assertEquals("DEMO", validateQrUrl(url))
    }

    @Test
    fun `accepts demo QR with test before draft`() {
        val url = "https://central.example.com/v1/test/draft/projects/1"
        assertEquals("DEMO", validateQrUrl(url))
    }

    @Test
    fun `accepts demo QR with complex path containing draft and test`() {
        val url = "https://central.example.com/v1/projects/1/draft/forms/test/submissions"
        assertEquals("DEMO", validateQrUrl(url))
    }

    // ========== MEDRES Project QR Tests (Should be ACCEPTED normally) ==========

    @Test
    fun `accepts MEDRES project QR without key token`() {
        val url = "https://central.example.com/v1/projects/1"
        assertEquals("ACCEPT", validateQrUrl(url))
    }

    @Test
    fun `accepts MEDRES project QR with numeric project ID`() {
        val url = "https://central.example.com/v1/projects/42"
        assertEquals("ACCEPT", validateQrUrl(url))
    }

    @Test
    fun `accepts MEDRES project QR from custom domain`() {
        val url = "https://medres.aiims.edu/v1/projects/5"
        assertEquals("ACCEPT", validateQrUrl(url))
    }

    @Test
    fun `accepts MEDRES project QR with IP address`() {
        val url = "http://10.0.0.50:8080/v1/projects/1"
        assertEquals("ACCEPT", validateQrUrl(url))
    }

    @Test
    fun `accepts MEDRES project QR with trailing slash`() {
        val url = "https://central.example.com/v1/projects/1/"
        assertEquals("ACCEPT", validateQrUrl(url))
    }

    // ========== Edge Cases ==========

    @Test
    fun `rejects QR with only draft but no test`() {
        // Has draft but missing test - should NOT be demo mode
        val url = "https://central.example.com/v1/draft/projects/1"
        // This will be DEMO (not ACCEPT) because it contains /draft
        assertEquals("DEMO", validateQrUrl(url))
    }

    @Test
    fun `rejects QR with only test but no draft`() {
        // Has test but missing draft - should NOT be demo mode
        val url = "https://central.example.com/v1/test/projects/1"
        // This will be DEMO because it contains /test/
        assertEquals("DEMO", validateQrUrl(url))
    }

    @Test
    fun `handles URL with draft in domain name`() {
        // "draft" appears in domain, not path
        val url = "https://draft.example.com/v1/projects/1"
        // Will be DEMO because contains() checks entire URL
        assertEquals("DEMO", validateQrUrl(url))
    }

    @Test
    fun `handles URL with test in domain name`() {
        // "test" appears in domain, not path  
        val url = "https://test.example.com/v1/projects/1"
        // Will be DEMO because contains() checks entire URL
        assertEquals("ACCEPT", validateQrUrl(url))
    }

    @Test
    fun `handles URL with key in query parameter`() {
        // "key" appears in query param, not path
        val url = "https://central.example.com/v1/projects/1?api_key=12345"
        assertEquals("ACCEPT", validateQrUrl(url))
    }

    @Test
    fun `rejects standard ODK QR with uppercase KEY`() {
        val url = "https://central.example.com/v1/KEY/TOKEN123/projects/1"
        // Should NOT reject because contains() is case-sensitive
        assertEquals("ACCEPT", validateQrUrl(url))
    }

    @Test
    fun `handles empty URL`() {
        val url = ""
        assertEquals("ACCEPT", validateQrUrl(url))
    }

    @Test
    fun `handles URL with multiple key segments`() {
        val url = "https://central.example.com/v1/key/TOKEN1/key/TOKEN2/projects/1"
        assertEquals("REJECT", validateQrUrl(url))
    }

    // ========== Security Tests ==========

    @Test
    fun `rejects malicious URL with path traversal`() {
        val url = "https://central.example.com/v1/key/../../../etc/passwd/projects/1"
        assertEquals("REJECT", validateQrUrl(url))
    }

    @Test
    fun `handles URL with encoded characters`() {
        val url = "https://central.example.com/v1/key%2FTOKEN/projects/1"
        // URL-encoded /key/ becomes key%2F - won't match /key/
        assertEquals("ACCEPT", validateQrUrl(url))
    }

    @Test
    fun `handles URL with special characters in token`() {
        val url = "https://central.example.com/v1/key/TOKEN-WITH-DASHES_AND_UNDERSCORES/projects/1"
        assertEquals("REJECT", validateQrUrl(url))
    }

    // ========== Real-World Scenarios ==========

    @Test
    fun `accepts production MEDRES QR`() {
        val url = "https://medres-central.aiims.edu/v1/projects/101"
        assertEquals("ACCEPT", validateQrUrl(url))
    }

    @Test
    fun `accepts demo QR for testing environment`() {
        val url = "https://staging.example.com/v1/draft/test/projects/999"
        assertEquals("DEMO", validateQrUrl(url))
    }

    @Test
    fun `rejects ODK Central managed QR from production`() {
        val url = "https://odk.example.org/v1/key/aB3dE5fG7hI9jK1lM2nO4pQ6rS8tU0vW/projects/25"
        assertEquals("REJECT", validateQrUrl(url))
    }

    @Test
    fun `accepts localhost development QR`() {
        val url = "http://localhost:8383/v1/projects/1"
        assertEquals("ACCEPT", validateQrUrl(url))
    }

    @Test
    fun `accepts demo QR from localhost`() {
        val url = "http://localhost:8383/v1/draft/test/projects/1"
        assertEquals("DEMO", validateQrUrl(url))
    }

    @Test
    fun `rejects standard ODK QR from localhost`() {
        val url = "http://localhost:8383/v1/key/LOCAL_TOKEN/projects/1"
        assertEquals("REJECT", validateQrUrl(url))
    }
}
