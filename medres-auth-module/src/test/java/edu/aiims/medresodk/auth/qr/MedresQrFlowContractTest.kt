package edu.aiims.medresodk.auth.qr

import edu.aiims.medresodk.auth.utils.MedresConstants
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Phase 3: End-to-end QR flow contract tests.
 *
 * Exercises the full parse → classify → stage pipeline to verify that
 * each QR type ends up in the correct staged state with no cross-contamination.
 */
class MedresQrFlowContractTest {

    private val identityDecompressor = MedresQrParser.Decompressor { it }
    private lateinit var parser: MedresQrParser
    private lateinit var prefs: FakeSharedPreferences
    private lateinit var store: MedresQrStagingStore

    @Before
    fun setUp() {
        parser = MedresQrParser(
            decompressor = identityDecompressor,
            maxCompressedBytes = 32_768,
            maxDecompressedBytes = 131_072,
            maxCompressionRatio = 1000f
        )
        prefs = FakeSharedPreferences()
        store = MedresQrStagingStore(prefs)
    }

    // -------------------------------------------------------------------------
    // Test 3.1 — MEDRES project QR → staged as MEDRES project → login UI shown
    // -------------------------------------------------------------------------

    @Test
    fun `MEDRES project QR stages as StagedMedresProjectContext`() {
        val qrJson = buildQrJson(
            serverUrl = "https://central.aiims.edu/v1/projects/1",
            projectName = "sdsdsdsd",
            username = "vivekgupta",
            adminSettings = mapOf("change_server" to false)
        )

        val result = parser.parse(qrJson)
        assertTrue(result is MedresProjectQr)
        result as MedresProjectQr

        // Scanner stages
        store.stageMedresProject(
            StagedMedresProjectContext(
                authBaseUrl = result.authBaseUrl,
                centralProjectId = result.centralProjectId,
                projectName = result.projectName,
                usernameHint = result.usernameHint,
                generalSettingsJson = result.generalSettingsJson,
                adminSettingsJson = result.adminSettingsJson
            )
        )

        // Login reads
        val staged = store.read()
        assertTrue("Expected StagedMedresProjectContext, got $staged", staged is StagedMedresProjectContext)
        staged as StagedMedresProjectContext

        assertEquals("https://central.aiims.edu/v1", staged.authBaseUrl)
        assertEquals("1", staged.centralProjectId)
        assertEquals("sdsdsdsd", staged.projectName)
        assertEquals("vivekgupta", staged.usernameHint)
        // Auth URL is staged — login screen should show credential fields
        assertTrue(store.hasMedresProjectContext())
        assertFalse(store.hasDraftFormContext())
    }

    // -------------------------------------------------------------------------
    // Test 3.2 — Draft QR → staged as draft → no credential prompt needed
    // -------------------------------------------------------------------------

    @Test
    fun `draft QR stages as StagedDraftFormContext with full URL intact`() {
        val draftUrl = "https://central.aiims.edu/v1/test/SECRETTOKEN/projects/2/forms/survey_form/draft"
        val qrJson = buildQrJson(
            serverUrl = draftUrl,
            projectName = "[Draft] Survey Form v2"
        )

        val result = parser.parse(qrJson)
        assertTrue(result is DraftFormQr)
        result as DraftFormQr

        // Scanner stages
        store.stageDraftForm(
            StagedDraftFormContext(
                originalDraftUrl = result.originalDraftUrl,
                centralProjectId = result.centralProjectId,
                formId = result.formId,
                displayName = result.displayName,
                displayIcon = result.displayIcon,
                generalSettingsJson = result.generalSettingsJson
            )
        )

        val staged = store.read()
        assertTrue("Expected StagedDraftFormContext, got $staged", staged is StagedDraftFormContext)
        staged as StagedDraftFormContext

        // Full draft URL must be preserved, not rewritten
        assertEquals(draftUrl, staged.originalDraftUrl)
        assertFalse("Draft URL must not contain /key/", staged.originalDraftUrl.contains("/key/"))
        assertEquals("2", staged.centralProjectId)
        assertEquals("survey_form", staged.formId)
        assertEquals("[Draft] Survey Form v2", staged.displayName)
        // Demo mode — no auth or admin settings staged
        assertNull(
            "Draft staging must not write auth_url",
            prefs.getString(MedresConstants.KEY_AUTH_URL, null)
        )
        assertTrue(store.hasDraftFormContext())
        assertFalse(store.hasMedresProjectContext())
    }

    @Test
    fun `draft QR displayName is never used as durable project identity`() {
        val draftUrl = "https://central.aiims.edu/v1/test/T/projects/1/forms/f/draft"
        val qrJson = buildQrJson(serverUrl = draftUrl, projectName = "[Draft] Disposable Label")

        val result = parser.parse(qrJson) as DraftFormQr
        store.stageDraftForm(
            StagedDraftFormContext(
                originalDraftUrl = result.originalDraftUrl,
                centralProjectId = result.centralProjectId,
                formId = result.formId,
                displayName = result.displayName,
                displayIcon = null,
                generalSettingsJson = result.generalSettingsJson
            )
        )
        val staged = store.read() as StagedDraftFormContext

        // displayName is a display label only, not auth_project_name
        assertNull(
            "auth_project_name must not be written for draft QRs",
            prefs.getString(MedresConstants.KEY_AUTH_PROJECT_NAME, null)
        )
        assertEquals("[Draft] Disposable Label", staged.displayName)
    }

    // -------------------------------------------------------------------------
    // Test 3.3 — Standard ODK managed QR → rejected, no staging
    // -------------------------------------------------------------------------

    @Test
    fun `standard ODK managed QR is rejected at parser level`() {
        val qrJson = buildQrJson(serverUrl = "https://odk.example.org/v1/key/LONGTOKEN123/projects/5")

        val result = parser.parse(qrJson)
        assertTrue("Expected StandardOdkManagedQr, got $result", result is StandardOdkManagedQr)
    }

    @Test
    fun `standard ODK managed QR — nothing staged`() {
        // Scanner must not call stagingStore for a StandardOdkManagedQr — verify prefs are empty
        val result = parser.parse(
            buildQrJson(serverUrl = "https://odk.example.org/v1/key/LONGTOKEN123/projects/5")
        )
        assertTrue(result is StandardOdkManagedQr)

        // No staging happened
        assertNull(store.read())
        assertFalse(store.hasMedresProjectContext())
        assertFalse(store.hasDraftFormContext())
    }

    // -------------------------------------------------------------------------
    // Cross-contamination: restaging must clear previous type
    // -------------------------------------------------------------------------

    @Test
    fun `scanning MEDRES QR after draft QR clears draft staged state`() {
        // First: draft QR scanned
        val draftUrl = "https://c.x/v1/test/T/projects/1/forms/f/draft"
        store.stageDraftForm(
            StagedDraftFormContext(draftUrl, "1", "f", "Draft", null, "{}")
        )
        assertTrue(store.hasDraftFormContext())

        // Second: MEDRES project QR scanned
        store.stageMedresProject(
            StagedMedresProjectContext("https://c.x/v1", "1", "Real Project", null, "{}", "{}")
        )

        assertTrue(store.hasMedresProjectContext())
        assertFalse(store.hasDraftFormContext())
        assertNull(prefs.getString(MedresConstants.KEY_DRAFT_URL, null))
    }

    @Test
    fun `scanning draft QR after MEDRES QR clears MEDRES staged state`() {
        store.stageMedresProject(
            StagedMedresProjectContext("https://c.x/v1", "1", "Real", null, "{}", "{}")
        )
        assertTrue(store.hasMedresProjectContext())

        val draftUrl = "https://c.x/v1/test/T/projects/1/forms/f/draft"
        store.stageDraftForm(
            StagedDraftFormContext(draftUrl, "1", "f", "Draft", null, "{}")
        )

        assertTrue(store.hasDraftFormContext())
        assertFalse(store.hasMedresProjectContext())
        assertNull(prefs.getString(MedresConstants.KEY_AUTH_URL, null))
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun buildQrJson(
        serverUrl: String,
        projectName: String = "",
        username: String = "",
        adminSettings: Map<String, Any> = emptyMap()
    ): String {
        val general = org.json.JSONObject().apply {
            if (serverUrl.isNotEmpty()) put("server_url", serverUrl)
            if (username.isNotEmpty()) put("username", username)
        }
        val admin = org.json.JSONObject().apply {
            adminSettings.forEach { (k, v) -> put(k, v) }
        }
        val project = org.json.JSONObject().apply {
            if (projectName.isNotEmpty()) put("name", projectName)
        }
        return org.json.JSONObject().apply {
            put("general", general)
            put("admin", admin)
            put("project", project)
        }.toString()
    }
}
