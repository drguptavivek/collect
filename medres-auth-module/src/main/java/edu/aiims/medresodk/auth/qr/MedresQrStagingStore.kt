package edu.aiims.medresodk.auth.qr

import android.content.SharedPreferences
import edu.aiims.medresodk.auth.utils.MedresConstants

/**
 * Reads and writes typed [MedresStagedQrContext] to/from [SharedPreferences].
 *
 * Rescan cleanup removes **only** session/security keys and staged QR context keys,
 * never broader preference state (e.g. preserved project mappings, PIN state).
 */
class MedresQrStagingStore(private val prefs: SharedPreferences) {

    // -------------------------------------------------------------------------
    // Stage
    // -------------------------------------------------------------------------

    /**
     * Writes a [StagedMedresProjectContext] to prefs, first clearing previous session state.
     */
    fun stageMedresProject(ctx: StagedMedresProjectContext) {
        clearSessionAndStagedState()
        prefs.edit().apply {
            putString(MedresConstants.KEY_STAGED_QR_TYPE, MedresConstants.STAGED_QR_TYPE_MEDRES_PROJECT)
            putString(MedresConstants.KEY_AUTH_URL, ctx.authBaseUrl)
            putString(MedresConstants.KEY_AUTH_PROJECT_ID, ctx.centralProjectId)
            ctx.projectName?.let { putString(MedresConstants.KEY_AUTH_PROJECT_NAME, it) }
                ?: remove(MedresConstants.KEY_AUTH_PROJECT_NAME)
            ctx.usernameHint?.let { putString(MedresConstants.KEY_AUTH_USERNAME_HINT, it) }
                ?: remove(MedresConstants.KEY_AUTH_USERNAME_HINT)
            putString(MedresConstants.KEY_QR_GENERAL_SETTINGS, ctx.generalSettingsJson)
            putString(MedresConstants.KEY_QR_ADMIN_SETTINGS, ctx.adminSettingsJson)
        }.apply()
    }

    /**
     * Writes a [StagedDraftFormContext] to prefs, first clearing previous session state.
     */
    fun stageDraftForm(ctx: StagedDraftFormContext) {
        clearSessionAndStagedState()
        prefs.edit().apply {
            putString(MedresConstants.KEY_STAGED_QR_TYPE, MedresConstants.STAGED_QR_TYPE_DRAFT_FORM)
            putString(MedresConstants.KEY_DRAFT_URL, ctx.originalDraftUrl)
            putString(MedresConstants.KEY_DRAFT_PROJECT_ID, ctx.centralProjectId)
            putString(MedresConstants.KEY_DRAFT_FORM_ID, ctx.formId)
            ctx.displayName?.let { putString(MedresConstants.KEY_DRAFT_DISPLAY_NAME, it) }
                ?: remove(MedresConstants.KEY_DRAFT_DISPLAY_NAME)
            ctx.displayIcon?.let { putString(MedresConstants.KEY_DRAFT_DISPLAY_ICON, it) }
                ?: remove(MedresConstants.KEY_DRAFT_DISPLAY_ICON)
            putString(MedresConstants.KEY_DRAFT_GENERAL_SETTINGS, ctx.generalSettingsJson)
        }.apply()
    }

    // -------------------------------------------------------------------------
    // Read
    // -------------------------------------------------------------------------

    /**
     * Returns the current staged context, or `null` if none is present or the type key is absent.
     */
    fun read(): MedresStagedQrContext? {
        return when (prefs.getString(MedresConstants.KEY_STAGED_QR_TYPE, null)) {
            MedresConstants.STAGED_QR_TYPE_MEDRES_PROJECT -> readMedresProject()
            MedresConstants.STAGED_QR_TYPE_DRAFT_FORM -> readDraftForm()
            else -> null
        }
    }

    private fun readMedresProject(): StagedMedresProjectContext? {
        val authUrl = prefs.getString(MedresConstants.KEY_AUTH_URL, null) ?: return null
        val projectId = prefs.getString(MedresConstants.KEY_AUTH_PROJECT_ID, null) ?: return null
        return StagedMedresProjectContext(
            authBaseUrl = authUrl,
            centralProjectId = projectId,
            projectName = prefs.getString(MedresConstants.KEY_AUTH_PROJECT_NAME, null),
            usernameHint = prefs.getString(MedresConstants.KEY_AUTH_USERNAME_HINT, null),
            generalSettingsJson = prefs.getString(MedresConstants.KEY_QR_GENERAL_SETTINGS, "{}") ?: "{}",
            adminSettingsJson = prefs.getString(MedresConstants.KEY_QR_ADMIN_SETTINGS, "{}") ?: "{}"
        )
    }

    private fun readDraftForm(): StagedDraftFormContext? {
        val draftUrl = prefs.getString(MedresConstants.KEY_DRAFT_URL, null) ?: return null
        val projectId = prefs.getString(MedresConstants.KEY_DRAFT_PROJECT_ID, null) ?: return null
        val formId = prefs.getString(MedresConstants.KEY_DRAFT_FORM_ID, null) ?: return null
        return StagedDraftFormContext(
            originalDraftUrl = draftUrl,
            centralProjectId = projectId,
            formId = formId,
            displayName = prefs.getString(MedresConstants.KEY_DRAFT_DISPLAY_NAME, null),
            displayIcon = prefs.getString(MedresConstants.KEY_DRAFT_DISPLAY_ICON, null),
            generalSettingsJson = prefs.getString(MedresConstants.KEY_DRAFT_GENERAL_SETTINGS, "{}") ?: "{}"
        )
    }

    // -------------------------------------------------------------------------
    // Cleanup — precise, not broad
    // -------------------------------------------------------------------------

    /**
     * Removes only session/security keys and staged QR context keys.
     *
     * Does **not** wipe unrelated preserved state such as:
     *   - Historical project mappings
     *   - PIN state
     *   - Feature flags
     *   - Clock validation entries
     */
    fun clearSessionAndStagedState() {
        prefs.edit().apply {
            MedresConstants.SESSION_KEYS_TO_CLEAR.forEach { remove(it) }
        }.apply()
    }

    // -------------------------------------------------------------------------
    // Type checks (convenience)
    // -------------------------------------------------------------------------

    fun hasMedresProjectContext(): Boolean =
        prefs.getString(MedresConstants.KEY_STAGED_QR_TYPE, null) ==
                MedresConstants.STAGED_QR_TYPE_MEDRES_PROJECT

    fun hasDraftFormContext(): Boolean =
        prefs.getString(MedresConstants.KEY_STAGED_QR_TYPE, null) ==
                MedresConstants.STAGED_QR_TYPE_DRAFT_FORM
}
