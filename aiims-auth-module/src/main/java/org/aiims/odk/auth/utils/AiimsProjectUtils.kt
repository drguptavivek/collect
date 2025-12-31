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

    /**
     * Formats a URL for display to the user by stripping /v1 and other path segments.
     * Example: https://central.example.org/v1/projects/5 -> https://central.example.org
     */
    fun formatUrlForDisplay(serverUrl: String?): String? {
        if (serverUrl.isNullOrBlank()) return serverUrl

        return try {
            val uri = Uri.parse(serverUrl)
            val scheme = uri.scheme ?: "https"
            val host = uri.host ?: return serverUrl
            val port = if (uri.port != -1) ":${uri.port}" else ""
            
            "$scheme://$host$port"
        } catch (e: Exception) {
            serverUrl
        }
    }

    /**
     * Formats a URL for internal API usage by ensuring it has the /v1 suffix.
     * Example: https://central.example.org -> https://central.example.org/v1
     */
    fun formatUrlForApi(baseUrl: String?): String? {
        if (baseUrl.isNullOrBlank()) return baseUrl

        var url = baseUrl.trim().trimEnd('/')
        
        // Ensure scheme
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "https://$url"
        }

        // Add /v1 if missing
        return if (!url.contains("/v1")) {
            "$url/v1"
        } else {
            // If it already has /v1, ensure it's not buried in a longer path if we want a clean API base
            // But for ODK compatibility, we often store the full /v1/projects/X URL.
            // If the user entered something with /v1, we keep it as is.
            url
        }
    }
}
