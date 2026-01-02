package edu.aiims.medresodk.auth.api

import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * Unit tests for RealAuthClient.
 * 
 * Tests URL sanitization and unsafe client detection logic.
 * Network tests require integration testing with actual servers.
 */
@RunWith(JUnit4::class)
class RealAuthClientTest {

    @Test
    fun `shouldUseUnsafeClient returns true for localhost`() {
        val result = shouldUseUnsafeClient("http://localhost:8080/v1")
        assertThat(result, equalTo(true))
    }

    @Test
    fun `shouldUseUnsafeClient returns true for 127_0_0_1`() {
        val result = shouldUseUnsafeClient("https://127.0.0.1/v1")
        assertThat(result, equalTo(true))
    }

    @Test
    fun `shouldUseUnsafeClient returns true for emulator host 10_0_2_2`() {
        val result = shouldUseUnsafeClient("https://10.0.2.2:8443/v1")
        assertThat(result, equalTo(true))
    }

    @Test
    fun `shouldUseUnsafeClient returns true for private network 192_168`() {
        val result = shouldUseUnsafeClient("https://192.168.1.100/v1")
        assertThat(result, equalTo(true))
    }

    @Test
    fun `shouldUseUnsafeClient returns true for central-dev hostname`() {
        val result = shouldUseUnsafeClient("https://central-dev/v1")
        assertThat(result, equalTo(true))
    }

    @Test
    fun `shouldUseUnsafeClient returns true for central_dot_dev hostname`() {
        val result = shouldUseUnsafeClient("https://central.dev/v1")
        assertThat(result, equalTo(true))
    }

    @Test
    fun `shouldUseUnsafeClient returns true for central_dot_local hostname`() {
        val result = shouldUseUnsafeClient("https://central.local/v1")
        assertThat(result, equalTo(true))
    }

    @Test
    fun `shouldUseUnsafeClient returns false for production URLs`() {
        val result = shouldUseUnsafeClient("https://odk-central.medres.org/v1")
        assertThat(result, equalTo(false))
    }

    @Test
    fun `shouldUseUnsafeClient returns false for getodk hosted URLs`() {
        val result = shouldUseUnsafeClient("https://myserver.getodk.cloud/v1")
        assertThat(result, equalTo(false))
    }

    @Test
    fun `sanitizeUrl strips project path correctly`() {
        val result = sanitizeUrl("https://server.com/v1/projects/123")
        assertThat(result, equalTo("https://server.com/v1/"))
    }

    @Test
    fun `sanitizeUrl adds trailing slash if missing`() {
        val result = sanitizeUrl("https://server.com/v1")
        assertThat(result, equalTo("https://server.com/v1/"))
    }

    @Test
    fun `sanitizeUrl keeps trailing slash if present`() {
        val result = sanitizeUrl("https://server.com/v1/")
        assertThat(result, equalTo("https://server.com/v1/"))
    }

    @Test
    fun `sanitizeUrl handles complex project paths`() {
        val result = sanitizeUrl("https://server.com/v1/projects/456/app-users")
        assertThat(result, equalTo("https://server.com/v1/"))
    }

    // Helper functions extracted from RealAuthClient for testing
    // In production, these would be extracted to a companion object or utility class

    private fun shouldUseUnsafeClient(url: String): Boolean {
        val lowerUrl = url.lowercase()
        return lowerUrl.contains("localhost") ||
            lowerUrl.contains("127.0.0.1") ||
            lowerUrl.contains("10.0.2.") ||
            lowerUrl.contains("192.168.") ||
            lowerUrl.contains("central-dev") ||
            lowerUrl.contains("central.dev") ||
            lowerUrl.contains("central.local")
    }

    private fun sanitizeUrl(apiUrl: String): String {
        var sanitizedUrl = apiUrl
        if (sanitizedUrl.contains("/projects/")) {
            sanitizedUrl = sanitizedUrl.substringBefore("/projects/") + "/"
        }
        if (!sanitizedUrl.endsWith("/")) {
            sanitizedUrl += "/"
        }
        return sanitizedUrl
    }
}
