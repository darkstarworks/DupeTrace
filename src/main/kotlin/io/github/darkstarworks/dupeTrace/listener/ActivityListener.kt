package io.github.darkstarworks.dupeTrace.listener

import io.github.darkstarworks.dupeTrace.db.DatabaseManager
import io.github.darkstarworks.dupeTrace.util.ItemIdUtil
import io.github.darkstarworks.dupeTrace.webhook.DiscordWebhook
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
import org.bukkit.event.entity.*
import org.bukkit.event.inventory.*
import org.bukkit.event.player.*
import org.bukkit.event.server.ServerCommandEvent
import org.bukkit.event.world.ChunkUnloadEvent
import org.bukkit.event.world.LootGenerateEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.inventory.AnvilInventory
import org.bukkit.inventory.ItemStack
import org.bukkit.event.vehicle.VehicleDestroyEvent
import org.bukkit.entity.minecart.StorageMinecart
import org.bukkit.plugin.java.JavaPlugin
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class ActivityListener(private val plugin: JavaPlugin, private val db: DatabaseManager) : Listener {

    // ========== TRACK KNOWN ITEMS - IN MEMORY ==========
    private val knownItems = ConcurrentHashMap<String, ItemLocation>()
    private val pendingAnvilInputs = ConcurrentHashMap<UUID, Set<String>>()
    private val lastAlertTs = ConcurrentHashMap<String, Long>()
    private val discordWebhook = DiscordWebhook(plugin)

    // Pseudo owners for non-player holders
    private val ownerItemFrame: UUID = UUID(0L, 1L)
    private val ownerArmorStand: UUID = UUID(0L, 2L)
    private val ownerContainer: UUID = UUID(0L, 3L)

    data class ItemLocation(val playerUUID: UUID, val location: String, val lastSeenMs: Long, val firstSeenMs: Long = lastSeenMs)

    // ========== CORE HELPERS ==========
    private fun locationString(loc: Location): String = "${loc.world?.name}:${loc.blockX},${loc.blockY},${loc.blockZ}"

    private fun holderLocationString(holder: org.bukkit.inventory.InventoryHolder?): String {
        if (holder == null) return "unknown:0,0,0"
        return when (holder) {
            is org.bukkit.block.BlockState -> locationString(holder.location)
            is org.bukkit.entity.Entity -> locationString(holder.location)
            else -> "unknown:0,0,0"
        }
    }

    private fun getUniqueId(item: ItemStack): String? = ItemIdUtil.getId(plugin, item)?.toString()

    private fun tagAndLog(player: Player, item: ItemStack, action: String, loc: Location) {
        // Determine if this item is being assigned a UUID for the first time
        val wasNew = ItemIdUtil.getId(plugin, item) == null
        val id = ItemIdUtil.ensureUniqueId(plugin, item) ?: return
        // If this item is a Shulker Box or bundle, ensure inner contents are tagged too
        deepTagShulkerContentsIfAny(item)
        deepTagBundleContentsIfAny(item)
        db.recordSeenAsync(id)
        db.logItemTransferAsync(id.toString(), player.uniqueId, action, locationString(loc))
        if (wasNew) {
            // Notify watchers with permission about newly assigned UUIDs
            sendItemAssignedMessage(player, item, id)
        }
        checkForDuplicates(id.toString(), player)
    }

    private fun sendItemAssignedMessage(player: Player, item: ItemStack, id: UUID) {
        try {
            val watchers = plugin.server.onlinePlayers.filter { it.hasPermission("dupetrace.alerts") }
            if (watchers.isEmpty()) return

            val fullId = id.toString()
            val shortId = fullId.take(8)

            val itemName = item.itemMeta?.displayName()?.let { net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(it).ifBlank { null } }
                ?: item.type.name.lowercase().replace('_', ' ').split(' ').joinToString(" ") { it.replaceFirstChar { c -> c.titlecase() } }

            val mm = net.kyori.adventure.text.minimessage.MiniMessage.miniMessage()
            val component = mm.deserialize(
                "<white>[<dark_aqua><player><white>] received <gold><item> <dark_gray>[<hover:show_text:'<blue><full>'><click:suggest_command:'<cmd>'><short><dark_gray>]",
                net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.unparsed("player", player.name),
                net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.unparsed("item", itemName),
                net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.unparsed("full", fullId),
                net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.unparsed("short", shortId),
                net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.unparsed("cmd", "/dupetest uuid $fullId")
            )

            watchers.forEach { it.sendMessage(component) }
        } catch (_: Throwable) {
            // Fallback plain text if components are unavailable
            val msg = "[${player.name}] received ${item.type} [$id]"
            plugin.server.onlinePlayers
                .filter { it.hasPermission("dupetrace.alerts") }
                .forEach { it.sendMessage(msg) }
        }
    }

    private fun deepTagShulkerContentsIfAny(item: ItemStack) {
        try {
            val meta = item.itemMeta ?: return
            if (meta is org.bukkit.inventory.meta.BlockStateMeta) {
                val bs = meta.blockState
                if (bs is org.bukkit.block.ShulkerBox) {
                    val inv = bs.inventory
                    var changed = false
                    for (i in 0 until inv.size) {
                        val inner = inv.getItem(i) ?: continue
                        val innerId = ItemIdUtil.ensureUniqueId(plugin, inner) ?: continue
                        db.recordSeenAsync(innerId)
                        inv.setItem(i, inner)
                        changed = true
                    }
                    if (changed) {
                        meta.blockState = bs
                        item.itemMeta = meta
                    }
                }
            }
        } catch (e: Exception) {
            // best-effort only; shulker box meta may not be accessible in all contexts
            plugin.logger.fine("Could not tag shulker contents: ${e.message}")
        }
    }

    private fun deepTagBundleContentsIfAny(item: ItemStack) {
        try {
            val meta = item.itemMeta ?: return
            if (meta is org.bukkit.inventory.meta.BundleMeta) {
                val items = meta.items
                var changed = false
                if (items is MutableList<ItemStack?>) {
                    for (i in items.indices) {
                        val inner = items[i] ?: continue
                        val copy = inner.clone()
                        val innerId = ItemIdUtil.ensureUniqueId(plugin, copy)
                        if (innerId != null) {
                            db.recordSeenAsync(innerId)
                            items[i] = copy
                            changed = true
                        }
                    }
                    if (changed) {
                        item.itemMeta = meta
                    }
                } else {
                    // Fallback: try reflection to call setItems(List)
                    try {
                        val newItems = items.map { inner ->
                            if (inner == null) return@map null
                            val copy = inner.clone()
                            val innerId = ItemIdUtil.ensureUniqueId(plugin, copy)
                            if (innerId != null) db.recordSeenAsync(innerId)
                            copy
                        }
                        val m = meta.javaClass.getMethod("setItems", List::class.java)
                        m.invoke(meta, newItems)
                        item.itemMeta = meta
                    } catch (e: Exception) {
                        // Reflection fallback failed
                        plugin.logger.fine("Could not set bundle items via reflection: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            // best-effort only; BundleMeta may not exist on older APIs
            plugin.logger.fine("Could not tag bundle contents: ${e.message}")
        }
    }

    // ========== CLEANUP HELPERS ==========
    private fun cleanupKnownItems(itemUUID: String) {
        knownItems.remove(itemUUID)
    }

    private fun cleanupUnknownForPlayer(player: Player) {
        // Build set of IDs currently in player's inventories
        val present = HashSet<String>()
        player.inventory.contents.filterNotNull().forEach { getUniqueId(it)?.let(present::add) }
        player.inventory.armorContents.filterNotNull().forEach { getUniqueId(it)?.let(present::add) }
        val off = player.inventory.itemInOffHand
        if (!off.type.isAir) getUniqueId(off)?.let(present::add)
        player.enderChest.contents.filterNotNull().forEach { getUniqueId(it)?.let(present::add) }
        // Remove known items mapped to this player that are not present anymore
        knownItems.entries.removeIf { (id, loc) -> loc.playerUUID == player.uniqueId && !present.contains(id) }
    }

    // ========== EVENT HANDLERS - CREATION ==========
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onCraft(event: CraftItemEvent) {
        val player = event.whoClicked as? Player ?: return
        val result = event.currentItem ?: event.inventory.result ?: return
        tagAndLog(player, result, "CRAFTED", player.location)
    }

    // ========== ITEM ENCHANTMENT - TAG ==========
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onEnchant(event: EnchantItemEvent) {
        tagAndLog(event.enchanter, event.item, "ENCHANTED", event.enchanter.location)
    }

    // ========== BREWING STAND - TAG ==========
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onBrew(event: BrewEvent) {
        val inv = event.contents
        for (i in 0 until inv.size) {
            val item = inv.getItem(i) ?: continue
            ItemIdUtil.ensureUniqueId(plugin, item)?.let { db.recordSeenAsync(it) }
        }
    }

    // ========== CRAFTER BLOCK - TAG (1.21+) ==========
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onCrafterCraft(event: org.bukkit.event.block.CrafterCraftEvent) {
        val result = event.result
        ItemIdUtil.ensureUniqueId(plugin, result)?.let { db.recordSeenAsync(it) }
    }

    // ========== SMITHING TABLE - TAG ==========
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onSmithing(event: PrepareSmithingEvent) {
        val result = event.inventory.result ?: return
        ItemIdUtil.ensureUniqueId(plugin, result)?.let { db.recordSeenAsync(it) }
    }

    // ========== LOOT GENERATING CONTAINERS - TAG ==========
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onLootGenerate(event: LootGenerateEvent) {
        event.loot.forEach { item ->
            ItemIdUtil.ensureUniqueId(plugin, item)?.let { db.recordSeenAsync(it) }
        }
    }

    // ========== VILLAGER TRADE RESULTS - TAG ==========
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onVillagerAcquireTrade(event: VillagerAcquireTradeEvent) {
        val old = event.recipe
        val result = old.result.clone()
        ItemIdUtil.ensureUniqueId(plugin, result)?.let { db.recordSeenAsync(it) }
        val newRecipe = org.bukkit.inventory.MerchantRecipe(result, old.maxUses)
        newRecipe.ingredients = old.ingredients
        newRecipe.uses = old.uses
        newRecipe.villagerExperience = old.villagerExperience
        newRecipe.priceMultiplier = old.priceMultiplier
        // In modern Bukkit, the experience reward flag uses hasExperienceReward()/setExperienceReward(boolean)
        newRecipe.setExperienceReward(runCatching { old.hasExperienceReward() }.getOrDefault(true))
        event.recipe = newRecipe
    }


    // ========== EVENT HANDLERS - ACQUISITION ==========
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onPickup(event: EntityPickupItemEvent) {
        val player = event.entity as? Player ?: return
        tagAndLog(player, event.item.itemStack, "PICKUP", event.item.location)
    }

    // ========== FISHING - TAG ==========
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onFish(event: PlayerFishEvent) {
        if (event.state != PlayerFishEvent.State.CAUGHT_FISH) return
        val item = (event.caught as? org.bukkit.entity.Item)?.itemStack ?: return
        tagAndLog(event.player, item, "FISHED", event.player.location)
    }

    // ========== ENTITY LOOT - TAG ==========
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onEntityDeath(event: EntityDeathEvent) {
        val killer = event.entity.killer
        event.drops.forEach { item ->
            if (killer != null) tagAndLog(killer, item, "MOB_DROP", event.entity.location)
            else ItemIdUtil.ensureUniqueId(plugin, item)?.let { db.recordSeenAsync(it) }
        }
    }

    // ========== BLOCK BREAK - TAG ==========
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onBlockBreak(event: BlockBreakEvent) {
        val player = event.player
        event.block.getDrops(player.inventory.itemInMainHand, player).forEach { item ->
            tagAndLog(player, item, "BLOCK_BREAK", event.block.location)
        }
        // making sure unknown items are tagged in case of an unopened container breaking
        val state = event.block.state
        if (state is Container) {
            state.inventory.contents.filterNotNull().forEach { item ->
                ItemIdUtil.ensureUniqueId(plugin, item)?.let { db.recordSeenAsync(it) }
            }
        }
    }

    // ========== SHEARING - TAG ==========
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onShear(event: PlayerShearEntityEvent) {
        // Shearing happens after the event; scan next tick
        plugin.server.scheduler.runTask(plugin, Runnable {
            scanPlayerInventory(event.player)
        })
    }

    // ========== EVENT HANDLERS - INVENTORY INTERACTIONS ==========
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        event.currentItem?.let { tagAndLog(player, it, "INVENTORY_CLICK_CURRENT", player.location) }
        tagAndLog(player, event.cursor, "INVENTORY_CLICK_CURSOR", player.location)
        if (event.isShiftClick) {
            event.clickedInventory?.contents?.filterNotNull()?.forEach { tagAndLog(player, it, "INVENTORY_SHIFT_CLICK", player.location) }
        }
    }

    // ========== INV DRAGGING - TAG ==========
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onInventoryDrag(event: InventoryDragEvent) {
        val player = event.whoClicked as? Player ?: return
        event.newItems.values.forEach { tagAndLog(player, it, "INVENTORY_DRAG", player.location) }
        tagAndLog(player, event.oldCursor, "INVENTORY_OLD_CURSOR", player.location)
    }

    // ========== CREATIVE INVENTORY - TAG & DUPLICATE CONTEXT ==========
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onInventoryCreative(event: InventoryCreativeEvent) {
        val player = event.whoClicked as? Player ?: return
        val locStr = locationString(player.location)
        val items = listOf(event.currentItem, event.cursor)
        items.forEach { item ->
            if (item == null) return@forEach
            val id = ItemIdUtil.ensureUniqueId(plugin, item)
            if (id != null) {
                db.recordSeenAsync(id)
                db.logItemTransferAsync(id.toString(), player.uniqueId, "CREATIVE_INVENTORY", locStr)
                checkForDuplicates(id.toString(), player, setOf("CREATIVE"))
            }
        }
    }

    // ========== INV OPEN - TAG ==========
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onInventoryOpen(event: InventoryOpenEvent) {
        val player = event.player as? Player ?: return
        if (plugin.config.getBoolean("inventory-open-scan-enabled", true)) {
            event.inventory.contents.filterNotNull().forEach { tagAndLog(player, it, "INVENTORY_OPEN_SCAN", player.location) }
        }
    }

    // ========== INV CLOSE - TAG ==========
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onInventoryClose(event: InventoryCloseEvent) {
        val player = event.player as? Player ?: return
        event.inventory.contents.filterNotNull().forEach { item ->
            val id = ItemIdUtil.ensureUniqueId(plugin, item) ?: return@forEach
            db.recordSeenAsync(id)
            checkForDuplicates(id.toString(), player)
        }
        // Clean up any pending anvil inputs for this player if applicable
        if (event.inventory is AnvilInventory) {
            pendingAnvilInputs.remove(player.uniqueId)
        }
    }

    // ========== ITEM DROP - TAG ==========
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onDrop(event: PlayerDropItemEvent) {
        val id = ItemIdUtil.ensureUniqueId(plugin, event.itemDrop.itemStack)
        if (id != null) {
            db.recordSeenAsync(id)
            db.logItemTransferAsync(id.toString(), event.player.uniqueId, "DROPPED", locationString(event.itemDrop.location))
            val now = System.currentTimeMillis()
            knownItems[id.toString()] = ItemLocation(event.player.uniqueId, locationString(event.itemDrop.location), now, now)
        }
    }

    // ========== DEATH EVENT - TAG ==========
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onDeath(event: PlayerDeathEvent) {
        val p = event.entity
        val deathLoc = locationString(p.location)
        val now = System.currentTimeMillis()
        event.drops.forEach { item ->
            val id = ItemIdUtil.ensureUniqueId(plugin, item)
            if (id != null) {
                db.recordSeenAsync(id)
                db.logItemTransferAsync(id.toString(), p.uniqueId, "DEATH_DROP", deathLoc)
                val idStr = id.toString()
                val existing = knownItems[idStr]
                val first = existing?.firstSeenMs ?: now
                knownItems[idStr] = ItemLocation(p.uniqueId, deathLoc, now, first)
            }
        }
    }

    // ========== EVENT HANDLERS - SPECIAL ENTITIES ==========
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onItemFrame(event: PlayerInteractEntityEvent) {
        if (event.rightClicked.type != EntityType.ITEM_FRAME && event.rightClicked.type != EntityType.GLOW_ITEM_FRAME) return
        val frame = event.rightClicked as ItemFrame
        val now = System.currentTimeMillis()
        val frameItem = frame.item
        if (!frameItem.type.isAir) {
            ItemIdUtil.ensureUniqueId(plugin, frameItem)?.let {
                db.recordSeenAsync(it)
                val idStr = it.toString()
                val existing = knownItems[idStr]
                val first = existing?.firstSeenMs ?: now
                knownItems[idStr] = ItemLocation(ownerItemFrame, locationString(frame.location), now, first)
            }
        }
        val handItem = event.player.inventory.itemInMainHand
        if (!handItem.type.isAir) {
            ItemIdUtil.ensureUniqueId(plugin, handItem)?.let { db.recordSeenAsync(it) }
        }
    }

    // ========== ARMOR STAND - TAG ==========
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onArmorStandManipulate(event: PlayerArmorStandManipulateEvent) {
        event.playerItem.let { ItemIdUtil.ensureUniqueId(plugin, it)?.let { id -> db.recordSeenAsync(id) } }
        event.armorStandItem.let {
            ItemIdUtil.ensureUniqueId(plugin, it)?.let { id ->
                db.recordSeenAsync(id)
                val idStr = id.toString()
                val now = System.currentTimeMillis()
                val existing = knownItems[idStr]
                val first = existing?.firstSeenMs ?: now
                knownItems[idStr] = ItemLocation(ownerArmorStand, locationString(event.rightClicked.location), now, first)
            }
        }
    }

    // ========== LECTERN - TAG BOOKS ==========
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onPlayerTakeBook(event: PlayerTakeLecternBookEvent) {
        val book = event.book
        if (book != null && !book.type.isAir) {
            tagAndLog(event.player, book, "LECTERN_TAKE", event.lectern.location)
        }
    }

    // ========== DISPENSER - TAG ==========
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onDispense(event: BlockDispenseEvent) {
        ItemIdUtil.ensureUniqueId(plugin, event.item)?.let { db.recordSeenAsync(it) }
    }

    // ========== HOPPER/CONTAINER ITEM MOVE - TAG ==========
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onInventoryMoveItem(event: InventoryMoveItemEvent) {
        val item = event.item
        val id = ItemIdUtil.ensureUniqueId(plugin, item)
        if (id != null) {
            db.recordSeenAsync(id)
            val now = System.currentTimeMillis()
            val locStr = holderLocationString(event.destination.holder ?: event.source.holder)
            val existing = knownItems[id.toString()]
            val first = existing?.firstSeenMs ?: now
            knownItems[id.toString()] = ItemLocation(ownerContainer, locStr, now, first)
        }
    }

    // ========== VEHICLE (CHEST MINECART) DESTROY - TAG CONTENTS ==========
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onVehicleDestroy(event: VehicleDestroyEvent) {
        val cart = event.vehicle as? StorageMinecart ?: return
        cart.inventory.contents.filterNotNull().forEach { item ->
            ItemIdUtil.ensureUniqueId(plugin, item)?.let { db.recordSeenAsync(it) }
        }
    }

    // ========== PLAYER QUIT - CLEANUP ==========
     @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
     fun onQuit(event: PlayerQuitEvent) {
         pendingAnvilInputs.remove(event.player.uniqueId)
     }
 
    // ========== ITEM CONSUME - CLEANUP ==========
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onItemConsume(event: PlayerItemConsumeEvent) {
        getUniqueId(event.item)?.let { cleanupKnownItems(it) }
    }

    // ========== ITEM DESPAWN - CLEANUP ==========
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onItemDespawn(event: ItemDespawnEvent) {
        getUniqueId(event.entity.itemStack)?.let { cleanupKnownItems(it) }
    }

    // ========== ITEM MERGE - CLEANUP ==========
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onItemMerge(event: ItemMergeEvent) {
        // When items merge, one entity disappears
        getUniqueId(event.entity.itemStack)?.let { cleanupKnownItems(it) }
    }

    // ========== ITEM BURN - CLEANUP ==========
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onItemBurn(event: EntityCombustEvent) {
        if (event.entity is org.bukkit.entity.Item) {
            val item = event.entity as org.bukkit.entity.Item
            getUniqueId(item.itemStack)?.let { cleanupKnownItems(it) }
        }
    }

    // ========== ITEM DAMAGE - CLEANUP ==========
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onItemDamage(event: EntityDamageEvent) {
        // Covers lava, fire, cactus, void damage to item entities
        if (event.entity is org.bukkit.entity.Item) {
            val item = event.entity as org.bukkit.entity.Item
            // If damage can destroy the item entity
            try {
                if (item.health <= event.finalDamage) {
                    getUniqueId(item.itemStack)?.let { cleanupKnownItems(it) }
                }
            } catch (_: Exception) {
                // Some server versions may not expose health; fallback when the entity is already dead
                if (!item.isValid || item.isDead) {
                    getUniqueId(item.itemStack)?.let { cleanupKnownItems(it) }
                }
            }
        }
    }

    // ========== ITEM BROKEN - CLEANUP ==========
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onItemBreak(event: PlayerItemBreakEvent) {
        // Tools/armor breaking from durability
        getUniqueId(event.brokenItem)?.let { cleanupKnownItems(it) }
    }

    // ========== FURNACE SMELT - CLEANUP ==========
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onFurnaceSmelt(event: FurnaceSmeltEvent) {
        // Input item is consumed during smelting
        getUniqueId(event.source)?.let { cleanupKnownItems(it) }
    }

    // ========== CREATIVE DESTROY - CLEANUP ==========
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onInventoryClickCleanup(event: InventoryClickEvent) {
        // Detect items being destroyed in creative mode
        if (event.whoClicked is Player && (event.whoClicked as Player).gameMode == GameMode.CREATIVE) {
            if (event.click == ClickType.CREATIVE) {
                event.currentItem?.let { item ->
                    getUniqueId(item)?.let { cleanupKnownItems(it) }
                }
            }
        }

        // Anvil result taken: cleanup inputs if tracked
        val top = event.view.topInventory
        if (top is AnvilInventory && event.slotType == InventoryType.SlotType.RESULT && event.rawSlot == 2) {
            val pid = (event.whoClicked as? Player)?.uniqueId
            if (pid != null) {
                pendingAnvilInputs.remove(pid)?.forEach { cleanupKnownItems(it) }
            }
        }
    }

    // ========== CLEANUP - MISSING CASES ==========
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onBlockPlace(event: BlockPlaceEvent) {
        // Items like shulker boxes being placed
        getUniqueId(event.itemInHand)?.let { cleanupKnownItems(it) }
        val state = event.blockPlaced.state
        if (state is org.bukkit.block.ShulkerBox) {
            state.inventory.contents.filterNotNull().forEach { item ->
                ItemIdUtil.ensureUniqueId(plugin, item)?.let { db.recordSeenAsync(it) }
            }
        }
    }

    // ========== ANVIL CONSUME - CLEANUP ==========
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onPrepareAnvil(event: PrepareAnvilEvent) {
        val inv = event.inventory
        if (event.result == null) {
            // No meaningful result; clear any pending
            inv.viewers.forEach { v ->
                (v as? Player)?.let { pendingAnvilInputs.remove(it.uniqueId) }
            }
            return
        }
        val ids = mutableSetOf<String>()
        inv.getItem(0)?.let { getUniqueId(it)?.let(ids::add) }
        inv.getItem(1)?.let { getUniqueId(it)?.let(ids::add) }
        if (ids.isNotEmpty()) {
            inv.viewers.forEach { v ->
                (v as? Player)?.let { pendingAnvilInputs[it.uniqueId] = ids }
            }
        }
    }

    // ========== CLEAR COMMAND USR - CLEANUP ==========
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onPlayerCommandClear(event: PlayerCommandPreprocessEvent) {
        val msg = event.message.lowercase(Locale.getDefault()).trim()
        if (msg.startsWith("/clear") || msg.startsWith("/minecraft:clear")) {
            val player = event.player
            // After the command executes, adjust known items for this player
            plugin.server.scheduler.runTask(plugin, Runnable {
                cleanupUnknownForPlayer(player)
            })
        }
    }

    // ========== CLEAR COMMAND SRV - CLEANUP ==========
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onServerCommandClear(event: ServerCommandEvent) {
        val cmd = event.command.lowercase(Locale.getDefault()).trim()
        if (cmd.startsWith("clear") || cmd.startsWith("minecraft:clear")) {
            // Try to resolve a target player (first arg if present)
            val parts = cmd.split(Regex("\\s+"))
            val targetName = parts.getOrNull(1)
            val target = if (targetName != null) plugin.server.getPlayerExact(targetName) else null
            if (target != null) {
                plugin.server.scheduler.runTask(plugin, Runnable { cleanupUnknownForPlayer(target) })
            } else {
                // If no explicit target, clean up all online players just in case
                plugin.server.scheduler.runTask(plugin, Runnable {
                    plugin.server.onlinePlayers.forEach { cleanupUnknownForPlayer(it) }
                })
            }
        }
    }

    // ========== SHULKER UNLOAD - CLEANUP ==========
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onChunkUnload(event: ChunkUnloadEvent) {
        // Items in unloaded chunks persist, but we should release them from knownItems
        event.chunk.entities.forEach { e ->
            if (e is org.bukkit.entity.Item) {
                getUniqueId(e.itemStack)?.let { cleanupKnownItems(it) }
            }
        }
    }

    // ========== PERIODIC SCANNING ==========
    fun startPeriodicScan() {
        val interval = plugin.config.getLong("scan-interval", 200L).coerceAtLeast(20L)
        plugin.server.scheduler.runTaskTimerAsynchronously(plugin, Runnable {
            // Snapshot players on async thread to avoid concurrent modification
            val players = plugin.server.onlinePlayers.toList()
            players.chunked(10).forEach { batch ->
                batch.forEach { player ->
                    try {
                        scanPlayerInventory(player)
                    } catch (e: Exception) {
                        plugin.logger.warning("Error scanning inventory for ${player.name}: ${e.message}")
                    }
                }
            }
        }, interval, interval)
    }

    // ========== ITEM HISTORY - SCAN ==========
    fun startKnownItemsCleanup() {
        val period = 20L * 60 // every 60s
        plugin.server.scheduler.runTaskTimer(plugin, Runnable {
            val ttlMs = plugin.config.getLong("known-items-ttl-ms", 600_000L).coerceAtLeast(60_000L)
            val cutoff = System.currentTimeMillis() - ttlMs
            knownItems.entries.removeIf { it.value.lastSeenMs < cutoff }
            // Also clean up old alert timestamps to prevent memory leak
            lastAlertTs.entries.removeIf { (uuid, ts) ->
                ts < cutoff || !knownItems.containsKey(uuid)
            }
        }, period, period)
    }

    // ========== PLAYER INVENTORY - SCAN ==========
    fun scanPlayerInventory(player: Player) {
        // main inventory
        player.inventory.contents.filterNotNull().forEach { item ->
            if (ItemIdUtil.getId(plugin, item) == null) {
                ItemIdUtil.ensureUniqueId(plugin, item)?.let { db.recordSeenAsync(it) }
            } else {
                getUniqueId(item)?.let { checkForDuplicates(it, player) }
            }
        }
        // armor
        player.inventory.armorContents.filterNotNull().forEach { item ->
            if (ItemIdUtil.getId(plugin, item) == null) {
                ItemIdUtil.ensureUniqueId(plugin, item)?.let { db.recordSeenAsync(it) }
            }
        }
        // offhand
        val off = player.inventory.itemInOffHand
        if (!off.type.isAir && ItemIdUtil.getId(plugin, off) == null) {
            ItemIdUtil.ensureUniqueId(plugin, off)?.let { db.recordSeenAsync(it) }
        }
        // ender chest
        player.enderChest.contents.filterNotNull().forEach { item ->
            if (ItemIdUtil.getId(plugin, item) == null) {
                ItemIdUtil.ensureUniqueId(plugin, item)?.let { db.recordSeenAsync(it) }
            }
        }
    }

    // ========== DUPLICATE DETECTION ==========
    private fun checkForDuplicates(itemUUID: String, player: Player, tags: Set<String> = emptySet()) {
        val now = System.currentTimeMillis()
        val graceMs = plugin.config.getLong("movement-grace-ms", 750L).coerceAtLeast(0L)
        // If this duplicate context is from creative and allowed, just map and exit
        if (tags.contains("CREATIVE") && plugin.config.getBoolean("allow-creative-duplicates", true)) {
            val existing = knownItems[itemUUID]
            val first = existing?.firstSeenMs ?: now
            knownItems[itemUUID] = ItemLocation(player.uniqueId, locationString(player.location), lastSeenMs = now, firstSeenMs = first)
            return
        }
        val current = ItemLocation(player.uniqueId, locationString(player.location), lastSeenMs = now, firstSeenMs = now)
        val existing = knownItems.putIfAbsent(itemUUID, current) ?: return

        // Same holder: refresh timestamp/location
        if (existing.playerUUID == current.playerUUID) {
            knownItems[itemUUID] = existing.copy(location = current.location, lastSeenMs = now)
            return
        }

        // Movement grace: if the last holder was seen very recently, treat as legitimate move
        if (now - existing.lastSeenMs <= graceMs) {
            knownItems[itemUUID] = current
            return
        }

        fun proceedRemoval(keepKnown: Boolean) {
            val debounceMs = plugin.config.getLong("duplicate-alert-debounce-ms", 2000L).coerceAtLeast(0L)
            val last = lastAlertTs[itemUUID] ?: 0L
            val tagSuffix = if (tags.isNotEmpty()) " [${tags.joinToString(",")}]" else ""
            if (now - last >= debounceMs) {
                plugin.logger.warning("DUPLICATE DETECTED$tagSuffix: Item $itemUUID found in multiple locations! Known: $existing New: $current")
                if (plugin.config.getBoolean("alert-admins", true)) {
                    plugin.server.onlinePlayers
                        .filter { it.hasPermission("dupetrace.alerts") }
                        .forEach { it.sendMessage("§c[DupeTrace] §fItem $itemUUID duplicated$tagSuffix! Player: ${player.name}") }
                }

                // Send Discord webhook if enabled
                if (plugin.config.getBoolean("discord.enabled", false)) {
                    // Try to get item type from the player's inventory
                    val itemType = player.inventory.contents
                        .filterNotNull()
                        .firstOrNull { ItemIdUtil.getId(plugin, it)?.toString() == itemUUID }
                        ?.type?.name ?: "Unknown"

                    discordWebhook.sendDuplicateAlert(
                        itemUUID,
                        player.name,
                        itemType,
                        current.location,
                        tags
                    )
                }

                lastAlertTs[itemUUID] = now
            }

            if (plugin.config.getBoolean("auto-remove-duplicates", false)) {
                if (keepKnown) {
                    removeDuplicateFromPlayer(player, itemUUID)
                } else {
                    val target = plugin.server.getPlayer(existing.playerUUID)
                    if (target != null) {
                        removeDuplicateFromPlayer(target, itemUUID)
                    } else {
                        plugin.logger.warning("[DupeTrace] Intended keeper ${existing.playerUUID} is offline; removing from ${player.name} instead.")
                        removeDuplicateFromPlayer(player, itemUUID)
                    }
                }
            }
            // Update mapping to presumed keeper
            knownItems[itemUUID] = if (keepKnown) existing.copy(lastSeenMs = now) else current
        }

        if (plugin.config.getBoolean("keep-oldest-on-dup-remove", true)) {
            // Prefer in-memory firstSeenMs; fall back to DB if equal or missing
            val inMemoryKeepKnown = existing.firstSeenMs <= current.firstSeenMs
            if (existing.firstSeenMs != current.firstSeenMs) {
                proceedRemoval(inMemoryKeepKnown)
            } else {
                plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
                    val knownTs = runCatching { db.getEarliestTransferTs(itemUUID, existing.playerUUID) }.getOrNull()
                    val currentTs = runCatching { db.getEarliestTransferTs(itemUUID, player.uniqueId) }.getOrNull()
                    val keepKnown = when {
                        knownTs == null && currentTs == null -> true
                        knownTs == null -> false
                        currentTs == null -> true
                        else -> knownTs <= currentTs
                    }
                    plugin.server.scheduler.runTask(plugin, Runnable { proceedRemoval(keepKnown) })
                })
            }
        } else {
            // Legacy behavior: remove from the player we just checked
            proceedRemoval(false)
        }
    }

    // ========== DUPLICATE DETECTED - REMOVE ==========
    private fun removeDuplicateFromPlayer(player: Player, itemUUID: String) {
        val inv = player.inventory
        var found = 0
        inv.contents.forEachIndexed { index, item ->
            if (item == null) return@forEachIndexed
            val id = getUniqueId(item)
            if (id == itemUUID) {
                found++
                if (found > 1) {
                    inv.setItem(index, null)
                }
            }
        }
        if (found <= 1) {
            plugin.logger.info("[DupeTrace] No extra copies of $itemUUID found in ${player.name}'s inventory to remove.")
        } else {
            db.logItemTransferAsync(itemUUID, player.uniqueId, "AUTO_REMOVE_DUPLICATE", locationString(player.location))
        }
        // Update in-memory mapping to reflect the current owner (if any)
        if (found >= 1) {
            knownItems[itemUUID] = ItemLocation(player.uniqueId, locationString(player.location), System.currentTimeMillis())
        } else {
            cleanupKnownItems(itemUUID)
        }
    }
}
