# Plugin Architecture 🏗️

Welcome to the technical deep-dive! If you're here, you probably want to understand how DupeTrace works under the hood, contribute code, or build something similar. Let's break it down.

## Project Structure

```
DupeTrace/
├── src/main/kotlin/io/github/darkstarworks/dupeTrace/
│   ├── DupeTrace.kt                 # Main plugin class
│   ├── command/
│   │   └── DupeTestCommand.kt       # Admin command handler
│   ├── db/
│   │   └── DatabaseManager.kt       # Database operations & connection pool
│   ├── listener/
│   │   ├── ActivityListener.kt      # Comprehensive event tracking
│   │   └── InventoryScanListener.kt # Inventory scan on open
│   ├── util/
│   │   └── ItemIdUtil.kt            # UUID tagging utilities
│   └── webhook/
│       └── DiscordWebhook.kt        # Discord integration
├── src/main/resources/
│   ├── config.yml                   # Default configuration
│   └── plugin.yml                   # Plugin metadata
└── build.gradle.kts                 # Gradle build script
```

## Tech Stack

| Component | Technology |
|-----------|-----------|
| **Language** | Kotlin 1.9+ (targeting JVM 21) |
| **Server Platform** | PaperMC 1.21.1+ |
| **Database** | PostgreSQL 12+ |
| **Connection Pool** | HikariCP 5.1.0 |
| **Build Tool** | Gradle 8.8 |
| **Dependency Management** | Gradle Kotlin DSL |

## Core Architecture Concepts

### 1. **Item Tagging via PersistentDataContainer**

Every non-stackable item (tools, armor, weapons) gets a unique UUID stored directly in its NBT data using Bukkit's `PersistentDataContainer` API.

```kotlin
// Simplified version from ItemIdUtil.kt
val meta = item.itemMeta ?: return null
val container = meta.persistentDataContainer
container.set(key(plugin), PersistentDataType.STRING, uuid.toString())
item.itemMeta = meta
```

**Why PersistentDataContainer?**
- Survives server restarts
- Survives item transfers between players
- Survives chunk unloads
- Not visible to players (no lore clutter)

---

### 2. **Event-Driven Tracking**

DupeTrace uses Bukkit's event system to monitor **every possible way** an item can move:

- **Player Events**: Pickup, drop, death, respawn, join, quit
- **Inventory Events**: Click, drag, swap, craft, enchant, anvil
- **Container Events**: Chest open/close, hopper transfer
- **Block Events**: Break, place, dispense
- **Entity Events**: Item frame, armor stand
- **World Events**: Loot generation, chunk unload

Every tracked event logs to the database with:
- Item UUID
- Player UUID
- Action type
- World coordinates
- Timestamp

Check out `ActivityListener.kt` (src/main/kotlin/io/github/darkstarworks/dupeTrace/listener/ActivityListener.kt) to see all the event handlers in action.

---

### 3. **In-Memory Duplicate Detection**

DupeTrace keeps a **concurrent hash map** of "known item locations" in memory:

```kotlin
data class ItemLocation(
    val playerUUID: UUID,
    val location: String,
    val lastSeenMs: Long,
    val firstSeenMs: Long
)

private val knownItems = ConcurrentHashMap<String, ItemLocation>()
```

**How Duplicate Detection Works:**

1. When an item is seen, check if its UUID is already in `knownItems`
2. Compare timestamps: if the last-seen time is within the **movement grace period** (`movement-grace-ms`), it's probably the same item moving legitimately
3. If the grace period has passed and the item appears in a *different location* → **DUPLICATE DETECTED**

This in-memory approach is **fast** but requires careful tuning of the grace period to avoid false positives.

---

### 4. **Asynchronous Database Operations**

To avoid blocking the main Minecraft server thread (which would cause lag), all database writes happen **asynchronously**:

```kotlin
fun recordSeenAsync(id: UUID) {
    plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
        try {
            recordSeen(id)
        } catch (t: Throwable) {
            plugin.logger.warning("Async recordSeen failed for $id: ${t.message}")
        }
    })
}
```

**Why async?**
- Database queries can take 10-100ms depending on load
- Running synchronously would cause server TPS drops
- Async operations are non-blocking and can run in parallel

Database **reads** (like command queries) also run async to prevent lag.

---

### 5. **Database Connection Pooling (HikariCP)**

Instead of opening a new database connection for every query (expensive!), DupeTrace uses **HikariCP** to maintain a pool of reusable connections.

```kotlin
val hikari = HikariConfig()
hikari.jdbcUrl = cfg.getString("database.postgres.url")
hikari.username = cfg.getString("database.postgres.user")
hikari.password = cfg.getString("database.postgres.password")
hikari.maximumPoolSize = cfg.getInt("database.postgres.pool-size", 10)
dataSource = HikariDataSource(hikari)
```

**Benefits:**
- Reduced latency (connections are pre-established)
- Handles concurrent queries efficiently
- Automatic connection recycling and health checks

---

### 6. **Periodic Scanning & Memory Management**

Two scheduled tasks run in the background:

#### **Periodic Inventory Scanner** (`scan-interval`)
```kotlin
plugin.server.scheduler.runTaskTimerAsynchronously(plugin, Runnable {
    plugin.server.onlinePlayers.forEach { player ->
        scanInventory(player)
    }
}, scanInterval, scanInterval)
```

Scans all online players' inventories for duplicate UUIDs at regular intervals.

#### **Known Items Cleanup Task** (`known-items-ttl-ms`)
```kotlin
plugin.server.scheduler.runTaskTimerAsynchronously(plugin, Runnable {
    val now = System.currentTimeMillis()
    knownItems.entries.removeIf { now - it.value.lastSeenMs > ttl }
}, cleanupInterval, cleanupInterval)
```

Removes stale entries from the in-memory cache to prevent memory leaks.

---

## Lifecycle Flow

### Plugin Startup

1. **`DupeTrace.onEnable()`** is called by Paper
2. Load and validate `config.yml`
3. Initialize `DatabaseManager` and connect to PostgreSQL
4. Create database schema and indexes if they don't exist
5. Register event listeners (`ActivityListener`, `InventoryScanListener`)
6. Register commands (`/dupetest`)
7. Start periodic scanner and cleanup tasks

### Item Tracking Flow

```
┌─────────────────────────────────────────────────────┐
│ Player picks up a Diamond Sword                     │
└───────────────┬─────────────────────────────────────┘
                │
                ▼
┌─────────────────────────────────────────────────────┐
│ EntityPickupItemEvent fired                         │
└───────────────┬─────────────────────────────────────┘
                │
                ▼
┌─────────────────────────────────────────────────────┐
│ ActivityListener.onPickup() called                  │
└───────────────┬─────────────────────────────────────┘
                │
                ▼
┌─────────────────────────────────────────────────────┐
│ ItemIdUtil.ensureUniqueId() → assigns UUID          │
└───────────────┬─────────────────────────────────────┘
                │
                ▼
┌─────────────────────────────────────────────────────┐
│ DatabaseManager.recordSeenAsync() → saves to DB     │
└───────────────┬─────────────────────────────────────┘
                │
                ▼
┌─────────────────────────────────────────────────────┐
│ DatabaseManager.logItemTransferAsync() → log action │
└───────────────┬─────────────────────────────────────┘
                │
                ▼
┌─────────────────────────────────────────────────────┐
│ checkForDuplicates() → compare with knownItems map  │
└───────────────┬─────────────────────────────────────┘
                │
                ▼
        ┌───────┴────────┐
        │                │
   No Dupe          Duplicate Found!
        │                │
        ▼                ▼
    Continue       Alert admins
                   Auto-remove (if enabled)
                   Send Discord webhook
```

### Plugin Shutdown

1. **`DupeTrace.onDisable()`** is called
2. Close HikariCP connection pool
3. Cancel scheduled tasks (automatic via Bukkit)
4. Flush any pending operations

---

## Design Patterns Used

### **Singleton Pattern**
`ItemIdUtil` is an `object` (Kotlin's singleton) since it's stateless and used everywhere.

### **Repository Pattern**
`DatabaseManager` encapsulates all database logic, keeping SQL queries isolated from business logic.

### **Observer Pattern**
Event listeners observe Bukkit events and react accordingly.

### **Command Pattern**
`DupeTestCommand` implements `CommandExecutor` and `TabCompleter` for clean command handling.

---

## Thread Safety

### Concurrent Access Points

1. **knownItems HashMap** → Uses `ConcurrentHashMap` for thread-safe reads/writes
2. **Database connections** → HikariCP handles concurrent access
3. **Event handlers** → Bukkit calls handlers on the main thread (mostly safe)
4. **Async tasks** → Run on separate thread pool, must not modify Bukkit state directly

### Important Rules

✅ **DO:**
- Use `ConcurrentHashMap` for shared state
- Run database queries async
- Use `runTask()` to schedule Bukkit API calls back to main thread

❌ **DON'T:**
- Modify inventories from async threads
- Access Bukkit entities from async threads
- Use regular `HashMap` for concurrent access

---

## Performance Characteristics

### Memory Usage

| Component | Approximate Memory |
|-----------|-------------------|
| Plugin code & dependencies | ~15-20 MB |
| In-memory item cache (10k items) | ~5-10 MB |
| HikariCP connection pool (10 conns) | ~5 MB |
| **Total** | **~25-35 MB** |

Memory usage scales with the number of tracked items in the cache. The `known-items-ttl-ms` setting controls cache size.

### Database Growth

| Metric | Growth Rate |
|--------|-------------|
| Items table | ~1 row per unique item (very slow) |
| Transfers table | ~100-1000 rows per hour (active server) |

**Example:** A 50-player server might log **50,000 transfers per day**. Plan your database storage accordingly!

---

## Error Handling Philosophy

DupeTrace follows a **fail-safe** approach:

- ✅ Log errors and continue (don't crash the server)
- ✅ Validate configuration on startup
- ✅ Catch exceptions in async operations
- ✅ Use `runCatching` for safe UUID parsing
- ❌ Don't throw exceptions up to Bukkit (it disables the plugin)

Example from DatabaseManager.kt:115:
```kotlin
return try {
    val updated = ps.executeUpdate()
    updated == 1
} catch (e: SQLException) {
    plugin.logger.warning("DB error while recording UUID $id: ${e.message}")
    false
}
```

Even if the database query fails, the plugin continues running.

---

## What's Next?

Now that you understand the architecture, dive into:

- [Core Functions →](core-functions.md) – Deep dive into key methods
- [Database Schema →](database-schema.md) – Tables, indexes, and queries
- [Event System →](event-system.md) – How every event is tracked

Questions? Open an issue on GitHub or ping us on Discord!
