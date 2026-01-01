package org.aiims.odk.auth.work

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import org.aiims.odk.auth.injection.AiimsAuthDependencyComponentProvider

class GracePeriodNotificationWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val remainingText = inputData.getString(KEY_REMAINING_TIME) ?: "soon"
        Log.d("GracePeriodNotification", "Showing grace period notification: $remainingText remaining")
        
        return try {
            val authManager = (applicationContext as AiimsAuthDependencyComponentProvider).aiimsAuthDependencyComponent.authManager
            authManager.showGracePeriodNotification(remainingText)
            Result.success()
        } catch (e: Exception) {
            Log.e("GracePeriodNotification", "Failed to show notification", e)
            Result.failure()
        }
    }

    companion object {
        const val KEY_REMAINING_TIME = "remaining_time"
    }
}
