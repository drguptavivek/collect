package edu.aiims.medresodk.auth.utils

import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.nullValue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for MedresProjectUtils.
 * 
 * Tests URL parsing for Central project IDs from QR code server URLs.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class MedresProjectUtilsTest {

    @Test
    fun `#getProjectIdFromUrl returns project ID from standard Central URL`() {
        val url = "https://central.example.com/v1/projects/1"
        assertThat(MedresProjectUtils.getProjectIdFromUrl(url), equalTo("1"))
    }

    @Test
    fun `#getProjectIdFromUrl returns project ID from URL with trailing slash`() {
        val url = "https://central.example.com/v1/projects/5/"
        // Note: trailing slash may cause issues - let's test current behavior
        assertThat(MedresProjectUtils.getProjectIdFromUrl(url), equalTo("5"))
    }

    @Test
    fun `#getProjectIdFromUrl returns multi-digit project ID`() {
        val url = "https://central.example.org/v1/projects/123"
        assertThat(MedresProjectUtils.getProjectIdFromUrl(url), equalTo("123"))
    }

    @Test
    fun `#getProjectIdFromUrl returns project ID from local development URL`() {
        val url = "https://central.local/v1/projects/2"
        assertThat(MedresProjectUtils.getProjectIdFromUrl(url), equalTo("2"))
    }

    @Test
    fun `#getProjectIdFromUrl returns project ID from IP address URL`() {
        val url = "http://192.168.1.100:8383/v1/projects/7"
        assertThat(MedresProjectUtils.getProjectIdFromUrl(url), equalTo("7"))
    }

    @Test
    fun `#getProjectIdFromUrl returns null for null URL`() {
        assertThat(MedresProjectUtils.getProjectIdFromUrl(null), nullValue())
    }

    @Test
    fun `#getProjectIdFromUrl returns null for empty URL`() {
        assertThat(MedresProjectUtils.getProjectIdFromUrl(""), nullValue())
    }

    @Test
    fun `#getProjectIdFromUrl returns null for blank URL`() {
        assertThat(MedresProjectUtils.getProjectIdFromUrl("   "), nullValue())
    }

    @Test
    fun `#getProjectIdFromUrl returns null for URL without project path`() {
        val url = "https://central.example.com/v1/forms"
        assertThat(MedresProjectUtils.getProjectIdFromUrl(url), nullValue())
    }

    @Test
    fun `#getProjectIdFromUrl returns null for URL with only base path`() {
        val url = "https://central.example.com"
        assertThat(MedresProjectUtils.getProjectIdFromUrl(url), nullValue())
    }

    @Test
    fun `#getProjectIdFromUrl handles URL from QR code JSON format`() {
        // This is the format from the sample QR code data
        val url = "https://central.example.com/v1/projects/1"
        assertThat(MedresProjectUtils.getProjectIdFromUrl(url), equalTo("1"))
    }

    @Test
    fun `#getProjectIdFromUrl handles HTTPS and HTTP protocols`() {
        val httpsUrl = "https://central.example.com/v1/projects/3"
        val httpUrl = "http://central.example.com/v1/projects/4"
        
        assertThat(MedresProjectUtils.getProjectIdFromUrl(httpsUrl), equalTo("3"))
        assertThat(MedresProjectUtils.getProjectIdFromUrl(httpUrl), equalTo("4"))
    }

    @Test
    fun `#getProjectIdFromUrl returns null for malformed URL`() {
        val url = "not-a-valid-url"
        assertThat(MedresProjectUtils.getProjectIdFromUrl(url), nullValue())
    }

    @Test
    fun `#getProjectIdFromUrl returns null when projects is not second-to-last segment`() {
        val url = "https://central.example.com/projects/1/forms"
        assertThat(MedresProjectUtils.getProjectIdFromUrl(url), nullValue())
    }

    @Test
    fun `#formatUrlForDisplay strips path segments`() {
        val url = "https://central.example.com/v1/projects/1"
        assertThat(MedresProjectUtils.formatUrlForDisplay(url), equalTo("https://central.example.com"))
    }

    @Test
    fun `#formatUrlForDisplay handles custom ports`() {
        val url = "http://192.168.1.50:8383/v1/projects/2"
        assertThat(MedresProjectUtils.formatUrlForDisplay(url), equalTo("http://192.168.1.50:8383"))
    }

    @Test
    fun `#formatUrlForDisplay handles simple base URL`() {
        val url = "https://central.local"
        assertThat(MedresProjectUtils.formatUrlForDisplay(url), equalTo("https://central.local"))
    }

    @Test
    fun `#formatUrlForApi appends v1 if missing`() {
        val url = "https://central.example.com"
        assertThat(MedresProjectUtils.formatUrlForApi(url), equalTo("https://central.example.com/v1"))
    }

    @Test
    fun `#formatUrlForApi preserves v1 if present`() {
        val url = "https://central.example.com/v1"
        assertThat(MedresProjectUtils.formatUrlForApi(url), equalTo("https://central.example.com/v1"))
    }

    @Test
    fun `#formatUrlForApi adds https if missing`() {
        val url = "central.example.com"
        assertThat(MedresProjectUtils.formatUrlForApi(url), equalTo("https://central.example.com/v1"))
    }

    @Test
    fun `#formatUrlForApi trims trailing slashes`() {
        val url = "https://central.example.com/"
        assertThat(MedresProjectUtils.formatUrlForApi(url), equalTo("https://central.example.com/v1"))
    }

    @Test
    fun `#getProjectIdFromUrl returns project ID from tokenized URL`() {
        // [v1, key, TOKEN, projects, PID]
        val url = "https://central.example.com/v1/key/ABCDEF12345/projects/10"
        assertThat(MedresProjectUtils.getProjectIdFromUrl(url), equalTo("10"))
    }

    @Test
    fun `#formatUrlForDisplay strips path segments including token`() {
        val url = "https://central.example.com/v1/key/ABCDEF12345/projects/1"
        assertThat(MedresProjectUtils.formatUrlForDisplay(url), equalTo("https://central.example.com"))
    }

    @Test
    fun `#formatUrlForApi ensures v1 is present even if longer path provided`() {
        val url = "https://central.example.com/projects/1"
        assertThat(MedresProjectUtils.formatUrlForApi(url), equalTo("https://central.example.com/v1/projects/1"))
    }
}
