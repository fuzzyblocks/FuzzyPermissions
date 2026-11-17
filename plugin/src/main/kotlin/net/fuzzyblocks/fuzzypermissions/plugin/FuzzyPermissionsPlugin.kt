package net.fuzzyblocks.fuzzypermissions.plugin

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import net.fuzzyblocks.fuzzypermissions.core.database.DatabaseCredentials
import net.fuzzyblocks.fuzzypermissions.core.database.DatabaseDispatcher
import net.fuzzyblocks.fuzzypermissions.core.database.DatabaseManager
import net.fuzzyblocks.fuzzypermissions.core.database.repository.JdbcPermissionRepository
import net.fuzzyblocks.fuzzypermissions.core.database.repository.PermissionRepository
import net.fuzzyblocks.fuzzypermissions.core.permission.PermissionCache
import net.fuzzyblocks.fuzzypermissions.core.permission.PlayerPermissionService
import net.fuzzyblocks.fuzzypermissions.core.permission.SnapshotEngine
import net.fuzzyblocks.fuzzypermissions.plugin.listener.PlayerConnectionListener
import org.bukkit.plugin.java.JavaPlugin
import java.util.logging.Level

class FuzzyPermissionsPlugin : JavaPlugin() {

    val scope = CoroutineScope(SupervisorJob() + DatabaseDispatcher)
    val cache = PermissionCache()

    private lateinit var database: DatabaseManager
    lateinit var repository: PermissionRepository
        private set
    lateinit var engine: SnapshotEngine
        private set

    /** Completes once the DB is migrated and roles are loaded. Listeners await this. */
    private val ready = CompletableDeferred<PlayerPermissionService>()
    suspend fun awaitService(): PlayerPermissionService = ready.await()
    
    @Volatile
    var permissionService: PlayerPermissionService? = null
        private set

    override fun onEnable() {
        saveDefaultConfig()

        val credentials = DatabaseCredentials(
            type = config.getString("database.type", "sqlite")!!,
            host = config.getString("database.host", "localhost")!!,
            port = config.getInt("database.port", 5432),
            database = config.getString("database.name", "fuzzypermissions")!!,
            username = config.getString("database.username", "")!!,
            password = config.getString("database.password", "")!!
        )
        val connectionListener = PlayerConnectionListener(this)
        server.pluginManager.registerEvents(connectionListener, this)
        database = DatabaseManager(dataFolder, credentials)

        scope.launch {
            try {
                database.connect()
                repository = JdbcPermissionRepository(database.dataSource, database.dialect)
                engine = SnapshotEngine(repository)
                engine.reloadGlobalDefinitions()
                
                val service = PlayerPermissionService(repository, engine, cache)
                permissionService = service
                ready.complete(service)
                logger.info("Database ready (${database.dialect}).")
                
                server.scheduler.runTask(this@FuzzyPermissionsPlugin, Runnable {
                    server.onlinePlayers.forEach(connectionListener::loadAsync)
                })
            } catch (t: Throwable) {
                ready.completeExceptionally(t)
                logger.log(Level.SEVERE, "Failed to initialise database; disabling plugin.", t)
                server.scheduler.runTask(this@FuzzyPermissionsPlugin, Runnable {
                    server.pluginManager.disablePlugin(this@FuzzyPermissionsPlugin)
                })
            }
        }

        // Next: register listeners and commands (Lifecycle API) here.
    }

    override fun onDisable() {
        scope.cancel()
        if (::database.isInitialized) database.close()
        DatabaseDispatcher.close()
    }
}