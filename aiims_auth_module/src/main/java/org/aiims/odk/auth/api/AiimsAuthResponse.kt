package org.aiims.odk.auth.api

import com.google.gson.annotations.SerializedName

/**
 * Data classes for authentication API responses.
 */

/**
 * Base API response wrapper.
 *
 * @param success Whether the request was successful
 * @param message Response message
 * @param data Response data (optional)
 * @param error Error details (optional)
 */
data class ApiResponse<T>(
    @SerializedName("success")
    val success: Boolean,

    @SerializedName("message")
    val message: String,

    @SerializedName("data")
    val data: T? = null,

    @SerializedName("error")
    val error: ApiError? = null
)

/**
 * API error details.
 *
 * @param code Error code
 * @param message Error message
 * @param details Additional error details (optional)
 */
data class ApiError(
    @SerializedName("code")
    val code: String,

    @SerializedName("message")
    val message: String,

    @SerializedName("details")
    val details: Map<String, Any>? = null
)

/**
 * Login response containing user information and tokens.
 *
 * @param user User information
 * @param deviceToken JWT device token for authentication
 * @param refreshToken Token for refreshing device token
 * @param expiresAt Token expiration timestamp (ISO 8601)
 * @param requiresPinSetup Whether user needs to set up PIN
 * @param isFirstLogin Whether this is the user's first login
 */
data class LoginResponse(
    @SerializedName("user")
    val user: User,

    @SerializedName("deviceToken")
    val deviceToken: String,

    @SerializedName("refreshToken")
    val refreshToken: String,

    @SerializedName("expiresAt")
    val expiresAt: String,

    @SerializedName("requiresPinSetup")
    val requiresPinSetup: Boolean,

    @SerializedName("isFirstLogin")
    val isFirstLogin: Boolean
)

/**
 * User information.
 *
 * @param id Unique user identifier
 * @param email User's email address
 * @param name User's display name
 * @param role User's role (national_admin, data_manager, partner_manager, team_member)
 * @param partnerId Partner ID (null for national_admin and data_manager)
 * @param partnerName Partner name (null if no partner)
 * @param phoneNumber User's phone number (optional)
 * @param isActive Whether user account is active
 * @param dateActiveTill Account expiry date (optional)
 */
data class User(
    @SerializedName("id")
    val id: String,

    @SerializedName("email")
    val email: String,

    @SerializedName("name")
    val name: String,

    @SerializedName("role")
    val role: String,

    @SerializedName("partnerId")
    val partnerId: String?,

    @SerializedName("partnerName")
    val partnerName: String?,

    @SerializedName("phoneNumber")
    val phoneNumber: String?,

    @SerializedName("isActive")
    val isActive: Boolean,

    @SerializedName("dateActiveTill")
    val dateActiveTill: String?
)

/**
 * Token verification response.
 *
 * @param valid Whether the token is valid
 * @param expiresAt Token expiration timestamp
 * @param user Current user information (if valid)
 */
data class VerifyTokenResponse(
    @SerializedName("valid")
    val valid: Boolean,

    @SerializedName("expiresAt")
    val expiresAt: String?,

    @SerializedName("user")
    val user: User?
)

/**
 * Token refresh response.
 *
 * @param deviceToken New device token
 * @param refreshToken New refresh token
 * @param expiresAt New expiration timestamp
 */
data class RefreshTokenResponse(
    @SerializedName("deviceToken")
    val deviceToken: String,

    @SerializedName("refreshToken")
    val refreshToken: String,

    @SerializedName("expiresAt")
    val expiresAt: String
)

/**
 * School information for partner.
 *
 * @param id School unique identifier
 * @param name School name
 * @param code School code
 * @param address School address
 * @param district District name
 * @param pinCode Postal code
 * @param phone Phone number
 * @param email Email address
 * @param isActive Whether school is active
 */
data class School(
    @SerializedName("id")
    val id: String,

    @SerializedName("name")
    val name: String,

    @SerializedName("code")
    val code: String,

    @SerializedName("address")
    val address: String?,

    @SerializedName("district")
    val district: String?,

    @SerializedName("pinCode")
    val pinCode: String?,

    @SerializedName("phone")
    val phone: String?,

    @SerializedName("email")
    val email: String?,

    @SerializedName("isActive")
    val isActive: Boolean
)

/**
 * Survey submission response.
 *
 * @param surveyId Submitted survey ID
 * @param status Submission status
 * @param timestamp Submission timestamp
 * @param requiresSync Whether data needs to be synced
 */
data class SurveySubmissionResponse(
    @SerializedName("surveyId")
    val surveyId: String,

    @SerializedName("status")
    val status: String,

    @SerializedName("timestamp")
    val timestamp: Long,

    @SerializedName("requiresSync")
    val requiresSync: Boolean
)

/**
 * Bulk sync response.
 *
 * @param syncedCount Number of successfully synced items
 * @param failedCount Number of failed items
 * @param failedItems List of failed items with error details
 * @param nextSyncTimestamp Recommended next sync time
 */
data class BulkSyncResponse(
    @SerializedName("syncedCount")
    val syncedCount: Int,

    @SerializedName("failedCount")
    val failedCount: Int,

    @SerializedName("failedItems")
    val failedItems: List<SyncFailure>,

    @SerializedName("nextSyncTimestamp")
    val nextSyncTimestamp: Long
)

/**
 * Sync failure details.
 *
 * @param id Item ID
 * @param error Error message
 * @param retryable Whether the item can be retried
 */
data class SyncFailure(
    @SerializedName("id")
    val id: String,

    @SerializedName("error")
    val error: String,

    @SerializedName("retryable")
    val retryable: Boolean
)