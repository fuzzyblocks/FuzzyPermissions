package net.fuzzyblocks.fuzzypermissions.core.database.repository

import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Timestamp
import java.sql.Types
import java.time.Instant
import java.util.Calendar
import java.util.TimeZone
import java.util.UUID

enum class SqlDialect {
    SQLITE, POSTGRESQL;

    companion object {
        fun fromProductName(name: String): SqlDialect = when {
            name.contains("sqlite", ignoreCase = true) -> SQLITE
            name.contains("postgres", ignoreCase = true) -> POSTGRESQL
            else -> error("Unsupported database: $name")
        }
    }
}

private fun utcCalendar(): Calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))

/** Role/group ids: VARCHAR(36) on SQLite, native UUID on PostgreSQL. */
internal fun PreparedStatement.setId(index: Int, id: UUID, dialect: SqlDialect) = when (dialect) {
    SqlDialect.SQLITE -> setString(index, id.toString())
    SqlDialect.POSTGRESQL -> setObject(index, id)
}

internal fun PreparedStatement.setNullableId(index: Int, id: UUID?, dialect: SqlDialect) {
    if (id != null) {
        setId(index, id, dialect)
    } else when (dialect) {
        SqlDialect.SQLITE -> setNull(index, Types.VARCHAR)
        SqlDialect.POSTGRESQL -> setNull(index, Types.OTHER)
    }
}

/** player_uuid columns are VARCHAR(36) on both backends, so always bind text. */
internal fun PreparedStatement.setPlayerUuid(index: Int, uuid: UUID) = setString(index, uuid.toString())

/** Reading as text works for both VARCHAR and native UUID columns. */
internal fun ResultSet.getUuid(column: Int): UUID = UUID.fromString(getString(column))
internal fun ResultSet.getUuid(column: String): UUID = UUID.fromString(getString(column))

internal fun ResultSet.getNullableUuid(column: String): UUID? = getString(column)?.let(UUID::fromString)

internal fun ResultSet.getIntOrNull(column: String): Int? =
    getInt(column).takeUnless { wasNull() }

/** TIMESTAMP is always written/read as UTC, independent of the JVM's default zone. */
internal fun PreparedStatement.setInstant(index: Int, instant: Instant) =
    setTimestamp(index, Timestamp.from(instant), utcCalendar())

internal fun ResultSet.getInstant(column: String): Instant? =
    getTimestamp(column, utcCalendar())?.toInstant()