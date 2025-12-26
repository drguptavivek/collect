package org.aiims.odk.auth.work

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import org.aiims.odk.auth.injection.AiimsAuthDependencyComponentProvider
import org.aiims.odk.auth.managers.AiimsAuthManager

class TelemetryWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        Log.d("TelemetryWorker", "Sending periodic telemetry...")
        return try {
            // Note: passing null for location.
            // The Manager handles this by sending "unknown" location.
            // This is primarily for presence/heartbeat.
            val authManager = (applicationContext as AiimsAuthDependencyComponentProvider).aiimsAuthDependencyComponent.authManager
            authManager.submitTelemetry(null)
            Result.success()
        } catch (e: Exception) {
            Log.e("TelemetryWorker", "Failed to send telemetry", e)
            Result.failure()
        }
    }
}
