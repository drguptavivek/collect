package org.aiims.odk.auth.fakes

import org.aiims.odk.auth.storage.db.TelemetryDao
import org.aiims.odk.auth.storage.db.TelemetryEntity

class FakeTelemetryDao : TelemetryDao {
    private val entities = mutableListOf<TelemetryEntity>()
    private var nextId = 1L

    override suspend fun insert(entity: TelemetryEntity): Long {
        val id = if (entity.id == 0L) nextId++ else entity.id
        val newEntity = entity.copy(id = id)
        entities.add(newEntity)
        return id
    }

    override suspend fun getAll(): List<TelemetryEntity> {
        return entities.sortedBy { it.createdAt }
    }

    override suspend fun delete(id: Long) {
        entities.removeIf { it.id == id }
    }

    override suspend fun getCount(): Int {
        return entities.size
    }
}
