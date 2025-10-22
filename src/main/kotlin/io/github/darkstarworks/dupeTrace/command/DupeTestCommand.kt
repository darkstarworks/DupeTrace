package io.github.darkstarworks.dupeTrace.command

import io.github.darkstarworks.dupeTrace.db.DatabaseManager
import io.github.darkstarworks.dupeTrace.util.ItemIdUtil
import org.bukkit.Material
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin

class DupeTestCommand(private val plugin: JavaPlugin, private val db: DatabaseManager) : CommandExecutor {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (args.isEmpty() || args[0].equals("give", true)) {
            if (sender !is Player) {
                sender.sendMessage("Players only.")
                return true
            }
            val item = ItemStack(Material.DIAMOND_SWORD)
            val id = ItemIdUtil.ensureUniqueId(plugin, item)
            if (id != null) {
                db.recordSeen(id)
                sender.inventory.addItem(item)
                sender.sendMessage("Given a Diamond Sword with id: $id")
            } else {
                sender.sendMessage("Failed to tag item!")
            }
            return true
        }
        sender.sendMessage("Usage: /$label give")
        return true
    }
}
