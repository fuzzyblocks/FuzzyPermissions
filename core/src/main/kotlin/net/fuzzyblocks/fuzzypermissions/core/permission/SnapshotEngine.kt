package net.fuzzyblocks.fuzzypermissions.core.permission

import net.fuzzyblocks.fuzzypermissions.api.model.PlayerPermissionSnapshot
import net.fuzzyblocks.fuzzypermissions.api.model.RoleSnapshot
import net.fuzzyblocks.fuzzypermissions.core.database.repository.PermissionRepository
import net.fuzzyblocks.fuzzypermissions.core.database.repository.PlayerRoleAssignment
import java.util.Locale
import java.util.UUID

class SnapshotEngine(
    private val repository: PermissionRepository
) {
    // Replaces AtomicReference. Volatile guarantees immediate cross-thread visibility 
    // when we atomically swap this map on /role reload.
    @Volatile
    private var cachedRoles: Map<UUID, RoleSnapshot> = emptyMap()

    /**
     * Called on server start and when an admin runs `/role reload`.
     */
    suspend fun reloadGlobalDefinitions() {
        // Build the new map, then swap the reference atomically
        cachedRoles = repository.loadRoles()
            .map { role -> role.copy(permissions = role.permissions.mapTo(HashSet()) { it.lowercase(Locale.ROOT)}) }
            .associateBy { it.id }
    }

    fun getRoleByName(name: String): RoleSnapshot? =
        cachedRoles.values.find { it.name.equals(name, ignoreCase = true) }

    fun getRoleById(id: UUID): RoleSnapshot? =
        cachedRoles[id]

    /**
     * Compiles a player's assignments and active elevations into an immutable snapshot.
     */
    fun buildSnapshot(
        playerUuid: UUID,
        assignments: List<PlayerRoleAssignment>,
        activeElevations: List<UUID> = emptyList(),
        currentWorld: String
    ): PlayerPermissionSnapshot {
        // Capture the volatile reference locally so it can't change mid-calculation
        val rolesMap = cachedRoles

        // 1. Filter assignments that apply to this world (global or matching world)
        val applicableAssignmentIds = assignments
            .filter { it.world == null || it.world.equals(currentWorld, ignoreCase = true) }
            .map { it.roleId }

        // 2. Combine base assignments and active elevations
        val allActiveRoleIds = applicableAssignmentIds + activeElevations
        val activeRoles = allActiveRoleIds.mapNotNull { rolesMap[it] }
            .sortedByDescending { it.weight } // Highest weight first

        // 3. Determine the primary group for Vault (defaults to "default" if no roles)
        val primaryGroup = activeRoles.firstOrNull()?.name ?: "default"

        // 4. Flatten the effective permission nodes
        val effectiveNodes = buildSet {
            activeRoles.forEach { role ->
                addAll(role.permissions)
            }
        }

        // 5. Extract just the names of the active elevations for UI/BossBar tracking
        val elevationNames = activeElevations.mapNotNull { rolesMap[it]?.name }

        return PlayerPermissionSnapshot(
            uuid = playerUuid,
            activeRoles = activeRoles.map { it.name },
            primaryGroup = primaryGroup,
            effectiveNodes = effectiveNodes,
            activeElevations = elevationNames
        )
    }
}