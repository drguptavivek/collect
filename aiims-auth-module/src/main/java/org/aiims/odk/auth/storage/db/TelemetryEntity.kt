package org.aiims.odk.auth.storage.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "telemetry_requests")
data class TelemetryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "data")
    val data: String, // Serialized JSON of TelemetryRequest

    @ColumnInfo(name = "project_id")
    val projectId: String,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "attempt_count")
    val attemptCount: Int = 0
)
