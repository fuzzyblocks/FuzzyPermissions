package net.fuzzyblocks.fuzzypermissions.api.model

import java.util.UUID

/**
 * An immutable, pre-calculated snapshot of a player's effective permissions.
 * This is cached in-memory so Vault and the main thread never block on DB reads.
 */
data class PlayerPermissionSnapshot(
    val uuid: UUID,
    
    // Sorted by weight descending (highest weight first)
    val activeRoles: List<String>,
    
    // Pre-calculated for O(1) Vault reads (usually the first item in activeRoles)
    val primaryGroup: String,
    
    // The completely flattened list of exact nodes from roles and groups
    val effectiveNodes: Set<String>,
    
    // Any active elevations, tracked so the UI/Bossbar can display them
    val activeElevations: List<String>
)