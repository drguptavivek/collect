package org.aiims.odk.auth.api

/**
 * User data class for authentication (Central Backend)
 */
data class User(
    val id: String,
    val username: String = "",
    val projectId: String = "",
    val expiresAt: String? = null
)
