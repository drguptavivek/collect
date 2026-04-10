package edu.aiims.medresodk.auth.qr

/**
 * Sealed hierarchy of typed staged QR contexts persisted between the scanner and login screen.
 *
 * These are written by [MedresQrStagingStore] after classification and read by
 * [MedresLoginActivity] to drive the correct flow without any further URL substring checks.
 */
sealed interface MedresStagedQrContext

/**
 * Staged context for a MEDRES project onboarding QR.
 *
 * After staging, the login screen should:
 *  1. Show username/password credentials UI.
 *  2. On success, write `server_url = <authBaseUrl>/key/<TOKEN>/projects/<centralProjectId>`.
 *
 * @property authBaseUrl       Auth API base (e.g. `https://central.aiims.edu/v1`).
 * @property centralProjectId  Integer project ID as string.
 * @property projectName       Durable MEDRES project identity (from QR metadata).
 * @property usernameHint      Pre-fills username field when available.
 * @property generalSettingsJson  Raw JSON string of the `general` QR section.
 * @property adminSettingsJson    Raw JSON string of the `admin` QR section.
 */
data class StagedMedresProjectContext(
    val authBaseUrl: String,
    val centralProjectId: String,
    val projectName: String?,
    val usernameHint: String?,
    val generalSettingsJson: String,
    val adminSettingsJson: String
) : MedresStagedQrContext

/**
 * Staged context for a draft / demo form QR.
 *
 * After staging, the login screen should:
 *  1. **Skip** username/password UI entirely.
 *  2. Apply the draft URL directly as `server_url` in demo mode.
 *  3. Show [displayName] as a contextual label; do **not** rename the durable MEDRES project.
 *
 * @property originalDraftUrl  Full draft URL preserved exactly.
 * @property centralProjectId  Derived from URL path.
 * @property formId            Derived from URL path.
 * @property displayName       Display label (`[Draft] ...`); display-only, not project identity.
 * @property displayIcon       Optional display icon character.
 * @property generalSettingsJson Raw JSON of the `general` section.
 */
data class StagedDraftFormContext(
    val originalDraftUrl: String,
    val centralProjectId: String,
    val formId: String,
    val displayName: String?,
    val displayIcon: String?,
    val generalSettingsJson: String
) : MedresStagedQrContext
