package dropnext.lib.jdbi.fake

import java.sql.Types


/** Column data for fake ResultSet. */
data class FakeColumn(
  val value: Any?,
  val sqlType: Int
) {
  companion object {
    /** Infer SQL type from value. */
    fun fromValue(value: Any?): FakeColumn {
      val sqlType = when (value) {
        is Boolean -> Types.BOOLEAN
        is Byte -> Types.TINYINT
        is Short -> Types.SMALLINT
        is Int -> Types.INTEGER
        is Long -> Types.BIGINT
        is Float -> Types.REAL
        is Double -> Types.DOUBLE
        is java.math.BigDecimal -> Types.DECIMAL
        is String -> Types.VARCHAR
        is Char -> Types.CHAR
        is ByteArray -> Types.VARBINARY
        is java.sql.Array -> Types.ARRAY
        is java.util.UUID -> Types.OTHER
        is kotlin.uuid.Uuid -> Types.OTHER
        is java.sql.Date -> Types.DATE
        is java.sql.Time -> Types.TIME
        is java.sql.Timestamp -> Types.TIMESTAMP
        is java.time.LocalDateTime -> Types.TIMESTAMP
        is java.time.LocalDate -> Types.DATE
        is java.time.LocalTime -> Types.TIME
        is java.time.ZonedDateTime -> Types.OTHER
        is java.time.OffsetDateTime -> Types.TIMESTAMP_WITH_TIMEZONE
        is java.time.OffsetTime -> Types.TIME_WITH_TIMEZONE
        is java.time.Instant -> Types.TIMESTAMP_WITH_TIMEZONE
        is kotlinx.datetime.LocalDateTime -> Types.TIMESTAMP
        is kotlinx.datetime.LocalDate -> Types.DATE
        is kotlinx.datetime.LocalTime -> Types.TIME
        is kotlin.time.Instant -> Types.TIMESTAMP_WITH_TIMEZONE
        null -> Types.NULL
        else -> Types.OTHER
      }
      return FakeColumn(value, sqlType)
    }
  }
}

/** Extended fake column with metadata. */
data class FakeColumnWithMetadata(
  val value: Any?,
  val sqlType: Int,
  val label: String,
  val name: String
) {
  companion object {
    fun fromFakeColumn(column: FakeColumn, label: String, name: String): FakeColumnWithMetadata {
      return FakeColumnWithMetadata(column.value, column.sqlType, label, name)
    }
  }
}