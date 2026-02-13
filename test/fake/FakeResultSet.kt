package dropnext.lib.jdbi.fake

import java.sql.Array as SqlArray
import java.sql.ResultSetMetaData
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.OffsetTime
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.UUID
import kotlin.time.toKotlinInstant
import kotlin.uuid.Uuid
import kotlinx.datetime.toKotlinLocalDate
import kotlinx.datetime.toKotlinLocalDateTime
import kotlinx.datetime.toKotlinLocalTime


/** Standard fake ResultSet implementation for testing. */
open class FakeResultSet(protected val columns: List<FakeColumn>) : BaseFakeResultSet() {

  // Helper to get column value
  protected fun fakeColumnValueAt(index: Int): Any? {
    require(index in 1..columns.size) { "Invalid column index: $index" }
    val value = columns[index - 1].value
    lastWasNull = value == null
    return value
  }

  override fun getMetaData(): ResultSetMetaData = FakeResultSetMetaData(columns)

  override fun getObject(columnIndex: Int): Any? {
    val value = fakeColumnValueAt(columnIndex)
    return when (value) {
      is SqlArray -> value // Return SqlArray directly for array columns
      else -> value
    }
  }

  override fun <T> getObject(columnIndex: Int, type: Class<T>): T? {
    val value = fakeColumnValueAt(columnIndex)
    @Suppress("UNCHECKED_CAST")
    return when {
      value == null -> null

      // UUID handling
      type == UUID::class.java -> when (value) {
        is UUID -> value as T
        is String -> UUID.fromString(value) as T
        else -> value as T?
      }
      type == Uuid::class.java -> when (value) {
        is Uuid -> value as T
        is String -> Uuid.parse(value) as T
        else -> value as T?
      }

      // java.sql types
      type == java.sql.Date::class.java -> when (value) {
        is java.sql.Date -> value as T
        is LocalDate -> java.sql.Date.valueOf(value) as T
        is LocalDateTime -> java.sql.Date.valueOf(value.toLocalDate()) as T
        else -> value as T?
      }
      type == java.sql.Time::class.java -> when (value) {
        is java.sql.Time -> value as T
        is LocalTime -> java.sql.Time.valueOf(value) as T
        else -> value as T?
      }
      type == Timestamp::class.java -> when (value) {
        is Timestamp -> value as T
        is LocalDateTime -> Timestamp.valueOf(value) as T
        is Instant -> Timestamp.from(value) as T
        else -> value as T?
      }

      // java.time types
      type == LocalDateTime::class.java -> when (value) {
        is LocalDateTime -> value as T
        is Timestamp -> value.toLocalDateTime() as T
        else -> value as T?
      }
      type == LocalDate::class.java -> when (value) {
        is LocalDate -> value as T
        is java.sql.Date -> value.toLocalDate() as T
        is Timestamp -> value.toLocalDateTime().toLocalDate() as T
        else -> value as T?
      }
      type == LocalTime::class.java -> when (value) {
        is LocalTime -> value as T
        is java.sql.Time -> value.toLocalTime() as T
        is Timestamp -> value.toLocalDateTime().toLocalTime() as T
        else -> value as T?
      }
      type == ZonedDateTime::class.java -> when (value) {
        is ZonedDateTime -> value as T
        is OffsetDateTime -> value.toZonedDateTime() as T
        is Instant -> value.atZone(ZoneOffset.UTC) as T
        else -> value as T?
      }
      type == OffsetDateTime::class.java -> when (value) {
        is OffsetDateTime -> value as T
        is ZonedDateTime -> value.toOffsetDateTime() as T
        is Instant -> OffsetDateTime.ofInstant(value, ZoneOffset.UTC) as T
        else -> value as T?
      }
      type == OffsetTime::class.java -> when (value) {
        is OffsetTime -> value as T
        else -> value as T?
      }
      type == Instant::class.java -> when (value) {
        is Instant -> value as T
        is Timestamp -> value.toInstant() as T
        is OffsetDateTime -> value.toInstant() as T
        is ZonedDateTime -> value.toInstant() as T
        else -> value as T?
      }

      // Kotlin kotlinx.datetime types
      type == kotlinx.datetime.LocalDateTime::class.java -> when (value) {
        is kotlinx.datetime.LocalDateTime -> value as T
        is LocalDateTime -> value.toKotlinLocalDateTime() as T
        is Timestamp -> value.toLocalDateTime().toKotlinLocalDateTime() as T
        else -> value as T?
      }
      type == kotlinx.datetime.LocalDate::class.java -> when (value) {
        is kotlinx.datetime.LocalDate -> value as T
        is LocalDate -> value.toKotlinLocalDate() as T
        is java.sql.Date -> value.toLocalDate().toKotlinLocalDate() as T
        is Timestamp -> value.toLocalDateTime().toLocalDate().toKotlinLocalDate() as T
        else -> value as T?
      }
      type == kotlinx.datetime.LocalTime::class.java -> when (value) {
        is kotlinx.datetime.LocalTime -> value as T
        is LocalTime -> value.toKotlinLocalTime() as T
        is java.sql.Time -> value.toLocalTime().toKotlinLocalTime() as T
        is Timestamp -> value.toLocalDateTime().toLocalTime().toKotlinLocalTime() as T
        else -> value as T?
      }
      type == kotlin.time.Instant::class.java -> when (value) {
        is kotlin.time.Instant -> value as T
        is Instant -> value.toKotlinInstant() as T
        is Timestamp -> value.toInstant().toKotlinInstant() as T
        is OffsetDateTime -> value.toInstant().toKotlinInstant() as T
        is ZonedDateTime -> value.toInstant().toKotlinInstant() as T
        else -> value as T?
      }

      // Default fallback
      else -> value as T?
    }
  }

  override fun getBoolean(columnIndex: Int): Boolean = fakeColumnValueAt(columnIndex) as? Boolean ?: false
  override fun getByte(columnIndex: Int): Byte = fakeColumnValueAt(columnIndex) as? Byte ?: 0
  override fun getShort(columnIndex: Int): Short = fakeColumnValueAt(columnIndex) as? Short ?: 0
  override fun getInt(columnIndex: Int): Int = fakeColumnValueAt(columnIndex) as? Int ?: 0
  override fun getLong(columnIndex: Int): Long {
    return when (val value = fakeColumnValueAt(columnIndex)) {
      null -> 0L
      is Long -> value
      is Timestamp -> value.time
      is Number -> value.toLong()
      else -> 0L
    }
  }
  override fun getFloat(columnIndex: Int): Float = fakeColumnValueAt(columnIndex) as? Float ?: 0f
  override fun getDouble(columnIndex: Int): Double = fakeColumnValueAt(columnIndex) as? Double ?: 0.0

  override fun getString(columnIndex: Int): String? {
    return when (val value = fakeColumnValueAt(columnIndex)) {
      null -> null
      is String -> value
      is UUID -> value.toString()
      else -> value.toString()
    }
  }

  override fun getTimestamp(columnIndex: Int): Timestamp? {
    return when (val value = fakeColumnValueAt(columnIndex)) {
      null -> null
      is Timestamp -> value
      is LocalDateTime -> Timestamp.valueOf(value)
      is Instant -> Timestamp.from(value)
      else -> null
    }
  }

  override fun getArray(columnIndex: Int): SqlArray {
    val value = fakeColumnValueAt(columnIndex)
    return when (value) {
      is SqlArray -> value
      is Array<*> -> FakeSqlArray(value)
      null -> throw UnsupportedOperationException("NULL array")
      else -> throw UnsupportedOperationException("Cannot convert $value to SqlArray")
    }
  }
}
