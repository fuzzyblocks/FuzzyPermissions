package net.fuzzyblocks.fuzzypermissions.plugin.permissible

import net.fuzzyblocks.fuzzypermissions.core.permission.PermissionCache
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.permissions.PermissibleBase
import java.lang.reflect.Field

class PermissibleInjector private constructor(
    private val cache: PermissionCache,
    private val field: Field
) {
    companion object {
        /** @throws IllegalStateException if this server build doesn't expose the expected field. */
        fun create(cache: PermissionCache): PermissibleInjector {
            // Works with both versioned (v1_20_R3) and un-relocated CraftBukkit packages.
            val pkg = Bukkit.getServer().javaClass.packageName
            val field = try {
                Class.forName("$pkg.entity.CraftHumanEntity").getDeclaredField("perm")
            } catch (e: ReflectiveOperationException) {
                throw IllegalStateException("Cannot locate CraftHumanEntity#perm on this server build", e)
            }
            check(PermissibleBase::class.java.isAssignableFrom(field.type)) {
                "CraftHumanEntity#perm has unexpected type ${field.type.name}"
            }
            field.isAccessible = true
            return PermissibleInjector(cache, field)
        }
    }

    fun inject(player: Player) {
        val current = field.get(player) as PermissibleBase
        if (current is FuzzyPermissible) return
        field.set(player, FuzzyPermissible(player, current, cache))
    }

    /** Restores the vanilla permissible. Do NOT call previous.clearPermissions(): subscriptions are keyed by the player. */
    fun uninject(player: Player) {
        val current = field.get(player)
        if (current is FuzzyPermissible) {
            field.set(player, current.previous)
            current.previous.recalculatePermissions()
        }
    }
}