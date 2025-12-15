package org.aiims.odk.auth.api

import android.content.Context
import kotlinx.coroutines.flow.Flow

/**
 * Placeholder API client to get build working
 */
class AiimsApiClient private constructor(
    private val context: Context
) {
    companion object {
        @Volatile
        private var INSTANCE: AiimsApiClient? = null

        fun getInstance(context: Context): AiimsApiClient {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: createInstance(context.applicationContext).also { INSTANCE = it }
            }
        }

        private fun createInstance(context: Context): AiimsApiClient {
            return AiimsApiClient(context)
        }
    }

    // Placeholder methods
    suspend fun login(email: String, password: String): AuthResult {
        return AuthResult.Success(
            User(
                id = "1",
                email = email,
                name = "Test User",
                role = "team_member",
                partnerId = null,
                partnerName = null,
                phoneNumber = null,
                isActive = true,
                dateActiveTill = null
            )
        )
    }
}