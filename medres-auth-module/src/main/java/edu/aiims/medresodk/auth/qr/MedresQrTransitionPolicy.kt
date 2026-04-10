package edu.aiims.medresodk.auth.qr

import java.net.URI

sealed interface CurrentQrContext {
    data object None : CurrentQrContext
    data class MedresProject(val centralProjectId: String?) : CurrentQrContext
    data class DraftForm(val identity: String?) : CurrentQrContext
    data class StandardOdkManaged(val centralProjectId: String?) : CurrentQrContext
    data class Unknown(val serverUrl: String) : CurrentQrContext
}

object MedresQrTransitionPolicy {

    fun classifyCurrentServerUrl(serverUrl: String?): CurrentQrContext {
        if (serverUrl.isNullOrBlank()) {
            return CurrentQrContext.None
        }

        return when {
            isDraftUrl(serverUrl) -> CurrentQrContext.DraftForm(extractDraftIdentity(serverUrl))
            isStandardOdkManagedUrl(serverUrl) -> CurrentQrContext.StandardOdkManaged(extractProjectId(serverUrl))
            isMedresProjectUrl(serverUrl) -> CurrentQrContext.MedresProject(extractProjectId(serverUrl))
            else -> CurrentQrContext.Unknown(serverUrl)
        }
    }

    fun requiresSessionReset(current: CurrentQrContext, scanned: MedresQrParseResult): Boolean {
        return when (scanned) {
            is InvalidQr,
            is StandardOdkManagedQr -> false

            is MedresProjectQr -> when (current) {
                is CurrentQrContext.None -> false
                is CurrentQrContext.MedresProject -> current.centralProjectId != scanned.centralProjectId
                is CurrentQrContext.DraftForm,
                is CurrentQrContext.StandardOdkManaged,
                is CurrentQrContext.Unknown -> true
            }

            is DraftFormQr -> when (current) {
                is CurrentQrContext.None -> false
                is CurrentQrContext.DraftForm -> current.identity != extractDraftIdentity(scanned.originalDraftUrl)
                is CurrentQrContext.MedresProject,
                is CurrentQrContext.StandardOdkManaged,
                is CurrentQrContext.Unknown -> true
            }
        }
    }

    private fun isDraftUrl(url: String): Boolean =
        url.contains("/test/") && url.contains("/draft")

    private fun isStandardOdkManagedUrl(url: String): Boolean =
        url.contains("/key/") && !isDraftUrl(url)

    private fun isMedresProjectUrl(url: String): Boolean =
        !isDraftUrl(url) && !url.contains("/key/") && url.contains("/projects/")

    private fun extractProjectId(url: String): String? {
        return try {
            val pathSegments = URI(url).path.orEmpty()
                .trimEnd('/')
                .split("/")
                .filter { it.isNotEmpty() }
            val projectIdx = pathSegments.indexOf("projects")
            if (projectIdx < 0 || projectIdx + 1 >= pathSegments.size) null else pathSegments[projectIdx + 1]
        } catch (_: Exception) {
            null
        }
    }

    private fun extractDraftIdentity(url: String): String? {
        return try {
            val uri = URI(url)
            val pathSegments = uri.path.orEmpty()
                .trimEnd('/')
                .split("/")
                .filter { it.isNotEmpty() }
            val projectIdx = pathSegments.indexOf("projects")
            val formIdx = pathSegments.indexOf("forms")
            if (projectIdx < 0 || formIdx < 0 || projectIdx + 1 >= pathSegments.size || formIdx + 1 >= pathSegments.size) {
                null
            } else {
                val projectId = pathSegments[projectIdx + 1]
                val formId = pathSegments[formIdx + 1]
                val authority = uri.authority ?: return null
                val scheme = uri.scheme ?: "https"
                "$scheme://$authority|$projectId|$formId"
            }
        } catch (_: Exception) {
            null
        }
    }
}
