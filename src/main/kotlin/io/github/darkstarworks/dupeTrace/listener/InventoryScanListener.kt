package io.github.darkstarworks.dupeTrace.listener

import io.github.darkstarworks.dupeTrace.db.DatabaseManager
import io.github.darkstarworks.dupeTrace.util.ItemIdUtil
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
        val who = e.whoClicked
        val items = who.inventory.contents.filterNotNull()
        scanPlayer(items)
    }

    private fun scanPlayer(items: List<org.bukkit.inventory.ItemStack>) {
        for (item in items) {
            // Tag any untagged non-stackable items and record their first-seen time.
            // Do NOT treat previously seen items as duplicates here — duplicate detection
            // is handled by ActivityListener with proper movement and ownership context.
            val id = ItemIdUtil.ensureUniqueId(plugin, item) ?: continue
            db.recordSeenAsync(id)
        }
    }
}
