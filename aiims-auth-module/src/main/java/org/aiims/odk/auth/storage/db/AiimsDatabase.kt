package org.aiims.odk.auth.storage.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [TelemetryEntity::class], version = 1, exportSchema = false)
abstract class AiimsDatabase : RoomDatabase() {
    abstract fun telemetryDao(): TelemetryDao
}
