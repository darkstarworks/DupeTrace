# Permissions 🔐

DupeTrace keeps things simple with just two permissions. No complicated hierarchies, no nested nodes – just straightforward access control.

---

## Permission Nodes

| Permission | Description | Default |
|------------|-------------|---------|
| `dupetrace.admin` | Access to all `/dupetest` commands | **op** |
| `dupetrace.alerts` | Receive duplicate detection alerts | **op** |

---

## `dupetrace.admin`

**Default:** Server operators (op)

Grants access to **all** DupeTrace commands:
- `/dupetest give`
- `/dupetest uuid`
- `/dupetest history`
- `/dupetest stats`
- `/dupetest search`
- `/dupetest discord` (test, status, reload)

### Who Should Have This?

Give this to:
- ✅ Server owners
- ✅ Head admins
- ✅ Staff members who handle dupe investigations
- ✅ Moderators you trust with database access

**Do NOT give this to:**
- ❌ Regular players (obviously)
- ❌ Trial staff or junior mods (unless you really trust them)
- ❌ Build team members who don't need investigation tools

### Example Usage (LuckPerms)
```
/lp group admin permission set dupetrace.admin true
```

### Example Usage (PermissionsEx)
```
/pex group admin add dupetrace.admin
```

---

## `dupetrace.alerts`

**Default:** Server operators (op)

Allows players to receive duplicate detection alerts in chat when a dupe is found.

### Alert Example
```
⚠ DUPLICATE DETECTED: Diamond Sword (ID: 8f3c5a1d...)
Players: Steve, Alex
```

### Who Should Have This?

Give this to:
- ✅ Server admins
- ✅ Active moderators
- ✅ Staff who monitor for exploits

**Consider withholding from:**
- ❌ AFK staff members (alert fatigue is real)
- ❌ Players who panic and make a scene every time there's an alert
- ❌ Staff who don't need to respond immediately to dupes

### Why Separate This Permission?

Not everyone who can *investigate* dupes (with `dupetrace.admin`) needs to be *notified* in real-time. You might want a few trusted admins to receive live alerts while junior staff only use commands when needed.

### Example Usage (LuckPerms)
```
/lp group moderator permission set dupetrace.alerts true
```

### Example Usage (PermissionsEx)
```
/pex group moderator add dupetrace.alerts
```

---

## Permission Combinations

### Setup #1: Full Admin Access
```yaml
# Staff member who can investigate AND receives alerts
Permissions:
  - dupetrace.admin
  - dupetrace.alerts
```

**Use Case:** Head admins, server owners, senior moderators

---

### Setup #2: Investigation Only
```yaml
# Staff member who can investigate but won't get spammed with alerts
Permissions:
  - dupetrace.admin
```

**Use Case:** Junior mods, staff who only investigate when asked, build team leads

---

### Setup #3: Alert Watcher
```yaml
# Staff member who sees alerts but can't run commands
Permissions:
  - dupetrace.alerts
```

**Use Case:** This is pretty rare, but could be useful for a "watcher" role who escalates issues to other admins.

---

## Configuration Interaction

The `alert-admins` setting in your `config.yml` controls whether alerts are sent to players with the `dupetrace.alerts` permission:

```yaml
alert-admins: true   # Sends alerts to dupetrace.alerts holders
alert-admins: false  # No alerts sent (only console logs)
```

Even if someone has `dupetrace.alerts`, they won't get alerts if `alert-admins` is set to `false`.

**Learn more:** [Configuration →](configuration.md)

---

## Wildcard Permissions

If you're using a wildcard permission plugin, DupeTrace respects common wildcard patterns:

- `dupetrace.*` – Grants both permissions
- `*` – Grants everything (standard Minecraft behavior)

### Example (LuckPerms)
```
/lp group admin permission set dupetrace.* true
```

---

## Frequently Asked Questions

### Can regular players see duplicate alerts?

Only if you give them the `dupetrace.alerts` permission AND `broadcast-duplicates` is set to `true` in the config.

By default:
- `dupetrace.alerts` is **op-only**
- `broadcast-duplicates` is **enabled**

So yes, if you op a player, they'll see alerts. If you want to prevent this, either:
1. Don't op them (use a permission plugin instead)
2. Set `broadcast-duplicates: false` in your config

---

### What happens if someone doesn't have permissions?

They'll get the standard "You do not have permission" message when trying to run commands. No alerts will be shown to them either.

---

### Do offline players keep their permissions?

That depends on your permission plugin (LuckPerms, PermissionsEx, etc.). DupeTrace doesn't store or manage permissions – it just checks what your permission plugin says.

---

### Can I create custom permission groups?

Absolutely! Use your permission plugin to create custom groups and assign DupeTrace permissions as needed.

**Example (LuckPerms):**
```
/lp creategroup dupe-investigators
/lp group dupe-investigators permission set dupetrace.admin true
/lp group dupe-investigators permission set dupetrace.alerts true
/lp user Steve parent add dupe-investigators
```

---

## Best Practices

### 🎯 Principle of Least Privilege
Only give permissions to staff who actively need them. Just because someone is a "moderator" doesn't mean they need dupe investigation tools.

### 🔔 Alert Fatigue is Real
If your staff complain about too many alerts, remove `dupetrace.alerts` from their group. They can still use commands when needed.

### 🧪 Test with Trial Staff
When promoting new staff, start them without `dupetrace.alerts` and grant it after they've proven themselves.

### 🔒 Audit Regularly
Periodically review who has `dupetrace.admin` access. People leave staff teams, and permissions should be revoked accordingly.

---

## What's Next?

You've mastered the basics! Ready to dive deeper? Check out the developer docs:

- [Plugin Architecture →](dev/architecture.md)
- [Core Functions →](dev/core-functions.md)
- [Database Schema →](dev/database-schema.md)
