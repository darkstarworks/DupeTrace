package io.github.darkstarworks.dupeTrace.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.bukkit.plugin.java.JavaPlugin
import java.sql.Connection
import java.sql.SQLException
import java.util.*

/**
 * Manages database connections and operations for DupeTrace (PostgreSQL only).
 *
 * Author: darkstarworks | https://github.com/darkstarworks
 * Donate: https://ko-fi/darkstarworks
 */
class DatabaseManager(private val plugin: JavaPlugin) {
    private var dataSource: HikariDataSource? = null

    fun init() {
        val cfg = plugin.config
        val hikari = HikariConfig()
        hikari.jdbcUrl = cfg.getString("database.postgres.url")
        hikari.username = cfg.getString("database.postgres.user")
        hikari.password = cfg.getString("database.postgres.password")
        hikari.driverClassName = "org.postgresql.Driver"
        hikari.maximumPoolSize = cfg.getInt("database.postgres.pool-size", 10).coerceIn(1, 50)
        hikari.connectionTimeout = cfg.getLong("database.postgres.connection-timeout-ms", 30000L).coerceAtLeast(1000L)
        hikari.poolName = "DupeTracePool"

        dataSource = HikariDataSource(hikari)
        createSchema()
    }

    /**
     * Fire-and-forget async wrapper. Executes the DB write off the main server thread.
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
     * Fire-and-forget async wrapper. Executes the DB write off the main server thread.
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
        val createItems = """
            CREATE TABLE IF NOT EXISTS dupetrace_items (
                id UUID PRIMARY KEY,
                first_seen TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
            )
        """.trimIndent()
        val createTransfers = """
            CREATE TABLE IF NOT EXISTS dupetrace_item_transfers (
                id BIGSERIAL PRIMARY KEY,
                item_uuid UUID NOT NULL,
                player_uuid UUID NOT NULL,
                action VARCHAR(64) NOT NULL,
                location TEXT NOT NULL,
                ts TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
            )
        """.trimIndent()

        // Performance indexes for common queries
        val createIndexItemUuid = """
            CREATE INDEX IF NOT EXISTS idx_transfers_item_uuid ON dupetrace_item_transfers(item_uuid)
        """.trimIndent()
        val createIndexPlayerUuid = """
            CREATE INDEX IF NOT EXISTS idx_transfers_player_uuid ON dupetrace_item_transfers(player_uuid)
        """.trimIndent()
        val createIndexTimestamp = """
            CREATE INDEX IF NOT EXISTS idx_transfers_ts ON dupetrace_item_transfers(ts DESC)
        """.trimIndent()
        val createCompositeIndex = """
            CREATE INDEX IF NOT EXISTS idx_transfers_item_player ON dupetrace_item_transfers(item_uuid, player_uuid, ts DESC)
        """.trimIndent()

        connection().use { conn ->
            conn.createStatement().use { st ->
                st.executeUpdate(createItems)
                st.executeUpdate(createTransfers)
                st.executeUpdate(createIndexItemUuid)
                st.executeUpdate(createIndexPlayerUuid)
                st.executeUpdate(createIndexTimestamp)
                st.executeUpdate(createCompositeIndex)
            }
        }
        plugin.logger.info("Database schema and indexes created successfully")
    }

    /**
     * Records that an item UUID has been seen.
     *
     * @param id The UUID of the item
     * @return true if this is the first time seeing this UUID (new), false if it already existed (duplicate)
     */
    fun recordSeen(id: UUID): Boolean {
        val sql = "INSERT INTO dupetrace_items(id) VALUES (?) ON CONFLICT (id) DO NOTHING"
        connection().use { conn ->
            conn.prepareStatement(sql).use { ps ->
                ps.setObject(1, id)
                return try {
                    val updated = ps.executeUpdate()
                    updated == 1
                } catch (e: SQLException) {
                    plugin.logger.warning("DB error while recording UUID $id: ${e.message}")
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

    /**
     * Get all transfer records for a specific item UUID.
     * Returns a list of transfer records with player UUID, action, location, and timestamp.
     */
    fun getItemTransferHistory(itemUUID: String, limit: Int = 100): List<TransferRecord> {
        val uuid = runCatching { UUID.fromString(itemUUID) }.getOrNull() ?: return emptyList()
        val sql = "SELECT player_uuid, action, location, ts FROM dupetrace_item_transfers WHERE item_uuid = ? ORDER BY ts DESC LIMIT ?"
        val records = mutableListOf<TransferRecord>()
        connection().use { conn ->
            conn.prepareStatement(sql).use { ps ->
                ps.setObject(1, uuid)
                ps.setInt(2, limit)
                try {
                    ps.executeQuery().use { rs ->
                        while (rs.next()) {
                            records.add(
                                TransferRecord(
                                    playerUUID = rs.getObject(1, UUID::class.java),
                                    action = rs.getString(2),
                                    location = rs.getString(3),
                                    timestamp = rs.getTimestamp(4).time
                                )
                            )
                        }
                    }
                } catch (e: SQLException) {
                    plugin.logger.warning("DB error while fetching transfer history for $uuid: ${e.message}")
                }
            }
        }
        return records
    }

    /**
     * Check if an item UUID exists in the database.
     */
    fun itemExists(itemUUID: String): Boolean {
        val uuid = runCatching { UUID.fromString(itemUUID) }.getOrNull() ?: return false
        val sql = "SELECT 1 FROM dupetrace_items WHERE id = ? LIMIT 1"
        connection().use { conn ->
            conn.prepareStatement(sql).use { ps ->
                ps.setObject(1, uuid)
                try {
                    ps.executeQuery().use { rs ->
                        return rs.next()
                    }
                } catch (e: SQLException) {
                    plugin.logger.warning("DB error while checking item existence for $uuid: ${e.message}")
                }
            }
        }
        return false
    }

    /**
     * Get all items associated with a specific player UUID.
     */
    fun getPlayerItems(playerUUID: UUID, limit: Int = 50): List<String> {
        val sql = "SELECT DISTINCT item_uuid FROM dupetrace_item_transfers WHERE player_uuid = ? ORDER BY ts DESC LIMIT ?"
        val items = mutableListOf<String>()
        connection().use { conn ->
            conn.prepareStatement(sql).use { ps ->
                ps.setObject(1, playerUUID)
                ps.setInt(2, limit)
                try {
                    ps.executeQuery().use { rs ->
                        while (rs.next()) {
                            items.add(rs.getObject(1, UUID::class.java).toString())
                        }
                    }
                } catch (e: SQLException) {
                    plugin.logger.warning("DB error while fetching player items for $playerUUID: ${e.message}")
                }
            }
        }
        return items
    }

    /**
     * Get database statistics for monitoring.
     */
    fun getStats(): DatabaseStats {
        val stats = DatabaseStats()
        connection().use { conn ->
            try {
                conn.createStatement().use { st ->
                    st.executeQuery("SELECT COUNT(*) FROM dupetrace_items").use { rs ->
                        if (rs.next()) stats.totalItems = rs.getLong(1)
                    }
                    st.executeQuery("SELECT COUNT(*) FROM dupetrace_item_transfers").use { rs ->
                        if (rs.next()) stats.totalTransfers = rs.getLong(1)
                    }
                    st.executeQuery("SELECT COUNT(DISTINCT player_uuid) FROM dupetrace_item_transfers").use { rs ->
                        if (rs.next()) stats.uniquePlayers = rs.getLong(1)
                    }
                }
            } catch (e: SQLException) {
                plugin.logger.warning("DB error while fetching stats: ${e.message}")
            }
        }
        return stats
    }

    data class TransferRecord(
        val playerUUID: UUID,
        val action: String,
        val location: String,
        val timestamp: Long
    )

    data class DatabaseStats(
        var totalItems: Long = 0,
        var totalTransfers: Long = 0,
        var uniquePlayers: Long = 0
    )

    fun close() {
        dataSource?.close()
        dataSource = null
    }
}
