package io.github.darkstarworks.dupeTrace

import io.github.darkstarworks.dupeTrace.command.DupeTestCommand
import io.github.darkstarworks.dupeTrace.db.DatabaseManager
import io.github.darkstarworks.dupeTrace.listener.InventoryScanListener
import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin

class DupeTrace : JavaPlugin() {

    private lateinit var db: DatabaseManager

    override fun onEnable() {
        // Plugin startup logic
        saveDefaultConfig()
        db = DatabaseManager(this)
        db.init()

        // Register listeners
        server.pluginManager.registerEvents(InventoryScanListener(this, db), this)

        // Register commands
        getCommand("dupetest")?.setExecutor(DupeTestCommand(this, db))

        logger.info("DupeTrace enabled. Using ${config.getString("database.type", "h2")} database.")

        // Optional: schedule a delayed scan for online players after plugins enable
        Bukkit.getScheduler().runTaskLater(this, Runnable {
            server.onlinePlayers.forEach { p ->
                p.inventory.contents.filterNotNull().forEach { /* trigger ensure on listener via click? optionally no-op */ }
            }
        }, 40L)
    }

    override fun onDisable() {
        // Plugin shutdown logic
        if (this::db.isInitialized) {
            db.close()
        }
        logger.info("DupeTrace disabled.")
    }
}
