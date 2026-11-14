package net.fuzzyblocks.fuzzypermissions.api.elevation

import java.util.UUID

/**
 * The public interface exposed to the TOTP plugin (or other authorized callers)
 * via Paper's ServicesManager.
 */
interface ElevationApi {
    
    /**
     * Attempts to elevate a player to a specific role based on a cryptographically signed JWT.
     * 
     * @param playerUuid The player to elevate.
     * @param roleName The target role.
     * @param jwtToken The signed token proving authorization.
     * @return An [ElevationResult] indicating the outcome.
     */
    fun elevatePlayer(playerUuid: UUID, roleName: String, jwtToken: String): ElevationResult
}

/**
 * Sealed hierarchy representing all possible outcomes of an elevation attempt.
 */
sealed interface ElevationResult {
    data object Success : ElevationResult
    data object InvalidSignature : ElevationResult
    data object RoleNotElevatable : ElevationResult
    data class OnCooldown(val remainingSeconds: Long) : ElevationResult
    data class Error(val message: String) : ElevationResult
}