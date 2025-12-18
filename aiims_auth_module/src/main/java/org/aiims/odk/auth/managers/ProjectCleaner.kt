package org.aiims.odk.auth.managers

/**
 * Interface to clean up project data (forms, instances, etc.) when a user logs out.
 * This should be implemented by the app module which has access to storage layers.
 */
interface ProjectCleaner {
    /**
     * Clear all sensitive forms and instances for the given project ID.
     */
    fun clearProjectData(projectId: String)
}
