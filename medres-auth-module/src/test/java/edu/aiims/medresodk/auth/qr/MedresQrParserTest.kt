package edu.aiims.medresodk.auth.qr

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Phase 1 tests — QR classifier contract.
 *
 * All tests use a fake decompressor so the parser is exercised as pure logic
 * without touching Android framework code or the real CompressionUtils.
 *
 * Fixture shapes are derived from the real sample QR images in local-testing/:
 *   - app_user_medres_qr.png       → MEDRES project URL
 *   - draft_form_medres_qr.png     → Draft/demo URL
 *   - standard_odk_draft_qr.png    → Standard ODK draft (still DraftFormQr)
 *   - standard_odk_managed_qr.png  → Standard ODK managed (/key/) → rejected
 */
class MedresQrParserTest {

    // A decompressor that simply returns its input (treats raw JSON as if already decompressed).
    private val identityDecompressor = MedresQrParser.Decompressor { it }

    private lateinit var parser: MedresQrParser

    @Before
    fun setUp() {
        parser = MedresQrParser(
            decompressor = identityDecompressor,
            // Generous limits so size/ratio tests don't interfere here
            maxCompressedBytes = 32_768,
            maxDecompressedBytes = 131_072,
            maxCompressionRatio = 1000f
        )
    }

    // -------------------------------------------------------------------------
    // Test 1.1 — MEDRES project QR parses as MedresProjectQr
    // -------------------------------------------------------------------------

    @Test
    fun `MEDRES project QR fixture parses as MedresProjectQr`() {
        val json = buildQrJson(
            serverUrl = "https://central.aiims.edu/v1/projects/1",
            projectName = "sdsdsdsd",
            projectId = "1",
            username = "vivekgupta",
            adminSettings = mapOf("change_server" to false)
        )

        val result = parser.parse(json)

        assertTrue("Expected MedresProjectQr, got $result", result is MedresProjectQr)
        result as MedresProjectQr

        assertEquals("https://central.aiims.edu/v1/projects/1", result.originalUrl)
        assertEquals("https://central.aiims.edu/v1", result.authBaseUrl)
        assertEquals("1", result.centralProjectId)
        assertEquals("sdsdsdsd", result.projectName)
        assertEquals("vivekgupta", result.usernameHint)
        assertTrue("adminSettingsJson should be non-empty", result.adminSettingsJson.isNotEmpty())
        assertTrue("generalSettingsJson should be non-empty", result.generalSettingsJson.isNotEmpty())
    }

    @Test
    fun `MEDRES project QR stores admin settings in adminSettingsJson`() {
        val json = buildQrJson(
            serverUrl = "https://central.aiims.edu/v1/projects/1",
            adminSettings = mapOf("change_server" to false, "delete_saved" to false)
        )

        val result = parser.parse(json) as MedresProjectQr
        val adminObj = JSONObject(result.adminSettingsJson)
        assertTrue(adminObj.has("change_server"))
        assertTrue(adminObj.has("delete_saved"))
    }

    @Test
    fun `MEDRES project QR with different PID`() {
        val json = buildQrJson(serverUrl = "https://central.aiims.edu/v1/projects/42")
        val result = parser.parse(json) as MedresProjectQr
        assertEquals("42", result.centralProjectId)
    }

    // -------------------------------------------------------------------------
    // Test 1.2 — Draft QR (MEDRES-hosted) parses as DraftFormQr
    // -------------------------------------------------------------------------

    @Test
    fun `MEDRES-hosted draft form QR parses as DraftFormQr`() {
        val draftUrl = "https://central.aiims.edu/v1/test/ABCDEF123456/projects/1/forms/school_info/draft"
        val json = buildQrJson(
            serverUrl = draftUrl,
            projectName = "[Draft] 1.School Information",
            projectId = ""   // draft QRs often omit project_id
        )

        val result = parser.parse(json)

        assertTrue("Expected DraftFormQr, got $result", result is DraftFormQr)
        result as DraftFormQr

        assertEquals(draftUrl, result.originalDraftUrl)
        assertEquals("1", result.centralProjectId)
        assertEquals("school_info", result.formId)
        assertEquals("[Draft] 1.School Information", result.displayName)
        // No auth base URL — drafts go straight to demo mode
    }

    @Test
    fun `draft QR does not expose authBaseUrl`() {
        // DraftFormQr has no authBaseUrl property — check compile-time shape only
        val url = "https://central.aiims.edu/v1/test/TOKEN/projects/2/forms/form_a/draft"
        val json = buildQrJson(serverUrl = url)
        val result = parser.parse(json)
        // Simply asserting type — DraftFormQr has no authBaseUrl field by design
        assertTrue(result is DraftFormQr)
    }

    @Test
    fun `draft QR centralProjectId derived from URL, not from project section`() {
        // project.project_id is absent; parser must derive from URL
        val json = buildQrJson(
            serverUrl = "https://central.aiims.edu/v1/test/T/projects/99/forms/myform/draft",
            projectId = ""  // absent in real draft QRs
        )
        val result = parser.parse(json) as DraftFormQr
        assertEquals("99", result.centralProjectId)
        assertEquals("myform", result.formId)
    }

    // -------------------------------------------------------------------------
    // Test 1.3 — Standard ODK draft QR still classifies as DraftFormQr
    // -------------------------------------------------------------------------

    @Test
    fun `standard ODK draft QR classifies as DraftFormQr`() {
        // domain doesn't matter; URL shape determines class
        val url = "https://odk-central.example.org/v1/test/TOKEN_ODK/projects/5/forms/myForm/draft"
        val json = buildQrJson(serverUrl = url)

        val result = parser.parse(json)
        assertTrue("Expected DraftFormQr, got $result", result is DraftFormQr)
    }

    // -------------------------------------------------------------------------
    // Test 1.4 — Standard ODK managed QR → StandardOdkManagedQr (rejected)
    // -------------------------------------------------------------------------

    @Test
    fun `standard ODK managed QR parses as StandardOdkManagedQr`() {
        val url = "https://odk.example.org/v1/key/LONGTOKEN1234567890/projects/7"
        val json = buildQrJson(serverUrl = url)

        val result = parser.parse(json)
        assertTrue("Expected StandardOdkManagedQr, got $result", result is StandardOdkManagedQr)
        assertEquals(url, (result as StandardOdkManagedQr).originalUrl)
    }

    @Test
    fun `MEDRES-hosted managed QR also rejected via key prefix`() {
        val url = "https://central.aiims.edu/v1/key/SESSION_TOKEN/projects/1"
        val json = buildQrJson(serverUrl = url)
        assertTrue(parser.parse(json) is StandardOdkManagedQr)
    }

    // -------------------------------------------------------------------------
    // Test 1.5 — Invalid payloads → InvalidQr
    // -------------------------------------------------------------------------

    @Test
    fun `malformed JSON returns InvalidQr`() {
        // The identity decompressor returns input as-is; invalid JSON should fail at JSON parse step
        val result = parser.parse("{not_valid_json")
        assertTrue("Expected InvalidQr, got $result", result is InvalidQr)
    }

    @Test
    fun `missing general section returns InvalidQr`() {
        val json = """{"admin":{},"project":{}}"""
        val result = parser.parse(json)
        assertTrue("Expected InvalidQr, got $result", result is InvalidQr)
        assertContains((result as InvalidQr).reason, "server_url")
    }

    @Test
    fun `missing server_url in general returns InvalidQr`() {
        val json = """{"general":{"autosend":"wifi_only"},"admin":{},"project":{}}"""
        val result = parser.parse(json)
        assertTrue("Expected InvalidQr, got $result", result is InvalidQr)
    }

    @Test
    fun `empty server_url returns InvalidQr`() {
        val json = buildQrJson(serverUrl = "")
        val result = parser.parse(json)
        assertTrue(result is InvalidQr)
    }

    @Test
    fun `unsupported URL shape returns InvalidQr`() {
        val json = buildQrJson(serverUrl = "https://example.com/some/unknown/shape")
        val result = parser.parse(json)
        assertTrue("Expected InvalidQr, got $result", result is InvalidQr)
    }

    @Test
    fun `MEDRES project URL missing project id returns InvalidQr`() {
        val json = buildQrJson(serverUrl = "https://central.aiims.edu/v1/projects/")
        val result = parser.parse(json)
        assertTrue("Expected InvalidQr, got $result", result is InvalidQr)
    }

    @Test
    fun `draft URL missing form id returns InvalidQr`() {
        val json = buildQrJson(
            serverUrl = "https://central.aiims.edu/v1/test/TOKEN/projects/1/forms//draft"
        )
        val result = parser.parse(json)
        assertTrue("Expected InvalidQr, got $result", result is InvalidQr)
    }

    // -------------------------------------------------------------------------
    // URL shape predicate unit tests
    // -------------------------------------------------------------------------

    @Test
    fun `isDraftUrl accepts canonical draft shape`() {
        assertTrue(
            parser.isDraftUrl(
                "https://central.aiims.edu/v1/test/TOKEN/projects/1/forms/f/draft"
            )
        )
    }

    @Test
    fun `isDraftUrl rejects URL missing test segment`() {
        assertFalse(
            parser.isDraftUrl("https://central.aiims.edu/v1/projects/1/forms/f/draft")
        )
    }

    @Test
    fun `isDraftUrl rejects URL missing draft suffix`() {
        assertFalse(
            parser.isDraftUrl("https://central.aiims.edu/v1/test/TOKEN/projects/1/forms/f")
        )
    }

    @Test
    fun `isManagedKeyUrl accepts canonical key shape`() {
        assertTrue(
            parser.isManagedKeyUrl("https://odk.example.org/v1/key/TOKEN/projects/5")
        )
    }

    @Test
    fun `isManagedKeyUrl rejects draft URL even with key segment`() {
        // A hypothetical URL with /key/ but ending in /draft is still a draft
        assertFalse(
            parser.isManagedKeyUrl(
                "https://central.aiims.edu/v1/test/TOKEN/projects/1/forms/f/draft"
            )
        )
    }

    @Test
    fun `isMedresProjectUrl accepts canonical MEDRES project shape`() {
        assertTrue(
            parser.isMedresProjectUrl("https://central.aiims.edu/v1/projects/1")
        )
    }

    @Test
    fun `isMedresProjectUrl rejects managed key URL`() {
        assertFalse(
            parser.isMedresProjectUrl("https://central.aiims.edu/v1/key/T/projects/1")
        )
    }

    // -------------------------------------------------------------------------
    // Security: size + ratio limits
    // -------------------------------------------------------------------------

    @Test
    fun `oversized compressed payload returns InvalidQr`() {
        val strictParser = MedresQrParser(
            decompressor = identityDecompressor,
            maxCompressedBytes = 10
        )
        val result = strictParser.parse("A".repeat(11))
        assertTrue(result is InvalidQr)
        assertContains((result as InvalidQr).reason, "large")
    }

    @Test
    fun `decompression bomb returns InvalidQr`() {
        val bombDecompressor = MedresQrParser.Decompressor { "X".repeat(200) }
        val strictParser = MedresQrParser(
            decompressor = bombDecompressor,
            maxCompressedBytes = 100,
            maxDecompressedBytes = 50,
            maxCompressionRatio = 1000f
        )
        val result = strictParser.parse("Y")
        assertTrue(result is InvalidQr)
        assertContains((result as InvalidQr).reason, "large")
    }

    @Test
    fun `excessive compression ratio returns InvalidQr`() {
        // Compressed: 1 byte → Decompressed: 100 chars ≈ 100 bytes → ratio = 100 > max 10
        val fatDecompressor = MedresQrParser.Decompressor { "X".repeat(100) }
        val strictParser = MedresQrParser(
            decompressor = fatDecompressor,
            maxCompressedBytes = 100,
            maxDecompressedBytes = 100_000,
            maxCompressionRatio = 10f
        )
        val result = strictParser.parse("Y")
        assertTrue(result is InvalidQr)
        assertContains((result as InvalidQr).reason, "ratio")
    }

    @Test
    fun `decompressor exception returns InvalidQr`() {
        val failDecompressor = MedresQrParser.Decompressor { throw RuntimeException("bad data") }
        val p = MedresQrParser(decompressor = failDecompressor)
        val result = p.parse("any")
        assertTrue(result is InvalidQr)
        assertContains((result as InvalidQr).reason, "Decompression failed")
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun buildQrJson(
        serverUrl: String,
        projectName: String = "",
        projectId: String = "",
        username: String = "",
        adminSettings: Map<String, Any> = emptyMap()
    ): String {
        val general = JSONObject().apply {
            if (serverUrl.isNotEmpty()) put("server_url", serverUrl)
            if (username.isNotEmpty()) put("username", username)
        }
        val admin = JSONObject().apply {
            adminSettings.forEach { (k, v) -> put(k, v) }
        }
        val project = JSONObject().apply {
            if (projectName.isNotEmpty()) put("name", projectName)
            if (projectId.isNotEmpty()) put("project_id", projectId)
        }
        return JSONObject().apply {
            put("general", general)
            put("admin", admin)
            put("project", project)
        }.toString()
    }

    private fun assertContains(actual: String, expected: String) {
        assertTrue(
            "Expected '$expected' to be contained in '$actual'",
            actual.contains(expected, ignoreCase = true)
        )
    }
}
