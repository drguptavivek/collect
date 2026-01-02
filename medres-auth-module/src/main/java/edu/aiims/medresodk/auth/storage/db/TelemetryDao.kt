package edu.aiims.medresodk.auth.storage.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface TelemetryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: TelemetryEntity): Long

    @Query("SELECT * FROM telemetry_requests ORDER BY created_at ASC")
    suspend fun getAll(): List<TelemetryEntity>

    @Query("DELETE FROM telemetry_requests WHERE id = :id")
    suspend fun delete(id: Long)
    
    @Query("SELECT COUNT(*) FROM telemetry_requests")
    suspend fun getCount(): Int
}
