<img width="100" height="100" alt="dupetrace" src="https://github.com/user-attachments/assets/150a0965-cf8f-477c-a57f-8802e4ef7fde" /> 

# $${\color{yellow}DupeTrace}$$ $${\color{yellow}v1.0.3}$$

_[pre-release]_

### **Duplication Detection Plugin, For PaperMC 1.21.10**


#### Features

- 🔍 **Automatic Duplicate Detection**: Tracks all non-stackable items with unique IDs
- 🗃️ **Database Persistence**: Supports both H2 (embedded) and PostgreSQL databases (recommended)
- ⚡ **Real-time Monitoring**: Comprehensive event tracking across inventories, containers, and player interactions
- 🛡️ **Auto-Removal**: Optional automatic removal of duplicated items
- 🎨 **Creative Mode Support**: Configurable handling of Creative mode duplication
- 📊 **Advanced Tracking**: Deep scanning of Shulker Boxes and Bundles
- 🔧 **Highly Configurable**: Fine-tune detection sensitivity and behavior

---

### Quick Start (For Server Admins)

#### Installation

1. Download `DupeTrace-1.0.3.jar` (no classifier) from the [releases page](https://github.com/darkstarworks/DupeTrace/releases)
   - Do NOT use the `-dev` jar on servers — it omits dependencies and is for development only.
2. Place the jar file in your server's `plugins/` directory
3. Restart your server
4. Configure settings in `plugins/DupeTrace/config.yml` (optional)

### Discord & Support

![flat](https://dcbadge.limes.pink/api/server/https://discord.gg/aWMU2JNXex?style=flat)

[![ko-fi](https://ko-fi.com/img/githubbutton_sm.svg)](https://ko-fi.com/darkstarworks)

### Basic Configuration

After first run, edit `plugins/DupeTrace/config.yml`:

```yaml
database:
  # DupeTrace currently supports only h2 or PostgreSQL
  # PostgreSQL is HIGHLY recommended if you have an active server!
  type: h2 # default: h2
  h2:
    # File path without extension; H2 will create .mv.db automatically
    file: plugins/DupeTrace/data/dupetrace
  postgres:
    # The requirements for PostgreSQL are already included with PaperMC,
    # you just need to provide a (remote) database
    url: jdbc:postgresql://localhost:5432/dupe_trace
    user: postgres
    password: postgres
  # If true, logs A LOT more info to the console
  debug: false # default: false

# Duplicate detection and notifications
broadcast-duplicates: true # default: true

alert-admins: true # default: true

# -- BETA FEATURE --
# Automatically removing duplicates COULD in very rare cases be triggered by false positives.
# Please make sure to set [keep-oldest-on-dup-remove: true]
auto-remove-duplicates: true # default: true

# When above [auto-remove-duplicates: true], keep
# the oldest recorded version of the item/block
keep-oldest-on-dup-remove: true # default: true

# The Grace Window is a brief moment where the anti-cheat is a bit more forgiving.
# It's designed to ignore very fast, legitimate movements (like quick turns or jumps)
# lower number = stronger detection, but more mistakes
# higher number = weaker detection, but less mistakes
movement-grace-ms: 750 # default: 750

# Suppress repeat alerts for the same item within this window
duplicate-alert-debounce-ms: 2000 # default (milliseconds): 2000

# If [allow-creative-duplicates: true],
# duplicates created in Creative mode are allowed (no alert/removal),
# but will be tagged as [CREATIVE] in logs
allow-creative-duplicates: true # default: true

# In-memory tracking safety to avoid memory growth
# TTL for known item entries (10 minutes)
known-items-ttl-ms: 600000 # default (milliseconds): 600000

# Scanning interval configuration in ticks (200 = 10 seconds)
scan-interval: 200 # default: 200

# If [inventory-open-scan-enabled: false], full inventory scans
# every time an inventory is opened will be disabled.
# This reduces overhead significantly on large active playerbases
inventory-open-scan-enabled: true # default: true
```

### Permissions

- `dupetrace.admin` - Access to `/dupetest` command (default: op)
- `dupetrace.alerts` - Receive duplicate detection alerts (default: op)

### Commands

- `/dupetest give` - Gives a test diamond sword with a unique ID for testing the system

---

## Troubleshooting

### "Duplicate detected" but it's a false positive

Increase `movement-grace-ms` in config.yml to give more time for legitimate item transfers.

### High memory usage

Reduce `known-items-ttl-ms` or `scan-interval` to decrease memory footprint.

### Items not being tracked

Ensure the items are non-stackable (max stack size of 1). Stackable items like diamonds or stone cannot be tracked individually.

### Database connection errors

- For H2: Ensure the plugin has write permissions to the `plugins/DupeTrace/data/` directory
- For PostgreSQL: Verify the database exists and credentials are correct

---

## Developer Guide

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
```bash
./gradlew.bat clean build
```

**Linux/macOS:**
```bash
./gradlew clean build
```

The shaded jar for deployment will be at: `build/libs/DupeTrace-1.0-paper.jar`

### Running a Development Server

Start a local Paper server with your plugin automatically loaded:

```bash
./gradlew.bat runServer  # Windows
./gradlew runServer      # Linux/macOS
```

The first run downloads the Paper server. The plugin is automatically loaded from your latest build.

---

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
- **Discussions**: [GitHub Discussions](https://github.com/darkstarworks/DupeTrace/discussions)

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

---

## Acknowledgments

Built with:
- [PaperMC](https://papermc.io/) - High-performance Minecraft server
- [Kotlin](https://kotlinlang.org/) - Modern JVM language
- [HikariCP](https://github.com/brettwooldridge/HikariCP) - Fast connection pooling
- [H2 Database](https://www.h2database.com/) - Embedded SQL database
- [Gradle](https://gradle.org/) - Build automation

### Dependencies (Shaded into Plugin)

- `kotlin-stdlib-jdk8` - Kotlin standard library
- `HikariCP 5.1.0` - Database connection pooling
- `H2 2.3.232` - Embedded database
- `PostgreSQL 42.7.4` - PostgreSQL JDBC driver

# Support Me - This Plugin is Free!
https://www.ko-fi.com/darkstarworks
