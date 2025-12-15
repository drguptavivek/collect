package org.aiims.odk.auth.api

/**
 * User data class for authentication
 */
data class User(
    val id: String,
    val email: String,
    val name: String,
    val role: String,
    val partnerId: String? = null,
    val partnerName: String? = null,
    val phoneNumber: String? = null,
    val isActive: Boolean = true,
    val dateActiveTill: String? = null
)