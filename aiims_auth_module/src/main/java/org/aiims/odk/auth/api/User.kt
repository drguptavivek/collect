package org.aiims.odk.auth.api

/**
 * User data class for authentication (Central Backend)
 */
data class User(
    val id: String,
    val username: String = "",
    val projectId: String = "",
    val expiresAt: String? = null,
    // Legacy fields kept for compatibility
    val name: String = username,
    val role: String = "App User",
    val isActive: Boolean = true,
    // Compat fields for legacy code
    val email: String = username,
    val partnerId: String? = null,
    val partnerName: String? = null,
    val phoneNumber: String? = null,
    val dateActiveTill: String? = null
)