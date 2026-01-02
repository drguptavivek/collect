package edu.aiims.medresodk.auth.security

import android.os.SystemClock
import android.util.Log
import edu.aiims.medresodk.auth.storage.MedresSecureStorage
import edu.aiims.medresodk.auth.utils.MedresConstants

/**
 * Clock Validator - Detects and prevents clock manipulation attacks.
 *
 * Uses monotonic clock (SystemClock.elapsedRealtime()) which cannot be manipulated
 * to validate wall-clock time (System.currentTimeMillis()).
 *
 * ## Security Strategy
 *
 * 1. **Monotonic Clock**: SystemClock.elapsedRealtime() counts milliseconds since boot
 *    and is not affected by user changes to device time settings.
 *
 * 2. **Anchor Points**: Store the last-known-good wall-clock time with its corresponding
 *    monotonic time to establish a time baseline.
 *
 * 3. **Time Jump Detection**: Compare current wall-clock time against expected time
 *    calculated from the anchor point. Detects suspicious jumps > 30 minutes.
 *
 * 4. **Server Time Validation**: When server time is available, calculate offset and
 *    use it to validate local time.
 *
 * ## Usage
 *
 * ```kotlin
 * val validator = ClockValidator.getInstance(context)
 * val result = validator.getCurrentTime()
 * when (result) {
 *     is ClockValidator.TimeResult.Valid -> {
 *         // Use result.time safely
 *     }
 *     is ClockValidator.TimeResult.ManipulationDetected -> {
 *         // Show warning, prevent re-auth
 *     }
 * }
 * ```
 *
 * @see MedresConstants.CLOCK_MANIPULATION_THRESHOLD_MS
 */
class ClockValidator private constructor(
    private val secureStorage: MedresSecureStorage
) {

    companion object {
        private const val TAG = "ClockValidator"

        @Volatile
        private var INSTANCE: ClockValidator? = null

        fun getInstance(secureStorage: MedresSecureStorage): ClockValidator {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ClockValidator(secureStorage).also { INSTANCE = it }
            }
        }

        @androidx.annotation.VisibleForTesting
        fun resetInstance() {
            INSTANCE = null
        }
    }

    /**
     * Result of clock validation check.
     */
    sealed class TimeResult {
        /** Time is valid, safe to use */
        data class Valid(val time: Long, val isServerSynced: Boolean = false) : TimeResult()

        /** Clock manipulation detected */
        data class ManipulationDetected(
            val reason: String,
            val expectedTime: Long,
            val actualTime: Long,
            val differenceMs: Long
        ) : TimeResult()
    }

    /**
     * Get the current validated time.
     *
     * This method validates the device clock against stored anchor points
     * and returns either a valid time or a manipulation detection result.
     *
     * @return TimeResult with either valid time or manipulation detection
     */
    fun getCurrentTime(): TimeResult {
        val currentWallTime = System.currentTimeMillis()
        val currentElapsed = SystemClock.elapsedRealtime()

        // Get stored anchor points
        val lastValidWallTime = secureStorage.lastValidWallTime
        val lastElapsedRealtime = secureStorage.lastElapsedRealtime

        // First run - no anchor points yet
        // Allow operation but mark as not server-validated
        // Anchor points will be established during login with server time
        if (lastValidWallTime == null || lastElapsedRealtime == null) {
            Log.d(TAG, "First run - no anchor points yet, will be established during login")
            // Initialize with current time to allow operation until server sync
            initializeAnchorPoints(currentWallTime, currentElapsed)
            return TimeResult.Valid(currentWallTime, isServerSynced = false)
        }

        // Calculate expected wall time based on elapsed time
        val elapsedDelta = currentElapsed - lastElapsedRealtime
        val expectedWallTime = lastValidWallTime + elapsedDelta

        // Check for time jump (backward or forward)
        val timeDifference = currentWallTime - expectedWallTime
        val absDifference = kotlin.math.abs(timeDifference)

        if (absDifference > MedresConstants.CLOCK_MANIPULATION_THRESHOLD_MS) {
            val reason = if (timeDifference < 0) {
                "Time went backward by ${formatDuration(absDifference)}"
            } else {
                "Time jumped forward by ${formatDuration(absDifference)}"
            }

            Log.w(TAG, "Clock manipulation detected: $reason")
            Log.w(TAG, "Expected: $expectedWallTime, Actual: $currentWallTime, Diff: ${timeDifference}ms")

            // Mark manipulation detected
            secureStorage.clockManipulationDetected = true

            return TimeResult.ManipulationDetected(
                reason = reason,
                expectedTime = expectedWallTime,
                actualTime = currentWallTime,
                differenceMs = timeDifference
            )
        }

        // Time is valid - update anchor points to keep them fresh
        updateAnchorPoints(currentWallTime, currentElapsed)

        // Apply server time offset if available
        val serverOffset = secureStorage.serverTimeOffsetMs
        val finalTime = if (serverOffset != 0L) {
            currentWallTime + serverOffset
        } else {
            currentWallTime
        }

        return TimeResult.Valid(finalTime, isServerSynced = serverOffset != 0L)
    }

    /**
     * Sync clock with server time and initialize/validate anchor points.
     *
     * Call this when receiving a timestamp from the server (e.g., token expiry time).
     * This validates local clock against server time and establishes trust.
     *
     * IMPORTANT: This is the PRIMARY way to establish clock trust.
     * Server time is considered the source of truth.
     *
     * @param serverTime Current server time in milliseconds (or token expiry time from server)
     * @param localTime Local time when server response was received (defaults to now)
     * @param forceSync If true, bypass validation and trust server time completely
     */
    fun syncWithServerTime(serverTime: Long, localTime: Long = System.currentTimeMillis(), forceSync: Boolean = false) {
        val offset = serverTime - localTime
        val currentElapsed = SystemClock.elapsedRealtime()

        // Check if this is first sync (no anchor points)
        val isFirstSync = secureStorage.lastValidWallTime == null || secureStorage.lastElapsedRealtime == null

        if (forceSync || isFirstSync) {
            // First sync or forced sync - trust server completely
            secureStorage.serverTimeOffsetMs = offset
            // Initialize anchor points with server-corrected time
            initializeAnchorPoints(serverTime, currentElapsed)
            Log.d(TAG, "Initial server sync - Anchor points established with server time")
            Log.d(TAG, "Server time: $serverTime, Local time: $localTime, Offset: ${offset}ms")
        } else {
            // Subsequent sync - only accept if offset is reasonable
            val maxOffset = 5 * 60 * 1000L // 5 minutes
            if (kotlin.math.abs(offset) <= maxOffset) {
                secureStorage.serverTimeOffsetMs = offset
                Log.d(TAG, "Server sync - Offset: ${offset}ms (${offset / 1000}s)")
            } else {
                // Server and local clocks differ significantly - potential manipulation
                // Use server time to reset anchor points
                Log.w(TAG, "Large clock offset detected: ${offset}ms - Correcting using server time")
                secureStorage.serverTimeOffsetMs = offset
                initializeAnchorPoints(serverTime, currentElapsed)
            }
        }
    }

    /**
     * Reset the manipulation flag.
     *
     * Call this after user has corrected their device time.
     */
    fun resetManipulationFlag() {
        secureStorage.clockManipulationDetected = false
        Log.d(TAG, "Manipulation flag reset")
    }

    /**
     * Check if clock manipulation has been detected.
     */
    fun isManipulationDetected(): Boolean {
        return secureStorage.clockManipulationDetected
    }

    /**
     * Get the current time without validation.
     *
     * WARNING: This returns System.currentTimeMillis() directly without validation.
     * Only use this for non-security-critical operations.
     */
    fun getUnsafeCurrentTime(): Long = System.currentTimeMillis()

    /**
     * Clear all clock validation data.
     *
     * Call this on logout or when switching users.
     */
    fun clear() {
        secureStorage.lastValidWallTime = null
        secureStorage.lastElapsedRealtime = null
        secureStorage.serverTimeOffsetMs = 0L
        secureStorage.clockManipulationDetected = false
        Log.d(TAG, "Clock validation data cleared")
    }

    // ===== Private Methods =====

    private fun initializeAnchorPoints(wallTime: Long, elapsedRealtime: Long) {
        secureStorage.lastValidWallTime = wallTime
        secureStorage.lastElapsedRealtime = elapsedRealtime
        Log.d(TAG, "Initialized anchor points: wall=$wallTime, elapsed=$elapsedRealtime")
    }

    private fun updateAnchorPoints(wallTime: Long, elapsedRealtime: Long) {
        // Update anchor points to keep them current
        // We update periodically (not on every check) to avoid excessive writes
        val currentElapsed = secureStorage.lastElapsedRealtime ?: return

        // Update every hour of elapsed time
        if (elapsedRealtime - currentElapsed > 60 * 60 * 1000L) {
            secureStorage.lastValidWallTime = wallTime
            secureStorage.lastElapsedRealtime = elapsedRealtime
            Log.d(TAG, "Updated anchor points: wall=$wallTime, elapsed=$elapsedRealtime")
        }
    }

    private fun formatDuration(ms: Long): String {
        val seconds = ms / 1000
        val minutes = seconds / 60
        val hours = minutes / 60

        return when {
            hours > 0 -> "${hours}h ${minutes % 60}m"
            minutes > 0 -> "${minutes}m ${seconds % 60}s"
            else -> "${seconds}s"
        }
    }
}
