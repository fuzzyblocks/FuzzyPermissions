package net.fuzzyblocks.fuzzypermissions.core.permission

import net.fuzzyblocks.fuzzypermissions.core.database.repository.PermissionRepository
import net.fuzzyblocks.fuzzypermissions.core.database.repository.PlayerRoleAssignment
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class PlayerPermissionService(
    private val repository: PermissionRepository,
    private val engine: SnapshotEngine,
    private val cache: PermissionCache
) {
    companion object {
        /** Matches no world-scoped assignment, so only global assignments apply. */
        const val NO_WORLD = ""
    }

    private val assignments = ConcurrentHashMap<UUID, List<PlayerRoleAssignment>>()

    /** Loads assignments from the DB (suspending) and publishes a provisional, global-only snapshot. */
    suspend fun load(uuid: UUID) {
        assignments[uuid] = repository.getPlayerAssignments(uuid)
        rebuild(uuid, NO_WORLD)
    }

    /** Pure in-memory recompile. @return false if the player has no loaded assignments. */
    fun rebuild(uuid: UUID, world: String): Boolean {
        val loaded = assignments[uuid] ?: return false
        cache.updateSnapshot(uuid, engine.buildSnapshot(uuid, loaded, emptyList(), world))
        return true
    }

    /** Re-applies freshly reloaded role definitions to every loaded player (e.g. after `/role reload`). */
    fun refreshAll(worldOf: (UUID) -> String) {
        assignments.keys.forEach { rebuild(it, worldOf(it)) }
    }

    fun unload(uuid: UUID) {
        assignments.remove(uuid)
        cache.remove(uuid)
    }
}