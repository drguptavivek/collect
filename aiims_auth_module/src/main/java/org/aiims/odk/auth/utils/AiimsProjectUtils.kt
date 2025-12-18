package org.aiims.odk.auth.utils

import android.net.Uri

/**
 * Utilities for parsing Project information from ODK Settings.
 */
object AiimsProjectUtils {

    /**
     * Extracts the Project ID from the Server URL.
     * Expected format: .../v1/projects/{projectId}
     * Example: https://central.example.org/v1/projects/5 -> "5"
     */
    fun getProjectIdFromUrl(serverUrl: String?): String? {
        if (serverUrl.isNullOrBlank()) return null

        return try {
            val uri = Uri.parse(serverUrl)
            // Path segments: [v1, projects, 5]
            val segments = uri.pathSegments
            if (segments.size >= 2 && segments[segments.size - 2] == "projects") {
                segments.last()
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
}
