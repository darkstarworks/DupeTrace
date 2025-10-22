package io.github.darkstarworks.dupeTrace

import io.github.darkstarworks.dupeTrace.command.DupeTestCommand
import io.github.darkstarworks.dupeTrace.db.DatabaseManager
import io.github.darkstarworks.dupeTrace.listener.ActivityListener
import io.github.darkstarworks.dupeTrace.listener.InventoryScanListener
import org.bukkit.plugin.java.JavaPlugin

/**
 * DupeTrace - A Minecraft plugin for detecting and preventing item duplication.
 *
 * Author: darkstarworks | https://github.com/darkstarworks
 * Donate: https://ko-fi/darkstarworks
 *
 * This plugin tracks non-stackable items using unique UUIDs stored in persistent data containers.
 * It monitors item movements across players, containers, and events to detect when the same
 * item appears in multiple locations simultaneously, indicating a duplication exploit.
 */
class DupeTrace : JavaPlugin() {

    private lateinit var db: DatabaseManager

    /**
     * Validates configuration values and logs warnings for any invalid settings.
     */
    private fun validateConfiguration() {
        val cfg = config

        // Validate numeric ranges
        val movementGrace = cfg.getLong("movement-grace-ms", 750L)
        if (movementGrace < 0) {
            logger.warning("Invalid movement-grace-ms: $movementGrace (must be >= 0). Using default: 750")
            cfg.set("movement-grace-ms", 750L)
        }

        val alertDebounce = cfg.getLong("duplicate-alert-debounce-ms", 2000L)
        if (alertDebounce < 0) {
            logger.warning("Invalid duplicate-alert-debounce-ms: $alertDebounce (must be >= 0). Using default: 2000")
            cfg.set("duplicate-alert-debounce-ms", 2000L)
        }

        val knownItemsTtl = cfg.getLong("known-items-ttl-ms", 600_000L)
        if (knownItemsTtl < 60_000L) {
            logger.warning("Invalid known-items-ttl-ms: $knownItemsTtl (must be >= 60000). Using default: 600000")
            cfg.set("known-items-ttl-ms", 600_000L)
        }

        val scanInterval = cfg.getLong("scan-interval", 200L)
        if (scanInterval < 20L) {
            logger.warning("Invalid scan-interval: $scanInterval (must be >= 20 ticks). Using default: 200")
            cfg.set("scan-interval", 200L)
        }

        // Validate database type
        val dbType = cfg.getString("database.type", "h2")?.lowercase()
        if (dbType != "h2" && dbType != "postgres" && dbType != "postgresql") {
            logger.warning("Invalid database.type: $dbType (must be 'h2' or 'postgres'). Using default: h2")
            cfg.set("database.type", "h2")
        }
    }

    override fun onEnable() {
        try {
            // Plugin startup logic
            saveDefaultConfig()
            validateConfiguration()
            db = DatabaseManager(this)
            db.init()

            // Register listeners
            server.pluginManager.registerEvents(InventoryScanListener(this, db), this)
            val activityListener = ActivityListener(this, db)
            server.pluginManager.registerEvents(activityListener, this)
            activityListener.startPeriodicScan()
            activityListener.startKnownItemsCleanup()

            // Register commands
            getCommand("dupetest")?.setExecutor(DupeTestCommand(this, db))

            logger.info("DupeTrace enabled. Using ${config.getString("database.type", "h2")} database.")
        } catch (e: NoClassDefFoundError) {
            logger.severe("Missing runtime dependency: ${e.message}. You are likely running the development jar (with '-dev' or '-thin' suffix) which omits dependencies. Please use the default DupeTrace-${description.version}.jar (shaded) in production.")
            // Disable the plugin gracefully
            server.pluginManager.disablePlugin(this)
        } catch (e: ClassNotFoundException) {
            logger.severe("Missing runtime class: ${e.message}. Please use the default shaded DupeTrace-${description.version}.jar and do not use the '-dev' jar on servers.")
            server.pluginManager.disablePlugin(this)
        }
    }

    override fun onDisable() {
        // Plugin shutdown logic
        if (this::db.isInitialized) {
            db.close()
        }
        logger.info("DupeTrace disabled.")
    }
}
