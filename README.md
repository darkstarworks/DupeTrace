# DupeTrace

**A PaperMC plugin for detecting and preventing item duplication exploits in Minecraft**

DupeTrace tracks non-stackable items using unique UUIDs and monitors their movements to detect when the same item appears in multiple locations simultaneously. Perfect for preventing duplication glitches on survival servers.

## Features

- 🔍 **Automatic Duplicate Detection**: Tracks all non-stackable items with unique IDs
- 🗃️ **Database Persistence**: Supports both H2 (embedded) and PostgreSQL databases
- ⚡ **Real-time Monitoring**: Comprehensive event tracking across inventories, containers, and player interactions
- 🛡️ **Auto-Removal**: Optional automatic removal of duplicated items
- 🎨 **Creative Mode Support**: Configurable handling of Creative mode duplication
- 📊 **Advanced Tracking**: Deep scanning of Shulker Boxes and Bundles
- 🔧 **Highly Configurable**: Fine-tune detection sensitivity and behavior

---

## Quick Start (For Server Admins)

### Installation

1. Download `DupeTrace-1.0-paper.jar` from the [releases page](https://github.com/darkstarworks/DupeTrace/releases)
2. Place the jar file in your server's `plugins/` directory
3. Restart your server
4. Configure settings in `plugins/DupeTrace/config.yml` (optional)

### Basic Configuration

After first run, edit `plugins/DupeTrace/config.yml`:

```yaml
# Database Configuration
database:
  type: h2  # Use 'h2' for embedded database or 'postgres' for PostgreSQL
  h2:
    file: plugins/DupeTrace/data/dupetrace
  postgres:  # Only needed if using PostgreSQL
    url: jdbc:postgresql://localhost:5432/dupe_trace
    user: postgres
    password: postgres

# Duplicate Detection Settings
broadcast-duplicates: true          # Log duplicates to console
alert-admins: true                  # Send alerts to players with dupetrace.alerts permission
auto-remove-duplicates: false       # Automatically remove duplicated items (USE WITH CAUTION)
keep-oldest-on-dup-remove: true    # When auto-removing, keep the copy held by the earliest interactor

# Fine-Tuning
movement-grace-ms: 750              # Grace period for legitimate rapid item movements (prevents false positives)
duplicate-alert-debounce-ms: 2000   # Minimum time between duplicate alerts for the same item
allow-creative-duplicates: true     # Allow duplicates in Creative mode (recommended: true)
known-items-ttl-ms: 600000         # Memory cleanup interval (10 minutes)

# Performance
scan-interval: 200                  # Periodic scan interval in ticks (200 = 10 seconds)
inventory-open-scan-enabled: true   # Scan inventories when opened
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
