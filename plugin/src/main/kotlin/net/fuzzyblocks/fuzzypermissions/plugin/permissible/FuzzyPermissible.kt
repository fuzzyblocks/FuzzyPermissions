package net.fuzzyblocks.fuzzypermissions.plugin.permissible

import net.fuzzyblocks.fuzzypermissions.core.permission.PermissionCache
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.permissions.PermissibleBase
import org.bukkit.permissions.Permission
import org.bukkit.permissions.PermissionAttachment
import org.bukkit.permissions.PermissionAttachmentInfo
import org.bukkit.plugin.Plugin
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Replaces CraftHumanEntity's PermissibleBase. Checks hit the in-memory snapshot
 * (ConcurrentHashMap read + HashSet lookup, no locks, no I/O).
 *
 * [previous] is the vanilla instance, kept so it can be restored when the plugin disables.
 */
class FuzzyPermissible(
    private val player: Player,
    val previous: PermissibleBase,
    private val cache: PermissionCache
) : PermissibleBase(player) { // passing the player keeps op handling identical to vanilla

    // NOTE: the super constructor calls recalculatePermissions() before these fields exist.
    // We deliberately don't override it, so that call is safe.
    private val attachments = CopyOnWriteArrayList<PermissionAttachment>()

    // ---- Core resolution -------------------------------------------------------------

    /** true/false when explicitly set by an attachment or granted by the snapshot; null when unset. */
    private fun explicit(name: String): Boolean? {
        var result: Boolean? = null
        for (attachment in attachments) { // later attachments win, like vanilla
            val value = attachment.permissions[name]
            if (value != null) result = value
        }
        if (result != null) return result
        return if (cache.getSnapshot(player.uniqueId)?.effectiveNodes?.contains(name) == true) true else null
    }

    private fun defaultFor(name: String): Boolean {
        val declared = Bukkit.getPluginManager().getPermission(name)
        return (declared?.default ?: Permission.DEFAULT_PERMISSION).getValue(isOp)
    }

    override fun hasPermission(inName: String): Boolean {
        val name = inName.lowercase(Locale.ROOT)
        return explicit(name) ?: defaultFor(name)
    }

    override fun hasPermission(perm: Permission): Boolean {
        val name = perm.name.lowercase(Locale.ROOT)
        return explicit(name) ?: perm.default.getValue(isOp)
    }

    override fun isPermissionSet(name: String): Boolean =
        explicit(name.lowercase(Locale.ROOT)) != null || super.isPermissionSet(name)

    override fun isPermissionSet(perm: Permission): Boolean = isPermissionSet(perm.name)

    override fun getEffectivePermissions(): MutableSet<PermissionAttachmentInfo> {
        val merged = LinkedHashMap<String, PermissionAttachmentInfo>()
        super.getEffectivePermissions().forEach { merged[it.permission] = it } // Bukkit defaults
        cache.getSnapshot(player.uniqueId)?.effectiveNodes?.forEach {
            merged[it] = PermissionAttachmentInfo(this, it, null, true)
        }
        for (attachment in attachments) {
            attachment.permissions.forEach { (node, value) ->
                merged[node] = PermissionAttachmentInfo(this, node, attachment, value)
            }
        }
        return HashSet(merged.values)
    }

    // ---- Attachments (other plugins still use these for temporary grants) -------------

    override fun addAttachment(plugin: Plugin): PermissionAttachment {
        require(plugin.isEnabled) { "Plugin ${plugin.name} is disabled" }
        val attachment = PermissionAttachment(plugin, player)
        attachments += attachment
        recalculatePermissions()
        return attachment
    }

    override fun addAttachment(plugin: Plugin, name: String, value: Boolean): PermissionAttachment =
        addAttachment(plugin).also { it.setPermission(name, value) }

    override fun addAttachment(plugin: Plugin, ticks: Int): PermissionAttachment =
        addAttachment(plugin).also { scheduleRemoval(plugin, it, ticks) }

    override fun addAttachment(plugin: Plugin, name: String, value: Boolean, ticks: Int): PermissionAttachment =
        addAttachment(plugin, name, value).also { scheduleRemoval(plugin, it, ticks) }

    private fun scheduleRemoval(plugin: Plugin, attachment: PermissionAttachment, ticks: Int) {
        Bukkit.getScheduler().runTaskLater(plugin, Runnable { attachment.remove() }, ticks.toLong())
    }

    override fun removeAttachment(attachment: PermissionAttachment) {
        require(attachments.remove(attachment)) { "Given attachment is not part of Permissible object $player" }
        attachment.removalCallback?.attachmentRemoved(attachment)
        recalculatePermissions()
    }
}