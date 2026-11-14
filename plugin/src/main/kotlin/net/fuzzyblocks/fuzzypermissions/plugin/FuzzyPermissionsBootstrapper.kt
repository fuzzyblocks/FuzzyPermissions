package net.fuzzyblocks.fuzzypermissions.plugin

import io.papermc.paper.plugin.bootstrap.BootstrapContext
import io.papermc.paper.plugin.bootstrap.PluginBootstrap
import io.papermc.paper.plugin.bootstrap.PluginProviderContext
import org.bukkit.plugin.java.JavaPlugin

@Suppress("UnstableApiUsage")
class FuzzyPermissionsBootstrapper : PluginBootstrap {

    override fun bootstrap(context: BootstrapContext) {
        context.logger.info("Bootstrapping FuzzyPermissions...")
        // In the future, we will load the initial database credentials here
        // and register Brigadier command root nodes to the LifecycleEventManager
    }

    override fun createPlugin(context: PluginProviderContext): JavaPlugin {
        context.logger.info("Creating FuzzyPermissions plugin instance...")
        
        // Later, we will instantiate the DatabaseManager, SnapshotEngine, etc., here
        // and inject them directly into the FuzzyPermissionsPlugin constructor.
        return FuzzyPermissionsPlugin()
    }
}