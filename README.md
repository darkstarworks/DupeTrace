<img width="600" height="110" alt="dupetrace-min" src="https://github.com/user-attachments/assets/72c72598-ffce-4db5-8357-d2ee63e203f6" />

### **Item Duplication Detection Plugin, For PaperMC 1.21.10**

## v1.0.4 _[pre-release]_

#### Features

- 🔍 **Automatic Duplicate Detection**: Tracks all non-stackable items with unique IDs
- 🗃️ **Database Persistence**: Supports ONLY PostgreSQL databases (for size and speed reasons)
- ⚡ **Real-time Monitoring**: Comprehensive event tracking across inventories, containers, and player interactions
- 🛡️ **Auto-Removal**: Optional automatic removal of duplicated items
- 🎨 **Creative Mode Support**: Configurable handling of Creative mode duplication
- 📊 **Advanced Tracking**: Deep scanning of Shulker Boxes and Bundles
- 🔧 **Highly Configurable**: Fine-tune detection sensitivity and behavior

---

### Quick Start (For Server Admins)

#### Installation

1. Download `DupeTrace-1.0.4-paper.jar` from the [releases page](https://github.com/darkstarworks/DupeTrace/releases)
2. Place the jar file in your server's `plugins/` directory
3. Restart your server
4. Configure settings in `plugins/DupeTrace/config.yml` (optional)

### Discord & Support

![flat](https://dcbadge.limes.pink/api/server/https://discord.gg/aWMU2JNXex?style=flat)

[![ko-fi](https://ko-fi.com/img/githubbutton_sm.svg)](https://ko-fi.com/darkstarworks)

### Basic Configuration

After first run, edit `plugins/DupeTrace/config.yml`:

```yaml
# ==============================
# DupeTrace Configuration (overview)
# ==============================
# How to use:
# 1) Set your PostgreSQL connection under database.postgres (url/user/password).
# 2) Restart the server (recommended) after saving changes.
#
#  The requirements for PostgreSQL are already included with PaperMC,
#         you just need to provide a (remote) database
#
# About defaults:
# - Every setting below keeps a short inline comment showing its default value.
# - If unsure, you can keep the defaults; they are safe for most servers.
#
# Overview of settings:
# database.postgres.url        — JDBC URL to your PostgreSQL database (e.g., jdbc:postgresql://localhost:5432/dupe_trace)
# database.postgres.user       — Database username
# database.postgres.password   — Database password
# database.debug               — Extra verbose DB logging to console (spammy). Default: false
# broadcast-duplicates         — Broadcast duplicate detections to all players. Default: true
# alert-admins                 — Send alerts to players with dupetrace.alerts. Default: true
# auto-remove-duplicates       — BETA: auto-remove extra copies (use with caution). Default: true
# keep-oldest-on-dup-remove    — When auto-removing, keep the oldest-known copy. Default: true
# movement-grace-ms            — Grace window to avoid false positives during fast moves. Default: 750
# duplicate-alert-debounce-ms  — Minimum time between alerts for the same item. Default: 2000
# allow-creative-duplicates    — Allow duplicates created in Creative (still logged). Default: true
# known-items-ttl-ms           — TTL for in-memory tracking to cap memory usage. Default: 600000
# scan-interval                — Periodic scan interval in ticks (20 ticks = 1s). Default: 200
# inventory-open-scan-enabled  — Scan inventories when they open (more overhead). Default: true
#
# Tip: Values ending in -ms are milliseconds.

database: # DupeTrace uses PostgreSQL only!
  postgres:
    url: jdbc:postgresql://localhost:5432/dupe_trace
    user: postgres
    password: postgres
  debug: false # default: false

broadcast-duplicates: true # default: true
alert-admins: true # default: true

# -- BETA FEATURE --
# Automatically removing duplicates COULD in very rare cases be triggered by false positives.
auto-remove-duplicates: true # default: true
keep-oldest-on-dup-remove: true # default: true
# When above [auto-remove-duplicates: true], it keeps the oldest recorded version of the item/block

# lower number = stronger detection, but more mistakes
# higher number = weaker detection, but less mistakes
movement-grace-ms: 750 # default: 750
duplicate-alert-debounce-ms: 2000 # default (milliseconds): 2000
allow-creative-duplicates: true # default: true
known-items-ttl-ms: 600000 # default (milliseconds): 600000
scan-interval: 200 # default: 200

# If [inventory-open-scan-enabled: false], full inventory scans will be disabled.
# This reduces overhead Significantly on large active playerbases
inventory-open-scan-enabled: true # default: true
```

### Permissions

- `dupetrace.admin` - Access to `/dupetest` command (default: op)
- `dupetrace.alerts` - Receive duplicate detection alerts (default: op)

### Commands

- `/dupetest give [player]` - Gives a test diamond sword with a unique ID for testing the system

---
## Troubleshooting Q&A

```diff
- "Duplicate detected" but it's a false positive
+ Increase `movement-grace-ms` in config.yml to give more time for legitimate item transfers.

- High memory usage
+ Reduce `known-items-ttl-ms` or `scan-interval` to decrease memory footprint.

- Items not being tracked
+ Ensure the items are non-stackable. Stackable blocks, items and consumables cannot be tracked individually.

- Database connection errors
+ Verify your database exists and the credentials are correct
```
---

## Developer DIY Guide

### Prerequisites

- Java Development Kit (JDK) 21 installed and on PATH
- Git (recommended)
- IntelliJ IDEA or another Kotlin-capable IDE
- Internet access for Gradle and PaperMC dependencies

### Project Layout

```
DupeTrace/
├── src/main/kotlin/io/github/darkstarworks/dupeTrace/
│   ├── DupeTrace.kt                 # Main plugin class
│   ├── command/
│   │   └── DupeTestCommand.kt       # Test command implementation
│   ├── db/
│   │   └── DatabaseManager.kt       # Database operations
│   ├── listener/
│   │   ├── ActivityListener.kt      # Comprehensive event tracking
│   │   └── InventoryScanListener.kt # Inventory monitoring
│   └── util/
│       └── ItemIdUtil.kt            # UUID tagging utilities
├── src/main/resources/
│   ├── config.yml                   # Default configuration
│   └── plugin.yml                   # Plugin metadata
├── build.gradle.kts                 # Gradle build configuration
└── LICENSE                          # MIT License
```

### Minecraft and PaperMC Versions

- **Target**: Minecraft 1.21+ (Paper API 1.21.10-R0.1-SNAPSHOT)
- **Java**: JDK 21 required
- Local development uses Paper 1.21 via `run-paper` Gradle plugin

### Building from Source

**Windows:**
```shell
./gradlew.bat clean build
```

**Linux/macOS:**
```bash
./gradlew clean build
```

The shaded jar for deployment will be at: `build/libs/DupeTrace-1.0.4-paper.jar`

### Running a Development Server

Start a local Paper server with your plugin automatically loaded:

```bash
./gradlew.bat runServer  # Windows
./gradlew runServer      # Linux/macOS
```

The first run downloads the Paper server. The plugin is automatically loaded from your latest build.

___

## Contributing

Contributions are welcome! Please:

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

### Kotlin Guidelines for Paper Plugins

- Be explicit with null checks (Bukkit APIs are platform types)
- Use `@EventHandler` for event listeners
- Avoid Bukkit API calls from async threads
- Use data classes for configuration models
- Encapsulate state in the JavaPlugin subclass

---

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Support

- **Issues**: [GitHub Issues](https://github.com/darkstarworks/DupeTrace/issues)
- **Discussions**: ![flat](https://dcbadge.limes.pink/api/server/https://discord.gg/aWMU2JNXex?style=flat)

---

## Technical Details

### How It Works

1. **Item Tagging**: When a non-stackable item is first seen, DupeTrace assigns it a unique UUID stored in the item's PersistentDataContainer
2. **Event Monitoring**: Comprehensive event listeners track item movements across players, inventories, containers, and world interactions
3. **Duplicate Detection**: If the same UUID appears in multiple locations simultaneously (accounting for movement grace period), a duplicate is flagged
4. **Database Logging**: All item interactions are logged to a database (H2 or PostgreSQL) with timestamps for forensic analysis
5. **Auto-Removal** (Optional): When enabled, the plugin can automatically remove duplicated items, keeping the copy held by the earliest interactor

### Database Schema

**dupetrace_items**
- `id` (UUID, PRIMARY KEY): Unique item identifier
- `first_seen` (TIMESTAMP): When this item was first registered

**dupetrace_item_transfers**
- `id` (BIGSERIAL/IDENTITY, PRIMARY KEY): Transfer record ID
- `item_uuid` (UUID): Item identifier
- `player_uuid` (UUID): Player who interacted with the item
- `action` (VARCHAR): Action type (PICKUP, CRAFTED, INVENTORY_CLICK, etc.)
- `location` (TEXT): World coordinates
- `ts` (TIMESTAMP): Transfer timestamp

## Acknowledgments

Built with:
- [PaperMC](https://papermc.io/) - High-performance Minecraft server
- [Kotlin](https://kotlinlang.org/) - Modern JVM language
- [HikariCP](https://github.com/brettwooldridge/HikariCP) - Fast connection pooling
- [Gradle](https://gradle.org/) - Build automation

### Dependencies (Shaded into Plugin)

- `kotlin-stdlib-jdk8` - Kotlin standard library
- `HikariCP 5.1.0` - Database connection pooling
- `PostgreSQL 42.7.4` - PostgreSQL JDBC driver
- `adventure MiniMessage 4.25.0` - Minecraft Java UI Library

## Support Me - This Plugin is Free! ❤️
https://www.ko-fi.com/darkstarworks
