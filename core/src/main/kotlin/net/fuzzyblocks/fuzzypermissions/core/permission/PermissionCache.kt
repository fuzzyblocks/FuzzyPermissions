package net.fuzzyblocks.fuzzypermissions.core.permission

import net.fuzzyblocks.fuzzypermissions.api.model.PlayerPermissionSnapshot
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class PermissionCache {
    private val snapshots = ConcurrentHashMap<UUID, PlayerPermissionSnapshot>()

    fun getSnapshot(uuid: UUID): PlayerPermissionSnapshot? = snapshots[uuid]

    fun updateSnapshot(uuid: UUID, snapshot: PlayerPermissionSnapshot) {
        snapshots[uuid] = snapshot
    }

    fun remove(uuid: UUID) {
        snapshots.remove(uuid)
    }

    fun clear() {
        snapshots.clear()
    }
}