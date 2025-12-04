<img width="500" alt="dupetrace-logo" src="https://github.com/user-attachments/assets/72c72598-ffce-4db5-8357-d2ee63e203f6" />

## Professional Item Duplication Detection & Prevention

**Protect your server's economy and integrity** with enterprise-grade duplicate detection that actually works.

<br>

**Open Source!**
<a href="https://github.com/darkstarworks/DupeTrace"><img alt="github-link" src="https://img.shields.io/badge/GitHub-100000?style=for-the-badge&logo=github&logoColor=white" /></a>

---

### Why Choose DupeTrace?

**Item duplication exploits are the #1 threat to server economies.** A single dupe glitch can:
- Destroy months of careful server balancing
- Make legitimate players quit in frustration
- Crash your economy overnight
- Force rollbacks that punish innocent players

**DupeTrace stops dupes BEFORE they spread** by:
- ✅ Tracking every non-stackable item with invisible UUID fingerprints
- ✅ Detecting when the same item appears in multiple locations simultaneously
- ✅ Auto-removing duplicates in real-time (configurable)
- ✅ Logging complete forensic histories for investigations
- ✅ Working passively without breaking other plugins or game mechanics

**Built for performance.** DupeTrace uses asynchronous database operations, connection pooling, and smart caching to handle **200-1000 events/second** without impacting your server TPS.

---

### Key Features

- 🔍 **Automatic Duplicate Detection**: Tracks all non-stackable items with unique IDs stored in NBT data
- 🗃️ **PostgreSQL Database**: Enterprise-grade persistence handles millions of records without slowdown
- ⚡ **Real-time Monitoring**: Comprehensive event tracking across 30+ Bukkit events
- 🛡️ **Auto-Removal**: Optional automatic removal of duplicated items (keeps the oldest legitimate copy)
- 📣 **Customizable Discord Webhooks**: Fully customizable alerts with embeds, mentions, and rate limiting
- 🎨 **Creative Mode Support**: Configurable handling of Creative mode duplication
- 📊 **Deep Container Scanning**: Recursively tracks items inside Shulker Boxes and Bundles
- 🔧 **Highly Configurable**: Fine-tune detection sensitivity, grace periods, and performance settings
- 🔬 **Forensic Tools**: Complete transfer history and player-based item searches for investigations
- 🚀 **Zero-Lag Design**: All database operations run asynchronously on separate threads

---

### Performance Metrics

**Lightweight & Fast**
- **Memory Usage**: ~25-35 MB total (plugin + connection pool + cache)
- **CPU Impact**: < 1% on modern hardware
- **TPS Impact**: None (all database operations are async)
- **Event Throughput**: Handles 200-1000 item events/second without lag
- **Database Growth**: ~150-200 MB per million transfers (~1-2 GB/month on active servers)

**Optimized for Scale**
- Tested on servers with **500+ concurrent players**
- Connection pooling handles concurrent database access efficiently
- Smart in-memory caching reduces database queries
- Configurable TTL prevents memory leaks on long-running servers

<br>

### Configuration (required!)

1. After first start, edit the generated `plugins/DupeTrace/config.yml`
2. Add your PostgreSQL details
3. Restart the server


<br>

### Commands

| command | description |
| --- | --- |
| `/dupetest give` | Give yourself a Diamond Sword tagged with a unique ID |
| `/dupetest uuid <uuid>` | View item's recent transfer history (latest 10 entries) |
| `/dupetest history <uuid> [limit]` | View full transfer log (default limit: 50) |
| `/dupetest stats` | View plugin and database statistics |
| `/dupetest search <player>` | List tracked items associated with a player |
| `/dupetest discord test` | Send a test message to verify webhook |
| `/dupetest discord status` | View webhook status and rate limit info |
| `/dupetest discord reload` | Reload webhook configuration |

Command aliases: `/dt` & `/dtrace`

<br>

### Large Servers
(Recommended for 100+ players)

```yaml
scan-interval: 300 # Increase to 15 seconds
inventory-open-scan-enabled: false # Disable for max performance
database:
  postgres:
    pool-size: 20 # Increase pool size
known-items-ttl-ms: 300000 # Reduce to 5 minutes
```

<br>

### Permissions

| permissions | description | default |
| --- | --- | --- |
| dupetrace.admin | Allows access to DupeTrace admin commands | op |
| dupetrace.alerts | Receive duplicate alerts from DupeTrace | op |

<br>

### Dependencies

A (remote) PostgreSQL Database is required

<br>

### Dependencies (included)

- `kotlin-stdlib-jdk8` - Kotlin standard library
- `HikariCP 5.1.0` - Database connection pooling
- `PostgreSQL 42.7.4` - PostgreSQL JDBC driver
- `adventure MiniMessage 4.25.0` - Minecraft Java UI Library

---

In Active and Continues development.

#### Discord

![flat](https://dcbadge.limes.pink/api/server/https://discord.gg/aWMU2JNXex?style=flat)

#### Support

[![ko-fi](https://ko-fi.com/img/githubbutton_sm.svg)](https://ko-fi.com/darkstarworks)
