package net.fuzzyblocks.fuzzypermissions.core.database

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import java.io.File
import java.sql.Connection
import javax.sql.DataSource

class DatabaseManager(
    private val dataFolder: File,
    private val config: DatabaseCredentials // We'll define this next
) {
    private lateinit var dataSource: HikariDataSource
    val isPostgres: Boolean get() = config.type.equals("postgres", ignoreCase = true)

    fun connect() {
        val hikariConfig = HikariConfig().apply {
            poolName = "PaperPermsPool"
            maximumPoolSize = 10
            
            if (isPostgres) {
                jdbcUrl = "jdbc:postgresql://${config.host}:${config.port}/${config.database}"
                username = config.username
                password = config.password
                driverClassName = "org.postgresql.Driver"
                addDataSourceProperty("cachePrepStmts", "true")
                addDataSourceProperty("prepStmtCacheSize", "250")
                addDataSourceProperty("prepStmtCacheSqlLimit", "2048")
            } else {
                // Fallback to local SQLite
                val dbFile = File(dataFolder, "database.db")
                jdbcUrl = "jdbc:sqlite:${dbFile.absolutePath}"
                driverClassName = "org.sqlite.JDBC"
                // SQLite specific tuning
                addDataSourceProperty("foreign_keys", "true")
                addDataSourceProperty("journal_mode", "WAL")
                addDataSourceProperty("synchronous", "NORMAL")
            }
        }

        dataSource = HikariDataSource(hikariConfig)
        runMigrations()
    }

    private fun runMigrations() {
        val location = if (isPostgres) {
            "classpath:db/migration/postgres"
        } else {
            "classpath:db/migration/sqlite"
        }

        val flyway = Flyway.configure(javaClass.classLoader)
            .dataSource(dataSource)
            .locations(location)
            // Baseline allows it to attach to an existing empty DB if needed
            .baselineOnMigrate(true) 
            .load()

        flyway.migrate()
    }

    /**
     * Exposes standard JDBC connections for our Repository layer.
     */
    fun getConnection(): Connection = dataSource.connection

    fun close() {
        if (this::dataSource.isInitialized && !dataSource.isClosed) {
            dataSource.close()
        }
    }
}

// A simple data class holding the parsed config values
data class DatabaseCredentials(
    val type: String = "sqlite",
    val host: String = "localhost",
    val port: Int = 5432,
    val database: String = "paperperms",
    val username: String = "",
    val password: String = ""
)