package org.aiims.odk.auth.analytics

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Singleton logger that writes logs to files in the app's internal storage.
 * - Writes to separate files per day.
 * - Rotates logs (deletes files older than 24 hours).
 * - Uses a Channel to serialize writes on a background thread.
 */
object AiimsFileLogger {

    private const val TAG = "AiimsFileLogger"
    private const val LOG_DIR_NAME = "aiims_logs"
    private const val LOG_FILE_PREFIX = "aiims_log_"
    private const val LOG_FILE_EXT = ".txt"
    private const val RETENTION_HOURS = 24L

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val logChannel = Channel<String>(Channel.UNLIMITED)
    
    private var logDir: File? = null
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    // Used to track the current date to switch files if day changes
    private var currentDateStr: String = ""

    fun init(context: Context) {
        scope.launch {
            try {
                logDir = File(context.filesDir, LOG_DIR_NAME)
                if (logDir?.exists() == false) {
                    logDir?.mkdirs()
                }
                
                // Initialize date string
                currentDateStr = dateFormat.format(Date())

                // Perform initial cleanup
                cleanOldLogs()

                // Start processing the channel
                processLogQueue()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize logger", e)
            }
        }
    }

    fun log(level: String, tag: String, message: String) {
        val timestamp = timeFormat.format(Date())
        val logEntry = "$timestamp [$level] $tag: $message\n"
        logChannel.trySend(logEntry)
    }

    private suspend fun processLogQueue() {
        for (entry in logChannel) {
            writeLog(entry)
        }
    }

    private fun writeLog(entry: String) {
        if (logDir == null) return

        try {
            val now = Date()
            val dateStr = dateFormat.format(now)
            
            // Check if day changed, if so, maybe trigger cleanup (optional, but good practice)
            if (dateStr != currentDateStr) {
                currentDateStr = dateStr
                cleanOldLogs()
            }

            val fileName = "$LOG_FILE_PREFIX$dateStr$LOG_FILE_EXT"
            val file = File(logDir, fileName)

            // Append to file
            FileWriter(file, true).use { writer ->
                writer.write(entry)
            }

        } catch (e: IOException) {
            Log.e(TAG, "Failed to write log", e)
        }
    }

    private fun cleanOldLogs() {
        if (logDir == null || logDir?.exists() == false) return

        val now = System.currentTimeMillis()
        val retentionMillis = TimeUnit.HOURS.toMillis(RETENTION_HOURS)

        logDir?.listFiles()?.forEach { file ->
            if (file.name.startsWith(LOG_FILE_PREFIX) && file.name.endsWith(LOG_FILE_EXT)) {
                // Check if file is older than retention period
                // We rely on lastModified() which is updated on write. 
                // Since we write daily files, a file unmodified for > 24h is definitely old.
                if (now - file.lastModified() > retentionMillis) {
                    try {
                        file.delete()
                        Log.i(TAG, "Deleted old log file: ${file.name}")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to delete old log file: ${file.name}", e)
                    }
                }
            }
        }
    }
    
    fun getLogDir(context: Context): File {
        return File(context.filesDir, LOG_DIR_NAME)
    }
}
