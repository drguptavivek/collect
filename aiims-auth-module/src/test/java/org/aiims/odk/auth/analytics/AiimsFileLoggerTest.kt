package org.aiims.odk.auth.analytics

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.equalTo
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class AiimsFileLoggerTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        AiimsFileLogger.resetForTest()
    }

    @Test
    fun `log writes to today's file`() = runTest {
        // Ensure clean state for logs
        val logDir = AiimsFileLogger.getLogDir(context)
        if (logDir.exists()) {
            logDir.deleteRecursively()
        }

        AiimsFileLogger.init(context)
        AiimsFileLogger.log("INFO", "TEST", "Test log entry")

        // Wait for file to appear
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val logFile = File(logDir, "aiims_log_$today.txt")

        var attempts = 0
        while (!logFile.exists() && attempts < 20) {
            Thread.sleep(100)
            attempts++
        }
        
        if (logFile.exists()) {
             // Wait for content flush
             Thread.sleep(500)
        }

        assertThat(logFile.exists(), equalTo(true))
        val content = logFile.readText()
        assertThat(content, containsString("Test log entry"))
    }
}
