package edu.aiims.medresodk.auth.utils

import android.net.Uri

/**
 * Utilities for parsing Project information from ODK Settings.
 */
object MedresProjectUtils {

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

        return try {
            val uri = Uri.parse(url)
            val scheme = uri.scheme
            val authority = uri.authority // host:port
            var path = uri.path ?: ""
            
            // If path contains /v1, we're good. Otherwise, we need to add it.
            // If path is empty or just /, we append /v1.
            // If path has other stuff (like /projects/1), we prepend /v1.
            if (!path.contains("/v1")) {
                path = if (path.isEmpty() || path == "/") "/v1" else "/v1$path"
            }
            
            "$scheme://$authority$path"
        } catch (e: Exception) {
            // Fallback for malformed
            if (!url.contains("/v1")) {
                // Remove existing projects path if we're falling back and it's there
                val base = if (url.contains("/projects/")) url.substringBefore("/projects/") else url
                "$base/v1"
            } else url
        }
    }
}
