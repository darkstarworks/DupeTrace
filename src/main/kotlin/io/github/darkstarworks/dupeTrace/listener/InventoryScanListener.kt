package io.github.darkstarworks.dupeTrace.listener

import io.github.darkstarworks.dupeTrace.db.DatabaseManager
import io.github.darkstarworks.dupeTrace.util.ItemIdUtil
import org.bukkit.Bukkit
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.plugin.java.JavaPlugin

class InventoryScanListener(private val plugin: JavaPlugin, private val db: DatabaseManager) : Listener {

    @EventHandler
    fun onJoin(e: PlayerJoinEvent) {
        scanPlayer(e.player.inventory.contents.filterNotNull())
    }

    @EventHandler
    fun onInventoryClick(e: InventoryClickEvent) {
        val who = e.whoClicked ?: return
        val items = who.inventory.contents.filterNotNull()
        scanPlayer(items)
    }

    private fun scanPlayer(items: List<org.bukkit.inventory.ItemStack>) {
        for (item in items) {
            val id = ItemIdUtil.ensureUniqueId(plugin, item) ?: continue
            val firstSeen = db.recordSeen(id)
            if (!firstSeen) {
                // Duplicate detected
                val msg = "[DupeTrace] Duplicate item detected: $id in ${'$'}{item.type}"
                plugin.logger.warning(msg)
                if (plugin.config.getBoolean("broadcast-duplicates", true)) {
                    // Notify ops only
                    Bukkit.getOnlinePlayers().filter { it.isOp }.forEach { it.sendMessage(msg) }
                }
            }
        }
    }
}
