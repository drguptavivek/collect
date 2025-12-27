package org.aiims.odk.auth.utils

import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.nullValue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for AiimsProjectUtils.
 * 
 * Tests URL parsing for Central project IDs from QR code server URLs.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AiimsProjectUtilsTest {

    @Test
    fun `#getProjectIdFromUrl returns project ID from standard Central URL`() {
        val url = "https://central.example.com/v1/projects/1"
        assertThat(AiimsProjectUtils.getProjectIdFromUrl(url), equalTo("1"))
    }

    @Test
    fun `#getProjectIdFromUrl returns project ID from URL with trailing slash`() {
        val url = "https://central.example.com/v1/projects/5/"
        // Note: trailing slash may cause issues - let's test current behavior
        assertThat(AiimsProjectUtils.getProjectIdFromUrl(url), equalTo("5"))
    }

    @Test
    fun `#getProjectIdFromUrl returns multi-digit project ID`() {
        val url = "https://central.example.org/v1/projects/123"
        assertThat(AiimsProjectUtils.getProjectIdFromUrl(url), equalTo("123"))
    }

    @Test
    fun `#getProjectIdFromUrl returns project ID from local development URL`() {
        val url = "https://central.local/v1/projects/2"
        assertThat(AiimsProjectUtils.getProjectIdFromUrl(url), equalTo("2"))
    }

    @Test
    fun `#getProjectIdFromUrl returns project ID from IP address URL`() {
        val url = "http://192.168.1.100:8383/v1/projects/7"
        assertThat(AiimsProjectUtils.getProjectIdFromUrl(url), equalTo("7"))
    }

    @Test
    fun `#getProjectIdFromUrl returns null for null URL`() {
        assertThat(AiimsProjectUtils.getProjectIdFromUrl(null), nullValue())
    }

    @Test
    fun `#getProjectIdFromUrl returns null for empty URL`() {
        assertThat(AiimsProjectUtils.getProjectIdFromUrl(""), nullValue())
    }

    @Test
    fun `#getProjectIdFromUrl returns null for blank URL`() {
        assertThat(AiimsProjectUtils.getProjectIdFromUrl("   "), nullValue())
    }

    @Test
    fun `#getProjectIdFromUrl returns null for URL without project path`() {
        val url = "https://central.example.com/v1/forms"
        assertThat(AiimsProjectUtils.getProjectIdFromUrl(url), nullValue())
    }

    @Test
    fun `#getProjectIdFromUrl returns null for URL with only base path`() {
        val url = "https://central.example.com"
        assertThat(AiimsProjectUtils.getProjectIdFromUrl(url), nullValue())
    }

    @Test
    fun `#getProjectIdFromUrl handles URL from QR code JSON format`() {
        // This is the format from the sample QR code data
        val url = "https://central.example.com/v1/projects/1"
        assertThat(AiimsProjectUtils.getProjectIdFromUrl(url), equalTo("1"))
    }

    @Test
    fun `#getProjectIdFromUrl handles HTTPS and HTTP protocols`() {
        val httpsUrl = "https://central.example.com/v1/projects/3"
        val httpUrl = "http://central.example.com/v1/projects/4"
        
        assertThat(AiimsProjectUtils.getProjectIdFromUrl(httpsUrl), equalTo("3"))
        assertThat(AiimsProjectUtils.getProjectIdFromUrl(httpUrl), equalTo("4"))
    }

    @Test
    fun `#getProjectIdFromUrl returns null for malformed URL`() {
        val url = "not-a-valid-url"
        assertThat(AiimsProjectUtils.getProjectIdFromUrl(url), nullValue())
    }

    @Test
    fun `#getProjectIdFromUrl returns null when projects is not second-to-last segment`() {
        val url = "https://central.example.com/projects/1/forms"
        assertThat(AiimsProjectUtils.getProjectIdFromUrl(url), nullValue())
    }
}
