package net.fuzzyblocks.fuzzypermissions.plugin

import org.bukkit.plugin.java.JavaPlugin

class FuzzyPermissionsPlugin : JavaPlugin() {

    override fun onLoad() {
        logger.info("PaperPerms loaded.")
    }

    override fun onEnable() {
        logger.info("Enabling PaperPerms...")
    }

    override fun onDisable() {
        logger.info("Disabling PaperPerms...")
        
        // Cleanup will go here:
        // 1. Cancel all background Coroutines (DatabaseDispatcher)
        // 2. Close the HikariCP connection pool
        // 3. Clear the PermissionCache
    }
}