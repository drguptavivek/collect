package edu.aiims.medresodk.auth.qr

import edu.aiims.medresodk.auth.utils.MedresConstants
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Phase 2 tests — typed staging contract.
 *
 * Uses an in-memory [FakeSharedPreferences] so no Android framework is required.
 */
class MedresQrStagingTest {

    private lateinit var prefs: FakeSharedPreferences
    private lateinit var store: MedresQrStagingStore

    @Before
    fun setUp() {
        prefs = FakeSharedPreferences()
        store = MedresQrStagingStore(prefs)
    }

    // -------------------------------------------------------------------------
    // Test 2.1 — Staging a MEDRES project QR stores all project onboarding fields
    // -------------------------------------------------------------------------

    @Test
    fun `staging MEDRES project QR writes correct type discriminator`() {
        store.stageMedresProject(sampleMedresCtx())
        assertEquals(
            MedresConstants.STAGED_QR_TYPE_MEDRES_PROJECT,
            prefs.getString(MedresConstants.KEY_STAGED_QR_TYPE, null)
        )
    }

    @Test
    fun `staging MEDRES project QR writes auth_url`() {
        store.stageMedresProject(sampleMedresCtx())
        assertEquals("https://central.aiims.edu/v1", prefs.getString(MedresConstants.KEY_AUTH_URL, null))
    }

    @Test
    fun `staging MEDRES project QR writes auth_project_id`() {
        store.stageMedresProject(sampleMedresCtx())
        assertEquals("1", prefs.getString(MedresConstants.KEY_AUTH_PROJECT_ID, null))
    }

    @Test
    fun `staging MEDRES project QR writes auth_project_name`() {
        store.stageMedresProject(sampleMedresCtx())
        assertEquals("sdsdsdsd", prefs.getString(MedresConstants.KEY_AUTH_PROJECT_NAME, null))
    }

    @Test
    fun `staging MEDRES project QR writes qr_general_settings`() {
        store.stageMedresProject(sampleMedresCtx())
        val general = prefs.getString(MedresConstants.KEY_QR_GENERAL_SETTINGS, null)
        assertNotNull(general)
        assertTrue(general!!.contains("server_url"))
    }

    @Test
    fun `staging MEDRES project QR writes qr_admin_settings`() {
        store.stageMedresProject(sampleMedresCtx())
        val admin = prefs.getString(MedresConstants.KEY_QR_ADMIN_SETTINGS, null)
        assertNotNull(admin)
    }

    @Test
    fun `reading after staging MEDRES project returns StagedMedresProjectContext`() {
        val ctx = sampleMedresCtx()
        store.stageMedresProject(ctx)

        val read = store.read()
        assertTrue("Expected StagedMedresProjectContext, got $read", read is StagedMedresProjectContext)
        read as StagedMedresProjectContext

        assertEquals(ctx.authBaseUrl, read.authBaseUrl)
        assertEquals(ctx.centralProjectId, read.centralProjectId)
        assertEquals(ctx.projectName, read.projectName)
        assertEquals(ctx.usernameHint, read.usernameHint)
        assertEquals(ctx.generalSettingsJson, read.generalSettingsJson)
        assertEquals(ctx.adminSettingsJson, read.adminSettingsJson)
    }

    // -------------------------------------------------------------------------
    // Test 2.2 — Staging a draft QR stores demo-only fields
    // -------------------------------------------------------------------------

    @Test
    fun `staging draft QR writes correct type discriminator`() {
        store.stageDraftForm(sampleDraftCtx())
        assertEquals(
            MedresConstants.STAGED_QR_TYPE_DRAFT_FORM,
            prefs.getString(MedresConstants.KEY_STAGED_QR_TYPE, null)
        )
    }

    @Test
    fun `staging draft QR writes draft_url`() {
        store.stageDraftForm(sampleDraftCtx())
        assertEquals(
            "https://central.aiims.edu/v1/test/TOKEN/projects/1/forms/school_info/draft",
            prefs.getString(MedresConstants.KEY_DRAFT_URL, null)
        )
    }

    @Test
    fun `staging draft QR writes draft_project_id`() {
        store.stageDraftForm(sampleDraftCtx())
        assertEquals("1", prefs.getString(MedresConstants.KEY_DRAFT_PROJECT_ID, null))
    }

    @Test
    fun `staging draft QR writes draft_form_id`() {
        store.stageDraftForm(sampleDraftCtx())
        assertEquals("school_info", prefs.getString(MedresConstants.KEY_DRAFT_FORM_ID, null))
    }

    @Test
    fun `staging draft QR writes draft_display_name`() {
        store.stageDraftForm(sampleDraftCtx())
        assertEquals(
            "[Draft] 1.School Information",
            prefs.getString(MedresConstants.KEY_DRAFT_DISPLAY_NAME, null)
        )
    }

    @Test
    fun `staging draft QR does NOT write auth_url`() {
        store.stageDraftForm(sampleDraftCtx())
        assertNull("Draft staging must not write auth_url", prefs.getString(MedresConstants.KEY_AUTH_URL, null))
    }

    @Test
    fun `staging draft QR does NOT write qr_admin_settings`() {
        store.stageDraftForm(sampleDraftCtx())
        assertNull(
            "Draft staging must not write admin settings",
            prefs.getString(MedresConstants.KEY_QR_ADMIN_SETTINGS, null)
        )
    }

    @Test
    fun `reading after staging draft returns StagedDraftFormContext`() {
        val ctx = sampleDraftCtx()
        store.stageDraftForm(ctx)

        val read = store.read()
        assertTrue("Expected StagedDraftFormContext, got $read", read is StagedDraftFormContext)
        read as StagedDraftFormContext

        assertEquals(ctx.originalDraftUrl, read.originalDraftUrl)
        assertEquals(ctx.centralProjectId, read.centralProjectId)
        assertEquals(ctx.formId, read.formId)
        assertEquals(ctx.displayName, read.displayName)
        assertEquals(ctx.generalSettingsJson, read.generalSettingsJson)
    }

    // -------------------------------------------------------------------------
    // Test 2.3 — Standard ODK managed QR stages nothing (handled at scanner level)
    // -------------------------------------------------------------------------

    @Test
    fun `read returns null when no context was staged`() {
        // Nothing written → should be null
        assertNull(store.read())
    }

    @Test
    fun `hasMedresProjectContext false when nothing staged`() {
        assertFalse(store.hasMedresProjectContext())
    }

    @Test
    fun `hasDraftFormContext false when nothing staged`() {
        assertFalse(store.hasDraftFormContext())
    }

    @Test
    fun `hasMedresProjectContext true after staging MEDRES project`() {
        store.stageMedresProject(sampleMedresCtx())
        assertTrue(store.hasMedresProjectContext())
        assertFalse(store.hasDraftFormContext())
    }

    @Test
    fun `hasDraftFormContext true after staging draft`() {
        store.stageDraftForm(sampleDraftCtx())
        assertTrue(store.hasDraftFormContext())
        assertFalse(store.hasMedresProjectContext())
    }

    // -------------------------------------------------------------------------
    // Test 2.4 — Rescan cleanup is precise
    // -------------------------------------------------------------------------

    @Test
    fun `clearSessionAndStagedState removes all session and staging keys`() {
        // Populate session keys
        prefs.edit()
            .putString(MedresConstants.KEY_AUTH_TOKEN, "mytoken")
            .putString(MedresConstants.KEY_USER_NAME, "user1")
            .putString(MedresConstants.KEY_AUTH_USERNAME_HINT, "hint-user")
            .putString(MedresConstants.KEY_AUTH_URL, "https://x.y/v1")
            .putString(MedresConstants.KEY_STAGED_QR_TYPE, MedresConstants.STAGED_QR_TYPE_MEDRES_PROJECT)
            .putString(MedresConstants.KEY_AUTH_PROJECT_ID, "5")
            .putString(MedresConstants.KEY_DRAFT_URL, "https://x.y/draft")
            .apply()

        store.clearSessionAndStagedState()

        // All session/staging keys must be gone
        assertNull(prefs.getString(MedresConstants.KEY_AUTH_TOKEN, null))
        assertEquals("user1", prefs.getString(MedresConstants.KEY_USER_NAME, null))
        assertNull(prefs.getString(MedresConstants.KEY_AUTH_USERNAME_HINT, null))
        assertNull(prefs.getString(MedresConstants.KEY_AUTH_URL, null))
        assertNull(prefs.getString(MedresConstants.KEY_STAGED_QR_TYPE, null))
        assertNull(prefs.getString(MedresConstants.KEY_AUTH_PROJECT_ID, null))
        assertNull(prefs.getString(MedresConstants.KEY_DRAFT_URL, null))
    }

    @Test
    fun `staging MEDRES project QR stores username hint separately from session user name`() {
        prefs.edit().putString(MedresConstants.KEY_USER_NAME, "real-auth-user").apply()

        store.stageMedresProject(sampleMedresCtx())

        assertEquals("real-auth-user", prefs.getString(MedresConstants.KEY_USER_NAME, null))
        assertEquals("vivekgupta", prefs.getString(MedresConstants.KEY_AUTH_USERNAME_HINT, null))
    }

    @Test
    fun `clearSessionAndStagedState preserves unrelated configuration keys`() {
        // Simulate preserved keys that must NOT be cleared
        prefs.edit()
            .putString(MedresConstants.KEY_PIN_HASH, "myhash")
            .putString(MedresConstants.KEY_PIN_SALT, "mysalt")
            .putBoolean(MedresConstants.KEY_MEDRES_AUTH_ENABLED, true)
            .putBoolean(MedresConstants.KEY_DEBUG_MODE, false)
            .putString(MedresConstants.KEY_LAST_VALID_WALL_TIME, "12345")
            .apply()

        store.clearSessionAndStagedState()

        // PIN and feature config must survive
        assertEquals("myhash", prefs.getString(MedresConstants.KEY_PIN_HASH, null))
        assertEquals("mysalt", prefs.getString(MedresConstants.KEY_PIN_SALT, null))
        assertTrue(prefs.getBoolean(MedresConstants.KEY_MEDRES_AUTH_ENABLED, false))
        assertEquals("12345", prefs.getString(MedresConstants.KEY_LAST_VALID_WALL_TIME, null))
    }

    @Test
    fun `staging new MEDRES project after previous draft clears draft keys`() {
        store.stageDraftForm(sampleDraftCtx())
        // Now rescan with MEDRES project
        store.stageMedresProject(sampleMedresCtx())

        // Discriminator must be medres_project
        assertEquals(
            MedresConstants.STAGED_QR_TYPE_MEDRES_PROJECT,
            prefs.getString(MedresConstants.KEY_STAGED_QR_TYPE, null)
        )
        // Draft keys must be absent
        assertNull(prefs.getString(MedresConstants.KEY_DRAFT_URL, null))
        assertNull(prefs.getString(MedresConstants.KEY_DRAFT_FORM_ID, null))
    }

    @Test
    fun `staging draft after previous MEDRES project clears MEDRES project keys`() {
        store.stageMedresProject(sampleMedresCtx())
        // Now rescan with draft
        store.stageDraftForm(sampleDraftCtx())

        // MEDRES staging keys must be absent
        assertNull(prefs.getString(MedresConstants.KEY_AUTH_URL, null))
        assertNull(prefs.getString(MedresConstants.KEY_AUTH_PROJECT_ID, null))
        // Draft keys must be present
        assertNotNull(prefs.getString(MedresConstants.KEY_DRAFT_URL, null))
    }

    // -------------------------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------------------------

    private fun sampleMedresCtx() = StagedMedresProjectContext(
        authBaseUrl = "https://central.aiims.edu/v1",
        centralProjectId = "1",
        projectName = "sdsdsdsd",
        usernameHint = "vivekgupta",
        generalSettingsJson = """{"server_url":"https://central.aiims.edu/v1/projects/1"}""",
        adminSettingsJson = """{"change_server":false}"""
    )

    private fun sampleDraftCtx() = StagedDraftFormContext(
        originalDraftUrl = "https://central.aiims.edu/v1/test/TOKEN/projects/1/forms/school_info/draft",
        centralProjectId = "1",
        formId = "school_info",
        displayName = "[Draft] 1.School Information",
        displayIcon = null,
        generalSettingsJson = """{"server_url":"https://central.aiims.edu/v1/test/TOKEN/projects/1/forms/school_info/draft"}"""
    )
}

// -------------------------------------------------------------------------
// In-memory SharedPreferences fake
// -------------------------------------------------------------------------

/**
 * Minimal in-process [android.content.SharedPreferences] fake for unit tests.
 * No Android framework dependency.
 */
class FakeSharedPreferences : android.content.SharedPreferences {

    private val data = mutableMapOf<String, Any?>()

    override fun getAll(): MutableMap<String, *> = data.toMutableMap()
    override fun getString(key: String, defValue: String?) = data[key] as? String ?: defValue
    override fun getStringSet(key: String, defValues: MutableSet<String>?) =
        @Suppress("UNCHECKED_CAST")
        (data[key] as? Set<String>)?.toMutableSet() ?: defValues

    override fun getInt(key: String, defValue: Int) = data[key] as? Int ?: defValue
    override fun getLong(key: String, defValue: Long) = data[key] as? Long ?: defValue
    override fun getFloat(key: String, defValue: Float) = data[key] as? Float ?: defValue
    override fun getBoolean(key: String, defValue: Boolean) = data[key] as? Boolean ?: defValue
    override fun contains(key: String) = data.containsKey(key)

    override fun edit(): android.content.SharedPreferences.Editor = FakeEditor(data)

    override fun registerOnSharedPreferenceChangeListener(
        listener: android.content.SharedPreferences.OnSharedPreferenceChangeListener
    ) {}

    override fun unregisterOnSharedPreferenceChangeListener(
        listener: android.content.SharedPreferences.OnSharedPreferenceChangeListener
    ) {}

    private class FakeEditor(private val data: MutableMap<String, Any?>) :
        android.content.SharedPreferences.Editor {

        private val pending = mutableMapOf<String, Any?>()
        private val removals = mutableSetOf<String>()
        private var clearAll = false

        override fun putString(key: String, value: String?) = apply { pending[key] = value }
        override fun putStringSet(key: String, values: MutableSet<String>?) =
            apply { pending[key] = values }

        override fun putInt(key: String, value: Int) = apply { pending[key] = value }
        override fun putLong(key: String, value: Long) = apply { pending[key] = value }
        override fun putFloat(key: String, value: Float) = apply { pending[key] = value }
        override fun putBoolean(key: String, value: Boolean) = apply { pending[key] = value }
        override fun remove(key: String) = apply { removals.add(key) }
        override fun clear() = apply { clearAll = true }

        override fun commit(): Boolean {
            flush(); return true
        }

        override fun apply() = flush()

        private fun flush() {
            if (clearAll) data.clear()
            removals.forEach { data.remove(it) }
            data.putAll(pending)
            pending.clear()
            removals.clear()
            clearAll = false
        }
    }
}
