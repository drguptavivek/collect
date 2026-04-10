package edu.aiims.medresodk.auth.qr

import org.json.JSONObject

/**
 * Pure-logic QR parser and classifier.
 *
 * Responsibilities:
 *  1. Decode the raw Base64-compressed QR payload (via the injected [Decompressor]).
 *  2. Enforce byte-accurate size limits (compressed & decompressed).
 *  3. Enforce a compression-ratio guard.
 *  4. Parse the JSON structure.
 *  5. Classify the `server_url` into one of [MedresQrParseResult] subtypes.
 *
 * No Android framework dependencies — fully unit-testable on the JVM.
 */
class MedresQrParser(
    private val decompressor: Decompressor = CompressionUtilsDecompressor,
    private val maxCompressedBytes: Int = MAX_COMPRESSED_BYTES,
    private val maxDecompressedBytes: Int = MAX_DECOMPRESSED_BYTES,
    private val maxCompressionRatio: Float = MAX_COMPRESSION_RATIO
) {

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    fun parse(rawQrData: String): MedresQrParseResult {
        // --- 1. Byte-accurate compressed size check ---
        val compressedByteCount = rawQrData.toByteArray(Charsets.UTF_8).size
        if (compressedByteCount > maxCompressedBytes) {
            return InvalidQr(
                "Compressed payload too large: $compressedByteCount bytes " +
                        "(max $maxCompressedBytes)"
            )
        }

        // --- 2. Decompress ---
        val decompressedJson = try {
            decompressor.decompress(rawQrData)
        } catch (e: Exception) {
            return InvalidQr("Decompression failed: ${e.message}")
        }

        // --- 3. Byte-accurate decompressed size check ---
        val decompressedByteCount = decompressedJson.toByteArray(Charsets.UTF_8).size
        if (decompressedByteCount > maxDecompressedBytes) {
            return InvalidQr(
                "Decompressed payload too large: $decompressedByteCount bytes " +
                        "(max $maxDecompressedBytes) — possible decompression bomb"
            )
        }

        // --- 4. Compression-ratio guard ---
        if (compressedByteCount > 0) {
            val ratio = decompressedByteCount.toFloat() / compressedByteCount.toFloat()
            if (ratio > maxCompressionRatio) {
                return InvalidQr(
                    "Compression ratio $ratio exceeds maximum $maxCompressionRatio — " +
                            "possible decompression bomb"
                )
            }
        }

        // --- 5. Parse JSON ---
        val json = try {
            JSONObject(decompressedJson)
        } catch (e: Exception) {
            return InvalidQr("JSON parse failed: ${e.message}")
        }

        val general = json.optJSONObject("general") ?: JSONObject()
        val admin = json.optJSONObject("admin") ?: JSONObject()
        val project = json.optJSONObject("project") ?: JSONObject()

        val serverUrl = general.optString("server_url", "").trim()
        if (serverUrl.isEmpty()) {
            return InvalidQr("Missing general.server_url")
        }

        val generalJson = general.toString()
        val adminJson = admin.toString()

        // --- 6. Classify by URL shape ---
        return classify(serverUrl, generalJson, adminJson, project)
    }

    // -------------------------------------------------------------------------
    // Classification
    // -------------------------------------------------------------------------

    private fun classify(
        serverUrl: String,
        generalJson: String,
        adminJson: String,
        project: JSONObject
    ): MedresQrParseResult {
        return when {
            isDraftUrl(serverUrl) -> buildDraftFormQr(serverUrl, generalJson, project)
            isManagedKeyUrl(serverUrl) -> StandardOdkManagedQr(originalUrl = serverUrl)
            isMedresProjectUrl(serverUrl) -> buildMedresProjectQr(
                serverUrl, generalJson, adminJson, project
            )
            else -> InvalidQr("Unrecognised URL shape: $serverUrl")
        }
    }

    // -------------------------------------------------------------------------
    // URL shape predicates
    // -------------------------------------------------------------------------

    /**
     * Draft/demo QR: must contain **both** `/test/` and `/draft` in the path.
     * Shape: `.../v1/test/<TOKEN>/projects/<PID>/forms/<FORM_ID>/draft`
     */
    internal fun isDraftUrl(url: String): Boolean {
        val path = extractPath(url)
        return DRAFT_URL_REGEX.matches(path)
    }

    /**
     * Standard ODK managed QR: contains `/key/` in the path but is NOT a draft.
     * Shape: `.../v1/key/<TOKEN>/projects/<PID>`
     */
    internal fun isManagedKeyUrl(url: String): Boolean {
        val path = extractPath(url)
        return MANAGED_KEY_URL_REGEX.matches(path) && !isDraftUrl(url)
    }

    /**
     * MEDRES project QR: path ends with `/projects/<PID>` and has no `/key/` segment.
     * Shape: `.../v1/projects/<PID>`
     */
    internal fun isMedresProjectUrl(url: String): Boolean {
        val path = extractPath(url)
        return MEDRES_PROJECT_URL_REGEX.matches(path) &&
                !path.contains("/key/") &&
                !path.contains("/test/")
    }

    // -------------------------------------------------------------------------
    // Builders
    // -------------------------------------------------------------------------

    private fun buildDraftFormQr(
        url: String,
        generalJson: String,
        project: JSONObject
    ): MedresQrParseResult {
        val path = extractPath(url)
        val segments = path.trimEnd('/').split("/").filter { it.isNotEmpty() }

        // Shape: v1 / test / <TOKEN> / projects / <PID> / forms / <FORM_ID> / draft
        val projectId = extractSegmentAfter(segments, "projects")
            ?.takeIf { it.isNotBlank() }
            ?: return InvalidQr("Draft QR missing project id in URL: $url")
        val formId = extractSegmentAfter(segments, "forms")
            ?.takeIf { it.isNotBlank() }
            ?: return InvalidQr("Draft QR missing form id in URL: $url")

        return DraftFormQr(
            originalDraftUrl = url,
            centralProjectId = projectId,
            formId = formId,
            displayName = project.optString("name", "").takeIf { it.isNotEmpty() },
            displayIcon = project.optString("icon", "").takeIf { it.isNotEmpty() },
            generalSettingsJson = generalJson
        )
    }

    private fun buildMedresProjectQr(
        url: String,
        generalJson: String,
        adminJson: String,
        project: JSONObject
    ): MedresQrParseResult {
        val path = extractPath(url)
        val segments = path.trimEnd('/').split("/").filter { it.isNotEmpty() }
        val projectId = extractSegmentAfter(segments, "projects")
            ?.takeIf { it.isNotBlank() }
            ?: return InvalidQr("MEDRES project QR missing project id in URL: $url")

        // Auth base URL: everything up to (not including) /projects
        val authBaseUrl = extractAuthBase(url)

        // Username hint from the general section
        val generalObj = runCatching { JSONObject(generalJson) }.getOrDefault(JSONObject())
        val usernameHint = generalObj.optString("username", "")
            .takeIf { it.isNotEmpty() }
            ?: generalObj.optString("user_name", "").takeIf { it.isNotEmpty() }

        return MedresProjectQr(
            originalUrl = url,
            authBaseUrl = authBaseUrl,
            centralProjectId = projectId,
            projectName = project.optString("name", "").takeIf { it.isNotEmpty() },
            usernameHint = usernameHint,
            generalSettingsJson = generalJson,
            adminSettingsJson = adminJson
        )
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Extract the path-only portion of a URL without triggering Android's Uri parser. */
    private fun extractPath(url: String): String {
        // Strip scheme + authority: "https://host:port/path?q" → "/path"
        val withoutScheme = if (url.contains("://")) {
            url.substringAfter("://")
        } else {
            url
        }
        // Remove authority (host/port)
        val path = "/" + withoutScheme.substringAfter("/").substringBefore("?").substringBefore("#")
        return path
    }

    /** Strips /projects/... from a URL to produce the auth base. */
    private fun extractAuthBase(url: String): String {
        val idx = url.indexOf("/projects")
        return if (idx >= 0) url.substring(0, idx) else url
    }

    private fun extractSegmentAfter(segments: List<String>, marker: String): String? {
        val idx = segments.indexOf(marker)
        return if (idx >= 0 && idx + 1 < segments.size) segments[idx + 1] else null
    }

    // -------------------------------------------------------------------------
    // Constants
    // -------------------------------------------------------------------------

    companion object {
        private val DRAFT_URL_REGEX =
            Regex("""^/.+/test/[^/]+/projects/[^/]+/forms/[^/]+/draft/?$""")

        private val MANAGED_KEY_URL_REGEX =
            Regex("""^/.+/key/[^/]+/projects/[^/]+/?$""")

        private val MEDRES_PROJECT_URL_REGEX =
            Regex("""^/.+/projects/[^/]+/?$""")

        /** Byte-accurate maximum for the raw (compressed) QR string. */
        const val MAX_COMPRESSED_BYTES = 4096

        /** Byte-accurate maximum for the decompressed JSON string. */
        const val MAX_DECOMPRESSED_BYTES = 16384 // 16 KB

        /**
         * Maximum allowed decompressed:compressed ratio.
         * A compressed JSON blob typically inflates 3–8×; 50× is a clear bomb signal.
         */
        const val MAX_COMPRESSION_RATIO = 50.0f
    }

    // -------------------------------------------------------------------------
    // Decompressor interface (allows test fakes)
    // -------------------------------------------------------------------------

    fun interface Decompressor {
        fun decompress(data: String): String
    }
}

/**
 * Real decompressor backed by ODK's CompressionUtils.
 * Wrapped in an object so it can be referenced as a default parameter without
 * forcing the caller to provide an instance.
 */
internal object CompressionUtilsDecompressor : MedresQrParser.Decompressor {
    override fun decompress(data: String): String =
        org.odk.collect.androidshared.utils.CompressionUtils.decompress(data)
}
