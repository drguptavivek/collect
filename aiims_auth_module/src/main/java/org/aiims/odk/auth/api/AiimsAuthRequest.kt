package org.aiims.odk.auth.api

import com.google.gson.annotations.SerializedName

/**
 * Data classes for authentication API requests.
 */

/**
 * Login request containing user credentials and device information.
 *
 * @param email User's email address
 * @param password User's password
 * @param deviceId Unique device identifier
 * @param deviceInfo Device information (model, OS version, etc.)
 */
data class LoginRequest(
    @SerializedName("email")
    val email: String,

    @SerializedName("password")
    val password: String,

    @SerializedName("deviceId")
    val deviceId: String,

    @SerializedName("deviceInfo")
    val deviceInfo: String
)

/**
 * Token verification request.
 *
 * @param deviceToken JWT device token to verify
 */
data class VerifyTokenRequest(
    @SerializedName("deviceToken")
    val deviceToken: String
)

/**
 * Token refresh request.
 *
 * @param refreshToken Refresh token (optional, uses stored token if not provided)
 */
data class RefreshTokenRequest(
    @SerializedName("refreshToken")
    val refreshToken: String? = null
)

/**
 * Logout request.
 *
 * @param deviceToken Device token to invalidate
 * @param deviceId Device identifier
 */
data class LogoutRequest(
    @SerializedName("deviceToken")
    val deviceToken: String,

    @SerializedName("deviceId")
    val deviceId: String
)

/**
 * PIN setup request (if PIN needs to be registered with server).
 *
 * @param pinHash Hashed PIN value
 * @param deviceId Device identifier
 */
data class PinSetupRequest(
    @SerializedName("pinHash")
    val pinHash: String,

    @SerializedName("deviceId")
    val deviceId: String
)

/**
 * Device registration request for first-time setup.
 *
 * @param userId User ID
 * @param deviceId Unique device identifier
 * @param deviceName Human-readable device name
 * @param deviceType Type of device (android, ios, etc.)
 * @param appVersion Application version
 */
data class DeviceRegistrationRequest(
    @SerializedName("userId")
    val userId: String,

    @SerializedName("deviceId")
    val deviceId: String,

    @SerializedName("deviceName")
    val deviceName: String,

    @SerializedName("deviceType")
    val deviceType: String = "android",

    @SerializedName("appVersion")
    val appVersion: String
)

/**
 * Survey submission request.
 *
 * @param surveyData JSON string containing survey data
 * @param surveyType Type of survey being submitted
 * @param deviceId Device identifier
 * @param timestamp Submission timestamp
 * @param isOffline Whether submission is from offline queue
 */
data class SurveySubmissionRequest(
    @SerializedName("surveyData")
    val surveyData: String,

    @SerializedName("surveyType")
    val surveyType: String,

    @SerializedName("deviceId")
    val deviceId: String,

    @SerializedName("timestamp")
    val timestamp: Long,

    @SerializedName("isOffline")
    val isOffline: Boolean = false
)

/**
 * Bulk sync request for offline data.
 *
 * @param surveys List of survey data to sync
 * @param deviceId Device identifier
 * @param lastSyncTimestamp Last successful sync timestamp
 */
data class BulkSyncRequest(
    @SerializedName("surveys")
    val surveys: List<SurveySubmissionRequest>,

    @SerializedName("deviceId")
    val deviceId: String,

    @SerializedName("lastSyncTimestamp")
    val lastSyncTimestamp: Long?
)