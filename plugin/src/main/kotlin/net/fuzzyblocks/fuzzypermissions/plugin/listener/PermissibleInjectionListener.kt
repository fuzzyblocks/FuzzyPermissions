package net.fuzzyblocks.fuzzypermissions.plugin.listener

import net.fuzzyblocks.fuzzypermissions.plugin.permissible.PermissibleInjector
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerLoginEvent

class PermissibleInjectionListener(private val injector: PermissibleInjector) : Listener {

    // LOWEST: before any other plugin can run a permission check during login.
    @EventHandler(priority = EventPriority.LOWEST)
    fun onLogin(event: PlayerLoginEvent) = injector.inject(event.player)

    // No quit handler needed: the CraftPlayer is discarded on disconnect, and the
    // snapshot is already removed by PlayerConnectionListener.
}