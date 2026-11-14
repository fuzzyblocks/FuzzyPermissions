package net.fuzzyblocks.fuzzypermissions.api.model

import java.util.UUID

/**
 * An immutable view of a role's definition, loaded from the database.
 */
data class RoleSnapshot(
    val id: UUID,
    val name: String,
    val weight: Int,
    val permissions: Set<String>,
    val elevatableDurationSeconds: Int?
)