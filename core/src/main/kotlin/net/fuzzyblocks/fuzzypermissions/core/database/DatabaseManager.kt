package net.fuzzyblocks.fuzzypermissions.core.database

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.withContext
import net.fuzzyblocks.fuzzypermissions.core.database.repository.SqlDialect
import org.flywaydb.core.Flyway
import javax.sql.DataSource
import java.io.File

class DatabaseManager(
    private val dataFolder: File,
    private val config: DatabaseCredentials
) {
    private lateinit var pool: HikariDataSource

    val dataSource: DataSource get() = pool

    val dialect: SqlDialect get() = config.dialect

    /** Builds the pool and runs migrations on [DatabaseDispatcher]; never call blocking JDBC on the main thread. */
    suspend fun connect() = withContext(DatabaseDispatcher) {
        val ds = HikariDataSource(buildHikariConfig())
        try {
            runMigrations(ds)
        } catch (t: Throwable) {
            ds.close()
            throw t
        }
        pool = ds
    }

    private fun buildHikariConfig() = HikariConfig().apply {
        poolName = "PaperPermsPool"
        when (dialect) {
            SqlDialect.POSTGRESQL -> {
                jdbcUrl = "jdbc:postgresql://${config.host}:${config.port}/${config.database}"
                username = config.username
                password = config.password
                driverClassName = "org.postgresql.Driver"
                maximumPoolSize = 10
            }
            SqlDialect.SQLITE -> {
                dataFolder.mkdirs()
                jdbcUrl = "jdbc:sqlite:${File(dataFolder, "database.db").absolutePath}"
                driverClassName = "org.sqlite.JDBC"
                addDataSourceProperty("foreign_keys", "true")
                addDataSourceProperty("journal_mode", "WAL")
                addDataSourceProperty("synchronous", "NORMAL")
                addDataSourceProperty("busy_timeout", "5000")
                addDataSourceProperty("transaction_mode", "IMMEDIATE")
                maximumPoolSize = 4
                minimumIdle = 1
            }
        }
    }

    private fun runMigrations(ds: HikariDataSource) {
        val location = when (dialect) {
            SqlDialect.POSTGRESQL -> "classpath:db/migration/postgres"
            SqlDialect.SQLITE -> "classpath:db/migration/sqlite"
        }
        Flyway.configure(javaClass.classLoader)
            .dataSource(ds)
            .locations(location)
            .load()
            .migrate()
    }

    fun close() {
        if (this::pool.isInitialized && !pool.isClosed) pool.close()
    }
}

data class DatabaseCredentials(
    val type: String = "sqlite",
    val host: String = "localhost",
    val port: Int = 5432,
    val database: String = "fuzzypermissions",
    val username: String = "",
    val password: String = ""
) {
    val dialect: SqlDialect
        get() = if (type.lowercase() in setOf("postgres", "postgresql")) SqlDialect.POSTGRESQL else SqlDialect.SQLITE

    override fun toString() =
        "DatabaseCredentials(type=$type, host=$host, port=$port, database=$database, username=$username, password=***)"
}