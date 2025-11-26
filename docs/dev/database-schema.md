# Database Schema 🗄️

DupeTrace uses PostgreSQL for persistent storage. Let's break down the database structure, indexing strategy, and query patterns.

## Why PostgreSQL?

**Speed.** **Scalability.** **Reliability.**

- Handles millions of rows without breaking a sweat
- Superior indexing and query optimization
- JSON support for future expansion
- Widely available (even free hosting options exist)

SQLite would choke on large transfer logs. MySQL would work but... why settle for second best?

---

## Database Tables

### `dupetrace_items`

Stores every unique item UUID that's been seen.

```sql
CREATE TABLE IF NOT EXISTS dupetrace_items (
    id UUID PRIMARY KEY,
    first_seen TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
)
```

#### Columns

| Column | Type | Description |
|--------|------|-------------|
| `id` | UUID | Unique item identifier (primary key) |
| `first_seen` | TIMESTAMP | When this UUID was first registered |

#### Sample Data

```
id                                   | first_seen
-------------------------------------|-------------------------
8f3c5a1d-2b4e-4f9a-8c7d-1e5b9a3f2c8d | 2025-10-20 14:32:18.123
2a7b9f4c-1e3d-4a8c-9b2f-7e6c5d4a3b2c | 2025-10-20 15:45:02.987
9e1d3c8f-2b4a-4c9e-8d7f-1a5b3c2e4d6f | 2025-10-21 09:12:33.456
```

#### Growth Rate

**Slow.** This table only grows when a **new unique item** is seen for the first time.

- Small server (10-50 players): ~100-1,000 rows per day
- Large server (200+ players): ~1,000-10,000 rows per day

After a few months, growth stabilizes (most items have already been seen).

---

### `dupetrace_item_transfers`

Logs every interaction with tracked items. This is where the magic (and most of the data) lives.

```sql
CREATE TABLE IF NOT EXISTS dupetrace_item_transfers (
    id BIGSERIAL PRIMARY KEY,
    item_uuid UUID NOT NULL,
    player_uuid UUID NOT NULL,
    action VARCHAR(64) NOT NULL,
    location TEXT NOT NULL,
    ts TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
)
```

#### Columns

| Column | Type | Description |
|--------|------|-------------|
| `id` | BIGSERIAL | Auto-incrementing primary key |
| `item_uuid` | UUID | Item identifier (foreign reference) |
| `player_uuid` | UUID | Player who interacted with the item |
| `action` | VARCHAR(64) | Type of action (PICKUP, CRAFTED, etc.) |
| `location` | TEXT | World coordinates (format: `world:x,y,z`) |
| `ts` | TIMESTAMP | When the interaction occurred |

#### Sample Data

```
id  | item_uuid                            | player_uuid                         | action           | location             | ts
----|--------------------------------------|-------------------------------------|------------------|----------------------|-------------------------
1   | 8f3c5a1d-2b4e-4f9a-8c7d-1e5b9a3f2c8d | a1b2c3d4-e5f6-7890-abcd-1234567890ab | CRAFTED          | world:123,64,-456    | 2025-10-20 14:32:18
2   | 8f3c5a1d-2b4e-4f9a-8c7d-1e5b9a3f2c8d | a1b2c3d4-e5f6-7890-abcd-1234567890ab | INVENTORY_CLICK  | world:120,63,-450    | 2025-10-20 14:35:42
3   | 8f3c5a1d-2b4e-4f9a-8c7d-1e5b9a3f2c8d | b2c3d4e5-f6a7-8901-bcde-234567890abc | DROP             | world:98,70,-389     | 2025-10-20 14:37:09
```

#### Growth Rate

**Fast.** This is your high-traffic table.

- Small server: ~10,000-50,000 rows per day
- Medium server: ~50,000-200,000 rows per day
- Large server: ~200,000-1,000,000+ rows per day

**Storage Planning:**
- Each row is roughly **150-200 bytes**
- 1 million rows ≈ **150-200 MB**
- Indexes add another 50-100 MB

Plan for **1-2 GB per month** on active servers.

---

## Indexes

Indexes make queries **fast**. Without them, PostgreSQL would scan millions of rows for every query (slow!).

### `idx_transfers_item_uuid`

```sql
CREATE INDEX IF NOT EXISTS idx_transfers_item_uuid
ON dupetrace_item_transfers(item_uuid)
```

**Purpose:** Speed up queries that filter by `item_uuid` (like `/dupetest uuid`)

**Query Example:**
```sql
SELECT * FROM dupetrace_item_transfers WHERE item_uuid = '8f3c5a1d-...'
```

---

### `idx_transfers_player_uuid`

```sql
CREATE INDEX IF NOT EXISTS idx_transfers_player_uuid
ON dupetrace_item_transfers(player_uuid)
```

**Purpose:** Speed up queries that filter by `player_uuid` (like `/dupetest search`)

**Query Example:**
```sql
SELECT DISTINCT item_uuid FROM dupetrace_item_transfers WHERE player_uuid = 'a1b2c3d4-...'
```

---

### `idx_transfers_ts`

```sql
CREATE INDEX IF NOT EXISTS idx_transfers_ts
ON dupetrace_item_transfers(ts DESC)
```

**Purpose:** Speed up time-based queries and sorting by most recent transfers

**Query Example:**
```sql
SELECT * FROM dupetrace_item_transfers ORDER BY ts DESC LIMIT 100
```

---

### `idx_transfers_item_player` (Composite Index)

```sql
CREATE INDEX IF NOT EXISTS idx_transfers_item_player
ON dupetrace_item_transfers(item_uuid, player_uuid, ts DESC)
```

**Purpose:** Optimize queries that filter by both `item_uuid` AND `player_uuid` (used by auto-removal logic)

**Query Example:**
```sql
SELECT MIN(ts) FROM dupetrace_item_transfers
WHERE item_uuid = '8f3c5a1d-...' AND player_uuid = 'a1b2c3d4-...'
```

**Why Composite?**
PostgreSQL can use a single composite index for queries that filter on:
- `item_uuid` only
- `item_uuid` + `player_uuid`
- `item_uuid` + `player_uuid` + sorted by `ts`

This reduces the number of indexes needed!

---

## Common Queries

### 1. Check if an Item Exists

```sql
SELECT 1 FROM dupetrace_items WHERE id = '8f3c5a1d-...' LIMIT 1
```

**Used By:** `/dupetest uuid`, `/dupetest history`

**Index Used:** Primary key on `id` (automatic)

---

### 2. Get Item Transfer History

```sql
SELECT player_uuid, action, location, ts
FROM dupetrace_item_transfers
WHERE item_uuid = '8f3c5a1d-...'
ORDER BY ts DESC
LIMIT 50
```

**Used By:** `/dupetest uuid`, `/dupetest history`

**Index Used:** `idx_transfers_item_uuid`

---

### 3. Get Player's Items

```sql
SELECT DISTINCT item_uuid
FROM dupetrace_item_transfers
WHERE player_uuid = 'a1b2c3d4-...'
ORDER BY ts DESC
LIMIT 50
```

**Used By:** `/dupetest search`

**Index Used:** `idx_transfers_player_uuid`

---

### 4. Get Earliest Transfer Timestamp

```sql
SELECT MIN(ts)
FROM dupetrace_item_transfers
WHERE item_uuid = '8f3c5a1d-...' AND player_uuid = 'a1b2c3d4-...'
```

**Used By:** Auto-removal logic (determining who had the item first)

**Index Used:** `idx_transfers_item_player`

---

### 5. Get Database Statistics

```sql
-- Total unique items
SELECT COUNT(*) FROM dupetrace_items;

-- Total transfer records
SELECT COUNT(*) FROM dupetrace_item_transfers;

-- Unique players who've interacted with items
SELECT COUNT(DISTINCT player_uuid) FROM dupetrace_item_transfers;
```

**Used By:** `/dupetest stats`

**Note:** These are slow on large datasets (full table scans). Consider caching results if running frequently.

---

## Data Retention Strategies

As your database grows, you might want to prune old records to save storage space.

### Strategy 1: Delete Old Transfers

Keep items table forever, but delete transfers older than X days:

```sql
DELETE FROM dupetrace_item_transfers
WHERE ts < NOW() - INTERVAL '90 days';
```

**Pros:**
- Reduces database size
- Keeps recent history for investigations

**Cons:**
- Loses historical forensic data
- Can't track long-term item movements

### Strategy 2: Archive to Cold Storage

Move old records to a separate archive table or export to CSV:

```sql
-- Create archive table (same structure)
CREATE TABLE dupetrace_item_transfers_archive AS
SELECT * FROM dupetrace_item_transfers
WHERE ts < NOW() - INTERVAL '180 days';

-- Delete archived records from main table
DELETE FROM dupetrace_item_transfers
WHERE ts < NOW() - INTERVAL '180 days';
```

**Pros:**
- Keeps all data (just elsewhere)
- Main table stays fast

**Cons:**
- More complex to query archived data

### Strategy 3: Partition by Date

Use PostgreSQL table partitioning to split data into monthly tables:

```sql
CREATE TABLE dupetrace_item_transfers (
    id BIGSERIAL,
    item_uuid UUID NOT NULL,
    player_uuid UUID NOT NULL,
    action VARCHAR(64) NOT NULL,
    location TEXT NOT NULL,
    ts TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
) PARTITION BY RANGE (ts);

CREATE TABLE transfers_2025_10 PARTITION OF dupetrace_item_transfers
FOR VALUES FROM ('2025-10-01') TO ('2025-11-01');
```

**Pros:**
- Automatic data organization
- Can drop old partitions easily
- Queries on recent data stay fast

**Cons:**
- Requires PostgreSQL 10+
- More complex setup

---

## Database Maintenance

### Vacuum Regularly

PostgreSQL needs periodic vacuuming to reclaim space from deleted rows:

```sql
VACUUM ANALYZE dupetrace_item_transfers;
```

**Run this:**
- After bulk deletes
- Weekly on high-traffic tables
- Monthly on low-traffic tables

### Reindex Occasionally

Rebuild indexes to optimize performance:

```sql
REINDEX TABLE dupetrace_item_transfers;
```

**Run this:**
- After major data changes
- If queries become slow
- Monthly on active servers

---

## Monitoring & Optimization

### Check Table Sizes

```sql
SELECT
    tablename,
    pg_size_pretty(pg_total_relation_size(schemaname||'.'||tablename)) AS size
FROM pg_tables
WHERE schemaname = 'public'
ORDER BY pg_total_relation_size(schemaname||'.'||tablename) DESC;
```

### Check Index Usage

```sql
SELECT
    schemaname,
    tablename,
    indexname,
    idx_scan AS index_scans
FROM pg_stat_user_indexes
ORDER BY idx_scan DESC;
```

If an index has **0 scans**, it's not being used and can be dropped.

### Slow Query Analysis

Enable slow query logging in `postgresql.conf`:

```
log_min_duration_statement = 1000  # Log queries > 1 second
```

Review logs to find inefficient queries.

---

## Backup Strategies

### Full Backup (pg_dump)

```bash
pg_dump -h localhost -U postgres -d dupe_trace > backup.sql
```

### Incremental Backup

Use PostgreSQL's WAL (Write-Ahead Logging) for point-in-time recovery:

```bash
pg_basebackup -h localhost -U postgres -D /backup/base
```

### Automated Backups

Set up a cron job:

```bash
0 2 * * * pg_dump -h localhost -U postgres -d dupe_trace | gzip > /backups/dupe_trace_$(date +\%Y\%m\%d).sql.gz
```

Runs daily at 2 AM, keeps compressed backups.

---

## Scaling Considerations

### When Database Gets Large (100M+ rows)

1. **Partition tables** by date
2. **Archive old data** to cold storage
3. **Use read replicas** for `/dupetest` queries
4. **Increase connection pool size** in config
5. **Upgrade to better hardware** (more RAM = faster queries)

### When Database Gets REALLY Large (1B+ rows)

1. Consider switching to a **time-series database** (TimescaleDB)
2. Implement **data retention policies** (auto-delete old records)
3. Use **materialized views** for statistics queries
4. Split into **multiple databases** by world or date range

---

## Connection Pool Tuning

**Config Setting:** `database.postgres.pool-size`

**Default:** 10 connections

**Tuning Guide:**

| Server Size | Recommended Pool Size |
|-------------|----------------------|
| Small (< 50 players) | 5-10 |
| Medium (50-200 players) | 10-20 |
| Large (200-500 players) | 20-30 |
| Mega (500+ players) | 30-50 |

**Formula:**
```
pool-size ≈ (average_online_players / 10) + 5
```

**Don't go crazy:** More connections ≠ better performance. PostgreSQL has overhead per connection. Past 50 connections, you'll see diminishing returns.

---

## Security Best Practices

### Use a Dedicated Database User

Don't use the `postgres` superuser! Create a limited user:

```sql
CREATE USER dupetrace WITH PASSWORD 'secure_password_here';
GRANT CONNECT ON DATABASE dupe_trace TO dupetrace;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO dupetrace;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO dupetrace;
```

### Restrict Network Access

Edit `pg_hba.conf` to limit connections:

```
# Allow only from your Minecraft server IP
host    dupe_trace    dupetrace    192.168.1.100/32    md5
```

### Use SSL/TLS

For remote databases, enforce encrypted connections:

```yaml
database:
  postgres:
    url: jdbc:postgresql://db.example.com:5432/dupe_trace?ssl=true&sslmode=require
```

---

## What's Next?

Dive into how events are tracked:

- [Event System →](event-system.md) – Complete guide to event monitoring

Want to contribute? Check out the [Architecture Overview →](architecture.md) first!
