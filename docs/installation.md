# Installation 📦

Getting DupeTrace running on your server is easier than teaching a villager to trade fairly. Let's do this!

## What You'll Need

Before we start, make sure you've got:

- ✅ A **PaperMC 1.21.1+** server (Spigot won't cut it, sorry!)
- ✅ **Java 21** installed
- ✅ A **PostgreSQL database** (local or remote – we're flexible)
- ✅ About 5 minutes of your time

> **Why PostgreSQL?** Simple: speed and scalability. We track *a lot* of items, and Postgres handles it like a champ. SQLite would cry. MySQL would probably work but... why settle?

## Step 1: Download the Plugin

Head over to our [Releases page](https://github.com/darkstarworks/DupeTrace/releases) and grab the latest `DupeTrace-x.x.x-paper.jar` file.

> ⚠️ **Important**: Download the file ending in `-paper.jar`, NOT the one with `-dev.jar`. The dev version is missing dependencies and will throw a tantrum on your server.

## Step 2: Drop It In

Move the downloaded `.jar` file into your server's `plugins/` folder. You know the drill.

```
your-server/
├── plugins/
│   ├── DupeTrace-1.1.0-paper.jar  ← Right here!
│   └── (your other plugins)
├── server.jar
└── ...
```

## Step 3: Set Up PostgreSQL

You'll need a PostgreSQL database ready to go. If you don't have one yet:

### Option A: Local Database (Quick & Dirty)

1. Install PostgreSQL on your server
2. Create a database called `dupe_trace`:
   ```sql
   CREATE DATABASE dupe_trace;
   ```
3. Note down your username and password (default is usually `postgres` / `postgres`)

### Option B: Remote Database (Fancy & Recommended)

Use a hosting provider like:
- **AWS RDS**
- **DigitalOcean Managed Databases**
- **ElephantSQL** (free tier available!)

Grab your connection URL, username, and password from your provider's dashboard.

## Step 4: Start Your Server

Fire up your server for the first time with DupeTrace installed:

```bash
java -jar server.jar
```

The plugin will create a default `config.yml` file at `plugins/DupeTrace/config.yml` and then probably complain that it can't connect to the database. That's expected – we haven't told it where to connect yet!

## Step 5: Configure the Database

Stop your server and open `plugins/DupeTrace/config.yml` in your favorite text editor.

Find the database section and fill in your details:

```yaml
database:
  postgres:
    url: jdbc:postgresql://localhost:5432/dupe_trace
    user: postgres
    password: your_super_secret_password
    pool-size: 10
    connection-timeout-ms: 30000
```

### Connection URL Format

The URL follows this pattern:
```
jdbc:postgresql://[host]:[port]/[database_name]
```

**Examples:**
- Local: `jdbc:postgresql://localhost:5432/dupe_trace`
- Remote: `jdbc:postgresql://db.example.com:5432/dupe_trace`
- With IP: `jdbc:postgresql://192.168.1.100:5432/dupe_trace`

> 💡 **Pro Tip**: If your database host doesn't use the default port (5432), make sure to change it in the URL!

## Step 6: Restart and Verify

Start your server again:

```bash
java -jar server.jar
```

Look for this happy message in your console:

```
[DupeTrace] Database schema and indexes created successfully
[DupeTrace] DupeTrace enabled. Using PostgreSQL database.
```

If you see that, congratulations! 🎉 You're all set.

## Troubleshooting

### "Missing runtime dependency" Error

**Problem:** You downloaded the `-dev.jar` file instead of the `-paper.jar` file.

**Solution:** Download the correct file from the releases page and replace the plugin jar.

---

### "Could not connect to database" Error

**Problem:** Your database credentials are wrong, or DupeTrace can't reach your database.

**Solution:**
1. Double-check your `url`, `user`, and `password` in `config.yml`
2. Make sure PostgreSQL is running
3. If using a remote database, check your firewall rules
4. Test the connection manually using `psql` or a database client

---

### Plugin Loads But Nothing Happens

**Problem:** Configuration might be invalid.

**Solution:** Check your server logs for warnings. DupeTrace validates config values on startup and will log any issues it finds.

---

## What's Next?

Now that DupeTrace is installed and connected, you'll probably want to:

1. **[Configure the plugin](configuration.md)** – Tweak detection sensitivity and behavior
2. **[Learn the commands](commands.md)** – Test the system and investigate dupes
3. **[Set up permissions](permissions.md)** – Control who gets alerts

Happy dupe hunting! 🎯
