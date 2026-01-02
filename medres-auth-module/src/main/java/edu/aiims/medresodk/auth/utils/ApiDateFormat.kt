package edu.aiims.medresodk.auth.utils

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * API date format constants and utilities.
 */
object ApiDateFormat {
    const val API_DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"

    val dateFormat: SimpleDateFormat = SimpleDateFormat(API_DATE_FORMAT, Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    fun parse(timestamp: String): Date? {
        return try {
            dateFormat.parse(timestamp)
        } catch (e: Exception) {
            null
        }
    }

    fun format(date: Date): String {
        return dateFormat.format(date)
    }
}
