package edu.aiims.medresodk.auth.work

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import edu.aiims.medresodk.auth.injection.MedresAuthDependencyComponentProvider
import edu.aiims.medresodk.auth.managers.MedresAuthManager

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
            val authManager = (applicationContext as MedresAuthDependencyComponentProvider).medresAuthDependencyComponent.authManager
            authManager.submitTelemetry(null)
            authManager.flushOfflineQueue()
            Result.success()
        } catch (e: Exception) {
            Log.e("TelemetryWorker", "Failed to send telemetry", e)
            Result.failure()
        }
    }
}
