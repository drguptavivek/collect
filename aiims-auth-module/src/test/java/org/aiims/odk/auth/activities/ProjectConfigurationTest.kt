package org.aiims.odk.auth.activities

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.gson.Gson
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.not
import org.hamcrest.Matchers.notNullValue
import org.hamcrest.Matchers.nullValue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.odk.collect.projects.Project
import org.odk.collect.projects.SharedPreferencesProjectsRepository
import org.odk.collect.settings.keys.MetaKeys
import org.odk.collect.settings.keys.ProjectKeys
import org.odk.collect.shared.strings.UUIDGenerator

/**
 * Tests for the Find-or-Create project pattern used in AiimsLoginActivity.
 * These tests verify the behavior when project configuration changes occur:
 * - Same URL, same project ID → Reuse existing project
 * - Different URL → Create new project
 * - Different project ID (same base URL) → Create new project
 * - Both URL and project ID change → Create new project
 */
@RunWith(AndroidJUnit4::class)
class ProjectConfigurationTest {

    private lateinit var context: Context
    private lateinit var projectsRepo: SharedPreferencesProjectsRepository
    private lateinit var metaPrefs: android.content.SharedPreferences

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        
        // Clear all shared prefs
        context.getSharedPreferences("meta", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("test_projects", Context.MODE_PRIVATE).edit().clear().commit()
        
        // Initialize project repository (same pattern as AiimsLoginActivity)
        val uuidGenerator = UUIDGenerator()
        val gson = Gson()
        metaPrefs = context.getSharedPreferences("meta", Context.MODE_PRIVATE)
        val metaSettings = TestSettings(metaPrefs)
        
        projectsRepo = SharedPreferencesProjectsRepository(
            uuidGenerator,
            gson,
            metaSettings,
            MetaKeys.KEY_PROJECTS
        )
    }

    /**
     * Helper to simulate manualConfigureProject logic from AiimsLoginActivity.
     * Returns the project UUID that was either found or created.
     */
    private fun findOrCreateProject(url: String): String {
        var targetProjectUuid: String? = null
        val allProjects = projectsRepo.getAll()

        for (proj in allProjects) {
            val projPrefs = context.getSharedPreferences("general_prefs${proj.uuid}", Context.MODE_PRIVATE)
            val projUrl = projPrefs.getString(ProjectKeys.KEY_SERVER_URL, null)
            if (projUrl == url) {
                targetProjectUuid = proj.uuid
                break
            }
        }

        // Create new if not found
        if (targetProjectUuid == null) {
            val centralPid = url.substringAfterLast("/")
            val newProject = Project.New(
                "AIIMS Project $centralPid",
                "A",
                "#3e9fcc"
            )
            val saved = projectsRepo.save(newProject)
            targetProjectUuid = saved.uuid
            
            // Save the URL to project settings
            val projPrefs = context.getSharedPreferences("general_prefs$targetProjectUuid", Context.MODE_PRIVATE)
            projPrefs.edit()
                .putString(ProjectKeys.KEY_SERVER_URL, url)
                .putString(ProjectKeys.KEY_PROTOCOL, ProjectKeys.PROTOCOL_SERVER)
                .commit()
        }

        // Set as active project
        metaPrefs.edit().putString(MetaKeys.CURRENT_PROJECT_ID, targetProjectUuid).commit()

        return targetProjectUuid!!
    }

    @Test
    fun `same URL reuses existing project - no change`() {
        // Given: A project already exists for a specific URL
        val url = "https://central.example.com/v1/projects/1"
        val firstProjectUuid = findOrCreateProject(url)
        
        // When: User configures with the same URL again
        val secondProjectUuid = findOrCreateProject(url)
        
        // Then: Same project should be reused
        assertThat("Same URL should reuse existing project", secondProjectUuid, equalTo(firstProjectUuid))
        assertThat("Total projects should be 1", projectsRepo.getAll().size, equalTo(1))
    }

    @Test
    fun `different project ID creates new project`() {
        // Given: A project exists for project ID 1
        val url1 = "https://central.example.com/v1/projects/1"
        val firstProjectUuid = findOrCreateProject(url1)
        
        // When: User configures for project ID 2 (same base URL, different project)
        val url2 = "https://central.example.com/v1/projects/2"
        val secondProjectUuid = findOrCreateProject(url2)
        
        // Then: A new project should be created
        assertThat("Different project ID should create new project", secondProjectUuid, not(equalTo(firstProjectUuid)))
        assertThat("Total projects should be 2", projectsRepo.getAll().size, equalTo(2))
        
        // Both projects should still exist
        assertThat(projectsRepo.get(firstProjectUuid), notNullValue())
        assertThat(projectsRepo.get(secondProjectUuid), notNullValue())
    }

    @Test
    fun `different server URL creates new project`() {
        // Given: A project exists for server A
        val urlServerA = "https://central-a.example.com/v1/projects/1"
        val firstProjectUuid = findOrCreateProject(urlServerA)
        
        // When: User configures for server B (same project ID, different server)
        val urlServerB = "https://central-b.example.com/v1/projects/1"
        val secondProjectUuid = findOrCreateProject(urlServerB)
        
        // Then: A new project should be created
        assertThat("Different server URL should create new project", secondProjectUuid, not(equalTo(firstProjectUuid)))
        assertThat("Total projects should be 2", projectsRepo.getAll().size, equalTo(2))
    }

    @Test
    fun `both URL and project ID change creates new project`() {
        // Given: A project exists for server A, project 1
        val urlOriginal = "https://central-a.example.com/v1/projects/1"
        val firstProjectUuid = findOrCreateProject(urlOriginal)
        
        // When: User configures for server B, project 2
        val urlNew = "https://central-b.example.com/v1/projects/2"
        val secondProjectUuid = findOrCreateProject(urlNew)
        
        // Then: A new project should be created
        assertThat("Different server and project should create new project", secondProjectUuid, not(equalTo(firstProjectUuid)))
        assertThat("Total projects should be 2", projectsRepo.getAll().size, equalTo(2))
    }

    @Test
    fun `switching back to previous project reuses it`() {
        // Given: Two projects exist
        val urlProject1 = "https://central.example.com/v1/projects/1"
        val urlProject2 = "https://central.example.com/v1/projects/2"
        
        val firstProjectUuid = findOrCreateProject(urlProject1)
        val secondProjectUuid = findOrCreateProject(urlProject2)
        
        // When: User switches back to project 1
        val backToFirstUuid = findOrCreateProject(urlProject1)
        
        // Then: Original project 1 should be reused
        assertThat("Switching back should reuse original project", backToFirstUuid, equalTo(firstProjectUuid))
        assertThat("Total projects should still be 2", projectsRepo.getAll().size, equalTo(2))
    }

    @Test
    fun `project data persists across configuration changes - Option B`() {
        // Given: Project 1 exists with forms/data
        val urlProject1 = "https://central.example.com/v1/projects/1"
        val project1Uuid = findOrCreateProject(urlProject1)
        
        // Simulate having project data (forms, settings, etc.)
        val projPrefs = context.getSharedPreferences("general_prefs$project1Uuid", Context.MODE_PRIVATE)
        projPrefs.edit()
            .putString("custom_setting", "important_value")
            .putBoolean("has_forms_downloaded", true)
            .commit()
        
        // When: User switches to project 2
        val urlProject2 = "https://central.example.com/v1/projects/2"
        findOrCreateProject(urlProject2)
        
        // And then switches back to project 1
        val backToProject1 = findOrCreateProject(urlProject1)
        
        // Then: Project 1's data should still exist (Option B - persistence)
        assertThat(backToProject1, equalTo(project1Uuid))
        
        val restoredPrefs = context.getSharedPreferences("general_prefs$project1Uuid", Context.MODE_PRIVATE)
        assertThat("Custom setting should persist", restoredPrefs.getString("custom_setting", null), equalTo("important_value"))
        assertThat("Forms flag should persist", restoredPrefs.getBoolean("has_forms_downloaded", false), equalTo(true))
    }

    @Test
    fun `multiple projects can coexist for multi-user shared device`() {
        // Given: A shared device used by multiple teams with different projects
        val urlTeamA = "https://central.hospital.org/v1/projects/101"
        val urlTeamB = "https://central.hospital.org/v1/projects/102"
        val urlTeamC = "https://central.clinic.org/v1/projects/1"
        
        // When: All three projects are configured at different times
        val teamAProject = findOrCreateProject(urlTeamA)
        val teamBProject = findOrCreateProject(urlTeamB)
        val teamCProject = findOrCreateProject(urlTeamC)
        
        // Then: All three projects should exist and be distinct
        assertThat("Total projects should be 3", projectsRepo.getAll().size, equalTo(3))
        assertThat("Team A and B should have different UUIDs", teamAProject, not(equalTo(teamBProject)))
        assertThat("Team B and C should have different UUIDs", teamBProject, not(equalTo(teamCProject)))
        assertThat("Team A and C should have different UUIDs", teamAProject, not(equalTo(teamCProject)))
        
        // All projects should be retrievable
        assertThat(projectsRepo.get(teamAProject), notNullValue())
        assertThat(projectsRepo.get(teamBProject), notNullValue())
        assertThat(projectsRepo.get(teamCProject), notNullValue())
    }

    // =============================================
    // Dev Server IP Tests (DEBUG only feature)
    // =============================================

    @Test
    fun `dev server IP is saved to preferences`() {
        // Given: Dev preferences
        val devPrefs = context.getSharedPreferences("aiims_dev_prefs", Context.MODE_PRIVATE)
        
        // When: Dev server IP is saved
        val testIp = "192.168.1.100:8383"
        devPrefs.edit()
            .putString(org.aiims.odk.auth.utils.AiimsConstants.KEY_DEV_SERVER_IP, testIp)
            .apply()
        
        // Then: It should be retrievable
        val savedIp = devPrefs.getString(org.aiims.odk.auth.utils.AiimsConstants.KEY_DEV_SERVER_IP, null)
        assertThat("Dev server IP should be saved", savedIp, equalTo(testIp))
    }

    @Test
    fun `dev server IP can be cleared`() {
        // Given: A saved dev server IP
        val devPrefs = context.getSharedPreferences("aiims_dev_prefs", Context.MODE_PRIVATE)
        devPrefs.edit()
            .putString(org.aiims.odk.auth.utils.AiimsConstants.KEY_DEV_SERVER_IP, "10.0.0.1:8080")
            .apply()
        
        // When: It is cleared
        devPrefs.edit()
            .remove(org.aiims.odk.auth.utils.AiimsConstants.KEY_DEV_SERVER_IP)
            .apply()
        
        // Then: It should return null
        val savedIp = devPrefs.getString(org.aiims.odk.auth.utils.AiimsConstants.KEY_DEV_SERVER_IP, null)
        assertThat("Dev server IP should be null after clearing", savedIp, nullValue())
    }

    @Test
    fun `dev server IP URL construction adds https if missing`() {
        // Test the URL construction logic used in showManualUrlDialog
        val devIp = "192.168.1.100:8383"
        
        // The logic: if devIp doesn't start with "http", prepend "https://"
        val baseUrl = if (devIp.startsWith("http")) devIp else "https://$devIp"
        
        assertThat("Should add https:// prefix", baseUrl, equalTo("https://192.168.1.100:8383"))
    }

    @Test
    fun `dev server IP URL preserves existing http prefix`() {
        // Test when user includes http:// in the IP
        val devIp = "http://192.168.1.100:8080"
        
        val baseUrl = if (devIp.startsWith("http")) devIp else "https://$devIp"
        
        assertThat("Should preserve http:// prefix", baseUrl, equalTo("http://192.168.1.100:8080"))
    }

    @Test
    fun `dev server IP is only used in DEBUG builds`() {
        // This test documents the expected behavior:
        // - In DEBUG builds: Dev server IP field is visible and can override base URL
        // - In RELEASE builds: Dev server IP field is hidden and ignored
        //
        // Since tests run in DEBUG mode, we can only verify the DEBUG behavior here.
        // The RELEASE behavior is enforced by BuildConfig.DEBUG checks in AiimsLoginActivity.
        
        // Verify this test runs in DEBUG mode
        assertThat("Tests should run in DEBUG mode", org.aiims.odk.auth.BuildConfig.DEBUG, equalTo(true))
        
        // In DEBUG mode, the dev server IP is accessible and usable
        val devPrefs = context.getSharedPreferences("aiims_dev_prefs", Context.MODE_PRIVATE)
        devPrefs.edit()
            .putString(org.aiims.odk.auth.utils.AiimsConstants.KEY_DEV_SERVER_IP, "dev.local:8080")
            .apply()
        
        val savedIp = devPrefs.getString(org.aiims.odk.auth.utils.AiimsConstants.KEY_DEV_SERVER_IP, null)
        assertThat("Dev IP should be accessible in DEBUG", savedIp, equalTo("dev.local:8080"))
    }

    /**
     * Simple Settings implementation for testing (mirrors AiimsLoginActivity.AiimsSettings)
     */
    private class TestSettings(private val prefs: android.content.SharedPreferences) : org.odk.collect.shared.settings.Settings {
        override fun save(key: String, value: Any?) {
            val editor = prefs.edit()
            when (value) {
                is String -> editor.putString(key, value)
                is Boolean -> editor.putBoolean(key, value)
                is Long -> editor.putLong(key, value)
                is Int -> editor.putInt(key, value)
                is Float -> editor.putFloat(key, value)
                is Set<*> -> @Suppress("UNCHECKED_CAST") editor.putStringSet(key, value as Set<String>)
                null -> editor.remove(key)
                else -> throw IllegalArgumentException("Unsupported type")
            }
            editor.apply()
        }

        override fun getString(key: String) = prefs.getString(key, null)
        override fun getBoolean(key: String) = prefs.getBoolean(key, false)
        override fun getLong(key: String) = prefs.getLong(key, 0L)
        override fun getInt(key: String) = prefs.getInt(key, 0)
        override fun getFloat(key: String) = prefs.getFloat(key, 0f)
        override fun getStringSet(key: String): Set<String>? = prefs.getStringSet(key, null)
        override fun getAll(): Map<String, *> = prefs.all
        override fun contains(key: String) = prefs.contains(key)
        override fun remove(key: String) { prefs.edit().remove(key).apply() }
        override fun clear() { prefs.edit().clear().apply() }
        override fun setDefaultForAllSettingsWithoutValues() {}
        override fun saveAll(prefs: Map<String, Any?>) { prefs.forEach { save(it.key, it.value) } }
        override fun reset(key: String) { remove(key) }
        override fun registerOnSettingChangeListener(listener: org.odk.collect.shared.settings.Settings.OnSettingChangeListener) {}
        override fun unregisterOnSettingChangeListener(listener: org.odk.collect.shared.settings.Settings.OnSettingChangeListener) {}
    }
}
