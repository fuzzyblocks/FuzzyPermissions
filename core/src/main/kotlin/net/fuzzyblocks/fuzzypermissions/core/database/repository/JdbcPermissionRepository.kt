package net.fuzzyblocks.fuzzypermissions.core.database.repository

import kotlinx.coroutines.withContext
import net.fuzzyblocks.fuzzypermissions.api.model.RoleSnapshot
import net.fuzzyblocks.fuzzypermissions.core.database.DatabaseDispatcher
import java.sql.Connection
import java.sql.ResultSet
import java.time.Instant
import java.util.UUID
import javax.sql.DataSource

class JdbcPermissionRepository(
    private val dataSource: DataSource,
    private val dialect: SqlDialect
) : PermissionRepository {

    companion object {
        /** Detects the dialect off the main thread, then builds the repository. */
        suspend fun create(dataSource: DataSource): JdbcPermissionRepository {
            val dialect = withContext(DatabaseDispatcher) {
                dataSource.connection.use { SqlDialect.fromProductName(it.metaData.databaseProductName) }
            }
            return JdbcPermissionRepository(dataSource, dialect)
        }
    }

    private suspend fun <T> withConnection(block: (Connection) -> T): T =
        withContext(DatabaseDispatcher) { dataSource.connection.use(block) }

    private suspend fun <T> transaction(block: (Connection) -> T): T = withConnection { conn ->
        conn.autoCommit = false
        try {
            block(conn).also { conn.commit() }
        } catch (t: Throwable) {
            runCatching { conn.rollback() }
            throw t
        } finally {
            conn.autoCommit = true
        }
    }

    private fun <V> Connection.loadGrouped(sql: String, value: (ResultSet) -> V): Map<UUID, List<V>> =
        createStatement().use { st ->
            st.executeQuery(sql).use { rs ->
                val result = HashMap<UUID, MutableList<V>>()
                while (rs.next()) {
                    result.getOrPut(rs.getUuid(1)) { mutableListOf() }.add(value(rs))
                }
                result
            }
        }

    private fun worldPredicate(world: String?) = if (world == null) "world IS NULL" else "world = ?"

    override suspend fun loadRoles(): List<RoleSnapshot> = withConnection { conn ->
        val rolePerms = conn.loadGrouped("SELECT role_id, permission FROM role_permissions") { it.getString(2) }
        val groupPerms = conn.loadGrouped("SELECT group_id, permission FROM group_permissions") { it.getString(2) }
        val roleGroups = conn.loadGrouped("SELECT role_id, group_id FROM role_groups") { it.getUuid(2) }

        conn.createStatement().use { st ->
            st.executeQuery("SELECT id, name, weight, elevatable_duration_seconds FROM roles").use { rs ->
                buildList {
                    while (rs.next()) {
                        val id = rs.getUuid("id")
                        val permissions = buildSet {
                            rolePerms[id]?.let(::addAll)
                            roleGroups[id]?.forEach { groupId -> groupPerms[groupId]?.let(::addAll) }
                        }
                        add(
                            RoleSnapshot(
                                id = id,
                                name = rs.getString("name"),
                                weight = rs.getInt("weight"),
                                permissions = permissions,
                                elevatableDurationSeconds = rs.getIntOrNull("elevatable_duration_seconds")
                            )
                        )
                    }
                }
            }
        }
    }

    override suspend fun loadGroups(): List<GroupSnapshot> = withConnection { conn ->
        val groupPerms = conn.loadGrouped("SELECT group_id, permission FROM group_permissions") { it.getString(2) }

        conn.createStatement().use { st ->
            st.executeQuery("""SELECT id, name FROM "groups"""").use { rs ->
                buildList {
                    while (rs.next()) {
                        val id = rs.getUuid("id")
                        add(GroupSnapshot(id, rs.getString("name"), groupPerms[id].orEmpty().toSet()))
                    }
                }
            }
        }
    }

    override suspend fun getPlayerAssignments(playerUuid: UUID): List<PlayerRoleAssignment> =
        withConnection { conn ->
            conn.prepareStatement("SELECT id, role_id, world FROM player_roles WHERE player_uuid = ?").use { ps ->
                ps.setPlayerUuid(1, playerUuid)
                ps.executeQuery().use { rs ->
                    buildList {
                        while (rs.next()) {
                            add(
                                PlayerRoleAssignment(
                                    id = rs.getLong("id"),
                                    playerUuid = playerUuid,
                                    roleId = rs.getUuid("role_id"),
                                    world = rs.getString("world")
                                )
                            )
                        }
                    }
                }
            }
        }

    override suspend fun assignRole(playerUuid: UUID, roleId: UUID, world: String?): Boolean =
        transaction { conn ->
            val exists = conn.prepareStatement(
                "SELECT 1 FROM player_roles WHERE player_uuid = ? AND role_id = ? AND ${worldPredicate(world)}"
            ).use { ps ->
                ps.setPlayerUuid(1, playerUuid)
                ps.setId(2, roleId, dialect)
                world?.let { ps.setString(3, it) }
                ps.executeQuery().use { it.next() }
            }
            if (exists) return@transaction false

            conn.prepareStatement(
                "INSERT INTO player_roles (player_uuid, role_id, world) VALUES (?, ?, ?)"
            ).use { ps ->
                ps.setPlayerUuid(1, playerUuid)
                ps.setId(2, roleId, dialect)
                ps.setString(3, world) // null-safe: binds SQL NULL
                ps.executeUpdate()
            }
            true
        }

    override suspend fun removeRole(playerUuid: UUID, roleId: UUID, world: String?): Boolean =
        withConnection { conn ->
            conn.prepareStatement(
                "DELETE FROM player_roles WHERE player_uuid = ? AND role_id = ? AND ${worldPredicate(world)}"
            ).use { ps ->
                ps.setPlayerUuid(1, playerUuid)
                ps.setId(2, roleId, dialect)
                world?.let { ps.setString(3, it) }
                ps.executeUpdate() > 0
            }
        }

    override suspend fun getCooldownExpiry(playerUuid: UUID): Instant? = withConnection { conn ->
        conn.prepareStatement("SELECT expires_at FROM elevation_cooldowns WHERE player_uuid = ?").use { ps ->
            ps.setPlayerUuid(1, playerUuid)
            ps.executeQuery().use { rs -> if (rs.next()) rs.getInstant("expires_at") else null }
        }
    }

    override suspend fun setCooldown(playerUuid: UUID, expiresAt: Instant) {
        withConnection { conn ->
            conn.prepareStatement(
                """
                INSERT INTO elevation_cooldowns (player_uuid, expires_at) VALUES (?, ?)
                ON CONFLICT (player_uuid) DO UPDATE SET expires_at = excluded.expires_at
                """.trimIndent()
            ).use { ps ->
                ps.setPlayerUuid(1, playerUuid)
                ps.setInstant(2, expiresAt)
                ps.executeUpdate()
            }
        }
    }

    override suspend fun clearCooldown(playerUuid: UUID) {
        withConnection { conn ->
            conn.prepareStatement("DELETE FROM elevation_cooldowns WHERE player_uuid = ?").use { ps ->
                ps.setPlayerUuid(1, playerUuid)
                ps.executeUpdate()
            }
        }
    }

    override suspend fun purgeExpiredCooldowns(now: Instant): Int = withConnection { conn ->
        conn.prepareStatement("DELETE FROM elevation_cooldowns WHERE expires_at < ?").use { ps ->
            ps.setInstant(1, now)
            ps.executeUpdate()
        }
    }

    override suspend fun appendAuditLog(entry: AuditLogEntry) {
        withConnection { conn ->
            conn.prepareStatement(
                """
                INSERT INTO audit_log ("timestamp", player_uuid, action, role_id, role_name, trigger_source)
                VALUES (?, ?, ?, ?, ?, ?)
                """.trimIndent()
            ).use { ps ->
                ps.setInstant(1, entry.timestamp)
                ps.setPlayerUuid(2, entry.playerUuid)
                ps.setString(3, entry.action)
                ps.setNullableId(4, entry.roleId, dialect)
                ps.setString(5, entry.roleName)
                ps.setString(6, entry.triggerSource)
                ps.executeUpdate()
            }
        }
    }
}