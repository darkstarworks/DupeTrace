# Event System 📡

DupeTrace monitors **dozens of Bukkit events** to track item movement across your server. If an item can move, we're watching it. Let's break down every event we track and why.

---

## Event Categories

We track events in these categories:

1. **Creation Events** – Items being crafted, enchanted, brewed, etc.
2. **Acquisition Events** – Items picked up, fished, looted, etc.
3. **Inventory Events** – Items clicked, dragged, swapped in inventories
4. **Container Events** – Items moved to/from chests, hoppers, etc.
5. **Drop & Death Events** – Items dropped or lost on death
6. **Block & Entity Events** – Items in item frames, armor stands, etc.
7. **World Events** – Chunk unloads, loot generation

---

## Creation Events

These events track items being **created** or **modified**.

### `CraftItemEvent`
**Fired When:** Player crafts an item
**What We Track:** The crafted result
**Action Logged:** `CRAFTED`

```kotlin
@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
fun onCraft(event: CraftItemEvent) {
    val player = event.whoClicked as? Player ?: return
    val result = event.currentItem ?: event.inventory.result ?: return
    tagAndLog(player, result, "CRAFTED", player.location)
}
```

**Why This Matters:** Crafted items need UUIDs immediately. If a dupe glitch involves crafting, we catch it.

---

### `EnchantItemEvent`
**Fired When:** Player enchants an item at an enchanting table
**What We Track:** The enchanted item
**Action Logged:** `ENCHANTED`

```kotlin
@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
fun onEnchant(event: EnchantItemEvent) {
    tagAndLog(event.enchanter, event.item, "ENCHANTED", event.enchanter.location)
}
```

**Edge Case:** Enchanting doesn't create a new item, but we track it to log the player interaction.

---

### `BrewEvent`
**Fired When:** Brewing stand finishes brewing
**What We Track:** All items in the brewing stand inventory
**Action Logged:** None (just ensures UUID exists)

```kotlin
@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
fun onBrew(event: BrewEvent) {
    val inv = event.contents
    for (i in 0 until inv.size) {
        val item = inv.getItem(i) ?: continue
        ItemIdUtil.ensureUniqueId(plugin, item)?.let { db.recordSeenAsync(it) }
    }
}
```

**Note:** Potions are usually stackable, so most won't get UUIDs. But some modded servers have non-stackable potions.

---

### `CrafterCraftEvent` (1.21+)
**Fired When:** Crafter block (new in 1.21) crafts an item
**What We Track:** The crafted result
**Action Logged:** None (just ensures UUID exists)

```kotlin
@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
fun onCrafterCraft(event: org.bukkit.event.block.CrafterCraftEvent) {
    val result = event.result
    ItemIdUtil.ensureUniqueId(plugin, result)?.let { db.recordSeenAsync(it) }
}
```

**Why 1.21+:** The Crafter block was added in Minecraft 1.21. Older versions don't have this event.

---

### `PrepareSmithingEvent`
**Fired When:** Smithing table prepares a result
**What We Track:** The result item
**Action Logged:** None (just ensures UUID exists)

```kotlin
@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
fun onSmithing(event: PrepareSmithingEvent) {
    val result = event.inventory.result ?: return
    ItemIdUtil.ensureUniqueId(plugin, result)?.let { db.recordSeenAsync(it) }
}
```

**Edge Case:** Smithing modifies existing items (e.g., upgrading diamond to netherite). We track the result.

---

### `LootGenerateEvent`
**Fired When:** Loot is generated (chests, mob drops, fishing)
**What We Track:** All loot items
**Action Logged:** None (just ensures UUIDs exist)

```kotlin
@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
fun onLootGenerate(event: LootGenerateEvent) {
    event.loot.forEach { item ->
        ItemIdUtil.ensureUniqueId(plugin, item)?.let { db.recordSeenAsync(it) }
    }
}
```

**Why This Matters:** Loot tables can generate non-stackable items (enchanted books, tools). We tag them before players see them.

---

### `VillagerAcquireTradeEvent`
**Fired When:** Villager acquires a new trade
**What We Track:** The trade result item
**Action Logged:** None (just ensures UUID exists)

```kotlin
@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
fun onVillagerAcquireTrade(event: VillagerAcquireTradeEvent) {
    val old = event.recipe
    val result = old.result.clone()
    ItemIdUtil.ensureUniqueId(plugin, result)?.let { db.recordSeenAsync(it) }
    // ... recreate recipe with tagged result
}
```

**Why Clone?** We need to modify the result item (add UUID), so we clone it and rebuild the trade recipe.

---

## Acquisition Events

These events track items being **acquired** by players.

### `EntityPickupItemEvent`
**Fired When:** Player picks up an item from the ground
**What We Track:** The picked-up item
**Action Logged:** `PICKUP`

```kotlin
@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
fun onPickup(event: EntityPickupItemEvent) {
    val player = event.entity as? Player ?: return
    tagAndLog(player, event.item.itemStack, "PICKUP", event.item.location)
}
```

**Why This Matters:** Most dupe glitches involve dropping and picking up items. We log every pickup with player + location.

---

### `PlayerFishEvent`
**Fired When:** Player fishes and catches something
**What We Track:** The caught item
**Action Logged:** `FISHED`

```kotlin
@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
fun onFish(event: PlayerFishEvent) {
    if (event.state != PlayerFishEvent.State.CAUGHT_FISH) return
    val item = (event.caught as? org.bukkit.entity.Item)?.itemStack ?: return
    tagAndLog(event.player, item, "FISHED", event.player.location)
}
```

**Edge Case:** Only fires on `CAUGHT_FISH` state (not bites, reels, or fails).

---

### `EntityDeathEvent`
**Fired When:** Entity (mob, player, animal) dies
**What We Track:** All items dropped
**Action Logged:** `MOB_DROP` (if killer exists)

```kotlin
@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
fun onEntityDeath(event: EntityDeathEvent) {
    val killer = event.entity.killer
    event.drops.forEach { item ->
        if (killer != null) tagAndLog(killer, item, "MOB_DROP", event.entity.location)
        else ItemIdUtil.ensureUniqueId(plugin, item)?.let { db.recordSeenAsync(it) }
    }
}
```

**Why Killer Check?** If a player killed the mob, we associate the loot with them. Otherwise, just tag it.

---

### `BlockBreakEvent`
**Fired When:** Player breaks a block
**What We Track:** Block drops + any items in container blocks
**Action Logged:** `BLOCK_BREAK`

```kotlin
@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
fun onBlockBreak(event: BlockBreakEvent) {
    val player = event.player
    event.block.getDrops(player.inventory.itemInMainHand, player).forEach { item ->
        tagAndLog(player, item, "BLOCK_BREAK", event.block.location)
    }
    // Tag items in containers (if chest is broken)
    val state = event.block.state
    if (state is Container) {
        state.inventory.contents.filterNotNull().forEach { item ->
            ItemIdUtil.ensureUniqueId(plugin, item)?.let { db.recordSeenAsync(it) }
        }
    }
}
```

**Why Container Check?** If a player breaks a chest without opening it, we still need to tag the items inside.

---

### `PlayerShearEntityEvent`
**Fired When:** Player shears a sheep, mooshroom, etc.
**What We Track:** Player's inventory (scanned next tick)
**Action Logged:** None (inventory scan handles it)

```kotlin
@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
fun onShear(event: PlayerShearEntityEvent) {
    plugin.server.scheduler.runTask(plugin, Runnable {
        scanPlayerInventory(event.player)
    })
}
```

**Why Next Tick?** Sheared items aren't added to inventory immediately. We wait 1 tick for the items to appear.

---

## Inventory Events

These events track item **movement within inventories**.

### `InventoryClickEvent`
**Fired When:** Player clicks an item in any inventory
**What We Track:** Current item, cursor item, shift-clicked items
**Action Logged:** `INVENTORY_CLICK_CURRENT`, `INVENTORY_CLICK_CURSOR`, `INVENTORY_SHIFT_CLICK`

```kotlin
@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
fun onInventoryClick(event: InventoryClickEvent) {
    val player = event.whoClicked as? Player ?: return
    event.currentItem?.let { tagAndLog(player, it, "INVENTORY_CLICK_CURRENT", player.location) }
    tagAndLog(player, event.cursor, "INVENTORY_CLICK_CURSOR", player.location)
    if (event.isShiftClick) {
        event.clickedInventory?.contents?.filterNotNull()?.forEach {
            tagAndLog(player, it, "INVENTORY_SHIFT_CLICK", player.location)
        }
    }
}
```

**Why Three Logs?** Clicks can involve multiple items (current slot, cursor, and shift-click moves entire stacks).

---

### `InventoryDragEvent`
**Fired When:** Player drags items across multiple slots
**What We Track:** New items placed, old cursor
**Action Logged:** `INVENTORY_DRAG`, `INVENTORY_OLD_CURSOR`

```kotlin
@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
fun onInventoryDrag(event: InventoryDragEvent) {
    val player = event.whoClicked as? Player ?: return
    event.newItems.values.forEach { tagAndLog(player, it, "INVENTORY_DRAG", player.location) }
    tagAndLog(player, event.oldCursor, "INVENTORY_OLD_CURSOR", player.location)
}
```

**Edge Case:** Dragging can split stacks. We track all affected items.

---

### `InventoryCreativeEvent`
**Fired When:** Creative mode player spawns or duplicates items
**What We Track:** Current item, cursor
**Action Logged:** `CREATIVE_INVENTORY`
**Special:** Passes `"CREATIVE"` tag to duplicate checker

```kotlin
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
```

**Why Special Handling?** Creative mode allows intentional duplication. The `"CREATIVE"` tag tells the duplicate checker to honor the `allow-creative-duplicates` config setting.

---

### `InventoryOpenEvent`
**Fired When:** Player opens any inventory (chest, furnace, etc.)
**What We Track:** All items in the opened inventory
**Action Logged:** None (just ensures UUIDs exist)

Only runs if `inventory-open-scan-enabled: true` in config.

---

### `PrepareAnvilEvent`
**Fired When:** Anvil prepares a result
**What We Track:** Result item, input items
**Action Logged:** None (just ensures UUIDs exist)

**Special Handling:** Anvils can "consume" input items. We track the UUIDs before and after to detect if items were duplicated.

---

## Drop & Death Events

### `PlayerDropItemEvent`
**Fired When:** Player drops an item (Q key)
**What We Track:** The dropped item
**Action Logged:** `DROP`

```kotlin
@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
fun onDrop(event: PlayerDropItemEvent) {
    tagAndLog(event.player, event.itemDrop.itemStack, "DROP", event.itemDrop.location)
}
```

---

### `PlayerDeathEvent`
**Fired When:** Player dies
**What We Track:** Items dropped on death + items kept (if keepInventory is enabled)
**Action Logged:** `DEATH`

```kotlin
@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
fun onPlayerDeath(event: PlayerDeathEvent) {
    val player = event.entity
    event.drops.forEach { tagAndLog(player, it, "DEATH", player.location) }

    // If keepInventory, scan what's kept
    if (event.keepInventory) {
        plugin.server.scheduler.runTask(plugin, Runnable {
            scanPlayerInventory(player)
        })
    }
}
```

**Why Next Tick for keepInventory?** Items are restored after the event fires.

---

### `PlayerRespawnEvent`
**Fired When:** Player respawns after death
**What We Track:** Player's inventory (scanned next tick)
**Action Logged:** None (inventory scan handles it)

```kotlin
@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
fun onRespawn(event: PlayerRespawnEvent) {
    plugin.server.scheduler.runTask(plugin, Runnable {
        scanPlayerInventory(event.player)
    })
}
```

---

## Container Events

### `InventoryMoveItemEvent`
**Fired When:** Hopper, dropper, or other automation moves items
**What We Track:** The moved item
**Action Logged:** `CONTAINER_TRANSFER`

---

### `BlockDispenseEvent`
**Fired When:** Dispenser dispenses an item
**What We Track:** The dispensed item
**Action Logged:** None (just ensures UUID exists)

---

## Block & Entity Events

### `PlayerInteractEntityEvent`
**Fired When:** Player interacts with an entity (item frame, armor stand, etc.)
**What We Track:** Items in/on the entity
**Action Logged:** `ENTITY_INTERACT`

Special handling for:
- **Item Frames** – Track the item inside
- **Armor Stands** – Track armor and held items

---

### `BlockPlaceEvent`
**Fired When:** Player places a block
**What We Track:** The item being placed
**Action Logged:** `BLOCK_PLACE`

---

### `VehicleDestroyEvent`
**Fired When:** Minecart is destroyed
**What We Track:** Items in storage minecarts
**Action Logged:** None (just ensures UUIDs exist)

---

## World Events

### `ChunkUnloadEvent`
**Fired When:** Chunk unloads
**What We Track:** Cleans up in-memory cache for items in that chunk
**Action Logged:** None

**Why This Matters:** Prevents memory leaks from items in unloaded chunks.

---

## Player State Events

### `PlayerJoinEvent`
**Fired When:** Player joins the server
**What We Track:** Player's inventory (scanned next tick)
**Action Logged:** None

```kotlin
@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
fun onJoin(event: PlayerJoinEvent) {
    plugin.server.scheduler.runTask(plugin, Runnable {
        scanPlayerInventory(event.player)
    })
}
```

**Why This Matters:** Catches any items the player had when they logged off (in case they were duped offline).

---

### `PlayerQuitEvent`
**Fired When:** Player leaves the server
**What We Track:** Cleans up in-memory cache for that player
**Action Logged:** None

---

## Periodic Scanning

In addition to events, DupeTrace runs **two scheduled tasks**:

### Periodic Inventory Scanner
**Frequency:** Configured by `scan-interval` (default: 200 ticks = 10 seconds)
**What It Does:** Scans all online players' inventories for duplicate UUIDs

**Why We Need This:** Event-driven tracking can miss edge cases (lag, creative mode exploits, plugin conflicts). Periodic scanning is the safety net.

---

### Known Items Cleanup Task
**Frequency:** Every 5 minutes
**What It Does:** Removes stale entries from the in-memory `knownItems` cache

**Why We Need This:** Prevents memory leaks. Items not seen in X minutes (config: `known-items-ttl-ms`) are removed from the cache.

---

## Event Priority: `MONITOR`

All DupeTrace event handlers use **`EventPriority.MONITOR`**:

```kotlin
@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
```

**What This Means:**
- Handlers run **last** (after all other plugins)
- We observe events **after they're finalized**
- We **never modify** event behavior
- `ignoreCancelled = true` means we skip cancelled events

**Why MONITOR?** We're passive observers, not active modifiers. Running last ensures we see the final state of items.

---

## Thread Safety Notes

- **Event handlers** run on the **main server thread** (safe to modify Bukkit state)
- **Database operations** run **asynchronously** (non-blocking)
- **Periodic scanners** run **asynchronously** (non-blocking)

**Important:** Never modify inventories or entities from async threads! Use `runTask()` to schedule back to main thread if needed.

---

## Performance Characteristics

### Events per Second (Busy Server)

| Event Type | Estimated Frequency |
|------------|---------------------|
| InventoryClickEvent | 100-500/sec |
| EntityPickupItemEvent | 50-200/sec |
| PlayerDropItemEvent | 20-100/sec |
| CraftItemEvent | 10-50/sec |
| InventoryDragEvent | 5-20/sec |
| **TOTAL** | **~200-1000 events/sec** |

**Memory Impact:** Each event logs to database (async) and updates in-memory cache (fast).

**CPU Impact:** Minimal. Most handlers just tag items and return. Database writes happen off-thread.

---

## What's Next?

You've mastered the event system! Continue your journey:

- [Architecture Overview ←](architecture.md) – How everything fits together
- [Core Functions ←](core-functions.md) – Deep dive into key methods
- [Database Schema ←](database-schema.md) – Tables and queries

Ready to contribute? Check out the [GitHub repo](https://github.com/darkstarworks/DupeTrace) and open a PR!
