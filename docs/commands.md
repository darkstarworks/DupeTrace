# Commands 🎮

All the tools you need to test, monitor, and investigate item duplication on your server. Every command starts with `/dupetest` (or the aliases `/dt` or `/dtrace` if you're feeling lazy).

**Permission Required:** `dupetrace.admin` (default: op)

---

## Command Overview

| Command | What It Does |
|---------|--------------|
| `/dupetest give` | Spawn a test item with tracking |
| `/dupetest uuid <uuid>` | View an item's recent history |
| `/dupetest history <uuid> [limit]` | View full transfer log |
| `/dupetest stats` | View plugin statistics |
| `/dupetest search <player>` | List items associated with a player |

---

## `/dupetest give`

**Aliases:** `/dt give`, `/dtrace give`

Gives you a Diamond Sword tagged with a unique UUID for testing purposes.

### Usage
```
/dupetest give
```

### Example
```
> /dt give
✓ Given a Diamond Sword with id: 8f3c5a1d-2b4e-4f9a-8c7d-1e5b9a3f2c8d
```

### What's It Good For?
- Testing that tracking is working
- Demonstrating dupe detection to staff
- Creating a test item to pass around and monitor

**Pro Tip:** Try duplicating the test item (using Creative mode or a known dupe glitch) to see DupeTrace in action!

---

## `/dupetest uuid <uuid>`

**Aliases:** `/dt uuid`, `/dtrace uuid`

Shows the **10 most recent** transfers/interactions for a specific item UUID. Perfect for quick checks.

### Usage
```
/dupetest uuid <item-uuid>
```

### Example
```
> /dt uuid 8f3c5a1d-2b4e-4f9a-8c7d-1e5b9a3f2c8d

=== Item History: 8f3c5a1d... ===
Showing latest 10 transfers:
• 2025-10-23 14:32:18 - CRAFTED by Steve at world: 123,64,-456
• 2025-10-23 14:35:42 - PICKUP by Steve at world: 120,63,-450
• 2025-10-23 14:37:09 - INVENTORY_CLICK by Alex at world: 98,70,-389
• 2025-10-23 14:38:55 - DROP by Alex at world: 95,71,-384
```

### When to Use This
- Quick investigation of a suspicious item
- Tracking how an item moved between players
- Confirming whether an item is legitimately obtained

---

## `/dupetest history <uuid> [limit]`

**Aliases:** `/dt history`, `/dtrace history`

Shows the **full transfer history** for an item UUID, with an optional limit (default: 50).

### Usage
```
/dupetest history <item-uuid> [limit]
```

### Examples
```
# Show last 50 transfers (default)
/dt history 8f3c5a1d-2b4e-4f9a-8c7d-1e5b9a3f2c8d

# Show last 100 transfers
/dt history 8f3c5a1d-2b4e-4f9a-8c7d-1e5b9a3f2c8d 100

# Show ALL transfers (careful with old items!)
/dt history 8f3c5a1d-2b4e-4f9a-8c7d-1e5b9a3f2c8d 999999
```

### Sample Output
```
=== Full Transfer History: 8f3c5a1d... ===
Showing up to 50 transfers:
1. 2025-10-23 14:32:18 - CRAFTED
   Player: Steve  Location: world: 123,64,-456
2. 2025-10-23 14:35:42 - PICKUP
   Player: Steve  Location: world: 120,63,-450
3. 2025-10-23 14:37:09 - INVENTORY_CLICK
   Player: Alex  Location: world: 98,70,-389
...
```

### Action Types You'll See
- `CRAFTED` – Player crafted this item
- `PICKUP` – Player picked up the item from the ground
- `INVENTORY_CLICK` – Player clicked the item in an inventory
- `DROP` – Player dropped the item
- `CONTAINER_TRANSFER` – Item moved to/from a chest or container
- `SHULKER_UNPACK` – Item was inside a Shulker Box that got opened

### When to Use This
- Deep forensic investigation
- Building a timeline of how an item was duped
- Tracking down the source of a duplication exploit

---

## `/dupetest stats`

**Aliases:** `/dt stats`, `/dtrace stats`

Shows statistics about DupeTrace's database and your server's resource usage.

### Usage
```
/dupetest stats
```

### Sample Output
```
=== DupeTrace Statistics ===
Database:
  • Total Items Tracked: 15,847
  • Total Transfers Logged: 342,901
  • Unique Players: 1,203
Server:
  • Memory Usage: 3842MB / 8192MB
  • Online Players: 47
```

### What Each Stat Means

**Total Items Tracked:** How many unique non-stackable items have been seen and registered since the plugin was installed. This number goes up as players craft, pickup, or interact with tools, armor, and weapons.

**Total Transfers Logged:** Every time an item is moved, clicked, dropped, or transferred, it's logged. This number can get *big* on active servers – that's normal!

**Unique Players:** How many different players have interacted with tracked items.

**Memory Usage:** Current JVM memory consumption. If this is consistently above 80%, consider allocating more RAM to your server.

### When to Use This
- Checking if the plugin is working properly
- Monitoring database growth over time
- Showing off to your staff how many items you're tracking (flex!)

---

## `/dupetest search <player>`

**Aliases:** `/dt search`, `/dtrace search`

Lists up to **50 tracked items** associated with a specific player. Perfect for player-focused investigations.

### Usage
```
/dupetest search <player-name>
```

### Example
```
> /dt search Steve

=== Items for Steve ===
Showing up to 50 most recent items:
1. 8f3c5a1d... (Click to view)
2. 2a7b9f4c... (Click to view)
3. 9e1d3c8f... (Click to view)
...
Use /dupetest history <uuid> for details
```

### When to Use This
- Investigating a specific player's items
- Checking what tracked items a player has interacted with
- Finding suspicious items on a player who's been reported for duping

**Pro Tip:** Copy the short UUID (like `8f3c5a1d`) and use it with `/dupetest history` to see the full transfer log for that item!

---

## Tab Completion

DupeTrace supports tab completion on all commands, so you can just type `/dt` and press **Tab** to see your options.

### Examples
```
/dt <tab>          → give, uuid, history, stats, search
/dt search <tab>   → lists online player names
```

---

## Command Aliases

Tired of typing `/dupetest`? We got you:

- `/dt` – Short and sweet
- `/dtrace` – For the nostalgic Unix nerds

All three work identically. Use whichever feels right!

---

## Troubleshooting

### "You do not have permission to use this command"

You need the `dupetrace.admin` permission. By default, this is given to server operators. Check out the [Permissions page](permissions.md) for details.

---

### "Item UUID not found in database"

The UUID you entered doesn't exist in the database. Possible reasons:
- You mistyped the UUID
- The item hasn't been seen by DupeTrace yet (maybe it was created before the plugin was installed)
- Database connection issues (check your logs)

---

### "Player not found"

The player name you searched for doesn't exist or has never joined your server. Double-check the spelling!

---

## What's Next?

Now that you know the commands, learn about [Permissions →](permissions.md) to control who can do what!
