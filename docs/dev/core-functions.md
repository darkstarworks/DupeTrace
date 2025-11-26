# Core Functions 🔧

Let's crack open the codebase and explore the essential functions that make DupeTrace tick. This is where the magic happens.

---

## ItemIdUtil – UUID Tagging

**File:** `src/main/kotlin/io/github/darkstarworks/dupeTrace/util/ItemIdUtil.kt`

### `ensureUniqueId(plugin: JavaPlugin, item: ItemStack?): UUID?`

The heart of item tracking. This function either retrieves or creates a UUID for an item.

**Source (simplified):**
```kotlin
fun ensureUniqueId(plugin: JavaPlugin, item: ItemStack?): UUID? {
    if (item == null) return null
    // Only non-stackable items get UUIDs
    if (item.type.maxStackSize != 1) return null

    val meta = item.itemMeta ?: return null
    val container = meta.persistentDataContainer
    val existing = container.get(key(plugin), PersistentDataType.STRING)

    // Parse existing UUID or generate a new one
    val id = if (existing.isNullOrBlank()) {
        UUID.randomUUID()
    } else {
        runCatching { UUID.fromString(existing) }.getOrNull() ?: UUID.randomUUID()
    }

    // Store UUID in item's NBT data
    container.set(key(plugin), PersistentDataType.STRING, id.toString())
    item.itemMeta = meta
    return id
}
```

**What's Happening:**
1. Check if the item is non-stackable (`maxStackSize == 1`)
2. Look for an existing UUID in the PersistentDataContainer
3. If found, parse it; if invalid or missing, generate a new one
4. Store the UUID back into the item's NBT
5. Return the UUID (or null if the item is stackable/null)

**Why This Works:**
- PersistentDataContainer survives **everything**: server restarts, player trades, chunk unloads
- UUIDs are invisible to players (no lore spam)
- Works on all non-stackable items: tools, armor, weapons, etc.

**Edge Cases:**
- Stackable items (dirt, cobblestone) → returns `null` (can't be tracked)
- Items without meta (air, barriers) → returns `null`
- Corrupted UUID strings → generates a fresh UUID

---

### `getId(plugin: JavaPlugin, item: ItemStack?): UUID?`

Read-only version of `ensureUniqueId()`. Retrieves the UUID without modifying the item.

**Source (simplified):**
```kotlin
fun getId(plugin: JavaPlugin, item: ItemStack?): UUID? {
    if (item == null) return null
    if (item.type.maxStackSize != 1) return null
    val meta = item.itemMeta ?: return null
    val value = meta.persistentDataContainer.get(key(plugin), PersistentDataType.STRING) ?: return null
    return runCatching { UUID.fromString(value) }.getOrNull()
}
```

**Use Cases:**
- Checking if an item is already tracked (without assigning a new UUID)
- Reading UUIDs for comparison in duplicate detection
- Command handlers that just need to display the UUID

---

## DatabaseManager – Persistence Layer

**File:** `src/main/kotlin/io/github/darkstarworks/dupeTrace/db/DatabaseManager.kt`

### `recordSeen(id: UUID): Boolean`

Records that an item UUID has been seen. Returns `true` if this is the **first time** seeing this UUID (new item), `false` if it already existed (potential duplicate).

**Source (simplified):**
```kotlin
fun recordSeen(id: UUID): Boolean {
    val sql = "INSERT INTO dupetrace_items(id) VALUES (?) ON CONFLICT (id) DO NOTHING"
    connection().use { conn ->
        conn.prepareStatement(sql).use { ps ->
            ps.setObject(1, id)
            return try {
                val updated = ps.executeUpdate()
                updated == 1  // 1 = inserted, 0 = already existed
            } catch (e: SQLException) {
                plugin.logger.warning("DB error while recording UUID $id: ${e.message}")
                false
            }
        }
    }
}
```

**How It Works:**
- Uses PostgreSQL's `ON CONFLICT DO NOTHING` to avoid duplicate key errors
- If the UUID already exists, the insert is silently skipped (0 rows affected)
- If the UUID is new, it's inserted (1 row affected)

**Return Values:**
- `true` → New item (first time seeing this UUID)
- `false` → Existing item (UUID already in database)

**Why This Matters:**
The return value tells us whether an item is legitimately new or suspiciously appearing again (possible duplicate).

---

### `logItemTransfer(itemUUID: String, playerUUID: UUID, action: String, location: String)`

Logs every interaction with a tracked item.

**Source (simplified):**
```kotlin
fun logItemTransfer(itemUUID: String, playerUUID: UUID, action: String, location: String) {
    val uuid = runCatching { UUID.fromString(itemUUID) }.getOrNull() ?: return
    val sql = "INSERT INTO dupetrace_item_transfers (item_uuid, player_uuid, action, location) VALUES (?,?,?,?)"
    connection().use { conn ->
        conn.prepareStatement(sql).use { ps ->
            ps.setObject(1, uuid)
            ps.setObject(2, playerUUID)
            ps.setString(3, action)
            ps.setString(4, location)
            try {
                ps.executeUpdate()
            } catch (e: SQLException) {
                plugin.logger.warning("DB error while logging transfer for $uuid: ${e.message}")
            }
        }
    }
}
```

**Action Types:**
- `PICKUP` – Player picked up from ground
- `DROP` – Player dropped item
- `CRAFTED` – Item was crafted
- `INVENTORY_CLICK` – Item clicked in inventory
- `CONTAINER_TRANSFER` – Item moved to/from chest
- `DEATH` – Player died with item
- `ENCHANT` – Item was enchanted
- And many more (see ActivityListener.kt for full list)

**Location Format:**
`world:x,y,z` (e.g., `world:123,64,-456`)

---

### `getItemTransferHistory(itemUUID: String, limit: Int = 100): List<TransferRecord>`

Retrieves the full transfer history for an item, sorted by most recent first.

**Source (simplified):**
```kotlin
fun getItemTransferHistory(itemUUID: String, limit: Int = 100): List<TransferRecord> {
    val uuid = runCatching { UUID.fromString(itemUUID) }.getOrNull() ?: return emptyList()
    val sql = "SELECT player_uuid, action, location, ts FROM dupetrace_item_transfers WHERE item_uuid = ? ORDER BY ts DESC LIMIT ?"
    val records = mutableListOf<TransferRecord>()
    connection().use { conn ->
        conn.prepareStatement(sql).use { ps ->
            ps.setObject(1, uuid)
            ps.setInt(2, limit)
            ps.executeQuery().use { rs ->
                while (rs.next()) {
                    records.add(
                        TransferRecord(
                            playerUUID = rs.getObject(1, UUID::class.java),
                            action = rs.getString(2),
                            location = rs.getString(3),
                            timestamp = rs.getTimestamp(4).time
                        )
                    )
                }
            }
        }
    }
    return records
}
```

**Returns:**
```kotlin
data class TransferRecord(
    val playerUUID: UUID,
    val action: String,
    val location: String,
    val timestamp: Long  // Unix epoch milliseconds
)
```

**Used By:**
- `/dupetest uuid` command (shows last 10 transfers)
- `/dupetest history` command (shows custom limit)
- Forensic investigations

---

### `getEarliestTransferTs(itemUUID: String, playerUUID: UUID): Long?`

Returns the **earliest timestamp** when a specific player interacted with an item. Used to determine which player had the item first (for auto-removal logic).

**Source (simplified):**
```kotlin
fun getEarliestTransferTs(itemUUID: String, playerUUID: UUID): Long? {
    val uuid = runCatching { UUID.fromString(itemUUID) }.getOrNull() ?: return null
    val sql = "SELECT MIN(ts) FROM dupetrace_item_transfers WHERE item_uuid = ? AND player_uuid = ?"
    connection().use { conn ->
        conn.prepareStatement(sql).use { ps ->
            ps.setObject(1, uuid)
            ps.setObject(2, playerUUID)
            ps.executeQuery().use { rs ->
                if (rs.next()) {
                    return rs.getTimestamp(1)?.time
                }
            }
        }
    }
    return null
}
```

**Returns:**
- `Long` – Unix epoch milliseconds of the earliest interaction
- `null` – No transfers found for this player/item combo

**Used By:**
- Auto-removal logic when `keep-oldest-on-dup-remove: true`

---

## ActivityListener – Event Tracking & Duplicate Detection

**File:** `src/main/kotlin/io/github/darkstarworks/dupeTrace/listener/ActivityListener.kt`

### `tagAndLog(player: Player, item: ItemStack, action: String, loc: Location)`

Central function called by most event handlers. Tags an item with a UUID and logs the interaction.

**Source (simplified):**
```kotlin
private fun tagAndLog(player: Player, item: ItemStack, action: String, loc: Location) {
    val wasNew = ItemIdUtil.getId(plugin, item) == null
    val id = ItemIdUtil.ensureUniqueId(plugin, item) ?: return

    // Tag Shulker Box and Bundle contents recursively
    deepTagShulkerContentsIfAny(item)
    deepTagBundleContentsIfAny(item)

    db.recordSeenAsync(id)
    db.logItemTransferAsync(id.toString(), player.uniqueId, action, locationString(loc))

    if (wasNew) {
        sendItemAssignedMessage(player, item, id)
    }

    checkForDuplicates(id.toString(), player)
}
```

**Flow:**
1. Check if item already has a UUID
2. Ensure UUID is assigned
3. Recursively tag nested containers (Shulker Boxes, Bundles)
4. Record in database (async)
5. Log transfer (async)
6. If new item, notify watchers
7. **Check for duplicates** (the critical part!)

---

### `checkForDuplicates(itemId: String, currentPlayer: Player)`

The duplicate detection algorithm. Compares the current item location with the in-memory cache.

**Conceptual Flow:**
```kotlin
private fun checkForDuplicates(itemId: String, currentPlayer: Player) {
    val now = System.currentTimeMillis()
    val currentLoc = locationString(currentPlayer.location)

    // Check if item is already known
    val known = knownItems[itemId]

    if (known != null) {
        val timeSinceLastSeen = now - known.lastSeenMs
        val gracePeriod = config.getLong("movement-grace-ms", 750L)

        // If outside grace period AND different player/location → DUPLICATE
        if (timeSinceLastSeen > gracePeriod &&
            (known.playerUUID != currentPlayer.uniqueId || known.location != currentLoc)) {

            // DUPLICATE DETECTED!
            alertDuplicate(itemId, known, currentPlayer)

            if (config.getBoolean("auto-remove-duplicates", true)) {
                removeDuplicate(itemId, known, currentPlayer)
            }
        }
    }

    // Update known location
    knownItems[itemId] = ItemLocation(
        playerUUID = currentPlayer.uniqueId,
        location = currentLoc,
        lastSeenMs = now,
        firstSeenMs = known?.firstSeenMs ?: now
    )
}
```

**Grace Period Logic:**
The `movement-grace-ms` setting prevents false positives during legitimate item transfers:

- Player picks up item → tracked at location A
- 100ms later, item appears in their inventory → same UUID, location B
- Without grace period → FALSE POSITIVE (looks like a dupe)
- With 750ms grace period → Ignored (within movement window)

**This is why tuning `movement-grace-ms` is critical!**

---

### `deepTagShulkerContentsIfAny(item: ItemStack)`

Recursively tags items inside Shulker Boxes. Dupers love to hide duped items in nested containers, so we track everything.

**Source (simplified):**
```kotlin
private fun deepTagShulkerContentsIfAny(item: ItemStack) {
    val meta = item.itemMeta ?: return
    if (meta is BlockStateMeta) {
        val bs = meta.blockState
        if (bs is ShulkerBox) {
            val inv = bs.inventory
            for (i in 0 until inv.size) {
                val inner = inv.getItem(i) ?: continue
                val innerId = ItemIdUtil.ensureUniqueId(plugin, inner) ?: continue
                db.recordSeenAsync(innerId)
                inv.setItem(i, inner)
            }
            meta.blockState = bs
            item.itemMeta = meta
        }
    }
}
```

**Why This Matters:**
Without deep scanning, a duper could:
1. Duplicate a Shulker Box full of items
2. Items inside would have the **same UUIDs**
3. DupeTrace would only see the Shulker's UUID, missing the inner dupes

Deep scanning prevents this exploit.

---

### `startPeriodicScan()`

Starts a background task that periodically scans all online players' inventories for duplicates.

**Source (simplified):**
```kotlin
fun startPeriodicScan() {
    val scanInterval = config.getLong("scan-interval", 200L)
    plugin.server.scheduler.runTaskTimerAsynchronously(plugin, Runnable {
        plugin.server.onlinePlayers.forEach { player ->
            scanInventory(player)
        }
    }, scanInterval, scanInterval)
}
```

**Why We Need This:**
Event tracking catches *most* duplicates, but some edge cases (like server lag, chunk loading, or creative mode exploits) can slip through. The periodic scanner acts as a **safety net**.

---

### `startKnownItemsCleanup()`

Starts a background task that removes stale entries from the in-memory cache.

**Source (simplified):**
```kotlin
fun startKnownItemsCleanup() {
    val ttl = config.getLong("known-items-ttl-ms", 600_000L)
    plugin.server.scheduler.runTaskTimerAsynchronously(plugin, Runnable {
        val now = System.currentTimeMillis()
        knownItems.entries.removeIf { now - it.value.lastSeenMs > ttl }
    }, 6000L, 6000L)  // Run every 5 minutes
}
```

**Why We Need This:**
Without cleanup, the `knownItems` map would grow forever, eventually causing memory issues. Items that haven't been seen in 10 minutes (default TTL) are removed from memory.

**Note:** Items are still tracked in the **database** – this only affects the in-memory cache used for duplicate detection.

---

## Discord Integration

**File:** `src/main/kotlin/io/github/darkstarworks/dupeTrace/webhook/DiscordWebhook.kt`

### `sendDuplicateAlert(itemId: String, players: List<String>, locations: List<String>)`

Sends a formatted alert to Discord when a duplicate is detected.

**Example Webhook Payload:**
```json
{
  "content": "🚨 **DUPLICATE DETECTED**",
  "embeds": [{
    "title": "Item Duplication Alert",
    "color": 16711680,
    "fields": [
      {"name": "Item UUID", "value": "8f3c5a1d-2b4e-4f9a-8c7d-1e5b9a3f2c8d"},
      {"name": "Players", "value": "Steve, Alex"},
      {"name": "Locations", "value": "world: 123,64,-456\nworld_nether: -50,70,100"}
    ],
    "timestamp": "2025-10-23T14:32:18Z"
  }]
}
```

Pretty neat for getting real-time alerts in your staff Discord channel!

---

## Performance Optimization Tips

### Use Async for Database Operations
```kotlin
// BAD (blocks main thread)
db.recordSeen(id)

// GOOD (async)
db.recordSeenAsync(id)
```

### Use ConcurrentHashMap for Shared State
```kotlin
// BAD (not thread-safe)
private val knownItems = HashMap<String, ItemLocation>()

// GOOD (thread-safe)
private val knownItems = ConcurrentHashMap<String, ItemLocation>()
```

### Batch Database Operations When Possible
Instead of 100 individual inserts, consider batching (though DupeTrace currently uses individual inserts for simplicity).

---

## What's Next?

Explore the data layer:

- [Database Schema →](database-schema.md) – Tables, indexes, and query patterns
- [Event System →](event-system.md) – Complete list of tracked events

Got questions about a specific function? Check the inline KDoc comments in the source code or ask on Discord!
