package io.github.darkstarworks.dupeTrace.listener

import io.github.darkstarworks.dupeTrace.db.DatabaseManager
import io.github.darkstarworks.dupeTrace.util.ItemIdUtil
import org.bukkit.*
import org.bukkit.block.Container
import org.bukkit.entity.EntityType
import org.bukkit.entity.ItemFrame
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockDispenseEvent
import org.bukkit.event.enchantment.EnchantItemEvent
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.event.entity.EntityPickupItemEvent
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.entity.VillagerAcquireTradeEvent
import org.bukkit.event.inventory.*
import org.bukkit.event.player.*
import org.bukkit.event.world.LootGenerateEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class ActivityListener(private val plugin: JavaPlugin, private val db: DatabaseManager) : Listener {


    // Track known items to detect duplicates (in-memory)
    private val knownItems = ConcurrentHashMap<String, ItemLocation>()

    data class ItemLocation(val playerUUID: UUID, val location: String)

    // ========== CORE HELPERS ==========
    private fun locationString(loc: Location): String = "${loc.world?.name}:${loc.blockX},${loc.blockY},${loc.blockZ}"

    private fun getUniqueId(item: ItemStack): String? = ItemIdUtil.getId(plugin, item)?.toString()

    private fun tagAndLog(player: Player, item: ItemStack, action: String, loc: Location) {
        val id = ItemIdUtil.ensureUniqueId(plugin, item) ?: return
        db.recordSeen(id)
        db.logItemTransfer(id.toString(), player.uniqueId, action, locationString(loc))
        checkForDuplicates(id.toString(), player)
    }

    // ========== EVENT HANDLERS - CREATION ==========
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onCraft(event: CraftItemEvent) {
        val player = event.whoClicked as? Player ?: return
        val result = event.currentItem ?: event.inventory.result ?: return
        tagAndLog(player, result, "CRAFTED", player.location)
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onEnchant(event: EnchantItemEvent) {
        tagAndLog(event.enchanter, event.item, "ENCHANTED", event.enchanter.location)
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onBrew(event: BrewEvent) {
        val inv = event.contents
        for (i in 0 until inv.size) {
            val item = inv.getItem(i) ?: continue
            ItemIdUtil.ensureUniqueId(plugin, item)?.let { db.recordSeen(it) }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onSmithing(event: PrepareSmithingEvent) {
        val result = event.inventory.result ?: return
        ItemIdUtil.ensureUniqueId(plugin, result)?.let { db.recordSeen(it) }
    }

    // Loot-generated containers (e.g., structures) should have items tagged at generation time
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onLootGenerate(event: LootGenerateEvent) {
        event.loot.forEach { item ->
            ItemIdUtil.ensureUniqueId(plugin, item)?.let { db.recordSeen(it) }
        }
    }

    // Ensure villager trade results are tagged as soon as the trade is created
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onVillagerAcquireTrade(event: VillagerAcquireTradeEvent) {
        val old = event.recipe
        val result = old.result.clone()
        ItemIdUtil.ensureUniqueId(plugin, result)?.let { db.recordSeen(it) }
        val newRecipe = org.bukkit.inventory.MerchantRecipe(result, old.maxUses)
        newRecipe.setIngredients(old.ingredients)
        newRecipe.setUses(old.uses)
        newRecipe.setVillagerExperience(old.villagerExperience)
        newRecipe.setPriceMultiplier(old.priceMultiplier)
        // In modern Bukkit, experience reward flag uses hasExperienceReward()/setExperienceReward(boolean)
        newRecipe.setExperienceReward(runCatching { old.hasExperienceReward() }.getOrDefault(true))
        event.setRecipe(newRecipe)
    }


    // ========== EVENT HANDLERS - ACQUISITION ==========
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onPickup(event: EntityPickupItemEvent) {
        val player = event.entity as? Player ?: return
        tagAndLog(player, event.item.itemStack, "PICKUP", event.item.location)
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onFish(event: PlayerFishEvent) {
        if (event.state != PlayerFishEvent.State.CAUGHT_FISH) return
        val item = (event.caught as? org.bukkit.entity.Item)?.itemStack ?: return
        tagAndLog(event.player, item, "FISHED", event.player.location)
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onEntityDeath(event: EntityDeathEvent) {
        val killer = event.entity.killer
        event.drops.forEach { item ->
            if (killer != null) tagAndLog(killer, item, "MOB_DROP", event.entity.location)
            else ItemIdUtil.ensureUniqueId(plugin, item)?.let { db.recordSeen(it) }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onBlockBreak(event: BlockBreakEvent) {
        val player = event.player
        event.block.getDrops(player.inventory.itemInMainHand, player).forEach { item ->
            tagAndLog(player, item, "BLOCK_BREAK", event.block.location)
        }
        // If a container is being broken, proactively tag its contents as they will drop next
        val state = event.block.state
        if (state is Container) {
            state.inventory.contents.filterNotNull().forEach { item ->
                ItemIdUtil.ensureUniqueId(plugin, item)?.let { db.recordSeen(it) }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onShear(event: PlayerShearEntityEvent) {
        // Shearing happens after event; scan next tick
        plugin.server.scheduler.runTask(plugin, Runnable {
            scanPlayerInventory(event.player)
        })
    }

    // ========== EVENT HANDLERS - INVENTORY INTERACTIONS ==========
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        event.currentItem?.let { tagAndLog(player, it, "INVENTORY_CLICK_CURRENT", player.location) }
        event.cursor?.let { tagAndLog(player, it, "INVENTORY_CLICK_CURSOR", player.location) }
        if (event.isShiftClick) {
            event.clickedInventory?.contents?.filterNotNull()?.forEach { tagAndLog(player, it, "INVENTORY_SHIFT_CLICK", player.location) }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onInventoryDrag(event: InventoryDragEvent) {
        val player = event.whoClicked as? Player ?: return
        event.newItems.values.forEach { tagAndLog(player, it, "INVENTORY_DRAG", player.location) }
        event.oldCursor?.let { tagAndLog(player, it, "INVENTORY_OLD_CURSOR", player.location) }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onInventoryOpen(event: InventoryOpenEvent) {
        val player = event.player as? Player ?: return
        event.inventory.contents.filterNotNull().forEach { tagAndLog(player, it, "INVENTORY_OPEN_SCAN", player.location) }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onInventoryClose(event: InventoryCloseEvent) {
        val player = event.player as? Player ?: return
        event.inventory.contents.filterNotNull().forEach { item ->
            val id = ItemIdUtil.ensureUniqueId(plugin, item) ?: return@forEach
            db.recordSeen(id)
            checkForDuplicates(id.toString(), player)
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onDrop(event: PlayerDropItemEvent) {
        val id = ItemIdUtil.ensureUniqueId(plugin, event.itemDrop.itemStack)
        if (id != null) {
            db.recordSeen(id)
            db.logItemTransfer(id.toString(), event.player.uniqueId, "DROPPED", locationString(event.itemDrop.location))
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onDeath(event: PlayerDeathEvent) {
        val p = event.entity
        event.drops.forEach { item ->
            val id = ItemIdUtil.ensureUniqueId(plugin, item)
            if (id != null) {
                db.recordSeen(id)
                db.logItemTransfer(id.toString(), p.uniqueId, "DEATH_DROP", locationString(p.location))
            }
        }
    }

    // ========== EVENT HANDLERS - SPECIAL ENTITIES ==========
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onItemFrame(event: PlayerInteractEntityEvent) {
        if (event.rightClicked.type != EntityType.ITEM_FRAME && event.rightClicked.type != EntityType.GLOW_ITEM_FRAME) return
        val frame = event.rightClicked as ItemFrame
        val frameItem = frame.item
        if (frameItem.type != Material.AIR) {
            ItemIdUtil.ensureUniqueId(plugin, frameItem)?.let { db.recordSeen(it) }
        }
        val handItem = event.player.inventory.itemInMainHand
        if (handItem.type != Material.AIR) {
            ItemIdUtil.ensureUniqueId(plugin, handItem)?.let { db.recordSeen(it) }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onArmorStandManipulate(event: PlayerArmorStandManipulateEvent) {
        event.playerItem?.let { ItemIdUtil.ensureUniqueId(plugin, it)?.let { id -> db.recordSeen(id) } }
        event.armorStandItem?.let { ItemIdUtil.ensureUniqueId(plugin, it)?.let { id -> db.recordSeen(id) } }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onDispense(event: BlockDispenseEvent) {
        ItemIdUtil.ensureUniqueId(plugin, event.item)?.let { db.recordSeen(it) }
    }

    // ========== PERIODIC SCANNING ==========
    fun startPeriodicScan() {
        val interval = plugin.config.getLong("scan-interval", 200L).coerceAtLeast(20L)
        plugin.server.scheduler.runTaskTimerAsynchronously(plugin, Runnable {
            plugin.server.onlinePlayers.chunked(10).forEach { batch ->
                plugin.server.scheduler.runTask(plugin, Runnable {
                    batch.forEach { scanPlayerInventory(it) }
                })
            }
        }, interval, interval)
    }

    fun scanPlayerInventory(player: Player) {
        // main inventory
        player.inventory.contents.filterNotNull().forEach { item ->
            if (ItemIdUtil.getId(plugin, item) == null) {
                ItemIdUtil.ensureUniqueId(plugin, item)?.let { db.recordSeen(it) }
            } else {
                getUniqueId(item)?.let { checkForDuplicates(it, player) }
            }
        }
        // armor
        player.inventory.armorContents.filterNotNull().forEach { item ->
            if (ItemIdUtil.getId(plugin, item) == null) {
                ItemIdUtil.ensureUniqueId(plugin, item)?.let { db.recordSeen(it) }
            }
        }
        // offhand
        val off = player.inventory.itemInOffHand
        if (off.type != Material.AIR && ItemIdUtil.getId(plugin, off) == null) {
            ItemIdUtil.ensureUniqueId(plugin, off)?.let { db.recordSeen(it) }
        }
        // ender chest
        player.enderChest.contents.filterNotNull().forEach { item ->
            if (ItemIdUtil.getId(plugin, item) == null) {
                ItemIdUtil.ensureUniqueId(plugin, item)?.let { db.recordSeen(it) }
            }
        }
    }

    // ========== DUPLICATE DETECTION ==========
    private fun checkForDuplicates(itemUUID: String, player: Player) {
        val current = ItemLocation(player.uniqueId, locationString(player.location))
        val known = knownItems.putIfAbsent(itemUUID, current)
        if (known != null && known != current) {
            plugin.logger.warning("DUPLICATE DETECTED: Item $itemUUID found in multiple locations! Known: $known New: $current")
            if (plugin.config.getBoolean("auto-remove-duplicates", false)) {
                removeDuplicateFromPlayer(player, itemUUID)
            }
            if (plugin.config.getBoolean("alert-admins", true)) {
                plugin.server.onlinePlayers
                    .filter { it.hasPermission("dupetrace.alerts") }
                    .forEach { it.sendMessage("§c[DupeTrace] §fItem $itemUUID duplicated! Player: ${player.name}") }
            }
        }
    }

    private fun removeDuplicateFromPlayer(player: Player, itemUUID: String) {
        var kept = false
        val inv = player.inventory
        inv.contents.forEachIndexed { index, item ->
            if (item == null) return@forEachIndexed
            val id = getUniqueId(item)
            if (id == itemUUID) {
                if (!kept) kept = true else inv.setItem(index, null)
            }
        }
    }
}
