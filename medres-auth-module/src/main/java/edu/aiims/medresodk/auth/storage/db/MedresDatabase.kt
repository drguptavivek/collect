package edu.aiims.medresodk.auth.storage.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [TelemetryEntity::class], version = 1, exportSchema = false)
abstract class MedresDatabase : RoomDatabase() {
    abstract fun telemetryDao(): TelemetryDao
}
