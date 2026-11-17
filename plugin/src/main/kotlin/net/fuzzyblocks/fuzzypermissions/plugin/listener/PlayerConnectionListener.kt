package net.fuzzyblocks.fuzzypermissions.plugin.listener

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import net.fuzzyblocks.fuzzypermissions.plugin.FuzzyPermissionsPlugin
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.AsyncPlayerPreLoginEvent
import org.bukkit.event.player.PlayerChangedWorldEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerLoginEvent
import org.bukkit.event.player.PlayerQuitEvent
import java.util.UUID
import java.util.logging.Level
import kotlin.time.Duration.Companion.seconds

class PlayerConnectionListener(private val plugin: FuzzyPermissionsPlugin) : Listener {

    private companion object {
        val LOAD_TIMEOUT = 10.seconds
        val UNAVAILABLE: Component =
            Component.text("Permissions are unavailable right now. Please try again shortly.", NamedTextColor.RED)
    }

    // ---- Phase 1: load from the DB on the async login thread ----

    @EventHandler(priority = EventPriority.LOWEST)
    fun onPreLogin(event: AsyncPlayerPreLoginEvent) {
        try {
            // This handler runs on a login thread, never the main thread, so blocking here is safe.
            runBlocking {
                val service = withTimeout(LOAD_TIMEOUT) { plugin.awaitService() }
                withTimeout(LOAD_TIMEOUT) { service.load(event.uniqueId) }
            }
        } catch (t: Throwable) {
            plugin.logger.log(Level.SEVERE, "Failed to load permissions for ${event.name}", t)
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, UNAVAILABLE)
        }
    }

    // ---- Cleanup when another plugin (or vanilla) denies the login after we loaded ----

    @EventHandler(priority = EventPriority.MONITOR)
    fun onPreLoginDenied(event: AsyncPlayerPreLoginEvent) {
        if (event.loginResult != AsyncPlayerPreLoginEvent.Result.ALLOWED) unloadIfOffline(event.uniqueId)
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onLoginDenied(event: PlayerLoginEvent) {
        if (event.result != PlayerLoginEvent.Result.ALLOWED) unloadIfOffline(event.player.uniqueId)
    }

    /** Guard: a denied duplicate login must not wipe the data of the session that is already online. */
    private fun unloadIfOffline(uuid: UUID) {
        if (plugin.server.getPlayer(uuid) == null) plugin.permissionService?.unload(uuid)
    }

    // ---- Phase 2: apply the real world on the main thread (no I/O) ----

    @EventHandler(priority = EventPriority.LOWEST)
    fun onJoin(event: PlayerJoinEvent) {
        val player = event.player
        val service = plugin.permissionService ?: return loadAsync(player)
        if (!service.rebuild(player.uniqueId, player.world.name)) loadAsync(player)
    }

    @EventHandler
    fun onWorldChange(event: PlayerChangedWorldEvent) {
        plugin.permissionService?.rebuild(event.player.uniqueId, event.player.world.name)
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onQuit(event: PlayerQuitEvent) {
        plugin.permissionService?.unload(event.player.uniqueId)
    }

    // ---- Fallback for players who never went through pre-login (late enable, plugin reload) ----

    fun loadAsync(player: Player) {
        val uuid = player.uniqueId
        plugin.scope.launch {
            try {
                val service = plugin.awaitService()
                service.load(uuid)
                plugin.server.scheduler.runTask(plugin, Runnable {
                    if (player.isOnline) service.rebuild(uuid, player.world.name) else service.unload(uuid)
                })
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                plugin.logger.log(Level.SEVERE, "Failed to load permissions for ${player.name}", t)
                plugin.server.scheduler.runTask(plugin, Runnable {
                    if (player.isOnline) player.kick(UNAVAILABLE)
                })
            }
        }
    }
}