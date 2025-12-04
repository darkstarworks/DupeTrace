# Configuration ⚙️

Time to tune DupeTrace to your server's needs! This page explains every knob, switch, and dial in your `config.yml`.

## Where's the Config File?

You'll find it at: `plugins/DupeTrace/config.yml`

It gets created automatically the first time you load the plugin. If you mess it up beyond repair, just delete it and restart – we'll make you a fresh one.

---

## Database Settings

```yaml
database:
  postgres:
    url: jdbc:postgresql://localhost:5432/dupe_trace
    user: postgres
    password: postgres
    pool-size: 10
    connection-timeout-ms: 30000
  debug: false
```

### `database.postgres.url`
**Default:** `jdbc:postgresql://localhost:5432/dupe_trace`

Your PostgreSQL connection string. Check out the [Installation guide](installation.md) for URL format examples.

### `database.postgres.user`
**Default:** `postgres`

Your database username. Please don't use `root`. Please. We're begging you.

### `database.postgres.password`
**Default:** `postgres`

Your database password. And for the love of Notch, change this from the default if you're on a public server!

### `database.postgres.pool-size`
**Default:** `10` (range: 1-50)

How many simultaneous database connections to keep in the pool. Think of it like having multiple checkout lanes at a grocery store.

- **Small servers (< 50 players):** 5-10 is perfect
- **Medium servers (50-200 players):** 10-20 should do it
- **Large servers (200+ players):** 20-30 keeps things smooth
- **Mega servers (500+ players):** 30-50 for the big leagues

### `database.postgres.connection-timeout-ms`
**Default:** `30000` (30 seconds)

How long to wait for a database connection before giving up. If you're getting timeout errors, your database might be overloaded or your network connection is having a bad day.

### `database.debug`
**Default:** `false`

Turns on verbose database logging. **Warning:** This is *very* chatty and will spam your console. Only enable this when troubleshooting database issues.

---

## Detection & Alert Settings

```yaml
broadcast-duplicates: true
alert-admins: true
auto-remove-duplicates: true
keep-oldest-on-dup-remove: true
```

### `broadcast-duplicates`
**Default:** `true`

When set to `true`, duplicate detection messages are broadcast to all online players. When `false`, only admins with the `dupetrace.alerts` permission see them.

**When to disable:**
- Your players panic every time they see a dupe alert
- You want to investigate quietly before announcing

### `alert-admins`
**Default:** `true`

Sends duplicate alerts to players with the `dupetrace.alerts` permission. If you turn this off, alerts only go to the console logs.

**When to disable:**
- You're getting too many false positives (tune `movement-grace-ms` first!)
- You prefer reviewing logs instead of live alerts

### `auto-remove-duplicates` ⚠️ BETA
**Default:** `true`

When a duplicate is detected, automatically delete the extra copy. This is powerful but **can cause issues** if you have false positives!

**Recommendation:** Start with this set to `false` and observe your server for a week. Once you're confident the detection is accurate, enable it.

### `keep-oldest-on-dup-remove`
**Default:** `true`

When auto-removing duplicates, keep the copy that was seen *first* in the database and remove the newer ones. This usually means keeping the legitimate item.

If set to `false`, it removes the oldest and keeps the newest (which is... weird, and probably not what you want).

---

## Timing & Performance Tuning

```yaml
movement-grace-ms: 750
duplicate-alert-debounce-ms: 2000
known-items-ttl-ms: 600000
scan-interval: 200
```

### `movement-grace-ms` 🎯 Most Important Setting
**Default:** `750` (0.75 seconds)

The "grace period" after an item moves before we start checking for duplicates. This prevents false positives during legitimate item transfers.

**The Magic Balance:**
- **Too low (< 500ms):** Lots of false positives from lag or fast item transfers
- **Too high (> 2000ms):** Dupers get a bigger window to exploit
- **Sweet spot:** 500-1000ms for most servers

**Adjust based on your server's performance:**
- Laggy server or lots of plugins? → Increase to 1000-1500ms
- Low-latency beast machine? → You can go as low as 500ms
- Average server? → 750ms is perfect (trust us, we tested)

### `duplicate-alert-debounce-ms`
**Default:** `2000` (2 seconds)

Minimum time between duplicate alerts for the *same item*. Prevents alert spam if someone's going ham with a dupe exploit.

Lower values = more alerts. Higher values = less console spam.

### `known-items-ttl-ms`
**Default:** `600000` (10 minutes)

How long to keep items in memory before clearing them out to prevent memory leaks. Items are still tracked in the database – this only affects the in-memory cache.

**Adjust if:**
- Memory usage is high → Lower to 300000 (5 mins)
- You have lots of RAM to spare → Increase to 900000 (15 mins)

### `scan-interval`
**Default:** `200` (10 seconds, because 20 ticks = 1 second)

How often (in ticks) to run the periodic inventory scanner. The scanner checks all online players' inventories for duplicate UUIDs.

**Performance Impact:**
- Lower value = more frequent scans = higher CPU usage
- Higher value = less frequent scans = dupes might hide longer

**Recommended values:**
- Small servers: 100 ticks (5 seconds)
- Average servers: 200 ticks (10 seconds) ← default
- Large servers: 400 ticks (20 seconds)

---

## Creative Mode Handling

```yaml
allow-creative-duplicates: true
```

### `allow-creative-duplicates`
**Default:** `true`

When set to `true`, items duplicated in Creative mode won't trigger alerts or auto-removal. They're still logged in the database for your records, though.

**Why allow Creative dupes?**
Creative mode players can spawn infinite items anyway, so duplicate detection is kinda pointless there. Plus, some build tools and world-editing workflows involve copying items.

Set to `false` if you want to track *everything*, even Creative shenanigans.

---

## Inventory Scanning

```yaml
inventory-open-scan-enabled: true
```

### `inventory-open-scan-enabled`
**Default:** `true`

When enabled, scans a player's inventory every time they open a container, chest, or their own inventory.

**Performance Note:** This adds overhead on busy servers. If you have 200+ active players doing lots of inventory work, consider disabling this and relying on the periodic scanner instead.

**When to disable:**
- Server TPS is struggling
- You have a massive playerbase
- You don't mind slightly slower dupe detection

---

## Discord Integration

DupeTrace features a **fully customizable Discord webhook** system with embeds, mentions, and rate limiting.

### Basic Setup

```yaml
discord:
  enabled: false
  webhook-url: ""
```

### `discord.enabled`
**Default:** `false`

Set to `true` to send duplicate alerts to a Discord channel via webhook.

### `discord.webhook-url`
**Default:** `""` (empty)

Your Discord webhook URL. To get one:
1. Open your Discord server
2. Go to **Server Settings** → **Integrations** → **Webhooks**
3. Click **New Webhook**
4. Copy the webhook URL and paste it here

**Test it:** After configuring, run `/dupetest discord test` in-game to verify your webhook works!

---

### Bot Appearance

```yaml
discord:
  username: "DupeTrace"
  avatar-url: ""
```

### `discord.username`
**Default:** `"DupeTrace"`

The name that appears as the webhook sender in Discord.

### `discord.avatar-url`
**Default:** `""` (empty = Discord default)

URL to a custom avatar image for the webhook. Leave empty to use Discord's default.

---

### Embed Customization

```yaml
discord:
  embed:
    color: "#FF0000"
    title: "🚨 Duplicate Item Detected"
    description: ""
    show-player: true
    show-item-type: true
    show-item-uuid: true
    show-full-uuid: false
    show-location: true
    show-timestamp: true
    field-name-player: "Player"
    field-name-item-type: "Item Type"
    field-name-uuid: "Item UUID"
    field-name-location: "Location"
    footer-text: ""
    footer-icon-url: ""
    thumbnail-url: ""
    image-url: ""
```

### `discord.embed.color`
**Default:** `"#FF0000"` (red)

Hex color code for the embed sidebar. Supports formats like `#FF0000` or `FF0000`.

### `discord.embed.title`
**Default:** `"🚨 Duplicate Item Detected"`

The embed title. Supports placeholders (see below).

### `discord.embed.description`
**Default:** `""` (empty)

Optional description text below the title. Supports placeholders.

### Field Visibility Options

| Setting | Default | Description |
|---------|---------|-------------|
| `show-player` | `true` | Show player name field |
| `show-item-type` | `true` | Show item type field |
| `show-item-uuid` | `true` | Show item UUID field |
| `show-full-uuid` | `false` | Show full UUID instead of shortened |
| `show-location` | `true` | Show coordinates field |
| `show-timestamp` | `true` | Show timestamp in embed |

### Custom Field Names

Customize field labels for localization:

| Setting | Default |
|---------|---------|
| `field-name-player` | `"Player"` |
| `field-name-item-type` | `"Item Type"` |
| `field-name-uuid` | `"Item UUID"` |
| `field-name-location` | `"Location"` |

### Footer & Images

| Setting | Description |
|---------|-------------|
| `footer-text` | Text at bottom of embed (supports `{version}`) |
| `footer-icon-url` | Small icon next to footer |
| `thumbnail-url` | Small image on right side of embed |
| `image-url` | Large image at bottom of embed |

---

### Template Placeholders

Use these in `title`, `description`, and `mentions.content`:

| Placeholder | Description | Example |
|-------------|-------------|---------|
| `{player}` | Player name | `Steve` |
| `{item_type}` | Item material | `DIAMOND_SWORD` |
| `{uuid_short}` | First 8 chars of UUID | `8f3c5a1d` |
| `{uuid_full}` | Complete UUID | `8f3c5a1d-2b4e-...` |
| `{location}` | World coordinates | `world:123,64,-456` |
| `{version}` | Plugin version | `1.2.0` |

**Example custom title:**
```yaml
title: "⚠️ Dupe Alert: {item_type} by {player}"
```

---

### Mention/Ping Settings

```yaml
discord:
  mentions:
    enabled: false
    role-id: ""
    user-ids: ""
    content: "{role_mention} {user_mentions}"
```

### `discord.mentions.enabled`
**Default:** `false`

Enable Discord mentions/pings in duplicate alerts.

### `discord.mentions.role-id`
**Default:** `""` (empty)

Discord role ID to ping. To get a role ID:
1. Enable **Developer Mode** in Discord (Settings → Advanced)
2. Right-click the role → **Copy ID**

### `discord.mentions.user-ids`
**Default:** `""` (empty)

Comma-separated list of Discord user IDs to ping.
Example: `"123456789012345678,987654321098765432"`

### `discord.mentions.content`
**Default:** `"{role_mention} {user_mentions}"`

Message content where mentions appear (outside the embed). Available placeholders:
- `{role_mention}` – The role ping
- `{user_mentions}` – User pings
- Plus all standard placeholders

---

### Rate Limiting

```yaml
discord:
  rate-limit:
    enabled: true
    max-per-minute: 30
    queue-max-size: 100
```

### `discord.rate-limit.enabled`
**Default:** `true`

Enable rate limiting to prevent Discord API abuse. **Highly recommended!**

### `discord.rate-limit.max-per-minute`
**Default:** `30`

Maximum alerts sent per minute. Discord's limit is 30/minute per webhook, so don't go higher.

### `discord.rate-limit.queue-max-size`
**Default:** `100`

When rate limited, alerts queue up. This sets the max queue size. Oldest alerts are dropped if exceeded.

---

### Complete Discord Example

```yaml
discord:
  enabled: true
  webhook-url: "https://discord.com/api/webhooks/123456789/abc..."

  username: "DupeGuard"
  avatar-url: "https://example.com/logo.png"

  embed:
    color: "#FF4444"
    title: "⚠️ Duplicate Detected: {item_type}"
    description: "A duplicate item was found on your server!"
    show-player: true
    show-item-type: true
    show-item-uuid: true
    show-full-uuid: false
    show-location: true
    show-timestamp: true
    footer-text: "DupeTrace v{version}"
    thumbnail-url: "https://example.com/warning.png"

  mentions:
    enabled: true
    role-id: "123456789012345678"
    user-ids: ""
    content: "{role_mention} Dupe detected!"

  rate-limit:
    enabled: true
    max-per-minute: 30
    queue-max-size: 100
```

---

## Example Configurations

### 🐌 Laggy Server (Gentle Settings)
```yaml
movement-grace-ms: 1500
scan-interval: 400
inventory-open-scan-enabled: false
known-items-ttl-ms: 300000
```

### ⚡ High-Performance Server (Aggressive Settings)
```yaml
movement-grace-ms: 500
scan-interval: 100
inventory-open-scan-enabled: true
known-items-ttl-ms: 900000
```

### 🕵️ Paranoid Admin (Catch Everything)
```yaml
movement-grace-ms: 600
broadcast-duplicates: false
alert-admins: true
auto-remove-duplicates: false
allow-creative-duplicates: false
```

---

## Need Help?

Still confused? Head over to our [Discord](https://discord.gg/aWMU2JNXex) and ask! We're happy to help you tune your config for your specific server setup.

**Next up:** [Commands →](commands.md)
