package io.github.darkstarworks.dupeTrace.command

import io.github.darkstarworks.dupeTrace.db.DatabaseManager
import io.github.darkstarworks.dupeTrace.util.ItemIdUtil
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import java.text.SimpleDateFormat
import java.util.*

class DupeTestCommand(private val plugin: JavaPlugin, private val db: DatabaseManager) : CommandExecutor, TabCompleter {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (args.isEmpty()) {
            sendHelp(sender)
            return true
        }

        when (args[0].lowercase()) {
            "give" -> handleGive(sender)
            "uuid" -> handleUuid(sender, args)
            "history" -> handleHistory(sender, args)
            "stats" -> handleStats(sender)
            "search" -> handleSearch(sender, args)
            else -> sendHelp(sender)
        }
        return true
    }

    private fun handleGive(sender: CommandSender) {
        if (sender !is Player) {
            sender.sendMessage("§cPlayers only.")
            return
        }
        val item = ItemStack(Material.DIAMOND_SWORD)
        val id = ItemIdUtil.ensureUniqueId(plugin, item)
        if (id != null) {
            db.recordSeenAsync(id)
            sender.inventory.addItem(item)
            sender.sendMessage("§aGiven a Diamond Sword with id: §f$id")
        } else {
            sender.sendMessage("§cFailed to tag item!")
        }
    }

    private fun handleUuid(sender: CommandSender, args: Array<out String>) {
        if (args.size < 2) {
            sender.sendMessage("§cUsage: /dupetest uuid <uuid>")
            return
        }

        val itemUUID = args[1]

        // Run async to avoid blocking main thread
        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            if (!db.itemExists(itemUUID)) {
                sender.sendMessage("§cItem UUID not found in database: $itemUUID")
                return@Runnable
            }

            val history = db.getItemTransferHistory(itemUUID, 10)
            if (history.isEmpty()) {
                sender.sendMessage("§eItem exists but has no transfer history: $itemUUID")
                return@Runnable
            }

            sender.sendMessage("§6=== Item History: ${itemUUID.take(8)}... ===")
            sender.sendMessage("§7Showing latest 10 transfers:")
            history.forEach { record ->
                val playerName = Bukkit.getOfflinePlayer(record.playerUUID).name ?: record.playerUUID.toString()
                val time = dateFormat.format(Date(record.timestamp))
                sender.sendMessage("§f• §b$time §7- §e${record.action} §7by §a$playerName §7at §f${record.location}")
            }
        })
    }

    private fun handleHistory(sender: CommandSender, args: Array<out String>) {
        if (args.size < 2) {
            sender.sendMessage("§cUsage: /dupetest history <uuid> [limit]")
            return
        }

        val itemUUID = args[1]
        val limit = args.getOrNull(2)?.toIntOrNull() ?: 50

        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            if (!db.itemExists(itemUUID)) {
                sender.sendMessage("§cItem UUID not found in database: $itemUUID")
                return@Runnable
            }

            val history = db.getItemTransferHistory(itemUUID, limit)
            if (history.isEmpty()) {
                sender.sendMessage("§eItem exists but has no transfer history: $itemUUID")
                return@Runnable
            }

            sender.sendMessage("§6=== Full Transfer History: ${itemUUID.take(8)}... ===")
            sender.sendMessage("§7Showing up to $limit transfers:")
            history.forEachIndexed { index, record ->
                val playerName = Bukkit.getOfflinePlayer(record.playerUUID).name ?: record.playerUUID.toString()
                val time = dateFormat.format(Date(record.timestamp))
                sender.sendMessage("§f${index + 1}. §b$time §7- §e${record.action}")
                sender.sendMessage("   §7Player: §a$playerName §7Location: §f${record.location}")
            }
        })
    }

    private fun handleStats(sender: CommandSender) {
        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            val stats = db.getStats()
            val runtime = Runtime.getRuntime()
            val usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024
            val maxMemory = runtime.maxMemory() / 1024 / 1024

            sender.sendMessage("§6=== DupeTrace Statistics ===")
            sender.sendMessage("§eDatabase:")
            sender.sendMessage("  §7• Total Items Tracked: §f${stats.totalItems}")
            sender.sendMessage("  §7• Total Transfers Logged: §f${stats.totalTransfers}")
            sender.sendMessage("  §7• Unique Players: §f${stats.uniquePlayers}")
            sender.sendMessage("§eServer:")
            sender.sendMessage("  §7• Memory Usage: §f${usedMemory}MB / ${maxMemory}MB")
            sender.sendMessage("  §7• Online Players: §f${plugin.server.onlinePlayers.size}")
        })
    }

    private fun handleSearch(sender: CommandSender, args: Array<out String>) {
        if (args.size < 2) {
            sender.sendMessage("§cUsage: /dupetest search <player>")
            return
        }

        val targetName = args[1]

        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            val target = Bukkit.getOfflinePlayer(targetName)
            if (!target.hasPlayedBefore() && !target.isOnline) {
                sender.sendMessage("§cPlayer not found: $targetName")
                return@Runnable
            }

            val items = db.getPlayerItems(target.uniqueId, 50)
            if (items.isEmpty()) {
                sender.sendMessage("§eNo tracked items found for player: §a${target.name}")
                return@Runnable
            }

            sender.sendMessage("§6=== Items for ${target.name} ===")
            sender.sendMessage("§7Showing up to 50 most recent items:")
            items.forEachIndexed { index, uuid ->
                val shortId = uuid.take(8)
                sender.sendMessage("§f${index + 1}. §7$shortId... §8(Click to view)")
            }
            sender.sendMessage("§7Use §e/dupetest history <uuid> §7for details")
        })
    }

    private fun sendHelp(sender: CommandSender) {
        sender.sendMessage("§6=== DupeTrace Commands ===")
        sender.sendMessage("§e/dupetest give §7- Give yourself a test item")
        sender.sendMessage("§e/dupetest uuid <uuid> §7- View item's recent history")
        sender.sendMessage("§e/dupetest history <uuid> [limit] §7- View full transfer log")
        sender.sendMessage("§e/dupetest stats §7- View plugin statistics")
        sender.sendMessage("§e/dupetest search <player> §7- List player's tracked items")
    }

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>
    ): List<String> {
        if (args.size == 1) {
            return listOf("give", "uuid", "history", "stats", "search")
                .filter { it.startsWith(args[0].lowercase()) }
        }
        if (args.size == 2 && args[0].equals("search", ignoreCase = true)) {
            return Bukkit.getOnlinePlayers()
                .map { it.name }
                .filter { it.startsWith(args[1], ignoreCase = true) }
        }
        return emptyList()
    }
}
