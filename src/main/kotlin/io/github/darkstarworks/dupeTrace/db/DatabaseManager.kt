package io.github.darkstarworks.dupeTrace.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.bukkit.plugin.java.JavaPlugin
import java.sql.Connection
import java.sql.SQLException
import java.util.*

/**
 * Manages database connections and operations for DupeTrace.
 *
 * Author: darkstarworks | https://github.com/darkstarworks
 * Donate: https://ko-fi/darkstarworks
 *
 * Supports both H2 (embedded) and PostgreSQL databases via HikariCP connection pooling.
 * Handles item UUID registration, transfer logging, and timestamp queries for duplicate detection.
 */
class DatabaseManager(private val plugin: JavaPlugin) {
    private var dataSource: HikariDataSource? = null
    private var isPostgres: Boolean = false

    fun init() {
        val cfg = plugin.config
        val type = (cfg.getString("database.type") ?: "h2").lowercase(Locale.getDefault())
        isPostgres = type == "postgres" || type == "postgresql"

        val hikari = HikariConfig()
        if (isPostgres) {
            hikari.jdbcUrl = cfg.getString("database.postgres.url")
            hikari.username = cfg.getString("database.postgres.user")
            hikari.password = cfg.getString("database.postgres.password")
            hikari.driverClassName = "org.postgresql.Driver"
        } else {
            val rawFile = cfg.getString("database.h2.file", "plugins/DupeTrace/data/dupetrace")
            val normalizedFile = normalizeH2FilePath(rawFile ?: "plugins/DupeTrace/data/dupetrace")
            // Ensure parent directories exist for non-home (~) paths
            if (!normalizedFile.startsWith("~/")) {
                try {
                    val parent = java.io.File(normalizedFile).parentFile
                    parent?.mkdirs()
                } catch (_: Throwable) {
                    // ignore, H2 will attempt to create directories as well
                }
            }
            hikari.jdbcUrl = "jdbc:h2:file:$normalizedFile;MODE=PostgreSQL;DATABASE_TO_UPPER=false;AUTO_SERVER=TRUE"
            hikari.driverClassName = "org.h2.Driver"
            hikari.username = "dt"
            hikari.password = ""
        }
        hikari.maximumPoolSize = 5
        hikari.poolName = "DupeTracePool"

        dataSource = HikariDataSource(hikari)
        createSchema()
    }

    /**
     * Fire-and-forget async wrapper. Schedules DB write-off in the main thread.
     */
    fun recordSeenAsync(id: UUID) {
        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            try {
                recordSeen(id)
            } catch (t: Throwable) {
                plugin.logger.warning("Async recordSeen failed for $id: ${t.message}")
            }
        })
    }

    /**
     * Fire-and-forget async wrapper. Schedules DB write-off in the main thread.
     */
    fun logItemTransferAsync(itemUUID: String, playerUUID: UUID, action: String, location: String) {
        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            try {
                logItemTransfer(itemUUID, playerUUID, action, location)
            } catch (t: Throwable) {
                plugin.logger.warning("Async logItemTransfer failed for $itemUUID: ${t.message}")
            }
        })
    }

    private fun connection(): Connection {
        return dataSource?.connection ?: throw IllegalStateException("DataSource not initialized")
    }

    private fun createSchema() {
        // Use UUID native type in both databases (supported by Postgres and H2)
        val createItems = """
            CREATE TABLE IF NOT EXISTS dupetrace_items (
                id UUID PRIMARY KEY,
                first_seen TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
            )
        """.trimIndent()
        val createTransfers = if (isPostgres) """
            CREATE TABLE IF NOT EXISTS dupetrace_item_transfers (
                id BIGSERIAL PRIMARY KEY,
                item_uuid UUID NOT NULL,
                player_uuid UUID NOT NULL,
                action VARCHAR(64) NOT NULL,
                location TEXT NOT NULL,
                ts TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
            )
        """.trimIndent() else """
            CREATE TABLE IF NOT EXISTS dupetrace_item_transfers (
                id IDENTITY PRIMARY KEY,
                item_uuid UUID NOT NULL,
                player_uuid UUID NOT NULL,
                action VARCHAR(64) NOT NULL,
                location TEXT NOT NULL,
                ts TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
            )
        """.trimIndent()
        connection().use { conn ->
            conn.createStatement().use { st ->
                st.executeUpdate(createItems)
                st.executeUpdate(createTransfers)
            }
        }
    }

    /**
     * Records that an item UUID has been seen.
     *
     * @param id The UUID of the item
     * @return true if this is the first time seeing this UUID (new), false if it already existed (duplicate)
     */
    fun recordSeen(id: UUID): Boolean {
        val sql = if (isPostgres) {
            // Postgres: try insert, detect conflict
            "INSERT INTO dupetrace_items(id) VALUES (?) ON CONFLICT (id) DO NOTHING"
        } else {
            // H2: emulate with MERGE which inserts when not exists
            "MERGE INTO dupetrace_items (id) KEY(id) VALUES (?)"
        }
        connection().use { conn ->
            conn.prepareStatement(sql).use { ps ->
                // setObject with UUID ensures native uuid in Postgres, H2 also supports
                ps.setObject(1, id)
                return try {
                    val updated = ps.executeUpdate()
                    // updated == 1 means it was inserted (new)
                    updated == 1
                } catch (e: SQLException) {
                    plugin.logger.warning("DB error while recording UUID $id: ${e.message}")
                    // on error, assume duplicate to be safe
                    false
                }
            }
        }
    }

    /**
     * Log item transfer/activity with player association.
     */
    fun logItemTransfer(itemUUID: String, playerUUID: UUID, action: String, location: String) {
        val uuid = runCatching { UUID.fromString(itemUUID) }.getOrNull() ?: return
        val sql = "INSERT INTO dupetrace_item_transfers (item_uuid, player_uuid, action, location) VALUES (?,?,?,?)"
        connection().use { conn ->
            conn.prepareStatement(sql).use { ps ->
                ps.setObject(1, uuid)
                ps.setObject(2, playerUUID)
                ps.setString(3, action)
                ps.setString(4, location)
                try {
                    ps.executeUpdate()
                } catch (e: SQLException) {
                    plugin.logger.warning("DB error while logging transfer for $uuid: ${e.message}")
                }
            }
        }
    }

    /**
     * Return the earliest timestamp (epoch millis) when this player was recorded interacting with this item.
     * Returns null if no transfer records exist.
     */
    fun getEarliestTransferTs(itemUUID: String, playerUUID: UUID): Long? {
        val uuid = runCatching { UUID.fromString(itemUUID) }.getOrNull() ?: return null
        val sql = "SELECT MIN(ts) FROM dupetrace_item_transfers WHERE item_uuid = ? AND player_uuid = ?"
        connection().use { conn ->
            conn.prepareStatement(sql).use { ps ->
                ps.setObject(1, uuid)
                ps.setObject(2, playerUUID)
                try {
                    ps.executeQuery().use { rs ->
                        if (rs.next()) {
                            return rs.getTimestamp(1)?.time
                        }
                    }
                } catch (e: SQLException) {
                    plugin.logger.warning("DB error while fetching earliest transfer for $uuid/$playerUUID: ${e.message}")
                }
            }
        }
        return null
    }

    private fun normalizeH2FilePath(path: String): String {
        val p = path.trim()
        // Absolute path? (works for both Unix and Windows)
        val f = java.io.File(p)
        if (f.isAbsolute) return f.absolutePath
        // Allow explicit relative and home-relative paths that H2 understands
        if (p.startsWith("~/") || p.startsWith("./")) return p
        // Otherwise, prefix with "./" to make it explicitly relative so H2 2.x accepts it
        return "./$p"
    }

    fun close() {
        dataSource?.close()
        dataSource = null
    }
}
