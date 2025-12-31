package org.aiims.odk.auth.utils

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.AesKeyStrength
import net.lingala.zip4j.model.enums.EncryptionMethod
import org.aiims.odk.auth.analytics.AiimsFileLogger
import org.odk.collect.settings.SettingsProvider
import org.odk.collect.settings.keys.ProtectedProjectKeys
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object LogExporter {

    private const val DEFAULT_PASSWORD = "aiims"

    suspend fun exportLogs(context: Context, settingsProvider: SettingsProvider): File? {
        return withContext(Dispatchers.IO) {
            try {
                // 1. Get Log Directory
                val logDir = AiimsFileLogger.getLogDir(context)
                if (!logDir.exists() || logDir.listFiles()?.isEmpty() == true) {
                    return@withContext null
                }

                // 2. Filter for today's logs (or all recent logs in the dir)
                // We will zip all logs currently in the directory (AiimsFileLogger handles retention)
                val logFiles = logDir.listFiles()?.filter { 
                    it.isFile && it.name.endsWith(".txt") 
                } ?: return@withContext null

                if (logFiles.isEmpty()) return@withContext null

                // 3. Prepare Zip File
                val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                val zipFileName = "AiimsLogs_$timeStamp.zip"
                val zipFileObj = File(context.cacheDir, zipFileName)
                
                // Delete if exists
                if (zipFileObj.exists()) zipFileObj.delete()

                // 4. Get Admin Password for Encryption
                val adminPassword = settingsProvider.getProtectedSettings().getString(ProtectedProjectKeys.KEY_ADMIN_PW)
                val passwordToUse = if (!adminPassword.isNullOrEmpty()) {
                    adminPassword!!.toCharArray()
                } else {
                    DEFAULT_PASSWORD.toCharArray()
                }

                // 5. Create Zip with Encryption
                val zipFile = ZipFile(zipFileObj, passwordToUse)
                val zipParameters = ZipParameters().apply {
                    isEncryptFiles = true
                    encryptionMethod = EncryptionMethod.AES
                    aesKeyStrength = AesKeyStrength.KEY_STRENGTH_256
                }

                zipFile.addFiles(logFiles, zipParameters)

                return@withContext zipFileObj
            } catch (e: Exception) {
                e.printStackTrace()
                return@withContext null
            }
        }
    }

    suspend fun saveToDownloads(context: Context, zipFile: File): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val resolver = context.contentResolver
                val contentValues = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, zipFile.name)
                    put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "application/zip")
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, "Download/AIIMS_Logs")
                        put(android.provider.MediaStore.MediaColumns.IS_PENDING, 1)
                    }
                }

                val collection = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI
                } else {
                    // Fallback for older versions if needed, or use MediaStore.Files
                    android.provider.MediaStore.Files.getContentUri("external")
                }

                val uri = resolver.insert(collection, contentValues) ?: return@withContext false

                resolver.openOutputStream(uri)?.use { outputStream ->
                    zipFile.inputStream().use { inputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }

                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    contentValues.clear()
                    contentValues.put(android.provider.MediaStore.MediaColumns.IS_PENDING, 0)
                    resolver.update(uri, contentValues, null, null)
                }

                true
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }
}
