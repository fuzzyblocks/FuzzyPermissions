package net.fuzzyblocks.fuzzypermissions.core.database.repository

import net.fuzzyblocks.fuzzypermissions.api.model.RoleSnapshot
import java.time.Instant
import java.util.UUID

data class GroupSnapshot(
    val id: UUID,
    val name: String,
    val permissions: Set<String>
)

/** A persistent role assignment. [world] == null means global scope. */
data class PlayerRoleAssignment(
    val id: Long,
    val playerUuid: UUID,
    val roleId: UUID,
    val world: String?
)

data class AuditLogEntry(
    val timestamp: Instant,
    val playerUuid: UUID,
    val action: String,
    val roleId: UUID?,
    val roleName: String?,
    val triggerSource: String
)

interface PermissionRepository {

    /**
     * All roles for the cache. Each [RoleSnapshot.permissions] is already flattened:
     * the role's own nodes plus the nodes of every group attached to it.
     */
    suspend fun loadRoles(): List<RoleSnapshot>

    suspend fun loadGroups(): List<GroupSnapshot>

    suspend fun getPlayerAssignments(playerUuid: UUID): List<PlayerRoleAssignment>

    /** @return true if a new assignment was created, false if it already existed. */
    suspend fun assignRole(playerUuid: UUID, roleId: UUID, world: String?): Boolean

    /** @return true if an assignment was removed. */
    suspend fun removeRole(playerUuid: UUID, roleId: UUID, world: String?): Boolean

    suspend fun getCooldownExpiry(playerUuid: UUID): Instant?
    suspend fun setCooldown(playerUuid: UUID, expiresAt: Instant)
    suspend fun clearCooldown(playerUuid: UUID)
    suspend fun purgeExpiredCooldowns(now: Instant): Int

    suspend fun appendAuditLog(entry: AuditLogEntry)
}