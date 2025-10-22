package io.github.darkstarworks.dupeTrace.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.bukkit.plugin.java.JavaPlugin
import java.sql.Connection
import java.sql.SQLException
import java.util.*

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
            val file = cfg.getString("database.h2.file", "plugins/DupeTrace/data/dupetace")
            hikari.jdbcUrl = "jdbc:h2:file:$file;MODE=PostgreSQL;DATABASE_TO_UPPER=false;AUTO_SERVER=TRUE"
            hikari.driverClassName = "org.h2.Driver"
            hikari.username = "sa"
            hikari.password = ""
        }
        hikari.maximumPoolSize = 5
        hikari.poolName = "DupeTracePool"

        dataSource = HikariDataSource(hikari)
        createSchema()
    }

    private fun connection(): Connection {
        return dataSource?.connection ?: throw IllegalStateException("DataSource not initialized")
    }

    private fun createSchema() {
        // Use UUID native type in both databases (supported by Postgres and H2)
        val create = """
            CREATE TABLE IF NOT EXISTS dupetrace_items (
                id UUID PRIMARY KEY,
                first_seen TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
            )
        """.trimIndent()
        connection().use { conn ->
            conn.createStatement().use { st ->
                st.executeUpdate(create)
            }
        }
    }

    /**
     * Records that this UUID is seen. Returns true if it's new, false if it already existed (duplicate detected).
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

    fun close() {
        dataSource?.close()
        dataSource = null
    }
}
