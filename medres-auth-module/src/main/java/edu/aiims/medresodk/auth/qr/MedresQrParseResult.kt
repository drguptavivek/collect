package edu.aiims.medresodk.auth.qr

/**
 * Sealed hierarchy representing the result of parsing and classifying a raw QR payload.
 *
 * Classification rules:
 *  - [MedresProjectQr]     → server_url shape: `.../v1/projects/<PID>`              → requires MEDRES login
 *  - [DraftFormQr]         → server_url shape: `.../v1/test/<TOKEN>/projects/<PID>/forms/<FORM_ID>/draft`
 *  - [StandardOdkManagedQr]→ server_url shape: `.../v1/key/<TOKEN>/projects/<PID>`  → rejected
 *  - [InvalidQr]           → payload too large, decompression bomb, bad JSON, missing fields
 */
sealed interface MedresQrParseResult

/**
 * A genuine MEDRES onboarding QR.
 *
 * @property originalUrl       The full URL as read from the QR (`/v1/projects/<PID>`).
 * @property authBaseUrl       The auth API base (`/v1`), stripped of the projects path.
 * @property centralProjectId  String project ID from the URL path.
 * @property projectName       From `project.name` in QR JSON; used as durable project identity.
 * @property usernameHint      From `general.username` / `general.user_name` if present.
 * @property generalSettingsJson  Raw JSON string of the `general` section.
 * @property adminSettingsJson    Raw JSON string of the `admin` section.
 */
data class MedresProjectQr(
    val originalUrl: String,
    val authBaseUrl: String,
    val centralProjectId: String,
    val projectName: String?,
    val usernameHint: String?,
    val generalSettingsJson: String,
    val adminSettingsJson: String
) : MedresQrParseResult

/**
 * A draft / demo form QR.
 *
 * Must **not** require MEDRES username/password login.
 * The full draft URL must be preserved and never rewritten.
 *
 * @property originalDraftUrl   Full draft URL (`.../v1/test/<TOKEN>/projects/<PID>/forms/<FID>/draft`).
 * @property centralProjectId   Derived from URL path (segment after `projects`).
 * @property formId             Derived from URL path (segment after `forms`).
 * @property displayName        From `project.name`; display-only, **not** durable project identity.
 * @property displayIcon        From `project.icon` if present.
 * @property generalSettingsJson Raw JSON of the `general` section.
 */
data class DraftFormQr(
    val originalDraftUrl: String,
    val centralProjectId: String,
    val formId: String,
    val displayName: String?,
    val displayIcon: String?,
    val generalSettingsJson: String
) : MedresQrParseResult

/**
 * A Standard ODK Central managed QR (has `/key/<TOKEN>/projects/<PID>` shape).
 * Must be explicitly rejected — no staging, no session mutation.
 */
data class StandardOdkManagedQr(
    val originalUrl: String
) : MedresQrParseResult

/**
 * An invalid or unrecognised QR payload.
 *
 * @property reason Human-readable description of why parsing/classification failed.
 */
data class InvalidQr(
    val reason: String
) : MedresQrParseResult
